package com.orangeisland.app.data.repository

import com.orangeisland.app.api.openai.CustomOpenAiProvider
import com.orangeisland.app.data.ApiKeyEntry
import com.orangeisland.app.data.BuiltInPrompts
import com.orangeisland.app.data.ConversationSettings
import com.orangeisland.app.data.CustomProviderConfig
import com.orangeisland.app.data.EmbeddingModelConfig
import com.orangeisland.app.data.LocalChatModelConfig
import com.orangeisland.app.data.McpServerConfig
import com.orangeisland.app.data.PromptTemplateItem
import com.orangeisland.app.data.ChatSettingsSnapshot
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.ShellDeviceConfig
import com.orangeisland.app.data.SystemPromptEntry
import com.orangeisland.app.model.ToolCallDisplayModes
import com.orangeisland.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Repository wrapping DataStore-backed SettingsManager.
 *
 * Exposes every setting as a hot, eagerly-shared [StateFlow] (so the UI can
 * `collectAsState` and callers can read `.value` synchronously), plus the
 * setters and atomic batch mutations. This is the single shared owner of the
 * app settings surface; `ChatViewModel` and the settings pages both consume it
 * instead of re-exposing each setting individually.
 *
 * StateFlow initial values match the previous `ChatViewModel.stateIn` defaults
 * so observable behavior is unchanged.
 */
class SettingsRepository(
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope
) {
    companion object {
        const val DEFAULT_MODEL_CONTEXT_LIMIT = 100_000
    }

    private fun <T> hot(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(scope, SharingStarted.Eagerly, initial)

    // ── Read StateFlows (eagerly shared) ──────────────────────

    val selectedModel: StateFlow<String> = hot(settingsManager.selectedModel, Constants.EXAMPLE_MODEL_ID)
    val availableModels: StateFlow<Map<String, List<String>>> = hot(settingsManager.availableModels, emptyMap())
    val enabledModels: StateFlow<Set<String>> = hot(settingsManager.enabledModels, emptySet())
    val modelAliases: StateFlow<Map<String, String>> = hot(settingsManager.modelAliases, emptyMap())
    val modelContextLimits: StateFlow<Map<String, Int>> = hot(settingsManager.modelContextLimits, emptyMap())
    val apiKeys: StateFlow<List<ApiKeyEntry>> = hot(settingsManager.apiKeys, emptyList())
    val activeApiKeyIds: StateFlow<Map<String, String>> = hot(settingsManager.activeApiKeyIds, emptyMap())
    val systemPrompts: StateFlow<List<SystemPromptEntry>> = hot(settingsManager.systemPrompts, emptyList())
    val activeSystemPromptId: StateFlow<String?> = hot(settingsManager.activeSystemPromptId, null)
    val maxContextWindow: StateFlow<Int> = hot(settingsManager.maxContextWindow, 20)
    val visualizeContextRollout: StateFlow<Boolean> = hot(settingsManager.visualizeContextRollout, false)
    val showMessageUsageStats: StateFlow<Boolean> = hot(settingsManager.showMessageUsageStats, false)
    val rememberLastConversation: StateFlow<Boolean> = hot(settingsManager.rememberLastConversation, false)
    val lastActiveConversationId: StateFlow<String?> = hot(settingsManager.lastActiveConversationId, null)
    val privacyPolicyAccepted: StateFlow<Boolean?> = settingsManager.privacyPolicyAccepted
        .map<Boolean, Boolean?> { it }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val codeExecutionEnabled: StateFlow<Boolean> = hot(settingsManager.codeExecutionEnabled, false)
    val googleSearchEnabled: StateFlow<Boolean> = hot(settingsManager.googleSearchEnabled, false)
    val thinkingEnabled: StateFlow<Boolean> = hot(settingsManager.thinkingEnabled, true)
    val thinkingLevel: StateFlow<String> = hot(settingsManager.thinkingLevel, "medium")
    val thinkingBudgetEnabled: StateFlow<Boolean> = hot(settingsManager.thinkingBudgetEnabled, false)
    val thinkingBudgetTokens: StateFlow<Int> = hot(settingsManager.thinkingBudgetTokens, 4096)
    val providerBaseUrls: StateFlow<Map<String, String>> = hot(settingsManager.providerBaseUrls, emptyMap())
    val trustedHttpHosts: StateFlow<Set<String>> = hot(settingsManager.trustedHttpHosts, emptySet())
    val httpReminderSilencedHosts: StateFlow<Set<String>> = hot(settingsManager.httpReminderSilencedHosts, emptySet())
    val titleGenerationEnabled: StateFlow<Boolean> = hot(settingsManager.titleGenerationEnabled, true)
    val titleGenerationModel: StateFlow<String?> = hot(settingsManager.titleGenerationModel, null)
    val titleGenerationPrompt: StateFlow<String> = hot(settingsManager.titleGenerationPrompt, BuiltInPrompts.TITLE_GENERATION_SYSTEM)
    val autoCompressModel: StateFlow<String?> = hot(settingsManager.autoCompressModel, null)
    val autoCompressPrompt: StateFlow<String> = hot(settingsManager.autoCompressPrompt, BuiltInPrompts.HISTORY_COMPRESSION_SYSTEM)
    val imageTranscriptionEnabledModels: StateFlow<Set<String>> = hot(settingsManager.imageTranscriptionEnabledModels, emptySet())
    val imageTranscriptionModel: StateFlow<String?> = hot(settingsManager.imageTranscriptionModel, null)
    val imageTranscriptionBatchSize: StateFlow<Int> = hot(settingsManager.imageTranscriptionBatchSize, 3)
    val imageTranscriptionPrompt: StateFlow<String> = hot(settingsManager.imageTranscriptionPrompt, BuiltInPrompts.IMAGE_TRANSCRIPTION_USER)
    val videoNarrationEnabledModels: StateFlow<Set<String>> = hot(settingsManager.videoNarrationEnabledModels, emptySet())
    val videoNarrationModel: StateFlow<String?> = hot(settingsManager.videoNarrationModel, null)
    val videoNarrationPrompt: StateFlow<String> = hot(settingsManager.videoNarrationPrompt, BuiltInPrompts.VIDEO_NARRATION_USER)
    val videoNarrationFps: StateFlow<Float> = hot(settingsManager.videoNarrationFps, 1f)
    val videoNarrationDetail: StateFlow<String> = hot(settingsManager.videoNarrationDetail, "default")
    val videoNarrationMaxLongSide: StateFlow<Int> = hot(settingsManager.videoNarrationMaxLongSide, 1280)
    val accessPastConversations: StateFlow<Boolean> = hot(settingsManager.accessPastConversations, true)
    val accessSavedMemories: StateFlow<Boolean> = hot(settingsManager.accessSavedMemories, true)
    val accessActiveMemory: StateFlow<Boolean> = hot(settingsManager.accessActiveMemory, true)
    val ragSearchEnabled: StateFlow<Boolean> = hot(settingsManager.ragSearchEnabled, false)
    val autoCacheEnabled: StateFlow<Boolean> = hot(settingsManager.autoCacheEnabled, true)
    val autoUpdateCheck: StateFlow<Boolean> = hot(settingsManager.autoUpdateCheck, true)
    val lastUpdateCheckTime: StateFlow<Long> = hot(settingsManager.lastUpdateCheckTime, 0L)
    val modelSearchMethod: StateFlow<String> = hot(settingsManager.modelSearchMethod, "keyword")
    val manualSearchMethod: StateFlow<String> = hot(settingsManager.manualSearchMethod, "keyword")
    val embeddingModels: StateFlow<List<EmbeddingModelConfig>> = hot(settingsManager.embeddingModels, emptyList())
    val activeEmbeddingModelId: StateFlow<String> = hot(settingsManager.activeEmbeddingModelId, "")
    val appLanguage: StateFlow<String> = hot(settingsManager.appLanguage, "system")
    val webSearchEnabled: StateFlow<Boolean> = hot(settingsManager.webSearchEnabled, false)
    val webSearchProvider: StateFlow<String> = hot(settingsManager.webSearchProvider, "duckduckgo")
    val webSearchApiKeys: StateFlow<Map<String, String>> = hot(settingsManager.webSearchApiKeys, emptyMap())
    val webSearchNumResults: StateFlow<Int> = hot(settingsManager.webSearchNumResults, 5)
    val webSearchBaseUrl: StateFlow<String> = hot(settingsManager.webSearchBaseUrl, "")
    val imageGenEnabled: StateFlow<Boolean> = hot(settingsManager.imageGenEnabled, false)
    val imageGenModel: StateFlow<String?> = hot(settingsManager.imageGenModel, null)
    val imageGenSize: StateFlow<String> = hot(settingsManager.imageGenSize, "1024x1024")
    val showDocumentationFab: StateFlow<Boolean> = hot(settingsManager.showDocumentationFab, true)
    val shellEnabled: StateFlow<Boolean> = hot(settingsManager.shellEnabled, false)
    val proxyEnabled: StateFlow<Boolean> = hot(settingsManager.proxyEnabled, false)
    val proxyType: StateFlow<String> = hot(settingsManager.proxyType, "http")
    val proxyHost: StateFlow<String> = hot(settingsManager.proxyHost, com.orangeisland.app.data.SettingsManager.DEFAULT_PROXY_HOST)
    val proxyPort: StateFlow<String> = hot(settingsManager.proxyPort, com.orangeisland.app.data.SettingsManager.DEFAULT_PROXY_PORT)
    val proxyUsername: StateFlow<String> = hot(settingsManager.proxyUsername, "")
    val proxyPassword: StateFlow<String> = hot(settingsManager.proxyPassword, "")
    val proxyBypass: StateFlow<String> = hot(settingsManager.proxyBypass, com.orangeisland.app.data.SettingsManager.DEFAULT_PROXY_BYPASS)
    val shellConfirmEnabled: StateFlow<Boolean> = hot(settingsManager.shellConfirmEnabled, true)
    val shellDevices: StateFlow<List<ShellDeviceConfig>> = hot(settingsManager.shellDevices, emptyList())
    val sandboxEnabled: StateFlow<Boolean> = hot(settingsManager.sandboxEnabled, false)
    // ── Device Access tools (all default off) ─────────────────
    val deviceInfoEnabled: StateFlow<Boolean> = hot(settingsManager.deviceInfoEnabled, false)
    val cameraToolEnabled: StateFlow<Boolean> = hot(settingsManager.cameraToolEnabled, false)
    val locationEnabled: StateFlow<Boolean> = hot(settingsManager.locationEnabled, false)
    val calendarEnabled: StateFlow<Boolean> = hot(settingsManager.calendarEnabled, false)
    val notificationEnabled: StateFlow<Boolean> = hot(settingsManager.notificationEnabled, false)
    val mediaControlEnabled: StateFlow<Boolean> = hot(settingsManager.mediaControlEnabled, false)
    val usageStatsEnabled: StateFlow<Boolean> = hot(settingsManager.usageStatsEnabled, false)
    val navigationEnabled: StateFlow<Boolean> = hot(settingsManager.navigationEnabled, false)
    val appLockEnabled: StateFlow<Boolean> = hot(settingsManager.appLockEnabled, false)
    val appLockEntries: StateFlow<Map<String, com.orangeisland.app.data.AppLockEntry>> =
        hot(settingsManager.appLockEntries, emptyMap())
    val toastEnabled: StateFlow<Boolean> = hot(settingsManager.toastEnabled, false)
    val petEnabled: StateFlow<Boolean> = hot(settingsManager.petEnabled, false)
    val petPosX: StateFlow<Int> = hot(settingsManager.petPosX, Int.MIN_VALUE)
    val petPosY: StateFlow<Int> = hot(settingsManager.petPosY, Int.MIN_VALUE)
    val alarmEnabled: StateFlow<Boolean> = hot(settingsManager.alarmEnabled, false)
    val healthToolEnabled: StateFlow<Boolean> = hot(settingsManager.healthToolEnabled, false)
    val timeToolEnabled: StateFlow<Boolean> = hot(settingsManager.timeToolEnabled, false)
    val uiAutomationEnabled: StateFlow<Boolean> = hot(settingsManager.uiAutomationEnabled, false)
    val userInteractionEnabled: StateFlow<Boolean> = hot(settingsManager.userInteractionEnabled, true)
    val amapApiKey: StateFlow<String> = hot(settingsManager.amapApiKey, "")
    val mcpServers: StateFlow<List<com.orangeisland.app.data.McpServerConfig>> = hot(settingsManager.mcpServers, emptyList())
    val enabledPluginIds: StateFlow<Set<String>> = hot(settingsManager.enabledPluginIds, emptySet())
    val defaultTemperature: StateFlow<Float?> = hot(settingsManager.defaultTemperature, null)
    val defaultMaxTokens: StateFlow<Int?> = hot(settingsManager.defaultMaxTokens, null)
    val defaultTopP: StateFlow<Float?> = hot(settingsManager.defaultTopP, null)
    val defaultFrequencyPenalty: StateFlow<Float?> = hot(settingsManager.defaultFrequencyPenalty, null)
    val defaultPresencePenalty: StateFlow<Float?> = hot(settingsManager.defaultPresencePenalty, null)
    val conversationSettings: StateFlow<Map<String, ConversationSettings>> = hot(settingsManager.conversationSettings, emptyMap())
    val themeMode: StateFlow<String> = hot(settingsManager.themeMode, "FOLLOW_DEVICE")
    val colorScheme: StateFlow<String> = hot(settingsManager.colorScheme, "DEFAULT")
    val dynamicColor: StateFlow<Boolean> = hot(settingsManager.dynamicColor, false)
    val blurEffectsEnabled: StateFlow<Boolean> = hot(settingsManager.blurEffectsEnabled, true)
    val codeBlockWrapEnabled: StateFlow<Boolean> = hot(settingsManager.codeBlockWrapEnabled, false)
    val splitAssistantBubbleByLine: StateFlow<Boolean> = hot(settingsManager.splitAssistantBubbleByLine, false)
    val nonStreamOutputEnabled: StateFlow<Boolean> = hot(settingsManager.nonStreamOutputEnabled, false)
    val customColorChatText: StateFlow<Long?> = hot(settingsManager.customColorChatText, null)
    val customColorGlobalText: StateFlow<Long?> = hot(settingsManager.customColorGlobalText, null)
    val customColorUserBubble: StateFlow<Long?> = hot(settingsManager.customColorUserBubble, null)
    val customColorAssistantBubble: StateFlow<Long?> = hot(settingsManager.customColorAssistantBubble, null)
    val customColorReasoningPanel: StateFlow<Long?> = hot(settingsManager.customColorReasoningPanel, null)
    val customColorChatBackground: StateFlow<Long?> = hot(settingsManager.customColorChatBackground, null)
    val customColorAccent: StateFlow<Long?> = hot(settingsManager.customColorAccent, null)
    val customColorInputField: StateFlow<Long?> = hot(settingsManager.customColorInputField, null)
    val illustrationChatBackgroundPath: StateFlow<String> = hot(settingsManager.illustrationChatBackgroundPath, "")
    val illustrationInputBackgroundPath: StateFlow<String> = hot(settingsManager.illustrationInputBackgroundPath, "")
    val illustrationDrawerBackgroundPath: StateFlow<String> = hot(settingsManager.illustrationDrawerBackgroundPath, "")
    val illustrationUserBubbleBackgroundPath: StateFlow<String> = hot(settingsManager.illustrationUserBubbleBackgroundPath, "")
    val illustrationUserBubbleCornerRadius: StateFlow<Float> = hot(settingsManager.illustrationUserBubbleCornerRadius, 20f)
    val illustrationTopBarBackgroundPath: StateFlow<String> = hot(settingsManager.illustrationTopBarBackgroundPath, "")
    val illustrationReasoningBackgroundPath: StateFlow<String> = hot(settingsManager.illustrationReasoningBackgroundPath, "")
    val transparencyTopBar: StateFlow<Float> = hot(settingsManager.transparencyTopBar, 1f)
    val topBarCapsuleScale: StateFlow<Float> = hot(settingsManager.topBarCapsuleScale, 1f)
    val transparencyMessageBubble: StateFlow<Float> = hot(settingsManager.transparencyMessageBubble, 1f)
    val transparencyUserBubbleMask: StateFlow<Float> = hot(settingsManager.transparencyUserBubbleMask, 0.55f)
    val transparencyReasoningPanel: StateFlow<Float> = hot(settingsManager.transparencyReasoningPanel, 1f)
    val transparencyDrawerItem: StateFlow<Float> = hot(settingsManager.transparencyDrawerItem, 1f)
    val recentCustomColors: StateFlow<List<Long>> = hot(settingsManager.recentCustomColors, emptyList())
    val hapticsEnabled: StateFlow<Boolean> = hot(settingsManager.hapticsEnabled, true)
    val toolCallDisplayMode: StateFlow<String> = hot(settingsManager.toolCallDisplayMode, ToolCallDisplayModes.DEFAULT)
    val schemeStyle: StateFlow<String> = hot(settingsManager.schemeStyle, "TONAL_SPOT")
    val fontPreference: StateFlow<String> = hot(settingsManager.fontPreference, "app_default")
    val customFontPath: StateFlow<String> = hot(settingsManager.customFontPath, "")
    val customFontName: StateFlow<String> = hot(settingsManager.customFontName, "")
    val fontSizeTier: StateFlow<String> = hot(settingsManager.fontSizeTier, com.orangeisland.app.ui.theme.FontSizeTiers.DEFAULT)
    val searchContextWindow: StateFlow<Int> = hot(settingsManager.searchContextWindow, 8)
    val searchMatchLimit: StateFlow<Int> = hot(settingsManager.searchMatchLimit, 10)
    val ragThreshold: StateFlow<Float> = hot(settingsManager.ragThreshold, 0.5f)
    val localChatModels: StateFlow<List<LocalChatModelConfig>> = hot(settingsManager.localChatModels, emptyList())
    val customProviders: StateFlow<List<CustomProviderConfig>> = hot(settingsManager.customProviders, emptyList())
    val manualModels: StateFlow<Map<String, List<String>>> = hot(settingsManager.manualModels, emptyMap())
    val lastModelsFetchFingerprint: StateFlow<String> = hot(settingsManager.lastModelsFetchFingerprint, "")
    // ── Auto Backup ───────────────────────────────────────────
    val autoBackupEnabled: StateFlow<Boolean> = hot(settingsManager.autoBackupEnabled, true)
    val autoBackupPeriodHours: StateFlow<Int> = hot(settingsManager.autoBackupPeriodHours, 24)
    val autoBackupCategories: StateFlow<String> = hot(settingsManager.autoBackupCategories, "conversations,memories,system_prompts,settings")
    val autoBackupDirectory: StateFlow<String> = hot(settingsManager.autoBackupDirectory, "Download/OrangeIsland/Backup")
    val autoDeleteEnabled: StateFlow<Boolean> = hot(settingsManager.autoDeleteEnabled, true)
    val autoDeletePeriodHours: StateFlow<Int> = hot(settingsManager.autoDeletePeriodHours, 168)
    val lastBackupTimestamp: StateFlow<Long> = hot(settingsManager.lastBackupTimestamp, 0L)
    // ── Health / Gadgetbridge / Sync ──────────────────────────
    val gadgetbridgeEnabled: StateFlow<Boolean> = hot(settingsManager.gadgetbridgeEnabled, false)
    val gadgetbridgeDbPath: StateFlow<String> = hot(settingsManager.gadgetbridgeDbPath, "")
    val healthSyncEnabled: StateFlow<Boolean> = hot(settingsManager.healthSyncEnabled, false)
    val healthSyncSupabaseUrl: StateFlow<String> = hot(settingsManager.healthSyncSupabaseUrl, "")
    val healthSyncSupabaseApiKey: StateFlow<String> = hot(settingsManager.healthSyncSupabaseApiKey, "")
    val healthSyncTableName: StateFlow<String> = hot(settingsManager.healthSyncTableName, "device_data")
    val autoApproveSensitiveTools: StateFlow<Boolean> = hot(settingsManager.autoApproveSensitiveTools, true)
    val environmentAwarenessEnabled: StateFlow<Boolean> = hot(settingsManager.environmentAwarenessEnabled, false)
    val miniAppEntries: StateFlow<List<com.orangeisland.app.data.MiniAppEntry>> = hot(settingsManager.miniAppEntries, emptyList())
    // ── Plugin device id ──────────────────────────────────────
    // Auto-injected per-install UUID (read-only — no UI, no setter).
    val appUserId: StateFlow<String> = hot(settingsManager.appUserId, "")
    // ── Account / Auth (local session mirror; drives the login gate) ─────────
    val loggedIn: StateFlow<Boolean> = hot(settingsManager.loggedIn, false)
    val userName: StateFlow<String> = hot(settingsManager.userName, "")
    val userEmail: StateFlow<String> = hot(settingsManager.userEmail, "")
    // ── Plugin configs (per-plugin user-filled values) ────────
    val pluginConfigs: StateFlow<Map<String, Map<String, String>>> = hot(settingsManager.pluginConfigs, emptyMap())
    // ── Text-to-Speech ──────────────────────────────────────────
    val ttsEnabled: StateFlow<Boolean> = hot(settingsManager.ttsEnabled, false)
    val ttsProvider: StateFlow<String> = hot(settingsManager.ttsProvider, "elevenlabs")
    val ttsApiKey: StateFlow<String> = hot(settingsManager.ttsApiKey, "")
    val ttsVoiceId: StateFlow<String> = hot(settingsManager.ttsVoiceId, "")
    val ttsModel: StateFlow<String> = hot(settingsManager.ttsModel, "")
    val ttsSpeed: StateFlow<Float> = hot(settingsManager.ttsSpeed, 1.0f)
    val ttsOutputFormat: StateFlow<String> = hot(settingsManager.ttsOutputFormat, "")
    val ttsStability: StateFlow<Float> = hot(settingsManager.ttsStability, 0.5f)
    val ttsSimilarityBoost: StateFlow<Float> = hot(settingsManager.ttsSimilarityBoost, 0.75f)
    val ttsStyle: StateFlow<Float> = hot(settingsManager.ttsStyle, 0.0f)
    val ttsVolume: StateFlow<Float> = hot(settingsManager.ttsVolume, 1.0f)
    val ttsPitch: StateFlow<Float> = hot(settingsManager.ttsPitch, 0.0f)
    // ── Speech-to-Text (SiliconFlow) ───────────────────────────
    val sttEnabled: StateFlow<Boolean> = hot(settingsManager.sttEnabled, false)
    val sttApiKey: StateFlow<String> = hot(settingsManager.sttApiKey, "")
    val sttModel: StateFlow<String> = hot(settingsManager.sttModel, "")
    val sttBaseUrl: StateFlow<String> = hot(settingsManager.sttBaseUrl, "")

    init {
        // Keep HttpClient's in-memory trusted-host set in sync with what's persisted —
        // it can't read DataStore itself (guardCleartextCredentials is a synchronous call
        // on the hot request path), so this repository pushes updates to it instead,
        // same pattern as would be used for the network proxy config.
        scope.launch {
            trustedHttpHosts.collect { hosts ->
                com.orangeisland.app.api.HttpClient.setTrustedHttpHosts(hosts)
            }
        }
    }

    /**
     * Single aggregated snapshot of every setting consumed by the chat screen.
     * Use this instead of collecting the ~40 individual settings flows when building a
     * [com.orangeisland.app.viewmodel.ChatUiState].
     */
    @Suppress("UNCHECKED_CAST")
    val chatSettingsSnapshot: StateFlow<ChatSettingsSnapshot> = combine(
        listOf<Flow<Any?>>(
            enabledModels,
            modelAliases,
            visualizeContextRollout,
            showMessageUsageStats,
            maxContextWindow,
            codeExecutionEnabled,
            googleSearchEnabled,
            thinkingEnabled,
            thinkingLevel,
            thinkingBudgetEnabled,
            thinkingBudgetTokens,
            webSearchEnabled,
            webSearchApiKeys,
            shellEnabled,
            shellDevices,
            mcpServers,
            videoNarrationEnabledModels,
            toolCallDisplayMode,
            conversationSettings,
            blurEffectsEnabled,
            codeBlockWrapEnabled,
            splitAssistantBubbleByLine,
            hapticsEnabled,
            customColorChatBackground,
            illustrationChatBackgroundPath,
            illustrationInputBackgroundPath,
            illustrationTopBarBackgroundPath,
            illustrationReasoningBackgroundPath,
            transparencyTopBar,
            topBarCapsuleScale,
            customColorInputField,
            customColorUserBubble,
            illustrationUserBubbleBackgroundPath,
            illustrationUserBubbleCornerRadius,
            customColorAssistantBubble,
            customColorReasoningPanel,
            customColorChatText,
            customColorGlobalText,
            transparencyMessageBubble,
            transparencyUserBubbleMask,
            transparencyReasoningPanel,
            systemPrompts,
            activeSystemPromptId,
            selectedModel,
            cameraToolEnabled
        )
    ) { values: Array<Any?> ->
        ChatSettingsSnapshot(
            enabledModels = values[0] as Set<String>,
            modelAliases = values[1] as Map<String, String>,
            visualizeContextRollout = values[2] as Boolean,
            showMessageUsageStats = values[3] as Boolean,
            maxContextWindow = values[4] as Int,
            codeExecutionEnabled = values[5] as Boolean,
            googleSearchEnabled = values[6] as Boolean,
            thinkingEnabled = values[7] as Boolean,
            thinkingLevel = values[8] as String,
            thinkingBudgetEnabled = values[9] as Boolean,
            thinkingBudgetTokens = values[10] as Int,
            webSearchEnabled = values[11] as Boolean,
            webSearchApiKeys = values[12] as Map<String, String>,
            shellEnabled = values[13] as Boolean,
            shellDevices = values[14] as List<ShellDeviceConfig>,
            mcpServers = values[15] as List<McpServerConfig>,
            videoNarrationEnabledModels = values[16] as Set<String>,
            toolCallDisplayMode = values[17] as String,
            conversationSettings = values[18] as Map<String, ConversationSettings>,
            blurEffectsEnabled = values[19] as Boolean,
            codeBlockWrapEnabled = values[20] as Boolean,
            splitAssistantBubbleByLine = values[21] as Boolean,
            hapticsEnabled = values[22] as Boolean,
            customColorChatBackground = values[23] as Long?,
            illustrationChatBackgroundPath = values[24] as String,
            illustrationInputBackgroundPath = values[25] as String,
            illustrationTopBarBackgroundPath = values[26] as String,
            illustrationReasoningBackgroundPath = values[27] as String,
            transparencyTopBar = values[28] as Float,
            topBarCapsuleScale = values[29] as Float,
            customColorInputField = values[30] as Long?,
            customColorUserBubble = values[31] as Long?,
            illustrationUserBubbleBackgroundPath = values[32] as String,
            illustrationUserBubbleCornerRadius = values[33] as Float,
            customColorAssistantBubble = values[34] as Long?,
            customColorReasoningPanel = values[35] as Long?,
            customColorChatText = values[36] as Long?,
            customColorGlobalText = values[37] as Long?,
            transparencyMessageBubble = values[38] as Float,
            transparencyUserBubbleMask = values[39] as Float,
            transparencyReasoningPanel = values[40] as Float,
            systemPrompts = values[41] as List<SystemPromptEntry>,
            activeSystemPromptId = values[42] as String?,
            selectedModel = values[43] as String,
            cameraToolEnabled = values[44] as Boolean
        )
    }.stateIn(scope, SharingStarted.Eagerly, ChatSettingsSnapshot())

    // ── Write (fire-and-forget; read current state from own StateFlows) ──
    //
    // These setters launch on [scope] and read "current" list/map state from this
    // repository's own `.value`, so callers no longer pass it in. Absorbed from the
    // former `SettingsDelegate`; logic is byte-for-byte equivalent.

    // Model selection
    fun setSelectedModel(model: String) {
        scope.launch { settingsManager.saveSelectedModel(model) }
    }

    fun setEnabledModels(models: Set<String>) {
        scope.launch {
            settingsManager.saveEnabledModels(models)
            if (!models.contains(selectedModel.value)) {
                settingsManager.saveSelectedModel(models.firstOrNull() ?: "")
            }
        }
    }

    fun updateModelAlias(model: String, alias: String) {
        scope.launch {
            val updated = modelAliases.value.toMutableMap()
            if (alias.isBlank()) updated.remove(model) else updated[model] = alias
            settingsManager.saveModelAliases(updated)
        }
    }

    fun updateModelContextLimit(model: String, limit: Int?) {
        scope.launch {
            val updated = modelContextLimits.value.toMutableMap()
            if (limit == null || limit <= 0) updated.remove(model) else updated[model] = limit
            settingsManager.saveModelContextLimits(updated)
        }
    }

    // API keys
    fun addApiKey(name: String, key: String, provider: String) {
        scope.launch {
            val entry = ApiKeyEntry(name = name, key = key, provider = provider)
            settingsManager.saveApiKeys(apiKeys.value + entry)
            settingsManager.setActiveApiKeyId(provider, entry.id)
        }
    }

    /**
     * Store exactly one key for [provider]: update the existing entry in place if there
     * is one, otherwise add it — and drop any extra entries for the same provider.
     * Idempotent, so onboarding never accumulates duplicates.
     */
    fun upsertApiKey(name: String, key: String, provider: String) {
        scope.launch {
            val current = apiKeys.value
            val existing = current.firstOrNull { it.provider == provider }
            val entry = existing?.copy(name = name, key = key) ?: ApiKeyEntry(name = name, key = key, provider = provider)
            settingsManager.saveApiKeys(current.filter { it.provider != provider } + entry)
            settingsManager.setActiveApiKeyId(provider, entry.id)
        }
    }

    fun deleteApiKey(id: String) {
        scope.launch {
            val current = apiKeys.value
            val entry = current.find { it.id == id } ?: return@launch
            val newList = current.filter { it.id != id }
            if (activeApiKeyIds.value[entry.provider] == id) {
                val other = newList.firstOrNull { it.provider == entry.provider }
                settingsManager.setActiveApiKeyId(entry.provider, other?.id)
            }
            settingsManager.saveApiKeys(newList)
        }
    }

    fun updateApiKey(id: String, name: String, key: String) {
        scope.launch {
            settingsManager.saveApiKeys(apiKeys.value.map { if (it.id == id) it.copy(name = name, key = key) else it })
        }
    }

    fun setActiveApiKey(provider: String, id: String) {
        scope.launch { settingsManager.setActiveApiKeyId(provider, id) }
    }

    // System prompts
    fun addSystemPrompt(
        title: String, systemItems: List<PromptTemplateItem>,
        userPrependItems: List<PromptTemplateItem>, userPostpendItems: List<PromptTemplateItem>
    ) {
        scope.launch {
            val newList = systemPrompts.value + SystemPromptEntry(title = title, systemItems = systemItems, userPrependItems = userPrependItems, userPostpendItems = userPostpendItems)
            settingsManager.saveSystemPrompts(newList)
            if (activeSystemPromptId.value == null) settingsManager.setActiveSystemPromptId(newList.last().id)
        }
    }

    fun deleteSystemPrompt(id: String) {
        scope.launch {
            val newList = systemPrompts.value.filter { it.id != id }
            settingsManager.saveSystemPrompts(newList)
            if (activeSystemPromptId.value == id) settingsManager.setActiveSystemPromptId(newList.firstOrNull()?.id)
        }
    }

    fun updateSystemPrompt(
        id: String, title: String, systemItems: List<PromptTemplateItem>,
        userPrependItems: List<PromptTemplateItem>, userPostpendItems: List<PromptTemplateItem>
    ) {
        scope.launch {
            settingsManager.saveSystemPrompts(systemPrompts.value.map { if (it.id == id) it.copy(title = title, content = "", systemItems = systemItems, userPrependItems = userPrependItems, userPostpendItems = userPostpendItems) else it })
        }
    }

    fun setActiveSystemPrompt(id: String) {
        scope.launch { settingsManager.setActiveSystemPromptId(id) }
    }

    // Custom provider CRUD (callbacks touch ChatViewModel's live provider map)
    fun addCustomProvider(name: String, baseUrl: String, onProviderAdd: (String, CustomOpenAiProvider) -> Unit) {
        scope.launch {
            settingsManager.saveProviderBaseUrl(name, baseUrl)
            settingsManager.saveCustomProviders(customProviders.value + CustomProviderConfig(name))
            onProviderAdd(name, CustomOpenAiProvider(name, baseUrl))
        }
    }

    fun renameCustomProvider(
        oldName: String, newName: String,
        onProviderRemove: (String) -> Unit,
        onProviderAdd: (String, CustomOpenAiProvider) -> Unit
    ) {
        val url = providerBaseUrls.value[oldName] ?: return
        scope.launch {
            onProviderRemove(oldName)
            val updated = customProviders.value.toMutableList()
            val idx = updated.indexOfFirst { it.name == oldName }
            if (idx >= 0) {
                updated[idx] = CustomProviderConfig(newName)
                settingsManager.saveCustomProviders(updated)
                settingsManager.saveProviderBaseUrl(oldName, "")
                settingsManager.saveProviderBaseUrl(newName, url)
                val models = availableModels.value.toMutableMap()
                models[newName] = models.remove(oldName) ?: emptyList()
                settingsManager.saveAvailableModels(newName, models[newName] ?: emptyList())
                settingsManager.saveAvailableModels(oldName, emptyList())
                val newManual = manualModels.value.toMutableMap()
                newManual[newName] = (newManual.remove(oldName) ?: emptyList()).map { it.replace("$oldName:", "$newName:") }
                settingsManager.saveManualModelsForProvider(newName, newManual[newName].orEmpty())
                settingsManager.saveManualModelsForProvider(oldName, emptyList())
                val newEnabled = enabledModels.value.map { if (it.startsWith("$oldName:")) it.replace("$oldName:", "$newName:") else it }.toSet()
                settingsManager.saveEnabledModels(newEnabled)
                val newAliases = modelAliases.value.mapKeys { if (it.key.startsWith("$oldName:")) it.key.replace("$oldName:", "$newName:") else it.key }
                settingsManager.saveModelAliases(newAliases)
                settingsManager.setActiveApiKeyId(oldName, null)
                val newKeys = apiKeys.value.map { if (it.provider == oldName) it.copy(provider = newName) else it }
                settingsManager.saveApiKeys(newKeys)
                activeApiKeyIds.value[oldName]?.let { settingsManager.setActiveApiKeyId(newName, it) }
            }
            onProviderAdd(newName, CustomOpenAiProvider(newName, url))
        }
    }

    fun deleteCustomProvider(name: String, onProviderRemove: (String) -> Unit) {
        scope.launch {
            settingsManager.saveCustomProviders(customProviders.value.filter { it.name != name })
            onProviderRemove(name)
            settingsManager.saveAvailableModels(name, emptyList())
            settingsManager.saveManualModelsForProvider(name, emptyList())
            settingsManager.saveEnabledModels(enabledModels.value.filter { !it.startsWith("$name:") }.toSet())
            settingsManager.saveModelAliases(modelAliases.value.filterKeys { !it.startsWith("$name:") })
            settingsManager.saveProviderBaseUrl(name, "")
            settingsManager.saveApiKeys(apiKeys.value.filter { it.provider != name })
            settingsManager.setActiveApiKeyId(name, null)
        }
    }

    // Manual model CRUD (custom providers only). Manual entries are stored separately
    // from availableModels so the fetch pipeline can't overwrite them; they are merged
    // with fetched lists at the UI layer (SettingsModelsPage).
    fun addManualModel(provider: String, modelId: String) {
        val id = modelId.trim()
        if (id.isBlank()) return
        scope.launch {
            val current = manualModels.value[provider].orEmpty()
            val prefixed = if (id.startsWith("$provider:")) id else "$provider:$id"
            if (current.contains(prefixed)) return@launch
            settingsManager.saveManualModelsForProvider(provider, current + prefixed)
        }
    }

    fun removeManualModel(provider: String, modelId: String) {
        val prefixed = if (modelId.startsWith("$provider:")) modelId else "$provider:$modelId"
        scope.launch {
            val current = manualModels.value[provider].orEmpty()
            settingsManager.saveManualModelsForProvider(provider, current - prefixed)
            // Clean up derived state so no dangling enabled/alias entries remain.
            settingsManager.saveEnabledModels(enabledModels.value - prefixed)
            settingsManager.saveModelAliases(modelAliases.value - prefixed)
        }
    }

    // Image transcription
    fun addImageTranscriptionModels(models: Set<String>) = scope.launch { settingsManager.saveImageTranscriptionEnabledModels(imageTranscriptionEnabledModels.value + models) }
    fun removeImageTranscriptionModel(model: String) = scope.launch { settingsManager.saveImageTranscriptionEnabledModels(imageTranscriptionEnabledModels.value - model) }

    // Video narration
    fun addVideoNarrationModels(models: Set<String>) = scope.launch { settingsManager.saveVideoNarrationEnabledModels(videoNarrationEnabledModels.value + models) }
    fun removeVideoNarrationModel(model: String) = scope.launch { settingsManager.saveVideoNarrationEnabledModels(videoNarrationEnabledModels.value - model) }

    // Shell devices
    fun removeShellDevice(deviceId: String) = scope.launch { settingsManager.saveShellDevices(shellDevices.value.filter { it.id != deviceId }) }

    fun setConversationSettings(convId: String, settings: ConversationSettings?) = scope.launch { settingsManager.saveConversationSettings(convId, settings) }

    // ── Simple setting toggles ────────────────────────────────
    fun setMaxContextWindow(window: Int) = scope.launch { settingsManager.saveMaxContextWindow(window) }
    fun setVisualizeContextRollout(enabled: Boolean) = scope.launch { settingsManager.saveVisualizeContextRollout(enabled) }
    fun setShowMessageUsageStats(enabled: Boolean) = scope.launch { settingsManager.saveShowMessageUsageStats(enabled) }
    fun setRememberLastConversation(enabled: Boolean) = scope.launch { settingsManager.saveRememberLastConversation(enabled) }
    fun setLastActiveConversationId(id: String?) = scope.launch { settingsManager.saveLastActiveConversationId(id) }
    fun setPrivacyPolicyAccepted(accepted: Boolean) = scope.launch { settingsManager.savePrivacyPolicyAccepted(accepted) }
    fun setProviderBaseUrl(provider: String, url: String) = scope.launch { settingsManager.saveProviderBaseUrl(provider, url) }
    fun trustHttpHost(host: String) = scope.launch { settingsManager.addTrustedHttpHost(host) }
    fun untrustHttpHost(host: String) = scope.launch { settingsManager.removeTrustedHttpHost(host) }
    fun silenceHttpReminder(host: String) = scope.launch { settingsManager.setHttpReminderSilenced(host, true) }
    fun unsilenceHttpReminder(host: String) = scope.launch { settingsManager.setHttpReminderSilenced(host, false) }
    fun setTitleGenerationEnabled(enabled: Boolean) = scope.launch { settingsManager.saveTitleGenerationEnabled(enabled) }
    fun setTitleGenerationModel(model: String?) = scope.launch { settingsManager.saveTitleGenerationModel(model) }
    fun setTitleGenerationPrompt(prompt: String) = scope.launch { settingsManager.saveTitleGenerationPrompt(prompt) }
    fun setAutoCompressModel(model: String?) = scope.launch { settingsManager.saveAutoCompressModel(model) }
    fun setAutoCompressPrompt(prompt: String) = scope.launch { settingsManager.saveAutoCompressPrompt(prompt) }
    fun setImageTranscriptionModel(model: String?) = scope.launch { settingsManager.saveImageTranscriptionModel(model) }
    fun setImageTranscriptionBatchSize(size: Int) = scope.launch { settingsManager.saveImageTranscriptionBatchSize(size) }
    fun setImageTranscriptionPrompt(prompt: String) = scope.launch { settingsManager.saveImageTranscriptionPrompt(prompt) }
    fun setVideoNarrationEnabledModels(models: Set<String>) = scope.launch { settingsManager.saveVideoNarrationEnabledModels(models) }
    fun setVideoNarrationModel(model: String?) = scope.launch { settingsManager.saveVideoNarrationModel(model) }
    fun setVideoNarrationPrompt(prompt: String) = scope.launch { settingsManager.saveVideoNarrationPrompt(prompt) }
    fun setVideoNarrationFps(fps: Float) = scope.launch { settingsManager.saveVideoNarrationFps(fps) }
    fun setVideoNarrationDetail(detail: String) = scope.launch { settingsManager.saveVideoNarrationDetail(detail) }
    fun setVideoNarrationMaxLongSide(size: Int) = scope.launch { settingsManager.saveVideoNarrationMaxLongSide(size) }
    fun setAccessPastConversations(enabled: Boolean) = scope.launch { settingsManager.saveAccessPastConversations(enabled) }
    fun setAccessSavedMemories(enabled: Boolean) = scope.launch { settingsManager.saveAccessSavedMemories(enabled) }
    fun setAccessActiveMemory(enabled: Boolean) = scope.launch { settingsManager.saveAccessActiveMemory(enabled) }
    fun setRagSearchEnabled(enabled: Boolean) = scope.launch { settingsManager.saveRagSearchEnabled(enabled) }
    fun setAutoCacheEnabled(enabled: Boolean) = scope.launch { settingsManager.saveAutoCacheEnabled(enabled) }
    fun setAutoUpdateCheck(enabled: Boolean) = scope.launch { settingsManager.saveAutoUpdateCheck(enabled) }
    fun setLastUpdateCheckTime(time: Long) = scope.launch { settingsManager.saveLastUpdateCheckTime(time) }
    fun setModelSearchMethod(method: String) = scope.launch { settingsManager.saveModelSearchMethod(method) }
    fun setManualSearchMethod(method: String) = scope.launch { settingsManager.saveManualSearchMethod(method) }
    fun setAppLanguage(language: String) = scope.launch { settingsManager.saveAppLanguage(language) }
    fun setWebSearchEnabled(enabled: Boolean) = scope.launch { settingsManager.saveWebSearchEnabled(enabled) }
    fun setWebSearchProvider(provider: String) = scope.launch { settingsManager.saveWebSearchProvider(provider) }
    fun setWebSearchApiKey(provider: String, apiKey: String) = scope.launch { settingsManager.saveWebSearchApiKey(provider, apiKey) }
    fun setWebSearchNumResults(n: Int) = scope.launch { settingsManager.saveWebSearchNumResults(n) }
    fun setWebSearchBaseUrl(url: String) = scope.launch { settingsManager.saveWebSearchBaseUrl(url) }
    fun setImageGenEnabled(enabled: Boolean) = scope.launch { settingsManager.saveImageGenEnabled(enabled) }
    fun setImageGenModel(model: String?) = scope.launch { settingsManager.saveImageGenModel(model) }
    fun setImageGenSize(size: String) = scope.launch { settingsManager.saveImageGenSize(size) }
    fun setShowDocumentationFab(enabled: Boolean) = scope.launch { settingsManager.saveShowDocumentationFab(enabled) }
    fun setShellEnabled(enabled: Boolean) = scope.launch { settingsManager.saveShellEnabled(enabled) }
    fun setProxyEnabled(enabled: Boolean) = scope.launch { settingsManager.saveProxyEnabled(enabled) }
    fun setProxyType(type: String) = scope.launch { settingsManager.saveProxyType(type) }
    fun setProxyHost(host: String) = scope.launch { settingsManager.saveProxyHost(host) }
    fun setProxyPort(port: String) = scope.launch { settingsManager.saveProxyPort(port) }
    fun setProxyUsername(user: String) = scope.launch { settingsManager.saveProxyUsername(user) }
    fun setProxyPassword(pass: String) = scope.launch { settingsManager.saveProxyPassword(pass) }
    fun setProxyBypass(bypass: String) = scope.launch { settingsManager.saveProxyBypass(bypass) }
    fun setSandboxEnabled(enabled: Boolean) = scope.launch { settingsManager.saveSandboxEnabled(enabled) }

    // ── Device Access tools ───────────────────────────────────
    fun setDeviceInfoEnabled(enabled: Boolean) = scope.launch { settingsManager.saveDeviceInfoEnabled(enabled) }
    fun setCameraToolEnabled(enabled: Boolean) = scope.launch { settingsManager.saveCameraToolEnabled(enabled) }
    fun setLocationEnabled(enabled: Boolean) = scope.launch { settingsManager.saveLocationEnabled(enabled) }
    fun setCalendarEnabled(enabled: Boolean) = scope.launch { settingsManager.saveCalendarEnabled(enabled) }
    fun setNotificationEnabled(enabled: Boolean) = scope.launch { settingsManager.saveNotificationEnabled(enabled) }
    fun setMediaControlEnabled(enabled: Boolean) = scope.launch { settingsManager.saveMediaControlEnabled(enabled) }
    fun setUsageStatsEnabled(enabled: Boolean) = scope.launch { settingsManager.saveUsageStatsEnabled(enabled) }
    fun setNavigationEnabled(enabled: Boolean) = scope.launch { settingsManager.saveNavigationEnabled(enabled) }
    fun setAppLockEnabled(enabled: Boolean) = scope.launch { settingsManager.saveAppLockEnabled(enabled) }
    fun setAppLockEntries(entries: Map<String, com.orangeisland.app.data.AppLockEntry>) =
        scope.launch { settingsManager.saveAppLockEntries(entries) }
    fun setToastEnabled(enabled: Boolean) = scope.launch { settingsManager.saveToastEnabled(enabled) }
    fun setPetEnabled(enabled: Boolean) = scope.launch { settingsManager.savePetEnabled(enabled) }
    fun setPetPos(x: Int, y: Int) = scope.launch { settingsManager.savePetPos(x, y) }
    fun setAlarmEnabled(enabled: Boolean) = scope.launch { settingsManager.saveAlarmEnabled(enabled) }
    fun setHealthToolEnabled(v: Boolean) = scope.launch { settingsManager.saveHealthToolEnabled(v) }
    fun setTimeToolEnabled(v: Boolean) = scope.launch { settingsManager.saveTimeToolEnabled(v) }
    fun setUiAutomationEnabled(enabled: Boolean) = scope.launch { settingsManager.saveUiAutomationEnabled(enabled) }
    fun setUserInteractionEnabled(enabled: Boolean) = scope.launch { settingsManager.saveUserInteractionEnabled(enabled) }
    fun setAmapApiKey(key: String) = scope.launch { settingsManager.saveAmapApiKey(key) }
    fun setEnvironmentAwarenessEnabled(enabled: Boolean) = scope.launch { settingsManager.saveEnvironmentAwarenessEnabled(enabled) }

    // ── MCP servers ──────────────────────────────────────────
    fun saveMcpServers(servers: List<com.orangeisland.app.data.McpServerConfig>) =
        scope.launch { settingsManager.saveMcpServers(servers) }

    fun addMcpServer(server: com.orangeisland.app.data.McpServerConfig) = scope.launch {
        settingsManager.saveMcpServers(mcpServers.value + server)
    }

    fun updateMcpServer(server: com.orangeisland.app.data.McpServerConfig) = scope.launch {
        settingsManager.saveMcpServers(mcpServers.value.map { if (it.id == server.id) server else it })
    }

    fun deleteMcpServer(id: String) = scope.launch {
        settingsManager.saveMcpServers(mcpServers.value.filter { it.id != id })
    }

    suspend fun getMcpServers(): List<com.orangeisland.app.data.McpServerConfig> = settingsManager.mcpServers.first()

    fun setPluginEnabled(pluginId: String, enabled: Boolean) = scope.launch {
        val current = enabledPluginIds.value
        val next = if (enabled) current + pluginId else current - pluginId
        settingsManager.saveEnabledPluginIds(next)
    }

    suspend fun getEnabledPluginIds(): Set<String> = settingsManager.enabledPluginIds.first()
    fun setThinkingEnabled(enabled: Boolean) = scope.launch { settingsManager.saveThinkingEnabled(enabled) }
    fun setThinkingLevel(level: String) = scope.launch { settingsManager.saveThinkingLevel(level) }
    fun setThinkingBudgetEnabled(enabled: Boolean) = scope.launch { settingsManager.saveThinkingBudgetEnabled(enabled) }
    fun setThinkingBudgetTokens(tokens: Int) = scope.launch { settingsManager.saveThinkingBudgetTokens(tokens) }
    fun setDefaultTemperature(v: Float?) = scope.launch { settingsManager.saveDefaultTemperature(v) }
    fun setDefaultMaxTokens(v: Int?) = scope.launch { settingsManager.saveDefaultMaxTokens(v) }
    fun setDefaultTopP(v: Float?) = scope.launch { settingsManager.saveDefaultTopP(v) }
    fun setDefaultFrequencyPenalty(v: Float?) = scope.launch { settingsManager.saveDefaultFrequencyPenalty(v) }
    fun setDefaultPresencePenalty(v: Float?) = scope.launch { settingsManager.saveDefaultPresencePenalty(v) }
    fun setMiniAppEntries(entries: List<com.orangeisland.app.data.MiniAppEntry>) = scope.launch { settingsManager.saveMiniAppEntries(entries) }
    fun setThemeMode(mode: String) = scope.launch { settingsManager.saveThemeMode(mode) }
    fun setColorScheme(scheme: String) = scope.launch { settingsManager.saveColorScheme(scheme) }
    fun setDynamicColor(enabled: Boolean) = scope.launch { settingsManager.saveDynamicColor(enabled) }
    fun setBlurEffectsEnabled(enabled: Boolean) = scope.launch { settingsManager.saveBlurEffectsEnabled(enabled) }
    fun setCodeBlockWrapEnabled(enabled: Boolean) = scope.launch { settingsManager.saveCodeBlockWrapEnabled(enabled) }
    fun setSplitAssistantBubbleByLine(enabled: Boolean) = scope.launch { settingsManager.saveSplitAssistantBubbleByLine(enabled) }
    fun setNonStreamOutputEnabled(enabled: Boolean) = scope.launch { settingsManager.saveNonStreamOutputEnabled(enabled) }
    fun setCustomColorChatText(v: Long?) = scope.launch { settingsManager.saveCustomColorChatText(v) }
    fun setCustomColorGlobalText(v: Long?) = scope.launch { settingsManager.saveCustomColorGlobalText(v) }
    fun setCustomColorUserBubble(v: Long?) = scope.launch { settingsManager.saveCustomColorUserBubble(v) }
    fun setCustomColorAssistantBubble(v: Long?) = scope.launch { settingsManager.saveCustomColorAssistantBubble(v) }
    fun setCustomColorReasoningPanel(v: Long?) = scope.launch { settingsManager.saveCustomColorReasoningPanel(v) }
    fun setCustomColorChatBackground(v: Long?) = scope.launch { settingsManager.saveCustomColorChatBackground(v) }
    fun setCustomColorAccent(v: Long?) = scope.launch { settingsManager.saveCustomColorAccent(v) }
    fun setCustomColorInputField(v: Long?) = scope.launch { settingsManager.saveCustomColorInputField(v) }
    fun setIllustrationChatBackgroundPath(path: String) = scope.launch { settingsManager.saveIllustrationChatBackgroundPath(path) }
    fun setIllustrationInputBackgroundPath(path: String) = scope.launch { settingsManager.saveIllustrationInputBackgroundPath(path) }
    fun setIllustrationDrawerBackgroundPath(path: String) = scope.launch { settingsManager.saveIllustrationDrawerBackgroundPath(path) }
    fun setIllustrationUserBubbleBackgroundPath(path: String) = scope.launch { settingsManager.saveIllustrationUserBubbleBackgroundPath(path) }
    fun setIllustrationUserBubbleCornerRadius(radius: Float) = scope.launch { settingsManager.saveIllustrationUserBubbleCornerRadius(radius) }
    fun setIllustrationTopBarBackgroundPath(path: String) = scope.launch { settingsManager.saveIllustrationTopBarBackgroundPath(path) }
    fun setIllustrationReasoningBackgroundPath(path: String) = scope.launch { settingsManager.saveIllustrationReasoningBackgroundPath(path) }
    fun setTransparencyTopBar(v: Float) = scope.launch { settingsManager.saveTransparencyTopBar(v) }
    fun setTopBarCapsuleScale(v: Float) = scope.launch { settingsManager.saveTopBarCapsuleScale(v) }
    fun setTransparencyMessageBubble(v: Float) = scope.launch { settingsManager.saveTransparencyMessageBubble(v) }
    fun setTransparencyUserBubbleMask(v: Float) = scope.launch { settingsManager.saveTransparencyUserBubbleMask(v) }
    fun setTransparencyReasoningPanel(v: Float) = scope.launch { settingsManager.saveTransparencyReasoningPanel(v) }
    fun setTransparencyDrawerItem(v: Float) = scope.launch { settingsManager.saveTransparencyDrawerItem(v) }
    fun addRecentCustomColor(argb: Long) = scope.launch {
        val updated = (listOf(argb) + recentCustomColors.value.filter { it != argb }).take(20)
        settingsManager.saveRecentCustomColors(updated)
    }
    fun clearRecentCustomColors() = scope.launch { settingsManager.saveRecentCustomColors(emptyList()) }
    fun setHapticsEnabled(enabled: Boolean) = scope.launch { settingsManager.saveHapticsEnabled(enabled) }
    fun setToolCallDisplayMode(mode: String) = scope.launch { settingsManager.saveToolCallDisplayMode(mode) }
    fun setSchemeStyle(style: String) = scope.launch { settingsManager.saveSchemeStyle(style) }
    fun setFontPreference(value: String) = scope.launch { settingsManager.saveFontPreference(value) }
    fun setCustomFontPath(value: String) = scope.launch { settingsManager.saveCustomFontPath(value) }
    fun setCustomFontName(value: String) = scope.launch { settingsManager.saveCustomFontName(value) }
    fun setFontSizeTier(tier: String) = scope.launch { settingsManager.setFontSizeTier(tier) }
    fun setSearchMatchLimit(n: Int) = scope.launch { settingsManager.saveSearchMatchLimit(n) }
    fun setSearchContextWindow(n: Int) = scope.launch { settingsManager.saveSearchContextWindow(n) }
    fun setRagThreshold(threshold: Float) = scope.launch { settingsManager.saveRagThreshold(threshold) }

    // ── Health / Gadgetbridge / Sync ──────────────────────────
    fun setGadgetbridgeEnabled(enabled: Boolean) = scope.launch { settingsManager.saveGadgetbridgeEnabled(enabled) }
    fun setGadgetbridgeDbPath(path: String) = scope.launch { settingsManager.saveGadgetbridgeDbPath(path) }
    fun setHealthSyncEnabled(enabled: Boolean) = scope.launch { settingsManager.saveHealthSyncEnabled(enabled) }
    fun setHealthSyncSupabaseUrl(url: String) = scope.launch { settingsManager.saveHealthSyncSupabaseUrl(url) }
    fun setHealthSyncSupabaseApiKey(key: String) = scope.launch { settingsManager.saveHealthSyncSupabaseApiKey(key) }
    fun setHealthSyncTableName(name: String) = scope.launch { settingsManager.saveHealthSyncTableName(name) }
    fun setAutoApproveSensitiveTools(enabled: Boolean) = scope.launch { settingsManager.saveAutoApproveSensitiveTools(enabled) }

    fun setShellConfirmEnabled(enabled: Boolean) = scope.launch { settingsManager.saveShellConfirmEnabled(enabled) }
    fun addShellDevice(device: ShellDeviceConfig) = scope.launch { settingsManager.saveShellDevices(shellDevices.value + device) }
    fun updateShellDevice(device: ShellDeviceConfig) = scope.launch {
        settingsManager.saveShellDevices(shellDevices.value.map { if (it.id == device.id) device else it })
    }

    // ── Derived lookups ─────────────────────────────────────────
    /** Resolves the currently-active cleartext API key for [provider], or `null`. */
    fun resolveActiveKey(provider: String): String? =
        apiKeys.value.find { it.id == activeApiKeyIds.value[provider] }?.key

    /**
     * Like [resolveActiveKey] but awaits the on-disk DataStore values instead of
     * reading the eagerly-shared `.value`, which may still be the empty default
     * during the startup window before DataStore loads. Use this on the request-
     * build path: reading `.value` there races the load and yields a blank key →
     * an empty `Authorization` header → intermittent 401s on providers that are
     * considered configured by base-URL alone (custom / OpenAI-compatible / Ollama).
     */
    suspend fun awaitActiveKey(provider: String): String? {
        val activeIds = settingsManager.activeApiKeyIds.first()
        val keys = settingsManager.apiKeys.first()
        return keys.find { it.id == activeIds[provider] }?.key
    }

    // ── Suspending DataStore access ───────────────────────────
    //
    // The StateFlows above are eagerly-shared with a default initial value, so at app
    // startup `.value` may briefly be the default before DataStore loads. These suspend
    // accessors read/write DataStore directly (awaiting the on-disk value, preserving
    // write ordering) for callers that need the persisted value immediately or ordered,
    // read-after-write semantics. They keep [SettingsManager] encapsulated as an internal
    // detail of this repository — the single owner of the settings surface.

    suspend fun awaitRememberLastConversation(): Boolean = settingsManager.rememberLastConversation.first()
    suspend fun awaitLastActiveConversationId(): String? = settingsManager.lastActiveConversationId.first()

    suspend fun getAutoUpdateCheck(): Boolean = settingsManager.autoUpdateCheck.first()
    suspend fun getLastUpdateCheckTime(): Long = settingsManager.lastUpdateCheckTime.first()
    suspend fun getEmbeddingModels(): List<EmbeddingModelConfig> = settingsManager.embeddingModels.first()
    suspend fun getActiveEmbeddingModelId(): String = settingsManager.activeEmbeddingModelId.first()
    suspend fun getModelAliases(): Map<String, String> = settingsManager.modelAliases.first()
    suspend fun getProviderBaseUrls(): Map<String, String> = settingsManager.providerBaseUrls.first()
    suspend fun getAvailableModels(): Map<String, List<String>> = settingsManager.availableModels.first()
    suspend fun getSystemPrompts(): List<SystemPromptEntry> = settingsManager.systemPrompts.first()

    suspend fun saveAvailableModels(provider: String, models: List<String>) = settingsManager.saveAvailableModels(provider, models)
    suspend fun saveModelAliases(aliases: Map<String, String>) = settingsManager.saveModelAliases(aliases)
    suspend fun saveLastUpdateCheckTime(time: Long) = settingsManager.saveLastUpdateCheckTime(time)
    suspend fun saveLastModelsFetchFingerprint(fingerprint: String) = settingsManager.saveLastModelsFetchFingerprint(fingerprint)
    suspend fun incrementMessagesSent() = settingsManager.incrementMessagesSent()
    suspend fun saveLocalChatModels(models: List<LocalChatModelConfig>) = settingsManager.saveLocalChatModels(models)
    suspend fun saveEmbeddingModels(models: List<EmbeddingModelConfig>) = settingsManager.saveEmbeddingModels(models)
    suspend fun setActiveEmbeddingModelId(id: String) = settingsManager.setActiveEmbeddingModelId(id)
    suspend fun saveAutoBackupEnabled(enabled: Boolean) = settingsManager.saveAutoBackupEnabled(enabled)
    suspend fun saveAutoBackupPeriodHours(hours: Int) = settingsManager.saveAutoBackupPeriodHours(hours)
    suspend fun saveAutoBackupCategories(categories: String) = settingsManager.saveAutoBackupCategories(categories)
    suspend fun saveAutoBackupDirectory(path: String) = settingsManager.saveAutoBackupDirectory(path)
    suspend fun saveAutoDeleteEnabled(enabled: Boolean) = settingsManager.saveAutoDeleteEnabled(enabled)
    suspend fun saveAutoDeletePeriodHours(hours: Int) = settingsManager.saveAutoDeletePeriodHours(hours)

    // ── Plugin user identity ──────────────────────────────────
    // Identity is consumed by the sandbox (PluginSandbox) at tool-call time and by the
    // WebView bridge's bootstrap. The suspend getters let those callers await the persisted
    // value rather than the eagerly-shared `.value` (which is "" before DataStore loads).
    // ── Plugin device id ──────────────────────────────────────
    suspend fun getAppUserId(): String = settingsManager.appUserId.first()

    // ── Plugin configs ────────────────────────────────────────
    /** Returns the stored config map for [pluginId], or empty if the user hasn't filled it. */
    fun getPluginConfig(pluginId: String): Map<String, String> = pluginConfigs.value[pluginId] ?: emptyMap()
    suspend fun awaitPluginConfig(pluginId: String): Map<String, String> =
        settingsManager.pluginConfigs.first()[pluginId] ?: emptyMap()
    fun savePluginConfig(pluginId: String, values: Map<String, String>) =
        scope.launch { settingsManager.savePluginConfig(pluginId, values) }
    /** Suspending variant — callers that need the write committed before continuing (e.g.
     *  navigating to a plugin UI that reads the config) should use this, not [savePluginConfig]. */
    suspend fun savePluginConfigAwait(pluginId: String, values: Map<String, String>) =
        settingsManager.savePluginConfig(pluginId, values)
    /** Resolves the config for [pluginId] as a JSON string, ready to inject as a JS global. */
    suspend fun pluginConfigJson(pluginId: String): String {
        val cfg = awaitPluginConfig(pluginId)
        return kotlinx.serialization.json.Json.encodeToString(cfg)
    }

    // ── Text-to-Speech ──────────────────────────────────────────
    fun setTtsEnabled(enabled: Boolean) = scope.launch { settingsManager.saveTtsEnabled(enabled) }
    fun setTtsProvider(provider: String) = scope.launch { settingsManager.saveTtsProvider(provider) }
    fun setTtsApiKey(key: String) = scope.launch { settingsManager.saveTtsApiKey(key) }
    fun setTtsVoiceId(voiceId: String) = scope.launch { settingsManager.saveTtsVoiceId(voiceId) }
    fun setTtsModel(model: String) = scope.launch { settingsManager.saveTtsModel(model) }
    fun setTtsSpeed(speed: Float) = scope.launch { settingsManager.saveTtsSpeed(speed) }
    fun setTtsOutputFormat(format: String) = scope.launch { settingsManager.saveTtsOutputFormat(format) }
    fun setTtsStability(stability: Float) = scope.launch { settingsManager.saveTtsStability(stability) }
    fun setTtsSimilarityBoost(value: Float) = scope.launch { settingsManager.saveTtsSimilarityBoost(value) }
    fun setTtsStyle(style: Float) = scope.launch { settingsManager.saveTtsStyle(style) }
    fun setTtsVolume(volume: Float) = scope.launch { settingsManager.saveTtsVolume(volume) }
    fun setTtsPitch(pitch: Float) = scope.launch { settingsManager.saveTtsPitch(pitch) }

    // ── Speech-to-Text (SiliconFlow) ───────────────────────────
    fun setSttEnabled(enabled: Boolean) = scope.launch { settingsManager.saveSttEnabled(enabled) }
    fun setSttApiKey(key: String) = scope.launch { settingsManager.saveSttApiKey(key) }
    fun setSttModel(model: String) = scope.launch { settingsManager.saveSttModel(model) }
    fun setSttBaseUrl(baseUrl: String) = scope.launch { settingsManager.saveSttBaseUrl(baseUrl) }
}
