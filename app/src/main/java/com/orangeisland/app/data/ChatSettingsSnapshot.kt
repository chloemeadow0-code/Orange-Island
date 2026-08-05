package com.orangeisland.app.data

import com.orangeisland.app.model.ToolCallDisplayModes

/**
 * UI-facing snapshot of all settings consumed by the chat screen.
 *
 * Aggregated inside [com.orangeisland.app.data.repository.SettingsRepository] so that
 * [com.orangeisland.app.viewmodel.ChatViewModel] does not have to subscribe to ~40
 * individual settings flows to build a single [com.orangeisland.app.viewmodel.ChatUiState].
 */
data class ChatSettingsSnapshot(
    val enabledModels: Set<String> = emptySet(),
    val modelAliases: Map<String, String> = emptyMap(),
    val visualizeContextRollout: Boolean = false,
    val showMessageUsageStats: Boolean = false,
    val maxContextWindow: Int = 20,
    val codeExecutionEnabled: Boolean = false,
    val googleSearchEnabled: Boolean = false,
    val thinkingEnabled: Boolean = true,
    val thinkingLevel: String = "medium",
    val thinkingBudgetEnabled: Boolean = false,
    val thinkingBudgetTokens: Int = 4096,
    val webSearchEnabled: Boolean = false,
    val webSearchApiKeys: Map<String, String> = emptyMap(),
    val shellEnabled: Boolean = false,
    val shellDevices: List<ShellDeviceConfig> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
    val videoNarrationEnabledModels: Set<String> = emptySet(),
    val toolCallDisplayMode: String = ToolCallDisplayModes.DEFAULT,
    val conversationSettings: Map<String, ConversationSettings> = emptyMap(),
    val blurEffectsEnabled: Boolean = true,
    val codeBlockWrapEnabled: Boolean = false,
    val splitAssistantBubbleByLine: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val customColorChatBackground: Long? = null,
    val illustrationChatBackgroundPath: String = "",
    val illustrationInputBackgroundPath: String = "",
    val illustrationTopBarBackgroundPath: String = "",
    val illustrationReasoningBackgroundPath: String = "",
    val transparencyTopBar: Float = 1f,
    val topBarCapsuleScale: Float = 1f,
    val customColorInputField: Long? = null,
    val customColorUserBubble: Long? = null,
    val illustrationUserBubbleBackgroundPath: String = "",
    val illustrationUserBubbleCornerRadius: Float = 20f,
    val customColorAssistantBubble: Long? = null,
    val customColorReasoningPanel: Long? = null,
    val customColorChatText: Long? = null,
    val customColorGlobalText: Long? = null,
    val transparencyMessageBubble: Float = 1f,
    val transparencyUserBubbleMask: Float = 0.55f,
    val transparencyReasoningPanel: Float = 1f,
    val systemPrompts: List<SystemPromptEntry> = emptyList(),
    val activeSystemPromptId: String? = null,
    val selectedModel: String = "",
)
