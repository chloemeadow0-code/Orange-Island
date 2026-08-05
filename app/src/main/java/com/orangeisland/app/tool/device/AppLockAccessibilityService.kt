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
        // Respond to both window-state changes (an app coming to the foreground) and window-list
        // changes (the foreground app shuffling its own windows / the mask getting covered by a
        // dialog or splash). The window-list case is what lets us re-assert the mask after it has
        // been pushed aside — without it, once the mask is buried a single window-state event is
        // never emitted again (same foreground package), and the lock silently stops working.
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (!cachedEnabled) return

        // Resolve the foreground package. For WINDOW_STATE_CHANGED the event carries it directly;
        // for WINDOWS_CHANGED it usually doesn't, so fall back to the dispatcher's last-known
        // foreground (kept fresh by the automation accessibility service and already noise-filtered).
        val pkg = when {
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                event.packageName?.toString()
            else -> com.orangeisland.app.workflow.trigger.AppForegroundDispatcher.lastKnown
        } ?: return
        if (pkg.isBlank()) return
        // Ignore our own package (MainActivity + AppLockMaskActivity) to prevent feedback loops.
        if (pkg == ownPackage) {
            // When we leave our own package, the mask (if shown) is no longer on top.
            if (maskOnTopFor != null) maskOnTopFor = null
            return
        }

        val entry = cachedEntries[pkg] ?: return
        // Track that the mask is (about to be) covering this app, so subsequent WINDOWS_CHANGED
        // events for the same locked app don't spam-launch it repeatedly.
        if (maskOnTopFor == pkg) return
        maskOnTopFor = pkg
        showMask(entry)
    }

    override fun onInterrupt() { /* no-op */ }

    /** The package the mask is currently believed to be covering, or null when it isn't shown /
     *  has been pushed aside and not yet re-asserted. Used to debounce re-launch. */
    @Volatile private var maskOnTopFor: String? = null

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
        val launched = runCatching { startActivity(intent) }.isSuccess
        // If the launch failed, clear the tracking so the next matching event retries.
        if (!launched) maskOnTopFor = null
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
