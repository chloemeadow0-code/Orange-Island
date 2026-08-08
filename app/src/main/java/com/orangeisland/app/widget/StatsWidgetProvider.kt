package com.orangeisland.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.orangeisland.app.MainActivity
import com.orangeisland.app.R
import com.orangeisland.app.data.local.ChatDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 使用统计桌面小组件 —— 展示今日 / 本周 / 本月的使用数据：
 *  - 消息条数（AI 与用户分别显示）
 *  - Token 消耗总量
 *  - 文字字数（AI 与用户各自的累计字符数）
 *  - 使用时长（AI 生成耗时累计）
 *
 * 默认展示「今日」。点击切换到「本周」/「本月」，循环切换。
 * 数据全部来自 Room messages 表（tokenCount 只记在 MODEL 行）。
 */
class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                for (widgetId in appWidgetIds) {
                    updateWidgetInternal(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsWidget", "Failed to update widget", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 点击范围切换按钮 → 刷新该小组件
        if (intent.action == ACTION_CYCLE_RANGE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
            // 切换存储在 SharedPreferences，按 widgetId 区分
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids.firstOrNull() ?: return)
            val current = prefs.getInt(keyRange(widgetId), RANGE_TODAY)
            val next = when (current) {
                RANGE_TODAY -> RANGE_WEEK
                RANGE_WEEK -> RANGE_MONTH
                else -> RANGE_TODAY
            }
            prefs.edit().putInt(keyRange(widgetId), next).apply()
            // 触发刷新
            val update = Intent(context, StatsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            context.sendBroadcast(update)
        }
    }

    private suspend fun updateWidgetInternal(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val range = prefs.getInt(keyRange(widgetId), RANGE_TODAY)
        val since = startOfRange(range)

        val stats = computeStats(context, since)

        val views = RemoteViews(context.packageName, R.layout.widget_stats)

        // 范围标签
        views.setTextViewText(R.id.stats_range, rangeLabel(range))

        // 各项数值（2×2 紧凑版：消息 / Token / AI 字数 / 我的字数）
        views.setTextViewText(R.id.stats_msg_total, formatCount(stats.totalMessages))
        views.setTextViewText(R.id.stats_tokens, formatTokens(stats.tokens))
        views.setTextViewText(R.id.stats_ai_chars, formatCount(stats.aiChars))
        views.setTextViewText(R.id.stats_user_chars, formatCount(stats.userChars))

        // 点击范围标签 → 切换 今日/本周/本月
        val cycleIntent = Intent(context, StatsWidgetProvider::class.java).apply {
            action = ACTION_CYCLE_RANGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setOnClickPendingIntent(
            R.id.stats_range,
            PendingIntent.getBroadcast(
                context, widgetId, cycleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // 点击整体 → 打开 App
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.stats_root,
            PendingIntent.getActivity(
                context, widgetId + 10000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    /** 从 Room 计算指定时间点之后的全部统计。 */
    private suspend fun computeStats(context: Context, since: Long): Stats {
        val db = ChatDatabase.build(context)
        try {
            val dao = db.chatDao()
            val aiMessages = dao.countMessagesByRoleSince("MODEL", since)
            val userMessages = dao.countMessagesByRoleSince("USER", since)
            val tokens = dao.sumTokensSince(since)
            val duration = dao.sumDurationSince(since)

            // 字数需在 Kotlin 算（overflow 文本不能靠 SQL LENGTH）
            val aiRows = dao.getMessagesByRoleSince("MODEL", since)
            val userRows = dao.getMessagesByRoleSince("USER", since)
            var aiChars = 0
            for (m in aiRows) {
                val decoded = m.decodeLargeText(context)
                aiChars += decoded.text.length
            }
            var userChars = 0
            for (m in userRows) {
                val decoded = m.decodeLargeText(context)
                userChars += decoded.text.length
            }

            return Stats(
                aiMessages = aiMessages,
                userMessages = userMessages,
                totalMessages = aiMessages + userMessages,
                tokens = tokens,
                aiChars = aiChars,
                userChars = userChars,
                durationMs = duration
            )
        } finally {
            db.close()
        }
    }

    private fun startOfRange(range: Int): Long {
        val cal = Calendar.getInstance()
        when (range) {
            RANGE_TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            RANGE_WEEK -> {
                // 本周一 0 点（中国习惯周一为一周开始）
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                if (cal.timeInMillis > System.currentTimeMillis()) {
                    cal.add(Calendar.WEEK_OF_YEAR, -1)
                }
            }
            RANGE_MONTH -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun rangeLabel(range: Int): String = when (range) {
        RANGE_TODAY -> "今日"
        RANGE_WEEK -> "本周"
        RANGE_MONTH -> "本月"
        else -> "今日"
    }

    private fun formatCount(n: Int): String = when {
        n >= 10000 -> String.format("%.1fw", n / 10000.0)
        else -> n.toString()
    }

    private fun formatTokens(n: Long): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 10_000 -> String.format("%.1fk", n / 1000.0)
        else -> n.toString()
    }

    private fun formatDuration(ms: Long): String {
        val totalMin = ms / 60000
        return when {
            totalMin >= 60 -> String.format("%d小时%d分", totalMin / 60, totalMin % 60)
            totalMin >= 1 -> "${totalMin}分钟"
            else -> "${ms / 1000}秒"
        }
    }

    private fun keyRange(widgetId: Int) = "range_$widgetId"

    private data class Stats(
        val aiMessages: Int,
        val userMessages: Int,
        val totalMessages: Int,
        val tokens: Long,
        val aiChars: Int,
        val userChars: Int,
        val durationMs: Long
    )

    companion object {
        private const val ACTION_CYCLE_RANGE = "com.orangeisland.app.STATS_CYCLE_RANGE"
        private const val PREFS = "widget_stats"
        private const val RANGE_TODAY = 0
        private const val RANGE_WEEK = 1
        private const val RANGE_MONTH = 2

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, StatsWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, StatsWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
