package com.orangeisland.app.util

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

private const val MAX_GRADIENT_BLUR_DP = 5f

/**
 * Two-pass separable Gaussian-ish blur whose radius varies along the Y axis.
 *
 * The shader samples the input texture N times with increasing offsets controlled
 * by [blurAtTop] (top blur radius in px) and [blurAtBottom] (bottom blur radius in px).
 * Intermediate rows are linearly interpolated.
 *
 * Requires Android 13+ (API 33). Falls back to no-op on older devices.
 *
 * @param blurAtTop    blur radius in density-independent pixels at the top edge.
 * @param blurAtBottom blur radius in density-independent pixels at the bottom edge.
 */
fun Modifier.gradientBlur(blurAtTopDp: Float, blurAtBottomDp: Float): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val density = LocalDensity.current.density
    val maxBlurPx = maxOf(blurAtTopDp, blurAtBottomDp)
        .coerceAtMost(MAX_GRADIENT_BLUR_DP) * density
    if (maxBlurPx <= 0f) return@composed this

    val fadeRangePx = 150f * density

    val horizontalShader = remember(maxBlurPx, fadeRangePx) {
        RuntimeShader(VARIABLE_BLUR_SHADER).apply {
            setFloatUniform("uParams", maxBlurPx, fadeRangePx)
            setFloatUniform("uDirection", 1f, 0f)
        }
    }
    val verticalShader = remember(maxBlurPx, fadeRangePx) {
        RuntimeShader(VARIABLE_BLUR_SHADER).apply {
            setFloatUniform("uParams", maxBlurPx, fadeRangePx)
            setFloatUniform("uDirection", 0f, 1f)
        }
    }

    val renderEffect = remember(horizontalShader, verticalShader) {
        val horizontal = RenderEffect.createRuntimeShaderEffect(horizontalShader, "content")
        val vertical = RenderEffect.createRuntimeShaderEffect(verticalShader, "content")
        RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
    }

    Modifier.graphicsLayer { this.renderEffect = renderEffect }
}

/**
 * Gradient blur with edge fade at both top and bottom.
 * Blur ramps from 0 at the edge to [maxBlurDp] over [edgeFadeDp] distance.
 */
fun Modifier.gradientBlurEdges(maxBlurDp: Float, edgeFadeDp: Float = 20f, topWeight: Float = 1f, bottomWeight: Float = 1f): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this
    if (topWeight <= 0f && bottomWeight <= 0f) return@composed this

    val density = LocalDensity.current.density
    val maxBlurPx = maxBlurDp.coerceAtMost(MAX_GRADIENT_BLUR_DP) * density
    if (maxBlurPx <= 0f) return@composed this

    val fadePx = edgeFadeDp * density
    var composableHPx by remember { mutableFloatStateOf(2400f) }

    val horizontalShader = remember(maxBlurPx, fadePx, topWeight, bottomWeight, composableHPx) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uMaxBlur", maxBlurPx)
            setFloatUniform("uFade", fadePx)
            setFloatUniform("uH", composableHPx)
            setFloatUniform("uWeights", topWeight, bottomWeight)
            setFloatUniform("uDirection", 1f, 0f)
        }
    }
    val verticalShader = remember(maxBlurPx, fadePx, topWeight, bottomWeight, composableHPx) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uMaxBlur", maxBlurPx)
            setFloatUniform("uFade", fadePx)
            setFloatUniform("uH", composableHPx)
            setFloatUniform("uWeights", topWeight, bottomWeight)
            setFloatUniform("uDirection", 0f, 1f)
        }
    }

    val renderEffect = remember(horizontalShader, verticalShader) {
        val horizontal = RenderEffect.createRuntimeShaderEffect(horizontalShader, "content")
        val vertical = RenderEffect.createRuntimeShaderEffect(verticalShader, "content")
        RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
    }

    Modifier
        .onSizeChanged { composableHPx = it.height.toFloat() }
        .graphicsLayer { this.renderEffect = renderEffect }
}

/**
 * Sets both top and bottom blur to the same value (uniform blur).
 */
fun Modifier.gradientBlur(radiusDp: Float): Modifier =
    gradientBlur(radiusDp, radiusDp)

/**
 * Fades content alpha near the top and bottom edges.
 *
 * Use this for scrollable list edges when the goal is to soften clipping rather
 * than optically blur the pixels. It avoids the per-pixel sampling cost of blur.
 */
fun Modifier.verticalEdgeFade(edgeFadeDp: Float = 20f, topWeight: Float = 1f, bottomWeight: Float = 1f): Modifier = composed {
    val density = LocalDensity.current.density
    val fadePx = edgeFadeDp.coerceAtLeast(0f) * density
    val topAlpha = topWeight.coerceIn(0f, 1f)
    val bottomAlpha = bottomWeight.coerceIn(0f, 1f)

    if (fadePx <= 0f || (topAlpha <= 0f && bottomAlpha <= 0f)) return@composed this

    Modifier
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            drawContent()

            val height = size.height.coerceAtLeast(1f)
            val normalizedFade = (fadePx / height).coerceIn(0f, 0.5f)
            val topFadeEnd = normalizedFade
            val bottomFadeStart = 1f - normalizedFade
            val opaque = Color.Black

            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to opaque.copy(alpha = 1f - topAlpha),
                        topFadeEnd to opaque,
                        bottomFadeStart to opaque,
                        1f to opaque.copy(alpha = 1f - bottomAlpha)
                    )
                ),
                blendMode = BlendMode.DstIn
            )
        }
}

/**
 * Gradient blur using a fixed 9-tap separable kernel.
 *
 * The previous shader used a dense 2D grid and evaluated exp() for every tap,
 * which was much too expensive for a scrolling list. This version chains a
 * horizontal pass and a vertical pass. Each pass samples 9 texels with constant
 * Gaussian weights, so the total drops from hundreds of texture reads per pixel
 * to 18 reads with no dynamic loops.
 */
private val VARIABLE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uParams;       // x = maxBlur (px), y = fadeRange (px)
    uniform float2 uDirection;    // one pass uses (1, 0), the other uses (0, 1)

    half4 main(float2 coord) {
        float t = saturate(coord.y / uParams.y);
        float s = uParams.x * (1.0 - t);
        if (s < 0.5) return content.eval(coord);

        float2 axis = uDirection * s;
        half4 accum = half4(content.eval(coord)) * 0.24084130;
        accum += half4(content.eval(coord + axis * 0.6)) * 0.20116756;
        accum += half4(content.eval(coord - axis * 0.6)) * 0.20116756;
        accum += half4(content.eval(coord + axis * 1.2)) * 0.11723004;
        accum += half4(content.eval(coord - axis * 1.2)) * 0.11723004;
        accum += half4(content.eval(coord + axis * 1.8)) * 0.04766218;
        accum += half4(content.eval(coord - axis * 1.8)) * 0.04766218;
        accum += half4(content.eval(coord + axis * 2.4)) * 0.01351957;
        accum += half4(content.eval(coord - axis * 2.4)) * 0.01351957;
        return accum;
    }
""".trimIndent()

/**
 * Edge-fade blur shader. Blur is strongest at edges, fading to 0 toward
 * center over [uFade] pixels. Global weights [uWeights] decouple top/bottom.
 */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float uMaxBlur;    // blur at edge (4dp → px)
    uniform float uFade;       // fade-in distance (40dp → px)
    uniform float uH;          // composable height in px
    uniform float2 uWeights;   // x = top global multiplier, y = bottom global multiplier
    uniform float2 uDirection;

    half4 main(float2 coord) {
        if (uWeights.x <= 0.0 && uWeights.y <= 0.0) return content.eval(coord);
        float t = saturate(1.0 - coord.y / uFade) * uWeights.x;
        float b = saturate(1.0 - (uH - coord.y) / uFade) * uWeights.y;
        float s = uMaxBlur * max(t, b);
        if (s < 0.5) return content.eval(coord);

        float2 axis = uDirection * s;
        half4 accum = half4(content.eval(coord)) * 0.24084130;
        accum += half4(content.eval(coord + axis * 0.6)) * 0.20116756;
        accum += half4(content.eval(coord - axis * 0.6)) * 0.20116756;
        accum += half4(content.eval(coord + axis * 1.2)) * 0.11723004;
        accum += half4(content.eval(coord - axis * 1.2)) * 0.11723004;
        accum += half4(content.eval(coord + axis * 1.8)) * 0.04766218;
        accum += half4(content.eval(coord - axis * 1.8)) * 0.04766218;
        accum += half4(content.eval(coord + axis * 2.4)) * 0.01351957;
        accum += half4(content.eval(coord - axis * 2.4)) * 0.01351957;
        return accum;
    }
""".trimIndent()
