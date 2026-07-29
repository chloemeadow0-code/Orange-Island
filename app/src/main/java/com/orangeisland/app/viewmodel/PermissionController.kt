package com.orangeisland.app.viewmodel

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.orangeisland.app.tool.device.AppLockAccessibilityService
import com.orangeisland.app.tool.device.DeviceNotificationListenerService
import com.orangeisland.app.tool.automation.AutomationAccessibilityService
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized runtime + special-permission state for the Device Access tools.
 *
 * Mirrors the controller pattern used by [ShellConfirmationController]: one class owns the
 * cross-cutting concern (here, "is this tool's system permission granted right now?") so each
 * Device Access tool provider can ask the same way and the settings UI can render the same way.
 *
 * Two kinds of permissions are involved:
 *  - **Runtime permissions** (location, calendar) — granted via the system permission dialog,
 *    requested from the settings page's `rememberLauncherForActivityResult`. [isGranted] reads
 *    them synchronously with `ContextCompat.checkSelfPermission`.
 *  - **Special permissions** (notification listener, usage access) — cannot be shown via the
 *    dialog; the user must toggle them in system Settings. [openSystemSettings] launches the
 *    matching Settings screen for the requested [Tool].
 *
 * The notification-listener state is also surfaced as a [StateFlow] so the settings page can
 * react when the user comes back from the system Settings screen after enabling it.
 */
class PermissionController(private val appContext: Context) {

    /** The Device Access tools that need a system permission. */
    enum class Tool { LOCATION, CALENDAR, NOTIFICATION, USAGE_STATS, ACCESSIBILITY, UI_AUTOMATION, OVERLAY, RECORD_AUDIO }

    /** True iff the tool's required permission(s) are currently granted. Safe to call from any thread. */
    fun isGranted(tool: Tool): Boolean = when (tool) {
        Tool.LOCATION -> hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        Tool.CALENDAR -> hasPermission(Manifest.permission.READ_CALENDAR) &&
            hasPermission(Manifest.permission.WRITE_CALENDAR)
        // Special permissions — see [checkSpecialGranted] for the non-checkSelfPermission path.
        Tool.NOTIFICATION -> notificationListenerEnabled
        Tool.USAGE_STATS -> usageAccessEnabled
        Tool.ACCESSIBILITY -> accessibilityEnabled
        Tool.UI_AUTOMATION -> uiAutomationAccessibilityEnabled
        Tool.OVERLAY -> overlayEnabled
        Tool.RECORD_AUDIO -> hasPermission(Manifest.permission.RECORD_AUDIO)
    }

    /** Launches the system Settings screen the user must visit to grant [tool]'s special permission.
     *  No-op for runtime-permission tools (those are requested in-app via the launcher contract). */
    fun openSystemSettings(tool: Tool) {
        val intent = when (tool) {
            Tool.NOTIFICATION -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Tool.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            // Both accessibility services are enabled from the same Settings screen.
            Tool.ACCESSIBILITY, Tool.UI_AUTOMATION -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            // Overlay (draw over other apps) — needed so workflows can launch another app from the
            // background on Android 10+. Targets this package's toggle directly.
            Tool.OVERLAY -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${appContext.packageName}")
            )
            // Runtime-permission tools shouldn't reach here; the settings page uses the launcher.
            else -> return
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    // Notification-listener state is observable so the settings page can refresh after the
    // user returns from the system Settings screen. The fragment checks the same setting that
    // Android uses to decide whether to bind our [DeviceNotificationListenerService].
    private val _notificationListenerEnabled = MutableStateFlow(notificationListenerEnabled)
    val notificationListenerEnabledFlow: StateFlow<Boolean> = _notificationListenerEnabled.asStateFlow()

    /** Re-query the notification-listener state. Call from the settings page's onResume. */
    fun refreshNotificationListenerState() {
        _notificationListenerEnabled.value = notificationListenerEnabled
    }

    private val notificationListenerComponent: ComponentName by lazy {
        ComponentName(appContext, DeviceNotificationListenerService::class.java)
    }

    // Flattened list of enabled NotificationListenerServices, same format Settings uses.
    private val notificationListenerEnabled: Boolean
        get() = try {
            val flat = Settings.Secure.getString(
                appContext.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            if (flat.isBlank()) false
            else TextUtils.split(flat, ":").any { ComponentName.unflattenFromString(it) == notificationListenerComponent }
        } catch (e: Throwable) {
            DebugLog.w("PermissionController", "notificationListenerEnabled query failed: ${e.javaClass.simpleName}")
            false
        }

    // UsageStatsManager has no checkSelfPermission equivalent; the documented check is
    // "an app with our package name appears in the system's granted usage-access set".
    private val usageAccessEnabled: Boolean
        get() = try {
            @Suppress("DEPRECATION")
            val mode = (appContext.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager)
                .unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    appContext.packageName
                )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Throwable) {
            DebugLog.w("PermissionController", "usageAccessEnabled query failed: ${e.javaClass.simpleName}")
            false
        }

    // Accessibility state — mirrors how the system tracks enabled services. Two services share
    // this lookup mechanism: the App Lock interceptor and the UI Automation driver.
    private val accessibilityEnabled: Boolean
        get() = AppLockAccessibilityService.isEnabled(appContext)

    private val _accessibilityEnabled = MutableStateFlow(accessibilityEnabled)
    val accessibilityEnabledFlow: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    /** Re-query accessibility state. Call from the settings page's onResume. */
    fun refreshAccessibilityState() {
        _accessibilityEnabled.value = accessibilityEnabled
        _uiAutomationAccessibilityEnabled.value = uiAutomationAccessibilityEnabled
    }

    /** Re-query overlay permission state. Call from the settings page's onResume. */
    fun refreshOverlayState() {
        _overlayEnabled.value = overlayEnabled
    }

    // UI Automation accessibility state — a separate service the user must enable independently.
    private val uiAutomationAccessibilityEnabled: Boolean
        get() = AutomationAccessibilityService.isEnabled(appContext)

    private val _uiAutomationAccessibilityEnabled = MutableStateFlow(uiAutomationAccessibilityEnabled)
    val uiAutomationAccessibilityEnabledFlow: StateFlow<Boolean> =
        _uiAutomationAccessibilityEnabled.asStateFlow()

    // Overlay (SYSTEM_ALERT_WINDOW) — special permission the user toggles in system Settings. On
    // Android 10+ a backgrounded process cannot startActivity() to open another app unless it holds
    // this, so workflow-triggered open_app/open_url need it to actually switch the screen.
    private val overlayEnabled: Boolean
        get() = Settings.canDrawOverlays(appContext)

    private val _overlayEnabled = MutableStateFlow(overlayEnabled)
    val overlayEnabledFlow: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
}
