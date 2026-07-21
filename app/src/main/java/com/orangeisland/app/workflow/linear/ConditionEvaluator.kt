package com.orangeisland.app.workflow.linear

import com.orangeisland.app.model.LinearCondition
import java.time.LocalTime
import java.time.ZoneId

/**
 * Snapshot of device state at the moment a linear workflow fires. [ConditionEvaluator] is a pure
 * function of (condition, context) → boolean, so a run's conditions can be reasoned about and
 * unit-tested without touching Android services. The provider that fills this in lives elsewhere
 * ([DeviceContextProvider]) and reads the real system services.
 *
 * Nullable fields mean "unknown" — conditions that depend on an unknown value fail-open (return
 * true) so a workflow isn't blocked just because, say, location permission was revoked mid-run;
 * the user can see in the run log that the condition was skipped on missing data.
 *
 * Independent implementation.
 */
data class DeviceContext(
    val nowMs: Long = System.currentTimeMillis(),
    val batteryLevel: Int? = null,        // 0..100
    val isCharging: Boolean = false,
    val wifiSsid: String? = null,         // without quotes
    val foregroundPackage: String? = null,
    val screenOn: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastChatMs: Long? = null          // epoch ms of the user's last chat message; null = no history
)

/**
 * Pure-function condition evaluator for linear workflows. Every condition type maps to one
 * [evaluateRaw] branch; the public [evaluate] then applies the per-condition [LinearCondition.invert]
 * flag. AND-combined across the list by [allPass].
 *
 * Independent implementation. The fail-open-on-unknown policy (a condition whose input is unknown
 * returns true rather than blocking the workflow) is Orange Island's choice; it prioritises
 * "do the action the user asked for" over "refuse because we couldn't check a guard".
 */
object ConditionEvaluator {

    /** True iff every condition in [conditions] passes (AND). An empty list is vacuously true. */
    fun allPass(conditions: List<LinearCondition>, ctx: DeviceContext, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        conditions.all { evaluate(it, ctx, zone) }

    /** Apply [condition.invert] to the raw evaluation. */
    fun evaluate(condition: LinearCondition, ctx: DeviceContext, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val raw = evaluateRaw(condition, ctx, zone)
        return if (condition.invert) !raw else raw
    }

    private fun evaluateRaw(c: LinearCondition, ctx: DeviceContext, zone: ZoneId): Boolean = when (c) {
        is LinearCondition.TimeBetween -> {
            val now = LocalTime.ofSecondOfDay(((ctx.nowMs / 1000) % 86400)).let {
                // Build from the wall-clock in the run's zone so DST/midnight wrap behave.
                val inst = java.time.Instant.ofEpochMilli(ctx.nowMs)
                LocalTime.ofInstant(inst, zone)
            }
            timeBetween(now, LocalTime.parse(c.start), LocalTime.parse(c.end))
        }
        is LinearCondition.DayOfWeekIn -> {
            val today = java.time.Instant.ofEpochMilli(ctx.nowMs).atZone(zone).dayOfWeek.value
            c.days.isEmpty() || today in c.days   // empty = no constraint
        }
        is LinearCondition.WifiSsidIs ->
            ctx.wifiSsid != null && ctx.wifiSsid.equals(c.ssid, ignoreCase = false)
        is LinearCondition.WifiSsidIn ->
            ctx.wifiSsid != null && c.ssids.any { it == ctx.wifiSsid }
        is LinearCondition.BatteryAbove ->
            ctx.batteryLevel != null && ctx.batteryLevel > c.percent
        is LinearCondition.BatteryBelow ->
            ctx.batteryLevel != null && ctx.batteryLevel < c.percent
        is LinearCondition.IsCharging -> ctx.isCharging
        is LinearCondition.IsNotCharging -> !ctx.isCharging
        is LinearCondition.ForegroundAppIs ->
            ctx.foregroundPackage != null && ctx.foregroundPackage == c.packageName
        is LinearCondition.ForegroundAppIn ->
            ctx.foregroundPackage != null && c.packageNames.any { it == ctx.foregroundPackage }
        is LinearCondition.ScreenIsOn -> ctx.screenOn
        is LinearCondition.ScreenIsOff -> !ctx.screenOn
        is LinearCondition.LastChatAgo ->
            // No chat history → fail-open (return true). Otherwise true when the gap ≥ the threshold.
            ctx.lastChatMs == null || (ctx.nowMs - ctx.lastChatMs) >= c.minutes * 60_000L
    }

    /** True if [now] is within [start]..[end], wrapping past midnight (e.g. 22:00..06:00). */
    private fun timeBetween(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        // Normalise: end <= start means the range wraps midnight (start..23:59 + 00:00..end).
        return if (!end.isAfter(start)) {
            now >= start || now <= end
        } else {
            now in start..end
        }
    }
}
