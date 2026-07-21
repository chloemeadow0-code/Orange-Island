package com.orangeisland.app.workflow.trigger

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.orangeisland.app.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Signal source for [com.orangeisland.app.model.LinearTrigger.BootCompleted]. Holds the current
 * matching set so the boot receiver (which runs in a fresh process before this source is up) can
 * read it via [matchingSnapshot] — and if that snapshot is empty (cold boot), the receiver falls
 * back to enqueuing a [BootFireWorker] that loads the enabled boot workflows from the repository
 * itself and fires each through its own [com.orangeisland.app.workflow.WorkflowRunner].
 *
 * The fire path lives in WorkManager (not in the BroadcastReceiver) because Android limits
 * receiver execution time; WorkManager is the documented way to do background work off a boot
 * broadcast. This is the standard pattern, not the "receiver holds the process alive inline"
 * alternative.
 *
 * Independent implementation.
 */
object BootSignalSource {

    /** Latest matching set (boot-triggered, enabled). Volatile so the boot receiver can read it
     *  without holding the source's scope. */
    @Volatile private var matchingSnapshot: List<String> = emptyList()

    fun start(
        scope: CoroutineScope,
        repository: WorkflowRepository,
        @Suppress("UNUSED_PARAMETER") starter: WorkflowStarter
    ): Job = scope.launch(Dispatchers.IO) {
        repository.observeEnabledLinear().collectLatest { all ->
            matchingSnapshot = all
                .filter { it.trigger is com.orangeisland.app.model.LinearTrigger.BootCompleted }
                .map { it.id }
        }
    }

    /** Called by [WorkflowBootReceiver] after BOOT_COMPLETED. */
    fun onBoot(context: android.content.Context) {
        val snap = matchingSnapshot
        if (snap.isNotEmpty()) {
            // Warm: enqueue a worker per workflow id (the worker is the durable fire path).
            snap.forEach { enqueueFire(context, it) }
        } else {
            // Cold boot: the source's flow hasn't emitted yet. Enqueue a single "discover and fire
            // all boot workflows" job that reads the repository itself.
            enqueueDiscover(context)
        }
    }

    private fun enqueueFire(context: android.content.Context, workflowId: String) {
        val req = OneTimeWorkRequestBuilder<BootFireWorker>()
            .setInputData(workDataOf(BootFireWorker.KEY_WORKFLOW_ID to workflowId))
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "wf_boot_$workflowId",
                androidx.work.ExistingWorkPolicy.KEEP,
                req
            )
        }
    }

    private fun enqueueDiscover(context: android.content.Context) {
        val req = OneTimeWorkRequestBuilder<BootFireWorker>().build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "wf_boot_discover",
                androidx.work.ExistingWorkPolicy.KEEP,
                req
            )
        }
    }
}
