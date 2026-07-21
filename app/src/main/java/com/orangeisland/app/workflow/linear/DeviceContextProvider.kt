package com.orangeisland.app.workflow.linear

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager

/**
 * Builds a [DeviceContext] snapshot by reading the live device state. Called by the linear engine
 * right before condition evaluation. Every read is best-effort — a missing permission or a service
 * that isn't enabled surfaces as a null field, and [ConditionEvaluator] fails those conditions open
 * (returns true) rather than blocking the workflow.
 *
 * The foreground-app field is populated by [foregroundProvider], injected so this class doesn't
 * hard-depend on a specific accessibility-service hook (the hook is wired in stage F4 when the
 * automation service gains a foreground dispatcher). Until then the default provider returns null,
 * so foreground-app conditions fail open.
 *
 * Independent implementation.
 */
class DeviceContextProvider(
    private val context: Context,
    private val foregroundProvider: () -> String? = { null },
    private val lastChatMsProvider: () -> Long? = { null }
) {

    /** Capture the device state at this instant. */
    fun snapshot(): DeviceContext {
        val now = System.currentTimeMillis()
        val battery = batteryStatus()
        val wifi = currentWifiSsid()
        val screenOn = isScreenOn()
        val (lat, lng) = lastKnownLocation()
        return DeviceContext(
            nowMs = now,
            batteryLevel = battery?.first,
            isCharging = battery?.second ?: false,
            wifiSsid = wifi,
            foregroundPackage = foregroundProvider(),
            screenOn = screenOn,
            latitude = lat,
            longitude = lng,
            lastChatMs = lastChatMsProvider()
        )
    }

    /** (level 0..100, isCharging) via the sticky BATTERY_CHANGED broadcast; null if unavailable. */
    private fun batteryStatus(): Pair<Int, Boolean>? = try {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) null
        else (level * 100 / scale) to (status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL)
    } catch (_: Exception) { null }

    /** Current WiFi SSID (quotes stripped), or null if WiFi is off / permission missing. */
    private fun currentWifiSsid(): String? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val info = wm.connectionInfo ?: return null
        val ssid = info.ssid ?: return null
        // Android wraps the SSID in double quotes ("<ssid>"); strip them. "<unknown ssid>" means
        // no connection or permission missing — treat as null so conditions fail open.
        val stripped = ssid.removePrefix("\"").removeSuffix("\"")
        if (stripped.isBlank() || ssid == "<unknown ssid>") null else stripped
    } catch (_: Exception) { null }

    private fun isScreenOn(): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.isInteractive
    } catch (_: Exception) { true }

    /** Last known fix from the passive providers; (lat, lng) or (null, null). Avoids a fresh fix
     *  (which would block / require location permission checks) — conditions only need a rough
     *  sense of "is the user near X", and the trigger layer owns geofence accuracy. */
    private fun lastKnownLocation(): Pair<Double?, Double?> = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return null to null
        val provider = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null to null
        val loc = lm.getLastKnownLocation(provider) ?: return null to null
        loc.latitude to loc.longitude
    } catch (_: Exception) { null to null }
}
