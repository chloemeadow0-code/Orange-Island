package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Signal source for the three app-foreground triggers, fed by [AppForegroundDispatcher]:
 *
 *  - [LinearTrigger.AppLaunched] — fires when [packageName] becomes foreground.
 *  - [LinearTrigger.AppClosed]   — fires when [packageName] leaves the foreground.
 *  - [LinearTrigger.AppForegroundDuration] — fires after [packageName] has stayed continuously
 *    in the foreground for ≥ [LinearTrigger.AppForegroundDuration.minutes].
 *
 * Transitions are detected by diffing the new package against the previous one. The duration
 * variant arms a per-workflow delayed coroutine on enter; the coroutine re-checks
 * [AppForegroundDispatcher.lastKnown] before firing so a mid-window app switch cancels the fire.
 *
 * Independent implementation.
 */
object AppForegroundSignalSource {

    fun start(
        scope: CoroutineScope,
        repository: WorkflowRepository,
        starter: WorkflowStarter
    ): Job = scope.launch(Dispatchers.IO) {
        // Current matching set, refreshed on every enabled-workflow emission.
        var matching: List<LinearWorkflow> = emptyList()
        val setJob = scope.launch {
            repository.observeEnabledLinear().collectLatest { all ->
                matching = all.filter {
                    it.trigger is LinearTrigger.AppLaunched ||
                        it.trigger is LinearTrigger.AppClosed ||
                        it.trigger is LinearTrigger.AppForegroundDuration
                }
            }
        }
        // Per-workflow duration timers; replaced wholesale on every enter transition.
        var timers: List<Job> = emptyList()
        var prevPackage: String? = null

        val remover = AppForegroundDispatcher.subscribe { newPackage ->
            val current = matching
            if (current.isEmpty() || newPackage == prevPackage) return@subscribe
            val prev = prevPackage
            prevPackage = newPackage

            // AppLaunched / AppClosed fire immediately on the transition.
            val immediate = current.mapNotNull { wf ->
                when (val t = wf.trigger) {
                    is LinearTrigger.AppLaunched -> if (t.packageName == newPackage) wf.id to t else null
                    is LinearTrigger.AppClosed -> if (prev != null && t.packageName == prev) wf.id to t else null
                    else -> null
                }
            }
            if (immediate.isNotEmpty()) {
                scope.launch {
                    immediate.forEach { (id, _) -> runCatching { starter.start(id) } }
                }
            }

            // Cancel any in-flight duration timers from the previous foreground app.
            timers.forEach { runCatching { it.cancel() } }
            timers = current.mapNotNull { wf ->
                val t = wf.trigger as? LinearTrigger.AppForegroundDuration ?: return@mapNotNull null
                if (t.packageName != newPackage) return@mapNotNull null
                scope.launch {
                    delay(t.minutes.toLong() * 60_000L)
                    // Re-check: user might have switched away during the wait.
                    if (AppForegroundDispatcher.lastKnown == t.packageName) {
                        runCatching { starter.start(wf.id) }
                            .onFailure { DebugLog.w("AppForegroundSignalSource", "duration fire failed", it) }
                    }
                }
            }
        }
        // The remover is held for the source's lifetime; app-lifetime sources never tear down.
    }
}
