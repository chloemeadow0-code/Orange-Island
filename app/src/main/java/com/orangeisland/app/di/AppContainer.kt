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
        AutoBackupManager(appContext, settingsManager, chatDao, memoryManager)
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

    // ── ViewModel Factory ─────────────────────────────────────

    fun chatViewModelFactory(): ChatViewModelFactory =
        ChatViewModelFactory(
            application, chatDao, settingsManager, memoryManager, appContext, sandboxManagerFactory,
            autoBackupManager, conversationRepository, settingsRepository,
            pluginToolProvider, pluginLoader, pluginSandbox
        )
}
