package com.orangeisland.app.data

object BuiltInPrompts {
    const val TITLE_GENERATION_SYSTEM =
        "You are a title generator. Output only a short title in the same language as the conversation."

    const val IMAGE_TRANSCRIPTION_SYSTEM =
        "You are an image describer. Describe the given image in detail."

    const val IMAGE_TRANSCRIPTION_USER =
        "Please describe this image in detail. Include all visible text, data, charts, layout, and visual elements. Preserve the original language of any text shown."
}
