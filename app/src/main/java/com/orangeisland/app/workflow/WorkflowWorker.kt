package com.orangeisland.app.workflow

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.anthropic.AnthropicProvider
import com.orangeisland.app.api.gemini.GeminiProvider
import com.orangeisland.app.api.ollama.OllamaProvider
import com.orangeisland.app.api.openai.DeepSeekProvider
import com.orangeisland.app.api.openai.OpenAiProvider
import com.orangeisland.app.api.openai.OpenRouterProvider
import com.orangeisland.app.api.openai.QwenProvider
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.ScheduleMode
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.tool.ToolDispatcher
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.workflow.trigger.buildWorkerForegroundInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
        val appContext = applicationContext
        runCatching { setForeground(buildWorkerForegroundInfo(appContext, "workflow")) }
            .onFailure { DebugLog.w(TAG, "setForeground failed", it) }

        val workflowId = inputData.getString(KEY_WORKFLOW_ID) ?: return Result.failure()
        val startNodeId = inputData.getString(KEY_START_NODE_ID)

        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val db = ChatDatabase.build(appContext)
        val repository = WorkflowRepository(db.workflowDao(), json)
        val settings = SettingsManager(appContext)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settingsRepository = SettingsRepository(settings, scope)
        val llmProviders = buildLlmProviders()
        // Custom providers (e.g. a user-defined provider like "亲亲老公") live in the dynamic
        // ProviderRegistry, not in the static buildLlmProviders() map. Without it, an LLM node bound
        // to a custom provider fails with "Provider '...' not available" in background runs, even
        // though the same provider works in chat. Rebuild the registry here the same way AppContainer
        // does, syncing persisted custom providers synchronously before the run starts.
        val localProvider = com.orangeisland.app.api.local.LocalProvider(appContext, settingsRepository)
        val providerRegistry = com.orangeisland.app.viewmodel.ProviderRegistry(settingsRepository, localProvider, scope).also {
            it.ensureCustomProvidersRegistered()
        }
        val dispatcher = ToolDispatcher(
            app = applicationContext as android.app.Application,
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
            contextProvider = com.orangeisland.app.workflow.linear.DeviceContextProvider(
                context = appContext,
                // Prefer the in-process value published by the accessibility service (accurate when
                // this process is alive); DeviceContextProvider falls back to UsageStatsManager when
                // it's null, which is the normal case in a fresh Worker process.
                foregroundProvider = { com.orangeisland.app.workflow.trigger.AppForegroundDispatcher.lastKnown }
            ),
            llmProviders = llmProviders,
            providerRegistry = providerRegistry,
            chatDao = db.chatDao(),
            appContext = appContext
        )

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

        /** Common tag applied to every scheduled graph-workflow request, so cold-start cleanup
         *  can list them all (WorkManager has no "list all unique names" API) and cancel the ones
         *  whose owning workflow no longer exists in the DB. */
        private const val TAG_ALL_SCHEDULE = "graph_workflow_schedule"

        /** Builds the built-in LLM provider map (no LocalProvider — it needs a ViewModelScope). */
        fun buildLlmProviders(): Map<String, LlmProvider> = mapOf(
            Constants.PROVIDER_GOOGLE to GeminiProvider(),
            Constants.PROVIDER_OPENAI to OpenAiProvider(),
            Constants.PROVIDER_ANTHROPIC to AnthropicProvider(),
            Constants.PROVIDER_DEEPSEEK to DeepSeekProvider(),
            Constants.PROVIDER_QWEN to QwenProvider(),
            Constants.PROVIDER_OLLAMA to OllamaProvider(),
            Constants.PROVIDER_OPEN_ROUTER to OpenRouterProvider()
        )
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
                .setRequiredNetworkType(NetworkType.CONNECTED)
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
                        .addTag(workflow.id)   // lets pruneOrphans resolve the owning workflow id
                        .addTag(TAG_ALL_SCHEDULE)  // common tag so cold-start can list every one
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
                        .addTag(workflow.id)
                        .addTag(TAG_ALL_SCHEDULE)
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
            val liveIds = repository.getEnabled().map { it.id }.toHashSet()
            liveIds.forEach { id ->
                runCatching { schedule(context, repository.get(id) ?: return@runCatching) }
            }
            pruneOrphans(context, liveIds)
        }

        /**
         * Cancel any WorkManager request tagged [TAG_ALL_SCHEDULE] whose owning workflow id is no
         * longer in [liveIds]. These are leftover periodic jobs from a workflow deleted at a time
         * when [cancel] wasn't called — without this they keep firing forever, failing on the
         * missing-row lookup, and burning battery. Safe on every cold start; idempotent.
         *
         * Each scheduled request carries three tags: the unique-name `workflow_<id>`, the raw
         * workflow id, and the common [TAG_ALL_SCHEDULE]. We list by the common tag, then for each
         * request pull out the raw-id tag; if that id isn't in [liveIds], cancel by unique name.
         */
        private suspend fun pruneOrphans(context: Context, liveIds: Set<String>) {
            withContext(Dispatchers.IO) {
                val wm = WorkManager.getInstance(context)
                runCatching {
                    wm.getWorkInfosByTagFlow(TAG_ALL_SCHEDULE).first().forEach { info ->
                        // The raw-id tag is whichever tag is the bare workflow id (the unique-name
                        // tag is the `workflow_`-prefixed form, and the common tag is the constant).
                        val ownerId = info.tags.firstOrNull { tag ->
                            tag != TAG_ALL_SCHEDULE && !tag.startsWith("workflow_") &&
                                tag !in liveIds && "workflow_$tag" in info.tags
                        } ?: return@forEach
                        wm.cancelUniqueWork("workflow_$ownerId")
                        DebugLog.d(TAG, "Pruned orphan graph workflow task: workflow_$ownerId")
                    }
                }.onFailure { DebugLog.w(TAG, "pruneOrphans failed", it) }
            }
        }
    }
}
