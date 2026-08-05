package com.orangeisland.app.tool.device

import android.app.Application
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.ProviderConfig
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.tool.CameraToolGate
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exposes the `take_photo` tool so the AI can autonomously capture a photo using the
 * device camera. The tool suspends until the UI card auto-captures the photo.
 *
 * A successful capture returns a JSON result with the saved path. If an image-transcription
 * (vision) model is configured, the provider also returns a textual description so the
 * model can understand the photo without receiving a raw image inline.
 */
class CameraToolProvider(
    private val app: Application,
    private val gate: CameraToolGate?,
    private val llmProviders: Map<String, LlmProvider>
) : ToolProvider {

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.cameraToolEnabled || gate == null) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "take_photo",
                description = "Capture a photo using the device camera. Use this when the user " +
                    "asks you to see something visually, when you need to observe the current " +
                    "environment, or when a visual check would help answer the question. " +
                    "The photo will be captured automatically and a description returned to you.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "facing" to com.orangeisland.app.api.ToolProperty(
                            "string",
                            "Which camera to use: 'back' (rear camera, default, good for " +
                                "photographing the environment/objects) or 'front' (selfie " +
                                "camera, good for photographing the user)."
                        )
                    ),
                    required = emptyList()
                )
            ))
        )
    }

    override fun handles(name: String): Boolean = name == "take_photo"

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        DebugLog.d("CamTool", "execute START name=$name args=$arguments")
        if (gate == null) {
            DebugLog.e("CamTool", "gate is null")
            return err("gate_unavailable", "Camera gate is not installed.")
        }

        val facing = parseFacing(arguments)
        DebugLog.d("CamTool", "requesting camera facing=$facing ...")
        val imagePath = gate.request(facing)
        DebugLog.d("CamTool", "gate.request returned imagePath=$imagePath")
        if (imagePath == null) {
            return err("capture_cancelled", "User cancelled or capture failed.")
        }

        // Optional vision transcription so the AI gets a textual description.
        val description = if (ctx.imageTranscriptionEnabled && ctx.transcriptionModelId.isNotBlank()) {
            DebugLog.d("CamTool", "transcribing photo...")
            runCatching {
                transcribePhoto(imagePath, ctx)
            }.getOrNull()?.also {
                DebugLog.d("CamTool", "transcription OK: ${it.take(200)}")
            }
        } else {
            DebugLog.d("CamTool", "transcription disabled (enabled=${ctx.imageTranscriptionEnabled} modelId='${ctx.transcriptionModelId}')")
            null
        }

        val result = buildJsonObject {
            put("success", true)
            put("path", imagePath)
            if (!description.isNullOrBlank()) put("description", description)
        }.toString()
        DebugLog.d("CamTool", "execute DONE returning result len=${result.length}")
        return result
    }

    // ── Internal helpers ─────────────────────────────────────

    private suspend fun transcribePhoto(imagePath: String, ctx: GenerationContext): String? {
        val providerName = ctx.transcriptionProviderName.takeIf { it.isNotBlank() }
            ?: llmProviders.keys.firstOrNull()
            ?: return null
        val provider = llmProviders[providerName] ?: return null

        val config = ProviderConfig(
            apiKey = ctx.transcriptionApiKey,
            modelId = ctx.transcriptionModelId,
            systemPrompt = com.orangeisland.app.data.BuiltInPrompts.IMAGE_TRANSCRIPTION_SYSTEM,
            thinkingEnabled = false,
            baseUrl = ctx.transcriptionBaseUrl
        )
        val promptMessages = listOf(
            ChatMessage(
                text = ctx.imageTranscriptionPrompt,
                images = listOf(imagePath),
                participant = Participant.USER,
                status = MessageStatus.SUCCESS
            )
        )
        val sb = StringBuilder()
        var streamError: String? = null
        provider.generateResponse(promptMessages, config).collect { event ->
            when (event) {
                is StreamEvent.TextChunk -> sb.append(event.text)
                is StreamEvent.Error -> { streamError = event.message }
                else -> {}
            }
        }
        return if (streamError == null) sb.toString().trim() else null
    }

    private fun err(code: String, message: String): String = buildJsonObject {
        put("success", false)
        put("error", code)
        put("message", message)
    }.toString()

    /** Parses the optional `facing` argument ('front' / 'back'); defaults to back. */
    private fun parseFacing(arguments: String): String {
        return runCatching {
            val args = kotlinx.serialization.json.Json.parseToJsonElement(arguments.ifBlank { "{}" })
            val obj = args as? kotlinx.serialization.json.JsonObject ?: return "back"
            val v = obj["facing"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            if (v.equals("front", ignoreCase = true)) "front" else "back"
        }.getOrDefault("back")
    }
}
