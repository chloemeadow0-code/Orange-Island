package com.orangeisland.app.workflow.trigger

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.tool.ToolDispatcher
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.workflow.TriggerKind
import com.orangeisland.app.workflow.TriggerSource
import com.orangeisland.app.workflow.WorkflowRunner
import com.orangeisland.app.workflow.WorkflowWorker
import com.orangeisland.app.workflow.linear.DeviceContextProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * WorkManager worker that fires a geofence-triggered workflow after a Play Services transition.
 * Enqueued by the play-flavor [com.orangeisland.app.workflow.geofence.PlayGeofenceReceiver]. The
 * workflow id + direction are encoded in the geofence requestId (see
 * [GeofenceSignalSource.encodeRequestId]), so the worker just decodes, loads the workflow, and
 * fires it if its stored trigger direction matches.
 *
 * Rebuilds the dependency graph from scratch (a Worker may run in a fresh process). Runs the
 * workflow in [WorkflowRunner.Mode.BACKGROUND]. The engine re-checks enabled / cooldown /
 * conditions, so a stale geofence (workflow disabled since the fence was registered) is a no-op.
 *
 * Independent implementation.
 */
class GeofenceFireWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        runCatching { setForeground(buildWorkerForegroundInfo(appContext, "geofence")) }
            .onFailure { DebugLog.w(TAG, "setForeground failed", it) }

        val requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure()
        val (workflowId, direction) = GeofenceSignalSource.decodeRequestId(requestId) ?: return Result.failure()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val db = ChatDatabase.build(appContext)
        return try {
            val repository = WorkflowRepository(db.workflowDao(), json)
            val wf = repository.getLinear(workflowId) ?: return Result.success()
            if (!wf.enabled) return Result.success()
            // Direction match: a geofence registered for ENTER must not fire on EXIT (and vice
            // versa). The requestId encodes the original direction; the stored trigger is the
            // source of truth for the current definition.
            val matches = when (direction) {
                GeofenceProvider.Direction.ENTER -> wf.trigger is LinearTrigger.GeofenceEnter
                GeofenceProvider.Direction.EXIT -> wf.trigger is LinearTrigger.GeofenceExit
            }
            if (!matches) return Result.success()

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
                contextProvider = DeviceContextProvider(appContext),
                llmProviders = llmProviders
            )
            runCatching {
                runner.run(
                    workflowId = workflowId,
                    mode = WorkflowRunner.Mode.BACKGROUND,
                    source = TriggerSource.Targeted.Node(kind = TriggerKind.API)
                )
            }.onFailure { DebugLog.e(TAG, "geofence fire failed for $workflowId", it) }
            Result.success()
        } catch (e: Exception) {
            DebugLog.e(TAG, "geofence worker crashed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            runCatching { db.close() }
        }
    }

    companion object {
        private const val TAG = "GeofenceFireWorker"
        private const val MAX_ATTEMPTS = 3
        const val KEY_REQUEST_ID = "request_id"

        /** Enqueue a fire for [requestId] (the geofence requestId, encoded workflow#direction). */
        fun enqueue(context: Context, requestId: String) {
            val req = OneTimeWorkRequestBuilder<GeofenceFireWorker>()
                .setInputData(workDataOf(KEY_REQUEST_ID to requestId))
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueue(req)
            }
        }
    }
}
