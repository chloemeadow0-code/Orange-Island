package com.orangeisland.app.api.music

import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.util.DebugLog
import kotlinx.serialization.json.Json
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
 * Cloud music generation provider backed by a Suno-compatible proxy API.
 *
 * Translates the previous plugin's `main.js` Suno flow into Kotlin, keeping the
 * lenient response parsing because Suno proxies return inconsistent JSON shapes.
 */
class SunoMusicGenerationProvider : MusicGenerationProvider {

    override val id: String = "suno"
    override val displayName: String = "Suno"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun submit(config: MusicProviderConfig, request: MusicGenerationRequest): MusicJob {
        val baseUrl = config.baseUrl?.trimEnd('/') ?: error("Suno API base URL is not configured")
        val apiKey = config.apiKey.trimStart().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "")

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )

        val gptDescriptionPrompt = buildString {
            append("A song")
            if (request.title.isNotBlank()) {
                append(" titled \"${request.title}\"")
            }
            append(", lyrics: ${request.lyrics}")
            if (!request.style.isNullOrBlank()) {
                append(", style: ${request.style}")
            }
        }

        val body = buildJsonObject {
            put("gpt_description_prompt", gptDescriptionPrompt)
            put("prompt", request.lyrics.take(400) + "\n[Outro]\n[End]")
            put("make_instrumental", request.makeInstrumental)
            put("mv", request.model?.trim() ?: "chirp-v4")
        }.toString()

        val response = HttpClient.post("$baseUrl/suno/submit/music", body, headers)
            ?: throw MusicProviderException("Suno submit returned no response")

        val result = json.parseToJsonElement(response)
        val taskId = extractTaskId(result)
            ?: throw MusicProviderException("Suno submit failed: $response")

        return MusicJob(id = taskId, provider = id, status = MusicJobStatus.PENDING)
    }

    override suspend fun poll(config: MusicProviderConfig, job: MusicJob): MusicJob {
        val baseUrl = config.baseUrl?.trimEnd('/') ?: error("Suno API base URL is not configured")
        val apiKey = config.apiKey.trimStart().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "")

        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )

        val response = HttpClient.get("$baseUrl/suno/fetch/${job.id}", headers)
            ?: return job.copy(status = MusicJobStatus.PROCESSING)

        val result = json.parseToJsonElement(response)
        val taskInfo = resolveTaskInfo(result)
        val status = taskInfo?.get("status")?.jsonPrimitive?.contentOrNull
            ?: result.jsonObject["status"]?.jsonPrimitive?.contentOrNull
            ?: ""

        return when (status.uppercase()) {
            "SUCCESS" -> {
                val audioUrl = findAudioUrl(taskInfo, result)
                if (audioUrl.isNullOrBlank()) {
                    job.copy(status = MusicJobStatus.FAILURE, errorMessage = "Suno succeeded but no audio URL found")
                } else {
                    job.copy(status = MusicJobStatus.SUCCESS, audioUrl = audioUrl)
                }
            }
            "FAILURE", "FAILED", "ERROR" -> {
                job.copy(status = MusicJobStatus.FAILURE, errorMessage = "Suno generation failed: $response")
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

    // ─── Response helpers ───────────────────────────────────────────────────

    private fun extractTaskId(element: JsonElement): String? {
        val root = element as? JsonObject ?: return null
        val data = root["data"]
        return when {
            data is JsonPrimitive && data.isString -> data.content
            data is JsonObject -> data["task_id"]?.jsonPrimitive?.contentOrNull
                ?: data["id"]?.jsonPrimitive?.contentOrNull
            else -> root["task_id"]?.jsonPrimitive?.contentOrNull
        }
    }

    private fun resolveTaskInfo(result: JsonElement): JsonObject? {
        val root = result as? JsonObject ?: return null
        val data = root["data"] ?: return root
        return when {
            data is JsonObject -> data
            data is kotlinx.serialization.json.JsonArray && data.isNotEmpty() -> data.firstOrNull()?.jsonObject
            else -> root
        }
    }

    private fun findAudioUrl(taskInfo: JsonObject?, rawResult: JsonElement): String? {
        // Preferred paths inside the task info.
        val innerData = taskInfo?.get("data")
        if (innerData is kotlinx.serialization.json.JsonArray && innerData.isNotEmpty()) {
            innerData.firstOrNull()?.jsonObject?.get("audio_url")?.jsonPrimitive?.contentOrNull?.let { return it }
        } else if (innerData is JsonObject) {
            innerData["audio_url"]?.jsonPrimitive?.contentOrNull?.let { return it }
        }
        taskInfo?.get("audio_url")?.jsonPrimitive?.contentOrNull?.let { return it }
        (rawResult as? JsonObject)?.get("audio_url")?.jsonPrimitive?.contentOrNull?.let { return it }

        // Fallback: recursive search for any string that looks like an audio URL.
        return extractAudioUrlRecursive(rawResult)
    }

    private fun extractAudioUrlRecursive(element: JsonElement, candidates: MutableList<String> = mutableListOf()): String? {
        when (element) {
            is JsonPrimitive -> {
                val text = element.contentOrNull ?: return null
                if (text.startsWith("http", ignoreCase = true)) {
                    val low = text.lowercase()
                    if (listOf(".mp3", ".wav", "audio", "suno", "cdn", "/stream", "output").any { low.contains(it) }) {
                        candidates.add(text)
                    }
                }
            }
            is JsonObject -> {
                val preferredKeys = listOf("audio_url", "output", "url", "audio", "video_url", "song", "download_url", "file")
                preferredKeys.forEach { key ->
                    element[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http", ignoreCase = true) }?.let {
                        candidates.add(it)
                    }
                }
                element.values.forEach { extractAudioUrlRecursive(it, candidates) }
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { extractAudioUrlRecursive(it, candidates) }
            }
            else -> {}
        }
        return candidates.firstOrNull()
    }
}

class MusicProviderException(message: String) : Exception(message)
