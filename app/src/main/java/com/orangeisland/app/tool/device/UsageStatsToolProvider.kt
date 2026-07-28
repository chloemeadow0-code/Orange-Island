package com.orangeisland.app.tool.device

import android.app.Application
import android.app.usage.UsageStatsManager
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.tool.SensitiveToolApprovalGate
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.util.NoisePackageFilter
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.PermissionController
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen-usage tool backed by [UsageStatsManager]. Reports per-app foreground time and
 * last-opened timestamp over a time range.
 *
 * One tool:
 *  - [get_app_usage] — aggregated foreground time per app. Default range is 'today'
 *    (since midnight, device timezone); 'week' = last 7 days. Returns the top apps by
 *    total usage, with app label, package, minutes-used, and last-opened time.
 *
 * Permission: PACKAGE_USAGE_STATS is a special permission granted from
 * Settings → Usage access (cannot be requested via the runtime dialog). The settings
 * page already surfaces an 'Open system settings' affordance via PermissionController.
 */
class UsageStatsToolProvider(
    private val app: Application,
    private val permissionController: PermissionController,
    private val approvalGate: SensitiveToolApprovalGate? = null,
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.usageStatsEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "get_app_usage",
                description = "Get per-app screen usage time for a time range. Returns the apps " +
                    "the user has spent the most time in, with total foreground minutes and " +
                    "last-opened time. Use when the user asks 'how much time have I spent on " +
                    "my phone', '我今天用了多久微信', 'screen time', etc.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "range" to ToolProperty("string", "Time range: 'today' (since midnight, default) or 'week' (last 7 days)."),
                        "limit" to ToolProperty("integer", "Max apps to return (default 10, max 30).")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "get_foreground_app",
                description = "Get the app the user is currently using (the app in the foreground " +
                    "right now). Returns its label, package name, and how long it has been in the " +
                    "foreground. Use when the workflow or user needs to know 'what app is open right " +
                    "now', '用户现在在用什么 app', '当前前台应用', e.g. to gate an action on the active app.",
                parameters = ToolParameters(
                    properties = emptyMap(),
                    required = emptyList()
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (name != "get_app_usage" && name != "get_foreground_app") return unknownTool(name)
        if (!permissionController.isGranted(PermissionController.Tool.USAGE_STATS)) {
            return error("permission_denied",
                "Usage access not granted. Ask the user to enable Screen Usage in Settings → Device Access (it opens the system Usage access screen).")
        }
        // The approval gate suspends until the UI resolves it. In a workflow (especially a background
        // Worker) there is no UI observer to resolve the request, so a bare await would hang until
        // the node's hard timeout. Bound it: if nobody approves within a few seconds, treat it as
        // denied and let the run move on instead of wedging.
        val approved = approvalGate?.let { gate ->
            kotlinx.coroutines.withTimeoutOrNull(APPROVAL_TIMEOUT_MS) {
                gate.approval.invoke(name, "读取应用使用情况")
            } ?: false
        } ?: true
        if (!approved) {
            return error("approval_denied", "用户拒绝了应用使用情况请求(或无 UI 在工作流执行时审批)。")
        }
        if (name == "get_foreground_app") return foregroundApp()
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val range = (parsed["range"] as? JsonPrimitive)?.content?.lowercase()?.takeIf { it.isNotBlank() } ?: "today"
        val limit = ((parsed["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 10).coerceIn(1, 30)

        val now = System.currentTimeMillis()
        val start = when (range) {
            "week" -> now - 7L * 24 * 60 * 60 * 1000
            else -> midnightToday()
        }
        if (start >= now) return error("bad_range", "Computed start time is not before now.")

        val usm = app.getSystemService(UsageStatsManager::class.java)
            ?: return error("no_service", "UsageStatsManager unavailable on this device.")

        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, now) ?: emptyList()
        } catch (e: Exception) {
            DebugLog.e("UsageStatsTool", "queryUsageStats failed", e)
            return error("query_failed", "Usage stats query failed: ${e.message}")
        }
        if (stats.isEmpty()) {
            // queryUsageStats returns an empty list both when there's genuinely no data and when
            // usage access isn't actually granted despite our AppOps check passing — surface it
            // as 'no data' and let the model decide what to say.
            return buildJsonObject {
                put("range", range); put("apps", buildJsonArray {}); put("count", 0)
            }.toString()
        }

        // Aggregate by package (INTERVAL_BEST may return multiple intervals per app).
        val pm = app.packageManager
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val agg = stats.asSequence()
            .filter { it.packageName != null }
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                val totalMs = list.sumOf { it.totalTimeInForeground }
                val lastUsed = list.maxOf { it.lastTimeUsed }
                Triple(pkg, totalMs, lastUsed)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (pkg, totalMs, lastUsed) ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                buildJsonObject {
                    put("app", label)
                    put("package", pkg)
                    put("foreground_minutes", totalMs / (60 * 1000))
                    put("last_used", iso.format(Date(lastUsed)))
                }
            }
            .toList()
        return buildJsonObject {
            put("range", range)
            put("apps", buildJsonArray { agg.forEach { add(it) } })
            put("count", agg.size)
        }.toString()
    }

    override fun handles(name: String): Boolean = name == "get_app_usage" || name == "get_foreground_app"

    /**
     * Returns the app currently in the foreground. Walks the last [FOREGROUND_LOOKBACK_MS] of usage
     * events and takes the most recent MOVE_TO_FOREGROUND, then reports how long it has been
     * foregrounded. Returns an error JSON if no foreground app can be determined (no permission in
     * practice, no recent events, or the screen is off).
     */
    private fun foregroundApp(): String {
        val usm = app.getSystemService(UsageStatsManager::class.java)
            ?: return error("no_service", "UsageStatsManager unavailable on this device.")
        val now = System.currentTimeMillis()
        val events = try {
            usm.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
        } catch (e: Exception) {
            DebugLog.e("UsageStatsTool", "queryEvents failed", e)
            return error("query_failed", "Foreground query failed: ${e.message}")
        } ?: return error("no_data", "No usage events in the lookback window.")
        val event = android.app.usage.UsageEvents.Event()
        var pkg: String? = null
        var sinceMs = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val candidate = event.packageName
                // The most recent MOVE_TO_FOREGROUND is frequently the IME (typing) or SystemUI
                // (notification shade). Skip those and keep walking back to the last real app.
                if (candidate != null && NoisePackageFilter.isNoise(app, candidate)) continue
                pkg = candidate
                sinceMs = event.timeStamp
            }
        }
        if (pkg == null) {
            return buildJsonObject {
                put("foreground_app", JsonPrimitive(""))
                put("package", JsonPrimitive(""))
                put("foreground_seconds", 0)
                put("note", "No app is currently in the foreground (screen off or no recent activity).")
            }.toString()
        }
        val pm = app.packageManager
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return buildJsonObject {
            put("foreground_app", label)
            put("package", pkg)
            put("foreground_seconds", if (sinceMs > 0) (now - sinceMs) / 1000 else 0)
            put("since", if (sinceMs > 0) iso.format(Date(sinceMs)) else "")
        }.toString()
    }

    private fun midnightToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun error(type: String, message: String): String =
        buildJsonObject { put("error", type); put("message", message) }.toString()

    private fun unknownTool(name: String): String =
        buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()

    companion object {
        /** Lookback window for [foregroundApp]: 60 s is short enough that the result reflects what
         *  the user is doing right now, while tolerating a brief pause between a query and the last
         *  app switch. */
        private const val FOREGROUND_LOOKBACK_MS = 60_000L

        /** Max wait for the sensitive-tool approval dialog. In a chat turn the UI resolves this in a
         *  second or two; in a workflow (esp. a background Worker) there is no observer, so without a
         *  bound the gate would suspend until the node's hard timeout. 8 s is generous for a human
         *  tap yet short enough that a headless workflow fails fast instead of hanging. */
        private const val APPROVAL_TIMEOUT_MS = 8_000L
    }
}
