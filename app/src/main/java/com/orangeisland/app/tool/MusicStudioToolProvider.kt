package com.orangeisland.app.tool

import android.app.Application
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.worker.MusicGenerationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Exposes the native Music Studio to the LLM as tools.
 *
 * - `generate_music`: enqueues a background WorkManager generation and returns immediately.
 *   The user is informed through the UI progress overlay and the music library.
 * - `list_music`: lists all locally saved tracks.
 * - `delete_music`: removes a track by title.
 */
class MusicStudioToolProvider(
    private val app: Application,
    private val repository: MusicStudioRepository
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.musicStudioEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "generate_music",
                description = "Create a music track from lyrics and an optional style. The generation " +
                    "runs in the background and takes 1–8 minutes. Tell the user it has started and " +
                    "that they can check the Music Studio for progress.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "title" to ToolProperty("string", "Song title."),
                        "lyrics" to ToolProperty("string", "Lyrics or a description of what the song should be about."),
                        "style" to ToolProperty("string", "Optional music style, e.g. 'pop, upbeat, female vocal' or 'lofi, chill'.")
                    ),
                    required = listOf("title", "lyrics")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "list_music",
                description = "List all songs generated in the Music Studio library.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "delete_music",
                description = "Delete a song from the Music Studio library by its exact title.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "title" to ToolProperty("string", "Exact title of the song to delete.")
                    ),
                    required = listOf("title")
                )
            ))
        )
    }

    override fun handles(name: String): Boolean =
        name in setOf("generate_music", "list_music", "delete_music")

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (!ctx.musicStudioEnabled) return err("disabled", "Music Studio is not enabled.")
        val args = parseArgs(arguments)

        val settingsManager = SettingsManager(app)
        val providerId = withContext(Dispatchers.IO) {
            settingsManager.musicStudioProvider.first()
        }

        return when (name) {
            "generate_music" -> generateMusic(args, providerId)
            "list_music" -> listMusic()
            "delete_music" -> deleteMusic(args)
            else -> err("unknown_tool", "Unknown music tool: $name")
        }
    }

    private fun generateMusic(args: Map<String, JsonPrimitive>, providerId: String): String {
        val title = args["title"]?.content?.takeIf { it.isNotBlank() }
            ?: return err("no_title", "Missing 'title' parameter.")
        val lyrics = args["lyrics"]?.content?.takeIf { it.isNotBlank() }
            ?: return err("no_lyrics", "Missing 'lyrics' parameter.")
        val style = args["style"]?.content?.takeIf { it.isNotBlank() } ?: ""

        val trackId = UUID.randomUUID().toString()

        MusicGenerationWorker.enqueue(
            context = app,
            trackId = trackId,
            providerId = providerId,
            title = title,
            lyrics = lyrics,
            style = style
        )

        return buildJsonObject {
            put("status", "enqueued")
            put("track_id", trackId)
            put("message", "已开始生成歌曲《$title》，预计需要 1–8 分钟，请稍后到音乐工作室查看。")
        }.toString()
    }

    private suspend fun listMusic(): String = withContext(Dispatchers.IO) {
        try {
            val tracks = repository.loadTracks()
            buildJsonObject {
                put("count", tracks.size)
                put("songs", tracks.joinToString(", ") { "《${it.title}》" })
            }.toString()
        } catch (e: Exception) {
            DebugLog.e(TAG, "list_music failed", e)
            err("load_failed", e.message)
        }
    }

    private suspend fun deleteMusic(args: Map<String, JsonPrimitive>): String = withContext(Dispatchers.IO) {
        val title = args["title"]?.content?.takeIf { it.isNotBlank() }
            ?: return@withContext err("no_title", "Missing 'title' parameter.")
        try {
            val tracks = repository.loadTracks()
            val target = tracks.find { it.title == title }
            if (target == null) {
                err("not_found", "未找到歌曲：$title")
            } else {
                repository.deleteTrack(target)
                buildJsonObject {
                    put("status", "deleted")
                    put("title", title)
                }.toString()
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "delete_music failed", e)
            err("delete_failed", e.message)
        }
    }

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
        private const val TAG = "MusicStudioToolProvider"
    }
}
