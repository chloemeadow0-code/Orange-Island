package com.orangeisland.app.workflow.trigger

import android.content.Context
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lifecycle host for the workflow-trigger signal sources. **Not** a registry: it does not iterate
 * over a list of sources or call a `sync` method on each. Each signal source owns its own
 * `Flow` subscription to the enabled-workflow set and reconciles its OS hooks itself.
 *
 * The host's only jobs are:
 *  1. start each source (handing it the repository / scope / starter it needs), and
 *  2. keep the [Job]s so [shutdown] can cancel them.
 *
 * This split is deliberate: a per-source `Flow` subscription means a source that has zero matching
 * workflows simply never collects anything (no callback, no diff) — the cost is proportional to
 * how many workflows actually use a given signal, with no centralised debounce or fan-out loop.
 *
 * Independent implementation.
 */
class WorkflowTriggerHost(
    private val context: Context,
    private val repository: WorkflowRepository,
    private val scope: CoroutineScope,
    private val starter: WorkflowStarter
) {
    private val jobs = mutableListOf<Job>()

    /** Start every signal source. Idempotent: a second call after [shutdown] re-starts. */
    fun start() {
        if (jobs.isNotEmpty()) return
        // Each call below subscribes the source to repository.observeEnabledLinear() and returns
        // the subscription's Job. The source filters to the workflows it handles.
        runCatching { ManualSignalSource.start() }
            .onFailure { DebugLog.e(TAG, "manual source failed", it) }
        jobs += runCatching { BootSignalSource.start(scope, repository, starter) }
            .onFailure { DebugLog.e(TAG, "boot source failed", it) }.getOrDefault(scope.launch { })
        jobs += runCatching { TimeSignalSource.start(context, scope, repository) }
            .onFailure { DebugLog.e(TAG, "time source failed", it) }.getOrDefault(scope.launch { })
        jobs += runCatching { BroadcastSignalSource.start(context, scope, repository, starter) }
            .onFailure { DebugLog.e(TAG, "broadcast source failed", it) }.getOrDefault(scope.launch { })
        jobs += runCatching { AppForegroundSignalSource.start(scope, repository, starter) }
            .onFailure { DebugLog.e(TAG, "app-foreground source failed", it) }.getOrDefault(scope.launch { })
        jobs += runCatching { NotificationSignalSource.start(scope, repository, starter) }
            .onFailure { DebugLog.e(TAG, "notification source failed", it) }.getOrDefault(scope.launch { })
        jobs += runCatching { GeofenceSignalSource.start(context, scope, repository, starter) }
            .onFailure { DebugLog.e(TAG, "geofence source failed", it) }.getOrDefault(scope.launch { })
        DebugLog.d(TAG, "host started")
    }

    /** Cancel every source's subscription. */
    fun shutdown() {
        jobs.forEach { runCatching { it.cancel() } }
        jobs.clear()
    }

    companion object { private const val TAG = "WorkflowTriggerHost" }
}
