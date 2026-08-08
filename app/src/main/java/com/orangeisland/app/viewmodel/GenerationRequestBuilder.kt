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
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
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

    private fun resolveVideoNarrationProviderName(): String =
        settings.videoNarrationModel.value?.let { providerRegistry.providerForModel(it) } ?: ""

    private fun resolveVideoNarrationModelId(): String =
        settings.videoNarrationModel.value?.let { ModelId.parse(it).modelName } ?: ""

    private fun resolveVideoNarrationApiKey(): String {
        val model = settings.videoNarrationModel.value ?: return ""
        val providerName = providerRegistry.providerForModel(model)
        if (providerName == Constants.PROVIDER_LOCAL) return ""
        return settings.resolveActiveKey(providerName) ?: ""
    }

    private fun resolveVideoNarrationBaseUrl(): String? {
        val model = settings.videoNarrationModel.value ?: return null
        return providerRegistry.getEffectiveBaseUrl(providerRegistry.providerForModel(model))
    }

    private fun isVideoNarrationEnabled(effectiveSettings: ConversationSettings): Boolean {
        val activeModel = currentActiveModel.value
        val globalEnabled = settings.videoNarrationEnabledModels.value.contains(activeModel)
        val perConv = effectiveSettings.videoNarrationEnabled
        val result = perConv ?: globalEnabled
        DebugLog.d(
            "VideoNarration",
            "isVideoNarrationEnabled: activeModel='$activeModel' " +
                "enabledModelsList=${settings.videoNarrationEnabledModels.value} " +
                "globalEnabled=$globalEnabled perConvOverride=$perConv -> result=$result"
        )
        return result
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
            presencePenalty = effectiveSettings.presencePenalty,
            stream = !settings.nonStreamOutputEnabled.value,
            customHeaders = settings.providerCustomHeaders.value[providerName] ?: emptyMap()
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
            // The make_voice_call tool only makes sense when the call loop can actually run, which
            // needs BOTH a TTS voice and an STT (SiliconFlow) key configured.
            voiceCallEnabled = settings.sttEnabled.value && settings.sttApiKey.value.isNotBlank() &&
                settings.ttsEnabled.value && settings.ttsApiKey.value.isNotBlank(),
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
            videoNarrationEnabled = isVideoNarrationEnabled(effectiveSettings),
            videoNarrationModel = settings.videoNarrationModel.value,
            videoNarrationPrompt = settings.videoNarrationPrompt.value,
            videoNarrationFps = settings.videoNarrationFps.value,
            videoNarrationDetail = settings.videoNarrationDetail.value,
            videoNarrationMaxLongSide = settings.videoNarrationMaxLongSide.value,
            videoNarrationProviderName = resolveVideoNarrationProviderName(),
            videoNarrationModelId = resolveVideoNarrationModelId(),
            videoNarrationApiKey = resolveVideoNarrationApiKey(),
            videoNarrationBaseUrl = resolveVideoNarrationBaseUrl(),
            mcpServers = settings.mcpServers.value,
            mcpServerIds = effectiveSettings.mcpServerIds,
            pluginIds = effectiveSettings.pluginIds,
            // Device Access tools — straight pass-through (no per-conversation override yet).
            deviceInfoEnabled = settings.deviceInfoEnabled.value,
            locationEnabled = settings.locationEnabled.value,
            amapApiKey = settings.amapApiKey.value,
            calendarEnabled = settings.calendarEnabled.value,
            notificationEnabled = settings.notificationEnabled.value,
            mediaControlEnabled = settings.mediaControlEnabled.value,
            usageStatsEnabled = settings.usageStatsEnabled.value,
            navigationEnabled = settings.navigationEnabled.value,
            appLockEnabled = settings.appLockEnabled.value,
            toastEnabled = settings.toastEnabled.value,
            alarmEnabled = settings.alarmEnabled.value,
            healthEnabled = settings.healthToolEnabled.value,
            healthDbPath = settings.gadgetbridgeDbPath.value,
            timeToolEnabled = settings.timeToolEnabled.value,
            uiAutomationEnabled = settings.uiAutomationEnabled.value,
            userInteractionEnabled = settings.userInteractionEnabled.value,
            cameraToolEnabled = settings.cameraToolEnabled.value,
            musicStudioEnabled = settings.musicStudioEnabled.value,
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
        val appContextSnapshot = appContextCollector?.getSnapshot() ?: ""
        val conversationGapLine = buildConversationGapLine(currentId, now.time)
        val combinedAppContext = if (conversationGapLine.isNotBlank() && appContextSnapshot.isNotBlank()) {
            "$conversationGapLine\n$appContextSnapshot"
        } else {
            conversationGapLine + appContextSnapshot
        }

        val runtimeValues = mapOf(
            PredefinedVariables.TIME to sdf.format(now),
            PredefinedVariables.DATE to dateSdf.format(now),
            PredefinedVariables.SENT_TIME to sdf.format(now),
            PredefinedVariables.SENT_DATE to dateSdf.format(now),
            PredefinedVariables.MODEL_ID to modelId,
            PredefinedVariables.ACTIVE_MEMORY to if (includeActiveMemory && activeMemory.isNotBlank()) activeMemory else "",
            PredefinedVariables.APP_CONTEXT to combinedAppContext
        )

        val projectId = conversation?.projectId
        val includeSavedMemories = settings.accessSavedMemories.value
        val projectMemoryBlock = if (includeSavedMemories && projectId != null) {
            buildProjectMemoryBlock(projectId)
        } else null
        // Suffix only — never prepended, so it never touches the stable cacheable prefix of the
        // compiled system prompt above.
        val anniversarySuffix = buildAnniversarySuffix()
        val stickySuffix = buildStickyNoteSuffix()

        if (entry != null) {
            val systemItems = entry.resolvedSystemItems
            // Prepend/postpend: {sent_time}/{sent_date} stay as placeholders resolved per-message in applyUserTemplate
            val perMsgValues = runtimeValues.filterKeys { it !in PredefinedVariables.PER_MESSAGE_VARS }
            val compiled = PredefinedVariables.compile(systemItems, runtimeValues).ifBlank { null }
            val withMemory = if (projectMemoryBlock != null && compiled != null) {
                "$compiled\n\n$projectMemoryBlock"
            } else if (projectMemoryBlock != null) {
                projectMemoryBlock
            } else {
                compiled
            }
            val withAnniversaries = if (anniversarySuffix != null && withMemory != null) {
                "$withMemory\n\n$anniversarySuffix"
            } else if (anniversarySuffix != null) {
                anniversarySuffix
            } else {
                withMemory
            }
            val withSticky = if (stickySuffix != null) {
                if (withAnniversaries != null) "$withAnniversaries\n\n$stickySuffix" else stickySuffix
            } else withAnniversaries
            return ResolvedPrompt(
                systemPrompt = withSticky,
                userPrepend = PredefinedVariables.compile(entry.userPrependItems, perMsgValues, emptyMap()).ifBlank { null },
                userPostpend = PredefinedVariables.compile(entry.userPostpendItems, perMsgValues, emptyMap()).ifBlank { null },
                projectId = projectId,
                systemPromptId = targetPromptId
            )
        }

        val fallbackWithAnniversaries = if (anniversarySuffix != null && projectMemoryBlock != null) {
            "$projectMemoryBlock\n\n$anniversarySuffix"
        } else anniversarySuffix ?: projectMemoryBlock

        val fallbackWithSticky = if (stickySuffix != null) {
            if (fallbackWithAnniversaries != null) "$fallbackWithAnniversaries\n\n$stickySuffix" else stickySuffix
        } else fallbackWithAnniversaries

        return ResolvedPrompt(
            systemPrompt = fallbackWithSticky,
            userPrepend = null,
            userPostpend = null,
            projectId = projectId,
            systemPromptId = targetPromptId
        )
    }

    /**
     * Builds a short suffix block listing anniversaries due soon (within 14 days, includes
     * today and just-passed one-time dates within the last day) so the model can naturally
     * bring one up without the user asking. Always appended at the TAIL of the system prompt —
     * never the front — so it doesn't disturb the stable cacheable prefix. Returns null when
     * there are no anniversaries saved or none are near.
     */
    private fun buildAnniversarySuffix(): String? {
        val all = settings.anniversaries.value
        if (all.isEmpty()) return null
        val today = java.time.LocalDate.now()
        val near = all
            .map { it to com.orangeisland.app.data.AnniversaryUtils.daysUntilNext(it, today) }
            .filter { (_, days) -> days in -1..14 }
            .sortedBy { (_, days) -> days }
        if (near.isEmpty()) return null
        val lines = near.joinToString("\n") { (e, days) ->
            val when_ = when {
                days == 0L -> "就是今天"
                days == 1L -> "明天"
                days > 0L -> "还有${days}天"
                else -> "刚过去"
            }
            val yearNote = if (e.recurring) "（第${com.orangeisland.app.data.AnniversaryUtils.yearsSince(e, today)}年）" else ""
            "- ${e.name}：${com.orangeisland.app.data.AnniversaryUtils.formatDate(e)}$yearNote，$when_"
        }
        return "## 近期纪念日\n\n$lines\n\n（如果合适，可以自然地提一句，不用刻意生硬地念出来。）"
    }

    /**
     * 便签能力提示。即使用户当前没有便签，也告诉 AI 这项能力存在，这样用户说"写个便签"
     * 时 AI 会主动调用 create_sticky_note。有便签时附上当前条数。
     */
    private fun buildStickyNoteSuffix(): String {
        val count = settings.stickyNotes.value.size
        val possession = if (count > 0) "用户当前有 $count 条便签。" else "用户还没有便签。"
        return "## 桌面便签\n\n" +
            "$possession 你可以用 create_sticky_note 写便签（会显示在桌面便签小组件上，" +
            "每次开屏随机展示一条，最多保留 50 条），用 list_sticky_notes 查看，" +
            "update_sticky_note 修改，delete_sticky_note 删除。" +
            "（用户提到\"记一下/便签/贴个便条/留句话\"等意图时主动用，不用等用户明确说工具名。）"
    }

    private fun formatConversationGap(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "不到1分钟"
            seconds < 3600 -> "${seconds / 60}分钟"
            seconds < 86400 -> {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                if (minutes == 0L) "${hours}小时" else "${hours}小时${minutes}分钟"
            }
            else -> {
                val days = seconds / 86400
                "${days}天"
            }
        }
    }

    private suspend fun buildConversationGapLine(conversationId: String, nowMillis: Long): String {
        return try {
            val messages = convRepo.getMessagesForConversationSnapshot(conversationId)
            val userMessages = messages
                .filter { it.participant == Participant.USER }
                .sortedBy { it.timestamp }
            if (userMessages.isEmpty()) {
                return "距用户上次发消息已过了：这是对话中的第一条消息"
            }
            val lastUserMsg = userMessages.last()
            val isCurrentMessage = (nowMillis - lastUserMsg.timestamp) < 5000L
            val targetMsg = if (isCurrentMessage && userMessages.size >= 2) {
                userMessages[userMessages.size - 2]
            } else {
                lastUserMsg
            }
            val gap = nowMillis - targetMsg.timestamp
            val gapText = if (gap < 1000L) "不到1分钟" else formatConversationGap(gap)
            "距用户上次发消息已过了：$gapText"
        } catch (e: Exception) {
            DebugLog.e("GenerationRequestBuilder", "buildConversationGapLine failed", e)
            ""
        }
    }

    /**
     * Reads project-scoped long-term memory files (global + project-private merged) and
     * formats them into a block suitable for appending to the system prompt. Returns null
     * when there are no files, the feature is disabled, or reading fails.
     */
    private suspend fun buildProjectMemoryBlock(projectId: String): String? {
        return try {
            val files = memoryManager.listFilesMerged(projectId).filter { it.name.isNotBlank() }
            if (files.isEmpty()) return null
            val parts = mutableListOf<String>()
            for (info in files) {
                val content = runCatching {
                    memoryManager.readFile(info.name, info.projectId)
                }.getOrElse {
                    DebugLog.w("GenerationRequestBuilder", "Failed to read memory ${info.name}: ${it.message}")
                    null
                }
                if (!content.isNullOrBlank()) {
                    parts.add("### ${info.name}\n${content.trim()}")
                }
            }
            if (parts.isEmpty()) return null
            "## 项目长期记忆\n\n" + parts.joinToString("\n\n")
        } catch (e: Exception) {
            DebugLog.e("GenerationRequestBuilder", "buildProjectMemoryBlock failed", e)
            null
        }
    }
}
