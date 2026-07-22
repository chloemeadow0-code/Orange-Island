package com.orangeisland.app.viewmodel

import android.app.Application
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.ProviderConfig
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.data.MemoryManager

import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.ToolCallData
import com.orangeisland.app.R
import com.orangeisland.app.service.OrangeIslandForegroundService
import com.orangeisland.app.service.AppForegroundTracker
import com.orangeisland.app.api.util.projectAssistantImagesToLatestUserMessage
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.SearchResultFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class GenerationConfig(
    val providerName: String,
    val modelId: String,
    val apiKey: String,
    val effectiveSystemPrompt: String?,
    val maxContextWindow: Int,
    val codeExecutionEnabled: Boolean,
    val googleSearchEnabled: Boolean,
    val thinkingEnabled: Boolean,
    val thinkingLevel: String = "medium",
    val thinkingBudgetEnabled: Boolean = false,
    val thinkingBudgetTokens: Int = 4096,
    val baseUrl: String?,
    val userPrepend: String? = null,
    val userPostpend: String? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null
)

data class GenerationContext(
    val conversationId: String? = null,
    val accessSavedMemories: Boolean = true,
    val accessActiveMemory: Boolean = true,
    val accessPastConversations: Boolean = true,
    val modelSearchMethod: String = "keyword",
    val activeEmbeddingConfig: com.orangeisland.app.data.EmbeddingModelConfig? = null,
    val embeddingApiKey: String = "",
    val ragThreshold: Float = 0.5f,
    val searchMatchLimit: Int = 10,
    val searchContextWindow: Int = 8,
    val webSearchEnabled: Boolean = false,
    val webSearchApiKeys: Map<String, String> = emptyMap(),
    val webSearchProvider: String = "duckduckgo",
    val webSearchNumResults: Int = 5,
    val webSearchBaseUrl: String = "",
    val imageGenEnabled: Boolean = false,
    val imageGenApiKey: String = "",
    val imageGenBaseUrl: String = "",
    val imageGenModel: String = "gpt-image-1",
    val imageGenSize: String = "1024x1024",
    val shellEnabled: Boolean = false,
    val shellDevices: List<com.orangeisland.app.data.ShellDeviceConfig> = emptyList(),
    val sandboxEnabled: Boolean = false,
    val imageTranscriptionEnabled: Boolean = false,
    val imageTranscriptionModel: String? = null,
    val imageTranscriptionBatchSize: Int = 3,
    val imageTranscriptionPrompt: String = com.orangeisland.app.data.BuiltInPrompts.IMAGE_TRANSCRIPTION_USER,
    val transcriptionProviderName: String = "",
    val transcriptionModelId: String = "",
    val transcriptionApiKey: String = "",
    val transcriptionBaseUrl: String? = null,
    /** All configured MCP servers (resolved at request-build time so the provider doesn't
     *  read DataStore on the hot path). [McpToolProvider] filters these by [mcpServerIds]. */
    val mcpServers: List<com.orangeisland.app.data.McpServerConfig> = emptyList(),
    /** Per-conversation MCP activation (null = use all globally enabled servers,
     *  empty = disable MCP for this turn, non-empty = exactly these server ids). */
    val mcpServerIds: List<String>? = null,
    /** Per-conversation JS-plugin activation. Same semantics as [mcpServerIds] but for
     *  locally-installed JS plugins (resolved by [PluginToolProvider]). */
    val pluginIds: List<String>? = null,
    // ── Device Access tools (each gated by its own setting + runtime permission) ──
    val deviceInfoEnabled: Boolean = false,
    val locationEnabled: Boolean = false,
    val amapApiKey: String = "",
    val calendarEnabled: Boolean = false,
    val notificationEnabled: Boolean = false,
    val usageStatsEnabled: Boolean = false,
    val navigationEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val toastEnabled: Boolean = false,
    val uiAutomationEnabled: Boolean = false,
    /** The project this conversation belongs to (null = ungrouped). Drives memory scoping:
     *  when non-null, memory tools read/write the project-private memory dir on top of the
     *  always-present global dir; RAG/search filters to the same project. */
    val projectId: String? = null
)

internal fun applyUserTemplateToMessages(
    messages: List<ChatMessage>,
    prepend: String?,
    postpend: String?
): List<ChatMessage> {
    if (prepend == null && postpend == null) return messages
    val timeSdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    val dateSdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return messages.map { msg ->
        val isToolMessage = msg.id.startsWith(Constants.TOOL_MSG_PREFIX) ||
            msg.id.startsWith(Constants.RESULT_MSG_PREFIX)
        if (!isToolMessage && msg.participant == Participant.USER && msg.text.isNotEmpty()) {
            val ts = java.util.Date(msg.timestamp)
            val rp = prepend?.replace("{sent_time}", timeSdf.format(ts))?.replace("{sent_date}", dateSdf.format(ts)) ?: ""
            val ra = postpend?.replace("{sent_time}", timeSdf.format(ts))?.replace("{sent_date}", dateSdf.format(ts)) ?: ""
            if (rp.isEmpty() && ra.isEmpty()) msg
            else msg.copy(text = rp + msg.text + ra)
        } else msg
    }
}

/**
 * The token-gated UI callbacks a single generation drives. Built once per call by
 * [GenerationSession.callbacksFor], so each generation entry point ([ChatViewModel]'s
 * send / regenerate / edit) wires the session ownership tokens in exactly one place
 * instead of re-threading five lambdas by hand.
 */
data class GenerationCallbacks(
    val onStreamUpdate: (ChatMessage) -> Unit,
    val onLoadingChange: (Boolean) -> Unit,
    val onGeneratingIdChange: (String?) -> Unit,
    val onStreamClear: () -> Unit,
    val isLatestPersist: () -> Boolean,
    val onTitleTriggerReady: ((String, String) -> Unit)? = null,
)

class GenerationManager(
    private val app: Application,
    private val conversations: com.orangeisland.app.data.repository.ConversationRepository,
    private val memoryManager: MemoryManager,
    private val providers: Map<String, LlmProvider>,
    private val context: android.content.Context,
    private val sandboxFactory: com.orangeisland.app.sandbox.SandboxManagerFactory? = null,
    /** Optional MCP client pool. When null, MCP tools are disabled (e.g. during title
     *  generation where external tools should not run). */
    private val mcpPool: com.orangeisland.app.mcp.McpClientPool? = null,
    /** Optional JS-plugin tool provider. When null, plugin tools are disabled. */
    private val pluginToolProvider: com.orangeisland.app.plugin.PluginToolProvider? = null,
    /** Permission state for the Device Access tools. Null during title generation
     *  (no device tools run there) — passed in for real chat from ChatViewModel. */
    private val permissionController: com.orangeisland.app.viewmodel.PermissionController? = null,
    /** Central tool dispatcher. Owns the 16 tool providers and routes execute() calls. When null
     *  (legacy/title-generation path), a private dispatcher is constructed inline so this manager
     *  keeps working standalone. Real chat receives the app-wide singleton from AppContainer. */
    private val toolDispatcher: com.orangeisland.app.tool.ToolDispatcher? = null,
    /** Optional Workflow AI tool provider (workflow_list/get/run/create/update/delete/set_enabled).
     *  Threaded into the standalone dispatcher built when [toolDispatcher] is null. When null itself
     *  (e.g. title generation), the LLM never sees workflow tools. Ignored when [toolDispatcher] is
     *  non-null — that dispatcher carries its own provider wiring. */
    private val workflowToolProvider: com.orangeisland.app.workflow.WorkflowAiToolProvider? = null
) {
    var onMessagePersisted: ((messageId: String, text: String) -> Unit)? = null

    /**
     * Resolves the active dispatcher: the injected app-wide one when available, otherwise a private
     * fallback wired with this manager's own dependencies (preserves the pre-refactor standalone
     * behaviour used by title generation and other non-chat callers).
     */
    private val tools: com.orangeisland.app.tool.ToolDispatcher = toolDispatcher ?: buildStandaloneDispatcher()

    private fun buildStandaloneDispatcher(): com.orangeisland.app.tool.ToolDispatcher =
        com.orangeisland.app.tool.ToolDispatcher(
            app = app,
            conversations = conversations,
            memoryManager = memoryManager,
            llmProviders = providers,
            appContext = context,
            sandboxFactory = sandboxFactory,
            mcpPool = mcpPool,
            pluginToolProvider = pluginToolProvider,
            permissionController = permissionController,
            workflowToolProvider = workflowToolProvider
        )

    init {
        // Forward the shell-confirmation gate to whichever dispatcher is in use. Read lazily inside
        // the dispatcher's own forwarding closure, so updates to this var after construction land.
        tools.onConfirmShellCommand = { server, summary -> onConfirmShellCommand?.invoke(server, summary) ?: true }
    }

    /** User-confirmation gate for remote shell mutations. Set by the ViewModel.
     *  Returns true to proceed, false to deny. */
    var onConfirmShellCommand: (suspend (server: String, summary: String) -> Boolean)? = null

    // The 16 tool providers and the dispatch list previously declared here now live in
    // [com.orangeisland.app.tool.ToolDispatcher], exposed via [tools]. The per-category builders
    // below delegate to it; direct execute() calls go through [executeTool].

    fun buildImageGenTool(ctx: GenerationContext): List<ToolDefinition> =
        tools.imageGenDefinitions(ctx)

    private val transcriptionManager = TranscriptionManager(providers, conversations, context)

    private fun getProviderInstance(name: String): LlmProvider =
        providers[name] ?: providers.values.first()

    // Image/video frame extraction lives in ImageProcessor (single source of truth).
    private val imageProcessor = ImageProcessor(app)

    suspend fun processImages(
        uris: List<String>,
        sliceConfigs: Map<String, VideoSliceConfig> = emptyMap()
    ): List<String> = imageProcessor.processImagesAndVideos(uris, sliceConfigs)

    fun buildMemoryTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.memoryDefinitions(ctx)

    fun buildWebSearchTool(ctx: GenerationContext): List<ToolDefinition> =
        tools.webSearchDefinitions(ctx)

    fun buildRagTool(ctx: GenerationContext): List<ToolDefinition> =
        tools.ragDefinitions(ctx)

    fun buildShellTool(ctx: GenerationContext): List<ToolDefinition> =
        tools.shellDefinitions(ctx)

    fun buildFileTool(ctx: GenerationContext): List<ToolDefinition> =
        tools.fileDefinitions(ctx)

    /** Tools exposed by active remote MCP servers. Empty when MCP is disabled or no servers
     *  are configured/active for this conversation. */
    fun buildMcpTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.mcpDefinitions(ctx)

    /** Tools exposed by active JS plugins. Empty when no plugins are installed/active. */
    fun buildPluginTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.pluginDefinitions(ctx)

    /** Navigation tools (open URL, open app, open settings, share text, list installed apps).
     *  Internally checks [GenerationContext.navigationEnabled]. */
    fun buildNavigationTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.navigationDefinitions(ctx)

    /** App Lock tools (set_pin, lock_app, unlock_app, list_locked_apps).
     *  Internally checks [GenerationContext.appLockEnabled]. */
    fun buildAppLockTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.appLockDefinitions(ctx)

    /** Toast tool (show_toast). Internally checks [GenerationContext.toastEnabled]. */
    fun buildToastTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.toastDefinitions(ctx)

    /** UI automation tools (ui_tap/ui_swipe/ui_scroll/ui_global_action/ui_inspect).
     *  Internally checks [GenerationContext.automationEnabled]. */
    fun buildAutomationTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.automationDefinitions(ctx)

    /** Workflow tools (workflow_list/get/run/create/update/delete/set_enabled). Lets the model read,
     *  fire, and AI-author linear workflows. Empty when the workflow feature is not wired into this
     *  GenerationManager (e.g. title generation). */
    fun buildWorkflowTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.workflowDefinitions(ctx)

    /** Device access tools (battery, location, calendar, notifications, usage stats).
     *  Each provider internally checks its own enable flag in [GenerationContext]. */
    fun buildDeviceTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.deviceDefinitions(ctx)

    /** Semantic message search — delegates to the RAG provider via [tools], which owns the
     *  embedding-search logic. Kept here as the entry point used by ChatViewModel's
     *  in-app conversation search. */
    suspend fun semanticSearch(query: String, limit: Int, ctx: GenerationContext): List<Pair<MessageEntity, Float>> =
        tools.semanticSearch(query, limit, ctx)

    private suspend fun executeTool(name: String, arguments: String, ctx: GenerationContext): String =
        tools.execute(name, arguments, ctx)

    private fun applyUserTemplate(messages: List<ChatMessage>, prepend: String?, postpend: String?): List<ChatMessage> {
        return applyUserTemplateToMessages(messages, prepend, postpend)
    }

    private fun appendMergedSegment(target: MutableList<MessageSegment>, segment: MessageSegment) {
        val last = target.lastOrNull()
        if (last != null && last.type == segment.type && (segment.type == "answer" || segment.type == "thought")) {
            target[target.lastIndex] = last.copy(
                content = last.content + segment.content,
                signature = segment.signature ?: last.signature,
                durationMs = mergeDurationMs(last.durationMs, segment.durationMs)
            )
        } else {
            target.add(segment)
        }
    }

    private fun mergeDurationMs(first: Long?, second: Long?): Long? {
        val merged = (first ?: 0L) + (second ?: 0L)
        return merged.takeIf { it > 0L }
    }

    private fun buildLiveSegments(
        flushed: List<MessageSegment>,
        answerBuf: StringBuilder,
        thoughtBuf: StringBuilder,
        signature: String? = null,
        thoughtDurationMs: Long? = null
    ): List<MessageSegment>? {
        val result = flushed.toMutableList()
        if (answerBuf.isNotEmpty()) {
            appendMergedSegment(result, MessageSegment(type = "answer", content = answerBuf.toString()))
        }
        if (thoughtBuf.isNotEmpty()) {
            appendMergedSegment(result, MessageSegment(
                type = "thought",
                content = thoughtBuf.toString(),
                signature = signature,
                durationMs = thoughtDurationMs
            ))
        }
        return result.ifEmpty { null }
    }

    private suspend fun buildApiPath(
        parentId: String?,
        conversationId: String,
        isRegenerate: Boolean,
        replaceMessageId: String?,
        config: GenerationConfig,
        ctx: GenerationContext,
        cancellationToken: Long
    ): Pair<List<ChatMessage>, ProviderConfig> {
        val dbMessages = conversations.getMessagesForConversationSnapshot(conversationId)
        val pathEntities = mutableListOf<MessageEntity>()
        var currId: String? = parentId
        while (currId != null) {
            val msg = dbMessages.find { it.id == currId } ?: break
            pathEntities.add(0, msg)
            currId = msg.parentId
        }
        // Inject tool call chains that are children of messages in the ancestor path.
        val expanded = mutableListOf<MessageEntity>()
        for (entity in pathEntities) {
            val toolChildren = dbMessages
                .filter { it.parentId == entity.id && it.id.startsWith(Constants.TOOL_MSG_PREFIX) }
                .sortedBy { it.timestamp }
            if (toolChildren.isEmpty()) {
                expanded.add(entity)
            } else {
                for (toolMsg in toolChildren) {
                    expanded.add(toolMsg)
                    val pending = mutableListOf(toolMsg)
                    var safety = 0
                    while (pending.isNotEmpty() && safety < 100) {
                        val current = pending.removeAt(0)
                        val children = dbMessages
                            .filter { it.parentId == current.id && (it.id.startsWith(Constants.RESULT_MSG_PREFIX) || it.id.startsWith(Constants.TOOL_MSG_PREFIX)) }
                            .sortedBy { it.timestamp }
                        for (child in children) {
                            val isResult = child.id.startsWith(Constants.RESULT_MSG_PREFIX)
                            if (isResult) {
                                // Include result_ messages so providers can emit
                                // correct tool_use/tool_result pairs. The result
                                // data lives in TOOL_MSG segments too, but Anthropic
                                // requires separate tool_result blocks in the next
                                // user-role message.
                                if (child !in expanded) {
                                    expanded.add(child)
                                }
                                pending.add(child)
                            } else if (child !in expanded) {
                                expanded.add(child)
                                pending.add(child)
                            }
                        }
                        safety++
                    }
                }
                expanded.add(entity.copy(toolCallJson = null))
            }
        }
        val currentPath = expanded.map {
            val segs = it.toolCallJson?.let { json -> try { Json.decodeFromString<List<MessageSegment>>(json) } catch (_: Exception) { null } }
            val toolCall = segs?.lastOrNull { s -> s.type == "tool" }?.let { s ->
                ToolCallData(s.toolName ?: "", s.toolArgs ?: "{}", s.toolResult ?: "", s.toolCallId)
            }
            val meta = it.attachmentMeta?.let { json -> try { Json.decodeFromString<com.orangeisland.app.model.AttachmentMeta>(json) } catch (_: Exception) { null } }
            val attachmentText = if (meta != null) {
                meta.items.mapNotNull { item ->
                    val content = item.textContent
                    val transcription = item.transcription
                    val includeTranscription = ctx.imageTranscriptionEnabled && transcription != null && transcription.isNotBlank()
                    when {
                        content != null -> {
                            val label = item.fileName ?: "file"
                            "\n\n--- File: $label ---\n$content"
                        }
                        includeTranscription -> {
                            val label = item.fileName ?: "image"
                            "\n\n--- Image Transcription: $label ---\n$transcription"
                        }
                        else -> null
                    }
                }.joinToString("")
            } else ""
            val combinedText = if (attachmentText.isNotBlank()) it.text + attachmentText else it.text
            val hasTranscription = ctx.imageTranscriptionEnabled && meta != null && meta.items.any { item -> !item.transcription.isNullOrBlank() }
            val effectiveImages = if (hasTranscription) emptyList() else it.images
            ChatMessage(id = it.id, parentId = it.parentId, text = combinedText, images = effectiveImages, thoughts = it.thoughts, thoughtTitle = it.thoughtTitle, tokenCount = it.tokenCount, status = it.status, participant = it.participant, timestamp = it.timestamp, thoughtTimeMs = it.thoughtTimeMs, segments = segs, toolCall = toolCall)
        }.filter { it.participant != Participant.ERROR }
            .let { path ->
                if (isRegenerate && replaceMessageId != null) {
                    val oldIdx = path.indexOfFirst { it.id == replaceMessageId }
                    if (oldIdx >= 0) path.take(oldIdx) else path
                } else path
            }

        val memoryTools = buildMemoryTools(ctx)
        val webSearchTool = buildWebSearchTool(ctx)
        val ragTool = buildRagTool(ctx)
        val shellTool = buildShellTool(ctx)
        val fileTool = buildFileTool(ctx)
        val imageGenTool = buildImageGenTool(ctx)
        val mcpTools = buildMcpTools(ctx)
        val pluginTools = buildPluginTools(ctx)
        val deviceTools = buildDeviceTools(ctx)
        val navigationTools = buildNavigationTools(ctx)
        val appLockTools = buildAppLockTools(ctx)
        val toastTools = buildToastTools(ctx)
        val automationTools = buildAutomationTools(ctx)
        val workflowTools = buildWorkflowTools(ctx)
        val allTools = memoryTools + webSearchTool + ragTool + imageGenTool + shellTool + fileTool + mcpTools + pluginTools + deviceTools + navigationTools + appLockTools + toastTools + automationTools + workflowTools
        val providerConfig = ProviderConfig(
            apiKey = config.apiKey,
            modelId = config.modelId,
            systemPrompt = config.effectiveSystemPrompt,
            maxContextWindow = config.maxContextWindow,
            codeExecutionEnabled = config.codeExecutionEnabled,
            googleSearchEnabled = config.googleSearchEnabled,
            thinkingEnabled = config.thinkingEnabled,
            thinkingLevel = config.thinkingLevel,
            thinkingBudgetEnabled = config.thinkingBudgetEnabled,
            thinkingBudgetTokens = config.thinkingBudgetTokens,
            baseUrl = config.baseUrl,
            tools = allTools,
            userPrepend = config.userPrepend,
            userPostpend = config.userPostpend,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
            frequencyPenalty = config.frequencyPenalty,
            presencePenalty = config.presencePenalty,
            cancellationToken = cancellationToken
        )
        return Pair(currentPath, providerConfig)
    }

    suspend fun generate(
        conversationId: String,
        modelMessageId: String,
        startTime: Long,
        isRegenerate: Boolean,
        replaceMessageId: String?,
        modelName: String,
        config: GenerationConfig,
        ctx: GenerationContext,
        generationJob: kotlinx.coroutines.Job?,
        callbacks: GenerationCallbacks,
        session: GenerationSession? = null
    ) {
        // Destructure into locals so the body below reads exactly as before.
        val (onStreamUpdate, onLoadingChange, onGeneratingIdChange, onStreamClear, isLatestPersist, onTitleTriggerReady) = callbacks
        val provider = getProviderInstance(config.providerName)

        onLoadingChange(true)
        onGeneratingIdChange(conversationId)
        com.orangeisland.app.util.CrashReporter.note("generate provider=${config.providerName} regen=$isRegenerate")
        withContext(Dispatchers.Main) { OrangeIslandForegroundService.start(app) }

        val cancellationToken = com.orangeisland.app.api.HttpClient.newCancellationToken()
        // Register the token on the session so the Stop button (session.stop →
        // stopInternal) can flag it via HttpClient.cancelToken, covering the gap
        // between tool-call rounds when activeStreamHandle is briefly null.
        session?.currentCancellationToken = cancellationToken

        var totalText = ""
        var totalThoughts = ""
        val thinkingPlaceholder = context.getString(R.string.thinking_ellipsis)
        var totalThoughtTitle: String? = null
        var totalTokenCount = 0
        var totalThoughtTimeMs: Long? = null
        var cumulativeThoughtMs: Long = 0
        var currentThoughtStartMs: Long? = null
        var currentThoughtDurationMs: Long = 0
        var currentStatus = MessageStatus.SENDING
        var retryText: String? = null
        val segments = mutableListOf(MessageSegment(type = "answer"))
        val generatedImages = mutableListOf<String>()
        var currentAnswerBuf = StringBuilder()
        var currentThoughtBuf = StringBuilder()
        var currentThoughtSignature: String? = null
        val placeholder = conversations.getMessagesForConversationSnapshot(conversationId).find { it.id == modelMessageId }
        val parentId = placeholder?.parentId
        var toolPath = emptyList<ChatMessage>()
        var titleTriggerFired = false
        var streamStartMs = 0L

        fun liveThoughtDurationMs(): Long? {
            val liveElapsed = currentThoughtStartMs?.let { System.currentTimeMillis() - it } ?: 0L
            return (currentThoughtDurationMs + liveElapsed).takeIf { it > 0L }
        }

        fun finishCurrentThoughtTiming() {
            val startedAt = currentThoughtStartMs ?: return
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed > 0L) {
                cumulativeThoughtMs += elapsed
                currentThoughtDurationMs += elapsed
                totalThoughtTimeMs = cumulativeThoughtMs
            }
            currentThoughtStartMs = null
        }

        try {
            // Stage 1: Image Transcription
            var transcriptionPerformed = false
            if (ctx.imageTranscriptionEnabled && ctx.transcriptionModelId.isNotEmpty()) {
                kotlinx.coroutines.delay(500) // let foreground service fully start
                val targets = transcriptionManager.collectTargets(conversationId, parentId)
                if (targets.isNotEmpty()) {
                    val (transcriptionSegments, transcriptionError) = transcriptionManager.transcribe(
                        targets, conversationId,
                        ctx.transcriptionProviderName, ctx.transcriptionModelId,
                        ctx.transcriptionApiKey, ctx.transcriptionBaseUrl,
                        ctx.imageTranscriptionPrompt,
                        generationJob, modelMessageId, startTime, onStreamUpdate
                    )
                    if (transcriptionError != null) {
                        totalText = transcriptionError
                        currentStatus = MessageStatus.ERROR
                        transcriptionPerformed = true
                    } else {
                        segments.addAll(0, transcriptionSegments)
                        transcriptionPerformed = true
                    }
                }
            }

            if (currentStatus != MessageStatus.ERROR) {
            // Re-scan the plugins directory so freshly-installed/uninstalled plugins are visible
            // to this turn without an app restart. Cheap: reads manifest.json files only.
            val tRefresh = System.currentTimeMillis()
            tools.refreshPlugins()
            DebugLog.d("GenPerf", "refreshPlugins: ${System.currentTimeMillis() - tRefresh}ms")

            val tApiPath = System.currentTimeMillis()
            val (currentPath, rawProviderConfig) = buildApiPath(parentId, conversationId, isRegenerate, replaceMessageId, config, ctx, cancellationToken)
            DebugLog.d("GenPerf", "buildApiPath: ${System.currentTimeMillis() - tApiPath}ms, pathSize=${currentPath.size}, toolCount=${rawProviderConfig.tools?.size ?: 0}")
            val providerConfig = if (transcriptionPerformed) rawProviderConfig.copy(includeImages = false) else rawProviderConfig

            var toolCallData: ToolCallData? = null
            var toolCallDataList: List<ToolCallData> = emptyList()
            val roundToolSegments = mutableListOf<MessageSegment>()

            var lastEmitMs = 0L

            fun modelMessage() = ChatMessage(
                id = modelMessageId, parentId = parentId,
                text = totalText, thoughts = totalThoughts.ifBlank { null },
                thoughtTitle = totalThoughtTitle, tokenCount = totalTokenCount,
                status = currentStatus, participant = Participant.MODEL,
                timestamp = startTime, thoughtTimeMs = totalThoughtTimeMs,
                modelName = modelName, toolCall = toolCallData,
                images = generatedImages.toList(),
                segments = buildLiveSegments(
                    segments,
                    currentAnswerBuf,
                    currentThoughtBuf,
                    currentThoughtSignature,
                    liveThoughtDurationMs()
                ),
                retryText = retryText
            )

            fun flushAnswerSegment() {
                if (currentAnswerBuf.isNotEmpty()) {
                    appendMergedSegment(segments, MessageSegment(type = "answer", content = currentAnswerBuf.toString()))
                    currentAnswerBuf = StringBuilder()
                }
            }

            fun flushThoughtSegment() {
                finishCurrentThoughtTiming()
                if (currentThoughtBuf.isNotEmpty()) {
                    appendMergedSegment(segments, MessageSegment(
                        type = "thought",
                        content = currentThoughtBuf.toString(),
                        signature = currentThoughtSignature,
                        durationMs = currentThoughtDurationMs.takeIf { it > 0L }
                    ))
                    currentThoughtBuf = StringBuilder()
                    currentThoughtSignature = null
                }
                currentThoughtDurationMs = 0L
            }

            suspend fun handleStreamEvent(event: StreamEvent) {
                when (event) {
                    is StreamEvent.TextChunk -> {
                        val answerText = if (currentStatus == MessageStatus.THINKING) event.text.trimStart() else event.text
                        if (currentStatus == MessageStatus.THINKING && answerText.isBlank()) {
                            retryText = null
                            return
                        }
                        if (currentStatus == MessageStatus.THINKING) {
                            flushThoughtSegment()
                        }
                        totalText += answerText
                        currentAnswerBuf.append(answerText)
                        if (answerText.isNotBlank()) {
                            currentStatus = MessageStatus.SENDING
                        }
                        retryText = null
                    }
                    is StreamEvent.ThoughtChunk -> {
                        flushAnswerSegment()
                        currentStatus = MessageStatus.THINKING
                        retryText = null
                        if (currentThoughtStartMs == null) {
                            currentThoughtStartMs = System.currentTimeMillis()
                        }
                        if (totalThoughts.isEmpty()) totalThoughts = thinkingPlaceholder
                        if (event.thought.isNotEmpty()) {
                            currentThoughtBuf.append(event.thought)
                            if (totalThoughts == thinkingPlaceholder) totalThoughts = event.thought
                            else totalThoughts += event.thought
                        }
                        if (event.title != null) totalThoughtTitle = event.title
                        if (event.signature != null) currentThoughtSignature = event.signature
                    }
                    is StreamEvent.UsageUpdate -> {
                        if (event.tokenCount > 0) totalTokenCount = event.tokenCount
                        if (totalText.isEmpty() && event.thoughtsTokenCount > 0) {
                            currentStatus = MessageStatus.THINKING
                            if (currentThoughtStartMs == null) {
                                currentThoughtStartMs = System.currentTimeMillis()
                            }
                            if (totalThoughts.isEmpty()) totalThoughts = thinkingPlaceholder
                        }
                    }
                    is StreamEvent.Retrying -> {
                        retryText = context.getString(R.string.generation_retry_attempt, event.attempt, event.maxAttempts)
                        onStreamUpdate(modelMessage())
                    }
                    is StreamEvent.Error -> {
                        flushThoughtSegment()
                        retryText = null
                        if (toolCallData == null && toolCallDataList.isEmpty()) {
                            totalText = event.message
                            currentStatus = MessageStatus.ERROR
                        }
                    }
                    is StreamEvent.ToolCallRequest -> {
                        flushAnswerSegment()
                        flushThoughtSegment()
                        val ts = MessageSegment(type = "tool", toolName = event.name, toolArgs = event.arguments, toolResult = null, toolCallId = event.id, signature = event.signature)
                        appendMergedSegment(segments, ts)
                        currentStatus = MessageStatus.TOOL_CALLING
                        onStreamUpdate(modelMessage())
                        lastEmitMs = System.currentTimeMillis()
                        val result = executeTool(event.name, event.arguments, ctx)
                        generatedImages.addAll(tools.drainGeneratedImages())
                        val clipped = result.take(Constants.MAX_TOOL_RESULT_LENGTH)
                        val idx = segments.indexOfLast { it.toolCallId == event.id }
                        if (idx >= 0) {
                            segments[idx] = segments[idx].copy(toolResult = clipped)
                            roundToolSegments.add(segments[idx])
                        }
                        val tcd = ToolCallData(event.name, event.arguments, clipped, event.signature, event.id)
                        if (toolCallData == null) toolCallData = tcd
                        toolCallDataList = toolCallDataList + tcd
                        currentStatus = MessageStatus.SENDING
                        // 不再无条件立即刷新——交给外层统一的节流判断（后面 500ms 内的下一次
                        // handleStreamEvent 会自然带出这次状态变化，不会丢失，只是不单独抢跑）
                    }
                    is StreamEvent.ToolCallsRequest -> {
                        flushAnswerSegment()
                        flushThoughtSegment()
                        event.calls.forEach { call ->
                            appendMergedSegment(segments, MessageSegment(type = "tool", toolName = call.name, toolArgs = call.arguments, toolResult = null, toolCallId = call.id, signature = call.signature))
                        }
                        currentStatus = MessageStatus.TOOL_CALLING
                        onStreamUpdate(modelMessage())
                        lastEmitMs = System.currentTimeMillis()
                        val tcds = event.calls.map { call ->
                            val result = executeTool(call.name, call.arguments, ctx)
                            generatedImages.addAll(tools.drainGeneratedImages())
                            val clipped = result.take(Constants.MAX_TOOL_RESULT_LENGTH)
                            val idx = segments.indexOfLast { it.toolCallId == call.id }
                            if (idx >= 0) {
                                segments[idx] = segments[idx].copy(toolResult = clipped)
                                roundToolSegments.add(segments[idx])
                            }
                            ToolCallData(call.name, call.arguments, clipped, call.signature, call.id)
                        }
                        toolCallData = tcds.firstOrNull()
                        toolCallDataList = tcds
                        currentStatus = MessageStatus.SENDING
                        // 不再无条件立即刷新——交给外层统一的节流判断
                    }
                }

                if (!titleTriggerFired && onTitleTriggerReady != null) {
                    val elapsed = System.currentTimeMillis() - streamStartMs
                    val totalContentLength = totalText.length + totalThoughts.length
                    if (totalContentLength >= 100 || elapsed >= 6000) {
                        titleTriggerFired = true
                        onTitleTriggerReady(totalText, totalThoughts)
                    }
                }

                val now = System.currentTimeMillis()
                val isSignificant = event is StreamEvent.Error
                if (now - lastEmitMs >= 500 || isSignificant) {
                    onStreamUpdate(modelMessage())
                    lastEmitMs = now
                }
            }

            val projectedPath = projectAssistantImagesToLatestUserMessage(currentPath, providerConfig.includeImages)
            val apiPath = applyUserTemplate(projectedPath, config.userPrepend, config.userPostpend)
            streamStartMs = System.currentTimeMillis()
            val tFirstToken = System.currentTimeMillis()
            var firstTokenLogged = false
            provider.generateResponse(apiPath, providerConfig).collect { event ->
                if (!firstTokenLogged && event is StreamEvent.TextChunk) {
                    DebugLog.d("GenPerf", "firstToken: ${System.currentTimeMillis() - tFirstToken}ms")
                    firstTokenLogged = true
                }
                handleStreamEvent(event)
            }
            finishCurrentThoughtTiming()
            // Always emit final state after collection completes
            if (generationJob?.isCancelled != true) {
                onStreamUpdate(modelMessage())
            }

            // Multi-tool loop
            var toolRound = 0
            toolPath = currentPath

            while (toolCallDataList.isNotEmpty() && currentStatus != MessageStatus.ERROR && currentCoroutineContext().isActive) {
                toolRound++
                val roundToolList = roundToolSegments.toList()
                roundToolSegments.clear()
                val thoughtSegs = segments.filter { it.type == "thought" }
                val txedSegments = if (thoughtSegs.isNotEmpty()) thoughtSegs + roundToolList else roundToolList
                val prevLastId = if (toolRound == 1) modelMessageId else toolPath.lastOrNull()?.id
                val toolMsgId = "${Constants.TOOL_MSG_PREFIX}${UUID.randomUUID()}"
                val toolMsgSegs = txedSegments.ifEmpty { null }
                val tcds = toolCallDataList
                val allSegmentsJson = Json.encodeToString(toolMsgSegs ?: tcds.map { tc ->
                    MessageSegment(type = "tool", toolName = tc.toolName, toolArgs = tc.arguments, toolResult = tc.result, signature = tc.signature, toolCallId = tc.toolCallId)
                })
                val resultMsgs = tcds.map { tcData ->
                    val rid = "${Constants.RESULT_MSG_PREFIX}${UUID.randomUUID()}"
                    val displayText = SearchResultFormatter.format(tcData.result, context)
                    rid to ChatMessage(
                        id = rid, parentId = toolMsgId,
                        text = displayText,
                        participant = Participant.USER, status = MessageStatus.SUCCESS,
                        toolCall = tcData
                    )
                }
                toolPath = toolPath.toMutableList().apply {
                    add(ChatMessage(
                        id = toolMsgId, parentId = prevLastId,
                        text = "", participant = Participant.MODEL,
                        status = MessageStatus.SUCCESS, toolCall = tcds.first(),
                        segments = toolMsgSegs
                    ))
                    for ((_, msg) in resultMsgs) add(msg)
                }
                conversations.upsertMessage(MessageEntity(
                    id = toolMsgId, conversationId = conversationId, parentId = prevLastId,
                    text = "", thoughts = null, status = MessageStatus.SUCCESS,
                    participant = Participant.MODEL, timestamp = System.currentTimeMillis(),
                    toolCallJson = allSegmentsJson
                ))
                for ((index, entry) in resultMsgs.withIndex()) {
                    val (rid, _) = entry
                    conversations.upsertMessage(MessageEntity(
                        id = rid, conversationId = conversationId, parentId = toolMsgId,
                        text = tcds[index].result, thoughts = null, status = MessageStatus.SUCCESS,
                        participant = Participant.USER, timestamp = System.currentTimeMillis(),
                        toolCallJson = Json.encodeToString(listOf(
                            MessageSegment(type = "tool", toolName = tcds[index].toolName, toolArgs = tcds[index].arguments, toolResult = tcds[index].result, signature = tcds[index].signature, toolCallId = tcds[index].toolCallId)
                        ))
                    ))
                }

                toolCallData = null
                toolCallDataList = emptyList()

                lastEmitMs = 0L

                val projectedToolPath = projectAssistantImagesToLatestUserMessage(toolPath, providerConfig.includeImages)
                val apiToolPath = applyUserTemplate(projectedToolPath, config.userPrepend, config.userPostpend)
                provider.generateResponse(apiToolPath, providerConfig).collect { event ->
                    handleStreamEvent(event)
                }
                finishCurrentThoughtTiming()
                // Always emit final state after tool round completes
                onStreamUpdate(modelMessage())
            }

            if (!currentCoroutineContext().isActive) {
                currentStatus = MessageStatus.STOPPED
            }

            if (!isRegenerate && isLatestPersist()) for (msg in toolPath) {
                if (msg.id.startsWith(Constants.TOOL_MSG_PREFIX) || msg.id.startsWith(Constants.RESULT_MSG_PREFIX)) {
                    val exists = conversations.getMessagesForConversationSnapshot(conversationId).any { it.id == msg.id }
                    if (!exists) {
                        conversations.upsertMessage(MessageEntity(
                            id = msg.id, conversationId = conversationId, parentId = msg.parentId,
                            text = msg.text, thoughts = null, status = msg.status,
                            participant = msg.participant, timestamp = System.currentTimeMillis(),
                            toolCallJson = msg.segments?.let { Json.encodeToString(it) }
                                ?: msg.toolCall?.let { Json.encodeToString(listOf(
                                    MessageSegment(type = "tool", toolName = it.toolName, toolArgs = it.arguments, toolResult = it.result, signature = it.signature, toolCallId = it.toolCallId)
                                )) }
                        ))
                    }
                }
            }

            if (currentStatus != MessageStatus.ERROR) {
                currentStatus = if (totalText.isNotEmpty() || totalThoughts.isNotEmpty()) MessageStatus.SUCCESS else MessageStatus.ERROR
            }
            if (generationJob?.isCancelled == true && currentStatus != MessageStatus.ERROR) {
                currentStatus = MessageStatus.STOPPED
            }
            } // else { // called buildApiPath when currentStatus == ERROR
        } catch (e: CancellationException) {
            currentStatus = MessageStatus.STOPPED
            throw e
        } catch (e: Exception) {
            val isCancelled = generationJob?.isCancelled == true
            currentStatus = if (isCancelled) MessageStatus.STOPPED else MessageStatus.ERROR
            if (!isCancelled) {
                totalText = "Error: ${e.localizedMessage ?: "An unexpected error occurred."}"
            }
        } finally {
            withContext(NonCancellable) {
                com.orangeisland.app.api.HttpClient.releaseToken(cancellationToken)
                if (session?.currentCancellationToken == cancellationToken) {
                    session.currentCancellationToken = null
                }
                try {
                    if (isLatestPersist()) {
                        val conversationExists = conversations.getConversation(conversationId) != null
                        if (conversationExists) {
                            finishCurrentThoughtTiming()
                            val finalSegments = buildLiveSegments(
                                segments,
                                currentAnswerBuf,
                                currentThoughtBuf,
                                currentThoughtSignature,
                                currentThoughtDurationMs.takeIf { it > 0L }
                            )
                                ?: segments.toList().ifEmpty { null }
                            val segmentsJson = finalSegments?.let { Json.encodeToString(it) }
                            val effectiveParentId = parentId
                            DebugLog.d("GenStopRace", "[generateFinally] BEFORE upsert id=$modelMessageId textLen=${totalText.length} status=$currentStatus time=${System.currentTimeMillis()}")
                            conversations.upsertMessage(MessageEntity(
                                id = modelMessageId, conversationId = conversationId, parentId = effectiveParentId,
                                text = totalText, images = generatedImages.toList(),
                                thoughts = totalThoughts.ifBlank { null },
                                thoughtTitle = totalThoughtTitle, tokenCount = totalTokenCount,
                                status = currentStatus, participant = Participant.MODEL, timestamp = startTime,
                                thoughtTimeMs = totalThoughtTimeMs, modelName = modelName, toolCallJson = segmentsJson
                            ))
                            DebugLog.d("GenStopRace", "[generateFinally] AFTER  upsert id=$modelMessageId textLen=${totalText.length} status=$currentStatus time=${System.currentTimeMillis()}")
                            if (totalText.isNotBlank()) {
                                onMessagePersisted?.invoke(modelMessageId, totalText)
                            }
                        }
                    }
                } catch (e: Exception) {
                    DebugLog.e("OrangeIslandVM", "Failed to persist message to DB", e)
                }
                // Terminal UI cleanup. These callbacks are token-gated at the sink
                // (in ChatViewModel), so they automatically no-op when this generation
                // was stopped or superseded — only the still-current generation resets
                // the loading/streaming/generating-id UI state.
                onStreamClear()
                onLoadingChange(false)
                onGeneratingIdChange(null)
                OrangeIslandForegroundService.stop(app)
                if (!AppForegroundTracker.isInForeground && currentStatus == MessageStatus.SUCCESS && totalText.isNotBlank()) {
                    OrangeIslandForegroundService.showCompletionNotification(app, totalText)
                }
            }
        }
    }
}
