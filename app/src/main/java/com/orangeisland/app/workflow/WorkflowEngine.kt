package com.orangeisland.app.workflow

import com.orangeisland.app.model.FlowNode
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.model.Workflow as Wf
import com.orangeisland.app.model.TriggerSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Executes a [Workflow] as a topological walk over its node graph.
 *
 * Algorithm (independent implementation; topological sort with in-degree decrement is textbook):
 *  1. Resolve the entry StartNodes for this trigger (manual = all StartNode(Manual); a scheduled /
 *     intent / api trigger targets the specific StartNode that carries it).
 *  2. Compile the graph (explicit edges + implicit NodeValue.Ref deps) and reject cycles.
 *  3. Compute the reachable subgraph from the entries; nodes outside it are skipped.
 *  4. Mark each entry StartNode Done with the trigger payload (its output flows to referencing nodes).
 *  5. Walk in topological order: a node becomes runnable when its in-degree hits zero; before
 *     running, every incoming edge's guard must be satisfiable by at least one source's state.
 *  6. Failures do not abort the walk �?an Errored node simply does not satisfy OnSuccess/Bool/Regex
 *     edges, so downstream error-handling branches (guarded by OnFailure) still run. Only at the
 *     end do we judge the whole run: success unless some Errored node was left un-handled (no
 *     outgoing OnFailure edge whose target ended Done).
 *
 * Cooperative cancellation: every node checks [ensureActive] before doing work, and the
 * [WorkflowGuard] enforces time/call caps. A cancelled or over-budget run throws and the engine
 * returns a CANCELLED/FAILED result.
 *
 * Android-free at this layer: tool dispatch is injected via [NodeExecutor.ToolRunner], so the
 * engine and its unit tests never touch ToolDispatcher directly.
 */
class WorkflowEngine {

    suspend fun execute(
        workflow: Workflow,
        triggerSource: TriggerSource,
        triggerPayload: String = "{}",
        guard: WorkflowGuard? = null,
        toolRunner: NodeExecutor.ToolRunner,
        llmRunner: NodeExecutor.LLMRunner? = null,
        notificationRunner: NodeExecutor.NotificationRunner? = null,
        chatMessageRunner: NodeExecutor.ChatMessageRunner? = null,
        onState: (String, NodeState) -> Unit = { _, _ -> }
    ): RunResult {
        val startedAt = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()
        val logger = RunLogger()
        val states = mutableMapOf<String, NodeState>()

        fun buildResult(success: Boolean, message: String): RunResult {
            val finishedAt = System.currentTimeMillis()
            return RunResult(
                workflowId = workflow.id, runId = runId,
                success = success, message = message,
                startedAt = startedAt, finishedAt = finishedAt,
                states = states.toMap(), logs = logger.entries
            )
        }

        logger.debug("Starting workflow '${workflow.name}' (${workflow.id}) [run=$runId]")

        return withContext(Dispatchers.Default) {
            try {
                // 1. Resolve entry nodes.
                val entries = resolveEntries(workflow, triggerSource)
                if (entries.isEmpty()) {
                    logger.warn("No matching start node for trigger $triggerSource")
                    return@withContext buildResult(false, "No matching start node")
                }
                entries.forEach { logger.debug("Entry: '${it.label.ifBlank { it.id }}' (${it.trigger})", it.id, it.label) }
                currentCoroutineContext().ensureActive()

                // 2. Compile + cycle check.
                val graph = GraphBuilder.compile(workflow)
                if (GraphBuilder.hasCycle(graph)) {
                    logger.error("Workflow has a cycle; aborting")
                    return@withContext buildResult(false, "Workflow contains a cycle")
                }

                // 3. Reachable subgraph.
                val reachable = GraphBuilder.reachable(graph, entries.map { it.id })

                // 4. Seed entries as Done with the trigger payload.
                entries.forEach { entry ->
                    states[entry.id] = NodeState.Done(triggerPayload)
                    onState(entry.id, NodeState.Done(triggerPayload))
                }

                // 5. Topological walk.
                val executor = NodeExecutor(states, ValueResolver(states), guard, toolRunner, llmRunner, notificationRunner, chatMessageRunner, logger, onState)
                walk(executor, workflow, graph, reachable, entries, states, logger, onState, triggerPayload)

                // 6. Final judgement: any Errored node without a satisfied OnFailure exit �?run failed.
                val unhandled = hasUnhandledFailure(graph, workflow, states, entries)
                if (unhandled == null) {
                    logger.debug("Workflow '${workflow.name}' completed")
                    buildResult(true, "Completed")
                } else {
                    logger.error("Unhandled failure at '${unhandled.label.ifBlank { unhandled.id }}'", unhandled.id, unhandled.label)
                    buildResult(false, "Node '${unhandled.label.ifBlank { unhandled.id }}' failed without an error handler")
                }
            } catch (e: CancellationException) {
                logger.warn("Run cancelled")
                buildResult(false, "Cancelled")
            } catch (e: WorkflowLimitExceeded) {
                logger.error("Run exceeded a guard limit: ${e.message}")
                buildResult(false, e.message ?: "Limit exceeded")
            } catch (e: Exception) {
                logger.error("Run crashed: ${e.message ?: e::class.simpleName}")
                buildResult(false, "Crash: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    // ── Entry resolution ────────────────────────────────────────────────────

    private fun resolveEntries(workflow: Wf, source: TriggerSource): List<StartNode> {
        val starts = workflow.nodes.filterIsInstance<StartNode>()
        if (starts.isEmpty()) return emptyList()
        val matched = when (source) {
            is TriggerSource.Manual -> starts.filter { it.trigger is TriggerSpec.Manual }
            is TriggerSource.Targeted -> starts.filter { matchesTarget(it, source) }
        }
        // Fallback: an explicit user action ("Run now" / AI workflow_run) should fire any graph
        // that has a start node, not just ones whose trigger type happens to match. Otherwise a
        // graph authored with a Schedule/Intent/AppOpen start node can never be run on demand ��
        // the user taps Run and gets "No matching start node" forever. Only the SCHEDULE and API
        // (background worker) Targeted kinds stay strict, so a periodic timer never accidentally
        // fires a Manual node. Fall back to every start node, preserving declared order.
        if (matched.isEmpty() && source.isExplicit()) {
            return starts
        }
        return matched
    }

    /** Is this trigger source an explicit, on-demand invocation (user tapped Run, or the AI called
     *  workflow_run) as opposed to a passive system signal (timer / boot / intent broadcast)? */
    private fun TriggerSource.isExplicit(): Boolean = when (this) {
        is TriggerSource.Manual -> true
        is TriggerSource.Targeted.Node -> kind == TriggerKind.API
    }

    /**
     * Does [node]'s trigger fire for [target]?
     *  - A pinned [TriggerSource.Targeted.Node] with a non-blank nodeId matches by identity.
     *  - Otherwise the node's [TriggerSpec] kind must equal the target's [TriggerKind], and for
     *    INTENT triggers the action string must equal [TriggerSource.Targeted.Node.match].
     */
    private fun matchesTarget(node: StartNode, target: TriggerSource.Targeted): Boolean {
        val pinned = target as TriggerSource.Targeted.Node
        if (pinned.nodeId.isNotBlank()) return node.id == pinned.nodeId
        return when (val spec = node.trigger) {
            is TriggerSpec.Manual -> false   // Manual nodes are not fired by targeted triggers.
            is TriggerSpec.Schedule -> pinned.kind == TriggerKind.SCHEDULE
            is TriggerSpec.IntentAction ->
                pinned.kind == TriggerKind.INTENT &&
                    (pinned.match == null || spec.action == pinned.match)
            is TriggerSpec.AppOpen -> pinned.kind == TriggerKind.APP_OPEN
            is TriggerSpec.Voice -> pinned.kind == TriggerKind.VOICE
            is TriggerSpec.Api -> pinned.kind == TriggerKind.API
        }
    }

    // ── Topological walk ────────────────────────────────────────────────────

    private suspend fun walk(
        executor: NodeExecutor,
        workflow: Workflow,
        graph: CompiledGraph,
        reachable: Set<String>,
        entries: List<StartNode>,
        states: MutableMap<String, NodeState>,
        logger: RunLogger,
        onState: (String, NodeState) -> Unit,
        triggerPayload: String
    ) {
        val nodeById = workflow.nodes.associateBy { it.id }
        val startIds = entries.map { it.id }.toSet()
        val incomingByTarget = workflow.edges.groupBy { it.to }

        // In-degree within the reachable subgraph, counting only non-start nodes (starts are seeded).
        val inDeg = mutableMapOf<String, Int>()
        reachable.forEach { id -> if (id !in startIds) inDeg[id] = 0 }
        graph.adjacency.forEach { (from, targets) ->
            if (from !in reachable || from in startIds) return@forEach
            targets.forEach { to ->
                if (to in reachable && to !in startIds) inDeg[to] = (inDeg[to] ?: 0) + 1
            }
        }

        val queue = ArrayDeque<String>()
        inDeg.filter { it.value == 0 }.keys.forEach { queue.addLast(it) }

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val id = queue.removeFirst()
            if (id in states) continue              // already executed (e.g. a start node)
            val node = nodeById[id] ?: continue

            val incoming = incomingByTarget[id].orEmpty()
                .filter { it.from in reachable && (it.from !in startIds || it.from in entries.map { e -> e.id }) }

            // A node runs if at least one incoming edge's guard is satisfied. No incoming edges
            // (orphan after reachability trim) �?run unconditionally.
            val shouldRun = incoming.isEmpty() || incoming.any { edge ->
                val srcState = states[edge.from]
                srcState != null &&
                    srcState !is NodeState.Skipped &&
                    ConditionEvaluator.edgeFires(edge.guard, srcState)
            }

            if (!shouldRun) {
                val reason = "Incoming edge conditions not met"
                logger.debug("Skipping '${node.label.ifBlank { id }}': $reason", id, node.label)
                states[id] = NodeState.Skipped(reason)
                onState(id, NodeState.Skipped(reason))
            } else {
                executor.execute(node, incoming, triggerPayload)
            }

            // Decrement downstream in-degrees whether we ran or skipped �?both settle the node.
            graph.adjacency[id].orEmpty().forEach { next ->
                val deg = inDeg[next] ?: return@forEach
                val newDeg = deg - 1
                inDeg[next] = newDeg
                if (newDeg == 0) queue.addLast(next)
            }
        }
    }

    // ── Final judgement ─────────────────────────────────────────────────────

    private fun hasUnhandledFailure(
        graph: CompiledGraph,
        workflow: Workflow,
        states: Map<String, NodeState>,
        entries: List<StartNode>
    ): FlowNode? {
        val nodeById = workflow.nodes.associateBy { it.id }
        val outgoingBySource = workflow.edges.groupBy { it.from }
        for ((id, state) in states) {
            if (state !is NodeState.Errored) continue
            // An Errored node is "handled" if some outgoing OnFailure edge leads to a Done node.
            val handled = outgoingBySource[id].orEmpty().any { edge ->
                edge.guard is com.orangeisland.app.model.EdgeGuard.OnFailure &&
                    states[edge.to] is NodeState.Done
            }
            if (!handled) return nodeById[id]
        }
        return null
    }
}

/** What launched the run. Manual fires all Manual start nodes; Targeted fires one specific node. */
sealed class TriggerSource {
    data object Manual : TriggerSource()
    sealed class Targeted : TriggerSource() {
        /** @param nodeId blank = match by [kind] (and [match] for intent action); non-blank = pin. */
        data class Node(val kind: TriggerKind, val nodeId: String = "", val match: String? = null) : Targeted()
    }
}

enum class TriggerKind { SCHEDULE, INTENT, APP_OPEN, VOICE, API }
