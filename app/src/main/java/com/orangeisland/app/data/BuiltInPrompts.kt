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

    /** Instructs the transcription model to answer with one delimited section
     *  per image when multiple images are sent in a single batched request. */
    fun imageTranscriptionBatchInstruction(count: Int): String {
        val markers = (1..count).joinToString(" ") { "@@IMAGE_$it@@" }
        return "There are $count images attached, in order. For EACH image, output " +
            "its own section starting with the exact marker on its own line (no other " +
            "text on that line), followed by the description for that image only. " +
            "Use the markers in order: $markers. Do not add any text before the first " +
            "marker or after the last section."
    }

    /** Splits a batched transcription response back into per-image texts using the
     *  @@IMAGE_N@@ markers. Returns null if the marker count doesn't match
     *  [expectedCount] or any section is blank — callers should fall back to
     *  per-image requests rather than guessing or dropping data. */
    fun parseImageTranscriptionBatch(raw: String, expectedCount: Int): List<String>? {
        val regex = Regex("""@@IMAGE_(\d+)@@""")
        val matches = regex.findAll(raw).toList()
        if (matches.size != expectedCount) return null
        val result = MutableList(expectedCount) { "" }
        for (i in matches.indices) {
            val idx = (matches[i].groupValues[1].toIntOrNull() ?: return null) - 1
            if (idx !in 0 until expectedCount) return null
            val start = matches[i].range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else raw.length
            result[idx] = raw.substring(start, end).trim()
        }
        if (result.any { it.isBlank() }) return null
        return result
    }
}
