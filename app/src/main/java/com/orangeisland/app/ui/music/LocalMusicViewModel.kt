package com.orangeisland.app.ui.music

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orangeisland.app.data.music.LocalMusicRepository
import com.orangeisland.app.data.music.LocalMusicTrack
import com.orangeisland.app.service.MusicPlaybackService
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalMusicUiState(
    val tracks: List<LocalMusicTrack> = emptyList(),
    val currentPlayingTrackId: String? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Pair<Int, Int>? = null,
    val errorMessage: String? = null,
    val currentPositionMs: Long = 0,
    val currentDurationMs: Long = 0,
    val playMode: Int = 0,
    val isSeekingByUser: Boolean = false,
    val isPlaying: Boolean = false
)

class LocalMusicViewModel(
    application: Application,
    private val repository: LocalMusicRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LocalMusicUiState())
    val state: StateFlow<LocalMusicUiState> = _state.asStateFlow()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != MusicPlaybackService.ACTION_STATE) return
            val trackId = intent.getStringExtra(MusicPlaybackService.EXTRA_NOW_PLAYING_ID) ?: ""
            val isPlaying = intent.getBooleanExtra(MusicPlaybackService.EXTRA_IS_PLAYING, false)
            val mode = intent.getIntExtra(MusicPlaybackService.EXTRA_PLAY_MODE, _state.value.playMode)
            val currentId = if (isPlaying && trackId.isNotBlank()) {
                _state.value.tracks.find { it.id == trackId }?.id
            } else if (!isPlaying) {
                null
            } else {
                _state.value.currentPlayingTrackId
            }
            _state.value = _state.value.copy(
                currentPlayingTrackId = currentId,
                playMode = mode,
                isPlaying = isPlaying
            )
        }
    }

    init {
        val filter = IntentFilter(MusicPlaybackService.ACTION_STATE)
        getApplication<Application>().registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        loadTracks()
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (_state.value.isSeekingByUser) continue
                if (_state.value.currentPlayingTrackId == null) continue
                try {
                    val snap = MusicPlaybackService.getLiveSnapshot() ?: continue
                    if (snap.isPlaying || snap.positionMs > 0) {
                        _state.value = _state.value.copy(
                            currentPositionMs = snap.positionMs,
                            currentDurationMs = snap.durationMs.coerceAtLeast(0)
                        )
                    }
                } catch (e: Exception) {
                    DebugLog.e("LocalMusicViewModel", "position update failed", e)
                }
            }
        }
    }

    fun loadTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tracks = repository.loadTracks()
                _state.value = _state.value.copy(tracks = tracks)
            } catch (e: Exception) {
                DebugLog.e("LocalMusicViewModel", "loadTracks failed", e)
                _state.value = _state.value.copy(errorMessage = "加载歌曲列表失败: ${e.message}")
            }
        }
    }

    fun importTrack(uri: Uri) = importTracks(listOf(uri))

    fun importTracks(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isUploading = true, uploadProgress = Pair(0, uris.size), errorMessage = null)
            val successIds = mutableListOf<String>()
            var skippedCount = 0
            var failedCount = 0
            uris.forEachIndexed { index, uri ->
                try {
                    val track = repository.importTrack(uri, getApplication())
                    successIds.add(track.id)
                } catch (e: com.orangeisland.app.data.music.DuplicateTrackException) {
                    DebugLog.d("LocalMusicViewModel", "skipped duplicate: ${e.message}")
                    skippedCount++
                } catch (e: Exception) {
                    DebugLog.e("LocalMusicViewModel", "importTrack failed for $uri", e)
                    failedCount++
                }
                _state.value = _state.value.copy(uploadProgress = Pair(index + 1, uris.size))
            }
            loadTracks()
            if (successIds.isNotEmpty()) {
                getApplication<Application>().startService(
                    Intent(getApplication(), MusicPlaybackService::class.java).apply {
                        action = MusicPlaybackService.ACTION_ADD_TRACKS
                        putExtra(MusicPlaybackService.EXTRA_TRACK_IDS, successIds.toTypedArray())
                    }
                )
            }
            val resultMessage = when {
                skippedCount > 0 && failedCount > 0 -> "已跳过 $skippedCount 首重复，$failedCount 首导入失败"
                skippedCount > 0 && failedCount == 0 -> "已跳过 $skippedCount 首重复歌曲"
                failedCount > 0 -> "部分文件导入失败（$failedCount 首）"
                else -> null
            }
            _state.value = _state.value.copy(
                isUploading = false,
                uploadProgress = null,
                errorMessage = resultMessage
            )
        }
    }

    fun deleteTrack(track: LocalMusicTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteTrack(track)
                loadTracks()
                getApplication<Application>().startService(
                    Intent(getApplication(), MusicPlaybackService::class.java).apply {
                        action = MusicPlaybackService.ACTION_REMOVE_TRACK
                        putExtra(MusicPlaybackService.EXTRA_TRACK_ID, track.id)
                    }
                )
            } catch (e: Exception) {
                DebugLog.e("LocalMusicViewModel", "deleteTrack failed", e)
                _state.value = _state.value.copy(errorMessage = "鍒犻櫎澶辫触: ${e.message}")
            }
        }
    }

    fun playTrack(track: LocalMusicTrack) {
        val app = getApplication<Application>()
        app.startService(
            Intent(app, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PLAY_TRACK
                putExtra(MusicPlaybackService.EXTRA_TRACK_ID, track.id)
            }
        )
    }

    fun stopPlayback() {
        val app = getApplication<Application>()
        app.startService(
            Intent(app, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PAUSE
            }
        )
        _state.value = _state.value.copy(currentPlayingTrackId = null)
    }

    fun resumePlayback() {
        getApplication<Application>().startService(
            Intent(getApplication(), MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PLAY
            }
        )
    }

    fun playNext() {
        getApplication<Application>().startService(
            Intent(getApplication(), MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_NEXT
            }
        )
    }

    fun playPrevious() {
        getApplication<Application>().startService(
            Intent(getApplication(), MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PREV
            }
        )
    }

    fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(currentPositionMs = positionMs, isSeekingByUser = false)
        MusicPlaybackService.seekTo(positionMs)
    }

    fun setUserSeeking(isSeeking: Boolean, dragPositionMs: Long = 0) {
        _state.value = if (isSeeking) {
            _state.value.copy(isSeekingByUser = true, currentPositionMs = dragPositionMs)
        } else {
            _state.value.copy(isSeekingByUser = false)
        }
    }

    fun setPlayMode(mode: Int) {
        _state.value = _state.value.copy(playMode = mode)
        getApplication<Application>().startService(
            Intent(getApplication(), MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_SET_PLAY_MODE
                putExtra(MusicPlaybackService.EXTRA_PLAY_MODE, mode)
            }
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(stateReceiver)
        } catch (e: Exception) {
            DebugLog.e("LocalMusicViewModel", "unregisterReceiver failed", e)
        }
    }
}
