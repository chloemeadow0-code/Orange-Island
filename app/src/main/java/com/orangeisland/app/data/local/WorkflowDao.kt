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
}
