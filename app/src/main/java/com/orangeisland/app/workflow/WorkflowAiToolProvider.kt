package com.orangeisland.app.workflow

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
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
    private val approval: (suspend (card: String) -> Boolean)? = null
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
            name = "workflow_update",
            description = "Replace an existing workflow's definition. The definition.id must match an existing workflow. Same schema as workflow_create.",
            parameters = ToolParameters(
                properties = mapOf("definition" to ToolProperty("object", "Full workflow definition with id matching an existing workflow.")),
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
        "workflow_create" -> createWorkflow(arguments)
        "workflow_update" -> updateWorkflow(arguments)
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

    private suspend fun createWorkflow(arguments: String): String {
        val def = parseAndValidate(arguments) ?: return errorJson("validation failed")
        when (def) {
            is ValidateResult.Ok -> {
                val card = WorkflowApprovalRenderer.renderCreate(def.definition)
                if (approval?.invoke(card) != true) {
                    return errorJson("authoring tools require foreground user approval")
                }
                repository.upsertLinear(def.definition)
                return okJson("created", def.definition.id, def.definition.name)
            }
            is ValidateResult.Err -> return errorJson("validation failed: ${def.code} �� ${def.detail}")
        }
    }

    private suspend fun updateWorkflow(arguments: String): String {
        val def = parseAndValidate(arguments) ?: return errorJson("validation failed: definition object missing")
        when (def) {
            is ValidateResult.Ok -> {
                if (repository.getLinear(def.definition.id) == null) {
                    return errorJson("no linear workflow with id=${def.definition.id}; use workflow_create instead")
                }
                val card = WorkflowApprovalRenderer.renderCreate(def.definition)  // reuse: same fields matter to the user
                if (approval?.invoke(card) != true) {
                    return errorJson("authoring tools require foreground user approval")
                }
                repository.upsertLinear(def.definition)
                return okJson("updated", def.definition.id, def.definition.name)
            }
            is ValidateResult.Err -> return errorJson("validation failed: ${def.code} �� ${def.detail}")
        }
    }

    private suspend fun deleteWorkflow(arguments: String): String {
        val id = parseId(arguments) ?: return errorJson("missing workflow_id")
        val card = "删除工作�?id=$id"
        if (approval?.invoke(card) != true) {
            return errorJson("authoring tools require foreground user approval")
        }
        repository.delete(id)
        return buildJsonObject { put("ok", true); put("id", id) }.toString()
    }

    private suspend fun setEnabledWorkflow(arguments: String): String {
        val id = parseId(arguments) ?: return errorJson("missing workflow_id")
        val enabled = parseEnabled(arguments) ?: return errorJson("missing enabled")
        val verb = if (enabled) "启用" else "禁用"
        if (approval?.invoke("${verb}工作�?id=$id") != true) {
            return errorJson("authoring tools require foreground user approval")
        }
        repository.setEnabled(id, enabled)
        return buildJsonObject { put("ok", true); put("id", id); put("enabled", enabled) }.toString()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

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

    /** Outcome of [parseAndValidate]. [Ok] carries the parsed definition; [Err] carries the parser's
     *  structured error so create/update can return a precise message. */
    private sealed class ValidateResult {
        data class Ok(val definition: LinearWorkflow) : ValidateResult()
        data class Err(val code: String, val detail: String) : ValidateResult()
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
        val TOOL_NAMES = setOf(
            "workflow_list", "workflow_get", "workflow_run",
            "workflow_create", "workflow_update", "workflow_delete", "workflow_set_enabled"
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
    }
}

/** Shared Json for linear-workflow (de)serialization inside the tool provider. Kept as a small
 *  internal object so the provider doesn't depend on AppContainer's instance. */
private object AppContainerJson {
    val linear = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
