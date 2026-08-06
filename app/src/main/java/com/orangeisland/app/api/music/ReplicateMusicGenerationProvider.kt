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
 * Generic Replicate music generation provider.
 *
 * The user supplies a model [version] (e.g. `meta/musicgen:...`) and an API key.
 * Lyrics are sent as the `prompt` input; style is appended to the prompt when present.
 * Output is resolved from the prediction result as a string URL or the first element of
 * an array, matching Replicate's common response shapes.
 */
class ReplicateMusicGenerationProvider : MusicGenerationProvider {

    override val id: String = "replicate"
    override val displayName: String = "Replicate"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun submit(config: MusicProviderConfig, request: MusicGenerationRequest): MusicJob {
        val apiKey = config.apiKey.trim()
        if (apiKey.isBlank()) throw MusicProviderException("Replicate API key is not configured")

        val version = config.model?.trim() ?: config.custom["model_version"]?.trim()
            ?: throw MusicProviderException("Replicate model version is not configured")

        val headers = mapOf(
            "Authorization" to "Token $apiKey",
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )

        val prompt = buildString {
            append(request.lyrics)
            if (!request.style.isNullOrBlank()) {
                append("\nStyle: ")
                append(request.style)
            }
        }

        val body = buildJsonObject {
            put("version", version)
            put("input", buildJsonObject {
                put("prompt", prompt)
                if (!request.style.isNullOrBlank()) put("style", request.style)
                put("duration", 8)
            })
        }.toString()

        val response = HttpClient.post("https://api.replicate.com/v1/predictions", body, headers)
            ?: throw MusicProviderException("Replicate submit returned no response")

        val result = json.parseToJsonElement(response)
        val predictionId = result.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: throw MusicProviderException("Replicate submit failed: $response")

        return MusicJob(id = predictionId, provider = id, status = MusicJobStatus.PENDING)
    }

    override suspend fun poll(config: MusicProviderConfig, job: MusicJob): MusicJob {
        val apiKey = config.apiKey.trim()
        val headers = mapOf(
            "Authorization" to "Token $apiKey",
            "Accept" to "application/json"
        )

        val response = HttpClient.get("https://api.replicate.com/v1/predictions/${job.id}", headers)
            ?: return job.copy(status = MusicJobStatus.PROCESSING)

        val result = json.parseToJsonElement(response).jsonObject
        val status = result["status"]?.jsonPrimitive?.contentOrNull ?: "unknown"

        return when (status.lowercase()) {
            "succeeded" -> {
                val output = result["output"]
                val audioUrl = resolveOutputUrl(output)
                if (audioUrl.isNullOrBlank()) {
                    job.copy(status = MusicJobStatus.FAILURE, errorMessage = "Replicate succeeded but no output URL")
                } else {
                    job.copy(status = MusicJobStatus.SUCCESS, audioUrl = audioUrl)
                }
            }
            "failed", "canceled" -> {
                val error = result["error"]?.jsonPrimitive?.contentOrNull
                    ?: result["logs"]?.jsonPrimitive?.contentOrNull
                    ?: "Replicate prediction $status"
                job.copy(status = MusicJobStatus.FAILURE, errorMessage = error)
            }
            else -> job.copy(status = MusicJobStatus.PROCESSING)
        }
    }

    override suspend fun download(url: String, destination: File): File {
        val bytes = HttpClient.getBytes(url) ?: throw MusicProviderException("Failed to download audio from $url")
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
}
