package com.orangeisland.app.tool.automation

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Automation tool provider — lets the AI drive the device UI the way a human finger would:
 * tap, swipe, scroll, fire system-wide actions (back/home/recents/notifications/...), and
 * read back the on-screen window tree to find what it just acted on.
 *
 * All execution goes through [AutomationBridge], which holds a reference to the live
 * [AutomationAccessibilityService]. If the user hasn't enabled the service, every tool returns
 * a structured "service_unavailable" envelope whose guidance string tells the model to ask the
 * user to open Settings → Accessibility.
 *
 * Why one provider for six tools (instead of six providers): the tools share the same
 * capability gate ([GenerationContext.uiAutomationEnabled]) and the same permission check
 * (the accessibility service being live). Splitting them would duplicate the gate N times.
 *
 * Safety posture: this is the most powerful tool surface in the app — it can touch any pixel
 * on the screen. There is no per-action confirmation dialog here (the user opted in by enabling
 * accessibility), but the description strings steer the model toward honest, observable actions
 * and away from chaining blind actions it can't see the result of.
 */
class AutomationToolProvider(
    /** All configured LLM providers; used by [ScreenReader] to call the vision model.
     *  Empty map disables ui_read_screen (other automation tools still work). */
    private val providers: Map<String, LlmProvider> = emptyMap()
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val screenReader: ScreenReader? = if (providers.isNotEmpty()) ScreenReader(providers) else null

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.uiAutomationEnabled) return emptyList()
        // ui_read_screen needs a configured vision model; gate it so the model doesn't try to
        // call a tool that can only fail. Other tools are always available when automation is on.
        val readScreenEnabled = screenReader != null &&
            ctx.transcriptionProviderName.isNotBlank() &&
            ctx.transcriptionModelId.isNotBlank()
        val core = listOf(
            ToolDefinition(function = ToolFunction(
                name = "ui_tap",
                description = "Tap an absolute screen coordinate (in pixels). Use ui_inspect " +
                    "first to find the coordinates of the element you want to tap. Returns " +
                    "{success: true} or an error envelope describing what went wrong.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "x" to ToolProperty("number", "Absolute horizontal pixel coordinate."),
                        "y" to ToolProperty("number", "Absolute vertical pixel coordinate."),
                        "duration_ms" to ToolProperty("integer",
                            "Optional hold duration in ms (default 50, range 1–5000). Use 600+ for a long-press.")
                    ),
                    required = listOf("x", "y")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "ui_swipe",
                description = "Swipe a straight line between two absolute screen coordinates. " +
                    "Use ui_inspect first to find the start/end points. Returns success/error JSON.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "start_x" to ToolProperty("number", "Start horizontal pixel."),
                        "start_y" to ToolProperty("number", "Start vertical pixel."),
                        "end_x" to ToolProperty("number", "End horizontal pixel."),
                        "end_y" to ToolProperty("number", "End vertical pixel."),
                        "duration_ms" to ToolProperty("integer",
                            "Optional swipe duration in ms (default 300, range 50–5000). Faster = flick, slower = drag.")
                    ),
                    required = listOf("start_x", "start_y", "end_x", "end_y")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "ui_scroll",
                description = "Scroll the active screen in a direction (up/down/left/right). " +
                    "Prefers a real scrollable container; falls back to a swipe gesture if none is found. " +
                    "Optional x/y anchor targets a specific scrollable area on screen.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "direction" to ToolProperty("string", "One of: up, down, left, right."),
                        "x" to ToolProperty("number", "Optional anchor pixel to pick a scrollable container at."),
                        "y" to ToolProperty("number", "Optional anchor pixel to pick a scrollable container at.")
                    ),
                    required = listOf("direction")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "ui_global_action",
                description = "Perform a system-level action via the accessibility layer. " +
                    "Returns {success: true} or an error. Available actions: back, home, recents, " +
                    "notifications, quick_settings, lock_screen, power_dialog, split_screen, take_screenshot. " +
                    "Some actions are OS-version gated (e.g. take_screenshot requires Android 11+).",
                parameters = ToolParameters(
                    properties = mapOf(
                        "action" to ToolProperty("string", "One of: back, home, recents, notifications, " +
                            "quick_settings, lock_screen, power_dialog, split_screen, take_screenshot.")
                    ),
                    required = listOf("action")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "ui_inspect",
                description = "Read the current window tree and return a flat list of on-screen " +
                    "elements (text, content description, view id, bounds, clickable, scrollable). " +
                    "Use this BEFORE tapping/swiping to find coordinates, and AFTER an action to " +
                    "verify it had the intended effect. Output is capped at the requested max_nodes " +
                    "(default 60).",
                parameters = ToolParameters(
                    properties = mapOf(
                        "max_nodes" to ToolProperty("integer",
                            "Maximum number of nodes to return (default 60, hard cap 200).")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "ui_set_text",
                description = "Type text into an editable field, identified by a selector. " +
                    "Tries ACTION_SET_TEXT first (works on most native EditTexts, supports any " +
                    "Unicode without invoking the IME); falls back to clipboard paste for fields " +
                    "that ignore SET_TEXT (WebView, some Compose fields). The user's clipboard is " +
                    "restored after paste. Use ui_inspect first to find the right selector value. " +
                    "Does NOT work on terminals/canvas-drawn UI that don't expose editable nodes.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "by" to ToolProperty("string",
                            "How to locate the field: 'text' (exact text on the node), " +
                                "'content_description' (exact desc on the node), 'view_id' (Android " +
                                "resource id like 'com.tencent.mm:id/search_box'), or 'coords' " +
                                "(value is 'x,y' screen pixels; matches the node at that point)."),
                        "value" to ToolProperty("string",
                            "The selector value (e.g. '搜索' for by=text, or '540,360' for by=coords)."),
                        "text" to ToolProperty("string", "The text to write into the field."),
                        "nth" to ToolProperty("integer",
                            "Which match to use when several nodes match (0-indexed, default 0). " +
                                "If multiple match, ui_inspect can tell you the order.")
                    ),
                    required = listOf("by", "value", "text")
                )
            ))
        )
        val readScreen = if (readScreenEnabled) listOf(ToolDefinition(function = ToolFunction(
            name = "ui_read_screen",
            description = "Take a screenshot of the current screen and have a vision model " +
                "describe it back to you (all visible text, UI layout, icons, images). This " +
                "is your EYES on the device — call it before/after acting to understand the " +
                "screen state, especially in apps that don't expose a useful accessibility " +
                "tree (games, canvas-drawn UI, image-heavy screens). Reuses the user's " +
                "configured image-transcription model, so it requires the user to have set up " +
                "image transcription in Settings. Slower than ui_inspect (a vision round-trip).",
            parameters = ToolParameters(
                properties = mapOf(
                    "focus" to ToolProperty("string",
                        "Optional guidance for what to look at, e.g. 'the search bar at the " +
                            "top', 'error messages', 'the selected item'. If omitted, the " +
                            "model describes the whole screen.")
                ),
                required = emptyList()
            )
        ))) else emptyList()
        return core + readScreen
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (!ctx.uiAutomationEnabled) return error("disabled", "UI automation is disabled in settings.")
        return try {
            val args = json.parseToJsonElement(arguments.ifBlank { "{}" }).jsonObject
            when (name) {
                "ui_tap" -> doTap(args)
                "ui_swipe" -> doSwipe(args)
                "ui_scroll" -> doScroll(args)
                "ui_global_action" -> doGlobal(args)
                "ui_inspect" -> doInspect(args)
                "ui_set_text" -> doSetText(args)
                "ui_read_screen" -> doReadScreen(ctx, args)
                else -> unknownTool(name)
            }
        } catch (e: Exception) {
            error("execution_error", e.message ?: "Unknown error")
        }
    }

    override fun handles(name: String): Boolean =
        name in setOf("ui_tap", "ui_swipe", "ui_scroll", "ui_global_action", "ui_inspect", "ui_set_text", "ui_read_screen")

    // ── Implementations ───────────────────────────────────────

    private suspend fun doTap(args: JsonObject): String {
        val x = args.num("x") ?: return error("missing_argument", "x is required")
        val y = args.num("y") ?: return error("missing_argument", "y is required")
        if (x < 0 || y < 0) return error("invalid_argument", "x and y must be >= 0")
        val duration = args.longClamped("duration_ms", 1L, 5000L) ?: 50L
        return AutomationBridge.tap(x, y, duration).toJson()
    }

    private suspend fun doSwipe(args: JsonObject): String {
        val sx = args.num("start_x") ?: return error("missing_argument", "start_x is required")
        val sy = args.num("start_y") ?: return error("missing_argument", "start_y is required")
        val ex = args.num("end_x") ?: return error("missing_argument", "end_x is required")
        val ey = args.num("end_y") ?: return error("missing_argument", "end_y is required")
        if (sx < 0 || sy < 0 || ex < 0 || ey < 0) return error("invalid_argument", "coordinates must be >= 0")
        val duration = args.longClamped("duration_ms", 50L, 5000L) ?: 300L
        return AutomationBridge.swipe(sx, sy, ex, ey, duration).toJson()
    }

    private suspend fun doScroll(args: JsonObject): String {
        val dirKey = args.str("direction") ?: return error("missing_argument", "direction is required")
        val direction = ScrollDirection.entries.firstOrNull { it.key == dirKey }
            ?: return error("invalid_argument", "direction must be one of up/down/left/right")
        val ax = args.num("x")
        val ay = args.num("y")
        // Anchor coordinates only make sense together.
        val anchorX = if (ax != null && ay != null) ax else null
        val anchorY = if (ax != null && ay != null) ay else null
        return AutomationBridge.scroll(direction, anchorX, anchorY).toJson()
    }

    private fun doGlobal(args: JsonObject): String {
        val action = args.str("action") ?: return error("missing_argument", "action is required")
        return AutomationBridge.globalAction(action).toJson()
    }

    private fun doInspect(args: JsonObject): String {
        val maxNodes = args.longClamped("max_nodes", 1L, 200L)?.toInt() ?: 60
        return AutomationBridge.snapshotWindowTree(maxNodes).toJson()
    }

    private fun doSetText(args: JsonObject): String {
        val byKey = args.str("by") ?: return error("missing_argument", "by is required")
        val selector = TextSelector.entries.firstOrNull { it.key == byKey }
            ?: return error("invalid_argument", "by must be one of: text, content_description, view_id, coords")
        val value = args.str("value") ?: return error("missing_argument", "value is required")
        val text = args.str("text") ?: return error("missing_argument", "text is required")
        val nth = args.longClamped("nth", 0L, 1000L)?.toInt() ?: 0
        return AutomationBridge.setText(selector, value, nth).toJson()
    }

    private suspend fun doReadScreen(ctx: GenerationContext, args: JsonObject): String {
        val reader = screenReader
            ?: return error("not_configured", "No LLM providers available for vision call.")
        // The tool only shows up in definitions when uiAutomationEnabled, but the vision model
        // itself comes from the transcription settings — verify both before doing the capture.
        if (ctx.transcriptionProviderName.isBlank() || ctx.transcriptionModelId.isBlank()) {
            return error("not_configured",
                "Image transcription is off or no vision model is configured. Ask the user to " +
                    "open Settings → Transcription and set a vision-capable model.")
        }
        val focusHint = args.str("focus")
        return when (val outcome = reader.readScreen(ctx, focusHint)) {
            is ScreenReader.Outcome.Description -> buildJsonObject {
                put("success", JsonPrimitive(true))
                put("model", JsonPrimitive(outcome.model))
                put("description", JsonPrimitive(outcome.text))
            }.toString()
            is ScreenReader.Outcome.NotConfigured -> error("not_configured", outcome.message)
            is ScreenReader.Outcome.CaptureFailed -> error("capture_failed", outcome.reason)
            is ScreenReader.Outcome.VisionError -> error("vision_error", outcome.message)
        }
    }

    // ── Result → JSON string ──────────────────────────────────

    private fun AutomationResult.toJson(): String = when (this) {
        is AutomationResult.Success -> {
            // Flatten the typed details map into the result JSON. The map values are primitives
            // (Number/Boolean/String) coming from the bridge; we coerce each to its JSON form.
            val extra = details.mapNotNull { (k, v) ->
                val prim = when (v) {
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    is String -> JsonPrimitive(v)
                    else -> null
                } ?: return@mapNotNull null
                k to prim
            }.toMap()
            buildJsonObject {
                put("success", JsonPrimitive(true))
                put("kind", JsonPrimitive(kind))
                extra.forEach { (k, p) -> put(k, p) }
            }.toString()
        }

        is AutomationResult.Failure -> buildJsonObject {
            put("success", JsonPrimitive(false))
            put("reason", JsonPrimitive(reason))
        }.toString()

        is AutomationResult.ServiceUnavailable -> buildJsonObject {
            put("success", JsonPrimitive(false))
            put("error_type", JsonPrimitive("service_unavailable"))
            put("guidance", JsonPrimitive(guidance))
        }.toString()

        is AutomationResult.Snapshot -> buildJsonObject {
            put("success", JsonPrimitive(true))
            put("count", JsonPrimitive(nodes.size))
            put("nodes", JsonArray(nodes.map { it.toJsonObject() }))
        }.toString()
    }

    private fun NodeSnapshot.toJsonObject(): JsonObject = buildJsonObject {
        cls?.let { put("class", JsonPrimitive(it)) }
        text?.let { put("text", JsonPrimitive(it)) }
        desc?.let { put("content_description", JsonPrimitive(it)) }
        viewId?.let { put("view_id", JsonPrimitive(it)) }
        boundsInScreen?.let { put("bounds", JsonPrimitive(it.toShortString())) }
        put("clickable", JsonPrimitive(clickable))
        put("scrollable", JsonPrimitive(scrollable))
        put("enabled", JsonPrimitive(enabled))
        put("child_count", JsonPrimitive(childCount))
    }

    // ── Argument helpers ──────────────────────────────────────

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

    private fun JsonObject.num(key: String): Float? =
        (this[key] as? JsonPrimitive)?.content?.toFloatOrNull()?.takeIf { it.isFinite() }

    private fun JsonObject.longClamped(key: String, min: Long, max: Long): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()?.coerceIn(min, max)

    private fun error(type: String, message: String): String = buildJsonObject {
        put("success", JsonPrimitive(false))
        put("error_type", JsonPrimitive(type))
        put("message", JsonPrimitive(message))
    }.toString()

    private fun unknownTool(name: String): String =
        error("unknown_tool", "AutomationToolProvider does not handle tool: $name")
}
