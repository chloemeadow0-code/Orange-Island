package com.orangeisland.app.tool.device

import android.app.Application
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exposes the device's current wall-clock time to the model. No permissions required —
 * reads the system clock only. Deliberately minimal: only the time string, no date/weekday/
 * timezone/timestamp (by design — see Settings → Device Access → 获取时间).
 */
class TimeToolProvider(private val app: Application) : ToolProvider {

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.timeToolEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "get_current_time",
                description = "Get the device's current local time (HH:mm:ss, 24-hour). " +
                    "No arguments. Use this when the user asks what time it is right now.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (name != "get_current_time") return unknownTool(name)
        return try {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
            val currentTime = sdf.format(Date())
            buildJsonObject { put("time", currentTime) }.toString()
        } catch (e: Exception) {
            DebugLog.e("TimeToolProvider", "Failed to read current time", e)
            buildJsonObject {
                put("error", "execution_error")
                put("message", e.localizedMessage ?: "Unknown error")
            }.toString()
        }
    }

    override fun handles(name: String): Boolean = name == "get_current_time"

    private fun unknownTool(name: String): String =
        buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
}
