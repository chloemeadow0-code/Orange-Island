package com.orangeisland.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orangeisland.app.R
import com.orangeisland.app.api.*
import com.orangeisland.app.api.LlamaEngine
import com.orangeisland.app.api.anthropic.*
import com.orangeisland.app.api.gemini.*
import com.orangeisland.app.api.local.*
import com.orangeisland.app.api.ollama.*
import com.orangeisland.app.api.openai.*
import com.orangeisland.app.data.AutoBackupManager
import com.orangeisland.app.data.BuiltInPrompts
import com.orangeisland.app.data.ClaudeChatImporter
import com.orangeisland.app.data.ChatSettingsSnapshot
import com.orangeisland.app.data.ConversationSettings
import com.orangeisland.app.data.DataExporter
import com.orangeisland.app.data.DataImporter
import com.orangeisland.app.data.EmbeddingModelConfig
import com.orangeisland.app.data.LocalChatModelConfig
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.PredefinedVariables

import com.orangeisland.app.data.ShellDeviceConfig
import com.orangeisland.app.data.UsageLogManager

import com.orangeisland.app.data.local.ChatEntity
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.local.ProjectEntity
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.model.AttachmentItem
import com.orangeisland.app.model.AttachmentMeta
import com.orangeisland.app.model.ChatConversation
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.ModelId
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.SelectedAttachment
import com.orangeisland.app.model.ToolCallData
import com.orangeisland.app.sandbox.SandboxManager
import com.orangeisland.app.sandbox.SandboxManagerFactory
import com.orangeisland.app.service.AppForegroundTracker
import com.orangeisland.app.service.OrangeIslandForegroundService
import com.orangeisland.app.service.AutoBackupWorker
import com.orangeisland.app.service.HealthSyncWorker
import com.orangeisland.app.ui.settings.ImportStrategy
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.util.PdfPageRenderer
import com.orangeisland.app.util.SearchResultFormatter
import com.orangeisland.app.util.SnackbarEvent
import com.orangeisland.app.util.SshClient
import com.orangeisland.app.util.UpdateCheckResult
import com.orangeisland.app.util.UpdateChecker
import com.orangeisland.app.util.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ChatViewModel(
    application: Application,
    // [chatDao] and [settingsManager] are retained ONLY to pass to ImportExportManager,
    // which threads them into DataExporter/DataImporter (bulk data-layer utilities that
    // genuinely need raw DAO/DataStore). All other managers use repositories uniformly.
    private val chatDao: com.orangeisland.app.data.local.ChatDao,
    private val settingsManager: com.orangeisland.app.data.SettingsManager,
    val memoryManager: MemoryManager,
    private val appContext: Context,
    private val sandboxFactory: SandboxManagerFactory? = null,
    // All injected via AppContainer/ChatViewModelFactory 鈥?the single construction site.
    val autoBackupManager: AutoBackupManager,
    conversationRepository: ConversationRepository,
    settingsRepository: SettingsRepository,
    private val workflowRepository: com.orangeisland.app.data.repository.WorkflowRepository? = null,
    /** Shared approval gate between the AI workflow-authoring tools and the chat UI. Null in
     *  contexts that don't render the approval card (e.g. unit tests). */
    val workflowApprovalGate: com.orangeisland.app.workflow.WorkflowApprovalGate? = null,
    private val pluginToolProvider: com.orangeisland.app.plugin.PluginToolProvider? = null,
    private val _pluginLoader: com.orangeisland.app.plugin.PluginLoader? = null,
    private val _pluginSandbox: com.orangeisland.app.plugin.PluginSandbox? = null,
    /** Workflow AI tool provider (workflow_list/get/run/create/...). Wired through to the
     *  GenerationManager's tool dispatcher so the model can read, fire, and AI-author linear
     *  workflows from chat. Null in contexts that don't expose workflow tools (e.g. unit tests). */
    private val workflowToolProvider: com.orangeisland.app.workflow.WorkflowAiToolProvider? = null,
    /** Shared gate for interactive card-style user choices (ask_user_choice). Wired through to
     *  the GenerationManager so the model can suspend on user input. Null when the UI observer
     *  is not available (e.g. unit tests). */
    val userInteractionGate: com.orangeisland.app.tool.UserInteractionGate? = null,
    /** Shared gate between the AI voice-call tool (make_voice_call) and the incoming-call UI.
     *  Wired through to the GenerationManager's tool dispatcher. Null when voice call is not
     *  available (e.g. unit tests, title generation). */
    val voiceCallGate: com.orangeisland.app.viewmodel.VoiceCallGate? = null,
    /** Collects environment changes (foreground app, model, prompt, wallpaper, theme, battery,
     *  WiFi, Bluetooth) and formats them for injection into the system prompt via {app_context}. */
    private val appContextCollector: com.orangeisland.app.data.environment.AppContextCollector? = null,
    /** Receives plugin-sent messages and triggers AI generation for them. */
    private val pluginMemoryProvider: com.orangeisland.app.plugin.AppPluginMemoryProvider? = null,
) : AndroidViewModel(application) {

    companion object {
        /** Overlay fade duration for conversation-switch transitions. */
        private const val SWITCH_OVERLAY_FADE_MS = 200L
        /** Auto-delete period tiers in hours: 7 days, 30 days, 365 days. */
        private val AUTO_DELETE_TIERS_HOURS = listOf(168, 720, 8760)
        /** Desktop-pet speech bubble length cap (characters). */
        private const val PET_BUBBLE_MAX = 30
    }

    val settings: SettingsRepository = settingsRepository

    /**
     * Conversation/message persistence behind the repository layer. CRUD, cascade-delete,
     * branch-selection and stuck-message logic live in [ConversationRepository]; managers
     * receive the repository (not raw DAO) for a uniform boundary.
     */
    private val convRepo: ConversationRepository = conversationRepository

    /** Cache for the expensive entity -> [ChatMessage] mapping (SearchResultFormatter + JSON parsing).
     *  Room re-emits the whole list on every streaming token, so re-parsing unchanged messages is
     *  a major hot-path cost. Key covers the fields that affect the mapped output. */
    private val chatMessageCache = android.util.LruCache<String, ChatMessage>(200)

    private fun messageCacheKey(entity: MessageEntity): String = buildString {
        append(entity.id)
        append('|')
        append(entity.status.name)
        append('|')
        append(entity.text.hashCode())
        append('|')
        append(entity.thoughts.hashCode())
        append('|')
        append(entity.toolCallJson.hashCode())
        append('|')
        append(entity.attachmentMeta.hashCode())
    }

    private fun mapMessageEntity(entity: MessageEntity): ChatMessage {
        val key = messageCacheKey(entity)
        return chatMessageCache.get(key) ?: ChatMessage(
            id = entity.id,
            parentId = entity.parentId,
            text = SearchResultFormatter.format(entity.text, appContext),
            images = entity.images,
            audio = entity.audio,
            thoughts = entity.thoughts,
            thoughtTitle = entity.thoughtTitle,
            tokenCount = entity.tokenCount,
            cachedTokenCount = entity.cachedTokenCount,
            contextMessageCount = entity.contextMessageCount,
            status = entity.status,
            participant = entity.participant,
            timestamp = entity.timestamp,
            thoughtTimeMs = entity.thoughtTimeMs,
            generationDurationMs = entity.generationDurationMs,
            modelName = entity.modelName,
            segments = entity.toolCallJson?.let { json ->
                try { Json.decodeFromString<List<MessageSegment>>(json) } catch (_: Exception) { null }
            } ?: entity.thoughts?.takeIf { t -> t.isNotBlank() }?.let { listOf(MessageSegment(type = "thought", content = entity.thoughts)) },
            toolCall = entity.toolCallJson?.let { json ->
                try {
                    val segs = Json.decodeFromString<List<MessageSegment>>(json)
                    segs.lastOrNull { s -> s.type == "tool" }?.let { s ->
                        val rawResult = s.toolResult ?: ""
                        ToolCallData(s.toolName ?: "", s.toolArgs ?: "{}", SearchResultFormatter.format(rawResult, appContext))
                    }
                } catch (_: Exception) { null }
            },
            attachmentMeta = entity.attachmentMeta?.let { json ->
                try { Json.decodeFromString<AttachmentMeta>(json) } catch (_: Exception) { null }
            }
        ).also { chatMessageCache.put(key, it) }
    }

    private val localProvider = LocalProvider(appContext, settings)

    /** Embedding subsystem: model CRUD + RAG cache + single-message indexing + key resolution. */
    val ragManager = RagManager(
        conversations = convRepo,
        settings = settings,
        localProvider = localProvider,
        appContext = appContext,
        scope = viewModelScope,
    ) { _snackbarMessage.emit(it) }

    /**
     * Data export/import orchestration (native backup + Claude + GPT formats).
     * [chatDao] and [settingsManager] are passed through to [DataExporter]/[DataImporter]
     * which need raw DAO/DataStore for bulk cross-table operations.
     */
    val importExport = ImportExportManager(
        app = getApplication(),
        conversations = convRepo,
        chatDao = chatDao,
        settingsManager = settingsManager,
        memoryManager = memoryManager,
        workflowRepository = workflowRepository,
        scope = viewModelScope,
        emitSnackbar = { _snackbarMessage.emit(it) },
        onDataChanged = { refreshDataCounts() },
    )

    /** Local (on-device) chat-model configuration CRUD. */
    val modelManager = ModelManager(settings, viewModelScope)

    /** Built-in + custom provider instances, resolution, and model discovery (see [ProviderRegistry]). */
    private val providerRegistry = ProviderRegistry(settings, localProvider, viewModelScope)

    /**
     * Startup jobs deferred until all StateFlow/property backing fields are
     * initialized 鈥?avoids the constructor this-escape where a Dispatchers.IO
     * coroutine accesses a field whose JVM backing field is still null.
     */
    /** Build the proxy config from settings and push it into the shared HttpClient. */
    private fun applyProxy() {
        val host = settings.proxyHost.value.trim()
        val cfg = if (settings.proxyEnabled.value && host.isNotEmpty()) {
            com.orangeisland.app.api.HttpClient.ProxyConfig(
                type = if (settings.proxyType.value.equals("socks5", ignoreCase = true))
                    com.orangeisland.app.api.HttpClient.ProxyType.SOCKS
                else com.orangeisland.app.api.HttpClient.ProxyType.HTTP,
                host = host,
                port = settings.proxyPort.value.trim().toIntOrNull() ?: 0,
                username = settings.proxyUsername.value,
                password = settings.proxyPassword.value,
                bypass = settings.proxyBypass.value.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
            )
        } else null
        com.orangeisland.app.api.HttpClient.setProxy(cfg)
    }

    private fun startInitJobs() {
        // Apply the network proxy at startup and whenever its settings change.
        viewModelScope.launch {
            val proxyFlows = listOf(
                settings.proxyEnabled.map { it.toString() },
                settings.proxyType, settings.proxyHost, settings.proxyPort,
                settings.proxyUsername, settings.proxyPassword, settings.proxyBypass
            )
            kotlinx.coroutines.flow.combine(proxyFlows) { it }.collect { applyProxy() }
        }
        // Check for app updates on launch (at most once per day) when auto-check is enabled.
        viewModelScope.launch(Dispatchers.IO) {
            if (settings.getAutoUpdateCheck()) {
                val lastCheck = settings.getLastUpdateCheckTime()
                val now = System.currentTimeMillis()
                if (now - lastCheck > 24 * 60 * 60 * 1000L) {
                    settings.saveLastUpdateCheckTime(now)
                    when (val result = UpdateChecker.check(getCurrentVersion(), getCurrentVersionCode())) {
                        is UpdateCheckResult.Available -> _updateDialogData.value = result.info
                        is UpdateCheckResult.Error -> DebugLog.w("ChatViewModel", "Auto update check failed: ${result.reason}")
                        UpdateCheckResult.UpToDate -> { /* nothing to do */ }
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val models = settings.getEmbeddingModels()
            val activeId = settings.getActiveEmbeddingModelId()
            val active = models.find { it.id == activeId } ?: return@launch
            val total = convRepo.getAllMessagesForIndexing().count { it.text.isNotBlank() }
            val cached = convRepo.getEmbeddingCountByModel(active.id)
            val notCached = (total - cached).coerceAtLeast(0)
            if (notCached > 0 && !ragManager.cachingProgress.value.containsKey(active.id)) {
                _snackbarMessage.emit(SnackbarEvent(
                    getApplication<Application>().getString(R.string.messages_not_cached, notCached, total),
                    getApplication<Application>().getString(R.string.cache_now)
                ) { cacheMessagesForModel(active.id) })
            }
        }
        // Clean up orphaned embeddings (messages that no longer exist)
        viewModelScope.launch(Dispatchers.IO) {
            convRepo.deleteOrphanedEmbeddings()
        }
        // Sweep orphaned PDF render files (pdf_* / pdf_preview_*) left in filesDir by a
        // process death while the page-select dialog was open. At startup nothing is
        // rendering and no dialog is open, so any pdf_*.jpg not referenced by a stored
        // message's images is junk and gets deleted.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val referenced = convRepo.getAllMessagesList()
                    .asSequence()
                    .flatMap { it.images.asSequence() }
                    .map { it.removePrefix("file://") }
                    .toHashSet()
                getApplication<Application>().filesDir.listFiles { f ->
                    f.isFile && f.name.startsWith("pdf_") && f.name.endsWith(".jpg")
                }?.forEach { f ->
                    if (f.absolutePath !in referenced) runCatching { f.delete() }
                }
            } catch (e: Exception) { DebugLog.d("ChatViewModel", "PDF thumbnail cleanup error", e) }
        }
        // 鈹€鈹€ Auto Backup 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
        try { AutoBackupWorker.schedule(getApplication()) } catch (_: Exception) {}
        viewModelScope.launch(Dispatchers.IO) {
            try { autoBackupManager.checkAndBackup() } catch (e: Exception) { DebugLog.e("ChatViewModel", "Auto backup check failed", e) }
        }
        // Health sync worker scheduling follows the setting toggle reactively
        viewModelScope.launch {
            settings.healthSyncEnabled.collect { enabled ->
                if (enabled) {
                    try { HealthSyncWorker.schedule(getApplication()) } catch (_: Exception) {}
                } else {
                    try { HealthSyncWorker.cancel(getApplication()) } catch (_: Exception) {}
                }
            }
        }
        // Desktop Pet lightweight link: nudge the floating pet whenever a NEW
        // successful assistant reply appears. We watch the id of the newest MODEL
        // message in the visible list — a change means a fresh reply landed
        // (regardless of whether isLoading toggled, which stream/voice paths can
        // skip). On a new id with SUCCESS status we emit a Bubble; the pet service
        // drops it if the pet is off, so this is harmless when disabled.
        viewModelScope.launch {
            // Only nudge the pet for replies generated during an ACTIVE generation
            // pass (_isLoading == true). Every previous attempt to distinguish
            // "loaded history" from "new reply" by remembering the last MODEL id
            // (per-conversation baseline, first-emission seeding, …) was fragile:
            // history loads in multiple batches, branch switches, compaction and
            // re-queries all surface "new" ids that are actually old messages, and
            // each gap case fired a stray pet Toast. isLoading is the one signal
            // that is unambiguously true only while the user is actually generating
            // — loading a conversation never sets it — so gate on it directly.
            var nudgeForGeneration: String? = null
            // Track the conversation id of the in-flight generation so a switch
            // mid-generation can't deliver a stray nudge to the wrong conversation.
            combine(messages, _isLoading, _generatingInConversationId) { list, loading, genConvId ->
                Triple(list, loading, genConvId)
            }.collect { (list, loading, genConvId) ->
                if (loading && genConvId == _currentConversationId.value) {
                    // Mark that we owe a nudge for THIS generation. Capture the newest
                    // MODEL id seen so far so we only react to the reply belonging to
                    // this generation, not an earlier one still in the list.
                    val last = list.lastOrNull { it.participant == Participant.MODEL }
                    if (last != null) nudgeForGeneration = last.id
                } else if (!loading && nudgeForGeneration != null) {
                    // Generation just finished (isLoading went false). Find the reply we
                    // were tracking and, if it landed successfully, fire ONE nudge.
                    val target = nudgeForGeneration
                    nudgeForGeneration = null
                    val msg = list.find { it.id == target }
                    if (msg != null && msg.status == MessageStatus.SUCCESS) {
                        val summary = petBubbleSummary(msg.text)
                        DebugLog.d("ChatViewModel", "pet nudge: modelId=$target summaryLen=${summary.length}")
                        if (summary.isNotEmpty()) {
                            com.orangeisland.app.pet.PetEventBus.emit(
                                com.orangeisland.app.pet.PetEventBus.Event.Bubble(summary)
                            )
                        } else {
                            // No text to bubble (e.g. tool-only reply) — still nudge the pet.
                            com.orangeisland.app.pet.PetEventBus.emit(com.orangeisland.app.pet.PetEventBus.Event.Wave)
                        }
                    }
                }
            }
        }
        // Sync local chat models into available models
        viewModelScope.launch {
            var lastLocalIds: List<String>? = null
            var lastAliases: Map<String, String>? = null
            settings.localChatModels.collect { models ->
                val localIds = models.map { "Local:${it.modelId}" }
                val currentAliases = settings.getModelAliases()
                val aliases = currentAliases.toMutableMap()
                models.forEach { aliases["Local:${it.modelId}"] = it.alias }
                if (localIds != lastLocalIds) {
                    settings.saveAvailableModels(Constants.PROVIDER_LOCAL, localIds)
                    lastLocalIds = localIds
                }
                if (aliases != lastAliases) {
                    settings.saveModelAliases(aliases)
                    lastAliases = aliases
                }
            }
        }
        // Keep the provider map and cached model lists consistent with settings.
        providerRegistry.launchSyncJobs()
        // Drop MCP pool connections for servers that were deleted.
        viewModelScope.launch {
            settings.mcpServers.collect { servers ->
                if (mcpClientPoolLazy.isInitialized()) {
                    mcpClientPool.retainOnly(servers.map { it.id }.toSet())
                }
            }
        }
    }

    // Generation lifecycle (IO scope, current job, send gate, race-free stop/persist
    // ownership tokens) lives in [GenerationSession]; declared below once the
    // generation StateFlows it shares are initialized.

    private val generationManager by lazy {
        GenerationManager(
            app = application,
            conversations = convRepo,
            memoryManager = memoryManager,
            providers = providerRegistry.all,
            context = appContext,
            sandboxFactory = sandboxFactory,
            mcpPool = mcpClientPool,
            pluginToolProvider = pluginToolProvider,
            permissionController = permissionController,
            workflowToolProvider = workflowToolProvider,
            userInteractionGate = userInteractionGate,
            voiceCallGate = voiceCallGate
        ).also { gm ->
            gm.onMessagePersisted = { messageId, text ->
                if (settings.autoCacheEnabled.value && (settings.modelSearchMethod.value == Constants.SEARCH_METHOD_RAG || settings.manualSearchMethod.value == Constants.SEARCH_METHOD_RAG)) {
                    indexMessageForRag(messageId, text)
                }
            }
            gm.onConfirmShellCommand = { server, summary -> shellConfirmation.confirm(server, summary) }
        }
    }

    /** MCP (Model Context Protocol) client pool 鈥?one connection per configured server.
     *  Lazily created (only when first MCP tool is requested), eagerly closed in [onCleared].
     *  Lives on viewModelScope so the background reconnect coroutines die with the ViewModel.
     *  On first access it also starts the heartbeat guardian (see [McpClientPool.startMonitoring])
     *  so the MCP settings UI's three-state icon stays live between generations. */
    private val foregroundListener: (Boolean) -> Unit = { inForeground ->
        if (inForeground) {
            viewModelScope.launch {
                mcpClientPool.refreshAll(settings.mcpServers.value.filter { it.enabled })
            }
        }
    }
    private val mcpClientPoolLazy = lazy {
        com.orangeisland.app.mcp.McpClientPool(ioScope = viewModelScope).also { pool ->
            pool.startMonitoring(settings.mcpServers)
            AppForegroundTracker.addListener(foregroundListener)
        }
    }
    val mcpClientPool: com.orangeisland.app.mcp.McpClientPool get() = mcpClientPoolLazy.value

    /** Per-server MCP connection status, observed by the MCP settings UI to render the three-state
     *  leading icon (spinner / error / ok). Forces the pool lazy so monitoring is running. */
    val mcpStatuses: kotlinx.coroutines.flow.StateFlow<Map<String, com.orangeisland.app.mcp.McpStatus>>
        get() = mcpClientPool.statuses

    val sandboxManager: SandboxManager? by lazy {
        sandboxFactory?.create()
    }
    val isSandboxFlavor: Boolean = sandboxFactory?.isAvailable() == true

    override fun onCleared() {
        super.onCleared()
        sandboxManager?.close()
        localProvider.close()
        session.cancelScope()
        autoBackupManager.destroy()
        if (mcpClientPoolLazy.isInitialized()) {
            mcpClientPool.closeAll()
            AppForegroundTracker.removeListener(foregroundListener)
        }
        pluginSandbox?.closeAll()
    }

    /** JS-plugin filesystem scanner; null if plugin support wasn't injected. Exposed for the
     *  settings page to install/uninstall .zip plugin packages. */
    val pluginLoader: com.orangeisland.app.plugin.PluginLoader? get() = _pluginLoader

    /** JS-plugin runtime pool; null if plugin support wasn't injected. Exposed so the settings
     *  page can ask the sandbox to reload a plugin after its main.js is replaced on disk. */
    val pluginSandbox: com.orangeisland.app.plugin.PluginSandbox? get() = _pluginSandbox

    fun getProviderInstance(name: String): LlmProvider =
        providerRegistry.getInstance(name) ?: error("Provider '$name' is not registered")

    /** Same as [getProviderInstance] but returns null instead of throwing when the provider
     *  isn't registered — for callers on a Composable recomposition path (e.g. settings pages)
     *  where the provider may have just been deleted and the page hasn't navigated away yet. */
    fun getProviderInstanceOrNull(name: String): LlmProvider? = providerRegistry.getInstance(name)



    private val _scrollToMessage = MutableSharedFlow<String?>(replay = 0)
    val scrollToMessage = _scrollToMessage.asSharedFlow()

    /** One-shot: set when sendMessage creates a new conversation so the conversation-open
     *  auto-scroll skips once (the send's scroll-to-message already handles it), preventing
     *  a double scroll on the first message of a new chat. Consumed by ChatApp. */
    @Volatile
    var suppressNextOpenScroll: Boolean = false

    fun triggerScrollToMessage(messageId: String? = null) {
        viewModelScope.launch {
            _scrollToMessage.emit(messageId)
        }
    }

    private val _currentActiveModel = MutableStateFlow<String?>(null)
    val currentActiveModel = kotlinx.coroutines.flow.combine(_currentActiveModel, settings.selectedModel) { active, default ->
        active ?: default
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXAMPLE_MODEL_ID)

    fun getProviderForModel(modelId: String): String = providerRegistry.providerForModel(modelId)

    /**
     * Generate a short voice-call reply to [userText] using the currently active model. Resolves
     * provider/key/baseUrl the same way [probeChatCompletion] does, but collects the full streamed
     * response (no tools) so the spoken answer is complete. Used only by the Voice Call feature —
     * the call loop reads the transcript it accumulates, so a single non-tool turn is enough.
     * Returns null on any failure (the caller falls back to a spoken apology).
     */
    suspend fun generateVoiceReply(userText: String): String? = withContext(Dispatchers.IO) {
        callLlm(userText)
    }

    /**
     * Generate the AI's opening line when a voice call is answered — lets the model pick something
     * natural instead of a canned "你好，我在听". Falls back to null so the caller can use a plain
     * greeting if the LLM is unavailable.
     */
    suspend fun generateVoiceGreeting(): String? = withContext(Dispatchers.IO) {
        callLlm("（这是语音通话的开始，用户刚刚接听了你的电话。用一句简短自然的话作为开场白，比如根据当前时间打招呼、或者轻松地开启对话。不要说“我在听”或“请说”。一两句话即可。）")
    }

    /** Shared single-turn LLM call for the voice-call feature (no tools, no thinking). */
    private suspend fun callLlm(prompt: String): String? {
        val model = currentActiveModel.value
        val providerName = providerRegistry.providerForModel(model)
        val provider = runCatching { providerRegistry.getInstance(providerName) }.getOrNull()
            ?: return null
        val apiKey = settings.resolveActiveKey(providerName).orEmpty()
        val storedUrl = settings.providerBaseUrls.value[providerName]
        val baseUrl = storedUrl?.takeIf { it.isNotBlank() }
            ?: if (providerRegistry.isBuiltIn(providerName)) null else provider.defaultBaseUrl
        val modelId = com.orangeisland.app.model.ModelId.parse(model).modelName
        val config = ProviderConfig(
            apiKey = apiKey,
            modelId = modelId,
            systemPrompt = VOICE_CALL_SYSTEM_PROMPT,
            baseUrl = baseUrl,
            tools = null,
            thinkingEnabled = false,
            temperature = 0.7f
        )
        val messages = listOf(ChatMessage(
            text = prompt,
            participant = Participant.USER
        ))
        val sb = StringBuilder()
        var firstError: String? = null
        provider.generateResponse(messages, config).collect { event ->
            when (event) {
                is StreamEvent.TextChunk -> sb.append(event.text)
                is StreamEvent.Error -> { if (firstError == null) firstError = event.message }
                else -> {}
            }
        }
        if (firstError != null) {
            com.orangeisland.app.util.DebugLog.e("VoiceCall", "LLM error: $firstError")
            return null
        }
        return sb.toString().trim().ifBlank { null }
    }

    /** System prompt for voice-call replies: short, spoken, conversational. */
    private val VOICE_CALL_SYSTEM_PROMPT =
        "你正在和用户进行语音通话。请用简短、口语化、自然的中文回答，每条不超过两三句话，" +
            "不要使用 Markdown、列表或代码块，像和朋友打电话聊天一样。"
    

        
    // Embedding subsystem state lives in [ragManager]; exposed here for the UI.
    val activeEmbeddingModel get() = ragManager.activeEmbeddingModel
    val cachingProgress get() = ragManager.cachingProgress
    val cacheCounts get() = ragManager.cacheCounts
    fun loadCacheCounts() = ragManager.loadCacheCounts()

    // 鈹€鈹€ Remote shell command confirmation gate 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    /** Shell-command confirmation policy + pending-prompt handshake (see [ShellConfirmationController]). */
    private val shellConfirmation = ShellConfirmationController(settings)
    val pendingShellCommand: StateFlow<ShellConfirmationController.PendingShellCommand?>
        get() = shellConfirmation.pendingShellCommand

    /** Called by the UI to resolve a pending confirmation. */
    fun resolveShellConfirmation(allow: Boolean, alwaysAllowServer: Boolean = false) =
        shellConfirmation.resolve(allow, alwaysAllowServer)

    fun setShellConfirmEnabled(enabled: Boolean) = shellConfirmation.setEnabled(enabled)

    /** Centralized runtime + special-permission state for the Device Access tools. */
    val permissionController: PermissionController = PermissionController(appContext)

    // 鈹€鈹€ Auto Backup 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

        val conversations: StateFlow<List<ChatConversation>> = convRepo.getAllConversations()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** All projects, sorted by [ProjectEntity.sortOrder] then [ProjectEntity.createdAt]. */
    val projects: StateFlow<List<com.orangeisland.app.data.local.ProjectEntity>> =
        convRepo.getAllProjects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The project that the next new chat (via top-bar "+") will be filed under. null = the
     * chat goes to "ungrouped". Updated implicitly when the user opens a conversation that
     * belongs to a project, or explicitly when they tap a project header in the drawer.
     */
    private val _activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _allMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val allMessages: StateFlow<List<ChatMessage>> = _allMessages.asStateFlow()

    private val _isSyncingModels = MutableStateFlow(false)
    val isSyncingModels: StateFlow<Boolean> = _isSyncingModels.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<SnackbarEvent>(replay = 1)
    val snackbarMessage = _snackbarMessage.asSharedFlow()
    fun emitSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        viewModelScope.launch { _snackbarMessage.emit(SnackbarEvent(message, actionLabel, onAction)) }
    }

    private val _updateDialogData = MutableStateFlow<UpdateInfo?>(null)
    val updateDialogData: StateFlow<UpdateInfo?> = _updateDialogData.asStateFlow()
    fun dismissUpdateDialog() { _updateDialogData.value = null }
    fun showUpdateDialog(info: UpdateInfo) { _updateDialogData.value = info }

    private val _apkDownloadProgress = MutableStateFlow<Float?>(null)
    val apkDownloadProgress: StateFlow<Float?> = _apkDownloadProgress.asStateFlow()
    fun dismissApkDownloadProgress() { _apkDownloadProgress.value = null }

    /** PDF / text-file preview state (see [MediaPreviewState]). */
    private val mediaPreview = MediaPreviewState()
    val previewPdfPages: StateFlow<List<String>> get() = mediaPreview.pdfPages
    val previewPdfIndex: StateFlow<Int> get() = mediaPreview.pdfIndex
    val previewFileContent: StateFlow<String?> get() = mediaPreview.fileContent
    val previewFileName: StateFlow<String?> get() = mediaPreview.fileName

    fun showPdfPreview(pages: List<String>, startIndex: Int) = mediaPreview.showPdf(pages, startIndex)
    fun showFilePreview(fileName: String, content: String) = mediaPreview.showFile(fileName, content)
    fun clearPreviews() = mediaPreview.clear()

    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    private val _selectedChildren = MutableStateFlow<Map<String?, String>>(emptyMap())

    /**
     * The current conversation's compacted-summary state, observed reactively so the chat list
     * can inject/remove the virtual "compressed history" card the moment compression writes the
     * summary. Null when there's no current conversation or it has no summary.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val currentCompactedSummary: Flow<Pair<String, Long>?> = _currentConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else convRepo.observeConversation(id).map { c ->
                val s = c?.compactedSummary
                val w = c?.compactedUpToTimestamp
                if (s != null && w != null) s to w else null
            }
        }

    private val _messageLoadLimit = MutableStateFlow(200)
    val messageLoadLimit: StateFlow<Int> = _messageLoadLimit.asStateFlow()

    fun loadOlderMessages(count: Int = 100) {
        val current = _messageLoadLimit.value
        _messageLoadLimit.value = current + count
    }

    fun loadAllMessages() {
        _messageLoadLimit.value = Int.MAX_VALUE
    }

    val messages: StateFlow<List<ChatMessage>> = combine(
        _allMessages,
        _streamingMessage,
        _selectedChildren,
        currentCompactedSummary,
        _messageLoadLimit
    ) { allMsgs, streaming, selectedChildren, summary, limit ->
        // Single source of truth for the visible-path walk: the tested
        // ConversationUiState.resolvePath (covered by ConversationUiStateTest).
        val path = ConversationUiState.resolvePath(allMsgs, streaming, selectedChildren)
        // If this conversation has a compacted summary, prepend a virtual SYSTEM card showing
        // the summary. The card is NOT a persisted message — it's derived from the conversation's
        // compactedSummary field so it stays in sync with auto/manual compression.
        val fullPath = if (summary != null) {
            val (summaryText, watermark) = summary
            // Count how many original messages were folded in: everything on the path at or before
            // the watermark (those are the ones compression collapsed into the summary).
            val foldedCount = path.count { it.timestamp <= watermark }
            val card = ChatMessage(
                id = "compacted_card_${_currentConversationId.value}",
                text = summaryText,
                participant = Participant.SYSTEM,
                timestamp = watermark,
                contextMessageCount = foldedCount
            )
            listOf(card) + path.filter { it.timestamp > watermark || it.participant == Participant.SYSTEM }
        } else {
            path
        }
        // UI-level window: only render the tail of the conversation. The full path is still kept
        // in [_allMessages] for branch switching / context logic, so older messages can be pulled
        // in by calling [loadOlderMessages] / [loadAllMessages].
        fullPath.takeLast(limit.coerceAtLeast(1))
    }.distinctUntilChanged()
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalTokens: StateFlow<Int> = _allMessages.map { list ->
        list.sumOf { it.tokenCount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _generatingInConversationId = MutableStateFlow<String?>(null)
    val generatingInConversationId: StateFlow<String?> = _generatingInConversationId.asStateFlow()

    /** Race-free generation lifecycle: IO scope, current job, send gate, stop/persist tokens. */
    private val session = GenerationSession(
        app = application,
        convRepo = convRepo,
        settings = settings,
        isLoading = _isLoading,
        streamingMessage = _streamingMessage,
        generatingInConversationId = _generatingInConversationId,
        allMessages = _allMessages,
        currentConversationId = _currentConversationId,
        onIndexMessageForRag = ::indexMessageForRag,
        onCacheMessages = { cacheMessagesForModel(it, silent = true) },
    )

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    private var switchingJob: Job? = null

    fun setSwitching(switching: Boolean) {
        _isSwitching.value = switching
    }

    private val _isNewChatMode = MutableStateFlow(true)
    val isNewChatMode: StateFlow<Boolean> = _isNewChatMode.asStateFlow()

    private val _isTransitioningToNewChat = MutableStateFlow(false)
    val isTransitioningToNewChat: StateFlow<Boolean> = _isTransitioningToNewChat.asStateFlow()

    private val _pendingSystemPromptId = MutableStateFlow<String?>(null)
    val pendingSystemPromptId: StateFlow<String?> = _pendingSystemPromptId.asStateFlow()

    /**
     * One-shot prefilled chat input set by an outside caller (e.g. the workflow detail page's
     * "Edit in chat" button). The chat input bar observes this and consumes the value into its
     * text field, then clears it. Null means "no pending prefill".
     */
    private val _pendingPrefillInput = MutableStateFlow<String?>(null)
    val pendingPrefillInput: StateFlow<String?> = _pendingPrefillInput.asStateFlow()

    /** Set the next prefilled chat input. Consumed (and cleared) by the chat input composable. */
    fun setPendingPrefillInput(text: String?) {
        _pendingPrefillInput.value = text
    }

    /** Clear the pending prefill after the input bar has consumed it. */
    fun consumePendingPrefillInput() {
        _pendingPrefillInput.value = null
    }

    /**
     * Carries the project id of the next-to-be-created conversation. Set by [createNewChat]
     * from [_activeProjectId] and consumed by [MessageGenerationController] when it persists
     * the new chat. Mirrors [_pendingSystemPromptId]: a snapshot taken on entering new-chat
     * mode, so subsequent changes to [_activeProjectId] do not retroactively move a chat
     * that the user has already started customizing.
     */
    private val _pendingProjectId = MutableStateFlow<String?>(null)
    val pendingProjectId: StateFlow<String?> = _pendingProjectId.asStateFlow()

    fun setPendingSystemPrompt(promptId: String?) {
        _pendingSystemPromptId.value = promptId
    }

    private val _pendingConversationSettings = MutableStateFlow<ConversationSettings?>(null)
    val pendingConversationSettings: StateFlow<ConversationSettings?> = _pendingConversationSettings.asStateFlow()

    private val _branchSwitchTrigger = MutableStateFlow<String?>(null)
    val branchSwitchTrigger: StateFlow<String?> = _branchSwitchTrigger.asStateFlow()

    /**
     * Single aggregate UI state for the chat screen.
     *
     * Combines all ViewModel flows and the settings snapshot into one [StateFlow] so that
     * [ChatApp] needs only a single `collectAsState()` instead of ~45 individual subscriptions.
     * Per-conversation setting overrides are resolved here once and exposed as plain fields.
     */
    @Suppress("UNCHECKED_CAST")
    val chatUiState: StateFlow<ChatUiState> = combine(
        listOf(
            conversations,
            messages,
            allMessages,
            isLoading,
            currentConversationId,
            generatingInConversationId,
            projects,
            activeProjectId,
            isNewChatMode,
            isSwitching,
            isTransitioningToNewChat,
            totalTokens,
            currentActiveModel,
            pendingConversationSettings,
            branchSwitchTrigger,
            pendingPrefillInput,
            isSyncingModels,
            updateDialogData,
            pendingSystemPromptId,
            pendingProjectId,
            settings.chatSettingsSnapshot
        )
    ) { values ->
        val conversations = values[0] as List<ChatConversation>
        val messages = values[1] as List<ChatMessage>
        val allMessages = values[2] as List<ChatMessage>
        val isLoading = values[3] as Boolean
        val currentConversationId = values[4] as String?
        val generatingInConversationId = values[5] as String?
        val projects = values[6] as List<ProjectEntity>
        val activeProjectId = values[7] as String?
        val isNewChatMode = values[8] as Boolean
        val isSwitching = values[9] as Boolean
        val isTransitioningToNewChat = values[10] as Boolean
        val totalTokens = values[11] as Int
        val currentActiveModel = values[12] as String
        val pendingConversationSettings = values[13] as ConversationSettings?
        val branchSwitchTrigger = values[14] as String?
        val pendingPrefillInput = values[15] as String?
        val isSyncingModels = values[16] as Boolean
        val updateDialogData = values[17] as UpdateInfo?
        val pendingSystemPromptId = values[18] as String?
        val pendingProjectId = values[19] as String?
        val settings = values[20] as ChatSettingsSnapshot

        val activeProjectName = activeProjectId?.let { id -> projects.find { it.id == id }?.name }
        val convOverride = if (currentConversationId != null) {
            settings.conversationSettings[currentConversationId]
        } else {
            pendingConversationSettings
        }
        val globalWebSearch = settings.webSearchEnabled
        val globalShell = settings.shellEnabled
        val mcpServerIds = convOverride?.mcpServerIds

        ChatUiState(
            conversations = conversations,
            messages = messages,
            allMessages = allMessages,
            isLoading = isLoading,
            currentConversationId = currentConversationId,
            generatingInConversationId = generatingInConversationId,
            projects = projects,
            activeProjectId = activeProjectId,
            activeProjectName = activeProjectName,
            isNewChatMode = isNewChatMode,
            isSwitching = isSwitching,
            isTransitioningToNewChat = isTransitioningToNewChat,
            totalTokens = totalTokens,
            selectedModel = currentActiveModel,
            pendingConversationSettings = pendingConversationSettings,
            branchSwitchTrigger = branchSwitchTrigger,
            pendingPrefillInput = pendingPrefillInput,
            isSyncingModels = isSyncingModels,
            updateDialogData = updateDialogData,
            pendingSystemPromptId = pendingSystemPromptId,
            pendingProjectId = pendingProjectId,
            enabledModels = settings.enabledModels,
            modelAliases = settings.modelAliases,
            visualizeContextRollout = settings.visualizeContextRollout,
            showUsageStats = settings.showMessageUsageStats,
            codeExecutionEnabled = ChatUiState.resolveEnabled(
                settings.codeExecutionEnabled,
                convOverride?.codeExecutionEnabled
            ),
            googleSearchEnabled = ChatUiState.resolveEnabled(
                settings.googleSearchEnabled,
                convOverride?.googleSearchEnabled
            ),
            thinkingEnabled = convOverride?.thinkingEnabled ?: settings.thinkingEnabled,
            thinkingLevel = convOverride?.thinkingLevel ?: settings.thinkingLevel,
            thinkingBudgetEnabled = convOverride?.thinkingBudgetEnabled ?: settings.thinkingBudgetEnabled,
            thinkingBudgetTokens = convOverride?.thinkingBudgetTokens ?: settings.thinkingBudgetTokens,
            webSearchEnabled = ChatUiState.resolveEnabled(
                settings.webSearchEnabled,
                convOverride?.webSearchEnabled
            ),
            globalWebSearch = globalWebSearch,
            shellEnabled = ChatUiState.resolveEnabled(
                settings.shellEnabled,
                convOverride?.shellEnabled
            ),
            globalShell = globalShell,
            toolCallDisplayMode = settings.toolCallDisplayMode,
            contextWindow = convOverride?.contextWindow ?: settings.maxContextWindow,
            webSearchApiKeys = settings.webSearchApiKeys,
            shellDevices = settings.shellDevices,
            mcpServers = settings.mcpServers,
            mcpServerIds = mcpServerIds,
            blurEffectsEnabled = settings.blurEffectsEnabled,
            codeBlockWrapEnabled = settings.codeBlockWrapEnabled,
            splitBubbleByLine = settings.splitAssistantBubbleByLine,
            hapticsEnabled = settings.hapticsEnabled,
            customChatBackground = settings.customColorChatBackground,
            chatBackgroundImagePath = settings.illustrationChatBackgroundPath,
            inputBackgroundImagePath = settings.illustrationInputBackgroundPath,
            topBarBackgroundImagePath = settings.illustrationTopBarBackgroundPath,
            reasoningBackgroundImagePath = settings.illustrationReasoningBackgroundPath,
            topBarAlpha = settings.transparencyTopBar,
            topBarCapsuleScale = settings.topBarCapsuleScale,
            customInputFieldColor = settings.customColorInputField,
            customUserBubbleColor = settings.customColorUserBubble,
            userBubbleBackgroundImagePath = settings.illustrationUserBubbleBackgroundPath,
            userBubbleCornerRadius = settings.illustrationUserBubbleCornerRadius,
            customAssistantBubbleColor = settings.customColorAssistantBubble,
            customReasoningPanelColor = settings.customColorReasoningPanel,
            customChatTextColor = settings.customColorChatText,
            customGlobalTextColor = settings.customColorGlobalText,
            messageBubbleAlpha = settings.transparencyMessageBubble,
            userBubbleMaskAlpha = settings.transparencyUserBubbleMask,
            reasoningPanelAlpha = settings.transparencyReasoningPanel,
            systemPrompts = settings.systemPrompts,
            activeSystemPromptId = settings.activeSystemPromptId,
            globalSelectedModel = settings.selectedModel
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    fun setPendingConversationSettings(settings: ConversationSettings?) {
        _pendingConversationSettings.value = settings
    }

    private val payloadBuilder by lazy {
        MessagePayloadBuilder(
            generationManager = generationManager,
            onSnackbar = { msg -> _snackbarMessage.emit(SnackbarEvent(msg)) },
        )
    }

    private val requestBuilder = GenerationRequestBuilder(
        settings = settings,
        convRepo = convRepo,
        memoryManager = memoryManager,
        providerRegistry = providerRegistry,
        ragManager = ragManager,
        appContext = appContext,
        currentActiveModel = currentActiveModel,
        pendingConversationSettings = _pendingConversationSettings,
        onSnackbar = { msg -> emitSnackbar(msg) },
        appContextCollector = appContextCollector,
    )

    private val generationController by lazy {
        MessageGenerationController(
            viewModelScope = viewModelScope,
            application = getApplication(),
            appContext = appContext,
            convRepo = convRepo,
            settings = settings,
            session = session,
            generationManagerProvider = { generationManager },
            requestBuilder = requestBuilder,
            payloadBuilder = payloadBuilder,
            providerRegistry = providerRegistry,
            localProvider = localProvider,
            allMessages = _allMessages,
            selectedChildren = _selectedChildren,
            streamingMessage = _streamingMessage,
            currentConversationId = _currentConversationId,
            isLoading = _isLoading,
            generatingInConversationId = _generatingInConversationId,
            isNewChatMode = _isNewChatMode,
            pendingConversationSettings = _pendingConversationSettings,
            pendingSystemPromptId = _pendingSystemPromptId,
            pendingProjectId = _pendingProjectId,
            currentActiveModel = currentActiveModel,
            messages = messages,
            onScrollToMessage = { id -> triggerScrollToMessage(id) },
            onSnackbar = { msg -> emitSnackbar(msg) },
            onSnackbarSuspend = { msg -> _snackbarMessage.emit(SnackbarEvent(msg)) },
            onPersistSelectedChildren = { convId, map -> persistSelectedChildren(convId, map) },
            onConversationCreatedBySend = { suppressNextOpenScroll = true },
        )
    }

    fun updateConversationSetting(convId: String?, update: (ConversationSettings) -> ConversationSettings) {
        if (convId != null) {
            val current = settings.conversationSettings.value[convId] ?: ConversationSettings()
            settings.setConversationSettings(convId, update(current))
        } else {
            val current = _pendingConversationSettings.value ?: ConversationSettings()
            _pendingConversationSettings.value = update(current)
        }
    }


    fun clearBranchSwitchTrigger() {
        _branchSwitchTrigger.value = null
    }

    // Export/Import state lives in [importExport]; exposed here for the UI.
    val exportProgress get() = importExport.exportProgress
    val importProgress get() = importExport.importProgress
    val importManifest get() = importExport.importManifest
    val importPreview get() = importExport.importPreview
    val claudeImportPreview get() = importExport.claudeImportPreview
    val claudeImportProgress get() = importExport.claudeImportProgress
    val claudeImportResult get() = importExport.claudeImportResult
    val gptImportPreview get() = importExport.gptImportPreview
    val gptImportProgress get() = importExport.gptImportProgress
    val gptImportResult get() = importExport.gptImportResult


    private val _conversationCount = MutableStateFlow(0)
    val conversationCount: StateFlow<Int> = _conversationCount.asStateFlow()

    private val _memoryCount = MutableStateFlow(0)
    val memoryCount: StateFlow<Int> = _memoryCount.asStateFlow()

    private val _systemPromptCount = MutableStateFlow(0)
    val systemPromptCount: StateFlow<Int> = _systemPromptCount.asStateFlow()

    private val _workflowCount = MutableStateFlow(0)
    val workflowCount: StateFlow<Int> = _workflowCount.asStateFlow()

    init {
        startInitJobs()

        // Restore the last active conversation on cold start when the setting is enabled.
        // Must use suspend await to read the persisted value, not the eagerly-shared
        // StateFlow default — otherwise the toggle appears to be ignored at startup.
        viewModelScope.launch {
            if (settings.awaitRememberLastConversation()) {
                val lastId = settings.awaitLastActiveConversationId()
                if (lastId != null && convRepo.getConversation(lastId) != null) {
                    _isNewChatMode.value = false
                    _currentConversationId.value = lastId
                    val conversation = convRepo.getConversation(lastId)
                    _currentActiveModel.value = conversation?.modelId
                    _activeProjectId.value = conversation?.projectId
                }
            }
        }

        viewModelScope.launch {
            _currentConversationId.collectLatest { id ->
                if (id != null) {
                    // Fix stuck sending states when loading conversation 鈥?skip if currently generating
                    if (!_isLoading.value) {
                        val stuckMessages = convRepo.getStuckMessagesForConversation(id)
                        stuckMessages.forEach { msg ->
                            convRepo.upsertMessage(msg.copy(status = MessageStatus.STOPPED))
                        }
                    }

                    // Restore selected branches
                    val conversation = convRepo.getConversation(id)
                    if (conversation?.selectedBranchesJson != null) {
                        try {
                            val map = Json.decodeFromString<Map<String, String>>(conversation.selectedBranchesJson)
                            val decodedMap = map.mapKeys { if (it.key == "null") null else it.key }
                            _selectedChildren.value = decodedMap
                        } catch (e: Exception) {
                            _selectedChildren.value = emptyMap()
                        }
                    } else {
                        _selectedChildren.value = emptyMap()
                    }

                                        convRepo.getMessagesForConversation(id).collect { entities ->
                        val mapped = entities.map { mapMessageEntity(it) }
                        // Backfill toolCall for old result_ messages persisted without toolCallJson.
                        // They inherit the parent tool_ message's ToolCallData so the provider can
                        // format them as proper "tool" role messages with matching tool_call_id.
                        val mappedById = mapped.associateBy { it.id }
                        _allMessages.value = mapped.map { msg ->
                            if (msg.id.startsWith(Constants.RESULT_MSG_PREFIX) && msg.toolCall == null) {
                                val parentTool = msg.parentId?.let { mappedById[it] }
                                if (parentTool != null && parentTool.toolCall != null) {
                                    msg.copy(toolCall = parentTool.toolCall)
                                } else msg
                            } else msg
                        }
                    }
                } else {
                    _allMessages.value = emptyList()
                    _selectedChildren.value = emptyMap()
                }
            }
        }
        
        // Remember the current conversation ID whenever it changes (excluding "new chat" mode).
        // Use combine so turning the toggle ON while already inside a conversation also
        // triggers a write — the old plain .collect on _currentConversationId alone missed this.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                _currentConversationId,
                settings.rememberLastConversation
            ) { id, remember -> id to remember }
                .collect { (id, remember) ->
                    if (id != null && remember) {
                        settings.setLastActiveConversationId(id)
                    }
                }
        }

        viewModelScope.launch {
            _selectedChildren.collect { childrenMap ->
                val id = _currentConversationId.value
                if (id != null) {
                    persistSelectedChildren(id, childrenMap)
                }
            }
        }

        // Wire plugin-sent messages into the chat generation pipeline.
        pluginMemoryProvider?.onMessageSent = { conversationId, text ->
            viewModelScope.launch {
                val originalId = _currentConversationId.value
                if (originalId != conversationId) {
                    _currentConversationId.value = conversationId
                    // Clear new-chat mode so MessageGenerationController does not spawn
                    // a brand-new conversation and strand the AI reply there.
                    _isNewChatMode.value = false
                    _isTransitioningToNewChat.value = false
                }
                sendMessage(text)
            }
        }
    }

    private suspend fun persistSelectedChildren(conversationId: String, childrenMap: Map<String?, String>) {
        convRepo.saveBranchSelections(conversationId, childrenMap)
    }

    // 鈹€鈹€ Custom providers 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    // Settings persistence lives in SettingsRepository; ChatViewModel only maintains
    // the live in-memory provider instances (the `providers` map) via callbacks.
    fun addCustomProvider(name: String, baseUrl: String) = providerRegistry.addCustom(name, baseUrl)
    fun renameCustomProvider(oldName: String, newName: String) = providerRegistry.renameCustom(oldName, newName)
    fun deleteCustomProvider(name: String) = providerRegistry.deleteCustom(name)

    // 鈹€鈹€ Manual models (custom providers only) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    fun addManualModel(provider: String, modelId: String) = settings.addManualModel(provider, modelId)
    fun removeManualModel(provider: String, modelId: String) = settings.removeManualModel(provider, modelId)
    fun isManualModelTaken(provider: String, modelId: String): Boolean {
        val prefixed = if (modelId.startsWith("$provider:")) modelId else "$provider:$modelId"
        return settings.manualModels.value[provider].orEmpty().contains(prefixed)
    }

    /**
     * Verifies a provider can actually serve chat completions.
     *
     * Strategy:
     *  - If the provider has manually-added models, send a minimal real chat/completions
     *    request with the first manual model and wait for the first streamed chunk. This is
     *    the only reliable way to validate providers that have no (or empty) /models endpoint —
     *    the exact scenario manual models exist for. Returns OK as soon as a chunk arrives;
     *    the request is cancelled immediately afterwards.
     *  - Otherwise, fall back to GET /models (cheap, lists available models).
     *
     * Unlike [fetchModelsForProvider] this surfaces concrete failure reasons (timeout,
     * unknown host, HTTP error) instead of silently returning an empty list. Returns a
     * human-readable status string; "OK ..." indicates success.
     */
    suspend fun testProviderConnection(providerName: String): String = withContext(Dispatchers.IO) {
        if (providerName == Constants.PROVIDER_LOCAL)
            return@withContext appContext.getString(R.string.provider_test_empty)
        providerRegistry.ensureCustomProvidersRegistered()
        val provider = providerRegistry.all[providerName]
            ?: return@withContext appContext.getString(R.string.provider_test_no_provider)
        val apiKey = settings.resolveActiveKey(providerName).orEmpty()
        val storedUrl = settings.providerBaseUrls.value[providerName]
        val baseUrl = storedUrl?.takeIf { it.isNotBlank() }
            ?: if (providerRegistry.isBuiltIn(providerName)) null else provider.defaultBaseUrl
            ?: return@withContext appContext.getString(R.string.provider_test_no_url)

        // Manual models exist precisely because /models can't be trusted for this provider —
        // probe chat directly with the first one rather than relying on the model list.
        val firstManualModel = settings.manualModels.value[providerName]
            ?.firstOrNull()
            ?.substringAfter("$providerName:")
        if (firstManualModel != null) {
            return@withContext probeChatCompletion(provider, apiKey, baseUrl, firstManualModel)
        }

        try {
            val models = kotlinx.coroutines.withTimeout(Constants.MODEL_FETCH_TIMEOUT_MS) {
                provider.fetchModels(apiKey, baseUrl)
            }
            if (models.isNotEmpty()) appContext.getString(R.string.provider_test_ok, models.size)
            else appContext.getString(R.string.provider_test_empty)
        } catch (e: java.net.SocketTimeoutException) {
            appContext.getString(R.string.provider_test_timeout)
        } catch (e: java.net.UnknownHostException) {
            appContext.getString(R.string.provider_test_unknown_host)
        } catch (e: java.net.ConnectException) {
            appContext.getString(R.string.provider_test_connect_failed)
        } catch (e: Exception) {
            e.message ?: appContext.getString(R.string.provider_test_empty)
        }
    }

    /**
     * Sends a one-message chat completion to [provider] and waits for the first streamed event.
     * Returns a localized status string. Any content/tool/usage event counts as success — we
     * don't need the full response, just proof the endpoint is reachable and the model/key work.
     *
     * Uses `Flow.first { }` so upstream is cancelled the instant we see a usable event; a chatty
     * reasoning model can't drag the probe out beyond its first token.
     */
    private suspend fun probeChatCompletion(
        provider: LlmProvider,
        apiKey: String,
        baseUrl: String?,
        modelId: String,
    ): String {
        val probeMessage = ChatMessage(text = "ping", participant = Participant.USER)
        val config = ProviderConfig(
            apiKey = apiKey,
            modelId = modelId,
            baseUrl = baseUrl,
            // Keep the probe cheap: cap output, skip tools/thinking extras.
            maxTokens = 1,
            tools = null,
            temperature = 0f,
        )
        val firstEvent = try {
            kotlinx.coroutines.withTimeout(Constants.CHAT_PROBE_TIMEOUT_MS) {
                provider.generateResponse(listOf(probeMessage), config).first { event ->
                    event !is StreamEvent.Retrying
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            return appContext.getString(R.string.provider_test_timeout)
        } catch (e: java.net.UnknownHostException) {
            return appContext.getString(R.string.provider_test_unknown_host)
        } catch (e: java.net.ConnectException) {
            return appContext.getString(R.string.provider_test_connect_failed)
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            return appContext.getString(R.string.provider_test_timeout)
        } catch (e: NoSuchElementException) {
            // Flow ended without emitting a usable event (e.g. only retries, then EOF).
            return appContext.getString(R.string.provider_test_empty)
        } catch (e: Exception) {
            return e.message ?: appContext.getString(R.string.provider_test_empty)
        }
        return when (firstEvent) {
            is StreamEvent.TextChunk,
            is StreamEvent.ThoughtChunk,
            is StreamEvent.UsageUpdate,
            is StreamEvent.ToolCallRequest,
            is StreamEvent.ToolCallsRequest ->
                appContext.getString(R.string.provider_test_chat_ok, modelId)
            is StreamEvent.Error ->
                firstEvent.message.ifBlank { appContext.getString(R.string.provider_test_empty) }
            else -> appContext.getString(R.string.provider_test_empty)
        }
    }

    fun getCurrentVersion(): String {
        return try { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
    }
    fun getCurrentVersionCode(): Int {
        return try {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            info.longVersionCode.toInt()
        } catch (_: Exception) { 0 }
    }

    /**
     * Manually check for updates from the About screen.
     * Shows the update dialog if a newer release is found, otherwise posts a
     * "you're up to date" or error snackbar.
     */
    suspend fun triggerManualUpdateCheck() {
        val current = getCurrentVersion()
        val currentCode = getCurrentVersionCode()
        when (val result = withContext(Dispatchers.IO) { UpdateChecker.check(current, currentCode) }) {
            is UpdateCheckResult.Available -> {
                _updateDialogData.value = result.info
                // Record the check time so auto-check won't also fire immediately.
                settings.saveLastUpdateCheckTime(System.currentTimeMillis())
            }
            UpdateCheckResult.UpToDate -> {
                emitSnackbar(getApplication<Application>().getString(R.string.about_up_to_date, current))
            }
            is UpdateCheckResult.Error -> {
                emitSnackbar(getApplication<Application>().getString(R.string.about_check_error, result.reason))
            }
        }
    }

    /**
     * Download the APK with an in-app progress bar, then open the system package
     * installer. Falls back to browser if anything goes wrong.
     */
    fun downloadAndInstallApk(url: String, version: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _apkDownloadProgress.value = 0f
                val file = File(appContext.cacheDir, "shared/orange-island-$version.apk")
                file.parentFile?.mkdirs()

                val request = Request.Builder().url(url).build()
                HttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Server returned ${response.code}")
                    }
                    val total = response.body.contentLength()
                    response.body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    _apkDownloadProgress.value = downloaded.toFloat() / total
                                }
                            }
                        }
                    }
                }

                _apkDownloadProgress.value = 1f
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                appContext.startActivity(intent)
                _apkDownloadProgress.value = null
            } catch (e: Exception) {
                DebugLog.e("ChatViewModel", "APK download failed", e)
                _apkDownloadProgress.value = null
                emitSnackbar("APK download failed: ${e.message}")
                // Fall back to opening the URL in the browser.
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                appContext.startActivity(intent)
            }
        }
    }

    fun addEmbeddingModel(config: EmbeddingModelConfig) = ragManager.addEmbeddingModel(config)
    fun deleteEmbeddingModel(id: String) = ragManager.deleteEmbeddingModel(id)
    fun renameEmbeddingModel(id: String, newName: String, batchSize: Int? = null) =
        ragManager.renameEmbeddingModel(id, newName, batchSize)
    fun setActiveEmbeddingModel(id: String) = ragManager.setActiveEmbeddingModel(id)
    fun cacheMessagesForModel(modelId: String, recache: Boolean = false, silent: Boolean = false) =
        ragManager.cacheMessagesForModel(modelId, recache, silent)

    fun isLocalModelIdTaken(modelId: String, excludeId: String? = null) =
        modelManager.isLocalModelIdTaken(modelId, excludeId)
    fun addLocalChatModel(config: LocalChatModelConfig) = modelManager.addLocalChatModel(config)
    fun deleteLocalChatModel(uuid: String) = modelManager.deleteLocalChatModel(uuid)
    fun updateLocalChatModel(
        uuid: String, newModelId: String, newAlias: String, nCtx: Int, temperature: Float, topP: Float, maxTokens: Int,
        mmprojPath: String = ""
    ) = modelManager.updateLocalChatModel(uuid, newModelId, newAlias, nCtx, temperature, topP, maxTokens, mmprojPath)

    suspend fun semanticSearch(query: String, limit: Int = 20, projectId: String? = null): List<Pair<MessageEntity, Float>> {
        val ctx = GenerationContext(
            accessSavedMemories = settings.accessSavedMemories.value,
            accessActiveMemory = settings.accessActiveMemory.value,
            accessPastConversations = settings.accessPastConversations.value,
            modelSearchMethod = settings.modelSearchMethod.value,
            activeEmbeddingConfig = activeEmbeddingModel.value,
            embeddingApiKey = ragManager.resolveEmbeddingApiKey() ?: "",
            ragThreshold = settings.ragThreshold.value,
            searchMatchLimit = settings.searchMatchLimit.value,
            searchContextWindow = settings.searchContextWindow.value,
            webSearchEnabled = settings.webSearchEnabled.value,
            webSearchApiKeys = settings.webSearchApiKeys.value,
            webSearchProvider = settings.webSearchProvider.value,
            webSearchNumResults = settings.webSearchNumResults.value,
            webSearchBaseUrl = settings.webSearchBaseUrl.value,
            projectId = projectId
        )
        return generationManager.semanticSearch(query, limit, ctx)
    }

    fun resolveEmbeddingKeyForProviderExact(targetProvider: String) =
        ragManager.resolveEmbeddingKeyForProviderExact(targetProvider)

    fun indexMessageForRag(messageId: String, text: String) = ragManager.indexMessageForRag(messageId, text)

    /** Keyword search scoped by [projectId]: null = global (ungrouped only), non-null = that project. */
    suspend fun searchMessages(query: String, limit: Int = 20, projectId: String? = null) =
        convRepo.searchMessagesScoped(query, projectId, limit)
    // 鈹€鈹€ Auto Backup 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.saveAutoBackupEnabled(enabled)
            if (enabled) {
                try { AutoBackupWorker.schedule(getApplication()) } catch (_: Exception) {}
            } else {
                try { AutoBackupWorker.cancel(getApplication()) } catch (_: Exception) {}
            }
        }
    }
    fun setAutoBackupPeriodHours(hours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.saveAutoBackupPeriodHours(hours)
            // Enforce: auto-delete period must be strictly greater than backup period
            val deleteTiers = AUTO_DELETE_TIERS_HOURS
            val deleteHours = settings.autoDeletePeriodHours.value
            if (deleteHours <= hours) {
                val nextDelete = deleteTiers.firstOrNull { it > hours } ?: AUTO_DELETE_TIERS_HOURS.last()
                settings.saveAutoDeletePeriodHours(nextDelete)
            }
        }
    }
    fun setAutoBackupCategories(categories: String) {
        viewModelScope.launch(Dispatchers.IO) { settings.saveAutoBackupCategories(categories) }
    }
    fun setAutoBackupDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) { settings.saveAutoBackupDirectory(path) }
    }
    fun setAutoDeleteEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settings.saveAutoDeleteEnabled(enabled) }
    }
    fun setAutoDeletePeriodHours(hours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val backupHours = settings.autoBackupPeriodHours.value
            val deleteTiers = AUTO_DELETE_TIERS_HOURS
            // Find the smallest valid delete tier that is > backupHours, and >= the requested hours
            val minValid = deleteTiers.firstOrNull { it > backupHours } ?: AUTO_DELETE_TIERS_HOURS.last()
            settings.saveAutoDeletePeriodHours(maxOf(hours, minValid))
        }
    }
    fun addShellDevice(device: ShellDeviceConfig) {
        settings.addShellDevice(device)
    }
    fun updateShellDevice(device: ShellDeviceConfig) {
        settings.updateShellDevice(device)
    }

    /**
     * Connects to an SSH host in capture mode and returns the server host key
     * (base64) together with its SHA-256 fingerprint, for the user to review and
     * pin. The host key is exchanged before authentication, so this succeeds even
     * if the password is wrong 鈥?letting the user pin the key first.
     */
    suspend fun verifySshHostKey(
        host: String, port: Int, user: String, password: String, timeoutSec: Int
    ): Result<Pair<String, String>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext Result.failure(Exception("Host is empty"))
        val client = SshClient(
            host, port, user.ifBlank { "root" }, password, timeoutSec * 1000,
            pinnedHostKey = "", allowUnknownHostKey = true
        )
        try {
            client.executeCommand("true")
        } catch (_: Exception) {
            // Ignore 鈥?the host key is captured during the handshake regardless of auth result.
        } finally {
            client.close()
        }
        val key = client.capturedHostKey
        if (key.isNullOrBlank()) Result.failure(Exception("Could not reach host or no host key presented"))
        else Result.success(key to SshClient.fingerprintSha256(key))
    }
    suspend fun testRemoteEmbedding(modelName: String, baseUrl: String, apiKey: String = ""): String? {
        val effectiveKey = apiKey.ifBlank { ragManager.resolveEmbeddingApiKey() ?: "" }
        val url = baseUrl.ifBlank { ragManager.resolveEmbeddingBaseUrl() }
        return withContext(Dispatchers.IO) {
            try {
                val result = EmbeddingClient.computeEmbedding("test connection", effectiveKey, modelName, url)
                if (result != null) "OK (dim=${result.size})" else "Request failed. Check API key, URL, and model name."
            } catch (e: Exception) {
                e.message ?: "Error"
            }
        }
    }

    /**
     * Verifies an MCP server is reachable and reports its tool count. Like
     * [testProviderConnection] it surfaces concrete failure reasons. Invalidates any stale
     * cached connection first so the probe always reflects the current [config].
     */
    suspend fun testMcpConnection(config: com.orangeisland.app.data.McpServerConfig): String = withContext(Dispatchers.IO) {
        mcpClientPool.invalidate(config.id)
        try {
            val tools = mcpClientPool.listTools(config)
            if (tools.isNotEmpty()) appContext.getString(R.string.mcp_test_ok, tools.size)
            else appContext.getString(R.string.mcp_test_no_tools)
        } catch (e: java.net.SocketTimeoutException) {
            appContext.getString(R.string.mcp_test_timeout)
        } catch (e: java.net.ConnectException) {
            appContext.getString(R.string.mcp_test_connect_failed)
        } catch (e: Exception) {
            e.message ?: appContext.getString(R.string.mcp_test_error)
        }
    }

    /**
     * Fetches the tool list for [config]. listTools() internally swallows connection
     * errors and returns an empty list on failure (by design, so a broken MCP server
     * doesn't abort a whole chat turn) — so an empty result here can mean either
     * "genuinely no tools" or "couldn't connect"; the UI shows a generic message
     * covering both cases.
     */
    suspend fun fetchMcpTools(config: com.orangeisland.app.data.McpServerConfig):
        List<io.modelcontextprotocol.kotlin.sdk.types.Tool> = withContext(Dispatchers.IO) {
        mcpClientPool.listTools(config)
    }

    /** Triggers a manual refresh of all enabled MCP connections. Called from the MCP settings
     *  page's top-bar refresh button. */
    fun refreshAllMcpConnections() {
        viewModelScope.launch {
            mcpClientPool.refreshAll(settings.mcpServers.value.filter { it.enabled })
        }
    }

    fun createNewChat() {
        UsageLogManager.log(
            UsageLogManager.Type.CONVERSATION,
            "create_new_chat",
            "projectId=${_activeProjectId.value}"
        )
        switchingJob?.cancel()
        // Snapshot the active project once, when first entering new-chat mode. Subsequent
        // changes to _activeProjectId (e.g. the user taps another project header while
        // already composing) do NOT override what's already on screen. Mirrors the
        // _pendingSystemPromptId rule just below.
        val activeProject = if (!_isNewChatMode.value) {
            val proj = _activeProjectId.value?.let { id -> projects.value.find { it.id == id } }
            _pendingProjectId.value = proj?.id
            // Project defaults become the starting point; null keeps the global default.
            _pendingSystemPromptId.value = proj?.systemPromptId
            proj
        } else {
            _activeProjectId.value?.let { id -> projects.value.find { it.id == id } }
        }
        _isNewChatMode.value = true
        _isTransitioningToNewChat.value = true
        _isSwitching.value = true
        // Apply data changes immediately so content is visible right away;
        // the overlay fade-out plays in the background.
        _currentConversationId.value = null
        _currentActiveModel.value = activeProject?.modelId
        _pendingConversationSettings.value = null
        _allMessages.value = emptyList()
        _selectedChildren.value = emptyMap()
        _branchSwitchTrigger.value = null
        _messageLoadLimit.value = 200
        switchingJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SWITCH_OVERLAY_FADE_MS)
            _isSwitching.value = false
            _isTransitioningToNewChat.value = false
        }
    }

    fun selectConversation(id: String) {
        if (_currentConversationId.value == id && !_isNewChatMode.value) return

        switchingJob?.cancel()
        _isTransitioningToNewChat.value = false
        _isSwitching.value = true
        switchingJob = viewModelScope.launch {
            _isNewChatMode.value = false
            _branchSwitchTrigger.value = null
            _messageLoadLimit.value = 200
            _currentConversationId.value = id
            val conversation = convRepo.getConversation(id)
            _currentActiveModel.value = conversation?.modelId
            // Opening a conversation inside a project makes that project the active context,
            // so the top-bar "+" continues to file new chats under the same project.
            _activeProjectId.value = conversation?.projectId
            triggerScrollToMessage()
            // Hold the switching overlay until BOTH conditions hold: the conversation's
            // messages have actually loaded (so we don't drop the overlay onto a
            // half-rendered list — the "freeze for a beat" on chat open) AND a minimum
            // visible duration has elapsed (so the spinner is actually seen for cached
            // conversations that load in a few ms — without this it flashed too briefly
            // for the AnimatedVisibility fadeIn to even render).
            val minShowMs = 280L
            val startMs = System.currentTimeMillis()
            try {
                withTimeout(SWITCH_OVERLAY_FADE_MS * 10) { // 2s cap
                    messages.first { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                // Timeout (empty or slow conversation) — proceed anyway.
            }
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < minShowMs) {
                kotlinx.coroutines.delay(minShowMs - elapsed)
            }
            // A short grace period so the LazyColumn has a frame to lay out before
            // the overlay fades, avoiding a single-frame flash of unpositioned items.
            kotlinx.coroutines.delay(SWITCH_OVERLAY_FADE_MS)
            _isSwitching.value = false
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            val existing = convRepo.getConversation(id)
            if (existing != null) {
                convRepo.upsertConversation(existing.copy(title = newTitle))
            }
        }
    }

    fun generateTitle(conversationId: String) = generationController.generateTitle(conversationId)

    /** Manually compress a conversation's older history into a summary card, retaining the most
     *  recent `maxContextWindow` user turns. Same path as auto-compress, just triggered on demand
     *  from the conversation's long-press menu. */
    fun compressHistory(conversationId: String) = generationController.compressHistory(conversationId, isManual = true)

    fun setConversationSystemPrompt(id: String, promptId: String?) {
        viewModelScope.launch {
            val existing = convRepo.getConversation(id)
            if (existing != null) {
                convRepo.upsertConversation(existing.copy(systemPromptId = promptId))
            }
        }
    }

    /**
     * Notify the environment tracker when a system prompt is *edited* (content changed,
     * not just switched). Only records the event when the edited prompt is currently
     * in effect — either as the global default or as the active conversation's prompt.
     */
    fun onSystemPromptEdited(promptId: String, title: String) {
        val isGlobalActive = settings.activeSystemPromptId.value == promptId
        val conv = _currentConversationId.value?.let { cid ->
            conversations.value.find { it.id == cid }
        }
        val isConversationActive = conv?.systemPromptId == promptId
        // Also cover the case where the conversation falls back to the global default
        // and that global default is the one being edited.
        val isFallbackGlobalActive = conv?.systemPromptId == null && isGlobalActive
        if (isGlobalActive || isConversationActive || isFallbackGlobalActive) {
            appContextCollector?.logSystemPromptChange(title)
        }
    }

    fun setActiveModel(model: String) {
        _currentActiveModel.value = model
        _currentConversationId.value?.let { id ->
            viewModelScope.launch {
                val existing = convRepo.getConversation(id)
                if (existing != null) {
                    convRepo.upsertConversation(existing.copy(modelId = model))
                }
            }
        }
    }

    fun deleteConversation(id: String) {
        UsageLogManager.log(
            UsageLogManager.Type.CONVERSATION,
            "delete_conversation",
            "conversationId=$id"
        )
        val stopFinalization = if (_currentConversationId.value == id) {
            session.stop()
        } else null
        viewModelScope.launch(Dispatchers.IO) {
            stopFinalization?.join()
            convRepo.deleteConversation(id)
            if (settings.lastActiveConversationId.value == id) {
                settings.setLastActiveConversationId(null)
            }
            if (_currentConversationId.value == id) createNewChat()
        }
    }

    // ── Projects ─────────────────────────────────────────────

    /**
     * Creates a project, makes it the active context (so the next "+" chat lands inside
     * it), and returns its id. [modelId] / [systemPromptId], when non-null, become the
     * project-level defaults that new chats inside it inherit.
     */
    fun createProject(name: String, modelId: String? = null, systemPromptId: String? = null, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = convRepo.createProject(name.trim().ifBlank { appContext.getString(R.string.project_default_name) }, systemPromptId, modelId)
            _activeProjectId.value = id
            onSuccess(id)
        }
    }

    fun renameProject(id: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) { convRepo.renameProject(id, name.trim()) }
    }

    fun setProjectDefaults(id: String, systemPromptId: String?, modelId: String?) {
        viewModelScope.launch(Dispatchers.IO) { convRepo.setProjectDefaults(id, systemPromptId, modelId) }
    }

    /** Deletes the project; member conversations fall back to ungrouped (none are lost). */
    fun deleteProject(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            convRepo.deleteProject(id)
            if (_activeProjectId.value == id) _activeProjectId.value = null
        }
    }

    /** Moves a conversation into [projectId] (null = move out to ungrouped). */
    fun moveConversation(conversationId: String, projectId: String?) {
        viewModelScope.launch(Dispatchers.IO) { convRepo.moveConversation(conversationId, projectId) }
    }

    /** Tap a project header in the drawer to make it the active context for new chats. */
    fun setActiveProject(projectId: String?) {
        _activeProjectId.value = projectId
    }

    // ── Project-private memory ───────────────────────────────
    // Wraps MemoryManager with the project id so the UI doesn't have to thread it through.
    // Files land in /memory_db_projects/<projectId>/ — invisible outside the project.

    suspend fun listProjectMemoryFiles(projectId: String) =
        memoryManager.listFiles(projectId)

    fun createProjectMemoryFile(projectId: String, name: String, content: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryManager.createFile(name, content, description, projectId)
        }
    }

    fun editProjectMemoryFile(projectId: String, name: String, content: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryManager.editFile(name, content = content, description = description, projectId = projectId)
        }
    }

    fun deleteProjectMemoryFile(projectId: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryManager.deleteFile(name, projectId)
        }
    }

    /**
     * Deletes a message and all its descendants (BFS cascade).
     * Hidden tool_/result_ children are included in the cascade.
     * Attachments, embeddings, and branch selections are cleaned up.
     * Returns the count of deleted messages (for the confirmation dialog).
     */
    fun deleteMessage(messageId: String): Int = generationController.deleteMessage(messageId)

    fun stopGeneration() = session.stop()

    fun regenerate(messageId: String) {
        UsageLogManager.log(
            UsageLogManager.Type.CONVERSATION,
            "regenerate",
            "conversationId=${_currentConversationId.value}, messageId=$messageId"
        )
        generationController.regenerate(messageId)
    }

    fun switchBranch(parentId: String?, currentMessageId: String, direction: Int) {
        if (_isLoading.value && _generatingInConversationId.value == _currentConversationId.value) return
        // Branch targets may be far back in the conversation; ensure the full visible path is
        // loaded so the switch and subsequent scroll can locate the target message.
        loadAllMessages()
        val siblings = _allMessages.value.filter { it.parentId == parentId && !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX) }.sortedBy { it.timestamp }
        if (siblings.size < 2) return
        var currentIndex = siblings.indexOfFirst { it.id == currentMessageId }
        if (currentIndex == -1) {
            val selectedId = _selectedChildren.value[parentId]
            currentIndex = siblings.indexOfFirst { it.id == selectedId }
        }
        if (currentIndex == -1) return
        val newIndex = (currentIndex + direction).coerceIn(0, siblings.size - 1)
        if (newIndex == currentIndex) return
        
        switchingJob?.cancel()
        _isSwitching.value = true
        switchingJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SWITCH_OVERLAY_FADE_MS) // Allow overlay to fade in
            val newMap = _selectedChildren.value.toMutableMap()
            val targetMessage = siblings[newIndex]
            newMap[parentId] = targetMessage.id
            _selectedChildren.value = newMap
            
            _branchSwitchTrigger.value = null
            _branchSwitchTrigger.value = targetMessage.id
        }
    }

    fun editMessage(messageId: String, newText: String) {
        UsageLogManager.log(
            UsageLogManager.Type.CONVERSATION,
            "edit_message",
            "conversationId=${_currentConversationId.value}, messageId=$messageId, length=${newText.length}"
        )
        generationController.editMessage(messageId, newText)
    }

    /** Text-only correction for an AI reply — creates a new branch, never calls the model. */
    fun editAssistantMessage(messageId: String, newText: String) {
        UsageLogManager.log(
            UsageLogManager.Type.CONVERSATION,
            "edit_assistant_message",
            "conversationId=${_currentConversationId.value}, messageId=$messageId, length=${newText.length}"
        )
        generationController.editAssistantMessage(messageId, newText)
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String {
        return try {
            val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) ?: uri.lastPathSegment ?: "unknown"
                    else uri.lastPathSegment ?: "unknown"
                } else uri.lastPathSegment ?: "unknown"
            } ?: (uri.lastPathSegment ?: "unknown")
        } catch (_: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }

    fun sendMessage(text: String, images: List<String> = emptyList(), attachments: List<SelectedAttachment> = emptyList()): Boolean {
        UsageLogManager.log(
            UsageLogManager.Type.CONVERSATION,
            "send_message",
            "conversationId=${_currentConversationId.value}, length=${text.length}, images=${images.size}, attachments=${attachments.size}"
        )
        return generationController.sendMessage(text, images, attachments)
    }

    /**
     * Persist a finished voice-call transcript into the current conversation as a single assistant
     * message, parented on the conversation's current tail message so it joins the visible chat
     * branch (not a new disconnected branch). The call's content then becomes part of the chat
     * history (and thus the AI's memory / RAG context) for future turns. No-op when there's no
     * open conversation or the transcript is empty.
     */
    suspend fun saveCallTranscript(transcript: List<Pair<String, String>>) {
        android.util.Log.e("VoiceCallDebug", "saveCallTranscript: turns=${transcript.size} convId=${_currentConversationId.value}")
        if (transcript.isEmpty()) return
        val convId = _currentConversationId.value ?: run {
            android.util.Log.e("VoiceCallDebug", "saveCallTranscript: no open conversation, skipping")
            return
        }
        // Find the current tail of the visible branch so the transcript parents onto it instead of
        // starting a new branch the user never sees. Pick the newest non-tool message by timestamp.
        val msgs = convRepo.getMessagesForConversationSnapshot(convId)
        val tailId = msgs
            .filter { !it.id.startsWith("tool_") && !it.id.startsWith("result_") }
            .maxByOrNull { it.timestamp }?.id
        val sb = StringBuilder()
        sb.appendLine("📞 语音通话记录：")
        sb.appendLine("（以下是你（橘子岛）与用户通过语音通话的内容，[用户] 是对方说的话，[橘子岛] 是你说的。）")
        for ((speaker, text) in transcript) {
            sb.append(speaker).append("：").appendLine(text)
        }
        val entity = com.orangeisland.app.data.local.MessageEntity(
            id = "voice_call_${java.util.UUID.randomUUID()}",
            conversationId = convId,
            parentId = tailId,
            text = sb.toString().trim(),
            participant = Participant.MODEL,
            status = MessageStatus.SUCCESS,
            timestamp = System.currentTimeMillis()
        ).encodeLargeText(appContext)
        convRepo.upsertMessage(entity)
        com.orangeisland.app.data.UsageLogManager.log(
            com.orangeisland.app.data.UsageLogManager.Type.CONVERSATION,
            "voice_call_saved",
            "turns=${transcript.size}"
        )
    }

    /**
     * Onboarding-focused model fetch for a single provider.
     *
     * Unlike [fetchAvailableModels] this carries no global side effects: no
     * `_isSyncingModels` guard (so re-entry always refetches the latest key),
     * no enabled-set intersection, and no snackbar. It is a plain suspend
     * function so the caller's coroutine owns its lifecycle 鈥?cancelling that
     * coroutine cooperatively aborts the in-flight network request, which keeps
     * the welcome flow seamless (no stale result can land after the user edits
     * their key and returns). Results are persisted so the [availableModels]
     * flow updates the list. Returns the prefixed model ids, or empty on
     * failure / unconfigured provider.
     */
    suspend fun fetchModelsForProvider(name: String): List<String> = providerRegistry.fetchModelsForProvider(name)

    fun computeProviderFingerprint(): String = providerRegistry.computeFingerprint()

    /**
     * Shrinks an assistant reply into a short line the desktop pet can show in its
     * speech bubble. Strips markdown/code fences, collapses whitespace, and caps at
     * [PET_BUBBLE_MAX] chars. Returns "" for empty/whitespace replies so the caller
     * can skip emitting a bubble entirely.
     */
    private fun petBubbleSummary(text: String): String {
        val cleaned = text
            .replace(Regex("```[\\s\\S]*?```"), " ") // fenced code blocks
            .replace(Regex("[`*_#>~]"), "")           // common markdown noise
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        return if (cleaned.length <= PET_BUBBLE_MAX) cleaned
        else cleaned.take(PET_BUBBLE_MAX - 1) + "…"
    }

    fun fetchAvailableModels() {
        viewModelScope.launch {
            if (_isSyncingModels.value) return@launch
            _isSyncingModels.value = true
            val successProviders = mutableListOf<String>()
            val failedProviders = mutableListOf<String>()
            var skippedCount = 0

            // Ensure custom providers are loaded into the providers map before iterating
            providerRegistry.ensureCustomProvidersRegistered()

            val message = try {
                providerRegistry.all.forEach { (name, _) ->
                    if (name == Constants.PROVIDER_LOCAL) return@forEach

                    try {
                        if (!providerRegistry.isConfigured(name, settings.resolveActiveKey(name) ?: "")) {
                            skippedCount++
                            settings.saveAvailableModels(name, emptyList())
                            return@forEach
                        }

                        val models = providerRegistry.fetchModelsForProvider(name)
                        if (models.isNotEmpty()) {
                            successProviders.add(name)
                        } else {
                            failedProviders.add(name)
                        }
                    } catch (e: Exception) {
                        failedProviders.add(name)
                    }
                }

                val allFetchedModels = settings.getAvailableModels().values.flatten().toSet()
                // Manual models live in a separate store from fetched/available models.
                // They must NOT be dropped from enabledModels here, or their checkmark
                // silently disappears the next time the available-models page runs a sync
                // (which is why manually-added models lost their check on re-entry).
                val allKnownModels = allFetchedModels + settings.manualModels.value.values.flatten()
                val newEnabled = settings.enabledModels.value.intersect(allKnownModels)
                settings.setEnabledModels(newEnabled)

                // Save fingerprint on any successful fetch so we don't re-fetch on next visit
                settings.saveLastModelsFetchFingerprint(computeProviderFingerprint())

                when {
                    successProviders.isNotEmpty() && failedProviders.isEmpty() ->
                        appContext.getString(R.string.sync_success_providers, successProviders.size)
                    successProviders.isNotEmpty() && failedProviders.isNotEmpty() ->
                        appContext.getString(R.string.sync_partial, successProviders.joinToString(), failedProviders.joinToString())
                    successProviders.isEmpty() && failedProviders.isNotEmpty() ->
                        appContext.getString(R.string.sync_failed_providers, failedProviders.joinToString())
                    else -> if (skippedCount > 0) appContext.getString(R.string.sync_no_providers) else appContext.getString(R.string.sync_completed)
                }
            } catch (e: Exception) {
                appContext.getString(R.string.sync_failed_providers, e.message ?: appContext.getString(R.string.unknown_error))
            } finally {
                _isSyncingModels.value = false
            }

            _snackbarMessage.tryEmit(SnackbarEvent(message))
        }
    }

    // ---- Data Control: Export / Import ----

    fun refreshDataCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _conversationCount.value = convRepo.getAllConversationsList().size
            _memoryCount.value = memoryManager.listFiles().size +
                (if (memoryManager.getActiveMemory().isNotEmpty()) 1 else 0)
            _systemPromptCount.value = settings.getSystemPrompts().size
            _workflowCount.value = workflowRepository?.let { repo ->
                runCatching { kotlinx.coroutines.runBlocking { repo.getAll().size } }.getOrDefault(0)
            } ?: 0
        }
    }

    fun exportData(uri: Uri, categories: Set<DataExporter.ExportCategory>, includeApiKeys: Boolean) =
        importExport.exportData(uri, categories, includeApiKeys)
    fun previewImport(uri: Uri) = importExport.previewImport(uri)
    fun clearImportState() = importExport.clearImportState()
    fun setClaudeImportPreview(preview: ClaudeChatImporter.ImportPreview) = importExport.setClaudeImportPreview(preview)
    fun previewClaudeChat(uri: Uri) = importExport.previewClaudeChat(uri)
    fun setClaudeImportError(error: String) = importExport.setClaudeImportError(error)
    fun clearClaudeImportState() = importExport.clearClaudeImportState()
    fun importClaudeChat(uri: Uri, strategy: ImportStrategy, selectedIds: Set<String>) =
        importExport.importClaudeChat(uri, strategy, selectedIds)
    fun previewGptChat(uri: Uri) = importExport.previewGptChat(uri)
    fun setGptImportError(error: String) = importExport.setGptImportError(error)
    fun clearGptImportState() = importExport.clearGptImportState()
    fun importGptChat(uri: Uri, strategy: ImportStrategy, selectedIds: Set<String>) =
        importExport.importGptChat(uri, strategy, selectedIds)
    fun importData(uri: Uri, decisions: Map<DataExporter.ExportCategory, DataImporter.ImportStrategy>) =
        importExport.importData(uri, decisions)
}
