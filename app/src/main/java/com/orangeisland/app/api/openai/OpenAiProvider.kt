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
            // gpt-5.6-sol (and potentially other gpt-5 models) do not support
            // reasoning_effort together with function tools on /v1/chat/completions.
            // When tools are present, omit reasoning_effort so the request succeeds.
            if (config.modelId.startsWith("gpt-5") && !request.tools.isNullOrEmpty()) {
                request
            } else {
                val effort = ThinkingLevels.openAiEffort(config.thinkingLevel)
                request.copy(reasoningEffort = effort)
            }
        } else request
    }
    // Reasoning/content parsing uses BaseOpenAiProvider's default (reasoning_content + content).
}
