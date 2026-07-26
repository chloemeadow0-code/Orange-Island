package com.orangeisland.app.tool.device

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.gadgetbridge.GadgetbridgeReader
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter

/**
 * Health tool provider — reads the same local Gadgetbridge SQLite export that
 * [com.orangeisland.app.ui.health.HealthViewModel] displays on the Health page, so the
 * numbers the AI reports always match what the user sees on screen. Read-only, no network
 * (Supabase sync is a separate, unrelated upload path — this tool never touches it).
 *
 * Gated by [GenerationContext.healthEnabled], a dedicated opt-in separate from whether the
 * Health page itself can read the file, since handing biometric data to an LLM provider is
 * a bigger privacy step than just displaying it locally.
 */
class HealthToolProvider(private val app: Application) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.healthEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "get_health_summary",
                description = "Get today's key health metrics from the user's wearable: steps, " +
                    "calories, current heart rate, latest SpO2 and stress level. Use for 'how am " +
                    "I doing today' / '我今天的健康数据怎么样'.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "get_daily_health_history",
                description = "Get daily health summaries (steps, calories, heart rate, stress, " +
                    "SpO2) for the past N days. Use for trends like 'how were my steps this week'.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "days" to ToolProperty("integer", "Number of past days to include (default 7, max 30).")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "get_sleep_history",
                description = "Get sleep summaries (total/deep/light/REM/awake duration, bed time, " +
                    "wake time) for the past N days. Use for 'how did I sleep' / '我昨晚睡得怎么样'.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "days" to ToolProperty("integer", "Number of past days to include (default 7, max 30).")
                    ),
                    required = emptyList()
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        val gate = checkAvailable(ctx.healthDbPath)
        if (gate != null) return gate
        return withContext(Dispatchers.IO) {
            when (name) {
                "get_health_summary" -> healthSummary(ctx.healthDbPath)
                "get_daily_health_history" -> dailyHistory(arguments, ctx.healthDbPath)
                "get_sleep_history" -> sleepHistory(arguments, ctx.healthDbPath)
                else -> unknownTool(name)
            }
        }
    }

    override fun handles(name: String): Boolean =
        name in setOf("get_health_summary", "get_daily_health_history", "get_sleep_history")

    // ── Internals ─────────────────────────────────────────────

    /** Mirrors [com.orangeisland.app.ui.health.HealthViewModel.hasManageStoragePermission].
     *  Returns an error string if the tool cannot run, null if it's clear to proceed. */
    private fun checkAvailable(customPath: String): String? {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) {
            return error("permission_denied",
                "Storage permission not granted. Ask the user to open Settings → Device Access → " +
                    "查看健康数据 and grant 'All files access'.")
        }
        if (!GadgetbridgeReader.dbFileExists(customPath)) {
            return error("db_not_found",
                "No Gadgetbridge export database found. The user's wearable app (Gadgetbridge) " +
                    "must have auto-export enabled and have synced at least once.")
        }
        return null
    }

    private fun healthSummary(customPath: String): String {
        val latestActivity = GadgetbridgeReader.readLatestActivitySample(customPath)
        val daily7 = GadgetbridgeReader.readDailySummaries(7, customPath)
        val today = daily7.lastOrNull()
        val (spo2, stress) = GadgetbridgeReader.readLatestSpo2AndStress(customPath)
        return buildJsonObject {
            put("date", JsonPrimitive(today?.date?.format(dateFmt) ?: ""))
            put("steps", JsonPrimitive(today?.steps ?: 0))
            put("calories", today?.calories?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
            put("current_heart_rate", latestActivity?.heartRate?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
            put("resting_heart_rate", today?.hrResting?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
            put("latest_spo2", spo2?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
            put("latest_stress", stress?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
            put("note", JsonPrimitive("-1 means no reading available for that metric today."))
        }.toString()
    }

    private fun dailyHistory(arguments: String, customPath: String): String {
        val days = parseDays(arguments)
        val summaries = GadgetbridgeReader.readDailySummaries(days, customPath)
        val arr = buildJsonArray {
            summaries.forEach { s ->
                add(buildJsonObject {
                    put("date", JsonPrimitive(s.date.format(dateFmt)))
                    put("steps", JsonPrimitive(s.steps))
                    put("calories", s.calories?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
                    put("hr_avg", s.hrAvg?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
                    put("hr_resting", s.hrResting?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
                    put("hr_max", s.hrMax?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
                    put("hr_min", s.hrMin?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
                    put("stress_avg", s.stressAvg?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
                    put("spo2_avg", s.spo2Avg?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1))
                })
            }
        }
        return buildJsonObject { put("days", JsonPrimitive(days)); put("summaries", arr) }.toString()
    }

    private fun sleepHistory(arguments: String, customPath: String): String {
        val days = parseDays(arguments)
        val summaries = GadgetbridgeReader.readSleepSummaries(days, customPath)
        val arr = buildJsonArray {
            summaries.filterNot { it.isNap }.forEach { s ->
                add(buildJsonObject {
                    put("sleep_start_ms", JsonPrimitive(s.timestamp))
                    put("wake_up_ms", JsonPrimitive(s.wakeupTime))
                    put("total_minutes", JsonPrimitive(s.totalDuration))
                    put("deep_sleep_minutes", JsonPrimitive(s.deepSleep))
                    put("light_sleep_minutes", JsonPrimitive(s.lightSleep))
                    put("rem_sleep_minutes", JsonPrimitive(s.remSleep))
                    put("awake_minutes", JsonPrimitive(s.awakeDuration))
                })
            }
        }
        return buildJsonObject { put("days", JsonPrimitive(days)); put("sleep_summaries", arr) }.toString()
    }

    private fun parseDays(arguments: String): Int {
        val args = runCatching { json.parseToJsonElement(arguments.ifBlank { "{}" }).jsonObject }.getOrNull()
        return (args?.get("days")?.toString()?.trim('"')?.toIntOrNull() ?: 7).coerceIn(1, 30)
    }

    private fun error(type: String, message: String): String = buildJsonObject {
        put("success", JsonPrimitive(false))
        put("error_type", JsonPrimitive(type))
        put("message", JsonPrimitive(message))
    }.toString()

    private fun unknownTool(name: String): String = error("unknown_tool", "HealthToolProvider does not handle tool: $name")
}
