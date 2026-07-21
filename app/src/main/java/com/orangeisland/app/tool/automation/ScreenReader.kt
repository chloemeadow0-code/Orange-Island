package com.orangeisland.app.tool.automation

import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.ProviderConfig
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.data.BuiltInPrompts
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext

/**
 * Orchestrates the "look at the screen" workflow for [AutomationToolProvider.ui_read_screen]:
 * capture a screenshot via the accessibility service, then feed it to the same vision model the
 * user already configured for image transcription, and return the model's textual description
 * back to the calling LLM as the tool result.
 *
 * Why a separate class (and not inline in the tool provider): the provider is supposed to be
 * thin — argument parsing + result formatting. The vision round-trip is a multi-step process
 * (capture → write PNG → call provider → accumulate stream → clean up) that benefits from being
 * isolated, and it depends on [LlmProvider] which the bridge layer never touches. Keeping the
 * accessibility layer (service + bridge) free of any AI-provider dependency also means a future
 * "OCR-only" or "local-vision" variant can swap in here without touching the service.
 *
 * Reuse: we deliberately consume the SAME provider/model/key/prompt that the existing image
 * transcription feature uses (read from [GenerationContext]), so the user configures vision ONCE
 * and it works for both "user attaches a photo" and "AI looks at the screen". No new setting.
 */
internal class ScreenReader(private val providers: Map<String, LlmProvider>) {

    /** Outcome of a single readScreen call — turned into JSON by the tool provider. */
    sealed class Outcome {
        data class Description(val text: String, val model: String) : Outcome()
        data class NotConfigured(val message: String) : Outcome()
        data class CaptureFailed(val reason: String) : Outcome()
        data class VisionError(val message: String) : Outcome()
    }

    /**
     * Capture + describe the current screen. Caller-side contract: this is a network call when
     * the configured vision model is remote, so it may take a few seconds. The returned PNG is
     * deleted regardless of outcome.
     *
     * @param focusHint optional extra guidance appended to the default transcription prompt,
     *  e.g. "focus on the search bar in the top-right". Pass null/blank for a generic read.
     */
    suspend fun readScreen(ctx: GenerationContext, focusHint: String?): Outcome {
        // 1. Resolve the vision provider/model/key from the transcription settings. If the user
        //    hasn't configured image transcription at all, we say so explicitly — the tool can't
        //    invent a vision model out of thin air.
        val providerName = ctx.transcriptionProviderName
        val modelId = ctx.transcriptionModelId
        val apiKey = ctx.transcriptionApiKey
        if (providerName.isBlank() || modelId.isBlank()) {
            return Outcome.NotConfigured(
                "Image transcription is not configured. Ask the user to open Settings → " +
                    "Transcription and pick a vision-capable model (e.g. gemini-2.0-flash, " +
                    "gpt-4o, qwen-vl) before using ui_read_screen."
            )
        }
        val provider = providers[providerName] ?: return Outcome.VisionError(
            "Provider '$providerName' is not available."
        )

        // 2. Capture the screen to a temp PNG.
        val png = AutomationBridge.captureScreenToFile()
            ?: return Outcome.CaptureFailed(
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R)
                    "Screenshot requires Android 11+; this device is too old."
                else "Screenshot failed — the screen may be off, locked, or the OS denied capture."
            )

        try {
            // 3. Build a one-shot vision request and stream the result into a string.
            val prompt = buildPrompt(focusHint)
            val config = ProviderConfig(
                apiKey = apiKey,
                modelId = modelId,
                systemPrompt = BuiltInPrompts.IMAGE_TRANSCRIPTION_SYSTEM,
                thinkingEnabled = false,
                baseUrl = ctx.transcriptionBaseUrl
            )
            val messages = listOf(ChatMessage(
                text = prompt,
                images = listOf(png.absolutePath),
                participant = Participant.USER,
                status = MessageStatus.SUCCESS
            ))
            val sb = StringBuilder()
            var firstError: String? = null
            provider.generateResponse(messages, config).collect { ev ->
                when (ev) {
                    is StreamEvent.TextChunk -> sb.append(ev.text)
                    is StreamEvent.Error -> {
                        if (firstError == null) firstError = ev.message
                    }
                    else -> {}
                }
            }
            firstError?.let { return Outcome.VisionError(it) }
            val text = sb.toString().trim()
            if (text.isBlank()) return Outcome.VisionError("Vision model returned an empty response.")
            return Outcome.Description(text, modelId)
        } catch (t: Throwable) {
            DebugLog.e("ScreenReader", "vision call failed", t)
            return Outcome.VisionError(t.message ?: "Unknown vision error")
        } finally {
            runCatching { png.delete() }
        }
    }

    /**
     * Compose the prompt. Default uses the existing transcription prompt verbatim, which already
     * asks for "all visible text, data, charts, layout, visual elements" — exactly what a UI
     * reader needs. If [focusHint] is provided, we append it as a focus directive.
     */
    private fun buildPrompt(focusHint: String?): String {
        val base = BuiltInPrompts.IMAGE_TRANSCRIPTION_USER
        val hint = focusHint?.trim()?.takeIf { it.isNotBlank() } ?: return base
        return base + "\n\nFocus especially on: $hint"
    }
}
