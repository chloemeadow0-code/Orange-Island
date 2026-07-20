package com.orangeisland.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.res.stringResource
import com.orangeisland.app.R

/** ARGB <-> HSV math and serialization, independent of UI. */
object ColorMath {
    fun argbToColor(argb: Long): Color {
        val a = ((argb shr 24) and 0xFF).toInt()
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return Color(r / 255f, g / 255f, b / 255f, a / 255f)
    }

    fun colorToArgb(color: Color): Long {
        val a = (color.alpha * 255 + 0.5f).toInt().coerceIn(0, 255)
        val r = (color.red * 255 + 0.5f).toInt().coerceIn(0, 255)
        val g = (color.green * 255 + 0.5f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255 + 0.5f).toInt().coerceIn(0, 255)
        return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    fun hsvToColor(h: Float, s: Float, v: Float, a: Float): Color {
        val hh = (h / 60f) % 6f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - f * s)
        val t = v * (1f - (1f - f) * s)
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t)
            3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q)
        }
        return Color(r, g, b, a)
    }

    fun Color.toHue(): Float {
        val max = maxOf(red, green, blue); val min = minOf(red, green, blue); val d = max - min
        if (d == 0f) return 0f
        val h = when (max) {
            red -> ((green - blue) / d) % 6f
            green -> (blue - red) / d + 2f
            else -> (red - green) / d + 4f
        }
        return ((h * 60f) + 360f) % 360f
    }

    fun Color.toSaturation(): Float {
        val max = maxOf(red, green, blue)
        return if (max == 0f) 0f else (max - minOf(red, green, blue)) / max
    }

    fun Color.toValue(): Float = maxOf(red, green, blue)

    fun hexToColor(hex: String): Color? {
        val clean = hex.removePrefix("#")
        if (clean.length != 6 && clean.length != 8) return null
        return try {
            if (clean.length == 6) {
                Color(clean.substring(0, 2).toInt(16) / 255f, clean.substring(2, 4).toInt(16) / 255f, clean.substring(4, 6).toInt(16) / 255f, 1f)
            } else {
                Color(clean.substring(2, 4).toInt(16) / 255f, clean.substring(4, 6).toInt(16) / 255f, clean.substring(6, 8).toInt(16) / 255f, clean.substring(0, 2).toInt(16) / 255f)
            }
        } catch (e: Exception) { null }
    }

    fun colorToHex(argb: Long): String {
        val a = ((argb shr 24) and 0xFF).toInt(); val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt(); val b = (argb and 0xFF).toInt()
        return if (a == 255) String.format("#%02X%02X%02X", r, g, b)
        else String.format("#%02X%02X%02X%02X", a, r, g, b)
    }
}

private enum class HsvaSurfaceMode { SATURATION_VALUE, HUE, ALPHA }

/** A single gesture-draggable color bar/panel; [mode] selects which one to render. */
@Composable
private fun HsvaSurface(
    mode: HsvaSurfaceMode,
    hue: Float,
    sat: Float,
    v: Float,
    alpha: Float,
    heightDp: Dp,
    onDrag: (x: Float, y: Float) -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val sw = size.width.toFloat(); val sh = size.height.toFloat()
                    onDrag((down.position.x.coerceIn(0f, sw) / sw).fastCoerceIn(0f, 1f), (down.position.y.coerceIn(0f, sh) / sh).fastCoerceIn(0f, 1f))
                    drag(down.id) { change ->
                        change.consume()
                        onDrag((change.position.x.coerceIn(0f, sw) / sw).fastCoerceIn(0f, 1f), (change.position.y.coerceIn(0f, sh) / sh).fastCoerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val w = size.width; val h = size.height
        when (mode) {
            HsvaSurfaceMode.SATURATION_VALUE -> {
                drawRect(ColorMath.hsvToColor(hue, 1f, 1f, 1f))
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent), 0f, w))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, h))
                val cx = sat * w; val cy = (1f - v) * h; val cr = 8.dp.toPx()
                drawCircle(Color.White, cr, Offset(cx, cy), style = Stroke(2.dp.toPx()))
                drawCircle(Color.Black, cr - 1.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))
            }
            HsvaSurfaceMode.HUE -> {
                val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                drawRoundRect(Brush.horizontalGradient(colors, 0f, w), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                val px = (hue / 360f) * w
                drawCircle(Color.White, 7.dp.toPx(), Offset(px, h / 2f))
                drawCircle(ColorMath.hsvToColor(hue, 1f, 1f, 1f), 5.5.dp.toPx(), Offset(px, h / 2f))
            }
            HsvaSurfaceMode.ALPHA -> {
                val step = 4.dp.toPx()
                var yy = 0f
                while (yy < h) {
                    var xx = 0f
                    while (xx < w) {
                        val isLight = ((xx / step).toInt() + (yy / step).toInt()) % 2 == 0
                        drawRect(if (isLight) Color.White else Color.LightGray, Offset(xx, yy), Size(step, step))
                        xx += step
                    }
                    yy += step
                }
                val base = ColorMath.hsvToColor(hue, sat, v, 1f)
                drawRoundRect(Brush.horizontalGradient(listOf(base.copy(alpha = 0f), base), 0f, w), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                val px = alpha * w
                drawCircle(Color.White, 7.dp.toPx(), Offset(px, h / 2f))
                drawCircle(base.copy(alpha = alpha), 5.5.dp.toPx(), Offset(px, h / 2f))
            }
        }
    }
}

/**
 * Color picker dialog. [initialArgb] null = currently following theme (use [fallback] as preview).
 * [onPick] receives null to "reset to follow theme", or a non-null ARGB value when confirmed.
 */
@Composable
fun ColorSwatchPickerDialog(
    initialArgb: Long?,
    fallback: Color,
    recentColors: List<Long> = emptyList(),
    onRecentColorsChanged: (List<Long>) -> Unit = {},
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    with(ColorMath) {
        val initColor = remember(initialArgb) { initialArgb?.let { argbToColor(it) } ?: fallback }
        var hue by remember { mutableFloatStateOf(initColor.toHue()) }
        var sat by remember { mutableFloatStateOf(initColor.toSaturation()) }
        var v by remember { mutableFloatStateOf(initColor.toValue()) }
        var a by remember { mutableFloatStateOf(initColor.alpha) }
        var hex by remember { mutableStateOf(initialArgb?.let { colorToHex(it) } ?: "") }

        val current = remember(hue, sat, v, a) { hsvToColor(hue, sat, v, a) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.color_picker_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(fallback))
                            Text(stringResource(R.string.color_picker_default), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(current))
                            Text(stringResource(R.string.color_picker_current), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HsvaSurface(HsvaSurfaceMode.SATURATION_VALUE, hue, sat, v, a, 180.dp) { x, y ->
                        sat = x; v = 1f - y; hex = colorToHex(colorToArgb(hsvToColor(hue, sat, v, a)))
                    }
                    HsvaSurface(HsvaSurfaceMode.HUE, hue, sat, v, a, 20.dp) { x, _ ->
                        hue = x * 360f; hex = colorToHex(colorToArgb(hsvToColor(hue, sat, v, a)))
                    }
                    HsvaSurface(HsvaSurfaceMode.ALPHA, hue, sat, v, a, 20.dp) { x, _ ->
                        a = x; hex = colorToHex(colorToArgb(hsvToColor(hue, sat, v, a)))
                    }
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { input ->
                            hex = input
                            hexToColor(input)?.let { c -> hue = c.toHue(); sat = c.toSaturation(); v = c.toValue(); a = c.alpha }
                        },
                        label = { Text("HEX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (recentColors.isNotEmpty()) {
                        Text(stringResource(R.string.color_picker_recent), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            recentColors.take(8).forEach { argb ->
                                val c = argbToColor(argb)
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background(c)
                                        .clickable {
                                            hue = c.toHue(); sat = c.toSaturation(); v = c.toValue(); a = c.alpha
                                            hex = colorToHex(argb)
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { onPick(null); onDismiss() }) { Text(stringResource(R.string.color_picker_reset)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val argb = colorToArgb(current)
                        onRecentColorsChanged((listOf(argb) + recentColors.filter { it != argb }).take(20))
                        onPick(argb)
                        onDismiss()
                    }) { Text(stringResource(R.string.color_picker_confirm)) }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
