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
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.StickyNoteEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面便签小组件（2×2，便签纸风格）。
 *
 * 每次刷新（系统约 30 分钟一次，或被 [refreshAll] 触发）从全部便签里**随机**取一条展示，
 * 模拟"每次开屏随机一句"的效果。无便签时显示引导文案。
 *
 * 数据源：[SettingsManager.stickyNotes]（与 AI 工具 [com.orangeisland.app.tool.StickyNoteToolProvider] 同源）。
 */
class StickyNoteWidgetProvider : AppWidgetProvider() {

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
                android.util.Log.e("StickyWidget", "onUpdate failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidgetInternal(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val settings = SettingsManager(context.applicationContext)
        val notes: List<StickyNoteEntry> = try {
            settings.stickyNotes.first()
        } catch (e: Exception) {
            android.util.Log.e("StickyWidget", "read notes failed", e)
            emptyList()
        }

        val views = RemoteViews(context.packageName, R.layout.widget_sticky)

        if (notes.isEmpty()) {
            views.setTextViewText(R.id.sticky_title, "")
            views.setTextViewText(R.id.sticky_content, "还没有便签\n让 AI 帮你写一句吧")
            views.setTextViewText(R.id.sticky_count, "")
            views.setTextViewText(R.id.sticky_time, "")
        } else {
            // 随机挑一条
            val note = notes.random()
            views.setTextViewText(R.id.sticky_title, note.title)
            views.setTextViewText(R.id.sticky_content, note.content)
            views.setTextViewText(R.id.sticky_count, "共 ${notes.size} 条")
            views.setTextViewText(R.id.sticky_time, formatDate(note.updatedAt))
        }

        // 点击打开 App
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.sticky_root,
            PendingIntent.getActivity(
                context, widgetId + 40000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    private fun formatDate(epoch: Long): String {
        if (epoch <= 0) return ""
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epoch))
    }

    companion object {
        /** 从 App 内部调用，强制刷新所有便签小组件。AI 增删改后会调用它。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, StickyNoteWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, StickyNoteWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
