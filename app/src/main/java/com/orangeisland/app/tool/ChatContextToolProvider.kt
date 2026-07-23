package com.orangeisland.app.tool

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Chat-context tool provider — exposes conversation-state queries so workflows and the AI
 * can reason about chat history (time since last message, message counts, etc.).
 */
class ChatContextToolProvider(private val chatDao: ChatDao) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunction(
            name = "time_since_last_chat",
            description = "Return how much time has passed since the last message in any conversation. " +
                "Returns seconds elapsed and a human-readable string (e.g. \"5 minutes ago\"). " +
                "Returns null if there are no messages yet.",
            parameters = ToolParameters(properties = emptyMap(), required = emptyList())
        )),
        ToolDefinition(function = ToolFunction(
            name = "count_conversation_messages",
            description = "Count the total number of messages in a specific conversation.",
            parameters = ToolParameters(
                properties = mapOf(
                    "conversation_id" to ToolProperty("string", "The conversation ID to count messages in.")
                ),
                required = listOf("conversation_id")
            )
        ))
    )

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        return try {
            when (name) {
                "time_since_last_chat" -> timeSinceLastChat()
                "count_conversation_messages" -> countConversationMessages(arguments)
                else -> unknownTool(name)
            }
        } catch (e: Exception) {
            error("execution_error", e.message ?: "Unknown error")
        }
    }

    override fun handles(name: String): Boolean = name in setOf(
        "time_since_last_chat", "count_conversation_messages"
    )

    // ── Implementation ─────────────────────────────────────

    private suspend fun timeSinceLastChat(): String {
        val lastMs = chatDao.getLatestMessageTimestamp()
        return if (lastMs == null) {
            buildJsonObject {
                put("success", JsonPrimitive(true))
                put("has_messages", JsonPrimitive(false))
                put("seconds", JsonPrimitive(0))
                put("readable", JsonPrimitive("无消息"))
            }.toString()
        } else {
            val seconds = (System.currentTimeMillis() - lastMs) / 1000
            buildJsonObject {
                put("success", JsonPrimitive(true))
                put("has_messages", JsonPrimitive(true))
                put("seconds", JsonPrimitive(seconds))
                put("readable", JsonPrimitive(formatDuration(seconds)))
            }.toString()
        }
    }

    private suspend fun countConversationMessages(arguments: String): String {
        val args = json.parseToJsonElement(arguments).jsonObject
        val conversationId = args["conversation_id"]?.toString()?.trim('"')
            ?: return error("missing_argument", "conversation_id is required")
        val count = chatDao.countMessagesInConversation(conversationId)
        return buildJsonObject {
            put("success", JsonPrimitive(true))
            put("conversation_id", JsonPrimitive(conversationId))
            put("count", JsonPrimitive(count))
        }.toString()
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds < 60 -> "${seconds}秒前"
        seconds < 3600 -> "${seconds / 60}分钟前"
        seconds < 86400 -> "${seconds / 3600}小时前"
        else -> "${seconds / 86400}天前"
    }

    private fun error(type: String, message: String): String =
        buildJsonObject {
            put("success", JsonPrimitive(false))
            put("error_type", JsonPrimitive(type))
            put("message", JsonPrimitive(message))
        }.toString()

    private fun unknownTool(name: String): String =
        error("unknown_tool", "ChatContextToolProvider does not handle tool: $name")
}
