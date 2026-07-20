package com.orangeisland.app.tool

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.resume

/**
 * Toast tool provider — lets the AI show a native Android Toast message on the device.
 *
 * Uses the standard [android.widget.Toast] API (no special permission required). Toasts are
 * a lightweight, non-intrusive way to give the user quick feedback ("Done", "Saved 3 items",
 * "Brightness set to 80%"). The tool description steers the model toward sparing use.
 *
 * Thread safety: tool execution runs on a background coroutine; Toast.makeText must be called
 * on the main thread, so we hop over via [withContext]([Dispatchers.Main]) and return the real
 * outcome (not a fire-and-forget lie).
 */
class ToastToolProvider(private val app: Application) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.toastEnabled) return emptyList()

        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "show_toast",
                description = "Show a short native Android Toast message at the bottom of the " +
                    "screen. Use SPARINGLY for brief, non-blocking feedback to the user " +
                    "(e.g. 'Done', 'Saved', 'Brightness 80%'). Do NOT use for conversational " +
                    "replies — those go in your normal text response. Do NOT call repeatedly.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "text" to ToolProperty("string",
                            "The message to display. Keep it short (recommended under 40 characters)."),
                        "long" to ToolProperty("boolean",
                            "Optional. true = show ~3.5s (long), false/omitted = show ~2s (short).")
                    ),
                    required = listOf("text")
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (name != "show_toast") return unknownTool(name)
        return try {
            val args = json.parseToJsonElement(arguments).jsonObject
            val text = args["text"]?.toString()?.trim('"')
                ?: return error("missing_argument", "text is required")
            if (text.isBlank()) return error("empty_text", "text must not be blank")
            if (text.length > 200) return error("too_long", "Keep Toast text under 200 characters.")

            val longDuration = args["long"]?.toString()?.trim('"')?.toBooleanStrictOrNull() ?: false

            // Hop to the main thread to call Toast.makeText, then resume with the real outcome.
            withContext(Dispatchers.Main) {
                try {
                    val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    Toast.makeText(app, text, duration).show()
                    success(text, longDuration)
                } catch (e: Exception) {
                    error("toast_failed", e.message ?: "Toast failed")
                }
            }
        } catch (e: Exception) {
            error("execution_error", e.message ?: "Unknown error")
        }
    }

    override fun handles(name: String): Boolean = name == "show_toast"

    // ── Helpers ─────────────────────────────────────────────

    private fun success(text: String, long: Boolean): String = buildJsonObject {
        put("success", JsonPrimitive(true))
        put("text", JsonPrimitive(text))
        put("duration", JsonPrimitive(if (long) "long" else "short"))
    }.toString()

    private fun error(type: String, message: String): String = buildJsonObject {
        put("success", JsonPrimitive(false))
        put("error_type", JsonPrimitive(type))
        put("message", JsonPrimitive(message))
    }.toString()

    private fun unknownTool(name: String): String =
        error("unknown_tool", "ToastToolProvider does not handle tool: $name")
}
