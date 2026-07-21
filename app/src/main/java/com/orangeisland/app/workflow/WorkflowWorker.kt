package com.orangeisland.app.workflow

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.ScheduleMode
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.tool.ToolDispatcher
import com.orangeisland.app.util.DebugLog
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Executes a scheduled workflow in the background via WorkManager.
 *
 * Scheduling mirrors [com.orangeisland.app.service.AutoBackupWorker]: each scheduled workflow
 * enqueues a unique WorkManager request (`workflow_<id>`), either periodic (Interval / repeating
 * CronLike) or one-time (OneShot / non-repeating CronLike). On fire, [doWork] rebuilds the
 * dependency graph from scratch (a Worker may run in a fresh process, so the app-wide
 * AppContainer singletons aren't assumed) and runs the workflow in [WorkflowRunner.Mode.BACKGROUND].
 *
 * Independent implementation.
 */
class WorkflowWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workflowId = inputData.getString(KEY_WORKFLOW_ID) ?: return Result.failure()
        val startNodeId = inputData.getString(KEY_START_NODE_ID)
        val appContext = applicationContext

        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val db = ChatDatabase.build(appContext)
        val repository = WorkflowRepository(db.workflowDao(), json)
        val settings = SettingsManager(appContext)
        // Background dispatcher: no permission controller (device tools limited by the guard's
        // background whitelist anyway), no MCP (lifted in a later stage), llmProviders empty.
        val dispatcher = ToolDispatcher(
            app = applicationContext as android.app.Application,
            conversations = com.orangeisland.app.data.repository.ConversationRepository(db.chatDao()),
            memoryManager = com.orangeisland.app.data.MemoryManager(appContext),
            llmProviders = emptyMap(),
            appContext = appContext,
            sandboxFactory = null,
            mcpPool = null,
            pluginToolProvider = null,
            permissionController = null
        )
        val runner = WorkflowRunner(repository, dispatcher, settings, json,
            contextProvider = com.orangeisland.app.workflow.linear.DeviceContextProvider(appContext))

        return try {
            val result = runner.run(
                workflowId = workflowId,
                mode = WorkflowRunner.Mode.BACKGROUND,
                source = if (startNodeId != null)
                    TriggerSource.Targeted.Node(kind = TriggerKind.SCHEDULE, nodeId = startNodeId)
                    else TriggerSource.Targeted.Node(kind = TriggerKind.SCHEDULE),
                startNodeId = startNodeId,
                triggerPayload = "{}"
            )
            // Retry on failure so a transient tool error gets a second chance; give up on
            // cancellation or guard-limit violations (retrying those wastes battery).
            when {
                result.message == "Cancelled" -> Result.success()
                result.success -> Result.success()
                result.message.contains("Limit exceeded", ignoreCase = true) -> Result.failure()
                else -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Background workflow run crashed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            // The Worker process may outlive the app, so close what we opened. Room + WorkManager
            // handle their own lifecycle; the dispatcher's pools are best-effort.
            runCatching { db.close() }
        }
    }

    companion object {
        private const val TAG = "WorkflowWorker"
        private const val MAX_ATTEMPTS = 3
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_START_NODE_ID = "start_node_id"

        private fun workName(workflowId: String) = "workflow_$workflowId"

        /**
         * Enqueue (or replace) the WorkManager request that fires [workflow] according to its
         * schedule trigger. No-op if [workflow] has no schedule trigger or is disabled.
         *
         * @return true if a request was scheduled, false otherwise (caller may log).
         */
        fun schedule(context: Context, workflow: Workflow): Boolean {
            val start = workflow.nodes.filterIsInstance<StartNode>().firstOrNull {
                it.trigger is TriggerSpec.Schedule
            } ?: return false
            if (!workflow.enabled) return false
            val trigger = start.trigger as TriggerSpec.Schedule
            val wm = WorkManager.getInstance(context)
            val name = workName(workflow.id)
            val data = workDataOf(
                KEY_WORKFLOW_ID to workflow.id,
                KEY_START_NODE_ID to start.id
            )
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val periodicMs = ScheduleCalculator.periodicIntervalMs(trigger)
            return when {
                // Periodic (Interval or repeating cron): replace so config edits take effect.
                periodicMs != null && trigger.mode !is ScheduleMode.OneShot -> {
                    val delay = ScheduleCalculator.nextDelayMs(trigger) ?: 0L
                    val request = PeriodicWorkRequestBuilder<WorkflowWorker>(
                        periodicMs, TimeUnit.MILLISECONDS
                    )
                        .setConstraints(constraints)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .setInputData(data)
                        .addTag(name)
                        .build()
                    wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
                    DebugLog.d(TAG, "Scheduled periodic workflow $name (interval=${periodicMs}ms delay=${delay}ms)")
                    true
                }
                // One-shot (OneShot or non-repeating cron): one-time with delay.
                else -> {
                    val delay = ScheduleCalculator.nextDelayMs(trigger) ?: return false
                    val request = OneTimeWorkRequestBuilder<WorkflowWorker>()
                        .setConstraints(constraints)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .setInputData(data)
                        .addTag(name)
                        .build()
                    wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
                    DebugLog.d(TAG, "Scheduled one-shot workflow $name (delay=${delay}ms)")
                    true
                }
            }
        }

        /** Cancel any pending WorkManager request for [workflowId]. */
        fun cancel(context: Context, workflowId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(workflowId))
        }

        /**
         * Re-schedule every enabled workflow's schedule trigger. Call once on app start so a
         * device reboot or app upgrade doesn't lose pending runs (WorkManager persists across
         * reboots, but new installs and `UPDATE` policy need a refresh).
         */
        suspend fun rescheduleAll(context: Context, repository: WorkflowRepository) {
            repository.getEnabled().forEach { wf ->
                runCatching { schedule(context, wf) }
            }
        }
    }
}
