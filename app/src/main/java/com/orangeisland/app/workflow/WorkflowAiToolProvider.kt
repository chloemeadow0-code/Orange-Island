package com.orangeisland.app.workflow

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exposes workflows to the LLM as a small read + run tool family, so the model can answer
 * "what automations do I have?" and "run the morning-report workflow" from chat.
 *
 * Tools:
 *  - `workflow_list`  : list every workflow (id, name, description, enabled, last-run status).
 *  - `workflow_get`   : fetch one workflow's full definition (nodes + edges) by id.
 *  - `workflow_run`   : launch a workflow by id (optionally pin a start node and pass a payload).
 *
 * Deliberately **no** create / update / delete tools: letting the model author a node graph
 * unsupervised is high-risk (it could build a self-triggering loop or a destructive chain). The
 * UI (stage D) remains the authoring surface; the model is a read-and-invoke consumer.
 *
 * Security: AI-triggered runs use [WorkflowRunner.Mode.BACKGROUND], so [WorkflowGuard]'s
 * background-safe whitelist applies — the model cannot fire a workflow that calls a destructive
 * tool even if one exists. This is stricter than a manual run from the UI.
 *
 * Independent implementation.
 *
 * @param runnerProvider builds a fresh runner per run call. Passed as a function rather than an
 *   instance because the runner may carry caller-specific callbacks; the provider returns a
 *   background-mode runner with null callbacks.
 */
class WorkflowAiToolProvider(
    private val repository: WorkflowRepository,
    private val runnerProvider: () -> WorkflowRunner
) : ToolProvider {

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = listOf(
        ToolDefinition(
            function = ToolFunction(
                name = "workflow_list",
                description = "List the user's saved workflows (automations). Each entry has id, " +
                    "name, description, enabled flag, and last run status. Use this before " +
                    "workflow_run to find the right id.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "workflow_get",
                description = "Fetch one workflow's full definition (nodes and edges) by id. " +
                    "Useful to inspect what a workflow does before running it.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "workflow_id" to ToolProperty(
                            type = "string",
                            description = "The workflow id from workflow_list."
                        )
                    ),
                    required = listOf("workflow_id")
                )
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "workflow_run",
                description = "Launch a workflow by id. Runs in a restricted background mode, so " +
                    "workflows that call destructive tools (shell writes, UI automation, app " +
                    "lock) will be blocked — only read-only/safe tools are permitted when " +
                    "triggered this way.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "workflow_id" to ToolProperty(
                            type = "string",
                            description = "The workflow id from workflow_list."
                        ),
                        "start_node_id" to ToolProperty(
                            type = "string",
                            description = "Optional: pin a specific start node by id. If omitted, " +
                                "the workflow's default start node is used."
                        ),
                        "payload" to ToolProperty(
                            type = "string",
                            description = "Optional: a JSON object string passed to the start " +
                                "node as its trigger payload (e.g. {\"query\":\"weather\"}). " +
                                "Downstream nodes can reference it."
                        )
                    ),
                    required = listOf("workflow_id")
                )
            )
        )
    )

    override fun handles(name: String): Boolean =
        name == "workflow_list" || name == "workflow_get" || name == "workflow_run"

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String = when (name) {
        "workflow_list" -> listWorkflows()
        "workflow_get" -> getWorkflow(arguments)
        "workflow_run" -> runWorkflow(arguments)
        else -> "Unknown workflow tool: $name"
    }

    // ── Tool implementations ────────────────────────────────────────────────

    private suspend fun listWorkflows(): String {
        val all = repository.getAll()
        if (all.isEmpty()) return "{\"workflows\":[]}"
        val arr = buildJsonArray {
            all.forEach { wf ->
                add(buildJsonObject {
                    put("id", wf.id)
                    put("name", wf.name)
                    put("description", wf.description)
                    put("enabled", wf.enabled)
                    put("node_count", wf.nodes.size)
                })
            }
        }
        return JsonObject(mapOf("workflows" to arr)).toString()
    }

    private suspend fun getWorkflow(arguments: String): String {
        val id = parseArgs(arguments)["workflow_id"]?.toString()?.trim('"')
            ?: return error("missing workflow_id")
        val wf = repository.get(id) ?: return error("workflow not found: $id")
        // Re-serialize the graph so the model sees the actual node structure.
        return repository.encode(wf)
    }

    private suspend fun runWorkflow(arguments: String): String {
        val args = parseArgs(arguments)
        val id = args["workflow_id"]?.toString()?.trim('"') ?: return error("missing workflow_id")
        val startNodeId = args["start_node_id"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        val payload = args["payload"]?.toString() ?: "{}"
        return try {
            val runner = runnerProvider()
            val result = runner.run(
                workflowId = id,
                mode = WorkflowRunner.Mode.BACKGROUND,
                source = if (startNodeId != null)
                    TriggerSource.Targeted.Node(kind = TriggerKind.API, nodeId = startNodeId)
                    else TriggerSource.Targeted.Node(kind = TriggerKind.API),
                startNodeId = startNodeId,
                triggerPayload = payload
            )
            if (result.success) {
                buildJsonObject {
                    put("status", "success")
                    put("message", result.message)
                    put("nodes_executed", result.states.size)
                }.toString()
            } else {
                buildJsonObject {
                    put("status", "failed")
                    put("message", result.message)
                }.toString()
            }
        } catch (e: Exception) {
            error("run failed: ${e.message ?: e::class.simpleName}")
        }
    }

    /** Minimal JSON object parser for tool arguments. Returns a Map of String→JsonElement. */
    private fun parseArgs(arguments: String): Map<String, Any> {
        if (arguments.isBlank()) return emptyMap()
        return try {
            val element = Json.parseToJsonElement(arguments) as? JsonObject ?: return emptyMap()
            element.mapValues { it.value.toString() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun error(message: String): String =
        JsonObject(mapOf("error" to JsonPrimitive(message))).toString()
}
