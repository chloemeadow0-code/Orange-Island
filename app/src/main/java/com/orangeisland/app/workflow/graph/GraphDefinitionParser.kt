package com.orangeisland.app.workflow.graph

import com.orangeisland.app.model.ActionNode
import com.orangeisland.app.model.BranchNode
import com.orangeisland.app.model.ChatMessageNode
import com.orangeisland.app.model.Comparison
import com.orangeisland.app.model.EdgeGuard
import com.orangeisland.app.model.FlowEdge
import com.orangeisland.app.model.FlowNode
import com.orangeisland.app.model.LLMNode
import com.orangeisland.app.model.MergeNode
import com.orangeisland.app.model.NodeValue
import com.orangeisland.app.model.NotifyNode
import com.orangeisland.app.model.Reducer
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TransformNode
import com.orangeisland.app.model.TransformOp
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.model.Workflow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * Parses a simplified graph-workflow blueprint (emitted by an LLM) into a validated [Workflow]
 * model with auto-generated UUIDs and grid-layout positions.
 *
 * The blueprint format is designed to be easy for a model to author: nodes are listed in order,
 * edges reference nodes by array index (not UUID), and positions are computed automatically.
 *
 * Blueprint shape:
 * ```json
 * {
 *   "name": "string (required, <=80)",
 *   "description": "string (optional, <=500)",
 *   "enabled": true,
 *   "nodes": [
 *     { "kind": "start", "label": "...", "trigger": { "type": "manual" } },
 *     { "kind": "action", "label": "...", "tool": "...", "args": { "key": {"type":"literal","value":"..."} or {"type":"ref","node_index":0} } },
 *     { "kind": "branch", "label": "...", "lhs": {...}, "cmp": "EQ", "rhs": {...} },
 *     { "kind": "merge", "label": "...", "reducer": "ALL_TRUE" },
 *     { "kind": "transform", "label": "...", "op": { "kind": "regex", "pattern": "...", ... } },
 *     { "kind": "llm", "label": "...", "prompt": {...}, "provider": "OpenAI", "model_id": "gpt-4o-mini" }
 *   ],
 *   "edges": [
 *     { "from_index": 0, "to_index": 1, "guard": { "type": "on_success" } },
 *     { "from_index": 1, "to_index": 2, "guard": { "type": "bool", "expected": true } }
 *   ]
 * }
 * ```
 *
 * Validation rules:
 *  - At least one node; the first node must be a start node (but any start node anywhere is ok).
 *  - Exactly one start node is recommended; more than one is allowed (multi-entry graph).
 *  - Edge indices must be valid (0..<nodeCount).
 *  - No duplicate edges (same from_index + to_index).
 *  - Branch guard types must match the source node's kind (bool after branch/merge, on_success/on_failure after action/llm/transform).
 *  - Tool names in action nodes are validated against [knownToolNames] when non-empty.
 *
 * Auto-layout: a simple row-major grid. Each node gets a position based on its index,
 * laid out left-to-right in rows of [NODES_PER_ROW]. This produces a readable canvas
 * that the user can manually rearrange later.
 *
 * Independent implementation.
 */
object GraphDefinitionParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawJson: String, knownToolNames: Set<String> = emptySet()): ParseResult {
        val root = try {
            json.parseToJsonElement(rawJson) as? JsonObject
                ?: return ParseResult.Err("not_an_object", "definition must be a JSON object")
        } catch (e: Exception) {
            return ParseResult.Err("invalid_json", e.message ?: "could not parse JSON")
        }

        // ── Scalar fields ────────────────────────────────────────────────────
        val name = root.str("name")?.takeIf { it.isNotBlank() }
            ?: return ParseResult.Err("missing_name", "name is required and must be non-empty")
        if (name.length > MAX_NAME) return ParseResult.Err("invalid_name", "name must be ≤ $MAX_NAME chars")

        val description = root.str("description")?.take(MAX_DESCRIPTION).orEmpty()
        val enabled = root.bool("enabled") ?: true

        // Optional bindings — let the model bind the workflow to a specific project / system
        // prompt / model explicitly. Absent → null (copyBindings in WorkflowAiToolProvider fills
        // these from the conversation context on create, and preserves existing ones on update).
        val projectId = root.str("projectId") ?: root.str("project_id")
        val systemPromptId = root.str("systemPromptId") ?: root.str("system_prompt_id")
        val modelId = root.str("modelId") ?: root.str("model_id")

        // ── Nodes ─────────────────────────────────────────────────────────────
        val nodesArr = root.arrayRaw("nodes")
            ?: return ParseResult.Err("missing_nodes", "nodes array is required")
        if (nodesArr.isEmpty()) return ParseResult.Err("empty_nodes", "at least one node is required")
        if (nodesArr.size > MAX_NODES) return ParseResult.Err("too_many_nodes", "at most $MAX_NODES nodes (got ${nodesArr.size})")

        val nodeIds = List(nodesArr.size) { "node_${UUID.randomUUID().toString().take(8)}" }
        val nodes = mutableListOf<FlowNode>()

        nodesArr.forEachIndexed { idx, el ->
            val nodeObj = el as? JsonObject
                ?: return ParseResult.Err("bad_node_shape", "each node must be an object").at(idx, "node")
            val node = parseNode(nodeObj, idx, nodeIds, knownToolNames)
                ?: return ParseResult.Err("invalid_node", "could not parse node at index $idx").at(idx, "node")
            nodes += node
        }

        // ── Edges ─────────────────────────────────────────────────────────────
        val edgesArr = root.arrayRaw("edges") ?: emptyList()
        if (edgesArr.size > MAX_EDGES)
            return ParseResult.Err("too_many_edges", "at most $MAX_EDGES edges (got ${edgesArr.size})")

        val edges = mutableListOf<FlowEdge>()
        val seenPairs = mutableSetOf<Pair<Int, Int>>()

        edgesArr.forEachIndexed { idx, el ->
            val edgeObj = el as? JsonObject
                ?: return ParseResult.Err("bad_edge_shape", "each edge must be an object").at(idx, "edge")

            val fromIdx = edgeObj.int("from_index")
                ?: return ParseResult.Err("missing_from_index", "edge[$idx]: from_index is required")
            val toIdx = edgeObj.int("to_index")
                ?: return ParseResult.Err("missing_to_index", "edge[$idx]: to_index is required")

            if (fromIdx !in nodes.indices)
                return ParseResult.Err("invalid_from_index", "edge[$idx]: from_index $fromIdx out of range (0..${nodes.lastIndex})")
            if (toIdx !in nodes.indices)
                return ParseResult.Err("invalid_to_index", "edge[$idx]: to_index $toIdx out of range (0..${nodes.lastIndex})")
            if (fromIdx == toIdx)
                return ParseResult.Err("self_loop", "edge[$idx]: self-loops are not allowed")
            if (!seenPairs.add(fromIdx to toIdx))
                return ParseResult.Err("duplicate_edge", "edge[$idx]: duplicate edge from $fromIdx to $toIdx")

            val guard = parseGuard(edgeObj.obj("guard"))
            edges += FlowEdge(
                id = "edge_${UUID.randomUUID().toString().take(8)}",
                from = nodeIds[fromIdx],
                to = nodeIds[toIdx],
                guard = guard
            )
        }

        // ── Auto-layout ───────────────────────────────────────────────────────
        val positionedNodes = nodes.mapIndexed { idx, node ->
            val pos = gridPosition(idx)
            when (node) {
                is StartNode -> node.copy(pos = pos)
                is ActionNode -> node.copy(pos = pos)
                is BranchNode -> node.copy(pos = pos)
                is MergeNode -> node.copy(pos = pos)
                is TransformNode -> node.copy(pos = pos)
                is LLMNode -> node.copy(pos = pos)
                is NotifyNode -> node.copy(pos = pos)
                is ChatMessageNode -> node.copy(pos = pos)
            }
        }

        val id = root.str("id") ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        return ParseResult.Ok(Workflow(
            id = id, name = name.trim(), description = description, enabled = enabled,
            nodes = positionedNodes, edges = edges,
            projectId = projectId, systemPromptId = systemPromptId, modelId = modelId,
            createdAt = now, updatedAt = now
        ))
    }

    // ── Node parsing ─────────────────────────────────────────────────────────

    private fun parseNode(
        obj: JsonObject,
        idx: Int,
        nodeIds: List<String>,
        knownToolNames: Set<String>
    ): FlowNode? {
        val kind = obj.str("kind") ?: return null
        val label = obj.str("label") ?: ""
        val id = nodeIds[idx]
        val pos = FlowNode.Vec2() // placeholder; overwritten by auto-layout

        return when (kind) {
            "start" -> {
                val trigger = parseTrigger(obj.obj("trigger"))
                    ?: TriggerSpec.Manual
                StartNode(id = id, label = label, pos = pos, trigger = trigger)
            }
            "action" -> {
                val tool = obj.str("tool")?.takeIf { it.isNotBlank() } ?: return null
                if (knownToolNames.isNotEmpty() && tool !in knownToolNames) return null
                val args = parseNodeValueMap(obj.obj("args"), nodeIds)
                val script = obj.str("script")
                ActionNode(id = id, label = label, pos = pos, toolName = tool, args = args, script = script)
            }
            "branch" -> {
                val lhs = parseNodeValue(obj.obj("lhs"), nodeIds) ?: NodeValue.Literal("")
                val cmp = parseComparison(obj.str("cmp")) ?: Comparison.EQ
                val rhs = parseNodeValue(obj.obj("rhs"), nodeIds) ?: NodeValue.Literal("")
                BranchNode(id = id, label = label, pos = pos, lhs = lhs, cmp = cmp, rhs = rhs)
            }
            "merge" -> {
                val reducer = when (obj.str("reducer")?.uppercase()) {
                    "ANY_TRUE" -> Reducer.ANY_TRUE
                    else -> Reducer.ALL_TRUE
                }
                MergeNode(id = id, label = label, pos = pos, reducer = reducer)
            }
            "transform" -> {
                val op = parseTransformOp(obj.obj("op"), nodeIds) ?: return null
                TransformNode(id = id, label = label, pos = pos, op = op)
            }
            "llm" -> {
                val prompt = parseNodeValue(obj.obj("prompt"), nodeIds) ?: NodeValue.Literal("")
                val provider = obj.str("provider") ?: "OpenAI"
                val modelId = obj.str("model_id") ?: obj.str("modelId") ?: "gpt-4o-mini"
                val systemPrompt = obj.str("system_prompt") ?: obj.str("systemPrompt") ?: ""
                val temperature = obj.num("temperature")?.toFloat() ?: 0.7f
                LLMNode(id = id, label = label, pos = pos, prompt = prompt,
                    provider = provider, modelId = modelId, systemPrompt = systemPrompt, temperature = temperature)
            }
            "notify" -> {
                val title = parseNodeValue(obj.obj("title"), nodeIds) ?: NodeValue.Literal("Orange Island")
                val content = parseNodeValue(obj.obj("content"), nodeIds) ?: NodeValue.Literal("")
                val pri = obj.str("priority") ?: "default"
                com.orangeisland.app.model.NotifyNode(id = id, label = label, pos = pos, title = title, content = content, priority = pri)
            }
            "chat_message" -> {
                val text = parseNodeValue(obj.obj("text"), nodeIds) ?: NodeValue.Literal("")
                val participant = obj.str("participant") ?: "MODEL"
                com.orangeisland.app.model.ChatMessageNode(id = id, label = label, pos = pos, text = text, participant = participant)
            }
            else -> null
        }
    }

    // ── Trigger parsing (graph mode uses the same TriggerSpec as manual editor) ──

    private fun parseTrigger(obj: JsonObject?): TriggerSpec? {
        if (obj == null) return null
        val type = obj.str("type") ?: return null
        return when (type) {
            "manual" -> TriggerSpec.Manual
            "schedule" -> {
                val modeStr = obj.str("mode") ?: "interval"
                val mode = when (modeStr) {
                    "interval" -> com.orangeisland.app.model.ScheduleMode.Interval
                    "oneshot" -> com.orangeisland.app.model.ScheduleMode.OneShot
                    "cronlike" -> com.orangeisland.app.model.ScheduleMode.CronLike
                    else -> com.orangeisland.app.model.ScheduleMode.Interval
                }
                val config = mutableMapOf<String, String>()
                obj.obj("config")?.entries?.forEach { (k, v) ->
                    if (v is JsonPrimitive) config[k] = v.contentOrNull ?: ""
                }
                TriggerSpec.Schedule(mode, config)
            }
            "intent" -> {
                val action = obj.str("action") ?: return null
                TriggerSpec.IntentAction(action)
            }
            "app_open" -> TriggerSpec.AppOpen
            "voice" -> TriggerSpec.Voice(obj.str("keyword"))
            "api" -> TriggerSpec.Api
            else -> null
        }
    }

    // ── NodeValue parsing ────────────────────────────────────────────────────

    private fun parseNodeValueMap(obj: JsonObject?, nodeIds: List<String>): Map<String, NodeValue> {
        if (obj == null) return emptyMap()
        return obj.entries.mapNotNull { (k, v) ->
            val nv = parseNodeValueFromElement(v, nodeIds)
            if (nv != null) k to nv else null
        }.toMap()
    }

    private fun parseNodeValue(obj: JsonObject?, nodeIds: List<String>): NodeValue? {
        if (obj == null) return null
        return parseNodeValueFromElement(obj, nodeIds)
    }

    private fun parseNodeValueFromElement(el: kotlinx.serialization.json.JsonElement, nodeIds: List<String>): NodeValue? {
        return when (el) {
            is JsonPrimitive -> NodeValue.Literal(el.contentOrNull ?: "")
            is JsonObject -> {
                val type = el.str("type") ?: return NodeValue.Literal(el.toString())
                when (type) {
                    "literal" -> NodeValue.Literal(el.str("value") ?: "")
                    "ref" -> {
                        val refIdx = el.int("node_index") ?: return null
                        if (refIdx !in nodeIds.indices) return null
                        NodeValue.Ref(nodeIds[refIdx])
                    }
                    else -> NodeValue.Literal(el.toString())
                }
            }
            else -> null
        }
    }

    // ── Comparison parsing ───────────────────────────────────────────────────

    private fun parseComparison(s: String?): Comparison? = when (s?.uppercase()) {
        "EQ" -> Comparison.EQ
        "NE" -> Comparison.NE
        "LT" -> Comparison.LT
        "LE" -> Comparison.LE
        "GT" -> Comparison.GT
        "GE" -> Comparison.GE
        "CONTAINS" -> Comparison.CONTAINS
        "NOT_CONTAINS" -> Comparison.NOT_CONTAINS
        "IN" -> Comparison.IN
        "NOT_IN" -> Comparison.NOT_IN
        else -> null
    }

    // ── TransformOp parsing ──────────────────────────────────────────────────

    private fun parseTransformOp(obj: JsonObject?, nodeIds: List<String>): TransformOp? {
        if (obj == null) return null
        val kind = obj.str("kind") ?: return null
        return when (kind) {
            "regex" -> TransformOp.Regex(
                pattern = obj.str("pattern") ?: "",
                group = obj.int("group") ?: 0,
                fallback = obj.str("fallback") ?: ""
            )
            "jsonpath" -> TransformOp.JsonPath(
                path = obj.str("path") ?: "",
                fallback = obj.str("fallback") ?: ""
            )
            "slice" -> TransformOp.Slice(
                start = obj.int("start") ?: 0,
                length = obj.int("length") ?: -1,
                fallback = obj.str("fallback") ?: ""
            )
            "join" -> TransformOp.Join(
                input = parseNodeValue(obj.obj("input"), nodeIds) ?: NodeValue.Literal(""),
                extras = (obj.arrayRaw("extras") ?: emptyList()).mapNotNull {
                    parseNodeValueFromElement(it, nodeIds)
                }
            )
            "random_int" -> TransformOp.RandomInt(
                min = obj.int("min") ?: 0,
                max = obj.int("max") ?: 100,
                fixed = obj.str("fixed")
            )
            "random_text" -> TransformOp.RandomText(
                length = obj.int("length") ?: 8,
                charset = obj.str("charset") ?: TransformOp.RandomText.ALNUM,
                fixed = obj.str("fixed")
            )
            "fixed" -> TransformOp.Fixed(obj.str("value") ?: "")
            else -> null
        }
    }

    // ── EdgeGuard parsing ────────────────────────────────────────────────────

    private fun parseGuard(obj: JsonObject?): EdgeGuard? {
        if (obj == null) return null
        val type = obj.str("type") ?: return null
        return when (type) {
            "on_success" -> EdgeGuard.OnSuccess
            "on_failure" -> EdgeGuard.OnFailure
            "bool" -> {
                val expected = obj.bool("expected") ?: true
                EdgeGuard.Bool(expected)
            }
            "regex" -> {
                val pattern = obj.str("pattern") ?: ""
                EdgeGuard.Regex(pattern)
            }
            else -> null
        }
    }

    // ── Auto-layout ──────────────────────────────────────────────────────────

    private fun gridPosition(index: Int): FlowNode.Vec2 {
        val col = index % NODES_PER_ROW
        val row = index / NODES_PER_ROW
        return FlowNode.Vec2(
            x = col * GRID_SPACING_X,
            y = row * GRID_SPACING_Y
        )
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arrayRaw(key: String): List<kotlinx.serialization.json.JsonElement>? =
        (this[key] as? JsonArray)?.toList()

    // ── Constants ────────────────────────────────────────────────────────────

    const val MAX_NAME = 80
    const val MAX_DESCRIPTION = 500
    const val MAX_NODES = 64
    const val MAX_EDGES = 128
    private const val NODES_PER_ROW = 4
    private const val GRID_SPACING_X = 220f
    private const val GRID_SPACING_Y = 160f

    sealed class ParseResult {
        data class Ok(val workflow: Workflow) : ParseResult()
        data class Err(val code: String, val detail: String) : ParseResult() {
            fun at(idx: Int, kind: String): Err = Err(code, "$kind[$idx]: $detail")
        }
    }
}
