package com.orangeisland.app.viewmodel

import android.content.Context
import com.orangeisland.app.R
import com.orangeisland.app.data.ConversationSettings
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.PredefinedVariables
import com.orangeisland.app.data.environment.AppContextCollector
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.model.ModelId
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.util.Constants
import kotlinx.coroutines.flow.StateFlow

/**
 * Stateless builder for the LLM generation request. Extracted from ChatViewModel.
 * Reads configuration singletons only; holds NO mutable UI state.
 */
class GenerationRequestBuilder(
    private val settings: SettingsRepository,
    private val convRepo: ConversationRepository,
    private val memoryManager: MemoryManager,
    private val providerRegistry: ProviderRegistry,
    private val ragManager: RagManager,
    private val appContext: Context,
    // currentActiveModel 是一个 StateFlow,buildGenerationPair / buildEffectiveSystemPrompt 用到它的 .value
    private val currentActiveModel: StateFlow<String>,
    // _pendingConversationSettings 也是 StateFlow,buildEffectiveConversationSettings 读它的 .value
    private val pendingConversationSettings: StateFlow<ConversationSettings?>,
    // resolveProviderKey 需要 emit snackbar
    private val onSnackbar: (String) -> Unit,
    private val appContextCollector: AppContextCollector? = null,
) {
    data class ProviderKey(val providerName: String, val apiKey: String)

    /** Resolves the active provider+key for [modelId] and verifies configuration.
     *  Emits a snackbar and returns null when the provider is not configured. */
    internal fun resolveProviderKey(modelId: String): ProviderKey? {
        val providerName = providerRegistry.providerForModel(modelId)
        val activeKey = settings.resolveActiveKey(providerName) ?: ""
        if (!providerRegistry.isConfigured(providerName, activeKey)) {
            onSnackbar(appContext.getString(R.string.no_api_key_for_provider, providerName))
            return null
        }
        return ProviderKey(providerName, activeKey)
    }

    private fun resolveTranscriptionProviderName(): String =
        settings.imageTranscriptionModel.value?.let { providerRegistry.providerForModel(it) } ?: ""

    private fun resolveTranscriptionModelId(): String =
        settings.imageTranscriptionModel.value?.let { ModelId.parse(it).modelName } ?: ""

    private fun resolveTranscriptionApiKey(): String {
        val model = settings.imageTranscriptionModel.value ?: return ""
        val providerName = providerRegistry.providerForModel(model)
        if (providerName == Constants.PROVIDER_LOCAL) return ""
        return settings.resolveActiveKey(providerName) ?: ""
    }

    private fun resolveTranscriptionBaseUrl(): String? {
        val model = settings.imageTranscriptionModel.value ?: return null
        return providerRegistry.getEffectiveBaseUrl(providerRegistry.providerForModel(model))
    }

    // Image generation reuses the selected model's provider credentials (mirrors transcription).
    private fun resolveImageGenModelId(): String =
        settings.imageGenModel.value?.let { ModelId.parse(it).apiModelName } ?: ""

    private fun resolveImageGenApiKey(): String {
        val model = settings.imageGenModel.value ?: return ""
        val providerName = providerRegistry.providerForModel(model)
        if (providerName == Constants.PROVIDER_LOCAL) return ""
        return settings.resolveActiveKey(providerName) ?: ""
    }

    private fun resolveImageGenBaseUrl(): String {
        val model = settings.imageGenModel.value ?: return ""
        return providerRegistry.getEffectiveBaseUrl(providerRegistry.providerForModel(model)) ?: ""
    }

    fun buildEffectiveConversationSettings(conversationId: String): ConversationSettings {
        val overrides = settings.conversationSettings.value[conversationId]
            ?: pendingConversationSettings.value  // new chat: may not be saved to map yet
            ?: ConversationSettings()
        return ConversationSettings(
            contextWindow = overrides.contextWindow ?: settings.maxContextWindow.value,
            temperature = overrides.temperature ?: settings.defaultTemperature.value,
            maxTokens = overrides.maxTokens ?: settings.defaultMaxTokens.value,
            topP = overrides.topP ?: settings.defaultTopP.value,
            frequencyPenalty = overrides.frequencyPenalty ?: settings.defaultFrequencyPenalty.value,
            presencePenalty = overrides.presencePenalty ?: settings.defaultPresencePenalty.value,
            codeExecutionEnabled = overrides.codeExecutionEnabled ?: settings.codeExecutionEnabled.value,
            googleSearchEnabled = overrides.googleSearchEnabled ?: settings.googleSearchEnabled.value,
            thinkingEnabled = overrides.thinkingEnabled ?: settings.thinkingEnabled.value,
            thinkingLevel = overrides.thinkingLevel ?: settings.thinkingLevel.value,
            thinkingBudgetEnabled = overrides.thinkingBudgetEnabled ?: settings.thinkingBudgetEnabled.value,
            thinkingBudgetTokens = overrides.thinkingBudgetTokens ?: settings.thinkingBudgetTokens.value,
            webSearchEnabled = if (settings.webSearchEnabled.value) (overrides.webSearchEnabled ?: true) else false,
            shellEnabled = if (settings.shellEnabled.value) (overrides.shellEnabled ?: true) else false,
            mcpServerIds = overrides.mcpServerIds,
            pluginIds = overrides.pluginIds
        )
    }

    internal fun buildGenerationPair(
        providerName: String,
        modelId: String,
        activeKey: String,
        resolvedSystemPrompt: String?,
        resolvedUserPrepend: String?,
        resolvedUserPostpend: String?,
        effectiveSettings: ConversationSettings,
        currentId: String,
        projectId: String? = null,
        systemPromptId: String? = null
    ): Pair<GenerationConfig, GenerationContext> {
        val config = GenerationConfig(
            providerName = providerName,
            modelId = ModelId.parse(modelId).modelName,
            apiKey = activeKey,
            effectiveSystemPrompt = resolvedSystemPrompt,
            maxContextWindow = effectiveSettings.contextWindow ?: settings.maxContextWindow.value,
            codeExecutionEnabled = effectiveSettings.codeExecutionEnabled ?: settings.codeExecutionEnabled.value,
            googleSearchEnabled = effectiveSettings.googleSearchEnabled ?: settings.googleSearchEnabled.value,
            thinkingEnabled = effectiveSettings.thinkingEnabled ?: settings.thinkingEnabled.value,
            thinkingLevel = effectiveSettings.thinkingLevel ?: settings.thinkingLevel.value,
            thinkingBudgetEnabled = effectiveSettings.thinkingBudgetEnabled ?: settings.thinkingBudgetEnabled.value,
            thinkingBudgetTokens = effectiveSettings.thinkingBudgetTokens ?: settings.thinkingBudgetTokens.value,
            baseUrl = providerRegistry.getEffectiveBaseUrl(providerName),
            userPrepend = resolvedUserPrepend,
            userPostpend = resolvedUserPostpend,
            temperature = effectiveSettings.temperature,
            maxTokens = effectiveSettings.maxTokens,
            topP = effectiveSettings.topP,
            frequencyPenalty = effectiveSettings.frequencyPenalty,
            presencePenalty = effectiveSettings.presencePenalty
        )
        val genCtx = GenerationContext(
            conversationId = currentId,
            accessSavedMemories = settings.accessSavedMemories.value,
            accessActiveMemory = settings.accessActiveMemory.value,
            accessPastConversations = settings.accessPastConversations.value,
            modelSearchMethod = settings.modelSearchMethod.value,
            activeEmbeddingConfig = ragManager.activeEmbeddingModel.value,
            embeddingApiKey = ragManager.resolveEmbeddingApiKey() ?: "",
            ragThreshold = settings.ragThreshold.value,
            searchMatchLimit = settings.searchMatchLimit.value,
            searchContextWindow = settings.searchContextWindow.value,
            webSearchEnabled = effectiveSettings.webSearchEnabled ?: settings.webSearchEnabled.value,
            webSearchApiKeys = settings.webSearchApiKeys.value,
            webSearchProvider = settings.webSearchProvider.value,
            webSearchNumResults = settings.webSearchNumResults.value,
            webSearchBaseUrl = settings.webSearchBaseUrl.value,
            imageGenEnabled = settings.imageGenEnabled.value && settings.imageGenModel.value?.contains(":") == true,
            imageGenApiKey = resolveImageGenApiKey(),
            imageGenBaseUrl = resolveImageGenBaseUrl(),
            imageGenModel = resolveImageGenModelId(),
            imageGenSize = settings.imageGenSize.value,
            shellEnabled = effectiveSettings.shellEnabled ?: settings.shellEnabled.value,
            shellDevices = settings.shellDevices.value,
            sandboxEnabled = settings.sandboxEnabled.value,
            ttsEnabled = settings.ttsEnabled.value && settings.ttsApiKey.value.isNotBlank(),
            ttsProvider = settings.ttsProvider.value,
            ttsApiKey = settings.ttsApiKey.value,
            ttsVoiceId = settings.ttsVoiceId.value,
            ttsModel = settings.ttsModel.value,
            ttsSpeed = settings.ttsSpeed.value,
            ttsOutputFormat = settings.ttsOutputFormat.value,
            ttsStability = settings.ttsStability.value,
            ttsSimilarityBoost = settings.ttsSimilarityBoost.value,
            ttsStyle = settings.ttsStyle.value,
            ttsVolume = settings.ttsVolume.value,
            ttsPitch = settings.ttsPitch.value,
            imageTranscriptionEnabled = settings.imageTranscriptionEnabledModels.value.contains(currentActiveModel.value),
            imageTranscriptionModel = settings.imageTranscriptionModel.value,
            imageTranscriptionBatchSize = settings.imageTranscriptionBatchSize.value,
            imageTranscriptionPrompt = settings.imageTranscriptionPrompt.value,
            transcriptionProviderName = resolveTranscriptionProviderName(),
            transcriptionModelId = resolveTranscriptionModelId(),
            transcriptionApiKey = resolveTranscriptionApiKey(),
            transcriptionBaseUrl = resolveTranscriptionBaseUrl(),
            mcpServers = settings.mcpServers.value,
            mcpServerIds = effectiveSettings.mcpServerIds,
            pluginIds = effectiveSettings.pluginIds,
            // Device Access tools — straight pass-through (no per-conversation override yet).
            deviceInfoEnabled = settings.deviceInfoEnabled.value,
            locationEnabled = settings.locationEnabled.value,
            amapApiKey = settings.amapApiKey.value,
            calendarEnabled = settings.calendarEnabled.value,
            notificationEnabled = settings.notificationEnabled.value,
            usageStatsEnabled = settings.usageStatsEnabled.value,
            navigationEnabled = settings.navigationEnabled.value,
            appLockEnabled = settings.appLockEnabled.value,
            toastEnabled = settings.toastEnabled.value,
            uiAutomationEnabled = settings.uiAutomationEnabled.value,
            userInteractionEnabled = settings.userInteractionEnabled.value,
            // Memory + RAG scope: a conversation inside a project only sees that project's
            // private memory store (plus the global one), and searches stay within it.
            projectId = projectId,
            modelId = modelId,
            systemPromptId = systemPromptId
        )
        return Pair(config, genCtx)
    }

    data class ResolvedPrompt(
        val systemPrompt: String?,
        val userPrepend: String?,
        val userPostpend: String?,
        // Carries the conversation's project so buildGenerationPair (a non-suspend fun) can
        // populate GenerationContext.projectId without doing its own DB lookup.
        val projectId: String? = null,
        val systemPromptId: String? = null
    )

    internal suspend fun buildEffectiveSystemPrompt(currentId: String): ResolvedPrompt {
        val conversation = convRepo.getConversation(currentId)
        val targetPromptId = conversation?.systemPromptId ?: settings.activeSystemPromptId.value
        val entry = settings.systemPrompts.value.find { it.id == targetPromptId }
        val activeMemory = memoryManager.getActiveMemory()
        val includeActiveMemory = settings.accessActiveMemory.value
        val modelId = ModelId.parse(currentActiveModel.value).modelName

        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        val dateSdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val now = java.util.Date()

        val runtimeValues = mapOf(
            PredefinedVariables.TIME to sdf.format(now),
            PredefinedVariables.DATE to dateSdf.format(now),
            PredefinedVariables.SENT_TIME to sdf.format(now),
            PredefinedVariables.SENT_DATE to dateSdf.format(now),
            PredefinedVariables.MODEL_ID to modelId,
            PredefinedVariables.ACTIVE_MEMORY to if (includeActiveMemory && activeMemory.isNotBlank()) activeMemory else "",
            PredefinedVariables.APP_CONTEXT to (appContextCollector?.getSnapshot() ?: "")
        )

        if (entry != null) {
            val systemItems = entry.resolvedSystemItems
            // Prepend/postpend: {sent_time}/{sent_date} stay as placeholders resolved per-message in applyUserTemplate
            val perMsgValues = runtimeValues.filterKeys { it !in PredefinedVariables.PER_MESSAGE_VARS }
            return ResolvedPrompt(
                systemPrompt = PredefinedVariables.compile(systemItems, runtimeValues).ifBlank { null },
                userPrepend = PredefinedVariables.compile(entry.userPrependItems, perMsgValues, emptyMap()).ifBlank { null },
                userPostpend = PredefinedVariables.compile(entry.userPostpendItems, perMsgValues, emptyMap()).ifBlank { null },
                projectId = conversation?.projectId,
                systemPromptId = targetPromptId
            )
        }

        return ResolvedPrompt(null, null, null, conversation?.projectId, targetPromptId)
    }
}
