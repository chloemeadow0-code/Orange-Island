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
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.service.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 网易云风格音乐卡片小组件（2×2）。
 *
 * - 上方显示歌曲标题 / 作者 / 歌词节选
 * - 底部 ⏮ ▶/⏸ ⏭ 按钮通过 startService 指令驱动 [MusicPlaybackService] 做后台播放
 * - [MusicPlaybackService] 切歌或播放状态变化时广播 → 这里 [onReceive] 接收并刷新
 */
class MusicWidgetProvider : AppWidgetProvider() {

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
                    updateWidgetInternal(context, appWidgetManager, widgetId, null)
                }
                // 首次加载后，主动拉取 Service 的当前播放状态。
                // 如果 Service 正在播放 → 它会回一个 ACTION_STATE 广播，onReceive 用真实状态刷新。
                // 如果 Service 没在播放（甚至没启动）→ 不回广播，保持上面 library.json 的默认展示。
                context.startService(
                    Intent(context, MusicPlaybackService::class.java).apply {
                        action = MusicPlaybackService.ACTION_QUERY_STATE
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MusicWidget", "onUpdate failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            // Service 推送的状态变化 → 刷新
            MusicPlaybackService.ACTION_STATE -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val pendingResult = goAsync()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                scope.launch {
                    try {
                        for (id in ids) {
                            updateWidgetInternal(context, mgr, id, intent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicWidget", "state refresh failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_CYCLE -> {
                // 自动轮播（无播放时每 CYCLE_SECONDS 秒切到下一首展示）
                context.startService(
                    Intent(context, MusicPlaybackService::class.java).apply {
                        action = MusicPlaybackService.ACTION_NEXT
                    }
                )
            }
        }
    }

    private suspend fun updateWidgetInternal(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        stateIntent: Intent?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_music)

        // ── 决定显示什么 ──
        var title: String
        var artist: String
        var lyric: String
        var pager: String
        var isPlaying = false

        if (stateIntent != null && stateIntent.hasExtra(MusicPlaybackService.EXTRA_TITLE)) {
            // 用 Service 广播的状态
            title = stateIntent.getStringExtra(MusicPlaybackService.EXTRA_TITLE) ?: ""
            artist = stateIntent.getStringExtra(MusicPlaybackService.EXTRA_ARTIST) ?: ""
            lyric = stateIntent.getStringExtra(MusicPlaybackService.EXTRA_LYRIC) ?: ""
            isPlaying = stateIntent.getBooleanExtra(MusicPlaybackService.EXTRA_IS_PLAYING, false)
            val idx = stateIntent.getIntExtra(MusicPlaybackService.EXTRA_INDEX, 0)
            val total = stateIntent.getIntExtra(MusicPlaybackService.EXTRA_TOTAL, 0)
            pager = if (total > 1) "${idx + 1} / $total" else ""
            if (title.isBlank()) {
                title = "未在播放"
                artist = "点击 ▶ 开始"
                lyric = "♪ ♪ ♪"
            }
        } else {
            // 首次/无状态：从 library.json 读首曲展示
            val repo = MusicStudioRepository(context.applicationContext, providers = emptyList())
            val tracks = try { repo.loadTracks() } catch (_: Exception) { emptyList() }
            if (tracks.isEmpty()) {
                title = "还没有创作歌曲"
                artist = "让 AI 帮你写一首歌吧"
                lyric = "点击打开音乐工作室"
                pager = ""
            } else {
                val t = tracks[0]
                title = t.title.ifBlank { "未命名" }
                artist = "AI 创作" + if (t.style.isNotBlank()) " · ${t.style}" else ""
                lyric = pickLyricPreview(t)
                pager = if (tracks.size > 1) "1 / ${tracks.size}" else ""
            }
        }

        views.setTextViewText(R.id.music_title, title)
        views.setTextViewText(R.id.music_artist, artist)
        views.setTextViewText(R.id.music_lyric, lyric)
        views.setTextViewText(R.id.music_pager, pager)
        // 播放/暂停图标用 vector drawable 切换（比 emoji 字符清晰、跨机型一致）
        views.setImageViewResource(
            R.id.music_btn_play,
            if (isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
        )

        // ── 点击歌曲区域 → 打开 App ──
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", "music")
        }
        views.setOnClickPendingIntent(
            R.id.music_root,
            PendingIntent.getActivity(
                context, widgetId + 30000, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // ── 控制按钮 → 发指令给 MusicPlaybackService ──
        views.setOnClickPendingIntent(
            R.id.music_btn_prev,
            serviceControl(context, MusicPlaybackService.ACTION_PREV, REQ_PREV)
        )
        views.setOnClickPendingIntent(
            R.id.music_btn_play,
            serviceControl(context, MusicPlaybackService.ACTION_TOGGLE, REQ_PLAY)
        )
        views.setOnClickPendingIntent(
            R.id.music_btn_next,
            serviceControl(context, MusicPlaybackService.ACTION_NEXT, REQ_NEXT)
        )

        manager.updateAppWidget(widgetId, views)
    }

    private fun serviceControl(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 从 track.lyrics 里取一段展示。 */
    private fun pickLyricPreview(track: com.orangeisland.app.data.music.MusicStudioTrack): String {
        val raw = track.lyrics.trim()
        if (raw.isEmpty()) return "♪ ♪ ♪"
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return "♪ ♪ ♪"
        return if (lines.size >= 2) "${lines[0]}\n${lines[1]}" else lines[0]
    }

    companion object {
        private const val ACTION_CYCLE = "com.orangeisland.app.MUSIC_CYCLE"
        private const val REQ_PREV = 9102
        private const val REQ_PLAY = 9103
        private const val REQ_NEXT = 9104

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MusicWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
