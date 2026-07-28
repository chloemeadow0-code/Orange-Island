package com.orangeisland.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Decides whether a foreground package is "noise" — i.e. not a real app the user deliberately
 * switched to, but a system surface that only *looks* like a foreground transition.
 *
 * Why this exists: the automation accessibility service forwards every TYPE_WINDOW_STATE_CHANGED
 * event as a foreground transition. That event fires for window changes the user never thinks of
 * as "switching apps":
 *  - The IME popping up / being dismissed while typing (its package becomes the foreground window).
 *  - Pulling down the notification shade / quick settings (SystemUI).
 *  - Landing on the home screen between two real apps (the Launcher).
 *
 * Each of these used to be recorded as a real "foreground app" event and ended up polluting both
 * the `{app_context}` snapshot fed to the model and the AppLaunched / AppClosed workflow triggers.
 *
 * The three categories below are detected lazily and cached, because their package sets change
 * extremely rarely (the user doesn't install a new keyboard or launcher often). Every PackageManager
 * lookup is wrapped in [runCatching]: on a hostile or stripped ROM a query may throw, and we'd
 * rather silently drop that one rule than crash the foreground-tracking path entirely.
 *
 * Note: this filter intentionally does NOT exclude the app's own package. In the usage-summary
 * path the app's own foreground time is genuine signal (the user was chatting), and in the
 * "current foreground" path the caller already knows it's asking from inside the app.
 */
object NoisePackageFilter {

    /** System UI surfaces that report window changes without being apps (notification shade, etc.). */
    private val SYSTEM_UI_PACKAGES = setOf("com.android.systemui")

    @Volatile private var launcherCache: Set<String>? = null
    @Volatile private var imeCache: Set<String>? = null

    /** True iff [pkg] is an IME, SystemUI, or the device's Launcher — i.e. not a real app switch. */
    fun isNoise(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg in SYSTEM_UI_PACKAGES) return true
        if (pkg in launchers(context)) return true
        if (pkg in inputMethods(context)) return true
        return false
    }

    /** The set of packages that register a HOME launcher activity. Empty if the query fails. */
    private fun launchers(context: Context): Set<String> {
        launcherCache?.let { return it }
        return synchronized(this) {
            launcherCache ?: queryLaunchers(context).also { launcherCache = it }
        }
    }

    /** The set of packages that register an input-method service (BIND_INPUT_METHOD). Empty on failure. */
    private fun inputMethods(context: Context): Set<String> {
        imeCache?.let { return it }
        return synchronized(this) {
            imeCache ?: queryInputMethods(context).also { imeCache = it }
        }
    }

    private fun queryLaunchers(context: Context): Set<String> = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun queryInputMethods(context: Context): Set<String> = runCatching {
        // android.view.InputMethod.SERVICE_INTERFACE — the action IME services declare in their
        // manifest to be bound as a keyboard. Spelled out as a literal because the constant lives
        // on the android.view.inputmethod.InputMethod interface, which is less stable to reach
        // across API levels than the well-documented string value itself.
        val ime = Intent("android.view.InputMethod")
        context.packageManager
            .queryIntentServices(ime, PackageManager.MATCH_ALL)
            .map { it.serviceInfo.packageName }
            .toSet()
    }.getOrDefault(emptySet())
}
