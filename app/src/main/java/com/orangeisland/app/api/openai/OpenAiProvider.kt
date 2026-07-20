package com.orangeisland.app.api.openai

import com.orangeisland.app.api.*
import com.orangeisland.app.model.ThinkingLevels
import com.orangeisland.app.util.Constants

class OpenAiProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_OPENAI
    override val defaultBaseUrl: String = "https://api.openai.com/v1"

    override fun customizeRequest(request: OpenAiChatRequest, config: ProviderConfig): OpenAiChatRequest {
        val isReasoningModel = config.modelId.startsWith("o1") ||
            config.modelId.startsWith("o3") ||
            config.modelId.startsWith("o4") ||
            config.modelId.startsWith("gpt-5")
        return if (config.thinkingEnabled && isReasoningModel) {
            val effort = ThinkingLevels.openAiEffort(config.thinkingLevel)
            request.copy(reasoningEffort = effort)
        } else request
    }
    // Reasoning/content parsing uses BaseOpenAiProvider's default (reasoning_content + content).
}
