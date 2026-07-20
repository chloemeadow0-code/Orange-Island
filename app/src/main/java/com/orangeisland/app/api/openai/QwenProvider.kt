package com.orangeisland.app.api.openai

import com.orangeisland.app.util.Constants

class QwenProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_QWEN
    override val defaultBaseUrl: String = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
    // Reasoning/content parsing uses BaseOpenAiProvider's default (reasoning_content + content).
}
