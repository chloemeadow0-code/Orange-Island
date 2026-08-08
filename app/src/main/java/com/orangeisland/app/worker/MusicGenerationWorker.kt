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

        val request = MusicGenerationRequest(
            title = title,
            lyrics = lyrics,
            style = style,
            model = model
        )

        // Generation produces the bare cloud track only. Voice replacement (RVC) is no longer
        // applied automatically here — it runs on demand per track via VoiceConversionWorker.
        val repository = MusicStudioRepository(
            applicationContext,
            providers = listOf(SunoMusicGenerationProvider(), ReplicateMusicGenerationProvider())
        )

        var finalResult: Result? = null
        try {
            repository.generate(config, request, voiceConversionConfig = null).collect { state ->
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
         * Shared tag applied to every music generation request. The Music Studio UI observes all
         * work carrying this tag so it can show a live progress bar and refresh the track list —
         * regardless of whether the generation was started from the Studio page or by the AI's
         * `generate_music` tool (which enqueues work independently of the ViewModel).
         */
        const val TAG_MUSIC_GENERATION = "music_generation"

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
                .addTag(TAG_MUSIC_GENERATION)
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
    }
}

/**
 * Runs a single RVC voice-conversion pass over an existing [MusicStudioTrack] on demand, producing
 * a new [com.orangeisland.app.data.music.VoiceVersion] attached to that track. Used by the Music
 * Studio detail sheet ("替换音色生成"); not part of the generation flow.
 *
 * Carries the same [MusicGenerationWorker.TAG_MUSIC_GENERATION] tag so the Studio's tag-based
 * progress observation picks it up without a separate channel.
 */
class VoiceConversionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return@withContext failure("Missing track id")
        val voiceModelUrl = inputData.getString(KEY_VOICE_MODEL_URL)
            ?: return@withContext failure("Missing voice model url")

        val settingsManager = SettingsManager(applicationContext)
        val apiKey = settingsManager.musicStudioReplicateApiKey.first()
        if (apiKey.isBlank()) return@withContext failure("请先在设置中配置 Replicate API Key")

        val config = VoiceConversionConfig(
            provider = "replicate_rvc",
            apiKey = apiKey,
            voiceModelUrl = voiceModelUrl
        )

        val repository = MusicStudioRepository(
            applicationContext,
            providers = listOf(SunoMusicGenerationProvider(), ReplicateMusicGenerationProvider()),
            voiceConversionProvider = ReplicateRvcVoiceConversionProvider()
        )

        var finalResult: Result? = null
        try {
            repository.convertVoice(trackId, voiceModelUrl, config).collect { state ->
                setProgress(workDataOf(KEY_PROGRESS_MESSAGE to state.message))
                when (state) {
                    is Completed -> finalResult = Result.success(
                        workDataOf(KEY_TRACK_ID to trackId)
                    )
                    is Failed -> finalResult = Result.failure(workDataOf(KEY_ERROR to state.message))
                    else -> { /* intermediate progress */ }
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Voice conversion worker failed", e)
            return@withContext failure("音色替换异常: ${e.message}")
        }

        finalResult ?: failure("音色替换未返回结果")
    }

    private fun failure(message: String?): Result =
        Result.failure(workDataOf(KEY_ERROR to (message ?: "unknown")))

    companion object {
        private const val TAG = "VoiceConversionWorker"

        const val KEY_TRACK_ID = "track_id"
        const val KEY_VOICE_MODEL_URL = "voice_model_url"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_ERROR = "error"

        /** Unique work name per track, so a second RVC request on the same track replaces the first. */
        fun workName(trackId: String): String = "music_voice_conversion_$trackId"

        fun enqueue(context: Context, trackId: String, voiceModelUrl: String): String {
            val name = workName(trackId)
            val work = OneTimeWorkRequestBuilder<VoiceConversionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TRACK_ID to trackId,
                        KEY_VOICE_MODEL_URL to voiceModelUrl
                    )
                )
                .addTag(MusicGenerationWorker.TAG_MUSIC_GENERATION)
                .addTag(name)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, work)
            return name
        }
    }
}
