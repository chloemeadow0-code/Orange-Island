package com.orangeisland.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
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
import com.orangeisland.app.data.ConversationSettings
import com.orangeisland.app.data.DataExporter
import com.orangeisland.app.data.DataImporter
import com.orangeisland.app.data.EmbeddingModelConfig
import com.orangeisland.app.data.LocalChatModelConfig
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.PredefinedVariables

import com.orangeisland.app.data.ShellDeviceConfig

import com.orangeisland.app.data.local.ChatEntity
import com.orangeisland.app.data.local.MessageEntity
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
import com.orangeisland.app.service.OrangeIslandForegroundService
import com.orangeisland.app.service.AutoBackupWorker
import com.orangeisland.app.ui.settings.ImportStrategy
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.util.PdfPageRenderer
import com.orangeisland.app.util.SearchResultFormatter
import com.orangeisland.app.util.SnackbarEvent
import com.orangeisland.app.util.SshClient
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
import java.io.File
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
    private val pluginToolProvider: com.orangeisland.app.plugin.PluginToolProvider? = null,
    private val _pluginLoader: com.orangeisland.app.plugin.PluginLoader? = null,
    private val _pluginSandbox: com.orangeisland.app.plugin.PluginSandbox? = null
) : AndroidViewModel(application) {

    companion object {
        /** Overlay fade duration for conversation-switch transitions. */
        private const val SWITCH_OVERLAY_FADE_MS = 200L
        /** Auto-delete period tiers in hours: 7 days, 30 days, 365 days. */
        private val AUTO_DELETE_TIERS_HOURS = listOf(168, 720, 8760)
    }

    val settings: SettingsRepository = settingsRepository

    /**
     * Conversation/message persistence behind the repository layer. CRUD, cascade-delete,
     * branch-selection and stuck-message logic live in [ConversationRepository]; managers
     * receive the repository (not raw DAO) for a uniform boundary.
     */
    private val convRepo: ConversationRepository = conversationRepository

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
        // Auto update-check disabled: UpdateChecker is hardcoded to the upstream
        // repo (orangeisland/app). Re-enable after repointing UpdateChecker.kt to
        // your own release source.
        // viewModelScope.launch(Dispatchers.IO) {
        //     if (settings.getAutoUpdateCheck()) {
        //         val lastCheck = settings.getLastUpdateCheckTime()
        //         val now = System.currentTimeMillis()
        //         if (now - lastCheck > 24 * 60 * 60 * 1000L) {
        //             settings.saveLastUpdateCheckTime(now)
        //             val info = UpdateChecker.check(getCurrentVersion())
        //             if (info != null) {
        //                 _updateDialogData.value = info
        //             }
        //         }
        //     }
        // }
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
            permissionController = permissionController
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
     *  Lives on viewModelScope so the background reconnect coroutines die with the ViewModel. */
    private val mcpClientPoolLazy = lazy {
        com.orangeisland.app.mcp.McpClientPool(ioScope = viewModelScope)
    }
    val mcpClientPool: com.orangeisland.app.mcp.McpClientPool get() = mcpClientPoolLazy.value

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
        if (mcpClientPoolLazy.isInitialized()) mcpClientPool.closeAll()
        pluginSandbox?.closeAll()
    }

    /** JS-plugin filesystem scanner; null if plugin support wasn't injected. Exposed for the
     *  settings page to install/uninstall .zip plugin packages. */
    val pluginLoader: com.orangeisland.app.plugin.PluginLoader? get() = _pluginLoader

    /** JS-plugin runtime pool; null if plugin support wasn't injected. Exposed so the settings
     *  page can ask the sandbox to reload a plugin after its main.js is replaced on disk. */
    val pluginSandbox: com.orangeisland.app.plugin.PluginSandbox? get() = _pluginSandbox

    fun getProviderInstance(name: String): LlmProvider = providerRegistry.getInstance(name)



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

    val messages: StateFlow<List<ChatMessage>> = combine(
        _allMessages,
        _streamingMessage,
        _selectedChildren
    ) { allMsgs, streaming, selectedChildren ->
        // Single source of truth for the visible-path walk: the tested
        // ConversationUiState.resolvePath (covered by ConversationUiStateTest).
        ConversationUiState.resolvePath(allMsgs, streaming, selectedChildren)
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

    private val _branchSwitchTrigger = MutableStateFlow<String?>(null)
    val branchSwitchTrigger: StateFlow<String?> = _branchSwitchTrigger.asStateFlow()

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
        viewModelScope.launch {
            _currentConversationId.collectLatest { id ->
                if (id != null) {
                    // Fix stuck sending states when loading conversation 鈥?skip if currently generating
                    if (!_isLoading.value) {
                        val stuckMessages = convRepo.getMessagesForConversation(id).first()
                            .filter { it.status == MessageStatus.SENDING || it.status == MessageStatus.THINKING || it.status == MessageStatus.TOOL_CALLING || it.status == MessageStatus.TRANSCRIBING }

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
                        val mapped = entities.map {
                            ChatMessage(
                                id = it.id,
                                parentId = it.parentId,
                                text = SearchResultFormatter.format(it.text, appContext),
                                images = it.images,
                                thoughts = it.thoughts,
                                thoughtTitle = it.thoughtTitle,
                                tokenCount = it.tokenCount,
                                status = it.status,
                                participant = it.participant,
                                timestamp = it.timestamp,
                                thoughtTimeMs = it.thoughtTimeMs,
                                modelName = it.modelName,
                                segments = it.toolCallJson?.let { json ->
                                    try { Json.decodeFromString<List<MessageSegment>>(json) } catch (_: Exception) { null }
                                } ?: it.thoughts?.takeIf { t -> t.isNotBlank() }?.let { listOf(MessageSegment(type = "thought", content = it)) },
                                toolCall = it.toolCallJson?.let { json ->
                                    try {
                                        val segs = Json.decodeFromString<List<MessageSegment>>(json)
                                        segs.lastOrNull { s -> s.type == "tool" }?.let { s ->
                                            val rawResult = s.toolResult ?: ""
                                            ToolCallData(s.toolName ?: "", s.toolArgs ?: "{}", SearchResultFormatter.format(rawResult, appContext))
                                        }
                                    } catch (_: Exception) { null }
                                },
                                attachmentMeta = it.attachmentMeta?.let { json ->
                                    try { Json.decodeFromString<AttachmentMeta>(json) } catch (_: Exception) { null }
                                }
                            )
                        }
                        // Backfill toolCall for old result_ messages persisted without toolCallJson.
                        // They inherit the parent tool_ message's ToolCallData so the provider can
                        // format them as proper "tool" role messages with matching tool_call_id.
                        _allMessages.value = mapped.map { msg ->
                            if (msg.id.startsWith(Constants.RESULT_MSG_PREFIX) && msg.toolCall == null) {
                                val parentTool = mapped.find { it.id == msg.parentId }
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
        
        viewModelScope.launch {
            _selectedChildren.collect { childrenMap ->
                val id = _currentConversationId.value
                if (id != null) {
                    persistSelectedChildren(id, childrenMap)
                }
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
    suspend fun checkForUpdates(): UpdateInfo? {
        val current = getCurrentVersion()
        return UpdateChecker.check(current)
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

    fun createNewChat() {
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
        switchingJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SWITCH_OVERLAY_FADE_MS) // Allow overlay to fade in
            _currentConversationId.value = null
            // Pre-fill the active model with the project's default if set; otherwise fall back
            // to the global default (null). Same single-source-of-truth rule as system prompt.
            _currentActiveModel.value = activeProject?.modelId
            _pendingConversationSettings.value = null
            _allMessages.value = emptyList()
            _selectedChildren.value = emptyMap()
            _branchSwitchTrigger.value = null
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
            kotlinx.coroutines.delay(SWITCH_OVERLAY_FADE_MS) // Allow overlay to fade in
            _isNewChatMode.value = false
            _branchSwitchTrigger.value = null
            _currentConversationId.value = id
            val conversation = convRepo.getConversation(id)
            _currentActiveModel.value = conversation?.modelId
            // Opening a conversation inside a project makes that project the active context,
            // so the top-bar "+" continues to file new chats under the same project.
            _activeProjectId.value = conversation?.projectId
            triggerScrollToMessage()
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

    fun setConversationSystemPrompt(id: String, promptId: String?) {
        viewModelScope.launch {
            val existing = convRepo.getConversation(id)
            if (existing != null) {
                convRepo.upsertConversation(existing.copy(systemPromptId = promptId))
            }
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
        val stopFinalization = if (_currentConversationId.value == id) {
            session.stop()
        } else null
        viewModelScope.launch(Dispatchers.IO) {
            stopFinalization?.join()
            convRepo.deleteConversation(id)
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

    fun regenerate(messageId: String) = generationController.regenerate(messageId)

    fun switchBranch(parentId: String?, currentMessageId: String, direction: Int) {
        if (_isLoading.value && _generatingInConversationId.value == _currentConversationId.value) return
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

    fun editMessage(messageId: String, newText: String) = generationController.editMessage(messageId, newText)

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

    fun sendMessage(text: String, images: List<String> = emptyList(), attachments: List<SelectedAttachment> = emptyList()): Boolean =
        generationController.sendMessage(text, images, attachments)

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
                val newEnabled = settings.enabledModels.value.intersect(allFetchedModels)
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
