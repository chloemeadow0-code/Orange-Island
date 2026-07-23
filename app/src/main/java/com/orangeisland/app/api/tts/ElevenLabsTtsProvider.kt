package com.orangeisland.app.api.tts

import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * ElevenLabs text-to-speech provider.
 *
 * Uses the /v1/text-to-speech/{voice_id} endpoint. Returns MP3 bytes.
 * Docs: https://elevenlabs.io/docs/api-reference/text-to-speech
 */
class ElevenLabsTtsProvider : TtsProvider {

    companion object {
        private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
        private const val DEFAULT_MODEL = "eleven_multilingual_v2"
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String?,
        apiKey: String,
        config: TtsConfig
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val effectiveVoice = voiceId?.takeIf { it.isNotBlank() } ?: "21m00Tcm4TlvDq8ikWAM"
        val model = config.model.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        try {
            val body = buildJsonObject {
                put("text", text)
                put("model_id", model)
                put("voice_settings", buildJsonObject {
                    put("stability", config.stability.coerceIn(0f, 1f))
                    put("similarity_boost", config.similarityBoost.coerceIn(0f, 1f))
                    if (config.speed != 1.0f) {
                        put("speed", config.speed.coerceIn(0.7f, 1.2f))
                    }
                    if (config.style > 0f) {
                        put("style", config.style.coerceIn(0f, 1f))
                        put("use_speaker_boost", true)
                    }
                })
            }.toString()

            val url = buildString {
                append("$BASE_URL/$effectiveVoice")
                if (config.outputFormat.isNotBlank()) {
                    append("?output_format=${config.outputFormat}")
                }
            }

            HttpClient.postBytes(
                url,
                body,
                headers = mapOf(
                    "xi-api-key" to apiKey,
                    "Content-Type" to "application/json"
                )
            )
        } catch (e: Exception) {
            DebugLog.e("ElevenLabsTts", "synthesize failed", e)
            null
        }
    }
}
