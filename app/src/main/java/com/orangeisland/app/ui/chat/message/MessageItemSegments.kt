package com.orangeisland.app.ui.chat.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment

internal fun mergeAdjacentSegments(segs: List<MessageSegment>): List<MessageSegment> {
    val merged = mutableListOf<MessageSegment>()
    for (seg in segs) {
        val last = merged.lastOrNull()
        // Only continuous answer/reasoning text is merged into one flowing block.
        // Transcriptions stay separate: each describes a distinct image, so a
        // 1:1 image↔block correspondence must be preserved.
        if (last != null && last.type == seg.type && (seg.type == "answer" || seg.type == "thought")) {
            merged[merged.lastIndex] = last.copy(
                content = last.content + seg.content,
                durationMs = mergeDurationMs(last.durationMs, seg.durationMs)
            )
        } else {
            merged.add(seg)
        }
    }
    return merged
}

private fun mergeDurationMs(first: Long?, second: Long?): Long? {
    val merged = (first ?: 0L) + (second ?: 0L)
    return merged.takeIf { it > 0L }
}

internal fun thoughtDurationMs(segs: List<MessageSegment>): Long? {
    return segs.sumOf { seg ->
        if (seg.type == "thought") seg.durationMs ?: 0L else 0L
    }.takeIf { it > 0L }
}

private fun MessageSegment.isBlankAnswerSegment(): Boolean =
    type == "answer" && content.isBlank()

internal fun MessageSegment.isVisibleAnswerSegment(): Boolean =
    type == "answer" && content.isNotBlank()

internal fun MessageSegment.isInfoSegment(): Boolean =
    type == "thought" || type == "tool" || type == "transcription"

internal fun ChatMessage.hasActiveAnswerSegment(): Boolean {
    val lastVisibleSegment = segments?.lastOrNull { !it.isBlankAnswerSegment() }
    return if (lastVisibleSegment != null) {
        lastVisibleSegment.isVisibleAnswerSegment()
    } else {
        text.isNotBlank()
    }
}

internal fun buildTimelineBlockKeys(
    messageId: String,
    segments: List<MessageSegment>,
    groupAdjacentBlocks: Boolean
): Set<String> {
    val keys = linkedSetOf<String>()
    var detailIndex = 0
    var index = 0
    while (index < segments.size) {
        val seg = segments[index]
        when {
            seg.type == "answer" -> {
                index++
            }
            seg.isInfoSegment() -> {
                if (groupAdjacentBlocks) {
                    var blockEnd = index
                    var firstDetailIndex: Int? = null
                    while (blockEnd < segments.size && !segments[blockEnd].isVisibleAnswerSegment()) {
                        val blockSeg = segments[blockEnd]
                        if (blockSeg.isInfoSegment()) {
                            if (firstDetailIndex == null) firstDetailIndex = detailIndex
                            detailIndex++
                        }
                        blockEnd++
                    }
                    keys += "$messageId:group:${firstDetailIndex ?: index}"
                    index = blockEnd
                } else {
                    keys += "$messageId:timeline:$detailIndex"
                    detailIndex++
                    index++
                }
            }
            else -> {
                index++
            }
        }
    }
    return keys
}

@Composable
internal fun AnimatedTimelineBlockAppearance(
    animationKey: String,
    animate: Boolean,
    content: @Composable () -> Unit
) {
    key(animationKey) {
        // Capture the decision on first composition. The caller flips `animate` to false on
        // the very next recomposition (the block/item is marked "seen"), and during streaming
        // that next frame can arrive before the enter animation finishes — so reading `animate`
        // live would tear the AnimatedVisibility down and the content would just snap in.
        val play = remember { animate }
        if (!play) {
            content()
        } else {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            AnimatedVisibility(
                visible = visible,
                // Fast-in, slow-out (decelerate) alpha + scale appearance.
                enter = fadeIn(tween(350, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(350, easing = LinearOutSlowInEasing)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                content()
            }
        }
    }
}

// Label a transcription segment; numbers them ("Image Transcription 1/2/…") only
// when more than one is present, so a single image keeps the clean unnumbered name.
internal fun transcriptionLabel(segs: List<MessageSegment>, index: Int): String {
    val total = segs.count { it.type == "transcription" }
    if (total <= 1) return "Image Transcription"
    val ordinal = segs.take(index + 1).count { it.type == "transcription" }
    return "Image Transcription $ordinal"
}
