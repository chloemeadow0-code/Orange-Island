package com.orangeisland.app.tool

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.VoiceCallGate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exposes the `make_voice_call` tool so the model can proactively start a voice conversation with
 * the user — the AI equivalent of "let me just call you". The call itself is run by the call-loop
 * manager ([com.orangeisland.app.viewmodel.VoiceCallManager]); this provider's only job is to ring
 * the user via the [VoiceCallGate] (which suspends until they answer/decline) and report the
 * outcome back to the model.
 *
 * Why a tool rather than always-on: a call hijacks the screen and the mic, so it must be the
 * model's explicit decision. The tool is only surfaced when both STT and TTS are configured (see
 * [GenerationContext.voiceCallEnabled]); otherwise the model never even sees it.
 *
 * Modelled on [TtsToolProvider] for structure and on [SensitiveToolApprovalGate]-style gating for
 * the suspend-until-user-answers pattern.
 */
class VoiceCallToolProvider(
    private val gate: VoiceCallGate?
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.voiceCallEnabled || gate == null) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "make_voice_call",
                description = "Call the user for a real-time voice conversation (like a phone " +
                    "call). A full-screen incoming-call screen rings the user; if they answer, " +
                    "you and they will talk back and forth by voice (you speak via TTS, they " +
                    "speak via the microphone + speech-to-text). Use this proactively when a " +
                    "topic is easier to discuss by voice than text, when the user seems to want a " +
                    "quick chat, or when they hint at wanting a call. Pass a short reason that " +
                    "shows on the incoming-call screen.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "reason" to ToolProperty(
                            "string",
                            "A short (<= 20 char) reason shown on the incoming-call screen, e.g. " +
                                "\"聊聊天\" or \"关于行程\"."
                        )
                    ),
                    required = listOf("reason")
                )
            ))
        )
    }

    override fun handles(name: String): Boolean = name == "make_voice_call"

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (gate == null) return err("not_available", "Voice call is not available in this context.")
        val args = try {
            json.parseToJsonElement(arguments.ifBlank { "{}" }).let {
                (it as? kotlinx.serialization.json.JsonObject) ?: kotlinx.serialization.json.buildJsonObject {}
            }
        } catch (_: Exception) {
            emptyMap<String, kotlinx.serialization.json.JsonElement>()
        }
        val reason = (args["reason"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: "AI 邀请通话"

        return try {
            val answered = gate.request(reason)
            if (answered) {
                buildJsonObject {
                    put("status", "answered")
                    put("message", "The user answered the call. The voice conversation is now running; reply briefly, your text will be spoken aloud.")
                }.toString()
            } else {
                buildJsonObject {
                    put("status", "declined")
                    put("message", "The user declined the call. Continue in text instead; do not immediately retry the call.")
                }.toString()
            }
        } catch (e: Exception) {
            err("request_failed", e.message)
        }
    }

    private fun err(code: String, message: String?) = buildJsonObject {
        put("status", "error")
        put("error", code)
        if (!message.isNullOrBlank()) put("message", message)
    }.toString()
}
