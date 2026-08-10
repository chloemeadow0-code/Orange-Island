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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalMusicUiState(
    val tracks: List<LocalMusicTrack> = emptyList(),
    val currentPlayingTrackId: String? = null,
    val isUploading: Boolean = false,
    val errorMessage: String? = null
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
            val currentId = if (isPlaying && trackId.isNotBlank()) {
                _state.value.tracks.find { it.id == trackId }?.id
            } else if (!isPlaying) {
                null
            } else {
                _state.value.currentPlayingTrackId
            }
            _state.value = _state.value.copy(currentPlayingTrackId = currentId)
        }
    }

    init {
        val filter = IntentFilter(MusicPlaybackService.ACTION_STATE)
        getApplication<Application>().registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        loadTracks()
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

    fun importTrack(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isUploading = true, errorMessage = null)
            try {
                repository.importTrack(uri, getApplication())
                loadTracks()
                getApplication<Application>().startService(
                    Intent(getApplication(), MusicPlaybackService::class.java).apply {
                        action = MusicPlaybackService.ACTION_RELOAD_LIBRARY
                    }
                )
            } catch (e: Exception) {
                DebugLog.e("LocalMusicViewModel", "importTrack failed", e)
                _state.value = _state.value.copy(errorMessage = "上传失败: ${e.message}", isUploading = false)
                return@launch
            }
            _state.value = _state.value.copy(isUploading = false)
        }
    }

    fun deleteTrack(track: LocalMusicTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_state.value.currentPlayingTrackId == track.id) {
                    getApplication<Application>().startService(
                        Intent(getApplication(), MusicPlaybackService::class.java).apply {
                            action = MusicPlaybackService.ACTION_PAUSE
                        }
                    )
                }
                repository.deleteTrack(track)
                loadTracks()
                getApplication<Application>().startService(
                    Intent(getApplication(), MusicPlaybackService::class.java).apply {
                        action = MusicPlaybackService.ACTION_RELOAD_LIBRARY
                    }
                )
            } catch (e: Exception) {
                DebugLog.e("LocalMusicViewModel", "deleteTrack failed", e)
                _state.value = _state.value.copy(errorMessage = "删除失败: ${e.message}")
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
