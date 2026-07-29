package com.orangeisland.app.api.stt

import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * SiliconFlow (硅基流动) speech-to-text provider.
 *
 * Uses the /v1/audio/transcriptions endpoint. Accepts multipart/form-data with a
 * `file` (binary audio) and a `model` field, and returns JSON `{"text": "..."}`.
 * Supported models include `FunAudioLLM/SenseVoiceSmall` and `TeleAI/TeleSpeechASR`.
 * Docs: https://api-docs.siliconflow.cn/docs/api/audio-transcriptions-post
 */
class SiliconFlowSttProvider : SttProvider {

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.siliconflow.cn"
        private const val DEFAULT_MODEL = "FunAudioLLM/SenseVoiceSmall"
        private const val PATH = "/v1/audio/transcriptions"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun transcribe(
        audioBytes: ByteArray,
        fileName: String,
        apiKey: String,
        config: SttConfig
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val model = config.model.ifBlank { DEFAULT_MODEL }
        // Resolve the endpoint URL. If the user supplied a baseUrl that already contains the
        // transcription path (or any path), use it verbatim; otherwise append the default path to
        // the bare host. This avoids the double-path bug (host + PATH + PATH → 404) when someone
        // pastes the full endpoint URL into the Base URL field.
        val url = resolveEndpointUrl(config.baseUrl)
        try {
            val effectiveName = if (fileName.isBlank()) "audio.m4a" else fileName
            // Infer a content type from the extension; default to audio/mpeg when unknown.
            val mimeType = mimeTypeFor(effectiveName)
            val fields = mapOf("model" to model)
            android.util.Log.e("SiliconFlowStt", "POST $url | bytes=${audioBytes.size} model=$model")
            val response = HttpClient.postMultipart(
                url = url,
                fileField = "file",
                fileName = effectiveName,
                fileMimeType = mimeType,
                fileBytes = audioBytes,
                textFields = fields,
                headers = mapOf("Authorization" to "Bearer $apiKey")
            )
            if (response == null) {
                android.util.Log.e("SiliconFlowStt", "HTTP request failed (null response) for $url")
                return@withContext null
            }
            android.util.Log.e("SiliconFlowStt", "response: ${response.take(300)}")

            val parsed = try {
                json.parseToJsonElement(response).jsonObject
            } catch (e: Exception) {
                DebugLog.e("SiliconFlowStt", "Non-JSON response: ${response.take(200)}", e)
                return@withContext null
            }
            // Some error responses carry {"code":..., "message":...} with no "text".
            val rawText = (parsed["text"] as? JsonPrimitive)?.content?.ifBlank { null }
            // SenseVoiceSmall appends emotion/event tags and emoji that pollute the transcript and
            // confuse the LLM. Strip them all (see [stripSenseVoiceTags]).
            val cleaned = rawText?.let { stripSenseVoiceTags(it) }
            android.util.Log.e("SiliconFlowStt", "rawText=$rawText -> cleaned=$cleaned")
            cleaned
        } catch (e: Exception) {
            DebugLog.e("SiliconFlowStt", "transcribe failed", e)
            null
        }
    }

    /**
     * Resolve the final endpoint URL from the user-supplied [baseUrl]:
     *  - blank → default host + default path
     *  - bare host (no path beyond "/") → host + default path
     *  - already contains the path → use verbatim (avoid double-path → 404)
     */
    private fun resolveEndpointUrl(baseUrl: String): String {
        val raw = baseUrl.trim()
        if (raw.isBlank()) return DEFAULT_BASE_URL + PATH
        // Does the path already include "/audio/transcriptions" (or any non-trivial path)?
        val pathPart = runCatching { java.net.URI(raw).path ?: "" }.getOrDefault("")
        val hasPath = pathPart.isNotBlank() && pathPart != "/"
        return if (hasPath) raw.trimEnd('/') else raw.trimEnd('/') + PATH
    }

    /**
     * Strip SenseVoice's special tokens AND emoji from the transcript. The model emits:
     *  - markup tags: `<|sad|>`, `<|HAPPY|>`, `<|Speech|>`, `<|BAP|>`, `<|woitn|>`, `<|nospeech|>` …
     *  - bracketed labels: `[sad]`, `(happy)` …
     *  - emoji: 😀😢😠 … (SenseVoice sometimes appends an emotion emoji)
     * All of these leak into the dialogue and confuse the conversational LLM. Removed here.
     */
    private fun stripSenseVoiceTags(text: String): String {
        var cleaned = text
        // 1. Remove "<|...|>" special tokens.
        cleaned = cleaned.replace(Regex("<\\|[^|]*\\|>"), "")
        // 2. Remove bracketed/parenthesised short emotion labels like [sad], (happy), 【伤心】.
        cleaned = cleaned.replace(Regex("[\\[\\(【《]\\s*[a-zA-Z\\u4e00-\\u9fa5]{1,8}\\s*[\\]\\)】》]"), "")
        // 3. Remove emoji (most pictographic / symbol ranges). Build char-by-char so surrogate
        //    pairs (emoji outside the BMP) are handled correctly.
        val sb = StringBuilder(cleaned.length)
        var i = 0
        while (i < cleaned.length) {
            val cp = cleaned.codePointAt(i)
            val w = Character.charCount(cp)
            if (!isEmoji(cp)) sb.appendCodePoint(cp)
            i += w
        }
        cleaned = sb.toString()
        // 4. Tidy whitespace.
        return cleaned.replace(Regex("\\s{2,}"), " ").trim()
    }

    /** True for emoji / pictograph / symbol code points (covers the ranges SenseVoice emits). */
    private fun isEmoji(cp: Int): Boolean {
        return cp in 0x1F300..0x1FAFF ||   // symbols & pictographs, supplemental symbols
            cp in 0x2600..0x27BF ||         // misc symbols & dingbats
            cp in 0x2190..0x21FF ||         // arrows
            cp in 0x2300..0x23FF ||         // misc technical
            cp in 0xFE00..0xFE0F ||         // variation selectors
            cp == 0x200D ||                 // zero-width joiner (keeps emoji together)
            cp in 0x1F000..0x1F02F ||       // mahjong / domino
            cp in 0x1F0A0..0x1F0FF          // playing cards
    }

    private fun mimeTypeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "m4a", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "webm" -> "audio/webm"
            else -> "audio/mpeg"
        }
    }
}
