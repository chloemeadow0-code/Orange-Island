package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger

/**
 * Owns [LinearTrigger.Manual]. No OS hook, no `Flow` subscription: manual workflows fire only
 * through [WorkflowRunner.run] (the UI "Run now" button or the `workflow_run` AI tool), which
 * bypasses the host entirely.
 *
 * This object exists purely as a place to declare ownership — its presence in [WorkflowTriggerHost]
 * documents that manual triggers are a known, intentionally-unhandled kind (so a future audit
 * doesn't mistake their absence for a bug). [start] is a no-op.
 *
 * Independent implementation.
 */
object ManualSignalSource {
    fun start() = Unit
}
