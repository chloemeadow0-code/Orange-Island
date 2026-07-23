package com.orangeisland.app.workflow

import com.orangeisland.app.model.ActionNode
import com.orangeisland.app.model.BranchNode
import com.orangeisland.app.model.ChatMessageNode
import com.orangeisland.app.model.FlowNode
import com.orangeisland.app.model.LLMNode
import com.orangeisland.app.model.MergeNode
import com.orangeisland.app.model.NotifyNode
import com.orangeisland.app.model.Reducer
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TransformNode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Executes a single node, mutating the shared [states] map and emitting live state via [onState].
 * The dispatch is by sealed-class kind; each branch returns true on success, false on error.
 *
 * The tool-dispatch concern is abstracted behind [ToolRunner] so this class (and the engine that
 * calls it) stays pure-JVM and unit-testable without Android or a real ToolDispatcher.
 *
 * Independent implementation.
 */
class NodeExecutor(
    private val states: MutableMap<String, NodeState>,
    private val resolver: ValueResolver,
    private val guard: WorkflowGuard?,
    private val toolRunner: ToolRunner,
    private val llmRunner: LLMRunner? = null,
    private val notificationRunner: NotificationRunner? = null,
    private val chatMessageRunner: ChatMessageRunner? = null,
    private val logger: RunLogger,
    private val onState: (String, NodeState) -> Unit
) {
    /** Functional interface for dispatching a tool call. Returns the tool's result string. */
    fun interface ToolRunner {
        suspend fun run(toolName: String, argsJson: String): String
    }

    /** Functional interface for dispatching an LLM inference call. Returns the model's text output. */
    fun interface LLMRunner {
        suspend fun run(provider: String, modelId: String, systemPrompt: String?, prompt: String): String
    }

    /** Functional interface for posting a system notification from a workflow node. */
    fun interface NotificationRunner {
        suspend fun run(title: String, content: String, priority: String): String
    }

    /** Functional interface for inserting a chat message from a workflow node. Returns the message id. */
    fun interface ChatMessageRunner {
        suspend fun run(text: String, participant: String): String
    }

    suspend fun execute(node: FlowNode, incomingEdges: List<com.orangeisland.app.model.FlowEdge>, triggerPayload: String): Boolean {
        currentCoroutineContext().ensureActive()
        return when (node) {
            is StartNode -> runStart(node, triggerPayload)
            is BranchNode -> runBranch(node, triggerPayload)
            is MergeNode -> runMerge(node, incomingEdges)
            is TransformNode -> runTransform(node, incomingEdges, triggerPayload)
            is ActionNode -> runAction(node)
            is LLMNode -> runLLM(node, triggerPayload)
            is NotifyNode -> runNotify(node, triggerPayload)
            is ChatMessageNode -> runChatMessage(node, triggerPayload)
        }
    }

    // ── Strategies ───────────────────────────────────────────────────────────

    private fun runStart(node: StartNode, triggerPayload: String): Boolean {
        // StartNode is already marked Done by the engine before the topological walk; this is a
        // no-op safety net in case a StartNode reaches execute() another way.
        if (states[node.id] == null) {
            mark(node, NodeState.Done(triggerPayload))
        }
        return true
    }

    private fun runBranch(node: BranchNode, triggerPayload: String): Boolean = try {
        mark(node, NodeState.Running)
        val lhs = resolver.resolve(node.lhs, triggerPayload)
        val rhs = resolver.resolve(node.rhs, triggerPayload)
        val result = ConditionEvaluator.compare(lhs, rhs, node.cmp)
        mark(node, NodeState.Done(result.toString()))
        true
    } catch (e: Exception) {
        fail(node, e.message ?: e::class.simpleName.orEmpty())
    }

    private fun runMerge(node: MergeNode, incomingEdges: List<com.orangeisland.app.model.FlowEdge>): Boolean = try {
        mark(node, NodeState.Running)
        // Read boolean outputs from every node that has an edge INTO this merge node. The engine
        // passes those edges via incomingEdges; we look up each source's Done output.
        val inputs = incomingEdges.mapNotNull { edge ->
            val src = states[edge.from] as? NodeState.Done ?: return@mapNotNull null
            ConditionEvaluator.parseBool(src.output)
        }
        val out = when (node.reducer) {
            Reducer.ALL_TRUE -> inputs.isNotEmpty() && inputs.all { it }
            Reducer.ANY_TRUE -> inputs.any { it }
        }
        mark(node, NodeState.Done(out.toString()))
        true
    } catch (e: Exception) {
        fail(node, e.message ?: e::class.simpleName.orEmpty())
    }

    private fun runTransform(
        node: TransformNode,
        incomingEdges: List<com.orangeisland.app.model.FlowEdge>,
        triggerPayload: String
    ): Boolean = try {
        mark(node, NodeState.Running)
        val output = when (val op = node.op) {
            is com.orangeisland.app.model.TransformOp.Join -> {
                val input = resolver.resolve(op.input, triggerPayload)
                val tail = op.extras.joinToString("") { resolver.resolve(it, triggerPayload) }
                TransformOps.apply(op, input + tail)
            }
            else -> {
                // Non-Join ops carry their config literally; the input they shape comes from the
                // single incoming edge's source output (regex/jsonpath/slice) or is self-contained
                // (random/fixed). Resolve the upstream via the first incoming edge.
                val input = if (op is com.orangeisland.app.model.TransformOp.Regex ||
                    op is com.orangeisland.app.model.TransformOp.JsonPath ||
                    op is com.orangeisland.app.model.TransformOp.Slice) {
                    val sourceId = incomingEdges.firstOrNull()?.from
                    if (sourceId != null) (states[sourceId] as? NodeState.Done)?.output ?: triggerPayload
                    else triggerPayload
                } else triggerPayload
                TransformOps.apply(op, input)
            }
        }
        mark(node, NodeState.Done(output))
        true
    } catch (e: Exception) {
        fail(node, e.message ?: e::class.simpleName.orEmpty())
    }

    private suspend fun runAction(node: ActionNode): Boolean = try {
        if (node.toolName.isBlank()) {
            return fail(node, "Action node has no tool name")
        }
        mark(node, NodeState.Running)
        val argsJson = buildArgsJson(node)
        val verdict = guard?.preflight(node, argsJson)
        if (verdict is WorkflowGuard.Verdict.Deny) {
            logger.warn("Action '${node.label.ifBlank { node.id }}' blocked: ${verdict.message}", node.id, node.label)
            return fail(node, verdict.message)
        }
        logger.debug("Calling tool ${node.toolName}", node.id, node.label)
        currentCoroutineContext().ensureActive()
        val result = toolRunner.run(node.toolName, argsJson)
        mark(node, NodeState.Done(result))
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: WorkflowLimitExceeded) {
        throw e
    } catch (e: Exception) {
        fail(node, e.message ?: e::class.simpleName.orEmpty())
    }

    private suspend fun runLLM(node: LLMNode, triggerPayload: String): Boolean = try {
        val runner = llmRunner ?: return fail(node, "LLM not available in this runner")
        mark(node, NodeState.Running)
        val prompt = resolver.resolve(node.prompt, triggerPayload)
        logger.debug("LLM prompt length=${prompt.length}", node.id, node.label)
        currentCoroutineContext().ensureActive()
        val result = runner.run(
            provider = node.provider,
            modelId = node.modelId,
            systemPrompt = node.systemPrompt.takeIf { it.isNotBlank() },
            prompt = prompt
        )
        mark(node, NodeState.Done(result))
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        fail(node, e.message ?: e::class.simpleName.orEmpty())
    }

    private suspend fun runNotify(node: NotifyNode, triggerPayload: String): Boolean = try {
        val runner = notificationRunner ?: return fail(node, "Notification not available in this runner")
        mark(node, NodeState.Running)
        val title = resolver.resolve(node.title, triggerPayload)
        val content = resolver.resolve(node.content, triggerPayload)
        logger.debug("Notify title=$title content=${content.take(80)}", node.id, node.label)
        currentCoroutineContext().ensureActive()
        val result = runner.run(title, content, node.priority)
        mark(node, NodeState.Done(result))
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        fail(node, e.message ?: e::class.simpleName.orEmpty())
    }

    private suspend fun runChatMessage(node: ChatMessageNode, triggerPayload: String): Boolean = try {
        val runner = chatMessageRunner ?: return fail(node, "Chat message not available in this runner")
        mark(node, NodeState.Running)
        val text = resolver.resolve(node.text, triggerPayload)
        logger.debug("ChatMessage text=${text.take(80)}", node.id, node.label)
        currentCoroutineContext().ensureActive()
        val result = runner.run(text, node.participant)
        mark(node, NodeState.Done(result))
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        fail(node, e.message ?: e::class.simpleName.orEmpty())
    }

    /** Build the JSON arguments object, resolving every NodeValue against upstream outputs.
     *  Hand-rolled (no org.json) so the engine stays pure-JVM and unit-testable without the
     *  Android jar's mocked JSONObject. The values are always strings, so a flat object of
     *  string→string is the full shape needed. */
    private fun buildArgsJson(node: ActionNode): String {
        if (node.args.isEmpty()) return "{}"
        val resolved = node.args.mapValues { (_, v) -> resolver.resolve(v) }
        val entries = resolved.entries.joinToString(",") { (k, v) ->
            "\"" + escapeJson(k) + "\":\"" + escapeJson(v) + "\""
        }
        return "{$entries}"
    }

    /** Minimal JSON string escaping for tool argument values. */
    private fun escapeJson(s: String): String = buildString(s.length + 2) {
        for (ch in s) when (ch) {
            '\\' -> append("\\\\"); '"' -> append("\\\"")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            '\b' -> append("\\b"); '\u000C' -> append("\\f")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }

    // ── State helpers ───────────────────────────────────────────────────────

    private fun mark(node: FlowNode, state: NodeState) {
        states[node.id] = state
        onState(node.id, state)
    }

    private fun fail(node: FlowNode, message: String): Boolean {
        logger.error("Node '${node.label.ifBlank { node.id }}' failed: $message", node.id, node.label)
        mark(node, NodeState.Errored(message))
        return false
    }
}
