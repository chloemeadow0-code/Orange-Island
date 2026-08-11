package com.orangeisland.app.tool

import android.app.Application
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.music.LocalMusicRepository
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.service.MusicPlaybackService
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Exposes unified playback control tools for both AI-generated and user-uploaded music.
 *
 * The underlying playlist is managed by [MusicPlaybackService], which merges tracks from
 * [MusicStudioRepository] (generated) and [LocalMusicRepository] (uploaded). These tools
 * let the LLM query and control that unified queue.
 */
class LocalMusicToolProvider(
    private val app: Application,
    private val localMusicRepository: LocalMusicRepository,
    private val musicStudioRepository: MusicStudioRepository? = null
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.localMusicEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "play_music",
                description = "Play a specific track from the unified music library (AI-generated + user-uploaded). " +
                    "If neither track_id nor title is provided, resumes playback of the current queue position. " +
                    "If provided, searches the unified queue by exact id or fuzzy title match and plays the first match.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "track_id" to ToolProperty("string", "Exact track id to play. Optional."),
                        "title" to ToolProperty("string", "Song title to search and play the first match. Optional.")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "pause_music",
                description = "Pause the currently playing music.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "next_music",
                description = "Skip to the next track in the unified queue. Returns queue_empty if the queue is empty.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "previous_music",
                description = "Go back to the previous track in the unified queue. Returns queue_empty if the queue is empty.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "get_now_playing_music",
                description = "Get the real-time playback state (title, artist, source, position, duration, queue index). " +
                    "Reads the in-process StateFlow instantly — no broadcast delay.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "search_music",
                description = "Search the unified music library by title, artist, or album. " +
                    "Returns a list of tracks with id, title, artist, and source for use with play_music.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to ToolProperty("string", "Search query string. Required."),
                        "source" to ToolProperty("string", "Filter by source: 'all' (default), 'generated', or 'uploaded'.")
                    ),
                    required = listOf("query")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "list_music_library",
                description = "List all tracks in the unified music library (AI-generated + user-uploaded). " +
                    "Use this to see what songs are available before asking the user what to play. No search keyword needed.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "source" to ToolProperty("string", "Filter by source: 'all' (default), 'generated', or 'uploaded'.")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "set_play_mode",
                description = "Set the playback repeat/shuffle mode. " +
                    "Modes: 'sequential' = play through and stop, " +
                    "'loop' = repeat all tracks, " +
                    "'single' = repeat current track only, " +
                    "'shuffle' = random order with repeat.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "mode" to ToolProperty(
                            "string",
                            "One of: 'sequential', 'loop', 'single', 'shuffle'. Required."
                        )
                    ),
                    required = listOf("mode")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "get_play_mode",
                description = "Get the current playback mode and whether shuffle is enabled.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            ))
        )
    }

    override fun handles(name: String): Boolean =
        name in setOf(
            "play_music", "pause_music", "next_music", "previous_music",
            "get_now_playing_music", "search_music", "list_music_library",
            "set_play_mode", "get_play_mode"
        )

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (!ctx.localMusicEnabled) return err("disabled", "Local music control is not enabled.")
        val args = parseArgs(arguments)
        val snapshotBefore = MusicPlaybackService.snapshotFlow.value

        return try {
            when (name) {
                "play_music" -> playMusic(args, snapshotBefore)
                "pause_music" -> sendCommand(MusicPlaybackService.ACTION_PAUSE, "pause", snapshotBefore)
                "next_music" -> sendCommand(MusicPlaybackService.ACTION_NEXT, "next", snapshotBefore, checkQueue = true)
                "previous_music" -> sendCommand(MusicPlaybackService.ACTION_PREV, "previous", snapshotBefore, checkQueue = true)
                "get_now_playing_music" -> getNowPlaying()
                "search_music" -> searchMusic(args)
                "list_music_library" -> listMusicLibrary(args)
                "set_play_mode" -> setPlayMode(args)
                "get_play_mode" -> getPlayMode()
                else -> err("unknown_tool", "Unknown local music tool: $name")
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "execute $name failed", e)
            err("execution_failed", e.localizedMessage)
        }
    }

    private fun playMusic(args: Map<String, JsonPrimitive>, snapshotBefore: MusicPlaybackService.PlaybackSnapshot): String {
        val trackId = args["track_id"]?.content?.takeIf { it.isNotBlank() }
        val title = args["title"]?.content?.takeIf { it.isNotBlank() }

        return if (trackId == null && title == null) {
            sendCommand(MusicPlaybackService.ACTION_PLAY, "play (resume)", snapshotBefore)
        } else {
            val allTracks = loadUnifiedTracks()
            val match = if (trackId != null) {
                allTracks.find { it.id == trackId }
            } else {
                allTracks.find { it.title.contains(title!!, ignoreCase = true) }
            }
            if (match == null) {
                val suggestions = allTracks.filter { it.title.contains(title ?: "", ignoreCase = true) }
                    .take(3).joinToString(", ") { "《${it.title}》" }
                val msg = if (suggestions.isNotBlank()) "未找到歌曲，你是不是想找：$suggestions" else "未找到歌曲"
                err("not_found", msg)
            } else {
                app.startService(android.content.Intent(app, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_PLAY_TRACK
                    putExtra(MusicPlaybackService.EXTRA_TRACK_ID, match.id)
                })
                buildJsonObject {
                    put("status", "sent")
                    put("command", "play_track")
                    put("track_id", match.id)
                    put("title", match.title)
                    put("previous_state", snapshotToJson(snapshotBefore))
                }.toString()
            }
        }
    }

    private fun sendCommand(
        action: String,
        label: String,
        snapshotBefore: MusicPlaybackService.PlaybackSnapshot,
        checkQueue: Boolean = false
    ): String {
        if (checkQueue && snapshotBefore.queueTotal <= 0) {
            return err("queue_empty", "播放队列为空")
        }
        app.startService(android.content.Intent(app, MusicPlaybackService::class.java).apply {
            this.action = action
        })
        return buildJsonObject {
            put("status", "sent")
            put("command", label)
            put("previous_state", snapshotToJson(snapshotBefore))
        }.toString()
    }

    private suspend fun getNowPlaying(): String {
        // 优先读 ExoPlayer 实时值，避免 snapshotFlow 只在离散事件刷新导致 positionMs 过期
        val live = MusicPlaybackService.getLiveSnapshot()
        val snap = live ?: MusicPlaybackService.snapshotFlow.value
        return if (snap.queueTotal <= 0) {
            buildJsonObject {
                put("isPlaying", false)
                put("title", "")
                put("artist", "")
                put("source", "")
                put("positionMs", 0)
                put("durationMs", 0)
                put("queueIndex", -1)
                put("queueTotal", 0)
            }.toString()
        } else {
            snapshotToJson(snap)
        }
    }

    private fun searchMusic(args: Map<String, JsonPrimitive>): String {
        val query = args["query"]?.content?.takeIf { it.isNotBlank() }
            ?: return err("no_query", "Missing 'query' parameter.")
        val source = args["source"]?.content?.takeIf { it.isNotBlank() } ?: "all"

        val generated = if (source in setOf("all", "generated")) {
            try {
                musicStudioRepository?.loadTracks().orEmpty()
            } catch (e: Exception) {
                DebugLog.e(TAG, "searchMusic generated repo failed", e)
                emptyList()
            }
        } else emptyList()
        val uploaded = if (source in setOf("all", "uploaded")) {
            try {
                localMusicRepository.searchTracks(query)
            } catch (e: Exception) {
                DebugLog.e(TAG, "searchMusic local repo failed", e)
                emptyList()
            }
        } else emptyList()

        val results = mutableListOf<kotlinx.serialization.json.JsonObject>()
        generated.forEach { t ->
            if (t.title.contains(query, ignoreCase = true)) {
                results.add(buildJsonObject {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", "AI 创作" + if (t.style.isNotBlank()) " · ${t.style}" else "")
                    put("source", "generated")
                })
            }
        }
        uploaded.forEach { t ->
            results.add(buildJsonObject {
                put("id", t.id)
                put("title", t.title)
                put("artist", t.artist.ifBlank { "未知歌手" })
                put("source", "uploaded")
            })
        }

        return buildJsonObject {
            put("count", results.size)
            put("tracks", kotlinx.serialization.json.JsonArray(results))
        }.toString()
    }

    private fun snapshotToJson(snap: MusicPlaybackService.PlaybackSnapshot): String =
        buildJsonObject {
            put("title", snap.title)
            put("artist", snap.artist)
            put("source", snap.source)
            put("isPlaying", snap.isPlaying)
            put("positionMs", snap.positionMs)
            put("durationMs", snap.durationMs)
            put("queueIndex", snap.queueIndex)
            put("queueTotal", snap.queueTotal)
        }.toString()

    private fun listMusicLibrary(args: Map<String, JsonPrimitive>): String {
        val source = args["source"]?.content?.takeIf { it.isNotBlank() } ?: "all"
        val tracks = loadUnifiedTracks().filter { track ->
            when (source) {
                "generated" -> track.source == "generated"
                "uploaded" -> track.source == "uploaded"
                else -> true
            }
        }

        val results = tracks.map { t ->
            buildJsonObject {
                put("id", t.id)
                put("title", t.title)
                put("artist", t.artist)
                put("source", t.source)
            }
        }

        return buildJsonObject {
            put("count", results.size)
            put("tracks", kotlinx.serialization.json.JsonArray(results))
        }.toString()
    }

    private fun loadUnifiedTracks(): List<UnifiedTrack> {
        val generated = try { musicStudioRepository?.loadTracks().orEmpty() } catch (e: Exception) { emptyList() }
        val uploaded = try { localMusicRepository.loadTracks() } catch (e: Exception) { emptyList() }
        val all = mutableListOf<UnifiedTrack>()
        generated.forEach { all.add(UnifiedTrack(it.id, it.title, "AI 创作" + if (it.style.isNotBlank()) " · ${it.style}" else "", it.createdAt, "generated")) }
        uploaded.forEach { all.add(UnifiedTrack(it.id, it.title, it.artist.ifBlank { "未知歌手" }, it.addedAt, "uploaded")) }
        all.sortByDescending { it.timestamp }
        return all
    }

    private fun setPlayMode(args: Map<String, JsonPrimitive>): String {
        val modeStr = args["mode"]?.content?.trim()?.lowercase()
            ?: return err("missing_param", "Missing required parameter 'mode'.")

        val modeInt = when (modeStr) {
            "sequential" -> 0
            "loop"       -> 1
            "single"     -> 2
            "shuffle"    -> 3
            else -> return err("invalid_mode",
                "Invalid mode '$modeStr'. Valid values: sequential, loop, single, shuffle.")
        }

        app.startService(android.content.Intent(app, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_SET_PLAY_MODE
            putExtra(MusicPlaybackService.EXTRA_PLAY_MODE, modeInt)
        })

        return buildJsonObject {
            put("status", "ok")
            put("mode", modeStr)
        }.toString()
    }

    private fun getPlayMode(): String {
        val snap = MusicPlaybackService.snapshotFlow.value
        val modeStr = when (snap.repeatMode) {
            0    -> "sequential"
            1    -> "loop"
            2    -> "single"
            3    -> "shuffle"
            else -> "sequential"
        }
        return buildJsonObject {
            put("mode", modeStr)
            put("shuffle_enabled", snap.shuffleEnabled)
        }.toString()
    }

    private data class UnifiedTrack(val id: String, val title: String, val artist: String, val timestamp: Long, val source: String)

    private fun parseArgs(arguments: String): Map<String, JsonPrimitive> {
        return try {
            val map = json.decodeFromString<Map<String, JsonPrimitive>>(arguments.ifBlank { "{}" })
            map.filterValues { it is JsonPrimitive }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to parse arguments", e)
            emptyMap()
        }
    }

    private fun err(code: String, message: String?): String = buildJsonObject {
        put("status", "error")
        put("error", code)
        if (!message.isNullOrBlank()) put("message", message)
    }.toString()

    companion object {
        private const val TAG = "LocalMusicToolProvider"
    }
}
