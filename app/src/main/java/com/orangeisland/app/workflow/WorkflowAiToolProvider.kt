package com.orangeisland.app.workflow

import android.content.Context
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.workflow.graph.GraphDefinitionParser
import com.orangeisland.app.workflow.linear.LinearDefinitionParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exposes workflows to the LLM: read (list/get), run, and the AI-authoring tools
 * (create/update/delete/set_enabled).
 *
 * The authoring tools take a linear-workflow `definition` object (trigger + conditions + actions),
 * run it through [LinearDefinitionParser] for strict validation, then �?before persisting �?go
 * through the [approval] gate. The gate receives a human-readable rendering of the change
 * (see [WorkflowApprovalRenderer]) and the parsed definition; the UI pops a card, the user
 * approves or denies, and the workflow is only saved on approval. Without an approval gate
 * (background context, unit tests) the authoring tools refuse �?AI authoring is a foreground,
 * user-witnessed action by design.
 *
 * `workflow_run` keeps the original background-mode behaviour: it fires an existing workflow but
 * the run itself still goes through [WorkflowGuard]'s background-safe whitelist, so the model
 * cannot trigger a destructive action even on an existing workflow.
 *
 * Independent implementation.
 *
 * @param runnerProvider builds a fresh runner per run call.
 * @param knownToolNames the assistant's currently-registered tool names, used to validate that
 *   a created workflow's action tools actually exist. Empty in tests �?check skipped.
 * @param approval foreground approval gate for authoring mutations. Returns true to persist.
 *   Null �?authoring tools return an error (they require a foreground, user-witnessed context).
 */
class WorkflowAiToolProvider(
    private val repository: WorkflowRepository,
    private val runnerProvider: () -> WorkflowRunner,
    private val knownToolNames: () -> Set<String> = { emptySet() },
    private val approval: (suspend (card: String) -> Boolean)? = null,
    private val settingsRepository: SettingsRepository? = null,
    /** Application context used to enqueue graph-mode schedule triggers into WorkManager. Linear
     *  workflows need no context here �� [com.orangeisland.app.workflow.trigger.TimeSignalSource]
     *  reconciles them via a Flow on the enabled set. Graph workflows have no such subscription,
     *  so a persisted Schedule trigger must be explicitly scheduled or it never fires (the original
     *  bug: AI-authored graph schedules were written to the DB but never enqueued). Null in tests. */
    private val appContext: Context? = null
) : ToolProvider {

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunction(
            name = "workflow_list",
            description = "List the user's saved workflows. Each entry has id, name, description, enabled. Use before workflow_run/workflow_update to find the right id.",
            parameters = ToolParameters(properties = emptyMap(), required = emptyList())
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_get",
            description = "Fetch one workflow's full definition by id.",
            parameters = ToolParameters(
                properties = mapOf("workflow_id" to ToolProperty("string", "Workflow id from workflow_list.")),
                required = listOf("workflow_id")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_run",
            description = "Launch a workflow by id immediately. Conditions still apply. Destructive tools are blocked in this headless context.",
            parameters = ToolParameters(
                properties = mapOf("workflow_id" to ToolProperty("string", "Workflow id to fire.")),
                required = listOf("workflow_id")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_create",
            description = CREATE_DESCRIPTION,
            parameters = ToolParameters(
                properties = mapOf("definition" to ToolProperty("object", "Linear workflow definition: name, trigger, optional conditions[], actions[], optional cooldownMs/maxRunsPerDay.")),
                required = listOf("definition")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_create_graph",
            description = CREATE_GRAPH_DESCRIPTION,
            parameters = ToolParameters(
                properties = mapOf("definition" to ToolProperty("object", "Graph workflow blueprint: name, nodes[], edges[]. See description for full schema.")),
                required = listOf("definition")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_update",
            description = "Replace an existing linear workflow's definition. The definition.id must match an existing linear workflow. Same schema as workflow_create.",
            parameters = ToolParameters(
                properties = mapOf("definition" to ToolProperty("object", "Full workflow definition with id matching an existing linear workflow.")),
                required = listOf("definition")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_update_graph",
            description = "Replace an existing graph workflow's definition. The definition.id must match an existing graph workflow. Same schema as workflow_create_graph.",
            parameters = ToolParameters(
                properties = mapOf("definition" to ToolProperty("object", "Full graph workflow blueprint with id matching an existing graph workflow.")),
                required = listOf("definition")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_delete",
            description = "Delete a workflow and its run history.",
            parameters = ToolParameters(
                properties = mapOf("workflow_id" to ToolProperty("string", "Workflow id to delete.")),
                required = listOf("workflow_id")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "workflow_set_enabled",
            description = "Enable or disable a workflow. Disabled workflows keep their definition but their triggers stop firing.",
            parameters = ToolParameters(
                properties = mapOf(
                    "workflow_id" to ToolProperty("string", "Workflow id."),
                    "enabled" to ToolProperty("boolean", "True to enable, false to disable.")
                ),
                required = listOf("workflow_id", "enabled")
            )
        ))
    )

    override fun handles(name: String): Boolean = name in TOOL_NAMES

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String = when (name) {
        "workflow_list" -> listWorkflows()
        "workflow_get" -> getWorkflow(arguments)
        "workflow_run" -> runWorkflow(arguments)
        "workflow_create" -> createWorkflow(arguments, ctx)
        "workflow_create_graph" -> createGraphWorkflow(arguments, ctx)
        "workflow_update" -> updateWorkflow(arguments, ctx)
        "workflow_update_graph" -> updateGraphWorkflow(arguments, ctx)
        "workflow_delete" -> deleteWorkflow(arguments)
        "workflow_set_enabled" -> setEnabledWorkflow(arguments)
        else -> "Unknown workflow tool: $name"
    }

    // ── Read tools ─────────────────────────────────────────────────────────

    private suspend fun listWorkflows(): String {
        // List BOTH linear (AI-authored) and graph (manual) workflows in one call so the model can
        // pick the right one to run/update. We read a mode-tagged summary rather than decoding each
        // row �� getAll() filters out linear rows (it backs the graph UI), and decoding to two
        // different types just to list ids is wasteful.
        val all = repository.listAllSummary()
        if (all.isEmpty()) return "{\"workflows\":[]}"
        val arr = buildJsonArray {
            all.forEach { wf ->
                add(buildJsonObject {
                    put("id", wf.id)
                    put("name", wf.name)
                    put("description", wf.description)
                    put("enabled", wf.enabled)
                    put("mode", wf.mode)
                })
            }
        }
        return JsonObject(mapOf("workflows" to arr)).toString()
    }

    private suspend fun getWorkflow(arguments: String): String {
        val id = parseId(arguments) ?: return errorJson("missing workflow_id")
        // Try linear first (AI-authored workflows are linear); fall back to graph.
        val linear = repository.getLinear(id)
        if (linear != null) {
            return buildJsonObject {
                put("ok", true)
                put("mode", "linear")
                put("definition", Json.parseToJsonElement(encodeLinear(linear)))
            }.toString()
        }
        val graph = repository.get(id) ?: return errorJson("workflow not found: $id")
        return buildJsonObject {
            put("ok", true)
            put("mode", "graph")
            put("definition", Json.parseToJsonElement(repository.encode(graph)))
        }.toString()
    }

    private suspend fun runWorkflow(arguments: String): String {
        val id = parseId(arguments) ?: return errorJson("missing workflow_id")
        return try {
            val runner = runnerProvider()
            val result = runner.run(
                workflowId = id,
                mode = WorkflowRunner.Mode.BACKGROUND,
                source = TriggerSource.Targeted.Node(kind = TriggerKind.API)
            )
            buildJsonObject {
                put("status", if (result.success) "success" else "failed")
                put("message", result.message)
            }.toString()
        } catch (e: Exception) {
            errorJson("run failed: ${e.message ?: e::class.simpleName}")
        }
    }

    // ── Authoring tools (all gated by [approval]) ──────────────────────────

    private suspend fun createWorkflow(arguments: String, ctx: GenerationContext): String {
        val def = parseAndValidate(arguments) ?: return errorJson("validation failed")
        when (def) {
            is ValidateResult.Ok -> {
                val bound = def.definition.copyBindings(ctx)
                val card = WorkflowApprovalRenderer.renderCreate(bound)
                if (approval?.invoke(card) != true) {
                    return errorJson("authoring tools require foreground user approval")
                }
                repository.upsertLinear(bound)
                return okJson("created", bound.id, bound.name)
            }
            is ValidateResult.Err -> return errorJson("validation failed: ${def.code} �� ${def.detail}")
        }
    }

    private suspend fun updateWorkflow(arguments: String, ctx: GenerationContext): String {
        val def = parseAndValidate(arguments) ?: return errorJson("validation failed: definition object missing")
        when (def) {
            is ValidateResult.Ok -> {
                val existing = repository.getLinear(def.definition.id)
                    ?: return errorJson("no linear workflow with id=${def.definition.id}; use workflow_create instead")
                // Preserve bindings the model didn't re-specify: a partial update (e.g. just the
                // trigger) must not blank out an existing project/prompt/model binding. Precedence is
                // definition > existing row > conversation context.
                val preserved = def.definition.preserveBindings(existing)
                val bound = preserved.copyBindings(ctx)
                val card = WorkflowApprovalRenderer.renderCreate(bound)  // reuse: same fields matter to the user
                if (approval?.invoke(card) != true) {
                    return errorJson("authoring tools require foreground user approval")
                }
                repository.upsertLinear(bound)
                return okJson("updated", bound.id, bound.name)
            }
            is ValidateResult.Err -> return errorJson("validation failed: ${def.code} �� ${def.detail}")
        }
    }

    private suspend fun createGraphWorkflow(arguments: String, ctx: GenerationContext): String {
        val def = parseAndValidateGraph(arguments) ?: return errorJson("validation failed")
        when (def) {
            is GraphValidateResult.Ok -> {
                val bound = def.workflow.copyBindings(ctx)
                val card = WorkflowApprovalRenderer.renderGraphCreate(bound)
                if (approval?.invoke(card) != true) {
                    return errorJson("authoring tools require foreground user approval")
                }
                repository.upsert(bound)
                scheduleGraph(bound)
                return okJson("created", bound.id, bound.name)
            }
            is GraphValidateResult.Err -> return errorJson("validation failed: ${def.code} �� ${def.detail}")
        }
    }

    private suspend fun updateGraphWorkflow(arguments: String, ctx: GenerationContext): String {
        val def = parseAndValidateGraph(arguments) ?: return errorJson("validation failed: definition object missing")
        when (def) {
            is GraphValidateResult.Ok -> {
                val existing = repository.get(def.workflow.id)
                    ?: return errorJson("no graph workflow with id=${def.workflow.id}; use workflow_create_graph instead")
                // Preserve bindings the model didn't re-specify (see updateWorkflow for rationale).
                val preserved = def.workflow.preserveBindings(existing)
                val bound = preserved.copyBindings(ctx)
                val card = WorkflowApprovalRenderer.renderGraphCreate(bound)
                if (approval?.invoke(card) != true) {
                    return errorJson("authoring tools require foreground user approval")
                }
                repository.upsert(bound)
                scheduleGraph(bound)
                return okJson("updated", bound.id, bound.name)
            }
            is GraphValidateResult.Err -> return errorJson("validation failed: ${def.code} �� ${def.detail}")
        }
    }

    private suspend fun deleteWorkflow(arguments: String): String {
        val id = parseId(arguments) ?: return errorJson("missing workflow_id")
        val card = "删除工作�?id=$id"
        if (approval?.invoke(card) != true) {
            return errorJson("authoring tools require foreground user approval")
        }
        // Cancel any pending WorkManager request before the row disappears (graph mode only ��
        // linear is reconciled by TimeSignalSource on the next emission).
        if (appContext != null && repository.modeOf(id) == "graph") {
            runCatching { WorkflowWorker.cancel(appContext!!, id) }
        }
        repository.delete(id)
        return buildJsonObject { put("ok", true); put("id", id) }.toString()
    }

    private suspend fun setEnabledWorkflow(arguments: String): String {
        val id = parseId(arguments) ?: return errorJson("missing workflow_id")
        val enabled = parseEnabled(arguments) ?: return errorJson("missing enabled")
        val verb = if (enabled) "吔�" else "禁用"
        if (approval?.invoke("${verb}工作�?id=$id") != true) {
            return errorJson("authoring tools require foreground user approval")
        }
        repository.setEnabled(id, enabled)
        // Reconcile the graph schedule: schedule() is a no-op for disabled workflows, so it doubles
        // as both the enable and disable path for graph mode. Linear is Flow-driven; skip it.
        if (appContext != null && repository.modeOf(id) == "graph") {
            if (enabled) {
                repository.get(id)?.let { runCatching { WorkflowWorker.schedule(appContext!!, it) } }
            } else {
                runCatching { WorkflowWorker.cancel(appContext!!, id) }
            }
        }
        return buildJsonObject { put("ok", true); put("id", id); put("enabled", enabled) }.toString()
    }

    /** Enqueue (or replace) the WorkManager request for a graph-mode workflow's Schedule trigger.
     *  No-op if the workflow has no schedule trigger or is disabled �� [WorkflowWorker.schedule]
     *  handles both guards internally. Best-effort: a scheduling failure is logged but does not
     *  fail the authoring call, since the definition is already persisted and will be picked up by
     *  [WorkflowWorker.rescheduleAll] on the next app start. */
    private fun scheduleGraph(workflow: Workflow) {
        val ctx = appContext ?: return
        runCatching { WorkflowWorker.schedule(ctx, workflow) }
            .onFailure { DebugLog.w(TAG, "graph schedule failed for ${workflow.id}", it) }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Copies a [LinearWorkflow] with project / system-prompt / model bindings resolved.
     *  Precedence: an explicit value in the definition wins; only when it's null do we fall back to
     *  the conversation context. This lets the model bind a workflow to any project explicitly, while
     *  still inheriting the current conversation's project when it doesn't specify one. On update the
     *  parser preserves the prior binding by reading it from the stored row before re-applying ctx. */
    private fun LinearWorkflow.copyBindings(ctx: GenerationContext): LinearWorkflow {
        val projectId = projectId ?: ctx.projectId
        val systemPromptId = systemPromptId ?: ctx.systemPromptId
        val modelId = modelId ?: ctx.modelId
        return copy(projectId = projectId, systemPromptId = systemPromptId, modelId = modelId)
    }

    /** Same binding logic for graph [Workflow]s. */
    private fun Workflow.copyBindings(ctx: GenerationContext): Workflow {
        val projectId = projectId ?: ctx.projectId
        val systemPromptId = systemPromptId ?: ctx.systemPromptId
        val modelId = modelId ?: ctx.modelId
        return copy(projectId = projectId, systemPromptId = systemPromptId, modelId = modelId)
    }

    /** Fills in any null binding on a freshly-parsed [LinearWorkflow] with the values from the
     *  existing stored row, so a partial update (e.g. only the trigger) keeps the prior project /
     *  prompt / model instead of blanking them. Call before [copyBindings]. */
    private fun LinearWorkflow.preserveBindings(existing: LinearWorkflow): LinearWorkflow =
        if (projectId != null && systemPromptId != null && modelId != null) this
        else copy(
            projectId = projectId ?: existing.projectId,
            systemPromptId = systemPromptId ?: existing.systemPromptId,
            modelId = modelId ?: existing.modelId
        )

    /** Graph-workflow counterpart to [preserveBindings]. */
    private fun Workflow.preserveBindings(existing: Workflow): Workflow =
        if (projectId != null && systemPromptId != null && modelId != null) this
        else copy(
            projectId = projectId ?: existing.projectId,
            systemPromptId = systemPromptId ?: existing.systemPromptId,
            modelId = modelId ?: existing.modelId
        )

    /** Parse + validate a `definition` argument into a [LinearWorkflow]. Returns the parser's
     *  structured error (code + human-readable detail) on failure so the caller can surface the
     *  exact reason to the model �� swallowing it into a bare null made every malformed definition
     *  look identical ("validation failed"), leaving the model no way to self-correct. */
    private fun parseAndValidate(arguments: String): ValidateResult {
        val defJson = extractDefinition(arguments)
            ?: return ValidateResult.Err("bad_arguments",
                "tool args must be an object with a 'definition' field containing the workflow object; " +
                "got: ${arguments.take(200)}")
        return when (val r = LinearDefinitionParser.parse(defJson, knownToolNames())) {
            is LinearDefinitionParser.ParseResult.Ok -> ValidateResult.Ok(r.definition)
            is LinearDefinitionParser.ParseResult.Err -> ValidateResult.Err(r.code, r.detail)
        }
    }

    /** Parse + validate a `definition` argument into a graph [Workflow]. */
    private fun parseAndValidateGraph(arguments: String): GraphValidateResult {
        val defJson = extractDefinition(arguments)
            ?: return GraphValidateResult.Err("bad_arguments",
                "tool args must be an object with a 'definition' field containing the workflow object; " +
                "got: ${arguments.take(200)}")
        return when (val r = GraphDefinitionParser.parse(defJson, knownToolNames())) {
            is GraphDefinitionParser.ParseResult.Ok -> GraphValidateResult.Ok(r.workflow)
            is GraphDefinitionParser.ParseResult.Err -> GraphValidateResult.Err(r.code, r.detail)
        }
    }

    /** Outcome of [parseAndValidate]. [Ok] carries the parsed definition; [Err] carries the parser's
     *  structured error so create/update can return a precise message. */
    private sealed class ValidateResult {
        data class Ok(val definition: LinearWorkflow) : ValidateResult()
        data class Err(val code: String, val detail: String) : ValidateResult()
    }

    private sealed class GraphValidateResult {
        data class Ok(val workflow: Workflow) : GraphValidateResult()
        data class Err(val code: String, val detail: String) : GraphValidateResult()
    }

    /** Pull the `definition` sub-object out of the tool args and return it as a JSON string. */
    private fun extractDefinition(arguments: String): String? = try {
        val obj = Json.parseToJsonElement(arguments) as? JsonObject ?: return null
        (obj["definition"] ?: return null).toString()
    } catch (_: Exception) { null }

    private fun parseId(arguments: String): String? = try {
        val obj = Json.parseToJsonElement(arguments) as? JsonObject ?: return null
        (obj["workflow_id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: (obj["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    private fun parseEnabled(arguments: String): Boolean? = try {
        val obj = Json.parseToJsonElement(arguments) as? JsonObject ?: return null
        (obj["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
    } catch (_: Exception) { null }

    private fun encodeLinear(def: LinearWorkflow): String =
        AppContainerJson.linear.encodeToString(LinearWorkflow.serializer(), def)

    private fun okJson(verb: String, id: String, name: String) = buildJsonObject {
        put("ok", true); put("id", id); put("name", name); put("action", verb)
    }.toString()

    private fun errorJson(message: String) = JsonObject(mapOf("error" to JsonPrimitive(message))).toString()

    companion object {
        private const val TAG = "WorkflowAiToolProvider"

        val TOOL_NAMES = setOf(
            "workflow_list", "workflow_get", "workflow_run",
            "workflow_create", "workflow_create_graph",
            "workflow_update", "workflow_update_graph",
            "workflow_delete", "workflow_set_enabled"
        )

        /** The workflow_create parameter spec, written as a teaching prompt: lists every supported
         *  trigger and condition type with their parameters so the model can construct a valid
         *  definition on the first try. Keep this in sync with [LinearTriggerKind] /
         *  [LinearConditionKind] aliases and [LinearDefinitionParser] validation ranges. */
        const val CREATE_DESCRIPTION = """
Create a new automation workflow. The user describes a trigger ("when X") and one or more actions ("do Y"); you build the definition object. The user approves via a card before it is saved, so make the name + description self-explanatory.

definition shape:
{
  "name": string (required, <=80 chars),
  "description": string (optional, <=500),
  "enabled": boolean (optional, default true),
  "projectId": string (optional, project id to bind this workflow to; omit/leave null to inherit the current conversation's project),
  "systemPromptId": string (optional, bind to a system prompt id),
  "modelId": string (optional, "provider:modelId" e.g. "OpenAI:gpt-4o-mini"),
  "trigger": { "type": <one of the below>, ...params },
  "conditions": [ { "type": <one of the below>, ...params, "invert"?: boolean } ],  // optional, AND-combined
  "actions": [ { "tool": <existing tool name>, "args": {object}, "timeout_seconds"?: 1..600 } ],  // required, 1..32, in order
  "cooldownMs": long (optional, 0..86400000, 0=no cooldown),
  "maxRunsPerDay": int (optional, 1..1000, null=unlimited)
}

SUPPORTED TRIGGER TYPES (trigger.type) with params:
  manual - no params. Fires only via workflow_run or the UI Run button.
  time_cron - recurring schedule. EITHER cron:"<5-field cron>" OR time_of_day:"HH:mm" with optional days_of_week:[1..7] (ISO, 1=Mon). ALWAYS recurring.
  wifi_connected / wifi_disconnected - params: ssid (optional, null=any).
  bluetooth_connected / bluetooth_disconnected - params: device_address (optional).
  headphones_plugged / headphones_unplugged - no params.
  power_connected / power_disconnected - no params.
  battery_below / battery_above - params: threshold (1..100), fires on crossing.
  geofence_enter / geofence_exit - params: lat (-90..90), lng (-180..180), radius_m (50..5000), label (optional).
  app_launched / app_closed - params: package_name.
  app_foreground_duration - params: package_name, minutes (>=1). Fires when app stays foreground >= minutes.
  notification_received - params: at least ONE of package_name, title_contains, text_contains, title_matches (regex), text_matches (regex).
  boot_completed - no params.
  screen_on / screen_off - no params.

SUPPORTED CONDITION TYPES (conditions[].type), each AND-combined, each accepts optional invert:true:
  time_between - start:"HH:mm", end:"HH:mm" (wraps midnight).
  day_of_week_in - days:[1..7] (empty=vacuous true).
  wifi_ssid_is / wifi_ssid_in - ssid / ssids.
  battery_above / battery_below - percent (1..100).
  is_charging / is_not_charging - no params.
  foreground_app_is / foreground_app_in - package_name / package_names.
  screen_is_on / screen_is_off - no params.
  last_chat_ago - minutes (>=1). True when the user hasn't chatted for >= minutes.

ACTIONS: each {tool, args, timeout_seconds?}. tool must be an EXISTING tool registered for this assistant. workflow_run is NOT allowed as an action (no chaining). Args is a JSON object matching what you'd emit in a chat tool call.

If the user wants "fire once at a time and never again", set a time_cron trigger and tell them to delete it after it fires, or just use workflow_run at that moment.

Prefer specific triggers and a clear name. Example: user says "silence my phone when I get home" -> trigger wifi_connected ssid:"Home", action set_ringer_mode args:{mode:"silent"}, name "Home silent".
        """

        /** The workflow_create_graph parameter spec. Graph workflows are node-and-edge directed graphs
         *  (not linear trigger��conditions��actions). Use this when the user asks for branching logic,
         *  parallel paths, or when a linear model cannot express the flow naturally.
         *
         *  Nodes are listed in order; edges reference nodes by array index (0-based). The system auto-
         *  assigns UUIDs and positions �� you do NOT need to provide ids or coordinates. */
        const val CREATE_GRAPH_DESCRIPTION = """
Create a new graph workflow (node-and-edge directed graph). Use this when the user needs branching logic, conditional paths, or parallel execution that a linear workflow cannot express. The user approves via a card before it is saved.

definition shape:
{
  "name": string (required, <=80 chars),
  "description": string (optional, <=500),
  "enabled": boolean (optional, default true),
  "projectId": string (optional, project id to bind this workflow to; omit/leave null to inherit the current conversation's project),
  "systemPromptId": string (optional, bind to a system prompt id),
  "modelId": string (optional, "provider:modelId" e.g. "OpenAI:gpt-4o-mini"),
  "nodes": [  // 1..64 nodes
    { "kind": "start", "label": "...", "trigger": { "type": "manual" } },
    { "kind": "action", "label": "...", "tool": "<existing tool name>", "args": { "key": {"type":"literal","value":"..."} or {"type":"ref","node_index":0} } },
    { "kind": "branch", "label": "...", "lhs": {"type":"literal","value":"..."}, "cmp": "EQ", "rhs": {"type":"literal","value":"..."} },
    { "kind": "merge", "label": "...", "reducer": "ALL_TRUE" },
    { "kind": "transform", "label": "...", "op": { "kind": "regex", "pattern": "...", "group": 0, "fallback": "" } },
    { "kind": "llm", "label": "...", "prompt": {"type":"literal","value":"..."}, "provider": "OpenAI", "model_id": "gpt-4o-mini" },
    { "kind": "notify", "label": "...", "title": {"type":"literal","value":"..."}, "content": {"type":"literal","value":"..."}, "priority": "default" },
    { "kind": "chat_message", "label": "...", "text": {"type":"literal","value":"..."}, "participant": "MODEL" }
  ],
  "edges": [  // 0..128 edges
    { "from_index": 0, "to_index": 1, "guard": { "type": "on_success" } },
    { "from_index": 1, "to_index": 2, "guard": { "type": "bool", "expected": true } }
  ]
}

NODE KINDS:
  start - Entry point. Must have a "trigger" object. Supported trigger types: manual, schedule {mode, config}, intent {action}, app_open, voice {keyword?}, api.
  action - Calls a tool. Fields: tool (string, required), args (object of NodeValues), script (optional).
  branch - Compares two values, emits boolean. Fields: lhs (NodeValue), cmp (EQ/NE/LT/LE/GT/GE/CONTAINS/NOT_CONTAINS/IN/NOT_IN), rhs (NodeValue).
  merge - Reduces multiple incoming booleans. Field: reducer (ALL_TRUE or ANY_TRUE).
  transform - Shapes a string. Field: op (one of the below).
  llm - Runs an LLM inference. Fields: prompt (NodeValue), provider (default OpenAI), model_id (default gpt-4o-mini), system_prompt, temperature (default 0.7).
  notify - Posts a system notification. Fields: title (NodeValue), content (NodeValue), priority (string, default "default").
  chat_message - Inserts a message into the bound project's chat. Fields: text (NodeValue), participant ("MODEL" or "USER", default "MODEL").

NODEVALUE format (used in action args, branch lhs/rhs, transform join, llm prompt):
  {"type": "literal", "value": "..."}  - a plain string
  {"type": "ref", "node_index": 0}       - reference to another node's output (by nodes-array index)

TRANSFORM OP kinds:
  regex {pattern, group?, fallback?}
  jsonpath {path, fallback?}
  slice {start?, length?, fallback?}
  join {input: NodeValue, extras: [NodeValue]}
  random_int {min?, max?, fixed?}
  random_text {length?, charset?, fixed?}
  fixed {value}

EDGE GUARD types (optional; omit for unconditional edge):
  on_success - fires when source node succeeds
  on_failure - fires when source node fails
  bool {expected: true/false} - fires when source output parses to this boolean
  regex {pattern} - fires when source output contains a match

RULES:
  - At least one node. First node should usually be "start".
  - Edge indices must be valid (0 <= from_index/to_index < nodes.length).
  - No self-loops (from_index == to_index).
  - No duplicate edges (same from+to pair).
  - Tool names in action nodes must be EXISTING tools registered for this assistant.
  - workflow_run is NOT allowed as an action tool (no chaining).

Example: "Every morning at 8:00, if it's a weekday, check my calendar; if the first event is a meeting, send a reminder; otherwise just say good morning."
  nodes: [
    {kind:"start", label:"����", trigger:{type:"schedule", mode:"cronlike", config:{expr:"0 8 * * 1-5"}}},
    {kind:"branch", label:"������?", lhs:{type:"literal",value:"true"}, cmp:"EQ", rhs:{type:"literal",value:"true"}},
    {kind:"action", label:"������", tool:"calendar_query", args:{count:{type:"literal",value:"1"}}},
    {kind:"branch", label:"�л���?", lhs:{type:"ref",node_index:2}, cmp:"CONTAINS", rhs:{type:"literal",value:"����"}},
    {kind:"action", label:"����", tool:"send_notification", args:{message:{type:"literal",value:"���Ϻã������л���"}}},
    {kind:"action", label:"�ʺ�", tool:"send_notification", args:{message:{type:"literal",value:"���Ϻã�����û�л���"}}}
  ],
  edges: [
    {from_index:0, to_index:1, guard:{type:"on_success"}},
    {from_index:1, to_index:2, guard:{type:"bool", expected:true}},
    {from_index:2, to_index:3, guard:{type:"on_success"}},
    {from_index:3, to_index:4, guard:{type:"bool", expected:true}},
    {from_index:3, to_index:5, guard:{type:"bool", expected:false}}
  ]

For device-event triggers (WiFi, battery, notifications, etc.), use workflow_create (linear mode) instead �� graph workflows currently support manual, schedule, intent, app_open, voice, and api triggers.
        """
    }
}

/** Shared Json for linear-workflow (de)serialization inside the tool provider. Kept as a small
 *  internal object so the provider doesn't depend on AppContainer's instance. */
private object AppContainerJson {
    val linear = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
