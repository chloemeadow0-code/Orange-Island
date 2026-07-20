package com.orangeisland.app.viewmodel

import android.content.Context
import com.orangeisland.app.R
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.ProviderConfig
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.data.BuiltInPrompts
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.model.AttachmentMeta
import com.orangeisland.app.model.AttachmentItem
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.service.OrangeIslandForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Handles image/video/PDF transcription as a pre-processing stage before
 * LLM generation. Extracted from GenerationManager (~160 lines).
 *
 * Lifecycle: created once per GenerationManager, used for each generation
 * that has images needing transcription.
 */
class TranscriptionManager(
    private val providers: Map<String, LlmProvider>,
    private val conversations: ConversationRepository,
    private val context: Context
) {
    data class TranscriptionTarget(
        val messageId: String,
        val imagePath: String,
        val metaItemIndex: Int
    )

    /**
     * Collect all images in the message path that need transcription.
     * User attachments in the latest user message are re-transcribed on every
     * send; assistant-generated images are cached after the first transcription.
     */
    suspend fun collectTargets(
        conversationId: String,
        parentId: String?
    ): List<TranscriptionTarget> {
        val allMessages = conversations.getMessagesForConversationSnapshot(conversationId)
        val pathMessages = mutableListOf<MessageEntity>()
        var currentId = parentId
        while (currentId != null) {
            val msg = allMessages.find { it.id == currentId } ?: break
            pathMessages.add(0, msg)
            currentId = msg.parentId
        }
        val latestUserMsg = pathMessages.lastOrNull { it.participant == Participant.USER }
        val targets = mutableListOf<TranscriptionTarget>()
        for (msg in pathMessages) {
            if (msg.images.isEmpty()) continue

            if (msg.participant == Participant.USER) {
                val meta = msg.attachmentMeta?.let {
                    try { Json.decodeFromString<AttachmentMeta>(it) } catch (_: Exception) { null }
                } ?: continue
                val isLatest = msg.id == latestUserMsg?.id
                meta.items.forEachIndexed { index, item ->
                    val imageIndex = item.imageIndex
                    val imageType = item.type
                    if (imageIndex == null || (imageType != "image" && imageType != "pdf" && imageType != "video")) return@forEachIndexed
                    val count = when (imageType) {
                        "pdf" -> item.pageCount ?: 1
                        "video" -> item.pageCount ?: 1
                        else -> 1
                    }
                    for (i in 0 until count) {
                        val offset = imageIndex + i
                        if (offset !in msg.images.indices) break
                        val imagePath = msg.images[offset]
                        if (isLatest || item.transcription.isNullOrEmpty()) {
                            targets.add(TranscriptionTarget(msg.id, imagePath, index))
                        }
                    }
                }
                continue
            }

            if (msg.participant == Participant.MODEL) {
                val meta = msg.attachmentMeta?.let {
                    try { Json.decodeFromString<AttachmentMeta>(it) } catch (_: Exception) { null }
                } ?: AttachmentMeta()
                val items = meta.items.toMutableList()
                var changed = false
                msg.images.forEachIndexed { imageIndex, imagePath ->
                    val existingIndex = items.indexOfFirst { it.type == "image" && it.imageIndex == imageIndex }
                    val itemIndex = if (existingIndex >= 0) {
                        existingIndex
                    } else {
                        items.add(
                            AttachmentItem(
                                type = "image",
                                fileName = File(imagePath).name,
                                mimeType = if (imagePath.endsWith(".png", true)) "image/png" else "image/jpeg",
                                imageIndex = imageIndex
                            )
                        )
                        changed = true
                        items.lastIndex
                    }
                    if (items[itemIndex].transcription.isNullOrEmpty()) {
                        targets.add(TranscriptionTarget(msg.id, imagePath, itemIndex))
                    }
                }
                if (changed) {
                    conversations.upsertMessage(msg.copy(
                        attachmentMeta = Json.encodeToString(
                            AttachmentMeta.serializer(),
                            AttachmentMeta(items = items)
                        )
                    ))
                }
            }
        }
        return targets
    }

    /**
     * Run transcription for all targets. Streams progress via [onProgress].
     * Returns the transcription segments (for display in the UI) and persists
     * results to the message attachment metadata.
     *
     * @return Pair of (segments list, error message or null)
     */
    suspend fun transcribe(
        targets: List<TranscriptionTarget>,
        conversationId: String,
        providerName: String,
        modelId: String,
        apiKey: String,
        baseUrl: String?,
        prompt: String,
        generationJob: Job?,
        modelMessageId: String,
        startTime: Long,
        onProgress: (ChatMessage) -> Unit
    ): Pair<List<MessageSegment>, String?> {
        val provider = providers[providerName] ?: providers.values.first()
        val transcriptionConfig = ProviderConfig(
            apiKey = apiKey,
            modelId = modelId,
            systemPrompt = BuiltInPrompts.IMAGE_TRANSCRIPTION_SYSTEM,
            thinkingEnabled = false,
            baseUrl = baseUrl
        )
        val placeholder = conversations.getMessagesForConversationSnapshot(conversationId).find { it.id == modelMessageId }
        val parentId = placeholder?.parentId
        val results = mutableMapOf<String, MutableList<Pair<Int, String>>>()
        val transcriptionSegments = mutableListOf<MessageSegment>()
        var processed = 0
        val total = targets.size

        for (target in targets) {
            if (generationJob?.isCancelled == true) throw CancellationException("Transcription cancelled")
            if (!currentCoroutineContext().isActive) throw CancellationException("Transcription cancelled")

            withContext(Dispatchers.Main) {
                OrangeIslandForegroundService.updateText(context.getString(R.string.transcription_progress, processed + 1, total))
            }

            val currentSegment = MessageSegment(type = "transcription", content = "Transcribing...")
            transcriptionSegments.add(currentSegment)
            onProgress(ChatMessage(
                id = modelMessageId, parentId = parentId, text = "",
                participant = Participant.MODEL, status = MessageStatus.TRANSCRIBING, timestamp = startTime,
                retryText = "${processed + 1}/$total",
                thoughtTitle = "Image Transcription",
                // Trailing empty answer segment keeps the timeline renderer active during
                // transcription (it keys on the presence of an "answer" segment), so the
                // block morphs in place into the thought block instead of disappearing.
                segments = transcriptionSegments.toList() + MessageSegment(type = "answer"),
            ))

            val promptMessages = listOf(ChatMessage(
                text = prompt.ifBlank { BuiltInPrompts.IMAGE_TRANSCRIPTION_USER },
                images = listOf(target.imagePath),
                participant = Participant.USER,
                status = MessageStatus.SUCCESS
            ))
            val transcription = StringBuilder()
            var streamError: String? = null
            provider.generateResponse(promptMessages, transcriptionConfig).collect { event ->
                when (event) {
                    is StreamEvent.TextChunk -> {
                        transcription.append(event.text)
                        transcriptionSegments[transcriptionSegments.lastIndex] = currentSegment.copy(content = transcription.toString())
                        onProgress(ChatMessage(
                            id = modelMessageId, parentId = parentId, text = "",
                            participant = Participant.MODEL, status = MessageStatus.TRANSCRIBING, timestamp = startTime,
                            retryText = "${processed + 1}/$total",
                            thoughtTitle = "Image Transcription",
                            // Trailing empty answer segment keeps the timeline renderer active during
                // transcription (it keys on the presence of an "answer" segment), so the
                // block morphs in place into the thought block instead of disappearing.
                segments = transcriptionSegments.toList() + MessageSegment(type = "answer"),
                        ))
                    }
                    is StreamEvent.Error -> { streamError = event.message }
                    else -> {}
                }
            }
            if (streamError != null) return Pair(transcriptionSegments, streamError)
            val text = transcription.toString().trim()
            transcriptionSegments[transcriptionSegments.lastIndex] = currentSegment.copy(content = text)
            results.getOrPut(target.messageId) { mutableListOf() }
                .add(target.metaItemIndex to text)
            processed++
        }

        // Persist results back to message attachment metadata
        for ((messageId, updates) in results) {
            val entity = conversations.getMessagesForConversationSnapshot(conversationId).find { it.id == messageId }
            if (entity != null) {
                val meta = entity.attachmentMeta?.let {
                    try { Json.decodeFromString<AttachmentMeta>(it) } catch (_: Exception) { null }
                } ?: AttachmentMeta()
                val items = meta.items.toMutableList()
                val grouped: Map<Int, List<String>> = updates.groupBy { it.first }.mapValues { e -> e.value.map { it.second } }
                for ((index, texts) in grouped) {
                    if (index in items.indices) {
                        val joined = if (texts.size == 1) texts.first()
                        else texts.mapIndexed { i, t -> "[Page ${i + 1}]\n$t" }.joinToString("\n\n")
                        items[index] = items[index].copy(transcription = joined)
                    }
                }
                conversations.upsertMessage(entity.copy(
                    attachmentMeta = Json.encodeToString(AttachmentMeta.serializer(), AttachmentMeta(items = items))
                ))
            }
        }
        return Pair(transcriptionSegments, null)
    }
}
