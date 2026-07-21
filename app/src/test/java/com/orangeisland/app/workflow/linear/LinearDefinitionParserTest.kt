package com.orangeisland.app.workflow.linear

import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearCondition
import com.orangeisland.app.workflow.linear.LinearDefinitionParser.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LinearDefinitionParser]. Pure JVM — the parser has no Android dependency,
 * which is why the AI-authoring validation can be tested exhaustively here.
 *
 * Covers: the happy path for every trigger family, the AND-combined condition list, action
 * validation (missing tool, unknown tool, timeout clamping), cooldown/daily-cap ranges, and
 * the structured error codes a model needs to self-correct on retry.
 */
class LinearDefinitionParserTest {

    private val knownTools = setOf("send_notification", "set_ringer_mode", "web_search", "read_memory_file")

    // ── Happy paths ────────────────────────────────────────────────────────

    @Test
    fun `parses a minimal manual workflow with one action`() {
        val json = """
            {"name":"Ping","trigger":{"type":"manual"},
             "actions":[{"tool":"send_notification","args":{"message":"hi"}}]}
        """.trimIndent()
        val result = LinearDefinitionParser.parse(json, knownTools)
        assertTrue(result.toString(), result is ParseResult.Ok)
        val def = (result as ParseResult.Ok).definition
        assertEquals("Ping", def.name)
        assertTrue(def.trigger is LinearTrigger.Manual)
        assertEquals(1, def.actions.size)
        assertEquals("send_notification", def.actions[0].tool)
        assertEquals(0L, def.cooldownMs)
        assertNull(def.maxRunsPerDay)
    }

    @Test
    fun `parses wifi trigger with ssid and conditions`() {
        val json = """
            {"name":"Home silent","enabled":true,
             "trigger":{"type":"wifi_connected","ssid":"MyHome"},
             "conditions":[
               {"type":"time_between","start":"22:00","end":"07:00"},
               {"type":"battery_above","percent":30,"invert":false},
               {"type":"is_charging","invert":true}
             ],
             "actions":[{"tool":"set_ringer_mode","args":{"mode":"silent"}}],
             "cooldownMs":300000,"maxRunsPerDay":5}
        """.trimIndent()
        val def = ok(json)
        assertTrue(def.trigger is LinearTrigger.WifiConnected)
        assertEquals("MyHome", (def.trigger as LinearTrigger.WifiConnected).ssid)
        assertEquals(3, def.conditions.size)
        assertTrue(def.conditions[0] is LinearCondition.TimeBetween)
        assertTrue(def.conditions[2] is LinearCondition.IsCharging)
        assertEquals(true, (def.conditions[2] as LinearCondition.IsCharging).invert)
        assertEquals(300_000L, def.cooldownMs)
        assertEquals(5, def.maxRunsPerDay)
    }

    @Test
    fun `parses geofence trigger with valid coordinates and radius`() {
        val json = """
            {"name":"Work","trigger":{"type":"geofence_enter","lat":39.9,"lng":116.4,"radiusM":200,"label":"office"},
             "actions":[{"tool":"send_notification"}]}
        """.trimIndent()
        val trigger = ok(json).trigger as LinearTrigger.GeofenceEnter
        assertEquals(39.9, trigger.lat, 0.001)
        assertEquals(200, trigger.radiusM)
        assertEquals("office", trigger.label)
    }

    @Test
    fun `parses notification trigger with regex filters`() {
        val json = """
            {"name":"WeChat alert","trigger":{"type":"notification_received","package_name":"com.tencent.mm",
              "title_matches":"^\\[.*\\].*"},
             "actions":[{"tool":"send_notification"}]}
        """.trimIndent()
        val trigger = ok(json).trigger as LinearTrigger.NotificationReceived
        assertEquals("com.tencent.mm", trigger.packageName)
        assertEquals("^\\[.*\\].*", trigger.titleMatches)
    }

    @Test
    fun `parses time_cron with time_of_day and days_of_week`() {
        val json = """
            {"name":"Weekday morning","trigger":{"type":"time_cron","time_of_day":"08:30","days_of_week":[1,2,3,4,5]},
             "actions":[{"tool":"send_notification"}]}
        """.trimIndent()
        val trigger = ok(json).trigger as LinearTrigger.TimeCron
        assertEquals("08:30", trigger.timeOfDay)
        assertEquals(listOf(1, 2, 3, 4, 5), trigger.daysOfWeek)
    }

    @Test
    fun `action timeout is clamped to allowed range`() {
        // 0s -> clamped up to 1s
        val json = """
            {"name":"X","trigger":{"type":"manual"},
             "actions":[{"tool":"send_notification","timeout_seconds":0}]}
        """.trimIndent()
        val a = ok(json).actions[0]
        assertTrue("expected timeout clamped to >= 1s, got ${a.timeoutMs}", a.timeoutMs >= 1_000L)
    }

    @Test
    fun `condition invert flag is read`() {
        val json = """
            {"name":"X","trigger":{"type":"manual"},
             "conditions":[{"type":"day_of_week_in","days":[6,7],"invert":true}],
             "actions":[{"tool":"send_notification"}]}
        """.trimIndent()
        val c = ok(json).conditions[0] as LinearCondition.DayOfWeekIn
        assertEquals(listOf(6, 7), c.days)
        assertTrue(c.invert)
    }

    // ── Error paths (stable codes the model can self-correct on) ───────────

    @Test
    fun `missing name returns missing_name`() {
        val r = LinearDefinitionParser.parse("""{"trigger":{"type":"manual"},"actions":[{"tool":"send_notification"}]}""", knownTools)
        assertErr(r, "missing_name")
    }

    @Test
    fun `missing trigger returns missing_trigger`() {
        val r = LinearDefinitionParser.parse("""{"name":"X","actions":[{"tool":"send_notification"}]}""", knownTools)
        assertErr(r, "missing_trigger")
    }

    @Test
    fun `unknown trigger type returns unknown_trigger_type`() {
        val r = LinearDefinitionParser.parse("""{"name":"X","trigger":{"type":"telepathy"},"actions":[{"tool":"send_notification"}]}""", knownTools)
        assertErr(r, "unknown_trigger_type")
    }

    @Test
    fun `empty actions returns empty_actions`() {
        val r = LinearDefinitionParser.parse("""{"name":"X","trigger":{"type":"manual"},"actions":[]}""", knownTools)
        assertErr(r, "empty_actions")
    }

    @Test
    fun `action with unknown tool returns unknown_tool`() {
        val r = LinearDefinitionParser.parse(
            """{"name":"X","trigger":{"type":"manual"},"actions":[{"tool":"delete_everything"}]}""", knownTools)
        assertErr(r, "unknown_tool")
    }

    @Test
    fun `action missing tool returns missing_tool`() {
        val r = LinearDefinitionParser.parse(
            """{"name":"X","trigger":{"type":"manual"},"actions":[{"args":{}}]}""", knownTools)
        assertErr(r, "missing_tool")
    }

    @Test
    fun `battery threshold out of range rejected`() {
        val r = LinearDefinitionParser.parse(
            """{"name":"X","trigger":{"type":"battery_below","threshold":150},"actions":[{"tool":"send_notification"}]}""",
            knownTools)
        assertTrue(r is ParseResult.Err)   // rejected (battery 1-100)
    }

    @Test
    fun `invalid regex in notification trigger rejected`() {
        val r = LinearDefinitionParser.parse(
            """{"name":"X","trigger":{"type":"notification_received","title_matches":"[unclosed"},"actions":[{"tool":"send_notification"}]}""",
            knownTools)
        assertTrue(r is ParseResult.Err)
    }

    @Test
    fun `geofence radius below minimum rejected`() {
        val r = LinearDefinitionParser.parse(
            """{"name":"X","trigger":{"type":"geofence_enter","lat":0,"lng":0,"radiusM":5},"actions":[{"tool":"send_notification"}]}""",
            knownTools)
        assertTrue(r is ParseResult.Err)   // radius must be ≥ 50
    }

    @Test
    fun `invalid json returns invalid_json`() {
        val r = LinearDefinitionParser.parse("""{not valid json""", knownTools)
        assertErr(r, "invalid_json")
    }

    @Test
    fun `cooldown over 24h rejected`() {
        val r = LinearDefinitionParser.parse(
            """{"name":"X","trigger":{"type":"manual"},"actions":[{"tool":"send_notification"}],"cooldownMs":99999999999}""",
            knownTools)
        assertErr(r, "invalid_cooldown")
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun ok(json: String) = (LinearDefinitionParser.parse(json, knownTools) as ParseResult.Ok).definition

    private fun assertErr(result: LinearDefinitionParser.ParseResult, code: String) {
        assertTrue("expected Err($code), got $result", result is ParseResult.Err)
        assertEquals(code, (result as ParseResult.Err).code)
        assertNotNull(result.detail)
    }
}
