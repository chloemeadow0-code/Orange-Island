package com.orangeisland.app.data.local

import android.content.Context
import com.orangeisland.app.util.DebugLog
import java.io.File
import java.util.UUID

/**
 * Stores large text fields (message text / thoughts) that exceed the safe SQLite row threshold
 * in external files, keeping the DB row small enough to avoid CursorWindow crashes.
 *
 * Pointer format: `oi-overflow://v1/<relative-file-name>` — this scheme is extremely unlikely
 * to collide with real user content or model output.
 */
object LargeTextStore {

    /** Single-field threshold: anything larger than this gets offloaded to a file. */
    const val THRESHOLD_CHARS = 50 * 1024 // 50 KB (character count)

    private const val POINTER_SCHEME = "oi-overflow://v1/"
    private const val DIR_NAME = "text_overflow"

    /** Encode [content] for storage: if it exceeds [THRESHOLD_CHARS], write to a file and return a pointer. */
    fun encode(context: Context, ownerId: String, field: String, content: String?): String? {
        if (content == null) return null
        if (content.length <= THRESHOLD_CHARS) return content

        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val fileName = "${ownerId}_${field}_${UUID.randomUUID()}.txt"
        val file = File(dir, fileName)
        return try {
            file.writeText(content, Charsets.UTF_8)
            POINTER_SCHEME + fileName
        } catch (e: Exception) {
            DebugLog.e(
                "LargeTextStore",
                "encode failed: ownerId=$ownerId field=$field contentLen=${content.length} file=${file.absolutePath}",
                e
            )
            // Fallback: return original content rather than silently dropping data.
            content
        }
    }

    /** Decode [stored]: if it is a pointer, read the file; otherwise return as-is. */
    fun decode(context: Context, stored: String?): String? {
        if (stored == null) return null
        if (!stored.startsWith(POINTER_SCHEME)) return stored

        val fileName = stored.removePrefix(POINTER_SCHEME)
        val file = File(File(context.filesDir, DIR_NAME), fileName)
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            DebugLog.e(
                "LargeTextStore",
                "decode failed: stored=$stored file=${file.absolutePath} exists=${file.exists()}",
                e
            )
            "[该消息内容因存储异常无法加载，请反馈]"
        }
    }

    /** Delete the overflow file if [stored] is a pointer. Non-existent files are not an error. */
    fun deleteIfOverflow(context: Context, stored: String?) {
        if (stored == null || !stored.startsWith(POINTER_SCHEME)) return
        val fileName = stored.removePrefix(POINTER_SCHEME)
        val file = File(File(context.filesDir, DIR_NAME), fileName)
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: Exception) {
                DebugLog.e(
                    "LargeTextStore",
                    "deleteIfOverflow failed: stored=$stored file=${file.absolutePath}",
                    e
                )
            }
        }
    }
}
