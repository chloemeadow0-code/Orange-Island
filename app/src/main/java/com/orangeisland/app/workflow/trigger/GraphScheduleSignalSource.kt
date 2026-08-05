package com.orangeisland.app.workflow.trigger

import android.content.Context
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.workflow.WorkflowWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Reactive scheduler for **graph-mode** [TriggerSpec.Schedule] triggers.
 *
 * Unlike the linear path (handled by [TimeSignalSource], which subscribes to
 * [WorkflowRepository.observeEnabledLinear]), the graph-mode schedule path used to be purely
 * push-based: [WorkflowWorker.schedule] ran only when the editor saved/enabled a workflow, or once
 * at cold start via [WorkflowWorker.rescheduleAll]. That meant an AI-authored change to a graph
 * workflow's schedule config (e.g. a new OneShot `atMs` written by `workflow_set_schedule`) would
 * **not** take effect until something else nudged the scheduler.
 *
 * This source closes that gap: it subscribes to [WorkflowRepository.observeAll] (every graph-mode
 * workflow, enabled or not) and reconciles the WorkManager requests on every emission. So any
 * change to a graph workflow's graphJson — including a OneShot `atMs` update — is reflected in the
 * next fire time automatically. Disabled workflows have their pending request cancelled.
 *
 * Independent implementation.
 */
object GraphScheduleSignalSource {

    fun start(context: Context, scope: CoroutineScope, repository: WorkflowRepository): Job =
        scope.launch(Dispatchers.IO) {
            repository.observeAll().collectLatest { all ->
                // The set of graph workflows that currently have a Schedule trigger on a start node.
                val scheduledIds = mutableSetOf<String>()
                all.forEach { wf ->
                    val hasScheduleStart = wf.nodes.any {
                        it is StartNode && it.trigger is TriggerSpec.Schedule
                    }
                    if (!hasScheduleStart) return@forEach
                    scheduledIds += wf.id
                    // schedule() is a no-op for disabled workflows (it returns false early), and
                    // UPDATE policy makes a re-sync cheap when nothing changed.
                    runCatching { WorkflowWorker.schedule(context, wf) }
                        .onFailure { DebugLog.w(TAG, "schedule failed for ${wf.id}", it) }
                }
                // Cancel pending requests for graph workflows that no longer carry a schedule start
                // node (deleted trigger, switched to manual, etc.). observeAll includes disabled
                // workflows so we don't lose them here — but a workflow whose start node lost its
                // Schedule trigger must be explicitly cancelled.
                val previous = previousSnapshot
                for (id in previous - scheduledIds) {
                    runCatching { WorkflowWorker.cancel(context, id) }
                }
                previousSnapshot = scheduledIds
            }
        }

    private const val TAG = "GraphScheduleSignalSource"

    /** Last set of ids we scheduled. Held at object scope so the diff across emissions works. */
    @Volatile private var previousSnapshot: Set<String> = emptySet()
}
