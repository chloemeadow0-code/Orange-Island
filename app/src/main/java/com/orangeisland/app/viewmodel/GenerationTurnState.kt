package com.orangeisland.app.viewmodel

import android.content.Context
import com.orangeisland.app.R
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.ToolCallData
import com.orangeisland.app.util.Constants

/**
 * Mutable state for a single [GenerationManager.generate] turn, plus the stream-event
 * handling and segment-flushing logic that reads/writes it. Extracted purely to shrink the
 * compiled size/complexity of [GenerationManager.generate] (see build-time bytecode issue) —
 * one instance per generate() call, discarded when the call returns.
 */
class GenerationTurnState(
    private val context: Context,
    val modelMessageId: String,
    val parentId: String?,
    val startTime: Long,
    val modelName: String,
    private val onStreamUpdate: (ChatMessage) -> Unit,
    private val onTitleTriggerReady: ((String, String) -> Unit)?,
    private val executeTool: suspend (name: String, arguments: String) -> String,
    private val drainGeneratedImages: () -> List<String>,
    private val drainAudio: () -> List<String>,
) {
    private val totalTextBuilder = StringBuilder()
    var totalText: String
        get() = totalTextBuilder.toString()
        set(value) {
            totalTextBuilder.setLength(0)
            totalTextBuilder.append(value)
        }

    private val totalThoughtsBuilder = StringBuilder()
    private var thoughtPlaceholderActive = false
    private val thinkingPlaceholder = context.getString(R.string.thinking_ellipsis)
    var totalThoughts: String
        get() = when {
            totalThoughtsBuilder.isNotEmpty() -> totalThoughtsBuilder.toString()
            thoughtPlaceholderActive -> thinkingPlaceholder
            else -> ""
        }
        set(value) {
            totalThoughtsBuilder.setLength(0)
            totalThoughtsBuilder.append(value)
            thoughtPlaceholderActive = false
        }

    var totalThoughtTitle: String? = null
    var totalTokenCount: Int = 0
    var totalCachedTokenCount: Int = 0
    var contextMessageCount: Int = 0
    var totalThoughtTimeMs: Long? = null
    private var cumulativeThoughtMs: Long = 0
    private var currentThoughtStartMs: Long? = null
    private var currentThoughtDurationMs: Long = 0
    var currentStatus: MessageStatus = MessageStatus.SENDING
    var retryText: String? = null
    val segments: MutableList<MessageSegment> = mutableListOf(MessageSegment(type = "answer"))
    val generatedImages: MutableList<String> = mutableListOf()
    val generatedAudio: MutableList<String> = mutableListOf()
    private var currentAnswerBuf = StringBuilder()
    private var currentThoughtBuf = StringBuilder()
    private var currentThoughtSignature: String? = null

    var toolCallData: ToolCallData? = null
    var toolCallDataList: List<ToolCallData> = emptyList()
    val roundToolSegments: MutableList<MessageSegment> = mutableListOf()

    var titleTriggerFired: Boolean = false
    var streamStartMs: Long = 0L
    var lastEmitMs: Long = 0L

    fun liveThoughtDurationMs(): Long? {
        val liveElapsed = currentThoughtStartMs?.let { System.currentTimeMillis() - it } ?: 0L
        return (currentThoughtDurationMs + liveElapsed).takeIf { it > 0L }
    }

    fun finishCurrentThoughtTiming() {
        val startedAt = currentThoughtStartMs ?: return
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed > 0L) {
            cumulativeThoughtMs += elapsed
            currentThoughtDurationMs += elapsed
            totalThoughtTimeMs = cumulativeThoughtMs
        }
        currentThoughtStartMs = null
    }

    private fun appendMergedSegment(target: MutableList<MessageSegment>, segment: MessageSegment) {
        val last = target.lastOrNull()
        if (last != null && last.type == segment.type && (segment.type == "answer" || segment.type == "thought")) {
            target[target.lastIndex] = last.copy(
                content = last.content + segment.content,
                signature = segment.signature ?: last.signature,
                durationMs = mergeDurationMs(last.durationMs, segment.durationMs)
            )
        } else {
            target.add(segment)
        }
    }

    private fun mergeDurationMs(first: Long?, second: Long?): Long? {
        val merged = (first ?: 0L) + (second ?: 0L)
        return merged.takeIf { it > 0 }
    }

    private fun buildLiveSegments(thoughtDurationMs: Long?): List<MessageSegment>? {
        val result = segments.toMutableList()
        if (currentAnswerBuf.isNotEmpty()) {
            appendMergedSegment(result, MessageSegment(type = "answer", content = currentAnswerBuf.toString()))
        }
        if (currentThoughtBuf.isNotEmpty()) {
            appendMergedSegment(result, MessageSegment(
                type = "thought",
                content = currentThoughtBuf.toString(),
                signature = currentThoughtSignature,
                durationMs = thoughtDurationMs
            ))
        }
        return result.ifEmpty { null }
    }

    fun modelMessage(): ChatMessage = ChatMessage(
        id = modelMessageId, parentId = parentId,
        text = totalText, thoughts = totalThoughts.ifBlank { null },
        thoughtTitle = totalThoughtTitle, tokenCount = totalTokenCount,
        cachedTokenCount = totalCachedTokenCount,
        contextMessageCount = contextMessageCount,
        status = currentStatus, participant = Participant.MODEL,
        timestamp = startTime, thoughtTimeMs = totalThoughtTimeMs,
        generationDurationMs = System.currentTimeMillis() - startTime,
        modelName = modelName, toolCall = toolCallData,
        images = generatedImages.toList(),
        audio = generatedAudio.toList(),
        segments = buildLiveSegments(liveThoughtDurationMs()),
        retryText = retryText
    )

    /** Snapshot used by [GenerationManager]'s finally-block persistence. Must be called AFTER
     *  [finishCurrentThoughtTiming] (it calls it itself), matching the original inline order. */
    fun finalSegments(): List<MessageSegment>? {
        finishCurrentThoughtTiming()
        return buildLiveSegments(currentThoughtDurationMs.takeIf { it > 0L })
            ?: segments.toList().ifEmpty { null }
    }

    fun emitCurrent() {
        onStreamUpdate(modelMessage())
    }

    fun flushAnswerSegment() {
        if (currentAnswerBuf.isNotEmpty()) {
            appendMergedSegment(segments, MessageSegment(type = "answer", content = currentAnswerBuf.toString()))
            currentAnswerBuf = StringBuilder()
        }
    }

    fun flushThoughtSegment() {
        finishCurrentThoughtTiming()
        if (currentThoughtBuf.isNotEmpty()) {
            appendMergedSegment(segments, MessageSegment(
                type = "thought",
                content = currentThoughtBuf.toString(),
                signature = currentThoughtSignature,
                durationMs = currentThoughtDurationMs.takeIf { it > 0L }
            ))
            currentThoughtBuf = StringBuilder()
            currentThoughtSignature = null
        }
        currentThoughtDurationMs = 0L
    }

    suspend fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.TextChunk -> {
                val answerText = if (currentStatus == MessageStatus.THINKING) event.text.trimStart() else event.text
                if (currentStatus == MessageStatus.THINKING && answerText.isBlank()) {
                    retryText = null
                    return
                }
                if (currentStatus == MessageStatus.THINKING) {
                    flushThoughtSegment()
                }
                totalTextBuilder.append(answerText)
                currentAnswerBuf.append(answerText)
                if (answerText.isNotBlank()) {
                    currentStatus = MessageStatus.SENDING
                }
                retryText = null
            }
            is StreamEvent.ThoughtChunk -> {
                flushAnswerSegment()
                currentStatus = MessageStatus.THINKING
                retryText = null
                if (currentThoughtStartMs == null) {
                    currentThoughtStartMs = System.currentTimeMillis()
                }
                if (totalThoughtsBuilder.isEmpty() && !thoughtPlaceholderActive) {
                    thoughtPlaceholderActive = true
                }
                if (event.thought.isNotEmpty()) {
                    currentThoughtBuf.append(event.thought)
                    totalThoughtsBuilder.append(event.thought)
                    thoughtPlaceholderActive = false
                }
                if (event.title != null) totalThoughtTitle = event.title
                if (event.signature != null) currentThoughtSignature = event.signature
            }
            is StreamEvent.UsageUpdate -> {
                if (event.tokenCount > 0) totalTokenCount = event.tokenCount
                if (event.cachedTokenCount > 0) totalCachedTokenCount = event.cachedTokenCount
                if (totalTextBuilder.isEmpty() && event.thoughtsTokenCount > 0) {
                    currentStatus = MessageStatus.THINKING
                    if (currentThoughtStartMs == null) {
                        currentThoughtStartMs = System.currentTimeMillis()
                    }
                    if (totalThoughtsBuilder.isEmpty() && !thoughtPlaceholderActive) {
                        thoughtPlaceholderActive = true
                    }
                }
            }
            is StreamEvent.Retrying -> {
                retryText = context.getString(R.string.generation_retry_attempt, event.attempt, event.maxAttempts)
                emitCurrent()
            }
            is StreamEvent.Error -> {
                flushThoughtSegment()
                retryText = null
                if (toolCallData == null && toolCallDataList.isEmpty()) {
                    totalText = event.message
                    currentStatus = MessageStatus.ERROR
                }
            }
            is StreamEvent.ToolCallRequest -> {
                com.orangeisland.app.util.DebugLog.d("ToolEvt", "ToolCallRequest name=${event.name} id=${event.id}")
                flushAnswerSegment()
                flushThoughtSegment()
                val ts = MessageSegment(type = "tool", toolName = event.name, toolArgs = event.arguments, toolResult = null, toolCallId = event.id, signature = event.signature)
                appendMergedSegment(segments, ts)
                currentStatus = MessageStatus.TOOL_CALLING
                emitCurrent()
                lastEmitMs = System.currentTimeMillis()
                val result = executeTool(event.name, event.arguments)
                com.orangeisland.app.util.DebugLog.d("ToolEvt", "executeTool returned len=${result.length} for ${event.name}")
                val drainedImages = drainGeneratedImages()
                val drainedAudio = drainAudio()
                generatedImages.addAll(drainedImages)
                generatedAudio.addAll(drainedAudio)
                val clipped = result.take(Constants.MAX_TOOL_RESULT_LENGTH)
                val idx = segments.indexOfLast { it.toolCallId == event.id }
                if (idx >= 0) {
                    segments[idx] = segments[idx].copy(
                        toolResult = clipped,
                        audioPath = drainedAudio.firstOrNull(),
                        imagePath = drainedImages.firstOrNull()
                    )
                    roundToolSegments.add(segments[idx])
                }
                val tcd = ToolCallData(event.name, event.arguments, clipped, event.signature, event.id)
                if (toolCallData == null) toolCallData = tcd
                toolCallDataList = toolCallDataList + tcd
                currentStatus = MessageStatus.SENDING
            }
            is StreamEvent.ToolCallsRequest -> {
                com.orangeisland.app.util.DebugLog.d("ToolEvt", "ToolCallsRequest count=${event.calls.size} names=[${event.calls.joinToString { it.name }}]")
                flushAnswerSegment()
                flushThoughtSegment()
                event.calls.forEach { call ->
                    appendMergedSegment(segments, MessageSegment(type = "tool", toolName = call.name, toolArgs = call.arguments, toolResult = null, toolCallId = call.id, signature = call.signature))
                }
                currentStatus = MessageStatus.TOOL_CALLING
                emitCurrent()
                lastEmitMs = System.currentTimeMillis()
                val tcds = event.calls.map { call ->
                    val result = executeTool(call.name, call.arguments)
                    val drainedImages = drainGeneratedImages()
                    val drainedAudio = drainAudio()
                    generatedImages.addAll(drainedImages)
                    generatedAudio.addAll(drainedAudio)
                    val clipped = result.take(Constants.MAX_TOOL_RESULT_LENGTH)
                    val idx = segments.indexOfLast { it.toolCallId == call.id }
                    if (idx >= 0) {
                        segments[idx] = segments[idx].copy(
                            toolResult = clipped,
                            audioPath = drainedAudio.firstOrNull(),
                            imagePath = drainedImages.firstOrNull()
                        )
                        roundToolSegments.add(segments[idx])
                    }
                    ToolCallData(call.name, call.arguments, clipped, call.signature, call.id)
                }
                toolCallData = tcds.firstOrNull()
                toolCallDataList = tcds
                currentStatus = MessageStatus.SENDING
            }
        }

        if (!titleTriggerFired && onTitleTriggerReady != null) {
            val elapsed = System.currentTimeMillis() - streamStartMs
            val totalThoughtsLength = if (totalThoughtsBuilder.isNotEmpty()) totalThoughtsBuilder.length
                else if (thoughtPlaceholderActive) thinkingPlaceholder.length else 0
            val totalContentLength = totalTextBuilder.length + totalThoughtsLength
            if (totalContentLength >= 100 || elapsed >= 6000) {
                titleTriggerFired = true
                onTitleTriggerReady.invoke(totalText, totalThoughts)
            }
        }

        val now = System.currentTimeMillis()
        val isSignificant = event is StreamEvent.Error
        if (now - lastEmitMs >= 300 || isSignificant) {
            emitCurrent()
            lastEmitMs = now
        }
    }
}
