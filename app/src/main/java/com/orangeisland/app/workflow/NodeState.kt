package com.orangeisland.app.workflow

import com.orangeisland.app.model.Workflow

/**
 * Terminal state of a single node within a run. The engine stores these in a Map as it walks the
 * graph; downstream nodes and edge guards read them to decide whether to fire.
 *
 * Independent implementation. Pending/Running/Done/Skipped/Errored is a standard five-state
 * progression for topological executors; the names and the result-carrying shape are Orange
 * Island's own.
 */
sealed class NodeState {
    data object Pending : NodeState()
    data object Running : NodeState()
    data class Done(val output: String) : NodeState()
    data class Skipped(val reason: String) : NodeState()
    data class Errored(val message: String) : NodeState()
}

/** Snapshot the engine emits via [onNodeStateChange]; the UI subscribes to light up canvas cards. */
data class RunSnapshot(
    val workflowId: String,
    val runId: String,
    val states: Map<String, NodeState>
)

/** Final outcome of a run, returned from [WorkflowEngine.execute]. */
data class RunResult(
    val workflowId: String,
    val runId: String,
    val success: Boolean,
    val message: String,
    val startedAt: Long,
    val finishedAt: Long,
    val states: Map<String, NodeState>,
    val logs: List<RunLogEntry>
)

/** Thrown when a guard limit (run time, tool-call count, background tool whitelist) is exceeded. */
class WorkflowLimitExceeded(message: String) : RuntimeException(message)

/** Convenience: a workflow + the materialized adjacency graph used by the engine. */
data class CompiledGraph(
    val workflow: Workflow,
    /** nodeId → downstream node ids (merged explicit edges + implicit NodeValue.Ref deps). */
    val adjacency: Map<String, List<String>>,
    /** nodeId → number of upstream nodes that must settle before this one can run. */
    val inDegree: Map<String, Int>
)
