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
 * WorkManager worker that fires a [LinearTrigger.BootCompleted] workflow after the device boots.
 * Enqueued by [BootSignalSource.onBoot]. Two modes:
 *  - input data carries a workflow id → fire that one.
 *  - input data is empty → cold-boot "discover" mode: load every enabled boot workflow from the
 *    repository and fire each (covers the race where BOOT_COMPLETED arrives before
 *    [BootSignalSource]'s flow has emitted).
 *
 * Rebuilds the dependency graph from scratch (a Worker may run in a fresh process). Runs the
 * workflow in [WorkflowRunner.Mode.BACKGROUND].
 *
 * Independent implementation.
 */
class BootFireWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        runCatching { setForeground(buildWorkerForegroundInfo(appContext, "boot")) }
            .onFailure { DebugLog.w(TAG, "setForeground failed", it) }

        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val db = ChatDatabase.build(appContext)
        return try {
            val repository = WorkflowRepository(db.workflowDao(), json)
            val ids = inputData.getString(KEY_WORKFLOW_ID)?.let { listOf(it) }
                ?: repository.getEnabledLinear()
                    .filter { it.trigger is LinearTrigger.BootCompleted }
                    .map { it.id }
            if (ids.isEmpty()) return Result.success()
            val settings = SettingsManager(appContext)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val settingsRepository = SettingsRepository(settings, scope)
            val llmProviders = WorkflowWorker.buildLlmProviders()
            // Sync custom providers into a registry so LLM nodes bound to a user-defined provider
            // resolve in background runs (see WorkflowWorker for rationale).
            val localProvider = com.orangeisland.app.api.local.LocalProvider(appContext, settingsRepository)
            val providerRegistry = com.orangeisland.app.viewmodel.ProviderRegistry(settingsRepository, localProvider, scope).also {
                it.ensureCustomProvidersRegistered()
            }
            val dispatcher = ToolDispatcher(
                app = appContext as android.app.Application,
                conversations = com.orangeisland.app.data.repository.ConversationRepository(db.chatDao()),
                memoryManager = com.orangeisland.app.data.MemoryManager(appContext),
                llmProviders = llmProviders,
                appContext = appContext,
                sandboxFactory = null,
                mcpPool = null,
                pluginToolProvider = null,
                permissionController = com.orangeisland.app.viewmodel.PermissionController(appContext),
                chatDao = db.chatDao()
            )
            val runner = WorkflowRunner(
                repository = repository,
                dispatcher = dispatcher,
                settings = settings,
                settingsRepository = settingsRepository,
                json = json,
                contextProvider = DeviceContextProvider(
                    context = appContext,
                    foregroundProvider = { com.orangeisland.app.workflow.trigger.AppForegroundDispatcher.lastKnown }
                ),
                llmProviders = llmProviders,
                providerRegistry = providerRegistry,
                chatDao = db.chatDao(),
                appContext = appContext
            )
            ids.forEach { id ->
                runCatching {
                    runner.run(
                        workflowId = id,
                        mode = WorkflowRunner.Mode.BACKGROUND,
                        source = TriggerSource.Targeted.Node(kind = TriggerKind.API)
                    )
                }.onFailure { DebugLog.e(TAG, "boot fire failed for $id", it) }
            }
            Result.success()
        } catch (e: Exception) {
            DebugLog.e(TAG, "boot worker crashed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            runCatching { db.close() }
        }
    }

    companion object {
        private const val TAG = "BootFireWorker"
        private const val MAX_ATTEMPTS = 3
        const val KEY_WORKFLOW_ID = "workflow_id"
        fun inputData(workflowId: String) = workDataOf(KEY_WORKFLOW_ID to workflowId)
    }
}
