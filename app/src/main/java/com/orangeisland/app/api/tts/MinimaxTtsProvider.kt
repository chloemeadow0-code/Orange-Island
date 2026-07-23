package com.orangeisland.app.api.tts

import android.util.Base64
import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * MiniMax text-to-speech provider.
 *
 * Uses the /v1/t2a_v2 endpoint. The audio is returned as a hex string inside JSON.
 * Docs: https://platform.minimaxi.com/document/T2A%20v2
 */
class MinimaxTtsProvider : TtsProvider {

    companion object {
        private const val BASE_URL = "https://api.minimax.chat/v1/t2a_v2"
        private const val DEFAULT_MODEL = "speech-01-turbo"
    }

    override suspend fun synthesize(
        text: String,
        voiceId: String?,
        apiKey: String,
        config: TtsConfig
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val effectiveVoice = voiceId?.takeIf { it.isNotBlank() } ?: "male-qn-qingse"
        val model = config.model.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        val format = config.outputFormat.takeIf { it.isNotBlank() } ?: "mp3"
        try {
            val body = buildJsonObject {
                put("model", model)
                put("text", text)
                put("voice_setting", buildJsonObject {
                    put("voice_id", effectiveVoice)
                    if (config.speed != 1.0f) {
                        put("speed", config.speed.coerceIn(0.5f, 2.0f))
                    }
                    if (config.volume != 1.0f) {
                        put("vol", config.volume.coerceIn(0.1f, 10.0f))
                    }
                    if (config.pitch != 0.0f) {
                        put("pitch", config.pitch.coerceIn(-12f, 12f))
                    }
                })
                put("audio_setting", buildJsonObject {
                    put("format", format)
                    put("sample_rate", 32000)
                })
            }.toString()
            val response = HttpClient.post(
                BASE_URL,
                body,
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                )
            ) ?: return@withContext null

            val json = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response)
            val baseResp = json["base_resp"]?.jsonObject
            val statusCode = (baseResp?.get("status_code") as? JsonPrimitive)?.content?.toIntOrNull()
            if (statusCode != 0) {
                val msg = (baseResp?.get("status_msg") as? JsonPrimitive)?.content ?: "unknown error"
                DebugLog.e("MinimaxTts", "API error: $statusCode — $msg")
                return@withContext null
            }

            val data = json["data"]?.jsonObject ?: return@withContext null
            val audioHex = (data["audio"] as? JsonPrimitive)?.content
                ?: return@withContext null
            // MiniMax returns hex-encoded MP3
            decodeHex(audioHex)
        } catch (e: Exception) {
            DebugLog.e("MinimaxTts", "synthesize failed", e)
            null
        }
    }

    private fun decodeHex(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4)
                + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
