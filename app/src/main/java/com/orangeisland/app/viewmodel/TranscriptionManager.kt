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
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.service.OrangeIslandForegroundService
import kotlinx.coroutines.CancellationException
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
                val meta = AttachmentMeta.parse(msg.attachmentMeta)
                if (meta == null) {
                    DebugLog.d("ImageTranscription", "collectTargets msg=${msg.id.take(12)} images=${msg.images.size} parse FAILED -> raw=${msg.attachmentMeta?.take(200)}")
                    continue
                }
                DebugLog.d("ImageTranscription", "collectTargets msg=${msg.id.take(12)} images=${msg.images.size} metaItems=${meta.items.size} " +
                    "itemTypes=${meta.items.map { "${it.type}(idx=${it.imageIndex},trans=${!it.transcription.isNullOrBlank()})" }} " +
                    "raw=${msg.attachmentMeta?.take(200)}")
                val isLatest = msg.id == latestUserMsg?.id
                meta.items.forEachIndexed { index, item ->
                    val imageIndex = item.imageIndex
                    val imageType = item.type
                    // Video narration has its own manager (VideoNarrationManager); do not
                    // process videos here or they show up as "Image Transcription".
                    if (imageIndex == null || (imageType != "image" && imageType != "pdf")) return@forEachIndexed
                    val count = when (imageType) {
                        "pdf" -> item.pageCount ?: 1
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
                val meta = AttachmentMeta.parse(msg.attachmentMeta) ?: AttachmentMeta()
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
        batchSize: Int,
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
        val total = targets.size
        var processed = 0
        val effectiveBatchSize = batchSize.coerceIn(1, 10)
        val chunks = targets.chunked(effectiveBatchSize)

        suspend fun runSingle(target: TranscriptionTarget, errorSetter: (String) -> Unit): String {
            val promptMessages = listOf(ChatMessage(
                text = prompt.ifBlank { BuiltInPrompts.IMAGE_TRANSCRIPTION_USER },
                images = listOf(target.imagePath),
                participant = Participant.USER,
                status = MessageStatus.SUCCESS
            ))
            val sb = StringBuilder()
            provider.generateResponse(promptMessages, transcriptionConfig).collect { event ->
                when (event) {
                    is StreamEvent.TextChunk -> sb.append(event.text)
                    is StreamEvent.Error -> errorSetter(event.message)
                    else -> {}
                }
            }
            return sb.toString().trim()
        }

        for (chunk in chunks) {
            if (generationJob?.isCancelled == true) throw CancellationException("Transcription cancelled")
            if (!currentCoroutineContext().isActive) throw CancellationException("Transcription cancelled")

            withContext(Dispatchers.Main) {
                OrangeIslandForegroundService.updateText(
                    context.getString(R.string.transcription_progress, processed + 1, total)
                )
            }

            val currentSegment = MessageSegment(type = "transcription", content = "Transcribing...")
            transcriptionSegments.add(currentSegment)

            fun emitProgress(content: String) {
                transcriptionSegments[transcriptionSegments.lastIndex] = currentSegment.copy(content = content)
                onProgress(ChatMessage(
                    id = modelMessageId, parentId = parentId, text = "",
                    participant = Participant.MODEL, status = MessageStatus.TRANSCRIBING, timestamp = startTime,
                    retryText = "${(processed + chunk.size).coerceAtMost(total)}/$total",
                    thoughtTitle = "Image Transcription",
                    segments = transcriptionSegments.toList() + MessageSegment(type = "answer"),
                ))
            }
            emitProgress(currentSegment.content)

            var streamError: String? = null
            val texts: List<String>

            if (chunk.size == 1) {
                val target = chunk[0]
                val promptMessages = listOf(ChatMessage(
                    text = prompt.ifBlank { BuiltInPrompts.IMAGE_TRANSCRIPTION_USER },
                    images = listOf(target.imagePath),
                    participant = Participant.USER,
                    status = MessageStatus.SUCCESS
                ))
                val sb = StringBuilder()
                provider.generateResponse(promptMessages, transcriptionConfig).collect { event ->
                    when (event) {
                        is StreamEvent.TextChunk -> {
                            sb.append(event.text)
                            emitProgress(sb.toString())
                        }
                        is StreamEvent.Error -> { streamError = event.message }
                        else -> {}
                    }
                }
                texts = listOf(sb.toString().trim())
            } else {
                val batchPrompt = buildString {
                    append(prompt.ifBlank { BuiltInPrompts.IMAGE_TRANSCRIPTION_USER })
                    append("\n\n")
                    append(BuiltInPrompts.imageTranscriptionBatchInstruction(chunk.size))
                }
                val promptMessages = listOf(ChatMessage(
                    text = batchPrompt,
                    images = chunk.map { it.imagePath },
                    participant = Participant.USER,
                    status = MessageStatus.SUCCESS
                ))
                val sb = StringBuilder()
                provider.generateResponse(promptMessages, transcriptionConfig).collect { event ->
                    when (event) {
                        is StreamEvent.TextChunk -> {
                            sb.append(event.text)
                            emitProgress(sb.toString())
                        }
                        is StreamEvent.Error -> { streamError = event.message }
                        else -> {}
                    }
                }
                val parsed = if (streamError == null) BuiltInPrompts.parseImageTranscriptionBatch(sb.toString(), chunk.size) else null
                texts = if (parsed != null) {
                    parsed
                } else if (streamError == null) {
                    DebugLog.e("ImageTranscription", "batch parse failed for chunk size=${chunk.size}, falling back to per-image calls")
                    val fallback = mutableListOf<String>()
                    for (target in chunk) {
                        if (streamError != null) break
                        val text = runSingle(target) { streamError = it }
                        fallback.add(text)
                    }
                    fallback
                } else emptyList()
            }

            if (streamError != null) return Pair(transcriptionSegments, streamError)

            transcriptionSegments[transcriptionSegments.lastIndex] = currentSegment.copy(
                content = if (chunk.size == 1) texts.first()
                    else texts.mapIndexed { i, t -> "[${i + 1}/${chunk.size}]\n$t" }.joinToString("\n\n")
            )

            chunk.forEachIndexed { i, target ->
                val text = texts.getOrElse(i) { "" }
                results.getOrPut(target.messageId) { mutableListOf() }
                    .add(target.metaItemIndex to text)
            }
            processed += chunk.size
        }

        for ((messageId, updates) in results) {
            val entity = conversations.getMessagesForConversationSnapshot(conversationId).find { it.id == messageId }
            if (entity != null) {
                val meta = AttachmentMeta.parse(entity.attachmentMeta) ?: AttachmentMeta()
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
