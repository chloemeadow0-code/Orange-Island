package com.orangeisland.app.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.orangeisland.app.MainActivity
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.data.music.MusicStudioTrack
import com.orangeisland.app.widget.MusicWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 后台音乐播放服务。
 *
 * 单一播放源：App 内的音乐页面、桌面小组件、通知/锁屏控件全部通过这个 Service 控制。
 *  - 启动时从 library.json 加载播放列表
 *  - 通过 [MusicWidgetProvider] 的广播指令接收：PLAY / PAUSE / NEXT / PREV / PLAY_INDEX
 *  - 状态变化（切歌/播放/暂停）→ 广播 ACTION_STATE 广播给小组件刷新
 *
 * 复用 MusicStudioRepository 读取本地歌曲列表（只读，传空 providers）。
 */
class MusicPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var tracks: List<MusicStudioTrack> = emptyList()

    override fun onCreate() {
        super.onCreate()
        loadTracks()

        val exo = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    broadcastState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    broadcastState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        // 自动下一首（MediaSession 默认 REPEAT_MODE_OFF 会停，这里手动跳）
                        if (player?.hasNextMediaItem() == true) player?.seekToNext()
                        else player?.seekTo(0, 0)
                    }
                    broadcastState()
                }
            })
        }
        player = exo

        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉 App：暂停并停掉服务（避免空转）
        player?.let {
            if (!it.isPlaying) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // ── 指令接收 ───────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playCurrent()
            ACTION_PAUSE -> player?.pause()
            ACTION_TOGGLE -> if (player?.isPlaying == true) player?.pause() else playCurrent()
            ACTION_NEXT -> next()
            ACTION_PREV -> prev()
            ACTION_QUERY_STATE -> broadcastState()  // 小组件首次加载时拉取当前状态
            ACTION_PLAY_INDEX -> {
                val idx = intent.getIntExtra(EXTRA_INDEX, -1)
                if (idx in tracks.indices) playAt(idx)
            }
            ACTION_PLAY_TRACK -> {
                // App 内按 trackId 播放（与小组件单一播放源）
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID) ?: return START_NOT_STICKY
                val idx = tracks.indexOfFirst { it.id == trackId }
                if (idx >= 0) playAt(idx) else playCurrent()
            }
            ACTION_PLAY_PATH -> {
                // VoiceVersion 等独立音频：单条播放，不进播放列表
                val path = intent.getStringExtra(EXTRA_PATH) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "音色版本"
                playSinglePath(path, title)
            }
        }
        return START_NOT_STICKY
    }

    private fun playSinglePath(path: String, title: String) {
        val item = MediaItem.Builder()
            .setUri(path)
            .setMediaId("single_$path")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("AI 创作")
                    .build()
            )
            .build()
        player?.setMediaItem(item)
        player?.prepare()
        player?.play()
        broadcastState()
    }

    // ── 播放控制 ───────────────────────────────────────────────────────────

    private fun loadTracks() {
        serviceScope.launch(Dispatchers.IO) {
            val repo = MusicStudioRepository(this@MusicPlaybackService, providers = emptyList())
            tracks = repo.loadTracks()
            // 把列表喂给 ExoPlayer（只有 localPath 有效的）
            val items = tracks.mapNotNull { t ->
                val path = t.localPath.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MediaItem.Builder()
                    .setUri(path)
                    .setMediaId(t.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(t.title.ifBlank { "未命名" })
                            .setArtist("AI 创作" + if (t.style.isNotBlank()) " · ${t.style}" else "")
                            .build()
                    )
                    .build()
            }
            serviceScope.launch(Dispatchers.Main) {
                player?.setMediaItems(items)
                player?.prepare()
                broadcastState()
            }
        }
    }

    private fun playCurrent() {
        if (player?.playbackState == Player.STATE_IDLE) {
            player?.prepare()
        }
        player?.play()
    }

    private fun playAt(idx: Int) {
        player?.seekToDefaultPosition(idx)
        player?.play()
    }

    private fun next() {
        if (tracks.isEmpty()) return
        val cur = player?.currentMediaItemIndex ?: 0
        val nextIdx = (cur + 1) % tracks.size
        playAt(nextIdx)
    }

    private fun prev() {
        if (tracks.isEmpty()) return
        val cur = player?.currentMediaItemIndex ?: 0
        val prevIdx = (cur - 1 + tracks.size) % tracks.size
        playAt(prevIdx)
    }

    // ── 状态广播 → 小组件刷新 ───────────────────────────────────────────────

    private fun broadcastState() {
        val p = player ?: return
        val idx = p.currentMediaItemIndex
        val track = tracks.getOrNull(idx)
        val state = Intent(ACTION_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_TITLE, track?.title ?: "")
            putExtra(EXTRA_ARTIST, track?.let { "AI 创作" + if (it.style.isNotBlank()) " · ${it.style}" else "" } ?: "")
            putExtra(EXTRA_LYRIC, track?.lyrics ?: "")
            putExtra(EXTRA_IS_PLAYING, p.isPlaying)
            putExtra(EXTRA_INDEX, idx)
            putExtra(EXTRA_TOTAL, tracks.size)
        }
        // 单一信号源：只发 ACTION_STATE 广播。小组件的 onReceive 会用它刷新，
        // 不要再额外发 APPWIDGET_UPDATE（会触发 onUpdate 走 null 分支，用 library.json 的
        // 首曲覆盖当前播放状态，导致 ▶/⏸ 永远不切换）。
        sendBroadcast(state)
    }

    companion object {
        // 指令 actions（Service 响应）
        const val ACTION_PLAY = "com.orangeisland.app.music.PLAY"
        const val ACTION_PAUSE = "com.orangeisland.app.music.PAUSE"
        const val ACTION_TOGGLE = "com.orangeisland.app.music.TOGGLE"
        const val ACTION_NEXT = "com.orangeisland.app.music.NEXT"
        const val ACTION_PREV = "com.orangeisland.app.music.PREV"
        const val ACTION_PLAY_INDEX = "com.orangeisland.app.music.PLAY_INDEX"
        const val ACTION_PLAY_TRACK = "com.orangeisland.app.music.PLAY_TRACK"
        const val ACTION_PLAY_PATH = "com.orangeisland.app.music.PLAY_PATH"
        const val ACTION_QUERY_STATE = "com.orangeisland.app.music.QUERY_STATE"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_PATH = "path"

        // 状态广播 action（小组件接收）
        const val ACTION_STATE = "com.orangeisland.app.music.STATE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_LYRIC = "lyric"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_TOTAL = "total"
    }
}
