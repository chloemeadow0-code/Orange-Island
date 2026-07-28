package com.orangeisland.app.workflow

import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.UsageLogManager
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.repository.WorkflowRepository
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.orangeisland.app.R
import com.orangeisland.app.data.local.ChatEntity
import com.orangeisland.app.data.local.MessageEntity
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
import kotlinx.coroutines.withTimeoutOrNull
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
    private val appContext: Context? = null,
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
        val runHardTimeoutMs = settings.workflowMaxRunMs.first()

        val guard = buildGuard(mode, startedAt)
        val ctx = buildContext(mode, workflow.projectId)
        val toolRunner = NodeExecutor.ToolRunner { name, args -> dispatcher.execute(name, args, ctx) }
        val llmRunner = buildLLMRunner(workflow)
        val notificationRunner = buildNotificationRunner()
        val chatMessageRunner = buildChatMessageRunner(workflow)

        val result = withTimeoutOrNull(runHardTimeoutMs) {
            engine.execute(
                workflow = workflow,
                triggerSource = resolvedSource,
                triggerPayload = triggerPayload,
                guard = guard,
                toolRunner = toolRunner,
                llmRunner = llmRunner,
                notificationRunner = notificationRunner,
                chatMessageRunner = chatMessageRunner,
                onState = { id, state -> onNodeState?.invoke(id, state) }
            )
        } ?: RunResult(
            workflowId = workflowId, runId = runId, success = false,
            message = "Run timed out after ${runHardTimeoutMs}ms",
            startedAt = startedAt, finishedAt = System.currentTimeMillis(),
            states = emptyMap(),
            logs = emptyList()
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
        val runHardTimeoutMs = settings.workflowMaxRunMs.first()
        val guard = buildGuard(mode, startedAt)
        val ctx = buildContext(mode, def.projectId)
        val linearEngine = LinearEngine(
            repository = repository,
            contextProvider = { contextProvider.snapshot() },
            toolRunner = LinearEngine.ToolRunner { action -> dispatcher.execute(action.tool, action.args.toString(), ctx) },
            guard = guard,
            runId = runId
        )
        val outcome = withTimeoutOrNull(runHardTimeoutMs) {
            linearEngine.fire(workflowId)
        } ?: run {
            // Total-timeout fallback: fire() didn't return (it normally records its own end via
            // recordLinearRunEnd), so record a FAILED end here to avoid a wedged RUNNING row.
            repository.recordLinearRunEnd(runId, com.orangeisland.app.model.LinearFireStatus.FAILED,
                "Run timed out after ${runHardTimeoutMs}ms", "")
            com.orangeisland.app.workflow.linear.LinearEngine.Outcome(
                com.orangeisland.app.model.LinearFireStatus.FAILED,
                "Run timed out after ${runHardTimeoutMs}ms", ""
            )
        }
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
                // Decode overflow pointers so workflow history contains real text.
                recent.reversed().forEach { raw ->
                    val msg = raw.decodeLargeText(appContext!!)
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

            val llmProvider = providerRegistry?.let { reg ->
                // Ensure custom providers (e.g. a user-defined OpenAI-compatible endpoint) are
                // registered into the live map before we resolve by name. In a freshly-started
                // background Worker the registry's registration is fired asynchronously in appScope;
                // if the LLM node resolves before it completes the provider is simply absent, and
                // getInstance() returns null. Awaiting registration here closes that race.
                reg.ensureCustomProvidersRegistered()
                reg.getInstance(effectiveProvider)
            } ?: llmProviders[effectiveProvider]
                ?: error("Provider '$effectiveProvider' is not registered")
            val apiKey = settingsRepository.awaitActiveKey(effectiveProvider).orEmpty()
            // Use .first() instead of .value: in a freshly-started Worker process the StateFlow's
            // initial value is an empty map (DataStore hasn't loaded yet), so .value would hand
            // back a null baseUrl, the custom provider would request an empty endpoint, and the
            // call 404s — even though the same provider works in chat. .first() suspends until the
            // real persisted value is available.
            val baseUrl = settingsRepository.providerBaseUrls.first()[effectiveProvider]
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
            firstError?.let {
                // Enrich the error so the workflow run log shows what was actually requested.
                error("$it [provider=$effectiveProvider model=$effectiveModelId baseUrl=${baseUrl ?: "<default>"}]")
            }
            sb.toString().trim()
        }
    }

    private suspend fun buildContext(mode: Mode, projectId: String? = null): GenerationContext {
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
            uiAutomationEnabled = mode == Mode.FOREGROUND,   // UI automation only in foreground
            // Inherit the workflow's bound project so action tools (memory/RAG scope, and any tool
            // that creates a conversation) operate inside the same project the user bound the
            // workflow to. Without this, conversations produced by workflow actions landed in the
            // ungrouped bucket even though the workflow carried a projectId.
            projectId = projectId
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

    private fun buildNotificationRunner(): NodeExecutor.NotificationRunner? {
        val context = appContext ?: return null
        return NodeExecutor.NotificationRunner { title, content, priority ->
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "workflow_notify"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Workflow Notify",
                    when (priority) {
                        "high" -> NotificationManager.IMPORTANCE_HIGH
                        "low" -> NotificationManager.IMPORTANCE_LOW
                        else -> NotificationManager.IMPORTANCE_DEFAULT
                    }
                ).apply { description = "Notifications sent by workflow nodes" }
                manager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
                .setContentText(content.ifBlank { "Workflow notification" })
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(
                    when (priority) {
                        "high" -> NotificationCompat.PRIORITY_HIGH
                        "low" -> NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    }
                )
                .setAutoCancel(true)
                .build()
            val notificationId = System.currentTimeMillis().toInt()
            manager.notify(notificationId, notification)
            "sent"
        }
    }

    private fun buildChatMessageRunner(workflow: Workflow): NodeExecutor.ChatMessageRunner? {
        val dao = chatDao ?: return null
        return NodeExecutor.ChatMessageRunner { text, participant ->
            val conversations = if (workflow.projectId != null) {
                dao.getConversationsInProject(workflow.projectId)
            } else {
                dao.getGlobalConversationsList()
            }
            val conversation = conversations.firstOrNull()
                ?: ChatEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    title = workflow.name.ifBlank { "Workflow" },
                    lastUpdated = System.currentTimeMillis(),
                    projectId = workflow.projectId
                ).also { dao.upsertConversation(it) }

            // Attach the message to the TAIL of the conversation's currently-selected visible
            // branch (not just the newest-by-timestamp message). The chat UI's visible path — and
            // therefore the AI's context — is built by ConversationUiState.resolvePath walking the
            // selectedBranchesJson map from the root. If we parent on the newest message instead,
            // the workflow message lands off the selected branch and the AI never sees it when the
            // user replies. Compute the same tail resolvePath would reach, parent on it, and then
            // register ourselves in selectedBranchesJson so we become the new tail.
            val allMsgs = dao.getMessagesForConversation(conversation.id).first()
                .map { it.decodeLargeText(appContext!!) }
            val branches = parseSelectedBranches(conversation.selectedBranchesJson)
            val parentId = visiblePathTailId(allMsgs, branches)

            val msgId = "msg_${java.util.UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val entity = MessageEntity(
                id = msgId,
                conversationId = conversation.id,
                parentId = parentId,
                text = text,
                participant = if (participant.uppercase() == "USER") Participant.USER else Participant.MODEL,
                status = MessageStatus.SUCCESS,
                timestamp = now
            ).encodeLargeText(appContext!!)
            dao.upsertMessage(entity)
            // Make this message the selected child of its parent so resolvePath walks into it.
            // Persist with the literal "null" key for a root-level (null-parent) entry — JSON keys
            // can't be null, which is the same convention ChatViewModel uses when loading/saving.
            val updatedBranches = branches.toMutableMap().apply { put(parentId, msgId) }
            val serializable = updatedBranches.mapKeys { (k, _) -> k ?: "null" }
            dao.upsertConversation(conversation.copy(
                selectedBranchesJson = json.encodeToString(serializable),
                lastUpdated = now
            ))
            msgId
        }
    }

    /** Parse the persisted selectedBranchesJson (parentId → selectedChildId). Tolerant of null /
     *  corrupt payloads — a bad map degrades to "no explicit selection", which is never worse than
     *  the legacy timestamp-based behaviour. The persisted map uses the literal string "null" for a
     *  root-level (null-parent) entry, since JSON keys can't be null. */
    private fun parseSelectedBranches(raw: String?): Map<String?, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(raw)
                .mapKeys { if (it.key == "null") null else it.key }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Returns the message id at the tail of the conversation's selected visible branch — i.e. the
     * exact node `ConversationUiState.resolvePath` would land on last. Mirrors that algorithm:
     *  - start at the root (parentId = null),
     *  - at each level pick the child named in [branches], or fall back to the newest-by-timestamp
     *    VISIBLE sibling (excluding tool_/result_ synthetic messages, which resolvePath hides),
     *  - stop when a level has no children.
     * Returns null for an empty conversation (the workflow message then becomes the root).
     */
    private fun visiblePathTailId(
        messages: List<MessageEntity>,
        branches: Map<String?, String>
    ): String? {
        var cursor: String? = null
        while (true) {
            val siblings = messages.filter { it.parentId == cursor }
            if (siblings.isEmpty()) break
            val visible = siblings.filter { !it.id.startsWith("tool_") && !it.id.startsWith("result_") }
            // Never descend into synthetic tool_/result_ nodes: if there are no visible
            // children the walk stops here.  This prevents workflow chat_message nodes
            // from parenting on hidden result_ messages, which would later break API
            // tool_use / tool_result pairing in GenerationManager.buildApiPath.
            if (visible.isEmpty()) break
            val selected = branches[cursor]?.let { sel -> visible.firstOrNull { it.id == sel } }
                ?: visible.maxByOrNull { it.timestamp }
                ?: break
            cursor = selected.id
        }
        return cursor
    }

    companion object {
        private val engine = WorkflowEngine()
    }
}
