package com.orangeisland.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.orangeisland.app.api.music.MusicGenerationRequest
import com.orangeisland.app.api.music.MusicProviderConfig
import com.orangeisland.app.api.music.ReplicateMusicGenerationProvider
import com.orangeisland.app.api.music.ReplicateRvcVoiceConversionProvider
import com.orangeisland.app.api.music.SunoMusicGenerationProvider
import com.orangeisland.app.api.music.VoiceConversionConfig
import com.orangeisland.app.api.music.VoiceConversionProvider
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.music.Completed
import com.orangeisland.app.data.music.ConvertingVoice
import com.orangeisland.app.data.music.Failed
import com.orangeisland.app.data.music.MusicGenerationState
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.data.music.Processing
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * WorkManager-backed music generation worker.
 *
 * Runs the whole submit → poll → download → (optional RVC voice conversion) → persist flow
 * in the background, so a generation survives leaving the Music Studio page or locking the
 * device. Progress is emitted via [setProgress] so the UI can observe it.
 */
class MusicGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val providerId = inputData.getString(KEY_PROVIDER_ID) ?: return@withContext failure("Missing provider")
        val title = inputData.getString(KEY_TITLE) ?: return@withContext failure("Missing title")
        val lyrics = inputData.getString(KEY_LYRICS) ?: return@withContext failure("Missing lyrics")
        val style = inputData.getString(KEY_STYLE)
        val model = inputData.getString(KEY_MODEL)

        val settingsManager = SettingsManager(applicationContext)
        val config = try {
            buildConfig(settingsManager, providerId)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to build provider config", e)
            return@withContext failure("配置错误: ${e.message}")
        }

        val voiceConversionConfig = try {
            buildVoiceConversionConfig(settingsManager)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to build voice conversion config", e)
            null
        }

        val request = MusicGenerationRequest(
            title = title,
            lyrics = lyrics,
            style = style,
            model = model
        )

        val voiceConversionProvider: VoiceConversionProvider? = if (voiceConversionConfig != null) {
            ReplicateRvcVoiceConversionProvider()
        } else null

        val repository = MusicStudioRepository(
            applicationContext,
            providers = listOf(SunoMusicGenerationProvider(), ReplicateMusicGenerationProvider()),
            voiceConversionProvider = voiceConversionProvider
        )

        var finalResult: Result? = null
        try {
            repository.generate(config, request, voiceConversionConfig).collect { state ->
                setProgress(progressData(state))
                when (state) {
                    is Completed -> {
                        finalResult = Result.success(
                            workDataOf(
                                KEY_TRACK_ID to state.track.id,
                                KEY_TITLE to state.track.title,
                                KEY_HAS_VOICE_REPLACEMENT to state.track.hasVoiceReplacement
                            )
                        )
                    }
                    is Failed -> {
                        finalResult = Result.failure(
                            workDataOf(KEY_ERROR to state.message)
                        )
                    }
                    else -> { /* intermediate progress */ }
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Generation worker failed", e)
            return@withContext failure("生成异常: ${e.message}")
        }

        finalResult ?: failure("生成未返回结果")
    }

    private fun failure(message: String?): Result =
        Result.failure(workDataOf(KEY_ERROR to (message ?: "unknown")))

    private fun progressData(state: MusicGenerationState): Data {
        return workDataOf(
            KEY_PROGRESS_MESSAGE to state.message,
            KEY_PROGRESS_ATTEMPT to when (state) {
                is Processing -> state.attempt
                else -> 0
            },
            KEY_PROGRESS_IS_VOICE_CONVERSION to (state is ConvertingVoice)
        )
    }

    companion object {
        private const val TAG = "MusicGenerationWorker"

        const val KEY_PROVIDER_ID = "provider_id"
        const val KEY_TITLE = "title"
        const val KEY_LYRICS = "lyrics"
        const val KEY_STYLE = "style"
        const val KEY_MODEL = "model"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_PROGRESS_ATTEMPT = "progress_attempt"
        const val KEY_PROGRESS_IS_VOICE_CONVERSION = "is_voice_conversion"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_TITLE_OUT = "title_out"
        const val KEY_HAS_VOICE_REPLACEMENT = "has_voice_replacement"
        const val KEY_ERROR = "error"

        /** Build a unique work name for a generation request. */
        fun workName(trackId: String): String = "music_generation_$trackId"

        /**
         * Enqueue a background music generation and return the work name.
         * [trackId] is a client-generated UUID used to identify the request.
         */
        fun enqueue(
            context: Context,
            trackId: String,
            providerId: String,
            title: String,
            lyrics: String,
            style: String? = null,
            model: String? = null
        ): String {
            val work = OneTimeWorkRequestBuilder<MusicGenerationWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PROVIDER_ID to providerId,
                        KEY_TITLE to title,
                        KEY_LYRICS to lyrics,
                        KEY_STYLE to style,
                        KEY_MODEL to model
                    )
                )
                .build()

            val name = workName(trackId)
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.REPLACE,
                work
            )
            return name
        }

        /**
         * Cancel a pending/running generation by its [trackId].
         */
        fun cancel(context: Context, trackId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(trackId))
        }

        private suspend fun buildConfig(
            settingsManager: SettingsManager,
            providerId: String
        ): MusicProviderConfig {
            return when (providerId) {
                "suno" -> MusicProviderConfig(
                    provider = "suno",
                    apiKey = settingsManager.musicStudioSunoApiKey.first(),
                    baseUrl = settingsManager.musicStudioSunoApiUrl.first()
                )
                "replicate" -> MusicProviderConfig(
                    provider = "replicate",
                    apiKey = settingsManager.musicStudioReplicateApiKey.first(),
                    model = settingsManager.musicStudioReplicateModelVersion.first().ifBlank { null }
                )
                else -> throw IllegalArgumentException("Unknown music provider: $providerId")
            }
        }

        private suspend fun buildVoiceConversionConfig(
            settingsManager: SettingsManager
        ): VoiceConversionConfig? {
            if (!settingsManager.musicStudioVoiceReplacementEnabled.first()) return null
            val apiKey = settingsManager.musicStudioReplicateApiKey.first()
            val modelUrl = settingsManager.musicStudioRvcModelUrl.first()
            if (apiKey.isBlank() || modelUrl.isBlank()) return null
            return VoiceConversionConfig(
                provider = "replicate_rvc",
                apiKey = apiKey,
                voiceModelUrl = modelUrl
            )
        }
    }
}
