package com.orangeisland.app.di

import android.app.Application
import android.content.Context
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.anthropic.AnthropicProvider
import com.orangeisland.app.api.gemini.GeminiProvider
import com.orangeisland.app.api.ollama.OllamaProvider
import com.orangeisland.app.api.openai.DeepSeekProvider
import com.orangeisland.app.api.openai.OpenAiProvider
import com.orangeisland.app.api.openai.OpenRouterProvider
import com.orangeisland.app.api.openai.QwenProvider
import com.orangeisland.app.api.music.ReplicateMusicGenerationProvider
import com.orangeisland.app.api.music.SunoMusicGenerationProvider
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.music.MusicStudioRepository
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.AutoBackupManager
import com.orangeisland.app.sandbox.SandboxManagerFactory
import com.orangeisland.app.util.Constants
import com.orangeisland.app.viewmodel.ChatViewModel
import com.orangeisland.app.viewmodel.ChatViewModelFactory
import kotlinx.coroutines.launch

/**
 * Centralized dependency container (manual DI).
 *
 * Replaces the ad-hoc dependency creation previously spread across
 * MainActivity (ChatDatabase.build, ChatViewModelFactory instantiation).
 * All shared dependencies are created once and reused.
 *
 * This is a stepping stone toward a full DI framework (Hilt/Koin);
 * for a single-module project it provides sufficient decoupling and
 * testability without annotation processing overhead.
 */
class AppContainer(private val appContext: Context) {
    private val application = appContext.applicationContext as Application

    /** App-lifetime scope that backs the shared settings StateFlows. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    // ── Data Layer ────────────────────────────────────────────

    val settingsManager: SettingsManager by lazy { SettingsManager(appContext) }
    val memoryManager: MemoryManager by lazy { MemoryManager(appContext) }
    val database: ChatDatabase by lazy { ChatDatabase.build(appContext) }
    val chatDao: ChatDao by lazy { database.chatDao() }

    // ── Repositories ──────────────────────────────────────────

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(chatDao, appContext)
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(settingsManager, appScope)
    }
    val localMusicRepository: com.orangeisland.app.data.music.LocalMusicRepository by lazy {
        com.orangeisland.app.data.music.LocalMusicRepository(appContext)
    }

    // ── Sandbox (flavor-specific) ─────────────────────────────

    val sandboxManagerFactory: SandboxManagerFactory? by lazy {
        try {
            // fdroid flavor provides FdroidSandboxManagerFactory
            Class.forName("com.orangeisland.app.sandbox.FdroidSandboxManagerFactory")
                .getDeclaredConstructor(android.content.Context::class.java)
                .newInstance(appContext) as SandboxManagerFactory
        } catch (_: ClassNotFoundException) {
            // play flavor provides PlaySandboxManagerFactory
            try {
                Class.forName("com.orangeisland.app.sandbox.PlaySandboxManagerFactory")
                    .getDeclaredConstructor()
                    .newInstance() as SandboxManagerFactory
            } catch (_: ClassNotFoundException) {
                null
            } catch (e: Exception) {
                // Class exists but failed to construct — this is a real error, not a flavor miss.
                com.orangeisland.app.util.DebugLog.e("AppContainer", "PlaySandboxManagerFactory init failed", e)
                null
            }
        } catch (e: Exception) {
            // FdroidSandboxManagerFactory exists but failed to construct.
            com.orangeisland.app.util.DebugLog.e("AppContainer", "FdroidSandboxManagerFactory init failed", e)
            null
        }
    }

    // ── Auto Backup ───────────────────────────────────────────

    val autoBackupManager: AutoBackupManager by lazy {
        AutoBackupManager(appContext, settingsManager, chatDao, memoryManager, workflowRepository)
    }

    // ── Workflow ──────────────────────────────────────────────
    // Shared Json for workflow graph (de)serialization — ignoreUnknownKeys so a future schema
    // addition doesn't break older builds reading newer exports. The runner, repository, exporter,
    // and importer all reference this same instance so encoding stays consistent.

    val workflowJson: kotlinx.serialization.json.Json by lazy {
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    val workflowRepository: com.orangeisland.app.data.repository.WorkflowRepository by lazy {
        com.orangeisland.app.data.repository.WorkflowRepository(database.workflowDao(), workflowJson)
    }

    /** Exposes chat memories to JS plugins (WebView pages and QuickJS sandbox tools). */
    val pluginMemoryProvider: com.orangeisland.app.plugin.AppPluginMemoryProvider by lazy {
        com.orangeisland.app.plugin.AppPluginMemoryProvider(conversationRepository, memoryManager)
    }

    /**
     * Builds a foreground [com.orangeisland.app.workflow.WorkflowRunner] wired to the app-wide
     * dispatcher. The [onConfirmDestructive] / [onNodeState] callbacks are caller-supplied (the UI
     * provides them per run), so the runner itself is constructed fresh each time rather than
     * held as a singleton. Background runners (WorkManager, Intent receiver) build their own with
     * Mode.BACKGROUND and null callbacks.
     */
    fun workflowRunner(
        onConfirmDestructive: (suspend (toolName: String, args: String) -> Boolean)? = null,
        onNodeState: ((String, com.orangeisland.app.workflow.NodeState) -> Unit)? = null
    ): com.orangeisland.app.workflow.WorkflowRunner = com.orangeisland.app.workflow.WorkflowRunner(
        repository = workflowRepository,
        dispatcher = toolDispatcher,
        settings = settingsManager,
        settingsRepository = settingsRepository,
        json = workflowJson,
        contextProvider = deviceContextProvider,
        providerRegistry = providerRegistry,
        llmProviders = llmProviders,
        chatDao = database.chatDao(),
        memoryManager = memoryManager,
        appContext = appContext,
        onConfirmDestructive = onConfirmDestructive,
        onNodeState = onNodeState
    )

    /** App-wide device-state snapshot provider for the linear engine. The foreground-app field is
     *  fed by [com.orangeisland.app.workflow.trigger.AppForegroundDispatcher], which the automation
     *  accessibility service publishes into on every TYPE_WINDOW_STATE_CHANGED event. */
    val deviceContextProvider: com.orangeisland.app.workflow.linear.DeviceContextProvider by lazy {
        com.orangeisland.app.workflow.linear.DeviceContextProvider(
            context = appContext,
            foregroundProvider = { com.orangeisland.app.workflow.trigger.AppForegroundDispatcher.lastKnown },
            lastChatMsProvider = {
                kotlinx.coroutines.runBlocking {
                    database.chatDao().getLatestMessageTimestamp()
                }
            }
        )
    }

    /**
     * Lazily-constructed app-wide [com.orangeisland.app.tool.ToolDispatcher]. Unlike the chat path
     * (which builds a standalone dispatcher inside GenerationManager), this one is the shared
     * instance the workflow runner and any future non-LLM tool caller uses. It is wired with the
     * same providers the chat path gets: memory, web search, RAG, image-gen, shell, device-access,
     * navigation/app-lock/toast, automation, MCP (none yet — see stage C MCP lift), and JS plugins.
     *
     * Note: llmProviders is empty here because AutomationToolProvider only needs the provider map
     * for sub-task LLM calls, which workflows don't trigger in this release. When they do, the
     * provider registry should be lifted to AppContainer and passed in.
     */
    val workflowAiToolProvider: com.orangeisland.app.workflow.WorkflowAiToolProvider by lazy {
        // The runnerProvider / knownToolNames lambdas are stored, not invoked, at construction —
        // they only run when the model actually calls a workflow tool, by which point
        // toolDispatcher below has finished initializing. This breaks what would otherwise be a
        // constructor cycle (toolDispatcher -> workflowAiToolProvider -> toolDispatcher).
        com.orangeisland.app.workflow.WorkflowAiToolProvider(
            repository = workflowRepository,
            runnerProvider = { workflowRunner() },
            settingsRepository = settingsRepository,
            knownToolNames = {
                // Resolve the set of tool NAMES this assistant registers — used by the parser to
                // reject workflow definitions that reference a non-existent action tool. We pass a
                // fully-enabled GenerationContext so EVERY gated provider (toast, shell, device,
                // automation, …) reports its tools: the point is "does this tool exist on this
                // assistant", not "is it enabled for the current chat turn". Passing the default
                // GenerationContext() (all flags false) made gated providers return empty lists, so
                // every workflow definition using e.g. show_toast/ui_global_action was rejected as
                // unknown_tool → every workflow_create call failed with "validation failed".
                //
                // sandboxEnabled / shellDevices are read from real settings (not hardcoded) because
                // ShellToolProvider.definitions() — which also gates file_read/file_write/file_edit/
                // file_glob/file_grep — returns emptyList() unless at least one of them is truthy.
                // Without this, those tool names NEVER appear here regardless of the user's actual
                // sandbox/shell-device configuration, and every workflow action referencing them is
                // rejected as unknown_tool.
                val allEnabled = com.orangeisland.app.viewmodel.GenerationContext(
                    shellEnabled = true, deviceInfoEnabled = true, locationEnabled = true,
                    calendarEnabled = true, notificationEnabled = true, usageStatsEnabled = true,
                    navigationEnabled = true, appLockEnabled = true, toastEnabled = true,
                    uiAutomationEnabled = true, webSearchEnabled = true, imageGenEnabled = true,
                    sandboxEnabled = settingsRepository.sandboxEnabled.value,
                    shellDevices = settingsRepository.shellDevices.value
                )
                toolDispatcher.allDefinitions(allEnabled).map { it.function.name }.toSet()
            },
            // Foreground approval gate: when the model calls workflow_create / _update / _delete /
            // _set_enabled, the provider renders a card and suspends on this callback. The chat
            // screen observes workflowApprovalGate.pending and pops an AlertDialog.
            approval = workflowApprovalGate.approval,
            // Application context so graph-mode Schedule triggers can be enqueued into WorkManager
            // at create/update/enable time (they have no Flow-driven reconciler like linear).
            appContext = appContext
        )
    }

    /** Shared approval gate between the AI tool provider (which suspends on it) and the chat UI
     *  (which renders the pending card). One instance per app — held here so both sides see it. */
    val workflowApprovalGate: com.orangeisland.app.workflow.WorkflowApprovalGate by lazy {
        com.orangeisland.app.workflow.WorkflowApprovalGate()
    }

    /** Shared approval gate between the AI voice-call tool (make_voice_call) and the incoming-call
     *  UI. Constructed once here so the same instance is wired to the ToolDispatcher (tool side)
     *  and to the full-screen incoming-call screen (render side). */
    val voiceCallGate: com.orangeisland.app.viewmodel.VoiceCallGate by lazy {
        com.orangeisland.app.viewmodel.VoiceCallGate()
    }

    /** Shared camera gate between the AI take_photo tool and the chat UI camera launcher.
     *  Constructed once here so the same instance is wired to the ToolDispatcher (tool side)
     *  and to the chat screen (render side). */
    val cameraToolGate: com.orangeisland.app.tool.CameraToolGate by lazy {
        com.orangeisland.app.tool.CameraToolGate()
    }

    /** Approval gate for sensitive device-access tools (location, notifications, usage stats).
     *  When autoApprove is false, AI-driven calls suspend until the user confirms via dialog.
     *
     *  Driven by the user-facing `autoApproveSensitiveTools` setting (default on): when on, the gate
     *  short-circuits every request — essential for background workflows, which have no UI to show
     *  the [com.orangeisland.app.ui.chat.SensitiveToolApprovalDialog] and otherwise time out into
     *  an `approval_denied` error. The chat path already bypasses this gate (null gate in the
     *  standalone dispatcher), so this only affects workflow tool dispatch. */
    val sensitiveToolApprovalGate: com.orangeisland.app.tool.SensitiveToolApprovalGate by lazy {
        com.orangeisland.app.tool.SensitiveToolApprovalGate().also { gate ->
            appScope.launch {
                settingsRepository.autoApproveSensitiveTools.collect { gate.autoApprove = it }
            }
        }
    }

    /** Collects environment changes (app foreground, model, prompt, wallpaper, theme, battery,
     *  WiFi, Bluetooth) into a ring buffer for injection into the system prompt via {app_context}. */
    val appContextCollector: com.orangeisland.app.data.environment.AppContextCollector by lazy {
        com.orangeisland.app.data.environment.AppContextCollector(
            context = appContext,
            settingsRepository = settingsRepository,
            scope = appScope
        )
    }

    /** Interactive choice gate for card-style user prompts (ask_user_choice). Shared between
     *  the tool provider (which suspends on it) and the chat UI (which renders the dialog). */
    val userInteractionGate: com.orangeisland.app.tool.UserInteractionGate by lazy {
        com.orangeisland.app.tool.UserInteractionGate()
    }

    /** Built-in LLM providers (excludes LocalProvider which needs a ViewModelScope). Used by both
     *  the chat path and the workflow LLM node executor. */
    val llmProviders: Map<String, LlmProvider> by lazy {
        mapOf(
            Constants.PROVIDER_GOOGLE to GeminiProvider(),
            Constants.PROVIDER_OPENAI to OpenAiProvider(),
            Constants.PROVIDER_ANTHROPIC to AnthropicProvider(),
            Constants.PROVIDER_DEEPSEEK to DeepSeekProvider(),
            Constants.PROVIDER_QWEN to QwenProvider(),
            Constants.PROVIDER_OLLAMA to OllamaProvider(),
            Constants.PROVIDER_OPEN_ROUTER to OpenRouterProvider()
        )
    }

    /** App-wide provider registry including built-in + user-defined custom providers. Unlike
     *  [llmProviders] (static, built-in only), this registry is dynamic and reflects the user's
     *  configured custom OpenAI-compatible providers at runtime. Used by the workflow runner so
     *  an LLM node bound to a custom provider can resolve to a real [LlmProvider] instance. */
    val providerRegistry: com.orangeisland.app.viewmodel.ProviderRegistry by lazy {
        val localProvider = com.orangeisland.app.api.local.LocalProvider(appContext, settingsRepository)
        val registry = com.orangeisland.app.viewmodel.ProviderRegistry(settingsRepository, localProvider, appScope)
        // Sync persisted custom providers into the live map now, so a workflow firing before the
        // user opens the chat (e.g. a boot trigger) still resolves custom providers. This is suspend
        // (it waits for DataStore); run it in appScope — launchSyncJobs' collectors also register
        // custom providers, so this just front-loads it for the earliest triggers.
        appScope.launch { registry.ensureCustomProvidersRegistered() }
        registry.launchSyncJobs()
        registry
    }

    // ── MCP (Model Context Protocol) ─────────────────────────────────────────
    // App-lifetime, shared between the live chat path (via ChatViewModel) and the workflow
    // engine's toolDispatcher below. Previously each owned a separate pool: ChatViewModel built
    // its own (viewModelScope-bound, died with the ViewModel), and toolDispatcher was wired with
    // mcpPool = null — MCP tools were simply invisible to workflows. Lifting the pool here fixes
    // both: connections now survive ViewModel recreation, and AI-authored workflows can reference
    // MCP tools since toolDispatcher (used both at authoring-time validation and at run-time by
    // WorkflowRunner) now sees the same connections chat does.

    /** Shared MCP client pool. Lazily created on first access (first MCP tool request from either
     *  chat or a workflow), never explicitly closed (app-lifetime; OS reclaims on process death). */
    val mcpClientPool: com.orangeisland.app.mcp.McpClientPool by lazy {
        com.orangeisland.app.mcp.McpClientPool(ioScope = appScope).also { pool ->
            pool.startMonitoring(settingsRepository.mcpServers)
        }
    }

    /** Foreground refresh: re-probe every enabled MCP server whenever the app returns to
     *  foreground, so the settings page's three-state icon doesn't wait for the next heartbeat
     *  tick. Mirrors what ChatViewModel used to do per-instance; now app-wide and started once. */
    private val mcpForegroundListener: (Boolean) -> Unit = { inForeground ->
        if (inForeground) {
            appScope.launch {
                mcpClientPool.refreshAll(settingsRepository.mcpServers.value.filter { it.enabled })
            }
        }
    }

    /** Starts MCP pool maintenance: foreground-refresh listener + a collector that drops pool
     *  connections for servers the user has deleted. Idempotent; call once from
     *  [com.orangeisland.app.OrangeIslandApplication.onCreate]. */
    fun startMcpMaintenance() {
        com.orangeisland.app.service.AppForegroundTracker.addListener(mcpForegroundListener)
        appScope.launch {
            settingsRepository.mcpServers.collect { servers ->
                mcpClientPool.retainOnly(servers.map { it.id }.toSet())
            }
        }
    }

    val toolDispatcher: com.orangeisland.app.tool.ToolDispatcher by lazy {
        com.orangeisland.app.tool.ToolDispatcher(
            app = application,
            conversations = conversationRepository,
            memoryManager = memoryManager,
            llmProviders = llmProviders,
            appContext = appContext,
            sandboxFactory = sandboxManagerFactory,
            mcpPool = mcpClientPool,
            pluginToolProvider = pluginToolProvider,
            permissionController = com.orangeisland.app.viewmodel.PermissionController(appContext),
            workflowToolProvider = workflowAiToolProvider,
            musicStudioRepository = musicStudioRepository,
            localMusicRepository = localMusicRepository,
            sensitiveToolApproval = sensitiveToolApprovalGate,
            chatDao = chatDao,
            userInteractionGate = userInteractionGate,
            voiceCallGate = voiceCallGate,
            cameraToolGate = cameraToolGate
        )
    }

    // ── Trigger Host (workflow v2) ───────────────────────────────────────────
    // The host starts each signal source (one per trigger kind). Unlike a registry, it does not
    // iterate a list of sources or call a sync() method on each — each source owns its own Flow
    // subscription to the enabled-workflow set and reconciles its OS hooks itself. See
    // [com.orangeisland.app.workflow.trigger.WorkflowTriggerHost].

    /** The host, lazily constructed. The [com.orangeisland.app.workflow.trigger.WorkflowStarter]
     *  closure is shared by every source so a fire routes through a BACKGROUND-mode runner. */
    val triggerHost: com.orangeisland.app.workflow.trigger.WorkflowTriggerHost by lazy {
        val starter = com.orangeisland.app.workflow.trigger.workflowStarter(
            runnerProvider = { workflowRunner() }
        )
        com.orangeisland.app.workflow.trigger.WorkflowTriggerHost(
            context = appContext,
            repository = workflowRepository,
            scope = appScope,
            starter = starter
        )
    }

    /** Start every signal source. Idempotent. Called from
     *  [com.orangeisland.app.OrangeIslandApplication.onCreate]. */
    fun startTriggerHost() {
        runCatching { triggerHost.start() }
            .onFailure { com.orangeisland.app.util.DebugLog.e("AppContainer", "trigger host start failed", it) }
    }

    // ── Desktop Pet ───────────────────────────────────────────────────────────
    /** Reactive controller that starts/stops [com.orangeisland.app.service.DesktopPetService]
     *  from the petEnabled setting + overlay-permission state. Mirrors the trigger-host pattern. */
    val petController: com.orangeisland.app.pet.PetController by lazy {
        com.orangeisland.app.pet.PetController(appContext, settingsRepository, appScope)
    }

    /** Begin observing the pet setting. Idempotent. Called from
     *  [com.orangeisland.app.OrangeIslandApplication.onCreate]. */
    fun startPetController() {
        runCatching { petController.start() }
            .onFailure { com.orangeisland.app.util.DebugLog.e("AppContainer", "pet controller start failed", it) }
    }

    /** Re-enqueue every enabled graph-mode workflow's Schedule trigger into WorkManager.
     *
     *  Linear workflows are reconciled live by [com.orangeisland.app.workflow.trigger.TimeSignalSource]
     *  (it subscribes to the enabled set on host start), so they need no extra step. Graph workflows
     *  have no such Flow-driven reconciler — they are scheduled at authoring time and must be
     *  refreshed here on cold start, since WorkManager's `UPDATE` policy and a fresh install / app
     *  upgrade both need a re-enqueue to guarantee the pending run survives. Idempotent; safe to
     *  call on every process start. */
    fun rescheduleGraphWorkflows() {
        runCatching {
            appScope.launch {
                runCatching { com.orangeisland.app.workflow.WorkflowWorker.rescheduleAll(appContext, workflowRepository) }
                    .onFailure { com.orangeisland.app.util.DebugLog.w("AppContainer", "graph rescheduleAll failed", it) }
                // A run left RUNNING across a process restart is wedged — its coroutine died with
                // the old process and can never call recordRunEnd. Sweep them to FAILED so the run
                // log doesn't show a perpetual spinner. Safe on every start; no-op when none stuck.
                runCatching { workflowRepository.failStrandedRuns() }
                    .onFailure { com.orangeisland.app.util.DebugLog.w("AppContainer", "failStrandedRuns failed", it) }
            }
        }.onFailure { com.orangeisland.app.util.DebugLog.w("AppContainer", "rescheduleGraphWorkflows launch failed", it) }
    }

    // ── JS Plugins ────────────────────────────────────────────
    // Three singletons: the filesystem scanner, the QuickJS runtime pool, and the ToolProvider
    // bridge. All app-lifetime; settings reads happen lazily so this is safe to construct early.

    val pluginLoader: com.orangeisland.app.plugin.PluginLoader by lazy {
        com.orangeisland.app.plugin.PluginLoader(appContext)
    }
    val pluginSandbox: com.orangeisland.app.plugin.PluginSandbox by lazy {
        com.orangeisland.app.plugin.PluginSandbox(
            appScope,
            // Device id is resolved lazily per tool call (the provider only suspends when
            // actually invoked), so it's safe to reference settingsRepository here even though
            // it is also defined as a `by lazy` on this same container.
            com.orangeisland.app.plugin.PluginSandbox.UserIdentityProvider {
                settingsRepository.getAppUserId()
            },
            // Per-plugin config (manifest-driven) resolved lazily per call.
            com.orangeisland.app.plugin.PluginSandbox.PluginConfigProvider { pluginId ->
                settingsRepository.pluginConfigJson(pluginId)
            },
            // Enable plugin UI pages to read/write chat history and long-term memories.
            memoryProvider = pluginMemoryProvider,
        )
    }
    val pluginToolProvider: com.orangeisland.app.plugin.PluginToolProvider by lazy {
        com.orangeisland.app.plugin.PluginToolProvider(pluginLoader, pluginSandbox, settingsRepository)
    }

    // ── Workflow ViewModel ────────────────────────────────────

    fun workflowViewModel(): com.orangeisland.app.viewmodel.WorkflowViewModel =
        com.orangeisland.app.viewmodel.WorkflowViewModel(
            repository = workflowRepository,
            appContext = appContext,
            runnerFactory = { onConfirm, onNodeState ->
                workflowRunner(onConfirm, onNodeState)
            }
        )

    // ── ViewModel Factory ─────────────────────────────────────

    fun chatViewModelFactory(): ChatViewModelFactory =
        ChatViewModelFactory(
            application, chatDao, settingsManager, memoryManager, appContext, sandboxManagerFactory,
            autoBackupManager, conversationRepository, settingsRepository, workflowRepository,
            workflowApprovalGate, pluginToolProvider, pluginLoader, pluginSandbox,
            workflowAiToolProvider, userInteractionGate, voiceCallGate, cameraToolGate,
            appContextCollector, pluginMemoryProvider, musicStudioRepository, localMusicRepository, mcpClientPool
        )

    fun healthViewModelFactory(): com.orangeisland.app.viewmodel.HealthViewModelFactory =
        com.orangeisland.app.viewmodel.HealthViewModelFactory(application, settingsManager)

    /** Music generation repository and ViewModel factory. Currently wired with Suno; more
     *  providers are added by extending the list passed to [MusicStudioRepository]. */
    val musicStudioRepository: MusicStudioRepository by lazy {
        MusicStudioRepository(
            context = appContext,
            providers = listOf(SunoMusicGenerationProvider(), ReplicateMusicGenerationProvider())
        )
    }

    fun musicStudioViewModelFactory(): com.orangeisland.app.ui.music.MusicStudioViewModelFactory =
        com.orangeisland.app.ui.music.MusicStudioViewModelFactory(application, settingsRepository, musicStudioRepository)

    fun localMusicViewModelFactory(): com.orangeisland.app.ui.music.LocalMusicViewModelFactory =
        com.orangeisland.app.ui.music.LocalMusicViewModelFactory(application, localMusicRepository)

    /**
     * Factory for the Voice Call ViewModel. Takes the shared [ChatViewModel] so the call loop can
     * reuse its model/credential resolution via [com.orangeisland.app.viewmodel.ChatViewModel.generateVoiceReply].
     */
    fun voiceCallViewModelFactory(
        chatViewModel: com.orangeisland.app.viewmodel.ChatViewModel
    ): com.orangeisland.app.viewmodel.VoiceCallViewModelFactory =
        com.orangeisland.app.viewmodel.VoiceCallViewModelFactory(application, settingsRepository, chatViewModel)
}
