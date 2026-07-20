package com.orangeisland.app.tool.device

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.orangeisland.app.data.AppLockEntry
import com.orangeisland.app.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AccessibilityService that powers App Lock: when the foreground window switches to an app
 * listed in the lock entries, launch [AppLockMaskActivity] directly on top of it.
 *
 * Design notes:
 *  - The locked-entries map is cached in memory and updated by a background coroutine. We
 *    never call runBlocking on the accessibility callback thread — that would deadlock with
 *    DataStore and starve the generation pipeline.
 *  - We do NOT call performGlobalAction(HOME) before showing the mask. The mask is a
 *    fullscreen activity that covers the locked app by itself; going HOME first caused the
 *    user to bounce to the launcher / Orange Island instead of seeing the lock screen.
 *  - Events from our own package (including the mask activity) are ignored to prevent
 *    feedback loops.
 *
 * Limitation (by Android design): the user can disable this service in system Settings to
 * bypass the lock. We surface this honestly in the settings UI.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsManager: SettingsManager? = null
    private var collectJob: Job? = null

    @Volatile private var cachedEnabled: Boolean = false
    @Volatile private var cachedEntries: Map<String, AppLockEntry> = emptyMap()

    private val ownPackage: String by lazy { packageName }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val sm = SettingsManager(applicationContext)
        settingsManager = sm
        // Continuously cache the latest lock state so onAccessibilityEvent can read it
        // synchronously without blocking.
        collectJob = scope.launch {
            kotlinx.coroutines.flow.combine(sm.appLockEnabled, sm.appLockEntries) { enabled, entries ->
                enabled to entries
            }.collectLatest { (enabled, entries) ->
                cachedEnabled = enabled
                cachedEntries = entries
            }
        }
    }

    override fun onDestroy() {
        collectJob?.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!cachedEnabled) return

        val pkg = event.packageName?.toString() ?: return
        // Ignore our own package (MainActivity + AppLockMaskActivity) to prevent feedback loops.
        if (pkg == ownPackage || pkg.isBlank()) return

        val entry = cachedEntries[pkg] ?: return
        showMask(entry)
    }

    override fun onInterrupt() { /* no-op */ }

    /** Launch the fullscreen mask activity directly on top of the locked app. */
    private fun showMask(entry: AppLockEntry) {
        val intent = Intent(this, AppLockMaskActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(AppLockMaskActivity.EXTRA_PACKAGE_NAME, entry.packageName)
            putExtra(AppLockMaskActivity.EXTRA_LABEL, entry.label)
            putExtra(AppLockMaskActivity.EXTRA_MESSAGE, entry.message)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        /** True iff this accessibility service is currently enabled by the user. */
        fun isEnabled(context: android.content.Context): Boolean {
            val expectedComponent = android.content.ComponentName(context, AppLockAccessibilityService::class.java)
            val flat = runCatching {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull() ?: return false
            if (flat.isBlank()) return false
            val enabled = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (!enabled) return false
            return flat.split(":").any {
                android.content.ComponentName.unflattenFromString(it) == expectedComponent
            }
        }
    }
}
