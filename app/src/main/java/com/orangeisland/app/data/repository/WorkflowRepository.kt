package com.orangeisland.app.data.repository

import com.orangeisland.app.data.local.WorkflowDao
import com.orangeisland.app.data.local.WorkflowEntity
import com.orangeisland.app.data.local.WorkflowRunEntity
import com.orangeisland.app.model.LinearFireStatus
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.model.Workflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
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
 * the repository itself is stateless across calls and safe to call concurrently â€?each method is
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
    // â”€â”€ CRUD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    /** Cheap mode probe ("graph" | "linear" | null) ¡ª lets the runner dispatch without decoding. */
    suspend fun modeOf(id: String): String? = withContext(Dispatchers.IO) {
        dao.getWorkflow(id)?.mode
    }

    /** All workflows whose [Workflow.enabled] is true â€?used by the scheduler on app start. */
    suspend fun getEnabled(): List<Workflow> = withContext(Dispatchers.IO) {
        dao.getEnabledWorkflows().map { toModel(it) }
    }

    // â”€â”€ Run lifecycle (called by the trigger layer) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ Run history â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun observeRuns(workflowId: String): Flow<List<WorkflowRunEntity>> =
        dao.observeRunsForWorkflow(workflowId)

    suspend fun getRecentRuns(workflowId: String, limit: Int = 20): List<WorkflowRunEntity> =
        withContext(Dispatchers.IO) { dao.getRecentRuns(workflowId, limit) }

    suspend fun getRun(runId: String): WorkflowRunEntity? =
        withContext(Dispatchers.IO) { dao.getRun(runId) }

    // â”€â”€ Serialization bridge â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Decode a stored [WorkflowEntity.graphJson] back to a [Workflow] model. */
    fun decode(graphJson: String): Workflow =
        json.decodeFromString(graphJson)

    /** Inverse of [decode], for the exporter and any caller that needs the blob form. */
    fun encode(workflow: Workflow): String = json.encodeToString(workflow)

    private fun toModel(row: WorkflowEntity): Workflow = json.decodeFromString<Workflow>(row.graphJson).copy(
        lastRunAt = row.lastRunAt,
        lastRunStatus = row.lastRunStatus,
        totalRuns = row.totalRuns,
        successRuns = row.successRuns,
        failedRuns = row.failedRuns
    )

    // ©¤©¤ Linear workflows (v2) ©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤
    // Linear definitions live in the same `workflows` table, distinguished by mode = "linear".
    // The graphJson blob holds the serialized [LinearWorkflow] instead of a graph [Workflow].
    // This keeps a single table, single observable, and single run-history stream for both modes.

    /** Insert or replace a linear workflow. Stamps mode = "linear" and the cooldown/cap fields. */
    suspend fun upsertLinear(workflow: LinearWorkflow): LinearWorkflow = withContext(Dispatchers.IO) {
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
            failedRuns = existing?.failedRuns ?: 0,
            mode = "linear",
            cooldownMs = workflow.cooldownMs,
            maxRunsPerDay = workflow.maxRunsPerDay,
            runsTodayCount = existing?.runsTodayCount ?: 0,
            runsTodayDate = existing?.runsTodayDate ?: ""
        )
        dao.upsertWorkflow(row)
        workflow.copy(createdAt = row.createdAt, updatedAt = row.updatedAt)
    }

    /** Decode a linear workflow by id. Returns null if missing or if the row is graph-mode. */
    suspend fun getLinear(id: String): LinearWorkflow? = withContext(Dispatchers.IO) {
        val row = dao.getWorkflow(id) ?: return@withContext null
        if (row.mode != "linear") return@withContext null
        json.decodeFromString<LinearWorkflow>(row.graphJson)
    }

    /** All enabled linear workflows ¡ª used by [com.orangeisland.app.workflow.trigger.TriggerRegistry]
     *  to sync OS-level listeners on app start and whenever the set changes. */
    suspend fun getEnabledLinear(): List<LinearWorkflow> = withContext(Dispatchers.IO) {
        dao.getEnabledByMode("linear").map { row ->
            json.decodeFromString<LinearWorkflow>(row.graphJson)
        }
    }

    /** Live stream of enabled linear workflows. Drives the trigger registry's re-sync. */
    fun observeEnabledLinear(): kotlinx.coroutines.flow.Flow<List<LinearWorkflow>> =
        dao.observeEnabledByMode("linear").map { rows ->
            rows.map { row -> json.decodeFromString<LinearWorkflow>(row.graphJson) }
        }

    /** All linear workflows (enabled + disabled), newest first ¡ª drives the v2 list screen.
     *  Rows carry the run-statistics mirror (lastRunAt / lastRunStatus / counts) the way graph-mode
     *  [Workflow]s do, copied from the entity so the list card renders without a second query. */
    fun observeAllLinear(): kotlinx.coroutines.flow.Flow<List<LinearWorkflow>> =
        dao.observeAllWorkflows().map { rows ->
            rows.filter { it.mode == "linear" }
                .map { row ->
                    json.decodeFromString<LinearWorkflow>(row.graphJson).copy(
                        lastRunAt = row.lastRunAt,
                        lastRunStatus = row.lastRunStatus
                    )
                }
        }

    /** One row of the linear run history, with the v2 richer status (SKIPPED_* gates). Kept as a
     *  thin wrapper over [WorkflowRunEntity] so both modes share the same history table/screen. */
    suspend fun recordLinearRunStart(workflowId: String, startNodeId: String? = null): String =
        recordRunStart(workflowId, startNodeId)

    /** Finalize a linear run. [status] is the linear engine's richer status; we map SKIPPED_* to
     *  the graph [RunStatus] for the shared history table, and bump the daily counter only for
     *  SUCCESS/FAILED (skips don't count toward the cap). */
    suspend fun recordLinearRunEnd(
        runId: String,
        status: LinearFireStatus,
        message: String,
        logsJson: String?
    ) = withContext(Dispatchers.IO) {
        val row = dao.getRun(runId) ?: return@withContext
        val now = System.currentTimeMillis()
        // Map to the shared RunStatus for the history table.
        val mapped = when (status) {
            LinearFireStatus.SUCCESS -> RunStatus.SUCCESS
            LinearFireStatus.FAILED -> RunStatus.FAILED
            // Skips are recorded as the workflow's last status but not as a failure the UI would
            // flag red ¡ª CANCELLED renders neutrally.
            LinearFireStatus.SKIPPED_CONDITIONS,
            LinearFireStatus.SKIPPED_COOLDOWN,
            LinearFireStatus.SKIPPED_DAILY_CAP,
            LinearFireStatus.SKIPPED_DISABLED -> RunStatus.CANCELLED
        }
        dao.upsertRun(row.copy(finishedAt = now, status = mapped.name, message = message, logsJson = logsJson))
        when (status) {
            LinearFireStatus.SUCCESS -> {
                dao.bumpRunStats(row.workflowId, now, mapped.name, successDelta = 1, failedDelta = 0)
                dao.bumpDailyCounter(row.workflowId, todayIso(), now)
            }
            LinearFireStatus.FAILED -> {
                dao.bumpRunStats(row.workflowId, now, mapped.name, successDelta = 0, failedDelta = 1)
                dao.bumpDailyCounter(row.workflowId, todayIso(), now)
            }
            else -> {
                // Skips: update lastRun timestamp/status so the list reflects the most recent fire,
                // but don't touch totals or the daily counter.
                dao.bumpRunStats(row.workflowId, now, mapped.name, successDelta = 0, failedDelta = 0)
            }
        }
    }

    /** Cooldown check: the timestamp of the most recent SUCCESS/FAILED fire for [workflowId], or
     *  null if there has never been an actual fire. Used by the linear engine's cooldown gate. */
    suspend fun lastActualFireAtMs(workflowId: String): Long? = withContext(Dispatchers.IO) {
        dao.getRecentRuns(workflowId, 50)
            .filter { it.status == RunStatus.SUCCESS.name || it.status == RunStatus.FAILED.name }
            .maxOfOrNull { it.finishedAt ?: it.startedAt }
    }

    /** Today's fire count (SUCCESS + FAILED) for the daily-cap gate. Resets when the stored date
     *  no longer matches today. */
    suspend fun runsTodayCount(workflowId: String): Int = withContext(Dispatchers.IO) {
        val row = dao.getWorkflow(workflowId) ?: return@withContext 0
        if (row.runsTodayDate == todayIso()) row.runsTodayCount else 0
    }

    private fun todayIso(): String = LocalDate.now(ZoneId.systemDefault()).toString()
}
