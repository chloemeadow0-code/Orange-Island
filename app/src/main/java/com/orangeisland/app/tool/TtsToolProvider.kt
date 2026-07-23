package com.orangeisland.app.tool

import android.app.Application
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.api.tts.ElevenLabsTtsProvider
import com.orangeisland.app.api.tts.MinimaxTtsProvider
import com.orangeisland.app.api.tts.TtsConfig
import com.orangeisland.app.api.tts.TtsProvider
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/**
 * Tool that synthesizes speech from text via a third-party TTS backend
 * (ElevenLabs or MiniMax). The generated MP3 is written to filesDir as
 * `audio_<uuid>.mp3` and its path is collected in [pending] so the
 * GenerationManager can attach it to the model message for inline playback.
 */
class TtsToolProvider(private val app: Application) : ToolProvider {

    private val elevenLabs = ElevenLabsTtsProvider()
    private val minimax = MinimaxTtsProvider()

    /** File paths of audio produced since the last [drainAudio]. Thread-safe. */
    private val pending = java.util.Collections.synchronizedList(mutableListOf<String>())

    /** Atomically take and clear the audio generated so far. */
    fun drainAudio(): List<String> = synchronized(pending) {
        val copy = pending.toList()
        pending.clear()
        copy
    }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.ttsEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "speak",
                description = "Convert text into spoken audio and play it to the user. Use this when the user explicitly asks for a voice reply, asks you to read something aloud, or when a voice response feels more natural than plain text. Do NOT call this for every message — only when voice adds real value.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "text" to ToolProperty("string", "The text to synthesize into speech. Keep it concise for better listening experience."),
                        "voice_id" to ToolProperty("string", "Optional voice ID. Leave empty to use the user's default setting.")
                    ),
                    required = listOf("text")
                )
            ))
        )
    }

    override fun handles(name: String): Boolean = name == "speak"

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        val argsStr = arguments.ifBlank { "{}" }
        val args = try {
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        } catch (_: Exception) { emptyMap() }
        val text = (args["text"] as? JsonPrimitive)?.content
            ?: return err("no_text", "Missing 'text' parameter.")
        val voiceId = (args["voice_id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: ctx.ttsVoiceId

        val apiKey = ctx.ttsApiKey
        if (apiKey.isBlank()) return err("no_api_key", "TTS API key is not configured.")

        val provider: TtsProvider = when (ctx.ttsProvider.lowercase()) {
            "elevenlabs" -> elevenLabs
            "minimax" -> minimax
            else -> elevenLabs
        }

        val ttsConfig = TtsConfig(
            model = ctx.ttsModel,
            speed = ctx.ttsSpeed,
            outputFormat = ctx.ttsOutputFormat,
            stability = ctx.ttsStability,
            similarityBoost = ctx.ttsSimilarityBoost,
            style = ctx.ttsStyle,
            volume = ctx.ttsVolume,
            pitch = ctx.ttsPitch
        )

        return withContext(Dispatchers.IO) {
            try {
                val bytes = provider.synthesize(text, voiceId, apiKey, ttsConfig)
                    ?: return@withContext err("synthesis_failed", "The TTS provider returned no audio.")

                val file = File(app.filesDir, "audio_${UUID.randomUUID()}.mp3")
                file.outputStream().use { it.write(bytes) }
                pending.add(file.absolutePath)

                buildJsonObject {
                    put("type", "tts")
                    put("status", "ok")
                    put("duration_seconds", estimateDuration(bytes.size))
                }.toString()
            } catch (e: Exception) {
                DebugLog.e("TtsTool", "speak failed", e)
                err("generation_error", e.message)
            }
        }
    }

    private fun estimateDuration(byteCount: Int): Int {
        // Rough heuristic for MP3 @ ~128 kbps: 1 second ≈ 16 KB
        return (byteCount / 16_000).coerceAtLeast(1)
    }

    private fun err(code: String, message: String?): String = buildJsonObject {
        put("type", "tts")
        put("error", code)
        if (!message.isNullOrBlank()) put("message", message)
    }.toString()
}
