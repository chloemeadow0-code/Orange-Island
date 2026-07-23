package com.orangeisland.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Cross-manufacturer helper for directing the user to battery-optimisation and
 * auto-start whitelist settings.
 *
 * Android does not provide a single API that guarantees background survival.
 * The best we can do is:
 * 1. Request the standard REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
 * 2. Open the vendor-specific "auto-start" or "background management" page.
 *
 * Both steps require the user to manually toggle switches; the app can only
 * navigate to the right screen.
 */
object PowerWhitelistHelper {

    enum class AutoStartResult {
        VENDOR_PAGE,
        APP_DETAIL_FALLBACK,
        FAILED
    }

    /** Returns true if the app is already on the system battery-optimisation whitelist. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * Launch the standard system dialog asking the user to ignore battery optimisations
     * for this app. Returns true if the Intent was successfully started.
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            startActivitySafe(context, intent)
        } else {
            false
        }
    }

    /**
     * Try to open the vendor-specific auto-start / background-management settings.
     * Falls back to the generic app-details page if no vendor Intent resolves.
     *
     * @return which type of page was actually opened.
     */
    fun openAutoStartSettings(context: Context): AutoStartResult {
        val vendor = Build.MANUFACTURER.lowercase()
        val candidates = vendorCandidates(vendor)

        for (component in candidates) {
            val intent = Intent().setComponent(component)
            if (intent.resolveActivity(context.packageManager) != null) {
                return if (startActivitySafe(context, intent)) {
                    AutoStartResult.VENDOR_PAGE
                } else {
                    AutoStartResult.FAILED
                }
            }
        }

        // Fallback: generic app-details page so the user can at least look for
        // battery / background options manually.
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return if (startActivitySafe(context, fallback)) {
            AutoStartResult.APP_DETAIL_FALLBACK
        } else {
            AutoStartResult.FAILED
        }
    }

    /** Vendor-specific setting ComponentNames, ordered by preference. */
    private fun vendorCandidates(vendor: String): List<ComponentName> = when (vendor) {
        "xiaomi" -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity")
        )
        "huawei", "honor" -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        )
        "oppo" -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        )
        "vivo" -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        )
        "oneplus" -> listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        )
        "meizu" -> listOf(
            ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
        )
        else -> emptyList()
    }

    /** Start an activity safely, logging failures instead of crashing. */
    private fun startActivitySafe(context: Context, intent: Intent): Boolean {
        return try {
            // Add FLAG_ACTIVITY_NEW_TASK if the context is not an Activity,
            // otherwise the start may throw AndroidRuntimeException.
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            DebugLog.w("PowerWhitelistHelper", "Failed to start activity: ${intent.component}", e)
            false
        }
    }
}
