package com.orangeisland.app.data.music

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 本地音乐上传库的存储与元数据管理。
 *
 * 职责仿照 [MusicStudioRepository]，但完全独立一套存储：
 * - 私有目录 `files/local_music/`
 * - 清单文件 `library.json`
 * - 上传时复制文件进私有目录，用 [MediaMetadataRetriever] 读 ID3 标签
 */
@OptIn(ExperimentalSerializationApi::class)
class LocalMusicRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val baseDir: File
        get() = File(context.filesDir, "local_music").also { if (!it.exists()) it.mkdirs() }

    private val libraryFile: File
        get() = File(baseDir, "library.json")

    fun loadTracks(): List<LocalMusicTrack> {
        return try {
            if (!libraryFile.exists()) return emptyList()
            libraryFile.inputStream().use { json.decodeFromStream(it) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "loadTracks failed", e)
            emptyList()
        }
    }

    private fun saveTracks(tracks: List<LocalMusicTrack>) {
        try {
            libraryFile.outputStream().use { json.encodeToStream(tracks, it) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "saveTracks failed", e)
        }
    }

    suspend fun importTrack(sourceUri: Uri, appContext: Context): LocalMusicTrack = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver

        // ── 查重：复制之前先读元数据 ────────────────────────────────────────
        val preRetriever = MediaMetadataRetriever()
        var preTitle = ""
        var preDurationMs = 0L
        try {
            preRetriever.setDataSource(appContext, sourceUri)
            preTitle = preRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: ""
            preDurationMs = preRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            DebugLog.e(TAG, "pre-read metadata failed for dedup, will import anyway: $sourceUri", e)
        } finally {
            preRetriever.release()
        }

        if (preTitle.isNotBlank() && preDurationMs > 0L) {
            val existing = loadTracks()
            val isDuplicate = existing.any { track ->
                track.title.equals(preTitle, ignoreCase = true) &&
                    kotlin.math.abs(track.durationMs - preDurationMs) < 500L
            }
            if (isDuplicate) throw DuplicateTrackException("已存在相同歌曲：$preTitle")
        }
        // ── 查重结束 ──────────────────────────────────────────────────────────

        val inputStream = try {
            resolver.openInputStream(sourceUri)
        } catch (e: Exception) {
            DebugLog.e(TAG, "openInputStream failed for $sourceUri", e)
            throw IOException("无法打开源文件: ${e.message}", e)
        }
        inputStream ?: throw IOException("ContentResolver 返回空流: $sourceUri")

        val id = UUID.randomUUID().toString()
        val ext = inferExtension(resolver, sourceUri)
        val destFile = File(baseDir, "$id.$ext")

        try {
            destFile.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "copy failed for $sourceUri -> ${destFile.absolutePath}", e)
            if (destFile.exists()) {
                runCatching { destFile.delete() }
                    .onFailure { DebugLog.e(TAG, "Failed to delete partial file", it) }
            }
            throw IOException("复制文件失败: ${e.message}", e)
        } finally {
            runCatching { inputStream.close() }
        }

        val retriever = MediaMetadataRetriever()
        var title = destFile.nameWithoutExtension
        var artist = ""
        var album = ""
        var durationMs = 0L
        try {
            retriever.setDataSource(destFile.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: destFile.nameWithoutExtension
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            DebugLog.e(TAG, "MediaMetadataRetriever failed for ${destFile.absolutePath}", e)
        } finally {
            retriever.release()
        }

        val track = LocalMusicTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            localPath = destFile.absolutePath,
            durationMs = durationMs
        )
        addTrack(track)
        track
    }

    fun addTrack(track: LocalMusicTrack) {
        val tracks = loadTracks().toMutableList()
        tracks.add(0, track)
        saveTracks(tracks)
    }

    fun deleteTrack(track: LocalMusicTrack): Boolean {
        val tracks = loadTracks().toMutableList()
        val removed = tracks.removeAll { it.id == track.id }
        if (removed) {
            saveTracks(tracks)
            try {
                File(track.localPath).delete()
            } catch (e: Exception) {
                DebugLog.e(TAG, "deleteTrack file failed for ${track.localPath}", e)
            }
        }
        return removed
    }

    fun searchTracks(query: String): List<LocalMusicTrack> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return loadTracks()
        return loadTracks().filter { track ->
            track.title.lowercase().contains(q) ||
                track.artist.lowercase().contains(q) ||
                track.album.lowercase().contains(q)
        }
    }

    private fun inferExtension(resolver: ContentResolver, uri: Uri): String {
        val mime = resolver.getType(uri)
        val fromMime = when (mime) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/aac" -> "aac"
            "audio/wav" -> "wav"
            "audio/flac" -> "flac"
            "audio/ogg" -> "ogg"
            "audio/x-m4a", "audio/mp4" -> "m4a"
            else -> null
        }
        if (fromMime != null) return fromMime

        val displayName = try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "query displayName failed", e)
            null
        }
        val fromDisplay = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
        if (fromDisplay != null) return fromDisplay

        return "mp3"
    }

    companion object {
        private const val TAG = "LocalMusicRepository"
    }
}

class DuplicateTrackException(message: String) : Exception(message)
