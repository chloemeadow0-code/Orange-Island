package com.orangeisland.app.workflow

import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.UsageLogManager
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.tool.ToolDispatcher
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.ProviderRegistry
import com.orangeisland.app.workflow.linear.DeviceContextProvider
import com.orangeisland.app.workflow.linear.LinearEngine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bridges the pure-JVM [WorkflowEngine] to the Android runtime: builds the [WorkflowGuard] from
 * user settings, constructs a [GenerationContext] for tool dispatch, drives the engine, and
 * records the run's lifecycle via [WorkflowRepository].
 *
 * Two execution modes map onto the guard's two confirmation policies:
 *  - [Mode.FOREGROUND]: launched from the UI (manual run, AI tool call). Destructive tools may
 *    prompt the user via [onConfirmDestructive]; the full tool set is allowed.
 *  - [Mode.BACKGROUND]: launched from WorkManager or the Intent receiver. Destructive tools are
 *    denied outright (no UI to confirm with), and when [SettingsManager.workflowBackgroundSafeOnly]
 *    is set only the read-only whitelist in [WorkflowGuard.BACKGROUND_SAFE_TOOLS] may run.
 *
 * Per-workflow serialization is delegated to a shared [Json] instance (passed in so the runner,
 * repository, and exporter all agree on the encoding).
 *
 * Independent implementation.
 *
 * @param onConfirmDestructive suspending gate for destructive tools in foreground mode. Null in
 *   background mode (the guard then denies destructive tools automatically).
 * @param onNodeState live per-node state callback; the UI subscribes to light up canvas cards.
 */
class WorkflowRunner(
    private val repository: WorkflowRepository,
    private val dispatcher: ToolDispatcher,
    private val settings: SettingsManager,
    private val settingsRepository: SettingsRepository? = null,
    private val json: Json,
    private val contextProvider: com.orangeisland.app.workflow.linear.DeviceContextProvider,
    private val providerRegistry: ProviderRegistry? = null,
    private val llmProviders: Map<String, LlmProvider> = emptyMap(),
    private val chatDao: com.orangeisland.app.data.local.ChatDao? = null,
    private val onConfirmDestructive: (suspend (toolName: String, args: String) -> Boolean)? = null,
    private val onNodeState: ((String, NodeState) -> Unit)? = null
) {
    enum class Mode { FOREGROUND, BACKGROUND }

    /**
     * Run [workflowId] and persist the outcome. Resolves the workflow from the repository, builds
     * the guard + context for [mode], drives the engine, then records start/end.
     *
     * Returns the engine's [RunResult] (also persisted). Throws only on cancellation that the
     * engine couldn't catch (shouldn't happen — the engine wraps everything in try/catch).
     */
    suspend fun run(
        workflowId: String,
        mode: Mode,
        source: TriggerSource,
        startNodeId: String? = null,
        triggerPayload: String = "{}"
    ): RunResult {
        // Dispatch by stored mode: linear workflows go through LinearEngine, graph workflows
        // through the original WorkflowEngine. A missing row is handled below.
        val storedMode = repository.modeOf(workflowId)
        if (storedMode == "linear") {
            return runLinear(workflowId, mode)
        }

        val workflow = repository.get(workflowId)
            ?: return failedResult(workflowId, "Workflow not found: $workflowId")
        if (!workflow.enabled) {
            recordQuick(workflowId, RunStatus.FAILED, "Workflow is disabled")
            return failedResult(workflowId, "Workflow is disabled")
        }

        val resolvedSource = resolveSource(source, startNodeId, workflow)
        val runId = repository.recordRunStart(workflowId, startNodeId)
        val startedAt = System.currentTimeMillis()

        val guard = buildGuard(mode, startedAt)
        val ctx = buildContext(mode)
        val toolRunner = NodeExecutor.ToolRunner { name, args -> dispatcher.execute(name, args, ctx) }
        val llmRunner = buildLLMRunner(workflow)

        val result = engine.execute(
            workflow = workflow,
            triggerSource = resolvedSource,
            triggerPayload = triggerPayload,
            guard = guard,
            toolRunner = toolRunner,
            llmRunner = llmRunner,
            onState = { id, state -> onNodeState?.invoke(id, state) }
        )

        val status = when {
            result.success -> RunStatus.SUCCESS
            result.message == "Cancelled" -> RunStatus.CANCELLED
            else -> RunStatus.FAILED
        }
        val logsJson = runCatching { json.encodeToString(result.logs) }.getOrNull()
        repository.recordRunEnd(runId, status, result.message, logsJson)
        return result
    }

    /** Linear-mode dispatch. Builds the same guard/context the graph path uses, then hands the
     *  cooldown/cap/condition/action flow to [LinearEngine]. */
    private suspend fun runLinear(workflowId: String, mode: Mode): RunResult {
        val def = repository.getLinear(workflowId)
            ?: return failedResult(workflowId, "Linear workflow not found: $workflowId")
        if (!def.enabled) {
            recordQuick(workflowId, RunStatus.FAILED, "Workflow is disabled")
            return failedResult(workflowId, "Workflow is disabled")
        }
        val runId = repository.recordLinearRunStart(workflowId)
        val startedAt = System.currentTimeMillis()
        val guard = buildGuard(mode, startedAt)
        val ctx = buildContext(mode)
        val linearEngine = LinearEngine(
            repository = repository,
            contextProvider = { contextProvider.snapshot() },
            toolRunner = LinearEngine.ToolRunner { action -> dispatcher.execute(action.tool, action.args.toString(), ctx) },
            guard = guard,
            runId = runId
        )
        val outcome = linearEngine.fire(workflowId)
        return RunResult(
            workflowId = workflowId, runId = runId,
            success = outcome.status == com.orangeisland.app.model.LinearFireStatus.SUCCESS,
            message = outcome.message,
            startedAt = startedAt, finishedAt = System.currentTimeMillis(),
            states = emptyMap(), logs = emptyList()
        )
        // Note: recordLinearRunEnd already ran inside LinearEngine.fire(), so unlike the graph
        // path we must NOT call repository.recordRunEnd here — that would double-count the run.
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** If the caller pinned a start node id, wrap the source so the engine matches by identity. */
    private fun resolveSource(source: TriggerSource, startNodeId: String?, workflow: Workflow): TriggerSource {
        if (startNodeId == null) return source
        // Find the pinned start's trigger kind to build a matching Targeted.Node.
        val kind = (workflow.nodes.firstOrNull { it.id == startNodeId } as? StartNode)?.trigger?.let(::kindOf)
            ?: return source
        return TriggerSource.Targeted.Node(kind = kind, nodeId = startNodeId)
    }

    private fun kindOf(spec: TriggerSpec): TriggerKind = when (spec) {
        is TriggerSpec.Schedule -> TriggerKind.SCHEDULE
        is TriggerSpec.IntentAction -> TriggerKind.INTENT
        is TriggerSpec.AppOpen -> TriggerKind.APP_OPEN
        is TriggerSpec.Voice -> TriggerKind.VOICE
        is TriggerSpec.Api -> TriggerKind.API
        is TriggerSpec.Manual -> TriggerKind.API   // pinned manual node — kind is irrelevant once nodeId is set
    }

    private suspend fun buildGuard(mode: Mode, startedAt: Long): WorkflowGuard {
        val maxRunMs = settings.workflowMaxRunMs.first()
        val maxCalls = settings.workflowMaxToolCalls.first()
        val backgroundSafeOnly = settings.workflowBackgroundSafeOnly.first()
        val isBackground = mode == Mode.BACKGROUND
        val confirm = if (isBackground) null else onConfirmDestructive
        return WorkflowGuard(
            startedAt = { startedAt },
            maxRunMs = maxRunMs,
            maxToolCalls = maxCalls,
            backgroundMode = isBackground,
            backgroundSafeOnly = backgroundSafeOnly,
            confirmDestructive = confirm
        )
    }

    /**
     * Build a GenerationContext for tool dispatch. Tools are gated by per-category enable flags,
     * so a workflow that calls `web_search` needs `webSearchEnabled = true`. Rather than make the
     * user configure every flag per workflow, foreground runs enable every category the workflow
     * might touch; background runs do the same but the guard's whitelist still blocks the
     * dangerous ones before dispatch. Sensitive credentials (API keys) are read from settings.
     */
    private fun buildLLMRunner(workflow: Workflow): NodeExecutor.LLMRunner? {
        // Prefer the dynamic ProviderRegistry (built-in + user custom providers); fall back to
        // the static llmProviders map in background workers that rebuild deps from scratch.
        if ((providerRegistry == null && llmProviders.isEmpty()) || settingsRepository == null) return null
        return NodeExecutor.LLMRunner { nodeProvider, nodeModelId, nodeSystemPrompt, prompt ->
            // ── Resolve overrides from workflow bindings ─────────────────────────
            val effectiveProvider: String
            val effectiveModelId: String
            val workflowModelId = workflow.modelId
            if (workflowModelId != null && ':' in workflowModelId) {
                val parts = workflowModelId.split(':', limit = 2)
                effectiveProvider = parts[0]
                effectiveModelId = parts[1]
            } else {
                effectiveProvider = nodeProvider
                effectiveModelId = nodeModelId
            }

            val effectiveSystemPrompt = workflow.systemPromptId?.let { spId ->
                settingsRepository.systemPrompts.value
                    .firstOrNull { it.id == spId }
                    ?.let { entry ->
                        buildString {
                            entry.resolvedSystemItems.forEach { appendLine(it.value) }
                        }.trim()
                    }
            } ?: nodeSystemPrompt

            // ── Build message list (project history + current prompt) ────────────
            val history = mutableListOf<ChatMessage>()
            val projectId = workflow.projectId
            if (projectId != null && chatDao != null) {
                val recent = kotlinx.coroutines.runBlocking {
                    chatDao.getRecentMessagesForProject(projectId, limit = 10)
                }
                // DAO returns DESC (newest first); reverse to ASC for chronological order.
                recent.reversed().forEach { msg ->
                    history += ChatMessage(
                        text = msg.text,
                        participant = when (msg.participant) {
                            com.orangeisland.app.model.Participant.MODEL -> Participant.MODEL
                            else -> Participant.USER
                        },
                        status = MessageStatus.SUCCESS
                    )
                }
            }
            history += ChatMessage(
                text = prompt,
                participant = Participant.USER,
                status = MessageStatus.SUCCESS
            )

            val llmProvider = providerRegistry?.getInstance(effectiveProvider)
                ?: llmProviders[effectiveProvider]
                ?: error("Provider '$effectiveProvider' not available")
            val apiKey = settingsRepository.awaitActiveKey(effectiveProvider).orEmpty()
            val baseUrl = settingsRepository.providerBaseUrls.value[effectiveProvider]
            val config = com.orangeisland.app.api.ProviderConfig(
                apiKey = apiKey,
                modelId = effectiveModelId,
                systemPrompt = effectiveSystemPrompt,
                baseUrl = baseUrl,
                temperature = 0.7f
            )
            UsageLogManager.logModel(
                name = "workflow / $effectiveProvider / $effectiveModelId",
                details = "history=${history.size - 1} | prompt=${prompt.length} chars"
            )
            val sb = StringBuilder()
            var firstError: String? = null
            val t0 = System.currentTimeMillis()
            llmProvider.generateResponse(history, config).collect { ev ->
                when (ev) {
                    is com.orangeisland.app.api.StreamEvent.TextChunk -> sb.append(ev.text)
                    is com.orangeisland.app.api.StreamEvent.Error -> {
                        if (firstError == null) firstError = ev.message
                    }
                    else -> {}
                }
            }
            val elapsed = System.currentTimeMillis() - t0
            UsageLogManager.logModel(
                name = "workflow / $effectiveProvider / $effectiveModelId ✓",
                details = "${elapsed}ms | output=${sb.length} chars"
            )
            firstError?.let { error(it) }
            sb.toString().trim()
        }
    }

    private suspend fun buildContext(mode: Mode): GenerationContext {
        val webKeys = settings.webSearchApiKeys.first()
        val webProvider = settings.webSearchProvider.first()
        val webNum = settings.webSearchNumResults.first()
        val webBase = settings.webSearchBaseUrl.first()
        val imageEnabled = settings.imageGenEnabled.first()
        val imageModel = settings.imageGenModel.first() ?: "gpt-image-1"
        val imageSize = settings.imageGenSize.first()
        return GenerationContext(
            accessSavedMemories = true,
            accessActiveMemory = true,
            accessPastConversations = true,
            webSearchEnabled = true,
            webSearchApiKeys = webKeys,
            webSearchProvider = webProvider,
            webSearchNumResults = webNum,
            webSearchBaseUrl = webBase,
            imageGenEnabled = imageEnabled,
            imageGenModel = imageModel,
            imageGenSize = imageSize,
            shellEnabled = true,
            deviceInfoEnabled = true,
            locationEnabled = true,
            calendarEnabled = true,
            notificationEnabled = true,
            usageStatsEnabled = true,
            navigationEnabled = true,
            appLockEnabled = true,
            toastEnabled = true,
            uiAutomationEnabled = mode == Mode.FOREGROUND   // UI automation only in foreground
        )
    }

    private fun failedResult(workflowId: String, message: String) = RunResult(
        workflowId = workflowId,
        runId = "none",
        success = false,
        message = message,
        startedAt = System.currentTimeMillis(),
        finishedAt = System.currentTimeMillis(),
        states = emptyMap(),
        logs = emptyList()
    )

    private suspend fun recordQuick(workflowId: String, status: RunStatus, message: String) {
        val runId = repository.recordRunStart(workflowId, null)
        repository.recordRunEnd(runId, status, message, null)
    }

    companion object {
        private val engine = WorkflowEngine()
    }
}
