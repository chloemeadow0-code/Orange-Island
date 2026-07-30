package com.orangeisland.app.data

import android.content.Context
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory ring buffer for usage logs (model calls + tool calls).
 *
 * Logs are kept in a circular buffer capped at [MAX_SIZE] entries. When the buffer
 * is full the oldest entry is overwritten. This avoids disk I/O on the hot path
 * and keeps the UI snappy.
 *
 * Thread-safe: all mutating operations are synchronized on the internal buffer.
 */
object UsageLogManager {

    private const val TAG = "UsageLogManager"
    const val MAX_SIZE = 500

    @Serializable
    enum class Type { MODEL, TOOL, REQUEST, CONVERSATION, SYNC, SECURITY }

    @Serializable
    data class Entry(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val type: Type,
        val name: String,
        val conversationId: String? = null,
        val details: String = "",
        val isError: Boolean = false
    ) {
        val timeFormatted: String
            get() = TIME_FORMAT.format(Date(timestamp))
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var logFile: File
    private val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeChannel = Channel<Entry>(Channel.UNLIMITED)
    private val initialized = AtomicBoolean(false)

    /** Must be called once during app startup (e.g. in Application.onCreate()).
     *  Restores existing logs from disk and starts the background writer. */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        logFile = File(context.filesDir, "logs/usage_log.jsonl").also {
            it.parentFile?.mkdirs()
        }
        val restored = restoreFromDisk()
        synchronized(_entries) {
            _entries.value = restored
        }
        writeScope.launch {
            for (entry in writeChannel) {
                try {
                    logFile.appendText(json.encodeToString(entry) + "\n")
                    maybeTruncate()
                } catch (e: Exception) {
                    DebugLog.w(TAG, "Failed to persist log entry", e)
                }
            }
        }
    }

    private fun restoreFromDisk(): List<Entry> {
        if (!::logFile.isInitialized || !logFile.exists()) return emptyList()
        return try {
            logFile.readLines()
                .asReversed()
                .mapNotNull { line ->
                    runCatching { json.decodeFromString<Entry>(line.trim()) }.getOrNull()
                }
                .take(MAX_SIZE)
                .reversed()
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to restore logs from disk", e)
            emptyList()
        }
    }

    private fun maybeTruncate() {
        try {
            val lines = logFile.readLines()
            val limit = MAX_SIZE * 2
            if (lines.size > limit) {
                val keep = lines.takeLast(MAX_SIZE)
                logFile.writeText(keep.joinToString("\n") + if (keep.isNotEmpty()) "\n" else "")
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to truncate log file", e)
        }
    }

    /** Append a new log entry. If the buffer exceeds [MAX_SIZE], the oldest entries are dropped. */
    fun log(type: Type, name: String, conversationId: String? = null, details: String = "", isError: Boolean = false) {
        val safeName = sanitize(name)
        val safeDetails = sanitize(details)
        val entry = Entry(type = type, name = safeName, conversationId = conversationId, details = safeDetails, isError = isError)
        synchronized(_entries) {
            val current = _entries.value.toMutableList()
            current.add(entry)
            while (current.size > MAX_SIZE) {
                current.removeAt(0)
            }
            _entries.value = current
        }
        if (initialized.get()) {
            writeChannel.trySend(entry)
        }
    }

    /** Convenience shorthand for model calls. */
    fun logModel(name: String, conversationId: String? = null, details: String = "", isError: Boolean = false) {
        log(Type.MODEL, name, conversationId, details, isError)
    }

    /** Convenience shorthand for tool calls. */
    fun logTool(name: String, conversationId: String? = null, details: String = "", isError: Boolean = false) {
        log(Type.TOOL, name, conversationId, details, isError)
    }

    /** Clear all entries and the on-disk file. */
    fun clear() {
        synchronized(_entries) {
            _entries.value = emptyList()
        }
        if (initialized.get()) {
            try {
                if (::logFile.isInitialized) logFile.writeText("")
            } catch (e: Exception) {
                DebugLog.w(TAG, "Failed to clear log file", e)
            }
        }
    }

    /**
     * Strips common sensitive patterns from log strings before they reach the buffer or disk.
     * Applied automatically inside [log] to both [name] and [details].
     */
    private fun sanitize(s: String): String {
        return s
            .replace(Regex("""(?i)(sk-[a-z0-9]{20,})"""), "[REDACTED_KEY]")
            .replace(Regex("""(?i)(Bearer\s+[a-zA-Z0-9_\-\.]{20,})"""), "Bearer [REDACTED]")
            .replace(Regex("""(?i)(api[_-]?key\s*[=:]\s*)[a-zA-Z0-9_\-\.]{16,}"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(x-api-key\s*[=:]\s*)[a-zA-Z0-9_\-\.]{16,}"""), "$1[REDACTED]")
            .replace(Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""), "[REDACTED_EMAIL]")
    }
}
