package com.orangeisland.app.ui.music

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.data.music.MusicStudioTrack
import com.orangeisland.app.data.music.VoiceVersion
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.service.MusicPlaybackService
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.worker.MusicGenerationWorker
import com.orangeisland.app.worker.VoiceConversionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicStudioUiState(
    val tracks: List<MusicStudioTrack> = emptyList(),
    /** True while any music generation (started from this page OR by the AI tool) is running. */
    val isGenerating: Boolean = false,
    /** Latest progress message from any active generation, or the most recent outcome. */
    val generationMessage: String = "",
    val currentPlayingTrackId: String? = null,
    val errorMessage: String? = null,
    /** Track whose detail sheet is open, if any. */
    val selectedTrackId: String? = null,
    /** Id of the track currently undergoing voice conversion, if any. */
    val voiceConvertingTrackId: String? = null,
    val voiceConversionMessage: String = "",
    /** RVC voice-replacement config, edited inline on the track detail page. */
    val replicateApiKey: String = "",
    val replicateModelVersion: String = "",
    val rvcModelUrl: String = ""
) {
    val selectedTrack: MusicStudioTrack? get() = tracks.find { it.id == selectedTrackId }
}

class MusicStudioViewModel(
    application: Application,
    private val settings: SettingsRepository,
    private val repository: MusicStudioRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MusicStudioUiState())
    val state: StateFlow<MusicStudioUiState> = _state.asStateFlow()

    private val workManager: WorkManager by lazy { WorkManager.getInstance(application) }

    /**
     * 监听 [MusicPlaybackService] 推送的播放状态广播，把当前播放 trackId 反映到 UI。
     * 这样 App 内和桌面小组件共用同一个 Service，UI 状态由 Service 单向驱动。
     */
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
        loadTracks()
        observeGenerations()
        observeRvcConfig()
        // 注册状态广播接收器
        getApplication<Application>().registerReceiver(
            stateReceiver,
            IntentFilter(MusicPlaybackService.ACTION_STATE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    /** Keep the in-page RVC config fields in sync with the persisted settings. */
    private fun observeRvcConfig() {
        viewModelScope.launch {
            settings.musicStudioReplicateApiKey.collect {
                _state.value = _state.value.copy(replicateApiKey = it)
            }
        }
        viewModelScope.launch {
            settings.musicStudioReplicateModelVersion.collect {
                _state.value = _state.value.copy(replicateModelVersion = it)
            }
        }
        viewModelScope.launch {
            settings.musicStudioRvcModelUrl.collect {
                _state.value = _state.value.copy(rvcModelUrl = it)
            }
        }
    }

    fun setReplicateApiKey(value: String) = settings.setMusicStudioReplicateApiKey(value)
    fun setReplicateModelVersion(value: String) = settings.setMusicStudioReplicateModelVersion(value)
    fun setRvcModelUrl(value: String) = settings.setMusicStudioRvcModelUrl(value)

    fun loadTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = repository.loadTracks()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(tracks = tracks)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun openTrackDetail(track: MusicStudioTrack) {
        _state.value = _state.value.copy(selectedTrackId = track.id)
    }

    fun closeTrackDetail() {
        _state.value = _state.value.copy(selectedTrackId = null)
    }

    /**
     * Kick off an on-demand RVC voice conversion for the currently selected track. Validates that
     * a Replicate key and voice model URL are configured first; the actual work runs in
     * [VoiceConversionWorker], observed via the shared tag so the detail sheet shows live progress.
     */
    fun generateVoiceVersion() {
        val track = _state.value.selectedTrack ?: return
        val apiKey = settings.musicStudioReplicateApiKey.value
        val modelUrl = settings.musicStudioRvcModelUrl.value
        if (apiKey.isBlank() || modelUrl.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = "请先在设置中配置 Replicate API Key 和音色模型 URL"
            )
            return
        }
        VoiceConversionWorker.enqueue(getApplication(), track.id, modelUrl)
        _state.value = _state.value.copy(
            voiceConvertingTrackId = track.id,
            voiceConversionMessage = "正在提交音色替换…"
        )
    }

    fun deleteVoiceVersion(version: VoiceVersion) {
        val trackId = _state.value.selectedTrackId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteVoiceVersion(trackId, version.id)
            if (_state.value.currentPlayingTrackId == "v_${version.id}") {
                withContext(Dispatchers.Main) {
                    getApplication<Application>().startService(
                        Intent(getApplication(), MusicPlaybackService::class.java).apply {
                            action = MusicPlaybackService.ACTION_PAUSE
                        }
                    )
                }
            }
            loadTracks()
        }
    }

    fun playVoiceVersion(version: VoiceVersion) {
        if (version.localPath.isBlank()) return
        val file = java.io.File(version.localPath)
        if (!file.exists()) {
            _state.value = _state.value.copy(errorMessage = "音频文件不存在")
            return
        }
        // 通过 Service 播放独立音频（不进列表），保证单一播放源
        getApplication<Application>().startService(
            Intent(getApplication(), MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PLAY_PATH
                putExtra(MusicPlaybackService.EXTRA_PATH, version.localPath)
                putExtra(MusicPlaybackService.EXTRA_TITLE, "音色版本")
            }
        )
        _state.value = _state.value.copy(currentPlayingTrackId = "v_${version.id}")
    }

    /**
     * Observe *every* music work request via the shared tag, so the UI reflects progress and
     * refreshes the library no matter who started the job — this page, the AI's `generate_music`
     * tool, or an on-demand voice conversion started from the detail sheet.
     *
     * Voice-conversion work is distinguished by its unique work-name prefix so the detail sheet can
     * show per-track RVC progress while the top-level progress card still reflects generation.
     */
    private fun observeGenerations() {
        viewModelScope.launch(Dispatchers.IO) {
            workManager.getWorkInfosByTagFlow(MusicGenerationWorker.TAG_MUSIC_GENERATION)
                .collect { workInfos ->
                    // Conversion work tags itself with its work-name (music_voice_generation_<trackId>)
                    // in addition to the shared generation tag, so the two kinds can be split here.
                    val conversionWork = workInfos.filter { info ->
                        info.tags.any { it.startsWith("music_voice_conversion_") }
                    }
                    val generationWork = workInfos - conversionWork.toSet()

                    handleGenerationWork(generationWork)
                    handleConversionWork(conversionWork)
                }
        }
    }

    private suspend fun handleGenerationWork(workInfos: List<WorkInfo>) {
        val anyActive = workInfos.any {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }
        val message = workInfos
            .filter { it.state == WorkInfo.State.RUNNING }
            .mapNotNull { it.progress.getString(MusicGenerationWorker.KEY_PROGRESS_MESSAGE) }
            .firstOrNull()
            ?: workInfos
                .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                .mapNotNull { it.progress.getString(MusicGenerationWorker.KEY_PROGRESS_MESSAGE) }
                .firstOrNull()
            ?: ""

        val anySucceeded = workInfos.any { it.state == WorkInfo.State.SUCCEEDED }
        val lastFailure = workInfos
            .filter { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }
            .mapNotNull { it.outputData.getString(MusicGenerationWorker.KEY_ERROR) }
            .lastOrNull()

        withContext(Dispatchers.Main) {
            _state.value = _state.value.copy(
                isGenerating = anyActive,
                generationMessage = when {
                    anyActive -> if (message.isBlank()) "生成中…" else message
                    anySucceeded -> "生成完成"
                    lastFailure != null -> lastFailure
                    else -> ""
                }
            )
        }

        if (anySucceeded) loadTracks()
    }

    private suspend fun handleConversionWork(workInfos: List<WorkInfo>) {
        // Each conversion request tags itself with its work-name (music_voice_conversion_<trackId>).
        val active = workInfos.firstOrNull {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }
        val trackId = active?.tags?.firstOrNull { it.startsWith("music_voice_conversion_") }
            ?.removePrefix("music_voice_conversion_")
        val message = active?.progress
            ?.getString(VoiceConversionWorker.KEY_PROGRESS_MESSAGE) ?: ""

        val succeeded = workInfos.any { it.state == WorkInfo.State.SUCCEEDED }
        val failure = workInfos
            .filter { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }
            .mapNotNull { it.outputData.getString(VoiceConversionWorker.KEY_ERROR) }
            .lastOrNull()

        withContext(Dispatchers.Main) {
            _state.value = _state.value.copy(
                voiceConvertingTrackId = trackId,
                voiceConversionMessage = when {
                    active != null -> if (message.isBlank()) "正在进行音色替换…" else message
                    else -> ""
                },
                errorMessage = if (!succeeded && failure != null && trackId == null) failure
                    else _state.value.errorMessage
            )
        }
        if (succeeded) loadTracks()
    }

    fun deleteTrack(track: MusicStudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTrack(track)
            if (_state.value.currentPlayingTrackId == track.id) {
                withContext(Dispatchers.Main) {
                    getApplication<Application>().startService(
                        Intent(getApplication(), MusicPlaybackService::class.java).apply {
                            action = MusicPlaybackService.ACTION_PAUSE
                        }
                    )
                }
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
        // 通过 Service 按 trackId 播放，保证 App 内 / 桌面小组件 / 锁屏共用同一播放源
        getApplication<Application>().startService(
            Intent(getApplication(), MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PLAY_TRACK
                putExtra(MusicPlaybackService.EXTRA_TRACK_ID, track.id)
            }
        )
        _state.value = _state.value.copy(currentPlayingTrackId = track.id)
    }

    fun stopPlayback() {
        getApplication<Application>().startService(
            Intent(getApplication(), MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PAUSE
            }
        )
        _state.value = _state.value.copy(currentPlayingTrackId = null)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(stateReceiver)
    }

    companion object {
        private const val TAG = "MusicStudioViewModel"
    }
}
