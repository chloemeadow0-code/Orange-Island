package com.orangeisland.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class BlobSpec(
    val centerXFrac: Float,
    val centerYFrac: Float,
    val radiusDp: Float,
    val xAmp: Float,
    val yAmp: Float,
    val xPeriodSec: Float,
    val yPeriodSec: Float,
)

@Composable
fun AnimatedBlobBackground(
    modifier: Modifier = Modifier,
    blurRadius: Float = 40f,
    centerAlpha: Float = 0.10f,
    quarterAlpha: Float = 0.05f,
    edgeAlpha: Float = 0.0f,
    dark: Boolean = true
) {
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val blobColor = if (dark) Color(
        red = lerp(cs.primaryContainer.red, cs.primary.red, 0.3f) * 0.5f,
        green = lerp(cs.primaryContainer.green, cs.primary.green, 0.3f) * 0.5f,
        blue = lerp(cs.primaryContainer.blue, cs.primary.blue, 0.3f) * 0.5f,
        alpha = cs.primaryContainer.alpha,
    ) else Color(
        red = lerp(cs.background.red, cs.primary.red, 0.2f),
        green = lerp(cs.background.green, cs.primary.green, 0.2f),
        blue = lerp(cs.background.blue, cs.primary.blue, 0.2f),
        alpha = cs.background.alpha,
    )
    val blobColors = List(4) { blobColor }

    val blobs = remember {
        val rng = Random(System.nanoTime())
        List(4) {
            BlobSpec(
                centerXFrac = rng.nextFloat() * 0.8f + 0.1f,
                centerYFrac = rng.nextFloat() * 0.7f + 0.15f,
                radiusDp = rng.nextFloat() * 40f + 180f,
                xAmp = rng.nextFloat() * 0.08f + 0.06f,
                yAmp = rng.nextFloat() * 0.08f + 0.06f,
                xPeriodSec = rng.nextFloat() * 12f + 10f,
                yPeriodSec = rng.nextFloat() * 12f + 8f,
            )
        }
    }

    var timeSec by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        val startNanos = System.nanoTime()
        while (true) {
            timeSec = (System.nanoTime() - startNanos) / 1_000_000_000.0
            delay(16L)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = blurRadius.dp)
        ) {
            val w = size.width
            val h = size.height
            val t = timeSec

            blobs.forEachIndexed { i, blob ->
                val phase = i.toDouble() * 1.3
                val xFrac = blob.centerXFrac + blob.xAmp * sin(t / blob.xPeriodSec * 2.0 * PI + phase).toFloat()
                val yFrac = blob.centerYFrac + blob.yAmp * cos(t / blob.yPeriodSec * 2.0 * PI + phase).toFloat()
                val cx = w * xFrac
                val cy = h * yFrac
                val r = blob.radiusDp * density.density

                val color = blobColors[i]
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to color.copy(alpha = centerAlpha),
                        0.25f to color.copy(alpha = quarterAlpha),
                        1.0f to color.copy(alpha = edgeAlpha),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().alpha(0.12f)) {
            val primary = blobColors[0]
            val tertiary = blobColors[2]
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to primary.copy(alpha = 0.6f),
                    0.5f to tertiary.copy(alpha = 0.3f),
                    1.0f to primary.copy(alpha = 0.6f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                )
            )
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color.Transparent,
                    1.0f to primary.copy(alpha = 0.2f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                )
            )
        }
    }
}
