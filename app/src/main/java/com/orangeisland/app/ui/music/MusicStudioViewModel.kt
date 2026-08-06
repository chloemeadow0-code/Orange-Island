package com.orangeisland.app.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.data.music.MusicStudioTrack
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.worker.MusicGenerationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class MusicStudioUiState(
    val tracks: List<MusicStudioTrack> = emptyList(),
    val isGenerating: Boolean = false,
    val generationMessage: String = "",
    val currentPlayingTrackId: String? = null,
    val showGenerateDialog: Boolean = false,
    val errorMessage: String? = null
)

class MusicStudioViewModel(
    application: Application,
    private val settings: SettingsRepository,
    private val repository: MusicStudioRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MusicStudioUiState())
    val state: StateFlow<MusicStudioUiState> = _state.asStateFlow()

    private val workManager: WorkManager by lazy { WorkManager.getInstance(application) }

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(application).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.value = _state.value.copy(currentPlayingTrackId = null)
                    }
                }
            })
        }
    }

    init {
        loadTracks()
    }

    fun loadTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = repository.loadTracks()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(tracks = tracks)
            }
        }
    }

    fun showGenerateDialog() {
        _state.value = _state.value.copy(showGenerateDialog = true)
    }

    fun dismissGenerateDialog() {
        _state.value = _state.value.copy(showGenerateDialog = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun generate(title: String, lyrics: String, style: String) {
        if (title.isBlank() || lyrics.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "标题和歌词不能为空")
            return
        }

        val providerId = settings.musicStudioProvider.value
        val configError = validateProviderConfig(providerId)
        if (configError != null) {
            _state.value = _state.value.copy(errorMessage = configError)
            return
        }

        _state.value = _state.value.copy(
            isGenerating = true,
            generationMessage = "正在提交后台任务…",
            showGenerateDialog = false
        )

        val trackId = UUID.randomUUID().toString()
        val workName = MusicGenerationWorker.enqueue(
            context = getApplication(),
            trackId = trackId,
            providerId = providerId,
            title = title,
            lyrics = lyrics,
            style = style.ifBlank { "pop" }
        )

        observeWork(workName)
    }

    private fun validateProviderConfig(providerId: String): String? {
        return when (providerId) {
            "suno" -> {
                if (settings.musicStudioSunoApiUrl.value.isBlank() || settings.musicStudioSunoApiKey.value.isBlank()) {
                    "请先在设置中配置 Suno API 地址和 Key"
                } else null
            }
            "replicate" -> {
                if (settings.musicStudioReplicateApiKey.value.isBlank()) {
                    "请先在设置中配置 Replicate API Key"
                } else if (settings.musicStudioReplicateModelVersion.value.isBlank()) {
                    "请先在设置中配置 Replicate Model Version"
                } else null
            }
            else -> "不支持的 provider: $providerId"
        }
    }

    private fun observeWork(workName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWorkFlow(workName)
                .collect { workInfos ->
                    val info = workInfos.firstOrNull() ?: return@collect
                    withContext(Dispatchers.Main) {
                        when (info.state) {
                            WorkInfo.State.RUNNING -> {
                                val progress = info.progress
                                val message = progress.getString(MusicGenerationWorker.KEY_PROGRESS_MESSAGE) ?: "生成中…"
                                _state.value = _state.value.copy(
                                    isGenerating = true,
                                    generationMessage = message
                                )
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                _state.value = _state.value.copy(
                                    isGenerating = false,
                                    generationMessage = ""
                                )
                                loadTracks()
                            }
                            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                val error = info.outputData.getString(MusicGenerationWorker.KEY_ERROR)
                                    ?: if (info.state == WorkInfo.State.CANCELLED) "生成已取消" else "生成失败"
                                _state.value = _state.value.copy(
                                    isGenerating = false,
                                    generationMessage = "",
                                    errorMessage = error
                                )
                            }
                            else -> { /* ENQUEUED / BLOCKED */ }
                        }
                    }
                }
        }
    }

    fun deleteTrack(track: MusicStudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTrack(track)
            if (_state.value.currentPlayingTrackId == track.id) {
                withContext(Dispatchers.Main) { exoPlayer.stop() }
            }
            loadTracks()
        }
    }

    fun playTrack(track: MusicStudioTrack) {
        if (track.localPath.isBlank()) return
        val file = java.io.File(track.localPath)
        if (!file.exists()) {
            _state.value = _state.value.copy(errorMessage = "音频文件不存在")
            return
        }
        try {
            exoPlayer.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            exoPlayer.prepare()
            exoPlayer.play()
            _state.value = _state.value.copy(currentPlayingTrackId = track.id)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Play failed", e)
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    fun stopPlayback() {
        exoPlayer.stop()
        _state.value = _state.value.copy(currentPlayingTrackId = null)
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
    }

    companion object {
        private const val TAG = "MusicStudioViewModel"
    }
}
