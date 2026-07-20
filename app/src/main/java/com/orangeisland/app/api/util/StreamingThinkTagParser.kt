package com.orangeisland.app.api.util

class StreamingThinkTagParser {
    var inThinkingBlock = false
    var pendingBuffer = ""
    // Only honor the first <think> block to avoid swallowing literal "<think>" in
    // normal text as a thinking delimiter. If models later adopt interleaved /
    // adaptive thinking via inline tags, this guard will need to be removed — but
    // those models will likely expose reasoning through structured API fields
    // (reasoning_content, thought flag, Anthropic content blocks) instead.
    private var hasExitedThinkBlock = false

    suspend fun feed(
        content: String,
        thinkingEnabled: Boolean,
        onText: suspend (String) -> Unit,
        onThought: suspend (String) -> Unit
    ) {
        pendingBuffer += content

        while (pendingBuffer.isNotEmpty()) {
            if (!inThinkingBlock) {
                val startIdx = if (!hasExitedThinkBlock) pendingBuffer.indexOf("<think>") else -1
                if (startIdx != -1) {
                    val before = pendingBuffer.substring(0, startIdx)
                    if (before.isNotEmpty()) onText(before)
                    inThinkingBlock = true
                    pendingBuffer = pendingBuffer.substring(startIdx + 7)
                } else {
                    val lastBracket = pendingBuffer.lastIndexOf('<')
                    if (!hasExitedThinkBlock && lastBracket != -1 && "<think>".startsWith(pendingBuffer.substring(lastBracket))) {
                        val before = pendingBuffer.substring(0, lastBracket)
                        if (before.isNotEmpty()) onText(before)
                        pendingBuffer = pendingBuffer.substring(lastBracket)
                        break
                    } else {
                        onText(pendingBuffer)
                        pendingBuffer = ""
                    }
                }
            } else {
                val endIdx = pendingBuffer.indexOf("</think>")
                if (endIdx != -1) {
                    val thought = pendingBuffer.substring(0, endIdx)
                    if (thought.isNotEmpty() && thinkingEnabled) onThought(thought)
                    inThinkingBlock = false
                    hasExitedThinkBlock = true
                    pendingBuffer = pendingBuffer.substring(endIdx + 8)
                } else {
                    val lastBracket = pendingBuffer.lastIndexOf('<')
                    if (lastBracket != -1 && "</think>".startsWith(pendingBuffer.substring(lastBracket))) {
                        val before = pendingBuffer.substring(0, lastBracket)
                        if (before.isNotEmpty() && thinkingEnabled) onThought(before)
                        pendingBuffer = pendingBuffer.substring(lastBracket)
                        break
                    } else {
                        if (thinkingEnabled) onThought(pendingBuffer)
                        pendingBuffer = ""
                    }
                }
            }
        }
    }

    suspend fun flush(
        onText: suspend (String) -> Unit,
        onThought: suspend (String) -> Unit
    ) {
        if (pendingBuffer.isNotEmpty()) {
            if (inThinkingBlock) onThought(pendingBuffer)
            else onText(pendingBuffer)
            pendingBuffer = ""
        }
    }
}
