package com.orangeisland.app.viewmodel

import android.content.Context
import com.orangeisland.app.R
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.OpenAiVideoUrl
import com.orangeisland.app.api.ProviderConfig
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.data.BuiltInPrompts
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.model.AttachmentMeta
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.service.OrangeIslandForegroundService
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pre-processing stage that sends attached videos to a dedicated video-understanding model
 * (e.g. MiniMax-M3) and writes the resulting narration back into the message metadata.
 *
 * Symmetric to [TranscriptionManager] but works on the original video file instead of
 * extracted frames, and stores the result in [AttachmentItem.videoTranscription].
 */
class VideoNarrationManager(
    private val providers: Map<String, LlmProvider>,
    private val conversations: ConversationRepository,
    private val context: Context
) {
    data class NarrationTarget(
        val messageId: String,
        val videoUri: String,
        val metaItemIndex: Int
    )

    /**
     * Collect all video attachments in the latest user message that need narration.
     * Only the latest user message is re-narrated on every send; earlier videos keep
     * their cached narration.
     */
    suspend fun collectTargets(
        conversationId: String,
        parentId: String?
    ): List<NarrationTarget> {
        DebugLog.d(TAG, "collectTargets conv=${conversationId.take(12)} parentId=${parentId?.take(12) ?: "null"}")
        val allMessages = conversations.getMessagesForConversationSnapshot(conversationId)
        val pathMessages = mutableListOf<MessageEntity>()
        var currentId = parentId
        while (currentId != null) {
            val msg = allMessages.find { it.id == currentId } ?: break
            pathMessages.add(0, msg)
            currentId = msg.parentId
        }
        val latestUserMsg = pathMessages.lastOrNull { it.participant == Participant.USER }
        if (latestUserMsg == null) {
            DebugLog.d(TAG, "collectTargets: no user msg in path -> no targets")
            return emptyList()
        }
        if (latestUserMsg.attachmentMeta.isNullOrBlank()) {
            DebugLog.d(TAG, "collectTargets: latestUserMsg has no attachmentMeta -> no targets (msg=${latestUserMsg.id.take(12)})")
            return emptyList()
        }
        val meta = AttachmentMeta.parse(latestUserMsg.attachmentMeta)
        if (meta == null) {
            DebugLog.w(TAG, "collectTargets: failed to parse attachmentMeta -> no targets. raw=${latestUserMsg.attachmentMeta?.take(300)}")
            return emptyList()
        }
        val targets = meta.items.mapIndexedNotNull { index, item ->
            if (item.type == "video" && item.videoTranscription.isNullOrBlank() && !item.originalUri.isNullOrBlank()) {
                NarrationTarget(latestUserMsg.id, item.originalUri, index)
            } else null
        }
        DebugLog.d(TAG, "collectTargets: items=${meta.items.size} videos=${meta.items.count { it.type == "video" }} targets(needs narration)=${targets.size}")
        targets.forEachIndexed { i, t ->
            DebugLog.d(TAG, "  target[$i] msg=${t.messageId.take(12)} metaIdx=${t.metaItemIndex} uri=${t.videoUri.take(80)}")
        }
        return targets
    }

    /**
     * Run video narration for all targets. Streams progress via [onProgress] and persists
     * the results back into the message attachment metadata.
     *
     * @return Pair of (segments list, error message or null)
     */
    suspend fun narrate(
        targets: List<NarrationTarget>,
        conversationId: String,
        providerName: String,
        modelId: String,
        apiKey: String,
        baseUrl: String?,
        prompt: String,
        fps: Float,
        detail: String,
        maxLongSide: Int,
        generationJob: Job?,
        modelMessageId: String,
        startTime: Long,
        onProgress: (ChatMessage) -> Unit
    ): Pair<List<MessageSegment>, String?> {
        DebugLog.d(TAG, "narrate START: targets=${targets.size} providers=${providers.keys} " +
            "reqProviderName='$providerName' modelId='$modelId' baseUrl='$baseUrl' apiKeyLen=${apiKey.length} " +
            "fps=$fps detail='$detail' maxLongSide=$maxLongSide")
        val provider = providers[providerName]
        if (provider == null) {
            DebugLog.w(TAG, "narrate: provider '$providerName' NOT in providers map (keys=${providers.keys}); falling back to first available")
        } else {
            DebugLog.d(TAG, "narrate: resolved provider=${provider.name} baseUrl=${baseUrl ?: provider.defaultBaseUrl}")
        }
        val providerInstance = provider ?: providers.values.first()
        val narrationConfig = ProviderConfig(
            apiKey = apiKey,
            modelId = modelId,
            systemPrompt = BuiltInPrompts.VIDEO_NARRATION_SYSTEM,
            thinkingEnabled = false,
            baseUrl = baseUrl,
            videoUrlOptions = OpenAiVideoUrl(
                url = "", // replaced per target below
                fps = fps,
                detail = detail,
                maxLongSidePixel = maxLongSide
            )
        )
        val placeholder = conversations.getMessagesForConversationSnapshot(conversationId).find { it.id == modelMessageId }
        val parentId = placeholder?.parentId
        val results = mutableMapOf<String, MutableList<Pair<Int, String>>>()
        val narrationSegments = mutableListOf<MessageSegment>()
        var processed = 0
        val total = targets.size

        for (target in targets) {
            if (generationJob?.isCancelled == true) throw CancellationException("Video narration cancelled")
            if (!currentCoroutineContext().isActive) throw CancellationException("Video narration cancelled")

            DebugLog.d(TAG, "narrate loop: ${processed + 1}/$total msg=${target.messageId.take(12)} idx=${target.metaItemIndex} uri=${target.videoUri.take(120)}")

            withContext(Dispatchers.Main) {
                OrangeIslandForegroundService.updateText(context.getString(R.string.video_narration_progress, processed + 1, total))
            }

            val currentSegment = MessageSegment(type = "video_transcription", content = "Narrating video...")
            narrationSegments.add(currentSegment)
            onProgress(ChatMessage(
                id = modelMessageId, parentId = parentId, text = "",
                participant = Participant.MODEL, status = MessageStatus.TRANSCRIBING, timestamp = startTime,
                retryText = "${processed + 1}/$total",
                thoughtTitle = "Video Narration",
                segments = narrationSegments.toList() + MessageSegment(type = "answer"),
            ))

            val promptMessages = listOf(ChatMessage(
                text = prompt.ifBlank { BuiltInPrompts.VIDEO_NARRATION_USER },
                videos = listOf(target.videoUri),
                participant = Participant.USER,
                status = MessageStatus.SUCCESS
            ))
            val narration = StringBuilder()
            var streamError: String? = null
            DebugLog.d(TAG, "narrate: calling generateResponse provider=${providerInstance.name} model='$modelId' baseUrl='$baseUrl'")
            providerInstance.generateResponse(promptMessages, narrationConfig).collect { event ->
                when (event) {
                    is StreamEvent.TextChunk -> {
                        narration.append(event.text)
                        narrationSegments[narrationSegments.lastIndex] = currentSegment.copy(content = narration.toString())
                        onProgress(ChatMessage(
                            id = modelMessageId, parentId = parentId, text = "",
                            participant = Participant.MODEL, status = MessageStatus.TRANSCRIBING, timestamp = startTime,
                            retryText = "${processed + 1}/$total",
                            thoughtTitle = "Video Narration",
                            segments = narrationSegments.toList() + MessageSegment(type = "answer"),
                        ))
                    }
                    is StreamEvent.Error -> {
                        DebugLog.e(TAG, "narrate stream ERROR for ${target.messageId.take(12)}: ${event.message}")
                        streamError = event.message
                    }
                    else -> {}
                }
            }
            DebugLog.d(TAG, "narrate loop done: ${processed + 1}/$total produced=${narration.length}chars error=${streamError != null}")
            if (streamError != null) {
                DebugLog.w(TAG, "narrate ABORTING early due to stream error")
                return Pair(narrationSegments, streamError)
            }
            val text = narration.toString().trim()
            narrationSegments[narrationSegments.lastIndex] = currentSegment.copy(content = text)
            results.getOrPut(target.messageId) { mutableListOf() }
                .add(target.metaItemIndex to text)
            processed++
        }

        // Persist results back to message attachment metadata
        for ((messageId, updates) in results) {
            val entity = conversations.getMessagesForConversationSnapshot(conversationId).find { it.id == messageId }
            if (entity != null) {
                val meta = AttachmentMeta.parse(entity.attachmentMeta) ?: AttachmentMeta()
                val items = meta.items.toMutableList()
                for ((index, text) in updates) {
                    if (index in items.indices) {
                        items[index] = items[index].copy(videoTranscription = text)
                    }
                }
                conversations.upsertMessage(entity.copy(
                    attachmentMeta = lenientJson.encodeToString(AttachmentMeta.serializer(), AttachmentMeta(items = items))
                ))
                DebugLog.d(TAG, "narrate persisted: msg=${messageId.take(12)} updatedFields=${updates.map { it.first }}")
            } else {
                DebugLog.w(TAG, "narrate persist SKIP: entity not found msg=${messageId.take(12)}")
            }
        }
        DebugLog.d(TAG, "narrate DONE OK: processed=$processed/$total")
        return Pair(narrationSegments, null)
    }

    companion object {
        private const val TAG = "VideoNarration"
        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}
