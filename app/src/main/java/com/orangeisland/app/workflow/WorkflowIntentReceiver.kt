package com.orangeisland.app.workflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.tool.ToolDispatcher
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Receives external Intent broadcasts and fires any workflow whose [StartNode] carries a matching
 * [TriggerSpec.IntentAction]. The Intent's extras become the run's trigger payload, available to
 * downstream nodes as the start node's output (a Transform node with JsonPath can extract fields).
 *
 * Security: declared in the manifest with `android:permission`, which requires senders to hold
 * [PERMISSION]. Because [PERMISSION] is `signature`-protected (defined in the same manifest), only
 * apps signed with the same key as Orange Island may send to it — so a malicious app cannot launch
 * a destructive workflow. Only same-key apps may trigger this receiver.
 *
 * Independent implementation.
 */
class WorkflowIntentReceiver : BroadcastReceiver() {

    /** Hook for tests; the real receiver builds a fresh runner per broadcast. */
    var runnerFactory: ((Context) -> WorkflowRunner)? = null

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val payload = extrasToPayload(intent)

        DebugLog.d(TAG, "Received intent action=$action payloadLen=${payload.length}")
        // goAsync() lets the receiver complete after onReceive returns; we cap work at a few
        // seconds via the runner's guard timeout, so the ANR window isn't a concern.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val runner = runnerFactory?.invoke(context) ?: buildRunner(context)
                val matches = findMatchingWorkflows(context, action)
                if (matches.isEmpty()) {
                    DebugLog.d(TAG, "No workflow matched action=$action")
                    return@launch
                }
                matches.forEach { (workflowId, startNodeId) ->
                    runCatching {
                        runner.run(
                            workflowId = workflowId,
                            mode = WorkflowRunner.Mode.BACKGROUND,
                            source = TriggerSource.Targeted.Node(
                                kind = TriggerKind.INTENT,
                                nodeId = startNodeId,
                                match = action
                            ),
                            startNodeId = startNodeId,
                            triggerPayload = payload
                        )
                    }
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Intent-triggered run failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Find (workflowId, startNodeId) pairs whose IntentAction trigger equals [action]. */
    private suspend fun findMatchingWorkflows(context: Context, action: String): List<Pair<String, String>> {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val repo = WorkflowRepository(ChatDatabase.build(context).workflowDao(), json)
        return repo.getEnabled().mapNotNull { wf ->
            val start = wf.nodes.filterIsInstance<StartNode>().firstOrNull { node ->
                (node.trigger as? TriggerSpec.IntentAction)?.action == action
            } ?: return@mapNotNull null
            wf.id to start.id
        }
    }

    /** Build a fresh background runner. Reuses the same wiring as WorkflowWorker. */
    private fun buildRunner(context: Context): WorkflowRunner {
        val appContext = context.applicationContext
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val db = ChatDatabase.build(appContext)
        val repository = WorkflowRepository(db.workflowDao(), json)
        val settings = SettingsManager(appContext)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val settingsRepository = com.orangeisland.app.data.repository.SettingsRepository(settings, scope)
        val llmProviders = WorkflowWorker.buildLlmProviders()
        // Sync custom providers into a registry so LLM nodes bound to a user-defined provider
        // resolve in background runs (see WorkflowWorker for rationale).
        val localProvider = com.orangeisland.app.api.local.LocalProvider(appContext, settingsRepository)
        val providerRegistry = com.orangeisland.app.viewmodel.ProviderRegistry(settingsRepository, localProvider, scope).also { reg ->
            // runBlocking: this builds the runner synchronously in onReceive; ensureCustomProvidersRegistered
            // is suspend (waits for DataStore) so we block briefly to register custom providers with
            // their real base URLs before the run starts.
            kotlinx.coroutines.runBlocking { reg.ensureCustomProvidersRegistered() }
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
        return WorkflowRunner(
            repository = repository,
            dispatcher = dispatcher,
            settings = settings,
            settingsRepository = settingsRepository,
            json = json,
            contextProvider = com.orangeisland.app.workflow.linear.DeviceContextProvider(
                context = appContext,
                foregroundProvider = { com.orangeisland.app.workflow.trigger.AppForegroundDispatcher.lastKnown }
            ),
            llmProviders = llmProviders,
            providerRegistry = providerRegistry
        )
    }

    /** Flatten intent extras into a JSON object string the start node can hand to downstream. */
    private fun extrasToPayload(intent: Intent): String {
        val obj = JSONObject()
        intent.extras?.keySet()?.forEach { key ->
            obj.put(key, intent.extras?.get(key)?.toString() ?: "")
        }
        return obj.toString()
    }

    companion object {
        private const val TAG = "WorkflowIntentReceiver"

        /** The signature permission senders must hold to broadcast to this receiver. */
        const val PERMISSION = "com.orangeisland.app.permission.TRIGGER_WORKFLOW"

        /** The default action a sender can use when the workflow didn't configure a custom one. */
        const val DEFAULT_ACTION = "com.orangeisland.app.TRIGGER_WORKFLOW"
    }
}
