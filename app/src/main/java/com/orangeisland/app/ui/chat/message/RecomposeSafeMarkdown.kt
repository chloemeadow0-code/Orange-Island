package com.orangeisland.app.ui.chat.message

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import kotlinx.coroutines.channels.Channel

/**
 * Double-buffered crossfade Markdown composable.
 * Prevents visual "flash" from AST re-parsing during streaming by maintaining
 * two content buffers and crossfading between them over ~180ms.
 *
 * The state machine is driven by a [Channel] queue so that content changes
 * arriving while a fade is still running are not dropped; they are simply
 * processed in order once the current fade finishes.  No state variable that
 * is mutated inside an effect is ever used as that effect's key, which
 * eliminates the race where rapid streaming updates caused the buffers to
 * flip backwards and create the "content flashes then disappears" bug.
 */
@Composable
internal fun RecomposeSafeMarkdown(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    render: @Composable (text: String) -> Unit
) {
    var buf0 by remember { mutableStateOf("") }
    var buf1 by remember { mutableStateOf("") }
    var front by remember { mutableIntStateOf(0) }
    var fadeAlpha by remember { mutableFloatStateOf(0f) }

    val currentContent by rememberUpdatedState(content)
    val currentIsStreaming by rememberUpdatedState(isStreaming)
    val fadeChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    // Detect content changes and enqueue a fade.  Non-streaming content is
    // written straight into the front buffer so settled messages render
    // immediately without any cross-fade overhead.
    LaunchedEffect(content, isStreaming) {
        if (!currentIsStreaming) {
            // Non-streaming: always render from buf0, clear buf1, reset fade.
            // Also drain any pending fade signal so the fade effect does not
            // flip front after we have already settled on buf0.
            buf0 = currentContent
            buf1 = ""
            front = 0
            fadeAlpha = 0f
            fadeChannel.tryReceive()
        } else {
            val cur = if (front == 0) buf0 else buf1
            if (currentContent != cur) {
                fadeChannel.trySend(Unit)
            }
        }
    }

    // Consume the fade queue.  Because the channel is CONFLATED, multiple
    // rapid updates collapse to a single pending signal; the loop simply
    // catches up with the latest content once the current fade ends.
    LaunchedEffect(Unit) {
        for (_signal in fadeChannel) {
            // If we have already switched to non-streaming, abort immediately.
            if (!currentIsStreaming) {
                fadeAlpha = 0f
                continue
            }

            val incoming = 1 - front
            if (front == 0) buf1 = currentContent else buf0 = currentContent

            val startNs = withFrameNanos { it }
            val durationNs = 180_000_000L
            while (true) {
                val nowNs = withFrameNanos { it }
                val p = ((nowNs - startNs).toFloat() / durationNs).coerceAtMost(1f)
                fadeAlpha = p
                if (p >= 1f) break
                // Abort early if streaming ended mid-fade.
                if (!currentIsStreaming) {
                    fadeAlpha = 0f
                    break
                }
            }
            if (currentIsStreaming) {
                front = incoming
            }
            fadeAlpha = 0f
        }
    }

    // Visibility / z-order: symmetric for both buffers
    val isFading = fadeAlpha > 0f
    val incoming = 1 - front
    val z0 = when {
        isFading && incoming == 0 -> 2f
        isFading && front == 0 -> 0f
        front == 0 -> 2f
        else -> 0f
    }
    val a0 = when {
        isFading && incoming == 0 -> fadeAlpha
        isFading && front == 0 -> 1f
        front == 0 -> 1f
        else -> 0f
    }
    val z1 = when {
        isFading && incoming == 1 -> 2f
        isFading && front == 1 -> 0f
        front == 1 -> 2f
        else -> 0f
    }
    val a1 = when {
        isFading && incoming == 1 -> fadeAlpha
        isFading && front == 1 -> 1f
        front == 1 -> 1f
        else -> 0f
    }

    Box(modifier = modifier) {
        if (buf0.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().zIndex(z0).alpha(a0)) { render(buf0) }
        }
        if (buf1.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().zIndex(z1).alpha(a1)) { render(buf1) }
        }
    }
}
