package com.orangeisland.app.ui.music

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeisland.app.R
import com.orangeisland.app.data.music.LocalMusicTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun LocalMusicPage(
    viewModel: LocalMusicViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importTracks(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.padding(top = 12.dp))

        FilledTonalButton(
            onClick = { launcher.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isUploading
        ) {
            if (state.isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                val progressText = state.uploadProgress?.let { (done, total) ->
                    "正在导入 ($done/$total)"
                } ?: stringResource(R.string.local_music_uploading)
                Text(
                    text = progressText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.local_music_upload),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.padding(top = 12.dp))

        if (state.tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.local_music_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.local_music_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { launcher.launch(arrayOf("audio/*")) }) {
                        Text(stringResource(R.string.local_music_upload_now))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.tracks, key = { it.id }) { track ->
                    LocalTrackCard(
                        track = track,
                        isPlaying = track.id == state.currentPlayingTrackId,
                        onPlay = { viewModel.playTrack(track) },
                        onStop = { viewModel.stopPlayback() },
                        onDelete = { viewModel.deleteTrack(track) }
                    )
                }
            }
        }

        val hasTracks = state.tracks.isNotEmpty()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatDuration(state.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = state.currentPositionMs.toFloat(),
                    onValueChange = { viewModel.setUserSeeking(true, it.toLong()) },
                    onValueChangeFinished = { viewModel.seekTo(state.currentPositionMs) },
                    valueRange = 0f..state.currentDurationMs.toFloat().coerceAtLeast(0f),
                    enabled = state.currentDurationMs > 0,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDuration(state.currentDurationMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { viewModel.setPlayMode((state.playMode + 1) % 4) },
                    enabled = hasTracks
                ) {
                    val modeTint = if (state.playMode == 0)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                    when (state.playMode) {
                        2 -> {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "单曲循环",
                                    modifier = Modifier.fillMaxSize(),
                                    tint = modeTint
                                )
                                Text(
                                    text = "1",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = modeTint,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 0.dp)
                                )
                            }
                        }
                        else -> {
                            Icon(
                                imageVector = if (state.playMode == 3) Icons.Default.Shuffle else Icons.Default.Repeat,
                                contentDescription = when (state.playMode) {
                                    0 -> "顺序播放"
                                    1 -> "全部循环"
                                    else -> "随机播放"
                                },
                                tint = modeTint
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    enabled = hasTracks
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                }
                IconButton(
                    onClick = {
                        if (state.isPlaying) viewModel.stopPlayback() else viewModel.resumePlayback()
                    },
                    enabled = state.currentPlayingTrackId != null || hasTracks,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) stringResource(R.string.stop) else stringResource(R.string.play),
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.playNext() },
                    enabled = hasTracks
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }

    state.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(error) }
        )
    }
}

@Composable
private fun LocalTrackCard(
    track: LocalMusicTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = { if (isPlaying) onStop() else onPlay() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.stop) else stringResource(R.string.play)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { stringResource(R.string.local_music_unknown_artist) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(track.durationMs) + "  ·  " +
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(track.addedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
