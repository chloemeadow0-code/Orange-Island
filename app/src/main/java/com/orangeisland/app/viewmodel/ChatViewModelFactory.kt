package com.orangeisland.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orangeisland.app.data.AutoBackupManager
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.sandbox.SandboxManagerFactory

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
    private val workflowRepository: com.orangeisland.app.data.repository.WorkflowRepository? = null,
    private val workflowApprovalGate: com.orangeisland.app.workflow.WorkflowApprovalGate? = null,
    private val pluginToolProvider: com.orangeisland.app.plugin.PluginToolProvider? = null,
    private val pluginLoader: com.orangeisland.app.plugin.PluginLoader? = null,
    private val pluginSandbox: com.orangeisland.app.plugin.PluginSandbox? = null,
    private val workflowToolProvider: com.orangeisland.app.workflow.WorkflowAiToolProvider? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                application, chatDao, settingsManager, memoryManager, context, sandboxFactory,
                autoBackupManager, conversationRepository, settingsRepository, workflowRepository,
                workflowApprovalGate, pluginToolProvider, pluginLoader, pluginSandbox,
                workflowToolProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
