package com.orangeisland.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.orangeisland.app.MainActivity
import com.orangeisland.app.R
import com.orangeisland.app.data.AnniversaryEntry
import com.orangeisland.app.data.AnniversaryUtils
import com.orangeisland.app.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 纪念日桌面小组件（2×2）。
 *
 * 展示离今天最近的一个纪念日倒计时；有多个纪念日时，每 [CYCLE_SECONDS]
 * 秒自动轮播到下一个。点击直接打开 App。
 */
class AnniversaryWidgetProvider : AppWidgetProvider() {

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
                // 排一次定时刷新，实现轮播
                scheduleNextCycle(context)
            } catch (e: Exception) {
                android.util.Log.e("AnniversaryWidget", "Failed to update widget", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CYCLE) {
            // 推进轮播索引，刷新所有该类小组件
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val idx = prefs.getInt(KEY_INDEX, 0) + 1
            prefs.edit().putInt(KEY_INDEX, idx).apply()

            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AnniversaryWidgetProvider::class.java))
            val pendingResult = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    for (widgetId in ids) {
                        updateWidgetInternal(context, mgr, widgetId)
                    }
                    scheduleNextCycle(context)
                } catch (e: Exception) {
                    android.util.Log.e("AnniversaryWidget", "cycle refresh failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun updateWidgetInternal(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val settings = SettingsManager(context.applicationContext)
        val entries = try {
            settings.anniversaries.first()
        } catch (e: Exception) {
            android.util.Log.e("AnniversaryWidget", "read anniversaries failed", e)
            emptyList()
        }

        val views = RemoteViews(context.packageName, R.layout.widget_anniversary)

        if (entries.isEmpty()) {
            views.setTextViewText(R.id.anniv_name, "还没有纪念日")
            views.setTextViewText(R.id.anniv_days, "—")
            views.setTextViewText(R.id.anniv_date, "点击打开 App 添加")
            views.setTextViewText(R.id.anniv_suffix, "")
            views.setTextViewText(R.id.anniv_badge, "纪念日")
        } else {
            // 按离今天最近排序（重复型永远 >=0；非重复型按固定日期远近）
            val today = LocalDate.now()
            val sorted = entries.sortedBy { AnniversaryUtils.daysUntilNext(it, today) }

            // 用全局轮播索引取当前要展示的纪念日
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val rawIdx = prefs.getInt(KEY_INDEX, 0)
            val idx = if (sorted.size == 1) 0 else rawIdx % sorted.size
            val entry = sorted[idx]

            val days = AnniversaryUtils.daysUntilNext(entry, today)
            val dateStr = AnniversaryUtils.formatDate(entry)

            views.setTextViewText(R.id.anniv_name, entry.name)
            views.setTextViewText(R.id.anniv_date, dateStr)

            when {
                days > 0L -> {
                    views.setTextViewText(R.id.anniv_days, days.toString())
                    views.setTextViewText(R.id.anniv_suffix, "天后")
                }
                days == 0L -> {
                    views.setTextViewText(R.id.anniv_days, "就是")
                    views.setTextViewText(R.id.anniv_suffix, "今天")
                }
                else -> {
                    views.setTextViewText(R.id.anniv_days, (-days).toString())
                    views.setTextViewText(R.id.anniv_suffix, "天前")
                }
            }

            // 第几个（仅重复型有意义）
            val years = AnniversaryUtils.yearsSince(entry, today)
            val badge = if (entry.recurring && years > 0) "第 $years 个纪念日" else "纪念日"
            views.setTextViewText(R.id.anniv_badge, badge)

            // 页码指示（多个时显示当前/总数）
            val pager = if (sorted.size > 1) "${idx + 1} / ${sorted.size}" else ""
            views.setTextViewText(R.id.anniv_pager, pager)
        }

        // 点击打开 App
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.anniv_root,
            PendingIntent.getActivity(
                context, widgetId + 20000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    /** 用 AlarmManager 定时触发自身广播，实现轮播（系统 updatePeriodMillis 最小 30 分钟，不够）。 */
    private fun scheduleNextCycle(context: Context) {
        val intent = Intent(context, AnniversaryWidgetProvider::class.java).apply {
            action = ACTION_CYCLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQ_CYCLE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + CYCLE_SECONDS * 1000L,
            pendingIntent
        )
    }

    companion object {
        private const val ACTION_CYCLE = "com.orangeisland.app.ANNIV_CYCLE"
        private const val PREFS = "widget_anniversary"
        private const val KEY_INDEX = "cycle_index"
        private const val REQ_CYCLE = 9001
        private const val CYCLE_SECONDS = 8L  // 每 8 秒切换一个

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AnniversaryWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, AnniversaryWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
