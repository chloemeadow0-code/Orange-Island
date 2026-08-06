package com.orangeisland.app.api.music

import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.util.DebugLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Replicate RVC voice-conversion provider.
 *
 * Mirrors the second stage of the original plugin: take a generated song URL,
 * run it through a Replicate RVC model, and return the converted audio URL.
 */
class ReplicateRvcVoiceConversionProvider : VoiceConversionProvider {

    override val id: String = "replicate_rvc"
    override val displayName: String = "Replicate RVC"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun convert(config: VoiceConversionConfig, sourceUrl: String): String {
        val apiKey = config.apiKey.trim()
        if (apiKey.isBlank()) throw MusicProviderException("Replicate API key is not configured")

        val voiceModelUrl = config.voiceModelUrl?.trim()
            ?: config.modelVersion?.trim()
            ?: throw MusicProviderException("RVC voice model URL is not configured")

        val modelVersion = config.custom["model_version"]?.trim()
            ?: DEFAULT_RVC_VERSION

        val headers = mapOf(
            "Authorization" to "Token $apiKey",
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )

        val body = buildJsonObject {
            put("version", modelVersion)
            put("input", buildJsonObject {
                put("song_input", sourceUrl)
                put("rvc_model", voiceModelUrl)
                put("index_rate", 0.75)
                put("clean_vocals", true)
                put("protect_rate", 0.33)
                put("split_vocals", true)
                put("autotune_vocals", false)
                put("f0_method", "rmvpe")
                put("pitch_change", -12)
            })
        }.toString()

        val response = HttpClient.post("https://api.replicate.com/v1/predictions", body, headers)
            ?: throw MusicProviderException("RVC submit returned no response")

        val result = json.parseToJsonElement(response)
        val predictionId = result.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: throw MusicProviderException("RVC submit failed: $response")

        // Poll with backoff, matching the plugin's ~15 minute ceiling.
        val maxAttempts = 150
        var lastResult: JsonObject? = null
        for (attempt in 1..maxAttempts) {
            val interval = when {
                attempt <= 20 -> 3_000L
                attempt <= 60 -> 5_000L
                else -> 8_000L
            }
            kotlinx.coroutines.delay(interval)

            val poll = HttpClient.get(
                "https://api.replicate.com/v1/predictions/$predictionId",
                headers
            ) ?: continue

            val pollResult = json.parseToJsonElement(poll).jsonObject
            lastResult = pollResult
            val status = pollResult["status"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            when (status.lowercase()) {
                "succeeded" -> {
                    val output = pollResult["output"]
                    val url = resolveOutputUrl(output)
                    if (!url.isNullOrBlank()) return url
                    throw MusicProviderException("RVC succeeded but no output URL: $poll")
                }
                "failed", "canceled" -> {
                    val error = pollResult["error"]?.jsonPrimitive?.contentOrNull
                        ?: pollResult["logs"]?.jsonPrimitive?.contentOrNull
                        ?: "RVC prediction $status"
                    throw MusicProviderException(error)
                }
                else -> { /* keep polling */ }
            }
        }
        throw MusicProviderException("RVC 转换超时，最后状态: ${lastResult?.get("status")}")
    }

    override suspend fun download(url: String, destination: File): File {
        val bytes = HttpClient.getBytes(url) ?: throw MusicProviderException("Failed to download RVC audio from $url")
        destination.parentFile?.mkdirs()
        destination.writeBytes(bytes)
        return destination
    }

    private fun resolveOutputUrl(output: JsonElement?): String? {
        return when (output) {
            is JsonPrimitive -> output.contentOrNull
            is JsonArray -> output.firstOrNull()?.jsonPrimitive?.contentOrNull
            is JsonObject -> output["audio"]?.jsonPrimitive?.contentOrNull
                ?: output["audio_url"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }

    companion object {
        /**
         * Default Replicate RVC model version used by the original plugin.
         * Users can override it via `custom["model_version"]`.
         */
        const val DEFAULT_RVC_VERSION =
            "5598e8029cbd7e9268db84ce8c2a334eab6ebccbee67b78cf63c38e964379e15"
    }
}
