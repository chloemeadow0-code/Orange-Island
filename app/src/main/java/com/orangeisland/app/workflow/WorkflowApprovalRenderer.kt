package com.orangeisland.app.workflow

import com.orangeisland.app.model.LinearCondition
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow

/**
 * Renders a [LinearWorkflow] as a human-readable approval card — the text the user sees in the
 * "approve this workflow?" dialog before an AI-authored definition is persisted.
 *
 * The goal is that a non-technical user can read the card and decide whether to allow the
 * workflow, without ever seeing JSON. Output shape:
 *
 * ```
 * 创建工作流 "回家静音"
 *
 * 当：连接到 WiFi "MyHome"
 * 仅当：22:00 到 06:30 之间、电量 > 30%
 * 执行：
 *   1. set_ringer_mode(mode="silent")
 *   2. send_notification(message="已静音")
 *
 * 冷却：5 分钟 · 每日上限：10 次
 * ```
 *
 * Pure functions, no Android dependency — fully unit-testable.
 *
 * Independent implementation.
 */
object WorkflowApprovalRenderer {

    /** Full plain-text rendering of a create/update card. */
    fun renderCreate(def: LinearWorkflow): String = buildString {
        appendLine("创建工作流 \"${def.name}\"")
        if (def.description.isNotBlank()) appendLine(def.description)
        appendLine()
        appendLine("当：${triggerText(def.trigger)}")
        appendLine("仅当：${if (def.conditions.isEmpty()) "始终满足" else def.conditions.joinToString("、") { conditionText(it) }}")
        appendLine("执行：")
        def.actions.forEachIndexed { idx, action ->
            appendLine("  ${idx + 1}. ${action.tool}${argsHint(action.args)}")
        }
        appendLine()
        append("冷却：${if (def.cooldownMs == 0L) "无" else formatDuration(def.cooldownMs)}")
        append(" · 每日上限：${def.maxRunsPerDay?.let { "$it 次" } ?: "无限"}")
    }

    /** Short one-line summary for the list/detail screens. */
    fun triggerText(trigger: LinearTrigger): String = when (trigger) {
        is LinearTrigger.Manual -> "手动触发"
        is LinearTrigger.TimeCron -> when {
            !trigger.timeOfDay.isNullOrBlank() -> {
                val days = if (trigger.daysOfWeek.isEmpty()) "每天" else "每${trigger.daysOfWeek.joinToString(",") { dayName(it) }}"
                "$days ${trigger.timeOfDay}"
            }
            !trigger.cron.isNullOrBlank() -> "定时（${trigger.cron}）"
            else -> "定时"
        }
        is LinearTrigger.WifiConnected -> "连接到 WiFi" + (trigger.ssid?.let { " \"$it\"" } ?: "")
        is LinearTrigger.WifiDisconnected -> "断开 WiFi" + (trigger.ssid?.let { " \"$it\"" } ?: "")
        is LinearTrigger.BluetoothConnected -> "蓝牙设备连接" + (trigger.deviceAddress?.let { "（$it）" } ?: "")
        is LinearTrigger.BluetoothDisconnected -> "蓝牙设备断开" + (trigger.deviceAddress?.let { "（$it）" } ?: "")
        is LinearTrigger.HeadphonesPlugged -> "插入耳机"
        is LinearTrigger.HeadphonesUnplugged -> "拔出耳机"
        is LinearTrigger.PowerConnected -> "接通电源"
        is LinearTrigger.PowerDisconnected -> "断开电源"
        is LinearTrigger.BatteryBelow -> "电量低于 ${trigger.threshold}%"
        is LinearTrigger.BatteryAbove -> "电量高于 ${trigger.threshold}%"
        is LinearTrigger.GeofenceEnter -> "进入${trigger.label ?: "地点（${trigger.lat},${trigger.lng}, ${trigger.radiusM}米）"}"
        is LinearTrigger.GeofenceExit -> "离开${trigger.label ?: "地点（${trigger.lat},${trigger.lng}, ${trigger.radiusM}米）"}"
        is LinearTrigger.AppLaunched -> "${trigger.packageName} 启动"
        is LinearTrigger.AppClosed -> "${trigger.packageName} 关闭"
        is LinearTrigger.AppForegroundDuration -> "${trigger.packageName} 在前台停留 ${trigger.minutes} 分钟"
        is LinearTrigger.NotificationReceived -> {
            val parts = mutableListOf<String>()
            trigger.packageName?.let { parts += "来自 $it" }
            trigger.titleContains?.let { parts += "标题含 \"$it\"" }
            trigger.textContains?.let { parts += "内容含 \"$it\"" }
            trigger.titleMatches?.let { parts += "标题匹配 /$it/" }
            trigger.textMatches?.let { parts += "内容匹配 /$it/" }
            "收到通知" + if (parts.isEmpty()) "" else "（${parts.joinToString("、")}）"
        }
        is LinearTrigger.BootCompleted -> "设备开机"
        is LinearTrigger.ScreenOn -> "亮屏"
        is LinearTrigger.ScreenOff -> "息屏"
    }

    /** Plain-text rendering of one condition. Applies the invert prefix when set. */
    fun conditionText(c: LinearCondition): String {
        val base = when (c) {
            is LinearCondition.TimeBetween -> "${c.start} 到 ${c.end} 之间"
            is LinearCondition.DayOfWeekIn -> if (c.days.isEmpty()) "任意星期" else "星期 ${c.days.joinToString(",") { dayName(it) }}"
            is LinearCondition.WifiSsidIs -> "WiFi 是 ${c.ssid}"
            is LinearCondition.WifiSsidIn -> "WiFi 属于 ${c.ssids.joinToString(",")}"
            is LinearCondition.BatteryAbove -> "电量 > ${c.percent}%"
            is LinearCondition.BatteryBelow -> "电量 < ${c.percent}%"
            is LinearCondition.IsCharging -> "充电中"
            is LinearCondition.IsNotCharging -> "未充电"
            is LinearCondition.ForegroundAppIs -> "${c.packageName} 在前台"
            is LinearCondition.ForegroundAppIn -> "${c.packageNames.size} 个 App 之一在前台"
            is LinearCondition.ScreenIsOn -> "屏幕亮"
            is LinearCondition.ScreenIsOff -> "屏幕灭"
            is LinearCondition.LastChatAgo -> "最近 ${c.minutes} 分钟未聊天"
        }
        return if (c.invert) "非（$base）" else base
    }

    /** ISO weekday number → short Chinese name. 1=Mon..7=Sun. */
    private fun dayName(iso: Int): String = when (iso) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"
        else -> "?"
    }

    /** Render a JsonObject args payload as `(k="v", ...)` truncated to ~80 chars. Sensitive keys
     *  (token/password/api_key/etc.) are masked so a workflow carrying credentials doesn't echo
     *  them into the user's chat history. */
    private fun argsHint(args: kotlinx.serialization.json.JsonObject): String {
        if (args.isEmpty()) return ""
        val pairs = args.entries.joinToString(", ") { (k, v) ->
            val value = if (isSensitiveKey(k)) "***" else argValue(v)
            "$k=\"$value\""
        }
        val hint = if (pairs.length > 80) pairs.take(80) + "…" else pairs
        return "($hint)"
    }

    private fun argValue(v: kotlinx.serialization.json.JsonElement): String = when (v) {
        is kotlinx.serialization.json.JsonPrimitive -> v.content.take(40)
        else -> v.toString().take(40)
    }

    private fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase().replace("-", "_").replace(" ", "_")
        return SENSITIVE_PARTS.any { part -> lower == part || lower.contains(part) }
    }

    private val SENSITIVE_PARTS = setOf(
        "token", "password", "passphrase", "private_key", "privatekey",
        "secret", "api_key", "apikey", "authorization", "auth_token", "access_token",
        "client_secret", "credential", "credentials"
    )

    /** Format a millisecond duration as a short Chinese string (e.g. "5 分钟", "2 小时", "30 秒"). */
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "$seconds 秒"
            seconds < 3600 -> "${seconds / 60} 分钟"
            seconds < 86400 -> "${seconds / 3600} 小时"
            else -> "${seconds / 86400} 天"
        }
    }
}
