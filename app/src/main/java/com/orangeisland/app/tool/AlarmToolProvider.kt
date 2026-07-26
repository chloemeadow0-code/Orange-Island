package com.orangeisland.app.tool

import android.app.Application
import android.content.Intent
import android.provider.AlarmClock
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Alarm/timer tool provider — delegates to the system clock app via [AlarmClock] intents.
 * No runtime permission needed (SET_ALARM is a normal, auto-granted permission); the only
 * requirement is a <queries> declaration in the manifest so PackageManager can see the
 * clock app's intent filters on Android 11+ (package visibility).
 *
 * Ported 1:1 from the proven design in sue1231513/orangechat's AlarmTool.kt: two tools,
 * set_alarm and set_timer, both fire-and-forget system Intents with EXTRA_SKIP_UI=true
 * (silent creation, no clock-app UI flash). No query/list/cancel — Android has no public
 * API to read another app's alarm list, so those actions were deliberately dropped there
 * to avoid misleading the model; same call here.
 */
class AlarmToolProvider(private val app: Application) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.alarmEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "set_alarm",
                description = "Set an alarm on the user's device through the system clock app. " +
                    "Creates the alarm silently (no clock-app UI shown). There is no way to list " +
                    "or query existing alarms — Android does not expose that data to other apps.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "hour" to ToolProperty("integer", "Hour in 24-hour format (0-23)."),
                        "minute" to ToolProperty("integer", "Minute (0-59)."),
                        "label" to ToolProperty("string", "A label/name for the alarm (optional).")
                    ),
                    required = listOf("hour", "minute")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "set_timer",
                description = "Set a countdown timer on the user's device through the system " +
                    "clock app. Useful for 'remind me in 10 minutes' or 'set a 5-minute timer'.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "seconds" to ToolProperty("integer",
                            "Timer duration in seconds, e.g. 300 for 5 minutes. Must be positive."),
                        "label" to ToolProperty("string", "A label/name for the timer (optional).")
                    ),
                    required = listOf("seconds")
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        return when (name) {
            "set_alarm" -> setAlarm(arguments)
            "set_timer" -> setTimer(arguments)
            else -> unknownTool(name)
        }
    }

    override fun handles(name: String): Boolean = name in setOf("set_alarm", "set_timer")

    // ── set_alarm ─────────────────────────────────────────────

    private fun setAlarm(arguments: String): String {
        val args = runCatching { json.parseToJsonElement(arguments.ifBlank { "{}" }).jsonObject }
            .getOrElse { return error("bad_arguments", "Could not parse arguments JSON.") }
        val hour = args["hour"]?.toString()?.trim('"')?.toIntOrNull()
            ?: return error("missing_argument", "Missing required parameters: hour and minute")
        val minute = args["minute"]?.toString()?.trim('"')?.toIntOrNull()
            ?: return error("missing_argument", "Missing required parameters: hour and minute")
        val label = args["label"]?.toString()?.trim('"') ?: ""

        if (hour !in 0..23 || minute !in 0..59) {
            return error("invalid_time", "Invalid time: hour must be 0-23, minute must be 0-59")
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activities = app.packageManager.queryIntentActivities(intent, 0)
        if (activities.isNullOrEmpty()) {
            return error("no_clock_app", "No clock app found that supports setting alarms")
        }

        return try {
            app.startActivity(intent)
            val displayHour = String.format("%02d", hour)
            val displayMinute = String.format("%02d", minute)
            buildJsonObject {
                put("success", JsonPrimitive(true))
                put("alarm_time", JsonPrimitive("$displayHour:$displayMinute"))
                put("label", JsonPrimitive(label))
                put("message", JsonPrimitive(
                    "Alarm set for $displayHour:$displayMinute" + if (label.isNotBlank()) " ($label)" else ""
                ))
            }.toString()
        } catch (e: Exception) {
            error("start_activity_failed", e.message ?: "Failed to set alarm")
        }
    }

    // ── set_timer ─────────────────────────────────────────────

    private fun setTimer(arguments: String): String {
        val args = runCatching { json.parseToJsonElement(arguments.ifBlank { "{}" }).jsonObject }
            .getOrElse { return error("bad_arguments", "Could not parse arguments JSON.") }
        val seconds = args["seconds"]?.toString()?.trim('"')?.toIntOrNull()
            ?: return error("missing_argument", "Missing required parameter: seconds")
        val label = args["label"]?.toString()?.trim('"') ?: ""

        if (seconds <= 0) {
            return error("invalid_duration", "Timer duration must be positive (seconds > 0)")
        }

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activities = app.packageManager.queryIntentActivities(intent, 0)
        if (activities.isNullOrEmpty()) {
            return error("no_clock_app", "No clock app found that supports setting timers")
        }

        return try {
            app.startActivity(intent)
            val minutes = seconds / 60
            val remaining = seconds % 60
            val display = when {
                minutes > 0 && remaining > 0 -> "${minutes}m ${remaining}s"
                minutes > 0 -> "${minutes}m"
                else -> "${remaining}s"
            }
            buildJsonObject {
                put("success", JsonPrimitive(true))
                put("timer_seconds", JsonPrimitive(seconds))
                put("timer_display", JsonPrimitive(display))
                put("label", JsonPrimitive(label))
                put("message", JsonPrimitive("Timer set for $display" + if (label.isNotBlank()) " ($label)" else ""))
            }.toString()
        } catch (e: Exception) {
            error("start_activity_failed", e.message ?: "Failed to set timer")
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun error(type: String, message: String): String = buildJsonObject {
        put("success", JsonPrimitive(false))
        put("error_type", JsonPrimitive(type))
        put("message", JsonPrimitive(message))
    }.toString()

    private fun unknownTool(name: String): String = error("unknown_tool", "AlarmToolProvider does not handle tool: $name")
}
