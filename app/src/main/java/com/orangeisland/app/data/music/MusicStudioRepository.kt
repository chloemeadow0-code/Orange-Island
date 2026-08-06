package com.orangeisland.app.data.music

import android.content.Context
import com.orangeisland.app.api.music.MusicGenerationProvider
import com.orangeisland.app.api.music.MusicJobStatus
import com.orangeisland.app.api.music.MusicProviderConfig
import com.orangeisland.app.api.music.MusicGenerationRequest
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
data class Completed(override val message: String = "生成完成", val track: MusicStudioTrack) : MusicGenerationState()
data class Failed(override val message: String = "生成失败") : MusicGenerationState()

/**
 * Orchestrates cloud music generation providers and persists the resulting
 * tracks in the app's private storage.
 */
class MusicStudioRepository(
    private val context: Context,
    providers: List<MusicGenerationProvider>
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
     * Run a full generation: submit, poll, download, and persist.
     * Emits [MusicGenerationState] updates so the UI can show progress.
     */
    fun generate(config: MusicProviderConfig, request: MusicGenerationRequest): Flow<MusicGenerationState> = flow {
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
            val track = finalizeTrack(provider, request, config, job.audioUrl, false)
            if (track != null) {
                emit(Completed(track = track))
            } else {
                emit(Failed("保存失败"))
            }
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
        val track = finalizeTrack(provider, request, config, audioUrl, false)
        if (track != null) {
            emit(Completed(track = track))
        } else {
            emit(Failed("下载或保存失败"))
        }
    }

    private suspend fun finalizeTrack(
        provider: MusicGenerationProvider,
        request: MusicGenerationRequest,
        config: MusicProviderConfig,
        audioUrl: String,
        hasVoiceReplacement: Boolean
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
                hasVoiceReplacement = hasVoiceReplacement,
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
