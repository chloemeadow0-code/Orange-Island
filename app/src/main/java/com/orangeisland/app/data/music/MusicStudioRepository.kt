package com.orangeisland.app.data.music

import android.content.Context
import com.orangeisland.app.api.music.MusicGenerationProvider
import com.orangeisland.app.api.music.MusicJobStatus
import com.orangeisland.app.api.music.MusicProviderConfig
import com.orangeisland.app.api.music.MusicGenerationRequest
import com.orangeisland.app.api.music.VoiceConversionConfig
import com.orangeisland.app.api.music.VoiceConversionProvider
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

sealed class MusicGenerationState {
    abstract val message: String
}

data class Submitting(override val message: String = "正在提交生成任务…") : MusicGenerationState()
data class Processing(override val message: String = "正在生成中…", val attempt: Int) : MusicGenerationState()
data class Downloading(override val message: String = "正在下载音频…") : MusicGenerationState()
data class ConvertingVoice(override val message: String = "正在进行音色替换…") : MusicGenerationState()
data class Completed(override val message: String = "生成完成", val track: MusicStudioTrack) : MusicGenerationState()
data class Failed(override val message: String = "生成失败") : MusicGenerationState()

/**
 * Orchestrates cloud music generation providers and persists the resulting
 * tracks in the app's private storage. Optionally applies a voice-conversion
 * step (e.g. Replicate RVC) after the base audio is generated.
 */
class MusicStudioRepository(
    private val context: Context,
    providers: List<MusicGenerationProvider>,
    private val voiceConversionProvider: VoiceConversionProvider? = null
) {

    private val providers = providers.associateBy { it.id }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val musicDir: File by lazy {
        File(context.filesDir, "music_studio").apply { mkdirs() }
    }

    private val libraryFile: File
        get() = File(musicDir, "library.json")

    fun loadTracks(): List<MusicStudioTrack> {
        return try {
            if (!libraryFile.exists()) return emptyList()
            val text = libraryFile.readText()
            json.decodeFromString<List<MusicStudioTrack>>(text)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to load music library", e)
            emptyList()
        }
    }

    private fun saveTracks(tracks: List<MusicStudioTrack>) {
        try {
            libraryFile.writeText(json.encodeToString(tracks))
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to save music library", e)
        }
    }

    fun addTrack(track: MusicStudioTrack) {
        val tracks = loadTracks().toMutableList()
        tracks.add(0, track)
        saveTracks(tracks)
    }

    fun replaceTrack(track: MusicStudioTrack): Boolean {
        val tracks = loadTracks().toMutableList()
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index == -1) return false
        tracks[index] = track
        saveTracks(tracks)
        return true
    }

    fun deleteTrack(track: MusicStudioTrack): Boolean {
        val tracks = loadTracks().toMutableList()
        val removed = tracks.removeAll { it.id == track.id }
        if (removed) {
            saveTracks(tracks)
            if (track.localPath.isNotBlank()) {
                File(track.localPath).delete()
            }
        }
        return removed
    }

    fun resolveProvider(id: String): MusicGenerationProvider? = providers[id]

    /**
     * Run a full generation: submit, poll, download, optionally voice-convert, and persist.
     * Emits [MusicGenerationState] updates so the UI can show progress.
     */
    fun generate(
        config: MusicProviderConfig,
        request: MusicGenerationRequest,
        voiceConversionConfig: VoiceConversionConfig? = null
    ): Flow<MusicGenerationState> = flow {
        val provider = providers[config.provider]
            ?: run { emit(Failed("未找到 provider: ${config.provider}")); return@flow }

        emit(Submitting())

        val job = try {
            provider.submit(config, request)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Submit failed", e)
            emit(Failed("提交失败: ${e.message}"))
            return@flow
        }

        if (job.status == MusicJobStatus.SUCCESS && !job.audioUrl.isNullOrBlank()) {
            val track = finalizeBaseTrack(provider, request, config, job.audioUrl)
            finishWithOptionalVoiceConversion(track, voiceConversionConfig)
            return@flow
        }

        // Poll with backoff, matching the plugin's ~10 minute ceiling.
        val maxAttempts = 80
        var lastJob = job
        for (attempt in 1..maxAttempts) {
            val interval = if (attempt <= 10) 3_000L else 8_000L
            delay(interval)
            emit(Processing(attempt = attempt))
            lastJob = try {
                provider.poll(config, lastJob)
            } catch (e: Exception) {
                DebugLog.e(TAG, "Poll error attempt $attempt", e)
                // Keep going unless this is the last attempt.
                if (attempt == maxAttempts) {
                    emit(Failed("轮询失败: ${e.message}"))
                    return@flow
                }
                continue
            }
            when (lastJob.status) {
                MusicJobStatus.SUCCESS -> break
                MusicJobStatus.FAILURE -> {
                    emit(Failed(lastJob.errorMessage ?: "生成失败"))
                    return@flow
                }
                else -> { /* keep polling */ }
            }
        }

        val audioUrl = lastJob.audioUrl
        if (audioUrl.isNullOrBlank()) {
            emit(Failed("未获取到音频链接"))
            return@flow
        }

        emit(Downloading())
        val track = finalizeBaseTrack(provider, request, config, audioUrl)
        finishWithOptionalVoiceConversion(track, voiceConversionConfig)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<MusicGenerationState>.finishWithOptionalVoiceConversion(
        baseTrack: MusicStudioTrack?,
        voiceConversionConfig: VoiceConversionConfig?
    ) {
        if (baseTrack == null) {
            emit(Failed("下载或保存失败"))
            return
        }

        val converter = voiceConversionProvider
        val cfg = voiceConversionConfig
        if (converter == null || cfg == null) {
            emit(Completed(track = baseTrack))
            return
        }

        emit(ConvertingVoice())
        val convertedTrack = withContext(Dispatchers.IO) {
            try {
                val convertedUrl = converter.convert(cfg, baseTrack.audioUrl)
                val convertedFile = File(musicDir, "${baseTrack.id}_rvc.mp3")
                converter.download(convertedUrl, convertedFile)

                val updated = baseTrack.copy(
                    audioUrl = convertedUrl,
                    localPath = convertedFile.absolutePath,
                    hasVoiceReplacement = true
                )
                replaceTrack(updated)
                // Keep the original base file as a fallback; just mark it as replaced.
                updated
            } catch (e: Exception) {
                DebugLog.e(TAG, "Voice conversion failed, returning base track", e)
                // Voice conversion is optional; do not fail the whole generation.
                baseTrack
            }
        }
        emit(Completed(track = convertedTrack))
    }

    private suspend fun finalizeBaseTrack(
        provider: MusicGenerationProvider,
        request: MusicGenerationRequest,
        config: MusicProviderConfig,
        audioUrl: String
    ): MusicStudioTrack? = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val file = File(musicDir, "$id.mp3")
            provider.download(audioUrl, file)
            val track = MusicStudioTrack(
                id = id,
                title = request.title,
                lyrics = request.lyrics,
                style = request.style ?: "",
                provider = config.provider,
                audioUrl = audioUrl,
                localPath = file.absolutePath,
                hasVoiceReplacement = false,
                createdAt = System.currentTimeMillis()
            )
            addTrack(track)
            track
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to finalize track", e)
            null
        }
    }

    companion object {
        private const val TAG = "MusicStudioRepository"
    }
}
