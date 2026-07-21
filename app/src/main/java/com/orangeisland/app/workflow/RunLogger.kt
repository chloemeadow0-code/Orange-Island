package com.orangeisland.app.workflow

import kotlinx.serialization.Serializable

/**
 * One line in a workflow run's execution log. The engine appends these as it walks the graph;
 * the repository persists the full list as JSON in
 * [com.orangeisland.app.data.local.WorkflowRunEntity.logsJson], and the run-log screen renders it.
 *
 * Each entry is attributed to a node (by id + label) so the UI can correlate log lines back to
 * canvas cards.
 */
@Serializable
data class RunLogEntry(
    val level: Level,
    val message: String,
    val nodeId: String? = null,
    val nodeLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    @Serializable
    enum class Level { DEBUG, WARN, ERROR }
}

/**
 * Collector used during a single run. Holds the entries in memory; the repository snapshots
 * [entries] into a WorkflowRunEntity when the run ends. Not thread-safe — a run executes on one
 * coroutine and the engine drives the logger single-threaded.
 */
class RunLogger {
    private val _entries = mutableListOf<RunLogEntry>()
    val entries: List<RunLogEntry> get() = _entries.toList()

    fun debug(message: String, nodeId: String? = null, nodeLabel: String? = null) =
        append(RunLogEntry.Level.DEBUG, message, nodeId, nodeLabel)

    fun warn(message: String, nodeId: String? = null, nodeLabel: String? = null) =
        append(RunLogEntry.Level.WARN, message, nodeId, nodeLabel)

    fun error(message: String, nodeId: String? = null, nodeLabel: String? = null) =
        append(RunLogEntry.Level.ERROR, message, nodeId, nodeLabel)

    private fun append(level: RunLogEntry.Level, message: String, nodeId: String?, nodeLabel: String?) {
        _entries += RunLogEntry(level = level, message = message, nodeId = nodeId, nodeLabel = nodeLabel)
    }
}
