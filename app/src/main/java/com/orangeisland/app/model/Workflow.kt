package com.orangeisland.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Workflow domain model — a directed graph of nodes and edges that the
 * [com.orangeisland.app.workflow.WorkflowEngine] executes.
 *
 * Independent implementation. The node-and-edge graph abstraction, the static-or-reference
 * parameter model, and the edge-guard concept are general workflow-engineering ideas; the
 * concrete types, names, and field shapes here are Orange Island's own.
 *
 * Persistence: [Workflow] is serialized to JSON and stored as a blob in
 * [com.orangeisland.app.data.local.WorkflowEntity.graphJson]. The kotlinx.serialization
 * polymorphism below (`classDiscriminator = "kind"`) is what lets a single `List<FlowNode>`
 * round-trip through that blob.
 */

@Serializable
data class Workflow(
    val id: String,
    val name: String,
    val description: String = "",
    val nodes: List<FlowNode> = emptyList(),
    val edges: List<FlowEdge> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ── Run outcome (mirrored to WorkflowEntity.lastRunStatus as its name) ─────

@Serializable
enum class RunStatus { RUNNING, SUCCESS, FAILED, CANCELLED }

// ── Nodes ──────────────────────────────────────────────────────────────────

/**
 * A node in the workflow graph. Five concrete kinds, each with a distinct execution strategy
 * in [com.orangeisland.app.workflow.NodeExecutor]:
 *  - [StartNode]    : entry point; carries the trigger that launches the run.
 *  - [ActionNode]   : invokes a tool by name.
 *  - [BranchNode]   : compares two values, emits a boolean.
 *  - [MergeNode]    : reduces several incoming booleans to one.
 *  - [TransformNode]: shapes a string (regex / json-path / slice / join / random / fixed).
 */
@Serializable
sealed class FlowNode {
    abstract val id: String
    abstract val label: String
    abstract val pos: Vec2

    /** The discriminator persisted in JSON (e.g. `"start"`, `"action"`). */
    abstract val kind: String

    @Serializable
    data class Vec2(val x: Float = 0f, val y: Float = 0f)
}

@Serializable
@SerialName("start")
data class StartNode(
    override val id: String,
    override val label: String = "",
    override val pos: FlowNode.Vec2 = FlowNode.Vec2(),
    val trigger: TriggerSpec = TriggerSpec.Manual
) : FlowNode() {
    override val kind: String = "start"
}

@Serializable
@SerialName("action")
data class ActionNode(
    override val id: String,
    override val label: String = "",
    override val pos: FlowNode.Vec2 = FlowNode.Vec2(),
    /** Fully-qualified tool name as the ToolDispatcher sees it (e.g. `web_search`,
     *  `plugin__my_plugin__do_thing`, `mcp__server__tool`). */
    val toolName: String,
    /** Tool arguments. Each value is either a literal string or a reference to another node's
     *  output, resolved at execution time by [com.orangeisland.app.workflow.ValueResolver]. */
    val args: Map<String, NodeValue> = emptyMap(),
    /** Optional inline script. Reserved for a future JS step node; the engine currently ignores
     *  it when [toolName] is non-blank, and runs it through the plugin sandbox when blank. */
    val script: String? = null
) : FlowNode() {
    override val kind: String = "action"
}

@Serializable
@SerialName("branch")
data class BranchNode(
    override val id: String,
    override val label: String = "",
    override val pos: FlowNode.Vec2 = FlowNode.Vec2(),
    val lhs: NodeValue,
    val cmp: Comparison,
    val rhs: NodeValue
) : FlowNode() {
    override val kind: String = "branch"
}

@Serializable
@SerialName("merge")
data class MergeNode(
    override val id: String,
    override val label: String = "",
    override val pos: FlowNode.Vec2 = FlowNode.Vec2(),
    val reducer: Reducer
) : FlowNode() {
    override val kind: String = "merge"
}

@Serializable
@SerialName("transform")
data class TransformNode(
    override val id: String,
    override val label: String = "",
    override val pos: FlowNode.Vec2 = FlowNode.Vec2(),
    val op: TransformOp
) : FlowNode() {
    override val kind: String = "transform"
}

// ── Branch / Merge operators ───────────────────────────────────────────────

@Serializable
enum class Comparison {
    EQ, NE, LT, LE, GT, GE,
    CONTAINS, NOT_CONTAINS,
    IN, NOT_IN
}

@Serializable
enum class Reducer { ALL_TRUE, ANY_TRUE }

// ── Transform operations ───────────────────────────────────────────────────

/**
 * A pure string-shaping operation. The engine resolves [input] (and [extras], where present)
 * against upstream node outputs before applying the op.
 *
 * [FIXED] exists for testing/branching: a transform that always returns the same value lets a
 * user build a "constant" tap without a dedicated node kind.
 */
@Serializable
sealed class TransformOp {
    @Serializable
    @SerialName("regex")
    data class Regex(val pattern: String, val group: Int = 0, val fallback: String = "") : TransformOp()

    @Serializable
    @SerialName("jsonpath")
    data class JsonPath(val path: String, val fallback: String = "") : TransformOp()

    @Serializable
    @SerialName("slice")
    data class Slice(val start: Int = 0, val length: Int = -1, val fallback: String = "") : TransformOp()

    @Serializable
    @SerialName("join")
    data class Join(val input: NodeValue, val extras: List<NodeValue> = emptyList()) : TransformOp()

    @Serializable
    @SerialName("random_int")
    data class RandomInt(val min: Int = 0, val max: Int = 100, val fixed: String? = null) : TransformOp()

    @Serializable
    @SerialName("random_text")
    data class RandomText(val length: Int = 8, val charset: String = ALNUM, val fixed: String? = null) : TransformOp() {
        companion object {
            const val ALNUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        }
    }

    @Serializable
    @SerialName("fixed")
    data class Fixed(val value: String) : TransformOp()
}

// ── Parameter values (the data-flow primitive) ────────────────────────────

/**
 * Either a literal string or a reference to another node's output. This is the sole mechanism
 * by which data flows between nodes: an [ActionNode]'s arg can point at an upstream node, and
 * the resolver substitutes the upstream's result string at execution time.
 */
@Serializable
sealed class NodeValue {
    @Serializable
    @SerialName("lit")
    data class Literal(val value: String) : NodeValue()

    @Serializable
    @SerialName("ref")
    data class Ref(val nodeId: String) : NodeValue()
}

// ── Edges ──────────────────────────────────────────────────────────────────

@Serializable
data class FlowEdge(
    val id: String,
    val from: String,
    val to: String,
    /** When null the edge is unconditional. Otherwise the guard decides, based on the source
     *  node's terminal state, whether this edge "fires" and lets the target node run. */
    val guard: EdgeGuard? = null
)

/**
 * A typed condition on an edge. Using a sealed class instead of a free-form string avoids the
 * parsing ambiguity of "is `true` a literal to match or a keyword?" — each case is unambiguous.
 */
@Serializable
sealed class EdgeGuard {
    /** Fires when the source node completed successfully. */
    @Serializable
    @SerialName("on_success")
    data object OnSuccess : EdgeGuard()

    /** Fires when the source node errored. Enables try/catch-style error branch-off. */
    @Serializable
    @SerialName("on_failure")
    data object OnFailure : EdgeGuard()

    /** Fires when the source node's output parses as the expected boolean. Used after a
     *  [BranchNode] or [MergeNode] to route on true/false. */
    @Serializable
    @SerialName("bool")
    data class Bool(val expected: Boolean) : EdgeGuard()

    /** Fires when the source node's output contains a match for [pattern]. */
    @Serializable
    @SerialName("regex")
    data class Regex(val pattern: String) : EdgeGuard()
}
