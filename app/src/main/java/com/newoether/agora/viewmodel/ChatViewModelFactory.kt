package com.newoether.agora.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.newoether.agora.data.AutoBackupManager
import com.newoether.agora.data.MemoryManager
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.data.local.ChatDao
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.sandbox.SandboxManagerFactory

class ChatViewModelFactory(
    private val application: Application,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager,
    private val memoryManager: MemoryManager,
    private val context: Context,
    private val sandboxFactory: SandboxManagerFactory? = null,
    private val autoBackupManager: AutoBackupManager,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val pluginToolProvider: com.newoether.agora.plugin.PluginToolProvider? = null,
    private val pluginLoader: com.newoether.agora.plugin.PluginLoader? = null,
    private val pluginSandbox: com.newoether.agora.plugin.PluginSandbox? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                application, chatDao, settingsManager, memoryManager, context, sandboxFactory,
                autoBackupManager, conversationRepository, settingsRepository,
                pluginToolProvider, pluginLoader, pluginSandbox
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
