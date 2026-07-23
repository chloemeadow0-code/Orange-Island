package com.orangeisland.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

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

    const val MAX_SIZE = 500

    enum class Type { MODEL, TOOL }

    data class Entry(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val type: Type,
        val name: String,
        val conversationId: String? = null,
        val details: String = ""
    ) {
        val timeFormatted: String
            get() = TIME_FORMAT.format(java.util.Date(timestamp))
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** Append a new log entry. If the buffer exceeds [MAX_SIZE], the oldest entries are dropped. */
    fun log(type: Type, name: String, conversationId: String? = null, details: String = "") {
        synchronized(_entries) {
            val current = _entries.value.toMutableList()
            current.add(Entry(type = type, name = name, conversationId = conversationId, details = details))
            while (current.size > MAX_SIZE) {
                current.removeAt(0)
            }
            _entries.value = current
        }
    }

    /** Convenience shorthand for model calls. */
    fun logModel(name: String, conversationId: String? = null, details: String = "") {
        log(Type.MODEL, name, conversationId, details)
    }

    /** Convenience shorthand for tool calls. */
    fun logTool(name: String, conversationId: String? = null, details: String = "") {
        log(Type.TOOL, name, conversationId, details)
    }

    /** Clear all entries. */
    fun clear() {
        synchronized(_entries) {
            _entries.value = emptyList()
        }
    }
}
