package com.orangeisland.app.viewmodel

import com.orangeisland.app.data.ConversationSettings
import com.orangeisland.app.data.McpServerConfig
import com.orangeisland.app.data.ShellDeviceConfig
import com.orangeisland.app.data.SystemPromptEntry
import com.orangeisland.app.data.local.ProjectEntity
import com.orangeisland.app.model.ChatConversation
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.ToolCallDisplayModes
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.UpdateInfo

/**
 * Single aggregate UI state for the chat screen.
 *
 * Replaces the ~45 individual `collectAsState()` calls in [ChatApp] so that only one
 * subscription is needed. Derived values (effective per-conversation overrides, active
 * project name, etc.) are resolved inside [ChatViewModel] and exposed here as plain
 * fields.
 */
data class ChatUiState(
    // ── Core chat data ───────────────────────────────────────────────
    val conversations: List<ChatConversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val allMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val currentConversationId: String? = null,
    val generatingInConversationId: String? = null,
    val projects: List<ProjectEntity> = emptyList(),
    val activeProjectId: String? = null,
    val activeProjectName: String? = null,
    val isNewChatMode: Boolean = true,
    val isSwitching: Boolean = false,
    val isTransitioningToNewChat: Boolean = false,
    /** True only during a branch switch (same conversation, different selected child). Unlike
     *  [isSwitching], the message list must stay mounted during a branch switch — clearing it
     *  (the `switchingToExisting` guard in ChatApp) left a stale scroll offset that hid the
     *  user's message until re-entering the conversation. */
    val isBranchSwitching: Boolean = false,
    val totalTokens: Int = 0,
    val selectedModel: String = Constants.EXAMPLE_MODEL_ID,
    val pendingConversationSettings: ConversationSettings? = null,
    val branchSwitchTrigger: String? = null,
    val pendingPrefillInput: String? = null,
    val isSyncingModels: Boolean = false,
    val updateDialogData: UpdateInfo? = null,
    val pendingSystemPromptId: String? = null,
    val pendingProjectId: String? = null,

    // ── Models ─────────────────────────────────────────────────────────
    val enabledModels: Set<String> = emptySet(),
    val modelAliases: Map<String, String> = emptyMap(),

    // ── Effective tool/feature toggles (per-conversation override resolved) ─
    val codeExecutionEnabled: Boolean = false,
    val googleSearchEnabled: Boolean = false,
    val thinkingEnabled: Boolean = true,
    val thinkingLevel: String = "medium",
    val thinkingBudgetEnabled: Boolean = false,
    val thinkingBudgetTokens: Int = 4096,
    val webSearchEnabled: Boolean = false,
    val globalWebSearch: Boolean = false,
    val shellEnabled: Boolean = false,
    val globalShell: Boolean = false,
    val cameraToolEnabled: Boolean = false,
    val videoNarrationEnabled: Boolean = false,
    val toolCallDisplayMode: String = ToolCallDisplayModes.DEFAULT,
    val contextWindow: Int = 20,
    val webSearchApiKeys: Map<String, String> = emptyMap(),
    val shellDevices: List<ShellDeviceConfig> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
    val mcpServerIds: List<String>? = null,

    // ── Display settings ─────────────────────────────────────────────
    val visualizeContextRollout: Boolean = false,
    val showUsageStats: Boolean = false,
    val blurEffectsEnabled: Boolean = true,
    val codeBlockWrapEnabled: Boolean = false,
    val splitBubbleByLine: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val customChatBackground: Long? = null,
    val chatBackgroundImagePath: String = "",
    val inputBackgroundImagePath: String = "",
    val topBarBackgroundImagePath: String = "",
    val reasoningBackgroundImagePath: String = "",
    val topBarAlpha: Float = 1f,
    val topBarCapsuleScale: Float = 1f,
    val customInputFieldColor: Long? = null,
    val customUserBubbleColor: Long? = null,
    val userBubbleBackgroundImagePath: String = "",
    val userBubbleCornerRadius: Float? = null,
    val customAssistantBubbleColor: Long? = null,
    val customReasoningPanelColor: Long? = null,
    val customChatTextColor: Long? = null,
    val customGlobalTextColor: Long? = null,
    val messageBubbleAlpha: Float = 1f,
    val userBubbleMaskAlpha: Float = 0.55f,
    val reasoningPanelAlpha: Float = 1f,

    // ── Project-dialog settings (also from SettingsRepository) ─────────
    val systemPrompts: List<SystemPromptEntry> = emptyList(),
    val activeSystemPromptId: String? = null,
    val globalSelectedModel: String = Constants.EXAMPLE_MODEL_ID,
) {
    companion object {
        /** Resolve the effective boolean setting with global OFF → always false semantics. */
        fun resolveEnabled(global: Boolean, override: Boolean?): Boolean =
            global && (override ?: true)
    }
}
