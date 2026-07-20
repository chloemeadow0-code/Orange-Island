package com.orangeisland.app.ui.chat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.orangeisland.app.R
import kotlin.math.roundToInt

data class VideoSliceResult(
    val uri: String,
    val frameCount: Int,
    val intervalMs: Long
)

object VideoSliceDefaults {
    fun defaultFrameCount(durationMs: Long): Int {
        val seconds = durationMs / 1000
        return when {
            seconds < 10 -> 3
            seconds < 30 -> 5
            seconds < 60 -> 8
            else -> maxOf(2, minOf(20, (seconds / 5).toInt()))
        }
    }
}

@Composable
fun VideoSliceDialog(
    videoUri: String,
    durationMs: Long,
    onConfirm: (VideoSliceResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val seconds = durationMs / 1000
    val defaultFrames = remember(durationMs) { VideoSliceDefaults.defaultFrameCount(durationMs) }

    var useFrameCountMode by remember { mutableStateOf(true) }
    var frameCount by remember { mutableIntStateOf(defaultFrames) }
    var intervalSec by remember(durationMs) {
        mutableIntStateOf(maxOf(1, (seconds / defaultFrames).toInt()))
    }

    val effectiveFrameCount = if (useFrameCountMode) frameCount else maxOf(2, (seconds / intervalSec).toInt())
    val effectiveIntervalMs = if (useFrameCountMode) {
        if (frameCount > 1) durationMs / frameCount else 0L
    } else {
        intervalSec * 1000L
    }

    val thumbnail = remember(videoUri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, android.net.Uri.parse(videoUri))
            val bitmap = retriever.frameAtTime
            retriever.release()
            bitmap?.let { Bitmap.createScaledBitmap(it, 512, (512f * it.height / it.width).roundToInt(), true) }
                ?.also { if (it != bitmap) bitmap.recycle() }
        } catch (_: Exception) { null }
    }

    val durationFormatted = remember(durationMs) {
        val m = seconds / 60
        val s = seconds % 60
        "${m}:${s.toString().padStart(2, '0')}"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.video_slice_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.video_duration, durationFormatted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (thumbnail != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = stringResource(R.string.video_preview),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val shape = RoundedCornerShape(50)
                    ModeChip(selected = useFrameCountMode, onClick = { useFrameCountMode = true }, label = stringResource(R.string.by_frame_count), modifier = Modifier.weight(1f))
                    ModeChip(selected = !useFrameCountMode, onClick = { useFrameCountMode = false }, label = stringResource(R.string.by_interval), modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))

                if (useFrameCountMode) {
                    Text(
                        stringResource(R.string.frames_count, effectiveFrameCount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = frameCount.toFloat(),
                        onValueChange = { frameCount = it.roundToInt().coerceIn(2, 20) },
                        valueRange = 2f..20f,
                        steps = 17
                    )
                    val betweenLabel = (effectiveIntervalMs / 1000f).let {
                        if (it < 1) "${(it * 1000).roundToInt()}ms" else "${it.roundToInt()}s"
                    }
                    Text(
                        stringResource(R.string.video_between_frames, betweenLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.interval_seconds, effectiveIntervalMs / 1000),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = intervalSec.toFloat(),
                        onValueChange = { intervalSec = it.roundToInt().coerceIn(1, minOf(30, seconds.toInt())) },
                        valueRange = 1f..minOf(30f, seconds.toFloat()).coerceAtLeast(2f),
                        steps = minOf(29, seconds.toInt() - 1)
                    )
                    Text(
                        stringResource(R.string.frames_count, effectiveFrameCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm(VideoSliceResult(videoUri, effectiveFrameCount, effectiveIntervalMs))
                    }, shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.extract_frames, effectiveFrameCount))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300)
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}
