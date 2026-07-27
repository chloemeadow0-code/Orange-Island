package com.orangeisland.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [WorkflowEntity] and [WorkflowRunEntity].
 *
 * Mirrors the ChatDao conventions: `@Query`-returning `Flow` for reactive list reads, `@Upsert`
 * for insert-or-replace, plain `@Query DELETE` for removals. Run history is exposed as a Flow so
 * the run-log screen updates live as a background run progresses.
 */
@Dao
interface WorkflowDao {
    // ── Workflow definitions ─────────────────────────────────

    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    fun observeAllWorkflows(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    suspend fun getAllWorkflowsList(): List<WorkflowEntity>

    @Query("SELECT * FROM workflows WHERE id = :id LIMIT 1")
    suspend fun getWorkflow(id: String): WorkflowEntity?

    @Query("SELECT * FROM workflows WHERE enabled = 1")
    suspend fun getEnabledWorkflows(): List<WorkflowEntity>

    @Upsert
    suspend fun upsertWorkflow(workflow: WorkflowEntity)

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: String)

    // ── Run-statistics mirror (updated by WorkflowRepository at run end) ──

    @Query("""
        UPDATE workflows
        SET lastRunAt = :lastRunAt,
            lastRunStatus = :lastRunStatus,
            totalRuns = totalRuns + 1,
            successRuns = successRuns + :successDelta,
            failedRuns = failedRuns + :failedDelta,
            updatedAt = :lastRunAt
        WHERE id = :id
    """)
    suspend fun bumpRunStats(
        id: String,
        lastRunAt: Long,
        lastRunStatus: String,
        successDelta: Int,
        failedDelta: Int
    )

    @Query("UPDATE workflows SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    /** Linear-mode daily-counter update. Resets to 1 when the day changes, otherwise increments.
     *  Only called for SUCCESS/FAILED fires (skips don't count). */
    @Query("""
        UPDATE workflows
        SET runsTodayCount = CASE
                WHEN runsTodayDate = :today THEN runsTodayCount + 1
                ELSE 1
            END,
            runsTodayDate = :today,
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun bumpDailyCounter(id: String, today: String, now: Long)

    /** Linear-mode cooldown/daily-cap fields, updated when a definition is saved. */
    @Query("UPDATE workflows SET cooldownMs = :cooldownMs, maxRunsPerDay = :maxRunsPerDay, updatedAt = :now WHERE id = :id")
    suspend fun setLinearLimits(id: String, cooldownMs: Long, maxRunsPerDay: Int?, now: Long)

    /** Workflows of a given mode ("linear" or "graph"). Used by the trigger registry to sync only
     *  linear workflows (the ones that carry trigger metadata the registry reads). */
    @Query("SELECT * FROM workflows WHERE mode = :mode AND enabled = 1")
    suspend fun getEnabledByMode(mode: String): List<WorkflowEntity>

    /** Live stream of enabled workflows of [mode], newest first. Drives the trigger registry's
     *  re-sync whenever a linear workflow is added/edited/toggled. */
    @Query("SELECT * FROM workflows WHERE mode = :mode AND enabled = 1 ORDER BY updatedAt DESC")
    fun observeEnabledByMode(mode: String): Flow<List<WorkflowEntity>>

    // ── Run history ──────────────────────────────────────────

    @Upsert
    suspend fun upsertRun(run: WorkflowRunEntity)

    @Query("SELECT * FROM workflow_runs WHERE workflowId = :workflowId ORDER BY startedAt DESC")
    fun observeRunsForWorkflow(workflowId: String): Flow<List<WorkflowRunEntity>>

    @Query("SELECT * FROM workflow_runs WHERE workflowId = :workflowId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecentRuns(workflowId: String, limit: Int = 20): List<WorkflowRunEntity>

    @Query("SELECT * FROM workflow_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRun(runId: String): WorkflowRunEntity?

    @Query("DELETE FROM workflow_runs WHERE workflowId = :workflowId")
    suspend fun deleteRunsForWorkflow(workflowId: String)

    /** Mark every still-RUNNING run as FAILED. Called on app start: a run that is RUNNING across a
     *  process restart can never finish (its coroutine died with the process), so leaving it
     *  RUNNING would show a perpetual spinner in the run log. */
    @Query("UPDATE workflow_runs SET status = 'FAILED', finishedAt = :now, message = :message WHERE status = 'RUNNING'")
    suspend fun failStrandedRuns(now: Long, message: String)
}
