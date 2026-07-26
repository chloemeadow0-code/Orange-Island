package com.orangeisland.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A non-interactive decorative image pinned to a corner of its parent [BoxScope].
 *
 * Mirrors the UI Playground `.bubble-decoration` / `.settings-branch` rules: the
 * artwork may bleed past the card edge and must never capture pointer events
 * (`.pointer-events: none`). A plain [Image] has no click or pointer-input
 * modifier, so it is transparent to touches by construction.
 *
 * Usage (inside a [androidx.compose.foundation.layout.Box]):
 * ```
 * Box(...) {
 *     ...content...
 *     DecorativeCorner(R.drawable.island_deco_sprout, width = 112.dp)
 * }
 * ```
 *
 * @param res the `R.drawable.island_deco_*` decorative PNG.
 * @param width drawn width; height follows the image's intrinsic aspect ratio.
 * @param alignment the box corner to pin to (defaults bottom-end).
 * @param offsetX / offsetY translate the artwork so it can overhang the edge.
 * @param alpha overall opacity (e.g. 0.62f for the settings branch).
 */
@Composable
fun BoxScope.DecorativeCorner(
    @DrawableRes res: Int,
    width: Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomEnd,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    alpha: Float = 1f,
) {
    val painter = painterResource(res)
    val ratio = painter.intrinsicSize.let {
        if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height else 1f
    }
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .align(alignment)
            .width(width)
            .aspectRatio(ratio)
            .let { if (offsetX != 0.dp || offsetY != 0.dp) it.offset(offsetX, offsetY) else it }
            .let { if (alpha < 1f) it.alpha(alpha) else it },
    )
}

/**
 * A standalone non-interactive decorative image (not pinned to a box corner).
 *
 * Use this when the artwork is laid out in normal flow (e.g. the watercolor
 * boat above the chat welcome text). Height follows the PNG's intrinsic aspect
 * ratio; only [width] is constrained. Like [DecorativeCorner] it adds no click
 * handler, so it never intercepts taps.
 */
@Composable
fun DecorativeImage(
    @DrawableRes res: Int,
    width: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val painter = painterResource(res)
    val ratio = painter.intrinsicSize.let {
        if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height else 1f
    }
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .width(width)
            .aspectRatio(ratio)
            .let { if (alpha < 1f) it.alpha(alpha) else it },
    )
}

