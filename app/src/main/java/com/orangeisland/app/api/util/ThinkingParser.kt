package com.orangeisland.app.api.util

/**
 * Unified thinking/reasoning content parser that handles multiple model formats:
 *
 *   Format A — XML-style (Qwen3, DeepSeek-R1, etc.):
 *     <think>reasoning content</think> actual text
 *
 *   Format B — Channel markers (Gemma 4):
 *     <|channel>thought
 *     reasoning content
 *     <channel|>
 *     actual text
 *
 * Call feed() with each token, then flush() at end-of-stream.
 */
class ThinkingParser {
    // State
    private var inThinking = false
    private var inDiscarding = false  // thinking disabled — skip until end marker
    private var pending = ""
    private var thinkingFormat: ThinkingFormat? = null

    // Per-format detection config
    private enum class ThinkingFormat(val start: String, val end: String) {
        XML("<think>", "</think>"),
        CHANNEL("<|channel>thought\n", "<channel|>")
    }

    // ── feed a raw token ──────────────────────────────────────────
    suspend fun feed(
        content: String,
        thinkingEnabled: Boolean,
        onText: suspend (String) -> Unit,
        onThought: suspend (String) -> Unit
    ) {
        pending += content

        while (pending.isNotEmpty()) {
            // ── Discarding mode: skip everything until end marker ──
            if (inDiscarding) {
                val endMarker = thinkingFormat!!.end
                val endIdx = pending.indexOf(endMarker)

                if (endIdx != -1) {
                    // Found end — discard everything up to and including end marker
                    pending = pending.substring(endIdx + endMarker.length)
                    inDiscarding = false
                    thinkingFormat = null
                } else {
                    // Check for partial end marker at the end
                    val lastCh = pending.lastIndexOf(endMarker[0])
                    if (lastCh != -1 && endMarker.startsWith(pending.substring(lastCh))) {
                        pending = pending.substring(lastCh)
                    } else {
                        pending = "" // discard all
                    }
                    break
                }
            }
            // ── Normal mode: scan for thinking start ──
            else if (!inThinking) {
                // ── Scan for any thinking start marker ──
                var bestStart = -1
                var bestFmt: ThinkingFormat? = null

                for (fmt in ThinkingFormat.entries) {
                    val idx = pending.indexOf(fmt.start)
                    if (idx != -1 && (bestStart == -1 || idx < bestStart)) {
                        bestStart = idx
                        bestFmt = fmt
                    }
                    // Also check partial match at the end
                    val lastIdx = pending.lastIndexOf(fmt.start[0])
                    if (lastIdx != -1 && fmt.start.startsWith(pending.substring(lastIdx))) {
                        if (bestStart == -1 || lastIdx < bestStart) {
                            bestStart = lastIdx
                            bestFmt = null // partial match only, don't commit yet
                        }
                    }
                }

                if (bestStart != -1 && bestFmt != null) {
                    // Full match found
                    val before = pending.substring(0, bestStart)
                    if (before.isNotEmpty()) onText(before)
                    pending = pending.substring(bestStart + bestFmt.start.length)
                    if (thinkingEnabled) {
                        inThinking = true
                        thinkingFormat = bestFmt
                    } else {
                        // Thinking disabled — enter discarding mode, skip until end tag
                        inDiscarding = true
                        thinkingFormat = bestFmt
                    }
                    continue
                } else if (bestStart != -1 && bestFmt == null) {
                    // Partial match at end — keep in buffer
                    val before = pending.substring(0, bestStart)
                    if (before.isNotEmpty()) onText(before)
                    pending = pending.substring(bestStart)
                    break
                } else {
                    // No match — emit all
                    onText(pending)
                    pending = ""
                }
            } else {
                // ── Inside thinking block, scan for end marker ──
                val endMarker = thinkingFormat!!.end
                val endIdx = pending.indexOf(endMarker)

                if (endIdx != -1) {
                    val thought = pending.substring(0, endIdx)
                    if (thought.isNotEmpty()) onThought(thought)
                    pending = pending.substring(endIdx + endMarker.length)
                    inThinking = false
                    thinkingFormat = null
                } else {
                    // Check for partial end marker at the end
                    val lastCh = pending.lastIndexOf(endMarker[0])
                    if (lastCh != -1 && endMarker.startsWith(pending.substring(lastCh))) {
                        val before = pending.substring(0, lastCh)
                        if (before.isNotEmpty()) onThought(before)
                        pending = pending.substring(lastCh)
                        break
                    } else {
                        onThought(pending)
                        pending = ""
                    }
                }
            }
        }
    }

    // ── flush remaining buffer ────────────────────────────────────
    suspend fun flush(
        onText: suspend (String) -> Unit,
        onThought: suspend (String) -> Unit
    ) {
        if (pending.isNotEmpty() && !inDiscarding) {
            if (inThinking) onThought(pending)
            else onText(pending)
        }
        pending = ""
    }

    // ── reset for reuse ───────────────────────────────────────────
    fun reset() {
        inThinking = false
        inDiscarding = false
        pending = ""
        thinkingFormat = null
    }
}
