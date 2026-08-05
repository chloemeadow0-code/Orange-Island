package com.orangeisland.app.data

object BuiltInPrompts {
    const val TITLE_GENERATION_SYSTEM =
        "You are a title generator. Output only a short title in the same language as the conversation."

    const val IMAGE_TRANSCRIPTION_SYSTEM =
        "You are an image describer. Describe the given image in detail."

    const val IMAGE_TRANSCRIPTION_USER =
        "Please describe this image in detail. Include all visible text, data, charts, layout, and visual elements. Preserve the original language of any text shown."

    const val VIDEO_NARRATION_SYSTEM =
        "You are a video understanding assistant. Describe the provided video in detail, organized by time."

    const val VIDEO_NARRATION_USER =
        "Please narrate the video in detail. Describe the people, actions, expressions, clothing, environment, camera changes, and any visible text or subtitles in chronological order. Attach approximate timestamps to important events. Do not invent information that is not present or uncertain in the video. Note that audio/dialogue may not be reliably transcribed."

    const val HISTORY_COMPRESSION_SYSTEM =
        "You summarize conversation history. Produce a concise summary capturing key facts, decisions, and context needed to continue the conversation. Preserve names, entities, and any unresolved questions. Output only the summary."
}
