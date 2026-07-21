package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.workflow.TriggerKind
import com.orangeisland.app.workflow.TriggerSource
import com.orangeisland.app.workflow.WorkflowRunner

/**
 * Suspends until [workflowId] has been run to completion (or failed), routing through a
 * BACKGROUND-mode [WorkflowRunner]. Implemented as a functional type rather than an interface so
 * each signal source can call it without depending on a shared callback object — the source just
 * holds a `suspend (String) -> Unit` it was constructed with.
 *
 * This is a [fun interface] (single abstract method) so call sites can pass a lambda.
 */
fun interface WorkflowStarter {
    /** Run [workflowId] headlessly; return when the run has settled. */
    suspend fun start(workflowId: String)
}

/**
 * Builds the standard [WorkflowStarter] used by every signal source: it fires the workflow through
 * a fresh BACKGROUND-mode runner using the API trigger kind (the runner re-checks enabled,
 * cooldown, conditions, and the background-safe tool whitelist).
 *
 * Kept as a top-level factory (not a method on a host object) so a signal source can capture it
 * without holding a reference to the app container — the closure owns the runner dependency.
 */
fun workflowStarter(
    runnerProvider: () -> WorkflowRunner
): WorkflowStarter = WorkflowStarter { id ->
    runCatching {
        runnerProvider().run(
            workflowId = id,
            mode = WorkflowRunner.Mode.BACKGROUND,
            source = TriggerSource.Targeted.Node(kind = TriggerKind.API)
        )
    }
}
