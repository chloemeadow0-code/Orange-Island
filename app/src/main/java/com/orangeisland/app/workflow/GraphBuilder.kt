package com.orangeisland.app.workflow

import com.orangeisland.app.model.ActionNode
import com.orangeisland.app.model.BranchNode
import com.orangeisland.app.model.FlowEdge
import com.orangeisland.app.model.FlowNode
import com.orangeisland.app.model.NodeValue
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TransformNode
import com.orangeisland.app.model.Workflow

/**
 * Builds the adjacency graph a run actually executes, and answers the two graph-theory questions
 * the engine needs: "is there a cycle?" and "which nodes are reachable from this entry?".
 *
 * Edges come from two sources, merged into one adjacency list:
 *  1. **Explicit** — the [FlowEdge]s the user drew on the canvas.
 *  2. **Implicit** — a [NodeValue.Ref] inside a node's parameters. If node B references node A's
 *     output, B cannot run until A has settled, so we synthesize an A→B edge even if the user
 *     forgot to draw one. This keeps data-flow correct by construction.
 *
 * Independent implementation. Topological sort, three-colour cycle detection, and forward/back
 * reachability are textbook algorithms; the variable names and structure here are Orange Island's
 * own and contain no third-party code.
 */
object GraphBuilder {

    /** Merge explicit edges and implicit NodeValue.Ref dependencies into one adjacency + in-degree. */
    fun compile(workflow: Workflow): CompiledGraph {
        val adjacency = mutableMapOf<String, MutableList<String>>()
        val inDegree = mutableMapOf<String, Int>()
        workflow.nodes.forEach { node ->
            adjacency[node.id] = mutableListOf()
            inDegree[node.id] = 0
        }

        fun addEdge(from: String, to: String) {
            if (from == to) return                      // self-loop guard (also caught by cycle check)
            val known = adjacency[from] ?: return        // ignore dangling refs to deleted nodes
            if (to !in inDegree) return
            if (to in known) return                      // dedupe: explicit + implicit of same edge counts once
            known += to
            inDegree[to] = (inDegree[to] ?: 0) + 1
        }

        // Explicit user edges.
        workflow.edges.forEach { addEdge(it.from, it.to) }
        // Implicit edges from NodeValue.Ref dependencies.
        implicitDependencies(workflow).forEach { (from, to) -> addEdge(from, to) }

        return CompiledGraph(workflow, adjacency, inDegree)
    }

    /** Scan every node's parameters for [NodeValue.Ref]s and emit (referencedNodeId → thisNodeId). */
    private fun implicitDependencies(workflow: Workflow): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        workflow.nodes.forEach { node ->
            when (node) {
                is StartNode -> Unit
                is ActionNode -> node.args.values.forEach { v ->
                    (v as? NodeValue.Ref)?.let { out += it.nodeId to node.id }
                }
                is BranchNode -> {
                    (node.lhs as? NodeValue.Ref)?.let { out += it.nodeId to node.id }
                    (node.rhs as? NodeValue.Ref)?.let { out += it.nodeId to node.id }
                }
                is TransformNode -> refsIn(node.op).forEach { out += it to node.id }
                // MergeNode has no NodeValue params; it reads boolean outputs via incoming edges.
                else -> Unit
            }
        }
        return out
    }

    /** NodeValue.Refs referenced by a TransformOp (input + extras, where present). */
    private fun refsIn(op: com.orangeisland.app.model.TransformOp): List<String> {
        val refs = mutableListOf<String>()
        when (op) {
            is com.orangeisland.app.model.TransformOp.Join -> {
                (op.input as? NodeValue.Ref)?.let { refs += it.nodeId }
                op.extras.forEach { (it as? NodeValue.Ref)?.let { r -> refs += r.nodeId } }
            }
            else -> Unit   // the other ops take literal pattern/path/int config, no Refs
        }
        return refs
    }

    /**
     * Three-colour DFS cycle detection. WHITE = unseen, GREY = on the current recursion stack,
     * BLACK = fully explored. Encountering a GREY node means we walked back into the stack → cycle.
     * Returns true if a cycle exists.
     */
    fun hasCycle(graph: CompiledGraph): Boolean {
        val colour = graph.workflow.nodes.associate { it.id to WHITE }.toMutableMap()
        fun visit(nodeId: String): Boolean {
            colour[nodeId] = GREY
            for (next in graph.adjacency[nodeId].orEmpty()) {
                when (colour[next]) {
                    GREY -> return true
                    WHITE -> if (visit(next)) return true
                    // BLACK — already finished, skip
                }
            }
            colour[nodeId] = BLACK
            return false
        }
        for (node in graph.workflow.nodes) {
            if (colour[node.id] == WHITE && visit(node.id)) return true
        }
        return false
    }

    /**
     * The set of node ids the engine should actually execute, starting from [entryIds].
     *
     * Two passes:
     *  1. Forward BFS from the entries, following adjacency edges — everything downstream.
     *  2. Reverse BFS back from the forward-reached set, following reversed edges — everything
     *     upstream that contributes a dependency (e.g. a node that a reached action references).
     *
     * The intersection of both passes is the minimal subgraph rooted at the entries that still
     * satisfies every NodeValue.Ref. Anything outside it is an orphan branch the run skips.
     */
    fun reachable(graph: CompiledGraph, entryIds: List<String>): Set<String> {
        val forward = bfs(entryIds) { graph.adjacency[it].orEmpty() }
        val reverse = buildReverseAdjacency(graph.adjacency)
        // Reverse-BFS starts from the forward-reached set and walks backwards.
        val withUpstream = bfs(forward.toList()) { reverse[it].orEmpty() }
        return withUpstream
    }

    private fun bfs(seeds: List<String>, neighbours: (String) -> List<String>): Set<String> {
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        seeds.forEach { if (seen.add(it)) queue.addLast(it) }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            neighbours(cur).forEach { if (seen.add(it)) queue.addLast(it) }
        }
        return seen
    }

    private fun buildReverseAdjacency(adjacency: Map<String, List<String>>): Map<String, List<String>> {
        val reverse = mutableMapOf<String, MutableList<String>>()
        adjacency.forEach { (from, targets) ->
            targets.forEach { to -> reverse.getOrPut(to) { mutableListOf() }.add(from) }
        }
        return reverse
    }

    private const val WHITE = 0
    private const val GREY = 1
    private const val BLACK = 2
}
