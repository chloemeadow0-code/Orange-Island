package com.orangeisland.app.workflow.trigger

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.tool.ToolDispatcher
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.workflow.TriggerSource
import com.orangeisland.app.workflow.TriggerKind
import com.orangeisland.app.workflow.WorkflowRunner
import com.orangeisland.app.workflow.WorkflowWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * WorkManager worker that fires a linear [LinearTrigger.TimeCron] workflow when its schedule
 * elapses. Mirrors [com.orangeisland.app.workflow.WorkflowWorker]: rebuilds the dependency graph
 * from scratch (a Worker may run in a fresh process, so app-wide singletons aren't assumed) and
 * runs the workflow in [WorkflowRunner.Mode.BACKGROUND].
 *
 * When the process is alive and [TimeTriggerFamily.get] is bound, the fire routes through the
 * family (which uses the registry's shared callback and re-enqueues one-shot schedules). When the
 * process is cold-started by WorkManager, the family isn't up yet, so the worker fires the
 * workflow directly via a freshly-built [WorkflowRunner] — exactly the same fallback
 * [WorkflowWorker] uses for graph-mode schedules.
 *
 * Independent implementation.
 */
class LinearTimeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workflowId = inputData.getString(KEY_WORKFLOW_ID) ?: return Result.failure()
        val appContext = applicationContext

        // Rebuild the dependency graph from scratch (a Worker may run in a fresh process, so the
        // app-wide AppContainer singletons aren't assumed) and run the workflow in BACKGROUND mode.
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val db = ChatDatabase.build(appContext)
        return try {
            val repository = WorkflowRepository(db.workflowDao(), json)
            val settings = SettingsManager(appContext)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val settingsRepository = SettingsRepository(settings, scope)
            val llmProviders = WorkflowWorker.buildLlmProviders()
            val dispatcher = ToolDispatcher(
                app = appContext as android.app.Application,
                conversations = com.orangeisland.app.data.repository.ConversationRepository(db.chatDao()),
                memoryManager = com.orangeisland.app.data.MemoryManager(appContext),
                llmProviders = llmProviders,
                appContext = appContext,
                sandboxFactory = null,
                mcpPool = null,
                pluginToolProvider = null,
                permissionController = null,
                chatDao = db.chatDao()
            )
            val runner = WorkflowRunner(
                repository = repository,
                dispatcher = dispatcher,
                settings = settings,
                settingsRepository = settingsRepository,
                json = json,
                contextProvider = com.orangeisland.app.workflow.linear.DeviceContextProvider(appContext),
                llmProviders = llmProviders
            )
            val def = repository.getLinear(workflowId)
            if (def == null || !def.enabled) return Result.success()
            if (def.trigger !is LinearTrigger.TimeCron) return Result.success()

            // Day-of-week gate (mirrors the family's check — the cold path doesn't use the family).
            if (!def.trigger.timeOfDay.isNullOrBlank() && def.trigger.daysOfWeek.isNotEmpty()) {
                val today = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()).dayOfWeek
                val allowed = def.trigger.daysOfWeek.mapNotNull { isoDayOfWeek(it) }.toSet()
                if (today !in allowed) {
                    DebugLog.d(TAG, "$workflowId cold-start skipped: $today not in days_of_week")
                    return Result.success()
                }
            }

            runner.run(
                workflowId = workflowId,
                mode = WorkflowRunner.Mode.BACKGROUND,
                source = TriggerSource.Targeted.Node(kind = TriggerKind.SCHEDULE)
            )
            Result.success()
        } catch (e: Exception) {
            DebugLog.e(TAG, "Linear time worker crashed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            runCatching { db.close() }
        }
    }

    private fun isoDayOfWeek(iso: Int): java.time.DayOfWeek? = when (iso) {
        1 -> java.time.DayOfWeek.MONDAY; 2 -> java.time.DayOfWeek.TUESDAY
        3 -> java.time.DayOfWeek.WEDNESDAY; 4 -> java.time.DayOfWeek.THURSDAY
        5 -> java.time.DayOfWeek.FRIDAY; 6 -> java.time.DayOfWeek.SATURDAY
        7 -> java.time.DayOfWeek.SUNDAY; else -> null
    }

    companion object {
        private const val TAG = "LinearTimeWorker"
        private const val MAX_ATTEMPTS = 3
        const val KEY_WORKFLOW_ID = "workflow_id"
        /** Unused by the worker itself but exported so a future caller can build input data. */
        fun inputData(workflowId: String) = workDataOf(KEY_WORKFLOW_ID to workflowId)
    }
}
