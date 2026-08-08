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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConsoleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // goAsync() + 后台线程读取 Room，避免阻塞 BroadcastReceiver 主线程。
        // 复用项目里 PetBootReceiver 的同款模式。
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                for (widgetId in appWidgetIds) {
                    updateWidgetInternal(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                android.util.Log.e("ConsoleWidget", "Failed to update widget", e)
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
        val views = RemoteViews(context.packageName, R.layout.widget_console)

        // ── 三入口点击：视频 / 图片 / 文件，都跳聊天页 ──
        val videoIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", "chat")
            putExtra("attach", "video")
        }
        views.setOnClickPendingIntent(
            R.id.card_video,
            PendingIntent.getActivity(
                context, 1001, videoIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val imageIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", "chat")
            putExtra("attach", "image")
        }
        views.setOnClickPendingIntent(
            R.id.card_image,
            PendingIntent.getActivity(
                context, 1002, imageIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val fileIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", "chat")
            putExtra("attach", "file")
        }
        views.setOnClickPendingIntent(
            R.id.card_file,
            PendingIntent.getActivity(
                context, 1003, fileIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val inputIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", "chat")
            putExtra("focus_input", true)
        }
        views.setOnClickPendingIntent(
            R.id.bottom_input,
            PendingIntent.getActivity(
                context, 1004, inputIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        /** 从 App 内部调用，强制刷新所有控制台小组件。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ConsoleWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, ConsoleWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
