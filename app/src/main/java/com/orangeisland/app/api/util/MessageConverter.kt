package com.orangeisland.app.api.util

import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.api.OpenAiContentPart
import com.orangeisland.app.api.OpenAiImageUrl
import com.orangeisland.app.api.OpenAiMessage
import com.orangeisland.app.api.OpenAiRequestFunction
import com.orangeisland.app.api.OpenAiRequestToolCall
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import java.io.File
import java.security.MessageDigest

fun buildToolCallId(toolName: String, arguments: String, prefix: String = Constants.TOOL_CALL_ID_PREFIX): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = "$toolName:$arguments"
    val hash = digest.digest(input.toByteArray())
    val shortHash = hash.take(8).joinToString("") { "%02x".format(it) }
    return "$prefix${toolName}_$shortHash"
}

fun encodeImageToBase64(imagePath: String): Pair<String, String>? {
    return try {
        val file = File(imagePath)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val mimeType = if (imagePath.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
        mimeType to base64
    } catch (e: Exception) {
        DebugLog.e("OrangeIslandAPI", "Failed to encode image: $imagePath", e)
        null
    }
}

fun convertToOpenAiMessages(
    messages: List<ChatMessage>,
    systemPrompt: String? = null,
    includeImages: Boolean = true
): List<OpenAiMessage> {
    val apiMessages = mutableListOf<OpenAiMessage>()

    if (!systemPrompt.isNullOrBlank()) {
        apiMessages.add(
            OpenAiMessage(
                role = "system",
                content = listOf(OpenAiContentPart(type = "text", text = systemPrompt))
            )
        )
    }

    apiMessages.addAll(messages.flatMap { msg ->
        val entries = mutableListOf<OpenAiMessage>()

        // tool_ messages: assistant turn with tool_calls only
        // (tool results come from the following result_ messages)
        if (msg.id.startsWith(Constants.TOOL_MSG_PREFIX)) {
            val toolSegs = msg.segments?.filter { it.type == "tool" }
            val thoughtContent = msg.segments?.lastOrNull { it.type == "thought" }?.content
            if (!toolSegs.isNullOrEmpty()) {
                val toolCalls = toolSegs.map { seg ->
                    val tid = seg.toolCallId ?: buildToolCallId(seg.toolName ?: "", seg.toolArgs ?: "{}")
                    OpenAiRequestToolCall(
                        id = tid,
                        function = OpenAiRequestFunction(name = seg.toolName ?: "", arguments = seg.toolArgs ?: "{}")
                    )
                }
                entries.add(OpenAiMessage(
                    role = "assistant",
                    content = listOf(OpenAiContentPart(type = "text", text = " ")),
                    toolCalls = toolCalls,
                    reasoningContent = thoughtContent?.ifEmpty { null }
                ))
            } else if (msg.toolCall != null) {
                val tc = msg.toolCall!!
                val toolId = tc.toolCallId ?: buildToolCallId(tc.toolName, tc.arguments)
                entries.add(OpenAiMessage(
                    role = "assistant",
                    content = listOf(OpenAiContentPart(type = "text", text = " ")),
                    toolCalls = listOf(OpenAiRequestToolCall(
                        id = toolId,
                        function = OpenAiRequestFunction(name = tc.toolName, arguments = tc.arguments)
                    )),
                    reasoningContent = thoughtContent?.ifEmpty { null }
                ))
            }
            return@flatMap entries
        }

        // result_ messages carry the tool result(s)
        if (msg.id.startsWith(Constants.RESULT_MSG_PREFIX)) {
            val toolSegs = msg.segments?.filter { it.type == "tool" }
            if (!toolSegs.isNullOrEmpty()) {
                for (seg in toolSegs) {
                    val toolId = seg.toolCallId ?: buildToolCallId(seg.toolName ?: "", seg.toolArgs ?: "{}")
                    entries.add(OpenAiMessage(
                        role = "tool",
                        content = listOf(OpenAiContentPart(type = "text", text = seg.toolResult ?: "")),
                        toolCallId = toolId
                    ))
                }
            } else if (msg.toolCall != null) {
                val tc = msg.toolCall!!
                val toolId = tc.toolCallId ?: buildToolCallId(tc.toolName, tc.arguments)
                entries.add(OpenAiMessage(
                    role = "tool",
                    content = listOf(OpenAiContentPart(type = "text", text = tc.result)),
                    toolCallId = toolId
                ))
            }
            return@flatMap entries
        }

        // Normal message: text + images
        val parts = mutableListOf<OpenAiContentPart>()
        if (msg.text.isNotEmpty()) {
            parts.add(OpenAiContentPart(type = "text", text = msg.text))
        }

        if (includeImages && msg.participant == Participant.USER) {
            for (imagePath in msg.images) {
                val encoded = encodeImageToBase64(imagePath)
                if (encoded != null) {
                    val (mimeType, base64) = encoded
                    parts.add(
                        OpenAiContentPart(
                            type = "image_url",
                            imageUrl = OpenAiImageUrl(url = "data:$mimeType;base64,$base64")
                        )
                    )
                }
            }
        }

        if (parts.isEmpty()) {
            parts.add(OpenAiContentPart(type = "text", text = " "))
        }

        entries.add(OpenAiMessage(
            role = if (msg.participant == Participant.USER) "user" else "assistant",
            content = parts
        ))
        entries
    })

    return apiMessages
}

fun limitContext(messages: List<ChatMessage>, maxUserMessages: Int): List<ChatMessage> {
    val result = mutableListOf<ChatMessage>()
    var userCount = 0
    for (msg in messages.reversed()) {
        result.add(0, msg)
        val isTool = msg.id.startsWith(Constants.TOOL_MSG_PREFIX) || msg.id.startsWith(Constants.RESULT_MSG_PREFIX)
        if (!isTool) userCount++
        if (userCount >= maxUserMessages) break
    }
    return result
}
