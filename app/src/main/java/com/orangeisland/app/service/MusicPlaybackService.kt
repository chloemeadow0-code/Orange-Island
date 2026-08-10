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
import com.orangeisland.app.data.music.LocalMusicRepository
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.data.music.MusicStudioTrack
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.widget.MusicWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 后台音乐播放服务。
 *
 * 统一播放源：App 内的 AI 生成音乐、用户上传音乐、桌面小组件、通知/锁屏控件
 * 全部通过这个 Service 控制。
 *  - 启动时从 MusicStudioRepository + LocalMusicRepository 加载合并播放列表
 *  - 通过广播指令接收：PLAY / PAUSE / NEXT / PREV / PLAY_INDEX / RELOAD_LIBRARY
 *  - 状态变化 → 广播 ACTION_STATE 给小组件刷新 + 更新进程内 snapshotFlow 供工具即时读取
 */
class MusicPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var tracks: List<PlaybackTrack> = emptyList()

    /** 待播放意图缓存：当 Service 收到 PLAY_TRACK / PLAY_INDEX 指令时 tracks 尚未加载完毕，
     *  先把意图存下来，等 loadTracks / reloadLibrary 完成后再自动触发。
     *  正常情况（tracks 已就绪）不走此缓存，直接查到即播。 */
    private var pendingTrackId: String? = null
    private var pendingIndex: Int? = null

    /** 统一运行时播放条目（内部私有，不改动外部数据类） */
    private data class PlaybackTrack(
        val id: String,
        val title: String,
        val artist: String,
        val localPath: String,
        val source: String // "generated" | "uploaded"
    )

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

        runningInstance = this
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        player?.let {
            if (!it.isPlaying) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        runningInstance = null
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
            ACTION_QUERY_STATE -> broadcastState()
            ACTION_PLAY_INDEX -> {
                val idx = intent.getIntExtra(EXTRA_INDEX, -1)
                if (idx in tracks.indices) {
                    playAt(idx)
                } else if (tracks.isEmpty()) {
                    // tracks 尚未加载完毕，缓存意图，等加载完成后自动触发
                    pendingIndex = idx
                }
            }
            ACTION_PLAY_TRACK -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID) ?: return START_NOT_STICKY
                val idx = tracks.indexOfFirst { it.id == trackId }
                if (idx >= 0) {
                    playAt(idx)
                } else if (tracks.isEmpty()) {
                    // tracks 尚未加载完毕，缓存意图，等加载完成后自动触发
                    pendingTrackId = trackId
                }
                // 如果 tracks 已加载且 id 真不存在，静默忽略（不 fallback playCurrent 制造意外播放）
            }
            ACTION_PLAY_PATH -> {
                val path = intent.getStringExtra(EXTRA_PATH) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "音色版本"
                playSinglePath(path, title)
            }
            ACTION_RELOAD_LIBRARY -> reloadLibrary()
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
            try {
                val generatedRepo = MusicStudioRepository(this@MusicPlaybackService, providers = emptyList())
                val localRepo = LocalMusicRepository(this@MusicPlaybackService)
                val generated = generatedRepo.loadTracks()
                val uploaded = localRepo.loadTracks()

                val merged = mutableListOf<PlaybackTrack>()
                generated.forEach { t ->
                    merged.add(
                        PlaybackTrack(
                            id = t.id,
                            title = t.title.ifBlank { "未命名" },
                            artist = "AI 创作" + if (t.style.isNotBlank()) " · ${t.style}" else "",
                            localPath = t.localPath,
                            source = "generated"
                        )
                    )
                }
                uploaded.forEach { t ->
                    merged.add(
                        PlaybackTrack(
                            id = t.id,
                            title = t.title.ifBlank { "未命名" },
                            artist = t.artist.ifBlank { "未知歌手" },
                            localPath = t.localPath,
                            source = "uploaded"
                        )
                    )
                }
                merged.sortByDescending { track ->
                    when (track.source) {
                        "generated" -> generated.find { it.id == track.id }?.createdAt ?: 0L
                        else -> uploaded.find { it.id == track.id }?.addedAt ?: 0L
                    }
                }
                tracks = merged

                val items = tracks.mapNotNull { t ->
                    val path = t.localPath.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    MediaItem.Builder()
                        .setUri(path)
                        .setMediaId(t.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(t.title)
                                .setArtist(t.artist)
                                .setExtras(Bundle().apply { putString(EXTRA_SOURCE, t.source) })
                                .build()
                        )
                        .build()
                }
                serviceScope.launch(Dispatchers.Main) {
                    player?.setMediaItems(items)
                    player?.prepare()
                    checkPendingPlayback()
                    broadcastState()
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "loadTracks failed", e)
                serviceScope.launch(Dispatchers.Main) {
                    tracks = emptyList()
                    player?.clearMediaItems()
                    broadcastState()
                }
            }
        }
    }

    private fun reloadLibrary() {
        val p = player ?: return
        val oldMediaId = p.currentMediaItem?.mediaId
        val oldPosition = p.currentPosition
        val wasPlaying = p.isPlaying

        serviceScope.launch(Dispatchers.IO) {
            try {
                val generatedRepo = MusicStudioRepository(this@MusicPlaybackService, providers = emptyList())
                val localRepo = LocalMusicRepository(this@MusicPlaybackService)
                val generated = generatedRepo.loadTracks()
                val uploaded = localRepo.loadTracks()

                val merged = mutableListOf<PlaybackTrack>()
                generated.forEach { t ->
                    merged.add(
                        PlaybackTrack(
                            id = t.id,
                            title = t.title.ifBlank { "未命名" },
                            artist = "AI 创作" + if (t.style.isNotBlank()) " · ${t.style}" else "",
                            localPath = t.localPath,
                            source = "generated"
                        )
                    )
                }
                uploaded.forEach { t ->
                    merged.add(
                        PlaybackTrack(
                            id = t.id,
                            title = t.title.ifBlank { "未命名" },
                            artist = t.artist.ifBlank { "未知歌手" },
                            localPath = t.localPath,
                            source = "uploaded"
                        )
                    )
                }
                merged.sortByDescending { track ->
                    when (track.source) {
                        "generated" -> generated.find { it.id == track.id }?.createdAt ?: 0L
                        else -> uploaded.find { it.id == track.id }?.addedAt ?: 0L
                    }
                }
                tracks = merged

                val items = tracks.mapNotNull { t ->
                    val path = t.localPath.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    MediaItem.Builder()
                        .setUri(path)
                        .setMediaId(t.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(t.title)
                                .setArtist(t.artist)
                                .setExtras(Bundle().apply { putString(EXTRA_SOURCE, t.source) })
                                .build()
                        )
                        .build()
                }

                val newIndex = if (oldMediaId != null) tracks.indexOfFirst { it.id == oldMediaId } else -1

                serviceScope.launch(Dispatchers.Main) {
                    if (newIndex >= 0) {
                        player?.setMediaItems(items, newIndex, oldPosition)
                        if (wasPlaying) player?.play()
                    } else {
                        player?.setMediaItems(items)
                        if (items.isNotEmpty()) {
                            player?.seekToDefaultPosition(0)
                        }
                        if (wasPlaying && items.isNotEmpty()) player?.play()
                    }
                    player?.prepare()
                    checkPendingPlayback()
                    broadcastState()
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "reloadLibrary failed", e)
                serviceScope.launch(Dispatchers.Main) {
                    tracks = emptyList()
                    player?.clearMediaItems()
                    broadcastState()
                }
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

    /** 在 tracks 加载/刷新完成后，检查是否有缓存的待播放意图需要兑现。
     *  确保在 player.prepare() 之后调用，避免时序问题。
     *  正常情况（tracks 已就绪时收到指令）此函数不执行任何操作。 */
    private fun checkPendingPlayback() {
        val trackId = pendingTrackId
        if (trackId != null) {
            val idx = tracks.indexOfFirst { it.id == trackId }
            if (idx >= 0) {
                playAt(idx)
            }
            pendingTrackId = null
            pendingIndex = null
            return
        }
        val idx = pendingIndex
        if (idx != null && idx in tracks.indices) {
            playAt(idx)
            pendingIndex = null
        }
    }

    // ── 状态广播 → 小组件刷新 + 进程内 StateFlow ───────────────────────────────

    private fun broadcastState() {
        val p = player ?: return
        val idx = p.currentMediaItemIndex
        val track = tracks.getOrNull(idx)
        val source = track?.source ?: ""
        val artist = track?.artist ?: ""
        val isPlaying = p.isPlaying
        val positionMs = p.currentPosition
        val durationMs = p.duration.coerceAtLeast(0)
        val total = tracks.size

        val snapshot = PlaybackSnapshot(
            title = track?.title ?: "",
            artist = artist,
            source = source,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            queueIndex = idx,
            queueTotal = total
        )
        _snapshot.value = snapshot

        val state = Intent(ACTION_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_TITLE, track?.title ?: "")
            putExtra(EXTRA_ARTIST, artist)
            putExtra(EXTRA_LYRIC, track?.let { "" } ?: "") // lyric 字段保留，实际内容为空（本地音乐无歌词）
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_INDEX, idx)
            putExtra(EXTRA_TOTAL, total)
            putExtra(EXTRA_SOURCE, source)
            putExtra(EXTRA_NOW_PLAYING_ID, track?.id ?: "")
        }
        sendBroadcast(state)
    }

    // ── 进程内即时状态（供工具层直接读取，不走广播） ─────────────────────────────

    data class PlaybackSnapshot(
        val title: String = "",
        val artist: String = "",
        val source: String = "",
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val queueIndex: Int = -1,
        val queueTotal: Int = 0
    )

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
        const val ACTION_RELOAD_LIBRARY = "com.orangeisland.app.music.RELOAD_LIBRARY"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_PATH = "path"
        const val EXTRA_SOURCE = "source"

        // 状态广播 action（小组件接收）
        const val ACTION_STATE = "com.orangeisland.app.music.STATE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_LYRIC = "lyric"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_NOW_PLAYING_ID = "now_playing_id"

        private val _snapshot = MutableStateFlow(PlaybackSnapshot())
        val snapshotFlow: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

        private var runningInstance: MusicPlaybackService? = null

        /** 实时查询当前播放位置和状态，直接读 ExoPlayer 当前值，不依赖离散事件触发的 snapshot 缓存。
         *  供同进程内工具类即时调用；Service 未运行时返回 null。
         *  ExoPlayer 必须在主线程访问，因此全部 player 读取逻辑用 withContext(Dispatchers.Main) 包裹。 */
        suspend fun getLiveSnapshot(): PlaybackSnapshot? {
            val instance = runningInstance ?: return null
            val p = instance.player ?: return null
            return withContext(Dispatchers.Main) {
                val idx = p.currentMediaItemIndex
                val track = instance.tracks.getOrNull(idx)
                PlaybackSnapshot(
                    title = track?.title ?: "",
                    artist = track?.artist ?: "",
                    source = track?.source ?: "",
                    isPlaying = p.isPlaying,
                    positionMs = p.currentPosition,
                    durationMs = p.duration.coerceAtLeast(0),
                    queueIndex = idx,
                    queueTotal = instance.tracks.size
                )
            }
        }

        private const val TAG = "MusicPlaybackService"
    }
}
