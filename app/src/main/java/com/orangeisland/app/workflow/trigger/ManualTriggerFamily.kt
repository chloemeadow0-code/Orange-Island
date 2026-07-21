package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger

/**
 * Trigger family that owns [LinearTrigger.Manual]. No OS-level listener: manual workflows fire
 * only through [com.orangeisland.app.workflow.WorkflowRunner.run] (the UI "Run now" button or the
 * `workflow_run` AI tool), which bypasses the registry entirely.
 *
 * The family exists purely for symmetry — its [handles] claim stops the registry from logging a
 * "no family owns this trigger" warning for manual workflows. [sync] and [shutdown] are no-ops.
 *
 * Independent implementation.
 */
class ManualTriggerFamily : TriggerFamily {

    override val name: String = "manual"

    override fun handles(trigger: LinearTrigger): Boolean = trigger is LinearTrigger.Manual

    override suspend fun sync(matching: List<com.orangeisland.app.model.LinearWorkflow>, callback: TriggerFireCallback) {
        // No-op: manual workflows fire via the runner, not via this family.
    }

    override suspend fun shutdown() = Unit
}
