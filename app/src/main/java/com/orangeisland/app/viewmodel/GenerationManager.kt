package com.orangeisland.app.viewmodel

import android.app.Application
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.data.UsageLogManager
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
import com.orangeisland.app.api.util.limitContext
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
    val ttsEnabled: Boolean = false,
    /** AI voice call tool (make_voice_call). Surfaced when both STT and TTS are configured, so the
     *  model can proactively ring the user for a full-duplex voice conversation. */
    val voiceCallEnabled: Boolean = false,
    val ttsProvider: String = "elevenlabs",
    val ttsApiKey: String = "",
    val ttsVoiceId: String = "",
    val ttsModel: String = "",
    val ttsSpeed: Float = 1.0f,
    val ttsOutputFormat: String = "",
    val ttsStability: Float = 0.5f,
    val ttsSimilarityBoost: Float = 0.75f,
    val ttsStyle: Float = 0.0f,
    val ttsVolume: Float = 1.0f,
    val ttsPitch: Float = 0.0f,
    val imageTranscriptionEnabled: Boolean = false,
    val imageTranscriptionModel: String? = null,
    val imageTranscriptionBatchSize: Int = 3,
    val imageTranscriptionPrompt: String = com.orangeisland.app.data.BuiltInPrompts.IMAGE_TRANSCRIPTION_USER,
    val transcriptionProviderName: String = "",
    val transcriptionModelId: String = "",
    val transcriptionApiKey: String = "",
    val transcriptionBaseUrl: String? = null,
    val videoNarrationEnabled: Boolean = false,
    val videoNarrationModel: String? = null,
    val videoNarrationPrompt: String = com.orangeisland.app.data.BuiltInPrompts.VIDEO_NARRATION_USER,
    val videoNarrationFps: Float = 1f,
    val videoNarrationDetail: String = "default",
    val videoNarrationMaxLongSide: Int = 1280,
    val videoNarrationProviderName: String = "",
    val videoNarrationModelId: String = "",
    val videoNarrationApiKey: String = "",
    val videoNarrationBaseUrl: String? = null,
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
    /** Media control tool — reads/controls other apps' MediaSession (e.g. NetEase Cloud Music).
     *  Same authorization path as the notification listener: MediaSessionManager only exposes other
     *  apps' sessions reliably while our NotificationListenerService is bound. */
    val mediaControlEnabled: Boolean = false,
    val usageStatsEnabled: Boolean = false,
    val navigationEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val toastEnabled: Boolean = false,
    val alarmEnabled: Boolean = false,
    val healthEnabled: Boolean = false,
    val healthDbPath: String = "",
    /** get_current_time tool — returns only HH:mm:ss, no date/timezone/timestamp. */
    val timeToolEnabled: Boolean = false,
    val uiAutomationEnabled: Boolean = false,
    val userInteractionEnabled: Boolean = true,
    /** The project this conversation belongs to (null = ungrouped). Drives memory scoping:
     *  when non-null, memory tools read/write the project-private memory dir on top of the
     *  always-present global dir; RAG/search filters to the same project. */
    val projectId: String? = null,
    /** The model id used for this generation turn (format "provider:modelId"). Captured by
     *  the workflow authoring tools so a created workflow inherits the conversation's model. */
    val modelId: String? = null,
    /** The system prompt id used for this generation turn. Captured by the workflow authoring
     *  tools so a created workflow inherits the conversation's system prompt. */
    val systemPromptId: String? = null
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
    private val workflowToolProvider: com.orangeisland.app.workflow.WorkflowAiToolProvider? = null,
    /** Optional gate for interactive card-style user choices (ask_user_choice). Threaded into the
     *  standalone dispatcher when [toolDispatcher] is null. Ignored when [toolDispatcher] is non-null. */
    private val userInteractionGate: com.orangeisland.app.tool.UserInteractionGate? = null,
    /** Optional gate for the AI voice-call tool (make_voice_call). Threaded into the standalone
     *  dispatcher when [toolDispatcher] is null. Ignored when [toolDispatcher] is non-null — that
     *  dispatcher carries its own gate. Null in title generation / contexts without the call UI. */
    private val voiceCallGate: com.orangeisland.app.viewmodel.VoiceCallGate? = null
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
            workflowToolProvider = workflowToolProvider,
            userInteractionGate = userInteractionGate,
            voiceCallGate = voiceCallGate
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
    private val videoNarrationManager = VideoNarrationManager(providers, conversations, context)

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
    suspend fun buildMcpTools(ctx: GenerationContext): List<ToolDefinition> =
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

    /** Alarm/timer tools (set_alarm, set_timer). Internally checks [GenerationContext.alarmEnabled]. */
    fun buildAlarmTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.alarmDefinitions(ctx)

    /** Health tools (get_health_summary/get_daily_health_history/get_sleep_history).
     *  Internally checks [GenerationContext.healthEnabled] and [GenerationContext.healthDbPath]. */
    fun buildHealthTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.healthDefinitions(ctx)

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

    /** User-interaction tools (ask_user_choice). Lets the model ask the user a multiple-choice
     *  question via a card-style dialog. Only exposed when the UI gate is installed. */
    fun buildUserInteractionTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.userInteractionDefinitions(ctx)

    /** Text-to-speech tools (speak). Lets the model generate voice audio for the user.
     *  Empty when TTS is disabled or not configured. */
    fun buildTtsTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.ttsDefinitions(ctx)

    /** AI voice-call tool (make_voice_call). Lets the model proactively start a voice conversation
     *  — rings the user via a full-screen incoming-call UI. Empty when STT or TTS isn't configured. */
    fun buildVoiceCallTools(ctx: GenerationContext): List<ToolDefinition> =
        tools.voiceCallDefinitions(ctx)

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
            // Synthetic tool_/result_ messages can appear in the ancestor chain when a
            // workflow chat_message was parented on a result_ node (legacy / edge case).
            // They are already injected as toolChildren of their parent model message above,
            // so adding them again here would create duplicate (and broken) tool_use blocks.
            if (entity.id.startsWith(Constants.TOOL_MSG_PREFIX) || entity.id.startsWith(Constants.RESULT_MSG_PREFIX)) {
                continue
            }
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
            val meta = com.orangeisland.app.model.AttachmentMeta.parse(it.attachmentMeta)
            val videos = meta?.items?.filter { item -> item.type == "video" }?.mapNotNull { item -> item.originalUri } ?: emptyList()
            if (meta != null && meta.items.any { item -> item.type == "video" }) {
                val vidItems = meta.items.filter { item -> item.type == "video" }
                DebugLog.d("VideoNarration", "buildApiPath msg=${it.id.take(12)} role=${it.participant} " +
                    "metaItems=${meta.items.size} videoItems=${vidItems.size} " +
                    "videoTranscriptions=${vidItems.mapIndexed { i, v -> "[$i]blank=${v.videoTranscription.isNullOrBlank()} len=${v.videoTranscription?.length ?: 0}" }} " +
                    "videoNarrationEnabled(ctx)=${ctx.videoNarrationEnabled}")
            }
            val attachmentText = if (meta != null) {
                meta.items.mapNotNull { item ->
                    val content = item.textContent
                    val transcription = item.transcription
                    val videoTranscription = item.videoTranscription
                    val includeImageTranscription = ctx.imageTranscriptionEnabled && transcription != null && transcription.isNotBlank()
                    val includeVideoNarration = ctx.videoNarrationEnabled && videoTranscription != null && videoTranscription.isNotBlank()
                    when {
                        content != null -> {
                            val label = item.fileName ?: "file"
                            "\n\n--- File: $label ---\n$content"
                        }
                        includeVideoNarration -> {
                            val label = item.fileName ?: "video"
                            "\n\n--- Video Narration: $label ---\n$videoTranscription"
                        }
                        includeImageTranscription -> {
                            val label = item.fileName ?: "image"
                            "\n\n--- Image Transcription: $label ---\n$transcription"
                        }
                        else -> null
                    }
                }.joinToString("")
            } else ""
            val combinedText = if (attachmentText.isNotBlank()) it.text + attachmentText else it.text
            val hasImageTranscription = ctx.imageTranscriptionEnabled && meta != null && meta.items.any { item -> !item.transcription.isNullOrBlank() }
            val narratedVideoItems = if (ctx.videoNarrationEnabled && meta != null) {
                meta.items.filter { item -> item.type == "video" && !item.videoTranscription.isNullOrBlank() }
            } else emptyList()
            val hasVideoNarration = narratedVideoItems.isNotEmpty()
            // Image stripping: drop ALL images when image transcription covers them (legacy
            // whole-message behaviour). Otherwise, when video narration is active, drop only
            // the frames that belong to a narrated video — pure images and frames of an
            // UN-narrated video must survive (the latter still need to reach the model).
            val effectiveImages = when {
                hasImageTranscription -> emptyList()
                hasVideoNarration -> {
                    val narratedFrameIndices = narratedVideoItems.flatMap { item ->
                        val start = item.imageIndex ?: return@flatMap emptyList<Int>()
                        val count = item.pageCount ?: 1
                        start until (start + count)
                    }.toSet()
                    if (narratedFrameIndices.isNotEmpty()) {
                        // Precise strip: drop only the frames belonging to narrated videos.
                        it.images.filterIndexed { idx, _ -> idx !in narratedFrameIndices }
                    } else {
                        // Fallback for legacy attachmentMeta stored as a bare URI array — the
                        // reconstructed video items carry no imageIndex/pageCount, so we can't
                        // tell which images are frames. If this message has ONLY video items
                        // (no pure images), every image must be a video frame → strip them all.
                        val hasPureImageItems = meta?.items.orEmpty().any { it.type != "video" && it.type != "file" && it.type != "pdf" }
                        if (!hasPureImageItems) emptyList() else it.images
                    }
                }
                else -> it.images
            }
            val effectiveVideos = if (hasVideoNarration) emptyList() else videos
            if (videos.isNotEmpty() || (hasVideoNarration && it.images.isNotEmpty())) {
                DebugLog.d("VideoNarration", "buildApiPath strip msg=${it.id.take(12)}: hasVideoNarration=$hasVideoNarration " +
                    "-> videos=${videos.size} effectiveVideos=${effectiveVideos.size} " +
                    "images=${it.images.size} effectiveImages=${effectiveImages.size} (dropped ${it.images.size - effectiveImages.size} video frames)")
            }
            ChatMessage(id = it.id, parentId = it.parentId, text = combinedText, images = effectiveImages, videos = effectiveVideos, thoughts = it.thoughts, thoughtTitle = it.thoughtTitle, tokenCount = it.tokenCount, cachedTokenCount = it.cachedTokenCount, contextMessageCount = it.contextMessageCount, status = it.status, participant = it.participant, timestamp = it.timestamp, thoughtTimeMs = it.thoughtTimeMs, generationDurationMs = it.generationDurationMs, segments = segs, toolCall = toolCall)
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
        val alarmTools = buildAlarmTools(ctx)
        val healthTools = buildHealthTools(ctx)
        val automationTools = buildAutomationTools(ctx)
        val workflowTools = buildWorkflowTools(ctx)
        val userInteractionTools = buildUserInteractionTools(ctx)
        val ttsTools = buildTtsTools(ctx)
        val voiceCallTools = buildVoiceCallTools(ctx)
        val allTools = memoryTools + webSearchTool + ragTool + imageGenTool + shellTool + fileTool + mcpTools + pluginTools + deviceTools + navigationTools + appLockTools + toastTools + alarmTools + healthTools + automationTools + workflowTools + userInteractionTools + ttsTools + voiceCallTools
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
        // ── Auto-compressed history injection ───────────────────────────
        // If this conversation has a running summary (compactedSummary) up to a
        // watermark timestamp, drop the already-summarized USER/MODEL messages
        // from the path and fold the summary into the system prompt instead. This
        // keeps long-term context alive without overflowing the context window.
        val compressedConv = conversations.getConversation(conversationId)
        val summary = compressedConv?.compactedSummary
        val watermark = compressedConv?.compactedUpToTimestamp

        val injectedPath = if (summary != null && watermark != null) {
            currentPath.filter { msg ->
                // SYSTEM cards are virtual UI-only (the compacted-history summary card) and must
                // never be sent to the model — the summary is already injected into the system
                // prompt below.
                if (msg.participant == Participant.SYSTEM) return@filter false
                // Keep tool_/result_ children attached to surviving parents;
                // limitContext downstream keeps tool_use/tool_result pairs intact.
                if (msg.participant != Participant.USER && msg.participant != Participant.MODEL) {
                    true
                } else {
                    msg.timestamp > watermark
                }
            }
        } else {
            currentPath.filter { it.participant != Participant.SYSTEM }
        }

        // Safety net: no summary yet (compression is async and may not finish
        // before this request) but the path already overflows the window. Without
        // this the local provider would hit LOCAL_CONTEXT_EXCEEDED and hard-fail.
        // Cloud providers would drop the overflow via prepareMessages anyway; applying
        // it uniformly keeps behavior consistent across providers.
        val finalPath = if (summary == null &&
            injectedPath.count { it.participant == Participant.USER } > config.maxContextWindow
        ) {
            limitContext(injectedPath, config.maxContextWindow)
        } else {
            injectedPath
        }

        val finalSystemPrompt = if (summary != null) {
            (config.effectiveSystemPrompt?.takeIf { it.isNotBlank() }?.let { "$it\n\n" } ?: "") +
                "[Summary of earlier conversation]\n$summary"
        } else {
            config.effectiveSystemPrompt
        }

        return Pair(finalPath, providerConfig.copy(systemPrompt = finalSystemPrompt))
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
        val (onStreamUpdate, onLoadingChange, onGeneratingIdChange, onStreamClear, isLatestPersist, onTitleTriggerReady) = callbacks
        val provider = getProviderInstance(config.providerName)

        onLoadingChange(true)
        onGeneratingIdChange(conversationId)
        com.orangeisland.app.util.CrashReporter.note("generate provider=${config.providerName} regen=$isRegenerate")
        withContext(Dispatchers.Main) { OrangeIslandForegroundService.start(app) }

        val cancellationToken = com.orangeisland.app.api.HttpClient.newCancellationToken()
        session?.currentCancellationToken = cancellationToken

        val placeholder = conversations.getMessagesForConversationSnapshot(conversationId).find { it.id == modelMessageId }
        val parentId = placeholder?.parentId

        val state = GenerationTurnState(
            context = context,
            modelMessageId = modelMessageId,
            parentId = parentId,
            startTime = startTime,
            modelName = modelName,
            onStreamUpdate = onStreamUpdate,
            onTitleTriggerReady = onTitleTriggerReady,
            executeTool = { name, arguments -> executeTool(name, arguments, ctx) },
            drainGeneratedImages = { tools.drainGeneratedImages() },
            drainAudio = { tools.drainAudio() },
        )
        var toolPath: List<ChatMessage> = emptyList()

        try {
            var transcriptionPerformed = false
            if (ctx.imageTranscriptionEnabled && ctx.transcriptionModelId.isNotEmpty()) {
                kotlinx.coroutines.delay(500)
                val targets = transcriptionManager.collectTargets(conversationId, parentId)
                DebugLog.d("ImageTranscription", "transcription gate: imageTranscriptionEnabled=${ctx.imageTranscriptionEnabled} modelId='${ctx.transcriptionModelId}' provider='${ctx.transcriptionProviderName}' targets=${targets.size}")
                if (targets.isNotEmpty()) {
                    val (transcriptionSegments, transcriptionError) = transcriptionManager.transcribe(
                        targets, conversationId,
                        ctx.transcriptionProviderName, ctx.transcriptionModelId,
                        ctx.transcriptionApiKey, ctx.transcriptionBaseUrl,
                        ctx.imageTranscriptionPrompt,
                        generationJob, modelMessageId, startTime, onStreamUpdate
                    )
                    if (transcriptionError != null) {
                        DebugLog.e("ImageTranscription", "transcription returned ERROR: $transcriptionError")
                        state.totalText = transcriptionError
                        state.currentStatus = MessageStatus.ERROR
                        transcriptionPerformed = true
                    } else {
                        DebugLog.d("ImageTranscription", "transcription OK, segments=${transcriptionSegments.size}")
                        state.segments.addAll(0, transcriptionSegments)
                        transcriptionPerformed = true
                    }
                } else {
                    DebugLog.d("ImageTranscription", "transcription gate OPEN but collectTargets returned 0 — no images need transcription")
                }
            } else {
                DebugLog.d("ImageTranscription", "transcription SKIPPED: enabled=${ctx.imageTranscriptionEnabled} modelIdEmpty=${ctx.transcriptionModelId.isEmpty()} (modelId='${ctx.transcriptionModelId}')")
            }

            if (state.currentStatus != MessageStatus.ERROR && ctx.videoNarrationEnabled && ctx.videoNarrationModelId.isNotEmpty()) {
                kotlinx.coroutines.delay(500)
                val videoTargets = videoNarrationManager.collectTargets(conversationId, parentId)
                DebugLog.d("VideoNarration", "narration gate: videoNarrationEnabled=${ctx.videoNarrationEnabled} modelId='${ctx.videoNarrationModelId}' provider='${ctx.videoNarrationProviderName}' targets=${videoTargets.size}")
                if (videoTargets.isNotEmpty()) {
                    val (narrationSegments, narrationError) = videoNarrationManager.narrate(
                        videoTargets, conversationId,
                        ctx.videoNarrationProviderName, ctx.videoNarrationModelId,
                        ctx.videoNarrationApiKey, ctx.videoNarrationBaseUrl,
                        ctx.videoNarrationPrompt,
                        ctx.videoNarrationFps, ctx.videoNarrationDetail, ctx.videoNarrationMaxLongSide,
                        generationJob, modelMessageId, startTime, onStreamUpdate
                    )
                    if (narrationError != null) {
                        DebugLog.e("VideoNarration", "narration returned ERROR: $narrationError")
                        state.totalText = narrationError
                        state.currentStatus = MessageStatus.ERROR
                    } else {
                        DebugLog.d("VideoNarration", "narration OK, segments=${narrationSegments.size}")
                        state.segments.addAll(0, narrationSegments)
                    }
                } else {
                    DebugLog.d("VideoNarration", "narration gate OPEN but collectTargets returned 0 — no videos need narration")
                }
            } else if (state.currentStatus != MessageStatus.ERROR) {
                DebugLog.d("VideoNarration", "narration SKIPPED: enabled=${ctx.videoNarrationEnabled} modelIdEmpty=${ctx.videoNarrationModelId.isEmpty()} (modelId='${ctx.videoNarrationModelId}')")
            }

            if (state.currentStatus != MessageStatus.ERROR) {
                val tRefresh = System.currentTimeMillis()
                tools.refreshPlugins()
                DebugLog.d("GenPerf", "refreshPlugins: ${System.currentTimeMillis() - tRefresh}ms")

                val tApiPath = System.currentTimeMillis()
                val (currentPath, rawProviderConfig) = buildApiPath(parentId, conversationId, isRegenerate, replaceMessageId, config, ctx, cancellationToken)
                state.contextMessageCount = currentPath.count {
                    !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
                }
                DebugLog.d("GenPerf", "buildApiPath: ${System.currentTimeMillis() - tApiPath}ms, pathSize=${currentPath.size}, toolCount=${rawProviderConfig.tools?.size ?: 0}")
                val providerConfig = if (transcriptionPerformed) rawProviderConfig.copy(includeImages = false) else rawProviderConfig

                state.streamStartMs = System.currentTimeMillis()
                runInitialStream(conversationId, currentPath, providerConfig, config, provider, state, generationJob)
                toolPath = currentPath

                toolPath = runToolCallLoop(toolPath, conversationId, providerConfig, config, provider, state)

                if (!currentCoroutineContext().isActive) {
                    state.currentStatus = MessageStatus.STOPPED
                }

                if (!isRegenerate && isLatestPersist()) {
                    persistPendingToolPathMessages(toolPath, conversationId)
                }

                if (state.currentStatus != MessageStatus.ERROR) {
                    state.currentStatus = if (state.totalText.isNotEmpty() || state.totalThoughts.isNotEmpty()) MessageStatus.SUCCESS else MessageStatus.ERROR
                }
                if (generationJob?.isCancelled == true && state.currentStatus != MessageStatus.ERROR) {
                    state.currentStatus = MessageStatus.STOPPED
                }
            }
        } catch (e: CancellationException) {
            state.currentStatus = MessageStatus.STOPPED
            throw e
        } catch (e: Exception) {
            val isCancelled = generationJob?.isCancelled == true
            state.currentStatus = if (isCancelled) MessageStatus.STOPPED else MessageStatus.ERROR
            if (!isCancelled) {
                state.totalText = "Error: ${e.localizedMessage ?: "An unexpected error occurred."}"
                UsageLogManager.logModel(
                    name = "${config.providerName} / ${config.modelId}",
                    conversationId = conversationId,
                    details = "生成失败: ${e.message}",
                    isError = true
                )
            }
        } finally {
            finalizeGeneration(conversationId, state, isLatestPersist, onStreamClear, onLoadingChange, onGeneratingIdChange, session, cancellationToken)
        }
    }

    private suspend fun runInitialStream(
        conversationId: String,
        currentPath: List<ChatMessage>,
        providerConfig: ProviderConfig,
        config: GenerationConfig,
        provider: LlmProvider,
        state: GenerationTurnState,
        generationJob: kotlinx.coroutines.Job?,
    ) {
        val projectedPath = projectAssistantImagesToLatestUserMessage(currentPath, providerConfig.includeImages)
        val apiPath = applyUserTemplate(projectedPath, config.userPrepend, config.userPostpend)
        val toolList = providerConfig.tools?.map { it.function.name }?.joinToString(", ") ?: "none"
        UsageLogManager.logModel(
            name = "${config.providerName} / ${config.modelId}",
            conversationId = conversationId,
            details = "tools=[$toolList] | messages=${apiPath.size}"
        )
        val tFirstToken = System.currentTimeMillis()
        var firstTokenLogged = false
        provider.generateResponse(apiPath, providerConfig).collect { event ->
            if (!firstTokenLogged && event is StreamEvent.TextChunk) {
                DebugLog.d("GenPerf", "firstToken: ${System.currentTimeMillis() - tFirstToken}ms")
                firstTokenLogged = true
            }
            state.handleStreamEvent(event)
        }
        state.finishCurrentThoughtTiming()
        if (generationJob?.isCancelled != true) {
            state.emitCurrent()
        }
    }

    private suspend fun runToolCallLoop(
        initialToolPath: List<ChatMessage>,
        conversationId: String,
        providerConfig: ProviderConfig,
        config: GenerationConfig,
        provider: LlmProvider,
        state: GenerationTurnState,
    ): List<ChatMessage> {
        var toolPath = initialToolPath
        var toolRound = 0

        while (state.toolCallDataList.isNotEmpty() && state.currentStatus != MessageStatus.ERROR && currentCoroutineContext().isActive) {
            toolRound++
            val roundToolList = state.roundToolSegments.toList()
            state.roundToolSegments.clear()
            val thoughtSegs = state.segments.filter { it.type == "thought" }
            val txedSegments = if (thoughtSegs.isNotEmpty()) thoughtSegs + roundToolList else roundToolList
            val prevLastId = if (toolRound == 1) state.modelMessageId else toolPath.lastOrNull()?.id
            val toolMsgId = "${Constants.TOOL_MSG_PREFIX}${UUID.randomUUID()}"
            val toolMsgSegs = txedSegments.ifEmpty { null }
            val tcds = state.toolCallDataList
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

            state.toolCallData = null
            state.toolCallDataList = emptyList()
            state.lastEmitMs = 0L

            val projectedToolPath = projectAssistantImagesToLatestUserMessage(toolPath, providerConfig.includeImages)
            val apiToolPath = applyUserTemplate(projectedToolPath, config.userPrepend, config.userPostpend)
            provider.generateResponse(apiToolPath, providerConfig).collect { event ->
                state.handleStreamEvent(event)
            }
            state.finishCurrentThoughtTiming()
            state.emitCurrent()
        }
        return toolPath
    }

    private suspend fun persistPendingToolPathMessages(toolPath: List<ChatMessage>, conversationId: String) {
        for (msg in toolPath) {
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
    }

    private suspend fun finalizeGeneration(
        conversationId: String,
        state: GenerationTurnState,
        isLatestPersist: () -> Boolean,
        onStreamClear: () -> Unit,
        onLoadingChange: (Boolean) -> Unit,
        onGeneratingIdChange: (String?) -> Unit,
        session: GenerationSession?,
        cancellationToken: Long,
    ) {
        withContext(NonCancellable) {
            com.orangeisland.app.api.HttpClient.releaseToken(cancellationToken)
            if (session?.currentCancellationToken == cancellationToken) {
                session.currentCancellationToken = null
            }
            try {
                if (isLatestPersist()) {
                    val conversationExists = conversations.getConversation(conversationId) != null
                    if (conversationExists) {
                        val finalSegments = state.finalSegments()
                        DebugLog.d("VideoNarration", "PERSIST segments: ${finalSegments?.map { "${it.type}(${it.content.length})" } ?: "null"}")
                        val segmentsJson = finalSegments?.let { Json.encodeToString(it) }
                        DebugLog.d("GenStopRace", "[generateFinally] BEFORE upsert id=${state.modelMessageId} textLen=${state.totalText.length} status=${state.currentStatus} time=${System.currentTimeMillis()}")
                        val entity = MessageEntity(
                            id = state.modelMessageId, conversationId = conversationId, parentId = state.parentId,
                            text = state.totalText, images = state.generatedImages.toList(), audio = state.generatedAudio.toList(),
                            thoughts = state.totalThoughts.ifBlank { null },
                            thoughtTitle = state.totalThoughtTitle, tokenCount = state.totalTokenCount,
                            cachedTokenCount = state.totalCachedTokenCount, contextMessageCount = state.contextMessageCount,
                            status = state.currentStatus, participant = Participant.MODEL, timestamp = state.startTime,
                            thoughtTimeMs = state.totalThoughtTimeMs, generationDurationMs = System.currentTimeMillis() - state.startTime,
                            modelName = state.modelName, toolCallJson = segmentsJson
                        )
                        if (session != null) {
                            session.withMessageWriteLock { conversations.upsertMessage(entity) }
                        } else {
                            conversations.upsertMessage(entity)
                        }
                        DebugLog.d("GenStopRace", "[generateFinally] AFTER  upsert id=${state.modelMessageId} textLen=${state.totalText.length} status=${state.currentStatus} time=${System.currentTimeMillis()}")
                        if (state.totalText.isNotBlank()) {
                            onMessagePersisted?.invoke(state.modelMessageId, state.totalText)
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("OrangeIslandVM", "Failed to persist message to DB", e)
            }
            onStreamClear()
            onLoadingChange(false)
            onGeneratingIdChange(null)
            OrangeIslandForegroundService.stop(app)
            OrangeIslandForegroundService.releaseFallbackWakeLock()
            if (!AppForegroundTracker.isInForeground && state.currentStatus == MessageStatus.SUCCESS && state.totalText.isNotBlank()) {
                OrangeIslandForegroundService.showCompletionNotification(app, state.totalText)
            }
        }
    }
}
