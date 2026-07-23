package com.orangeisland.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A persisted workflow definition.
 *
 * The graph itself (nodes + edges) is stored as a single JSON blob in [graphJson] rather than
 * being normalized across rows. Workflows are read and written as whole units — there is no
 * query that ever needs to select a single node or edge across workflows — so a blob keeps the
 * schema simple, the migrations trivial, and avoids an O(nodes) row fan-out on every load. The
 * JSON shape is defined by [com.orangeisland.app.model.Workflow] and serialized with the shared
 * kotlinx.serialization Json instance.
 *
 * Denormalized run statistics ([lastRunAt], [lastRunStatus], [totalRuns], [successRuns],
 * [failedRuns]) are mirrored from [WorkflowRunEntity] so the list screen can render a card
 * without a second query. They are updated by the repository at the end of each run.
 */
@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val graphJson: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long? = null,
    val lastRunStatus: String? = null,   // RunStatus.name — SUCCESS / FAILED / RUNNING / CANCELLED
    val totalRuns: Int = 0,
    val successRuns: Int = 0,
    val failedRuns: Int = 0,
    /** "graph" (node-and-edge, the original engine) or "linear" (trigger+conditions+actions, AI-authored).
     *  Defaults to "graph" so every workflow created before v15 keeps working through the graph engine. */
    val mode: String = "graph",
    /** Minimum gap between two consecutive fires, in ms. Linear mode only; 0 = no cooldown. */
    val cooldownMs: Long = 0,
    /** Max fires per local day. Linear mode only; null = unlimited. */
    val maxRunsPerDay: Int? = null,
    /** Mirrored daily-fire counter (only SUCCESS/FAILED count). Reset when [runsTodayDate] changes. */
    val runsTodayCount: Int = 0,
    /** ISO date string (yyyy-MM-dd) the counter belongs to. */
    val runsTodayDate: String = "",
    /** Bound project id — when set, the workflow's LLM nodes can see this project's recent chat history. */
    val projectId: String? = null,
    /** Bound system prompt id — resolved at runtime from SettingsRepository. */
    val systemPromptId: String? = null,
    /** Bound model id (format "provider:modelId") — overrides LLMNode defaults when set. */
    val modelId: String? = null
)

/**
 * One execution of a workflow. A new row is inserted when a run starts (status = RUNNING) and
 * updated when it finishes. Rows are never deleted except by the parent workflow's CASCADE, so
 * the run history grows monotonically — the UI paginates/truncates it.
 *
 * [workflowName] is intentionally denormalized: a workflow may be renamed after a run, but the
 * historical record should keep the name it had when the run happened.
 *
 * [logsJson] holds the per-node log entries serialized as a JSON array (see
 * [com.orangeisland.app.workflow.RunLogEntry]); null until the run completes.
 */
@Entity(
    tableName = "workflow_runs",
    indices = [Index("workflowId")],
    foreignKeys = [
        ForeignKey(
            entity = WorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflowId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkflowRunEntity(
    @PrimaryKey val runId: String,
    val workflowId: String,
    val workflowName: String,
    val startNodeId: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,             // RunStatus.name
    val message: String,
    val logsJson: String? = null
)
