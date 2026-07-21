package com.orangeisland.app.workflow.linear

import com.orangeisland.app.model.LinearCondition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Unit tests for [ConditionEvaluator]. Pure JVM — [DeviceContext] is a plain data class with no
 * Android dependency, so every condition branch can be exercised exhaustively here.
 *
 * Focus areas: the invert flag, midnight-wrapping time ranges, fail-open-on-unknown (a condition
 * whose input is null returns true so a workflow isn't blocked by a missing permission), and the
 * AND-combined [allPass].
 */
class ConditionEvaluatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun ctx(
        nowMs: Long = atTime(10, 0),
        battery: Int? = 50,
        charging: Boolean = false,
        wifi: String? = null,
        foreground: String? = null,
        screenOn: Boolean = true,
        lastChatMs: Long? = null
    ) = DeviceContext(nowMs, battery, charging, wifi, foreground, screenOn, null, null, lastChatMs)

    // ── invert ─────────────────────────────────────────────────────────────

    @Test
    fun `invert negates the raw result`() {
        // charging = false; IsCharging → false; IsCharging(invert=true) → true.
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.IsCharging(invert = false), ctx(charging = false), zone))
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.IsCharging(invert = true), ctx(charging = false), zone))
    }

    // ── time_between (with midnight wrap) ──────────────────────────────────

    @Test
    fun `time_between normal range`() {
        val c = LinearCondition.TimeBetween("09:00", "17:00")
        assertTrue(ConditionEvaluator.evaluate(c, ctx(nowMs = atTime(12, 0)), zone))
        assertFalse(ConditionEvaluator.evaluate(c, ctx(nowMs = atTime(8, 0)), zone))
        assertFalse(ConditionEvaluator.evaluate(c, ctx(nowMs = atTime(18, 0)), zone))
    }

    @Test
    fun `time_between wraps past midnight`() {
        // 22:00..06:00 should match late evening AND early morning.
        val c = LinearCondition.TimeBetween("22:00", "06:00")
        assertTrue(ConditionEvaluator.evaluate(c, ctx(nowMs = atTime(23, 30)), zone))
        assertTrue(ConditionEvaluator.evaluate(c, ctx(nowMs = atTime(2, 0)), zone))
        assertFalse(ConditionEvaluator.evaluate(c, ctx(nowMs = atTime(12, 0)), zone))
    }

    // ── battery / charging ─────────────────────────────────────────────────

    @Test
    fun `battery thresholds compare strictly`() {
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.BatteryAbove(30), ctx(battery = 50), zone))
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.BatteryAbove(50), ctx(battery = 50), zone))  // strict >
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.BatteryBelow(50), ctx(battery = 49), zone))
    }

    @Test
    fun `battery condition fails open when level unknown`() {
        // null battery → both BatteryAbove and BatteryBelow return false (can't confirm).
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.BatteryAbove(30), ctx(battery = null), zone))
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.BatteryBelow(30), ctx(battery = null), zone))
    }

    @Test
    fun `is_not_charging is the negation of is_charging`() {
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.IsNotCharging(), ctx(charging = false), zone))
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.IsNotCharging(), ctx(charging = true), zone))
    }

    // ── wifi ───────────────────────────────────────────────────────────────

    @Test
    fun `wifi_ssid_is matches case-sensitively`() {
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.WifiSsidIs("Home"), ctx(wifi = "Home"), zone))
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.WifiSsidIs("Home"), ctx(wifi = "home"), zone))
    }

    @Test
    fun `wifi condition fails open when ssid unknown`() {
        // null wifi → WifiSsidIs returns false (can't confirm).
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.WifiSsidIs("Home"), ctx(wifi = null), zone))
    }

    // ── foreground app ─────────────────────────────────────────────────────

    @Test
    fun `foreground_app_is matches exact package`() {
        assertTrue(ConditionEvaluator.evaluate(
            LinearCondition.ForegroundAppIs("com.tencent.mm"), ctx(foreground = "com.tencent.mm"), zone))
        assertFalse(ConditionEvaluator.evaluate(
            LinearCondition.ForegroundAppIs("com.tencent.mm"), ctx(foreground = "com.example.other"), zone))
    }

    // ── screen ─────────────────────────────────────────────────────────────

    @Test
    fun `screen_is_on and screen_is_off are complementary`() {
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.ScreenIsOn(), ctx(screenOn = true), zone))
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.ScreenIsOn(), ctx(screenOn = false), zone))
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.ScreenIsOff(), ctx(screenOn = false), zone))
    }

    // ── last_chat_ago (fail-open when no history) ──────────────────────────

    @Test
    fun `last_chat_ago true when gap exceeds threshold`() {
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - 2 * 60 * 60 * 1000L
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.LastChatAgo(60), ctx(nowMs = now, lastChatMs = twoHoursAgo), zone))
        assertFalse(ConditionEvaluator.evaluate(LinearCondition.LastChatAgo(180), ctx(nowMs = now, lastChatMs = twoHoursAgo), zone))
    }

    @Test
    fun `last_chat_ago fails open when no chat history`() {
        // null lastChatMs → returns true (don't block a workflow just because the user never chatted).
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.LastChatAgo(60), ctx(lastChatMs = null), zone))
    }

    // ── day_of_week_in (empty = no constraint) ─────────────────────────────

    @Test
    fun `day_of_week_in empty list passes vacuously`() {
        assertTrue(ConditionEvaluator.evaluate(LinearCondition.DayOfWeekIn(days = emptyList()), ctx(), zone))
    }

    // ── allPass (AND-combined) ─────────────────────────────────────────────

    @Test
    fun `allPass is AND-combined`() {
        val conditions = listOf(
            LinearCondition.BatteryAbove(30),
            LinearCondition.IsCharging()
        )
        // Both true → allPass true.
        assertTrue(ConditionEvaluator.allPass(conditions, ctx(battery = 80, charging = true), zone))
        // One false → allPass false.
        assertFalse(ConditionEvaluator.allPass(conditions, ctx(battery = 80, charging = false), zone))
    }

    @Test
    fun `allPass empty list is vacuously true`() {
        assertTrue(ConditionEvaluator.allPass(emptyList(), ctx(), zone))
    }

    // ── helper ─────────────────────────────────────────────────────────────

    /** Epoch ms for today at HH:mm in the test zone. */
    private fun atTime(hour: Int, minute: Int): Long {
        val today = java.time.LocalDate.now(zone)
        return today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }
}
