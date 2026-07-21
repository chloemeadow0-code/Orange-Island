package com.orangeisland.app.di

import android.app.Application
import android.content.Context
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.data.AutoBackupManager
import com.orangeisland.app.sandbox.SandboxManagerFactory
import com.orangeisland.app.viewmodel.ChatViewModel
import com.orangeisland.app.viewmodel.ChatViewModelFactory

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
        ConversationRepository(chatDao)
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(settingsManager, appScope)
    }
    val authRepository: com.orangeisland.app.data.repository.AuthRepository by lazy {
        com.orangeisland.app.data.repository.AuthRepository(settingsManager, appScope)
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
        json = workflowJson,
        contextProvider = deviceContextProvider,
        onConfirmDestructive = onConfirmDestructive,
        onNodeState = onNodeState
    )

    /** App-wide device-state snapshot provider for the linear engine. The foreground-app field is
     *  wired in stage F4 (when the automation accessibility service gains a foreground dispatcher);
     *  until then it returns null and foreground conditions fail open. */
    val deviceContextProvider: com.orangeisland.app.workflow.linear.DeviceContextProvider by lazy {
        com.orangeisland.app.workflow.linear.DeviceContextProvider(appContext)
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
            knownToolNames = {
                // Resolve lazily so newly-installed plugins / freshly-connected MCP servers are seen.
                toolDispatcher.allDefinitions(com.orangeisland.app.viewmodel.GenerationContext())
                    .map { it.function.name }.toSet()
            },
            approval = null   // foreground approval card is wired in stage F5 (UI layer)
        )
    }

    val toolDispatcher: com.orangeisland.app.tool.ToolDispatcher by lazy {
        com.orangeisland.app.tool.ToolDispatcher(
            app = application,
            conversations = conversationRepository,
            memoryManager = memoryManager,
            llmProviders = emptyMap(),
            appContext = appContext,
            sandboxFactory = sandboxManagerFactory,
            mcpPool = null,
            pluginToolProvider = pluginToolProvider,
            permissionController = null,   // device tools run but cannot check permission state here yet
            workflowToolProvider = workflowAiToolProvider
        )
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
        )
    }
    val pluginToolProvider: com.orangeisland.app.plugin.PluginToolProvider by lazy {
        com.orangeisland.app.plugin.PluginToolProvider(pluginLoader, pluginSandbox, settingsRepository)
    }

    // ── Workflow ViewModel ────────────────────────────────────

    fun workflowViewModel(): com.orangeisland.app.viewmodel.WorkflowViewModel =
        com.orangeisland.app.viewmodel.WorkflowViewModel(
            repository = workflowRepository,
            runnerFactory = { onConfirm, onNodeState ->
                workflowRunner(onConfirm, onNodeState)
            }
        )

    // ── ViewModel Factory ─────────────────────────────────────

    fun chatViewModelFactory(): ChatViewModelFactory =
        ChatViewModelFactory(
            application, chatDao, settingsManager, memoryManager, appContext, sandboxManagerFactory,
            autoBackupManager, conversationRepository, settingsRepository, workflowRepository,
            pluginToolProvider, pluginLoader, pluginSandbox
        )
}
