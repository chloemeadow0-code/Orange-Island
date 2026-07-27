package com.orangeisland.app.workflow.linear

import com.orangeisland.app.model.LinearAction
import com.orangeisland.app.model.LinearCondition
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parses the JSON object an LLM emits for `workflow_create` / `workflow_update` into a validated
 * [LinearWorkflow], or returns a structured error a human (and the model, on retry) can act on.
 *
 * Strict by design: every field the engine relies on is checked, ranges are enforced, regexes
 * are compiled, and trigger/condition `type` strings must match a known kind. A lenient parse
 * would let a bad definition through to the approval card — or worse, to fire time.
 *
 * Tool names are validated against [knownToolNames] so the model cannot reference a tool that
 * doesn't exist on the assistant. The set may be empty in tests, in which case the tool-name
 * check is skipped (the caller is then responsible for guaranteeing the names).
 *
 * Independent implementation. The validation taxonomy and ParseResult shape are Orange Island's
 * own; the trigger/condition parameter ranges follow documented Android/automation conventions.
 */
object LinearDefinitionParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawJson: String, knownToolNames: Set<String>): ParseResult {
        val root = try {
            json.parseToJsonElement(rawJson) as? JsonObject
                ?: return ParseResult.Err("not_an_object", "definition must be a JSON object")
        } catch (e: Exception) {
            return ParseResult.Err("invalid_json", e.message ?: "could not parse JSON")
        }

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

        val triggerObj = root.obj("trigger")
            ?: return ParseResult.Err("missing_trigger", "trigger object is required")
        val trigger = parseTrigger(triggerObj)
            ?: return ParseResult.Err("unknown_trigger_type",
                "trigger.type must be one of: ${LinearTriggerKind.ALIAS.keys.sorted().joinToString()}")

        val conditions = mutableListOf<LinearCondition>()
        root.array("conditions")?.forEachIndexed { idx, condObj ->
            val cond = parseCondition(condObj)
                ?: return ParseResult.Err("unknown_condition_type",
                    "type must be one of: ${LinearConditionKind.ALIAS.keys.sorted().joinToString()}").at(idx, "condition")
            conditions += cond
        }

        val actionsArr = root.arrayRaw("actions")
            ?: return ParseResult.Err("missing_actions", "actions array is required")
        if (actionsArr.isEmpty()) return ParseResult.Err("empty_actions", "at least one action is required")
        if (actionsArr.size > MAX_ACTIONS)
            return ParseResult.Err("too_many_actions", "at most $MAX_ACTIONS actions (got ${actionsArr.size})")

        val actions = mutableListOf<LinearAction>()
        actionsArr.forEachIndexed { idx, el ->
            val actionObj = el as? JsonObject
                ?: return ParseResult.Err("bad_action_shape", "must be an object").at(idx, "action")
            when (val r = parseAction(actionObj, idx, knownToolNames)) {
                is ActionParseResult.Ok -> actions += r.action
                is ActionParseResult.Err -> return r.err
            }
        }

        val cooldownMs = root.long("cooldownMs") ?: root.long("cooldown_ms")?.times(1000) ?: 0L
        if (cooldownMs !in 0..MAX_COOLDOWN_MS)
            return ParseResult.Err("invalid_cooldown", "cooldownMs must be in 0..${MAX_COOLDOWN_MS} (got $cooldownMs)")

        val maxRunsPerDay = root.int("maxRunsPerDay") ?: root.int("max_runs_per_day")
        if (maxRunsPerDay != null && maxRunsPerDay !in MIN_DAILY_CAP..MAX_DAILY_CAP)
            return ParseResult.Err("invalid_daily_cap",
                "maxRunsPerDay must be in $MIN_DAILY_CAP..$MAX_DAILY_CAP or null (got $maxRunsPerDay)")

        val id = root.str("id") ?: java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        return ParseResult.Ok(LinearWorkflow(
            id = id, name = name.trim(), description = description, enabled = enabled,
            trigger = trigger, conditions = conditions, actions = actions,
            cooldownMs = cooldownMs, maxRunsPerDay = maxRunsPerDay,
            projectId = projectId, systemPromptId = systemPromptId, modelId = modelId,
            createdAt = now, updatedAt = now
        ))
    }

    sealed class ParseResult {
        data class Ok(val definition: LinearWorkflow) : ParseResult()
        data class Err(val code: String, val detail: String) : ParseResult() {
            fun at(idx: Int, kind: String): Err = Err(code, "$kind[$idx]: $detail")
        }
    }

    /** Internal result of parsing one action; [parse] lifts the Err into its own ParseResult. */
    private sealed class ActionParseResult {
        data class Ok(val action: LinearAction) : ActionParseResult()
        data class Err(val err: ParseResult.Err) : ActionParseResult()
    }

    // ── Trigger parsing ────────────────────────────────────────────────────

    private fun parseTrigger(obj: JsonObject): LinearTrigger? {
        val type = obj.str("type") ?: return null
        return when (LinearTriggerKind.ALIAS[type]) {
            LinearTriggerKind.MANUAL -> LinearTrigger.Manual
            LinearTriggerKind.TIME_CRON -> {
                val cron = obj.str("cron")
                val tod = obj.str("timeOfDay") ?: obj.str("time_of_day")
                val days = obj.intList("daysOfWeek") ?: obj.intList("days_of_week") ?: emptyList()
                if (cron.isNullOrBlank() && tod.isNullOrBlank()) return null
                if (tod != null && !tod.matches(TIME_OF_DAY_RE)) return null
                if (days.any { it !in 1..7 }) return null
                LinearTrigger.TimeCron(cron?.takeIf { it.isNotBlank() }, tod, days)
            }
            LinearTriggerKind.WIFI_CONNECTED -> LinearTrigger.WifiConnected(obj.str("ssid"))
            LinearTriggerKind.WIFI_DISCONNECTED -> LinearTrigger.WifiDisconnected(obj.str("ssid"))
            LinearTriggerKind.BT_CONNECTED -> LinearTrigger.BluetoothConnected(obj.str("deviceAddress") ?: obj.str("device_address"))
            LinearTriggerKind.BT_DISCONNECTED -> LinearTrigger.BluetoothDisconnected(obj.str("deviceAddress") ?: obj.str("device_address"))
            LinearTriggerKind.HEADPHONES_PLUGGED -> LinearTrigger.HeadphonesPlugged
            LinearTriggerKind.HEADPHONES_UNPLUGGED -> LinearTrigger.HeadphonesUnplugged
            LinearTriggerKind.POWER_CONNECTED -> LinearTrigger.PowerConnected
            LinearTriggerKind.POWER_DISCONNECTED -> LinearTrigger.PowerDisconnected
            LinearTriggerKind.BATTERY_BELOW -> {
                val t = obj.int("threshold") ?: obj.int("threshold_percent") ?: return null
                if (t !in 1..100) return null
                LinearTrigger.BatteryBelow(t)
            }
            LinearTriggerKind.BATTERY_ABOVE -> {
                val t = obj.int("threshold") ?: obj.int("threshold_percent") ?: return null
                if (t !in 1..100) return null
                LinearTrigger.BatteryAbove(t)
            }
            LinearTriggerKind.GEOFENCE_ENTER, LinearTriggerKind.GEOFENCE_EXIT -> {
                val lat = obj.num("lat") ?: return null
                val lng = obj.num("lng") ?: return null
                val radiusM = obj.int("radiusM") ?: obj.int("radius_m") ?: return null
                if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
                if (radiusM !in MIN_GEOFENCE_RADIUS..MAX_GEOFENCE_RADIUS) return null
                val label = obj.str("label")
                if (LinearTriggerKind.ALIAS[type] == LinearTriggerKind.GEOFENCE_ENTER)
                    LinearTrigger.GeofenceEnter(lat, lng, radiusM, label)
                else LinearTrigger.GeofenceExit(lat, lng, radiusM, label)
            }
            LinearTriggerKind.APP_LAUNCHED -> LinearTrigger.AppLaunched(obj.str("packageName") ?: obj.str("package_name") ?: return null)
            LinearTriggerKind.APP_CLOSED -> LinearTrigger.AppClosed(obj.str("packageName") ?: obj.str("package_name") ?: return null)
            LinearTriggerKind.APP_FG_DURATION -> {
                val pkg = obj.str("packageName") ?: obj.str("package_name") ?: return null
                val mins = obj.int("minutes") ?: return null
                if (mins < 1) return null
                LinearTrigger.AppForegroundDuration(pkg, mins)
            }
            LinearTriggerKind.NOTIFICATION_RECEIVED -> {
                val pkg = obj.str("packageName") ?: obj.str("package_name")
                val titleContains = obj.str("titleContains") ?: obj.str("title_contains")
                val textContains = obj.str("textContains") ?: obj.str("text_contains")
                val titleMatches = obj.str("titleMatches") ?: obj.str("title_matches")
                val textMatches = obj.str("textMatches") ?: obj.str("text_matches")
                if (listOf(pkg, titleContains, textContains, titleMatches, textMatches).all { it.isNullOrBlank() }) return null
                if (titleMatches != null && !isValidRegex(titleMatches)) return null
                if (textMatches != null && !isValidRegex(textMatches)) return null
                LinearTrigger.NotificationReceived(pkg, titleContains, textContains, titleMatches, textMatches)
            }
            LinearTriggerKind.BOOT_COMPLETED -> LinearTrigger.BootCompleted
            LinearTriggerKind.SCREEN_ON -> LinearTrigger.ScreenOn
            LinearTriggerKind.SCREEN_OFF -> LinearTrigger.ScreenOff
            null -> null
        }
    }

    // ── Condition parsing ──────────────────────────────────────────────────

    private fun parseCondition(obj: JsonObject): LinearCondition? {
        val type = obj.str("type") ?: return null
        val invert = obj.bool("invert") ?: false
        return when (LinearConditionKind.ALIAS[type]) {
            LinearConditionKind.TIME_BETWEEN -> {
                val s = obj.str("start") ?: return null
                val e = obj.str("end") ?: return null
                if (!s.matches(TIME_OF_DAY_RE) || !e.matches(TIME_OF_DAY_RE)) return null
                LinearCondition.TimeBetween(s, e, invert)
            }
            LinearConditionKind.DAY_OF_WEEK_IN -> {
                val days = obj.intList("days") ?: return null
                if (days.isEmpty() || days.any { it !in 1..7 }) return null
                LinearCondition.DayOfWeekIn(days, invert)
            }
            LinearConditionKind.WIFI_SSID_IS -> LinearCondition.WifiSsidIs(obj.str("ssid") ?: return null, invert)
            LinearConditionKind.WIFI_SSID_IN -> {
                val ssids = obj.strList("ssids") ?: return null
                if (ssids.isEmpty() || ssids.any { it.isBlank() }) return null
                LinearCondition.WifiSsidIn(ssids, invert)
            }
            LinearConditionKind.BATTERY_ABOVE -> {
                val p = obj.int("percent") ?: return null
                if (p !in 1..100) return null
                LinearCondition.BatteryAbove(p, invert)
            }
            LinearConditionKind.BATTERY_BELOW -> {
                val p = obj.int("percent") ?: return null
                if (p !in 1..100) return null
                LinearCondition.BatteryBelow(p, invert)
            }
            LinearConditionKind.IS_CHARGING -> LinearCondition.IsCharging(invert)
            LinearConditionKind.IS_NOT_CHARGING -> LinearCondition.IsNotCharging(invert)
            LinearConditionKind.FOREGROUND_APP_IS ->
                LinearCondition.ForegroundAppIs(obj.str("packageName") ?: obj.str("package_name") ?: return null, invert)
            LinearConditionKind.FOREGROUND_APP_IN -> {
                val pkgs = obj.strList("packageNames") ?: obj.strList("package_names") ?: return null
                if (pkgs.isEmpty() || pkgs.any { it.isBlank() }) return null
                LinearCondition.ForegroundAppIn(pkgs, invert)
            }
            LinearConditionKind.SCREEN_IS_ON -> LinearCondition.ScreenIsOn(invert)
            LinearConditionKind.SCREEN_IS_OFF -> LinearCondition.ScreenIsOff(invert)
            LinearConditionKind.LAST_CHAT_AGO -> {
                val mins = obj.int("minutes") ?: return null
                if (mins < 1) return null
                LinearCondition.LastChatAgo(mins, invert)
            }
            null -> null
        }
    }

    // ── Action parsing ─────────────────────────────────────────────────────

    private fun parseAction(obj: JsonObject, idx: Int, knownToolNames: Set<String>): ActionParseResult {
        val tool = obj.str("tool")?.takeIf { it.isNotBlank() }
            ?: return ActionParseResult.Err(ParseResult.Err("missing_tool", "tool name is required").at(idx, "action"))
        if (knownToolNames.isNotEmpty() && tool !in knownToolNames) {
            return ActionParseResult.Err(
                ParseResult.Err("unknown_tool", "tool '$tool' is not registered for this assistant").at(idx, "action"))
        }
        val args = (obj["args"] as? JsonObject) ?: JsonObject(emptyMap())
        val rawTimeout = obj.long("timeoutMs")
            ?: obj.long("timeout_ms")
            ?: obj.int("timeout_seconds")?.toLong()?.times(1000)
            ?: 60_000L
        val timeoutMs = rawTimeout.coerceIn(MIN_ACTION_TIMEOUT_MS, MAX_ACTION_TIMEOUT_MS)
        return ActionParseResult.Ok(LinearAction(tool, args, timeoutMs))
    }

    // ── JSON field helpers (tolerant of missing keys) ──────────────────────

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): List<JsonObject>? =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }
    private fun JsonObject.arrayRaw(key: String): List<kotlinx.serialization.json.JsonElement>? =
        (this[key] as? JsonArray)?.toList()
    private fun JsonObject.intList(key: String): List<Int>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
    private fun JsonObject.strList(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private fun isValidRegex(s: String): Boolean = try { Regex(s); true } catch (_: Exception) { false }

    // ── Constants ──────────────────────────────────────────────────────────
    const val MAX_NAME = 80
    const val MAX_DESCRIPTION = 500
    const val MAX_ACTIONS = 32
    const val MIN_ACTION_TIMEOUT_MS = 1_000L
    const val MAX_ACTION_TIMEOUT_MS = 600_000L
    const val MAX_COOLDOWN_MS = 24L * 60 * 60 * 1000
    const val MIN_DAILY_CAP = 1
    const val MAX_DAILY_CAP = 1000
    const val MIN_GEOFENCE_RADIUS = 50
    const val MAX_GEOFENCE_RADIUS = 5000
    private val TIME_OF_DAY_RE = Regex("""^\d{2}:\d{2}$""")
}

/** Maps the `type` string the LLM emits to a discriminator enum. Kept as a name→enum alias map
 *  so both the parser (lookup) and the approval renderer (reverse) share one source of truth,
 *  and adding a trigger/condition kind only needs one edit here. */
enum class LinearTriggerKind {
    MANUAL, TIME_CRON,
    WIFI_CONNECTED, WIFI_DISCONNECTED, BT_CONNECTED, BT_DISCONNECTED,
    HEADPHONES_PLUGGED, HEADPHONES_UNPLUGGED,
    POWER_CONNECTED, POWER_DISCONNECTED, BATTERY_BELOW, BATTERY_ABOVE,
    GEOFENCE_ENTER, GEOFENCE_EXIT,
    APP_LAUNCHED, APP_CLOSED, APP_FG_DURATION, NOTIFICATION_RECEIVED,
    BOOT_COMPLETED, SCREEN_ON, SCREEN_OFF;

    companion object {
        val ALIAS: Map<String, LinearTriggerKind> = mapOf(
            "manual" to MANUAL,
            "time_cron" to TIME_CRON,
            "wifi_connected" to WIFI_CONNECTED, "wifi_disconnected" to WIFI_DISCONNECTED,
            "bluetooth_connected" to BT_CONNECTED, "bluetooth_disconnected" to BT_DISCONNECTED,
            "headphones_plugged" to HEADPHONES_PLUGGED, "headphones_unplugged" to HEADPHONES_UNPLUGGED,
            "power_connected" to POWER_CONNECTED, "power_disconnected" to POWER_DISCONNECTED,
            "battery_below" to BATTERY_BELOW, "battery_above" to BATTERY_ABOVE,
            "geofence_enter" to GEOFENCE_ENTER, "geofence_exit" to GEOFENCE_EXIT,
            "app_launched" to APP_LAUNCHED, "app_closed" to APP_CLOSED,
            "app_foreground_duration" to APP_FG_DURATION, "notification_received" to NOTIFICATION_RECEIVED,
            "boot_completed" to BOOT_COMPLETED, "screen_on" to SCREEN_ON, "screen_off" to SCREEN_OFF
        )
    }
}

enum class LinearConditionKind {
    TIME_BETWEEN, DAY_OF_WEEK_IN, WIFI_SSID_IS, WIFI_SSID_IN,
    BATTERY_ABOVE, BATTERY_BELOW, IS_CHARGING, IS_NOT_CHARGING,
    FOREGROUND_APP_IS, FOREGROUND_APP_IN, SCREEN_IS_ON, SCREEN_IS_OFF, LAST_CHAT_AGO;

    companion object {
        val ALIAS: Map<String, LinearConditionKind> = mapOf(
            "time_between" to TIME_BETWEEN, "day_of_week_in" to DAY_OF_WEEK_IN,
            "wifi_ssid_is" to WIFI_SSID_IS, "wifi_ssid_in" to WIFI_SSID_IN,
            "battery_above" to BATTERY_ABOVE, "battery_below" to BATTERY_BELOW,
            "is_charging" to IS_CHARGING, "is_not_charging" to IS_NOT_CHARGING,
            "foreground_app_is" to FOREGROUND_APP_IS, "foreground_app_in" to FOREGROUND_APP_IN,
            "screen_is_on" to SCREEN_IS_ON, "screen_is_off" to SCREEN_IS_OFF,
            "last_chat_ago" to LAST_CHAT_AGO
        )
    }
}
