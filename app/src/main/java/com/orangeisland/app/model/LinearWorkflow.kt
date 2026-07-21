package com.orangeisland.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Linear (trigger → conditions → actions) workflow model — the shape an AI authors and the
 * [com.orangeisland.app.workflow.linear.LinearEngine] executes.
 *
 * Coexists with the original node-graph [Workflow]: both persist in the `workflows` table,
 * distinguished by `WorkflowEntity.mode` ("linear" vs "graph"). Linear workflows are far easier
 * for a model to generate correctly (one trigger, an AND-list of conditions, an ordered action
 * list) than a node graph, which is why the AI tool surface only authors linear definitions.
 *
 * Independent implementation. The trigger/condition taxonomy and the linear shape are general
 * automation ideas; the concrete class names, sealed-subclass names, and field shapes here are
 * Orange Island's own.
 */

@Serializable
data class LinearWorkflow(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val trigger: LinearTrigger,
    val conditions: List<LinearCondition> = emptyList(),
    val actions: List<LinearAction>,
    /** Minimum gap between two consecutive fires, in ms. 0 = no cooldown. */
    val cooldownMs: Long = 0,
    /** Max fires per local day (SUCCESS + FAILED count toward it). null = unlimited. */
    val maxRunsPerDay: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Mirrored run-stat fields (denormalized from the entity for the list card). Not serialised
     *  back to the DB — the repository copies them in on read, and they're ignored on write. */
    val lastRunAt: Long? = null,
    val lastRunStatus: String? = null
)

/** One step in a linear workflow. Tool name + JSON args, matching the chat tool-call shape. */
@Serializable
data class LinearAction(
    val tool: String,
    val args: JsonObject = JsonObject(emptyMap()),
    /** Per-action timeout in ms. Default 60s. Clamped to 1s..600s by the parser. */
    val timeoutMs: Long = 60_000
)

// ── Triggers (19 kinds) ───────────────────────────────────────────────────

@Serializable
sealed class LinearTrigger {

    /** Only fires via the UI "Run now" button or the workflow_run tool. */
    @Serializable @SerialName("manual")
    data object Manual : LinearTrigger()

    /** Recurring schedule. Either a 5-field cron expression, or a daily time-of-day with optional
     *  days-of-week filter (ISO 1=Mon..7=Sun). */
    @Serializable @SerialName("time_cron")
    data class TimeCron(
        val cron: String? = null,
        val timeOfDay: String? = null,
        val daysOfWeek: List<Int> = emptyList()
    ) : LinearTrigger()

    @Serializable @SerialName("wifi_connected")
    data class WifiConnected(val ssid: String? = null) : LinearTrigger()   // null = any SSID

    @Serializable @SerialName("wifi_disconnected")
    data class WifiDisconnected(val ssid: String? = null) : LinearTrigger()

    @Serializable @SerialName("bluetooth_connected")
    data class BluetoothConnected(val deviceAddress: String? = null) : LinearTrigger()

    @Serializable @SerialName("bluetooth_disconnected")
    data class BluetoothDisconnected(val deviceAddress: String? = null) : LinearTrigger()

    @Serializable @SerialName("headphones_plugged")
    data object HeadphonesPlugged : LinearTrigger()

    @Serializable @SerialName("headphones_unplugged")
    data object HeadphonesUnplugged : LinearTrigger()

    @Serializable @SerialName("power_connected")
    data object PowerConnected : LinearTrigger()

    @Serializable @SerialName("power_disconnected")
    data object PowerDisconnected : LinearTrigger()

    /** Fires on the downward crossing of [threshold] percent. */
    @Serializable @SerialName("battery_below")
    data class BatteryBelow(val threshold: Int) : LinearTrigger()

    /** Fires on the upward crossing of [threshold] percent. */
    @Serializable @SerialName("battery_above")
    data class BatteryAbove(val threshold: Int) : LinearTrigger()

    @Serializable @SerialName("geofence_enter")
    data class GeofenceEnter(
        val lat: Double, val lng: Double,
        val radiusM: Int,
        val label: String? = null
    ) : LinearTrigger()

    @Serializable @SerialName("geofence_exit")
    data class GeofenceExit(
        val lat: Double, val lng: Double,
        val radiusM: Int,
        val label: String? = null
    ) : LinearTrigger()

    @Serializable @SerialName("app_launched")
    data class AppLaunched(val packageName: String) : LinearTrigger()

    @Serializable @SerialName("app_closed")
    data class AppClosed(val packageName: String) : LinearTrigger()

    /** Fires when [packageName] has stayed continuously in the foreground for ≥ [minutes]. */
    @Serializable @SerialName("app_foreground_duration")
    data class AppForegroundDuration(val packageName: String, val minutes: Int) : LinearTrigger()

    /** Fires when a notification matching at least one of the filters arrives.
     *  All provided filters are AND-combined; titleMatches/textMatches are Java regexes. */
    @Serializable @SerialName("notification_received")
    data class NotificationReceived(
        val packageName: String? = null,
        val titleContains: String? = null,
        val textContains: String? = null,
        val titleMatches: String? = null,
        val textMatches: String? = null
    ) : LinearTrigger()

    @Serializable @SerialName("boot_completed")
    data object BootCompleted : LinearTrigger()

    @Serializable @SerialName("screen_on")
    data object ScreenOn : LinearTrigger()

    @Serializable @SerialName("screen_off")
    data object ScreenOff : LinearTrigger()
}

// ── Conditions (16 kinds, each AND-combined, each with an invert flag) ─────

@Serializable
sealed class LinearCondition {
    /** Negate this single condition's raw result. Default false. */
    abstract val invert: Boolean

    @Serializable @SerialName("time_between")
    data class TimeBetween(val start: String, val end: String, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("day_of_week_in")
    data class DayOfWeekIn(val days: List<Int>, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("wifi_ssid_is")
    data class WifiSsidIs(val ssid: String, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("wifi_ssid_in")
    data class WifiSsidIn(val ssids: List<String>, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("battery_above")
    data class BatteryAbove(val percent: Int, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("battery_below")
    data class BatteryBelow(val percent: Int, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("is_charging")
    data class IsCharging(override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("is_not_charging")
    data class IsNotCharging(override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("foreground_app_is")
    data class ForegroundAppIs(val packageName: String, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("foreground_app_in")
    data class ForegroundAppIn(val packageNames: List<String>, override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("screen_is_on")
    data class ScreenIsOn(override val invert: Boolean = false) : LinearCondition()

    @Serializable @SerialName("screen_is_off")
    data class ScreenIsOff(override val invert: Boolean = false) : LinearCondition()

    /** True when the user's last chat message was ≥ [minutes] ago. */
    @Serializable @SerialName("last_chat_ago")
    data class LastChatAgo(val minutes: Int, override val invert: Boolean = false) : LinearCondition()
}

/** Outcome categories for a linear fire — richer than the graph engine's SUCCESS/FAILED because
 *  the linear mode has cooldown / daily-cap / condition gates the UI wants to surface. */
enum class LinearFireStatus {
    SUCCESS,
    FAILED,
    SKIPPED_CONDITIONS,
    SKIPPED_COOLDOWN,
    SKIPPED_DAILY_CAP,
    SKIPPED_DISABLED
}
