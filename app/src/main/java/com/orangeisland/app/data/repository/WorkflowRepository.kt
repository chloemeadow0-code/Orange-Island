package com.orangeisland.app.data.repository

import com.orangeisland.app.data.local.WorkflowDao
import com.orangeisland.app.data.local.WorkflowEntity
import com.orangeisland.app.data.local.WorkflowRunEntity
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.model.Workflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Persistence + run-state orchestration for [Workflow]s.
 *
 * Deliberately thin on execution: this repository owns the database surface (CRUD, run-statistics
 * mirror, run-history rows) but does **not** run the engine. The trigger layer (WorkflowWorker /
 * IntentReceiver / AiToolProvider, added in stage C) drives [WorkflowEngine] directly and reports
 * the outcome back here via [recordRunStart] / [recordRunEnd]. That split keeps this class
 * Android-free apart from the Room DAO, and lets the engine be unit-tested without a DB.
 *
 * Concurrency: per-workflow run serialization is the trigger layer's job (it tracks running jobs);
 * the repository itself is stateless across calls and safe to call concurrently �?each method is
 * one DAO transaction.
 *
 * Independent implementation. The graphJson-blob storage strategy, the denormalized run-stats
 * mirror, and the repository/runner split are Orange Island's own design.
 *
 * @param json the shared kotlinx.serialization instance with `ignoreUnknownKeys = true` and
 *   polymorphic [Workflow] support (the workflow/edge/nodevalue discriminators configured by the
 *   caller). Repository does not configure its own Json so serialization stays consistent with
 *   everywhere else workflows are encoded (e.g. DataExporter).
 */
class WorkflowRepository(
    private val dao: WorkflowDao,
    private val json: Json
) {
    // ── CRUD ────────────────────────────────────────────────────────────────

    /** Live stream of all workflows, newest first. Drives the list screen. */
    fun observeAll(): Flow<List<Workflow>> =
        dao.observeAllWorkflows().map { rows -> rows.map { toModel(it) } }

    suspend fun getAll(): List<Workflow> = withContext(Dispatchers.IO) {
        dao.getAllWorkflowsList().map { toModel(it) }
    }

    suspend fun get(id: String): Workflow? = withContext(Dispatchers.IO) {
        dao.getWorkflow(id)?.let { toModel(it) }
    }

    /** Insert or replace. Returns the saved model (with timestamps/ids filled in). */
    suspend fun upsert(workflow: Workflow): Workflow = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.getWorkflow(workflow.id)
        val row = WorkflowEntity(
            id = workflow.id,
            name = workflow.name,
            description = workflow.description,
            graphJson = json.encodeToString(workflow),
            enabled = workflow.enabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastRunAt = existing?.lastRunAt,
            lastRunStatus = existing?.lastRunStatus,
            totalRuns = existing?.totalRuns ?: 0,
            successRuns = existing?.successRuns ?: 0,
            failedRuns = existing?.failedRuns ?: 0
        )
        dao.upsertWorkflow(row)
        workflow
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteWorkflow(id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    /** All workflows whose [Workflow.enabled] is true �?used by the scheduler on app start. */
    suspend fun getEnabled(): List<Workflow> = withContext(Dispatchers.IO) {
        dao.getEnabledWorkflows().map { toModel(it) }
    }

    // ── Run lifecycle (called by the trigger layer) ─────────────────────────

    /** Create a RUNNING run row and return its id. The trigger layer passes this id to
     *  [recordRunEnd] when the engine finishes. */
    suspend fun recordRunStart(
        workflowId: String,
        startNodeId: String?
    ): String = withContext(Dispatchers.IO) {
        val wf = dao.getWorkflow(workflowId)
        val runId = "run_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        dao.upsertRun(
            WorkflowRunEntity(
                runId = runId,
                workflowId = workflowId,
                workflowName = wf?.name ?: "(deleted)",
                startNodeId = startNodeId,
                startedAt = now,
                finishedAt = null,
                status = RunStatus.RUNNING.name,
                message = "Running"
            )
        )
        runId
    }

    /** Finalize a run: update its row with outcome + logs, and bump the workflow's stats mirror. */
    suspend fun recordRunEnd(
        runId: String,
        status: RunStatus,
        message: String,
        logsJson: String?
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getRun(runId) ?: return@withContext
        val now = System.currentTimeMillis()
        dao.upsertRun(
            existing.copy(
                finishedAt = now,
                status = status.name,
                message = message,
                logsJson = logsJson
            )
        )
        when (status) {
            RunStatus.SUCCESS -> dao.bumpRunStats(existing.workflowId, now, status.name, successDelta = 1, failedDelta = 0)
            RunStatus.FAILED -> dao.bumpRunStats(existing.workflowId, now, status.name, successDelta = 0, failedDelta = 1)
            // CANCELLED runs count toward total but not success/failed (delta 0/0 still increments total).
            RunStatus.CANCELLED -> dao.bumpRunStats(existing.workflowId, now, status.name, successDelta = 0, failedDelta = 0)
            RunStatus.RUNNING -> Unit   // defensive; recordRunEnd should never be called with RUNNING
        }
    }

    // ── Run history ─────────────────────────────────────────────────────────

    fun observeRuns(workflowId: String): Flow<List<WorkflowRunEntity>> =
        dao.observeRunsForWorkflow(workflowId)

    suspend fun getRecentRuns(workflowId: String, limit: Int = 20): List<WorkflowRunEntity> =
        withContext(Dispatchers.IO) { dao.getRecentRuns(workflowId, limit) }

    suspend fun getRun(runId: String): WorkflowRunEntity? =
        withContext(Dispatchers.IO) { dao.getRun(runId) }

    // ── Serialization bridge ────────────────────────────────────────────────

    /** Decode a stored [WorkflowEntity.graphJson] back to a [Workflow] model. */
    fun decode(graphJson: String): Workflow =
        json.decodeFromString(graphJson)

    /** Inverse of [decode], for the exporter and any caller that needs the blob form. */
    fun encode(workflow: Workflow): String = json.encodeToString(workflow)

    private fun toModel(row: WorkflowEntity): Workflow = json.decodeFromString(row.graphJson)
}
