package com.orangeisland.app.tool.device

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exposes static device telemetry (battery, storage, memory, build) to the model.
 *
 * The simplest Device Access tool: no permissions required — every source here is a
 * public API or a sticky broadcast that any app can read. The battery sticky broadcast
 * is the one slightly non-obvious path; we read it via [registerReceiver] with a null
 * receiver filter, which returns the last-broadcast BatteryManager extras without
 * actually subscribing (a well-documented pattern).
 */
class DeviceInfoToolProvider(private val app: Application) : ToolProvider {

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.deviceInfoEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "get_device_info",
                description = "Read the device's current hardware and system state: battery " +
                    "level and charging status, available storage and RAM, device model, " +
                    "manufacturer, and Android version. No arguments. Useful when the user " +
                    "asks 'how much battery do I have left', 'is my phone charging', " +
                    "'how much free space', etc.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (name != "get_device_info") return unknownTool(name)
        return readDeviceInfo().toString()
    }

    override fun handles(name: String): Boolean = name == "get_device_info"

    private fun readDeviceInfo(): JsonObject {
        // Battery: read the sticky broadcast returned by registerReceiver (no real subscription).
        val battery = runCatching {
            val filter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            @Suppress("DEPRECATION")
            val intent = app.registerReceiver(null, filter)
            intent
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level != null && scale > 0) (level * 100 / scale) else null
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val memInfo = runCatching {
            val mi = android.app.ActivityManager.MemoryInfo()
            (app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
            mi
        }.getOrNull()

        val storage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            Triple(stat.availableBytes, stat.totalBytes, stat.blockSizeLong)
        }.getOrNull()

        return buildJsonObject {
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("brand", Build.BRAND)
            put("device", Build.DEVICE)
            put("android_version", Build.VERSION.RELEASE ?: "unknown")
            put("sdk_level", Build.VERSION.SDK_INT)
            if (percent != null) put("battery_percent", percent)
            put("battery_charging", charging)
            if (memInfo != null) {
                put("memory_total_mb", memInfo.totalMem / (1024 * 1024))
                put("memory_available_mb", memInfo.availMem / (1024 * 1024))
                put("memory_low", memInfo.lowMemory)
            }
            if (storage != null) {
                put("storage_total_mb", storage.second / (1024 * 1024))
                put("storage_available_mb", storage.first / (1024 * 1024))
            }
        }
    }

    private fun unknownTool(name: String): String =
        buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
}
