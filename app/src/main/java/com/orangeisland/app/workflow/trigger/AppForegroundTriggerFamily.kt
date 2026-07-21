package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Trigger family for the three app-foreground triggers, fed by
 * [com.orangeisland.app.tool.automation.AutomationAccessibilityService] through
 * [AppForegroundDispatcher]:
 *
 *  - [LinearTrigger.AppLaunched] — fires on a foreground transition *into* [packageName].
 *  - [LinearTrigger.AppClosed]   — fires on a transition *out of* [packageName].
 *  - [LinearTrigger.AppForegroundDuration] — fires when [packageName] has stayed continuously
 *    in the foreground for ≥ [LinearTrigger.AppForegroundDuration.minutes].
 *
 * Transitions are de-duped by package name (the accessibility service fires
 * TYPE_WINDOW_STATE_CHANGED several times per app switch — dialog popups, menu opens — but the
 * foreground package only changes on an actual transition). The duration variant schedules a
 * delayed coroutine per matching workflow when the app enters the foreground; if the user
 * switches away before the delay elapses the coroutine is cancelled and the workflow does not
 * fire, so it triggers on *continuous* usage rather than a brief visit.
 *
 * Independent implementation.
 */
class AppForegroundTriggerFamily(
    private val scope: CoroutineScope
) : TriggerFamily {

    override val name: String = "app_foreground"

    @Volatile private var matching: List<LinearWorkflow> = emptyList()
    @Volatile private var fireCallback: TriggerFireCallback? = null
    @Volatile private var lastForegroundPackage: String? = null
    private val listenerRemover = java.util.concurrent.atomic.AtomicReference<Runnable?>(null)

    /** Active duration-timer jobs keyed by workflow id, cancelled on app switch-out. */
    private val durationJobs = mutableMapOf<String, Job>()
    private val durationJobsMutex = Mutex()

    override fun handles(trigger: LinearTrigger): Boolean =
        trigger is LinearTrigger.AppLaunched ||
            trigger is LinearTrigger.AppClosed ||
            trigger is LinearTrigger.AppForegroundDuration

    override suspend fun sync(matching: List<LinearWorkflow>, callback: TriggerFireCallback) {
        this.matching = matching
        this.fireCallback = callback
        // Subscribe to the dispatcher the first time we have matching workflows; unsubscribe when
        // the matching set empties so the accessibility service's hot path can no-op.
        if (matching.isNotEmpty() && listenerRemover.get() == null) {
            val remover = AppForegroundDispatcher.addListener(::onForegroundChange)
            listenerRemover.set(remover)
        } else if (matching.isEmpty()) {
            listenerRemover.getAndSet(null)?.run()
        }
        // Cancel any duration timers for workflows that no longer exist / are disabled.
        val liveIds = matching.map { it.id }.toSet()
        durationJobsMutex.withLock {
            durationJobs.keys.toList().filter { it !in liveIds }.forEach { id ->
                durationJobs.remove(id)?.cancel()
            }
        }
    }

    override suspend fun shutdown() {
        matching = emptyList()
        fireCallback = null
        lastForegroundPackage = null
        listenerRemover.getAndSet(null)?.run()
        durationJobsMutex.withLock {
            durationJobs.values.forEach { it.cancel() }
            durationJobs.clear()
        }
    }

    /** Called by [AppForegroundDispatcher] from the accessibility service's event handler. */
    private fun onForegroundChange(newPackage: String?) {
        val prev = lastForegroundPackage
        lastForegroundPackage = newPackage
        if (newPackage == null || prev == newPackage) return
        val cb = fireCallback ?: return
        val snap = matching
        if (snap.isEmpty()) return

        // ── AppLaunched / AppClosed: fire immediately on transition ──────────────────────
        val fires = mutableListOf<Pair<String, LinearTrigger>>()
        for (wf in snap) {
            when (val t = wf.trigger) {
                is LinearTrigger.AppLaunched -> if (t.packageName == newPackage) fires += wf.id to t
                is LinearTrigger.AppClosed -> if (prev != null && t.packageName == prev) fires += wf.id to t
                else -> Unit
            }
        }
        if (fires.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                for ((wfId, t) in fires) {
                    runCatching { cb.onFire(wfId, t) }
                        .onFailure { DebugLog.w(TAG, "app_foreground fire failed for $wfId", it) }
                }
            }
        }

        // ── AppForegroundDuration: schedule delayed timers for the new foreground app ─────
        scope.launch(Dispatchers.IO) { scheduleDurationTimers(newPackage) }
    }

    private suspend fun scheduleDurationTimers(packageName: String) {
        val cb = fireCallback ?: return
        val snap = matching
        val durationSpecs = snap.mapNotNull { wf ->
            val t = wf.trigger as? LinearTrigger.AppForegroundDuration ?: return@mapNotNull null
            if (t.packageName == packageName) wf.id to t else null
        }
        if (durationSpecs.isEmpty()) return

        // Switching to a new app cancels any in-flight timers for the previous app.
        durationJobsMutex.withLock {
            durationJobs.values.forEach { it.cancel() }
            durationJobs.clear()
        }

        for ((wfId, spec) in durationSpecs) {
            val job = scope.launch(Dispatchers.IO) {
                delay(spec.minutes.toLong() * 60_000L)
                // Re-check: the user might have switched away during the wait.
                if (AppForegroundDispatcher.lastKnown == spec.packageName) {
                    runCatching { cb.onFire(wfId, spec) }
                        .onFailure { DebugLog.w(TAG, "app_foreground_duration fire failed for $wfId", it) }
                }
                durationJobsMutex.withLock { durationJobs.remove(wfId) }
            }
            durationJobsMutex.withLock { durationJobs[wfId] = job }
        }
    }

    companion object {
        private const val TAG = "AppForegroundFamily"
    }
}
