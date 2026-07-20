package com.orangeisland.app.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    typeSpeedMs: Int = 100,
    deleteSpeedMs: Int = 50,
    pauseAfterTypeMs: Int = 2000,
    pauseAfterDeleteMs: Int = 600
) {
    var visibleCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        while (true) {
            for (i in 1..text.length) {
                visibleCount = i
                delay(typeSpeedMs.toLong())
            }
            delay(pauseAfterTypeMs.toLong())
            for (i in text.length - 1 downTo 0) {
                visibleCount = i
                delay(deleteSpeedMs.toLong())
            }
            delay(pauseAfterDeleteMs.toLong())
        }
    }

    val cursorAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Row(modifier = modifier) {
        Text(
            text = text.take(visibleCount),
            style = style,
            fontWeight = fontWeight,
            color = color
        )
        Text(
            text = "|",
            style = style,
            fontWeight = fontWeight,
            color = color.copy(alpha = cursorAlpha),
            modifier = Modifier.alpha(cursorAlpha)
        )
    }
}
