package com.orangeisland.app.util

import android.content.Context
import com.orangeisland.app.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

object SearchResultFormatter {

    fun isRawSearchResult(text: String): Boolean = try {
        val type = Json.parseToJsonElement(text).jsonObject["type"]?.let { (it as? JsonPrimitive)?.content }
        type == "web_search" || type == "search_conversations" || type == "execute_shell_command" || type == "list_shells" || type == "list_memory_files" || type == "list_conversations" || type == "read_conversation"
    } catch (_: Exception) { false }

    fun format(text: String, context: Context): String {
        if (!isRawSearchResult(text)) return text
        return try {
            val json = Json.parseToJsonElement(text).jsonObject
            val error = json["error"]?.let { (it as? JsonPrimitive)?.content }
            if (error != null) return formatError(json, error, context)
            when (json["type"]?.let { (it as? JsonPrimitive)?.content }) {
                "web_search" -> formatWebSearch(json, context)
                "search_conversations" -> formatConversationSearch(json, context)
                "list_conversations" -> formatConversationList(json, context)
                "read_conversation" -> formatConversationRead(json, context)
                "list_memory_files" -> formatMemoryList(json)
                "list_shells" -> formatShellList(json, context)
                "execute_shell_command" -> formatShellCommand(json, context)
                else -> text
            }
        } catch (_: Exception) {
            text
        }
    }

    fun getFirstLine(text: String, context: Context): String {
        val formatted = format(text, context)
        return formatted.lines().first().take(100)
    }

    private fun formatError(json: JsonObject, error: String, context: Context): String {
        val query = json["query"]?.let { (it as? JsonPrimitive)?.content } ?: ""
        return when (error) {
            "no_query" -> context.getString(R.string.search_no_query)
            "no_results" -> context.getString(R.string.search_no_results)
            "no_response" -> context.getString(R.string.search_no_response)
            "no_api_key" -> context.getString(R.string.provider_no_keys, "Brave Search")
            "search_error" -> {
                val msg = json["message"]?.let { (it as? JsonPrimitive)?.content } ?: context.getString(R.string.unknown)
                context.getString(R.string.search_error_format, msg)
            }
            else -> context.getString(R.string.search_error_format, error)
        }
    }

    private fun formatWebSearch(json: JsonObject, context: Context): String {
        val query = json["query"]?.let { (it as? JsonPrimitive)?.content } ?: ""
        val results = json["results"]?.jsonArray ?: return context.getString(R.string.search_no_results)
        if (results.isEmpty()) return context.getString(R.string.search_no_results)

        val untitled = context.getString(R.string.search_untitled)
        val body = results.take(10).mapIndexed { i, element ->
            val obj = element.jsonObject
            val title = (obj["title"] as? JsonPrimitive)?.content ?: untitled
            val url = (obj["url"] as? JsonPrimitive)?.content ?: ""
            val desc = (obj["description"] as? JsonPrimitive)?.content ?: ""
            "${i + 1}. $title\n   $url\n   $desc"
        }.joinToString("\n\n")

        val total = results.size
        val prefix = if (query.isNotBlank())
            context.getString(R.string.search_found_results, total, query)
        else
            context.getString(R.string.search_found_results_no_query, total)
        return "$prefix\n\n$body"
    }

    private fun formatConversationSearch(json: JsonObject, context: Context): String {
        val query = json["query"]?.let { (it as? JsonPrimitive)?.content } ?: ""
        val results = json["results"]?.jsonArray ?: return context.getString(R.string.search_no_matches, query)
        if (results.isEmpty()) return context.getString(R.string.search_no_matches, query)

        val userRole = context.getString(R.string.search_role_user)
        val modelRole = context.getString(R.string.search_role_model)
        val untitled = context.getString(R.string.search_untitled)

        val body = results.take(5).joinToString("\n\n") { element ->
            val obj = element.jsonObject
            val title = (obj["title"] as? JsonPrimitive)?.content ?: untitled
            val messages = obj["messages"]?.jsonArray
            val previews = messages?.take(3)?.joinToString("\n") { msg ->
                val msgObj = msg.jsonObject
                val participant = (msgObj["participant"] as? JsonPrimitive)?.content ?: ""
                val role = if (participant == "USER") userRole else modelRole
                val text = (msgObj["text"] as? JsonPrimitive)?.content?.lines()?.first()?.take(200) ?: ""
                "$role: $text"
            } ?: ""
            "## $title\n$previews"
        }

        val total = results.sumOf { (it.jsonObject["messages"]?.jsonArray?.size ?: 0) }
        return context.getString(R.string.search_found_matches, total, query) + "\n\n$body"
    }

    private fun formatShellCommand(json: JsonObject, context: Context): String {
        val server = json["server"]?.let { (it as? JsonPrimitive)?.content } ?: ""
        val command = json["command"]?.let { (it as? JsonPrimitive)?.content } ?: ""
        val output = json["output"]?.let { (it as? JsonPrimitive)?.content } ?: ""
        val exitCode = json["exit_code"]?.let { (it as? JsonPrimitive)?.content }
        val error = json["error"]?.let { (it as? JsonPrimitive)?.content }
        val serverLine = if (server.isNotEmpty()) context.getString(R.string.shell_result_server, server) + "\n" else ""
        return if (error != null) {
            val msg = json["message"]?.let { (it as? JsonPrimitive)?.content } ?: ""
            "$serverLine${context.getString(R.string.shell_result_command, command)}\n${context.getString(R.string.shell_result_error, msg)}${if (output.isNotEmpty()) "\n\n$output" else ""}"
        } else {
            val code = exitCode?.toIntOrNull() ?: -1
            "$serverLine${context.getString(R.string.shell_result_command, command)}\n${context.getString(R.string.shell_result_exit_code, code)}\n\n$output"
        }
    }

    private fun formatMemoryList(json: JsonObject): String {
        val files = json["files"]?.jsonArray ?: return "No memory files."
        if (files.isEmpty()) return "No memory files."
        return files.joinToString("\n") { element ->
            val obj = element.jsonObject
            val name = (obj["name"] as? JsonPrimitive)?.content ?: ""
            val desc = (obj["description"] as? JsonPrimitive)?.content ?: ""
            if (desc.isNotEmpty()) "$name — $desc" else name
        }
    }

    private fun formatShellList(json: JsonObject, context: Context): String {
        val devices = json["devices"]?.jsonArray ?: return context.getString(R.string.shell_no_devices)
        if (devices.isEmpty()) return context.getString(R.string.shell_no_devices)
        val list = devices.joinToString("\n") { element ->
            val obj = element.jsonObject
            val name = (obj["name"] as? JsonPrimitive)?.content?.ifBlank { null } ?: "Untitled"
            val desc = (obj["description"] as? JsonPrimitive)?.content ?: ""
            if (desc.isNotEmpty()) "$name — $desc" else name
        }
        return list
    }

    private fun formatConversationList(json: JsonObject, context: Context): String {
        val total = (json["total"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val offset = (json["offset"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val limit = (json["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val hasMore = (json["has_more"] as? JsonPrimitive)?.content?.toBoolean() ?: false
        val conversations = json["conversations"]?.jsonArray
        if (conversations == null || conversations.isEmpty()) {
            return context.getString(R.string.conversation_list_empty)
        }

        val header = context.getString(R.string.conversation_list_header, total, offset + 1, offset + conversations.size)
        val body = conversations.joinToString("\n") { element ->
            val obj = element.jsonObject
            val id = (obj["id"] as? JsonPrimitive)?.content?.takeLast(8) ?: "?"
            val title = (obj["title"] as? JsonPrimitive)?.content ?: context.getString(R.string.search_untitled)
            val dateStr = (obj["timestamp"] as? JsonPrimitive)?.content ?: ""
            if (dateStr.isNotEmpty()) "- [$id] $title ($dateStr)" else "- [$id] $title"
        }
        val footer = if (hasMore) context.getString(R.string.conversation_list_more) else ""
        return "$header\n\n$body${if (footer.isNotEmpty()) "\n\n$footer" else ""}"
    }

    private fun formatConversationRead(json: JsonObject, context: Context): String {
        val title = (json["title"] as? JsonPrimitive)?.content ?: context.getString(R.string.search_untitled)
        val totalMessages = (json["total_messages"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val offset = (json["offset"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val hasMore = (json["has_more"] as? JsonPrimitive)?.content?.toBoolean() ?: false
        val messages = json["messages"]?.jsonArray
        val userRole = context.getString(R.string.search_role_user)
        val modelRole = context.getString(R.string.search_role_model)

        val header = context.getString(R.string.conversation_read_header, title, totalMessages)
        val body = if (messages != null && messages.isNotEmpty()) {
            messages.joinToString("\n\n") { element ->
                val obj = element.jsonObject
                val participant = (obj["participant"] as? JsonPrimitive)?.content ?: ""
                val role = if (participant == "USER") userRole else modelRole
                val text = (obj["text"] as? JsonPrimitive)?.content ?: ""
                "$role: $text"
            }
        } else {
            context.getString(R.string.conversation_read_empty)
        }
        val pageInfo = context.getString(R.string.conversation_read_page, offset + 1, offset + (messages?.size ?: 0))
        val footer = if (hasMore) context.getString(R.string.conversation_read_more) else ""
        return "$header\n$pageInfo\n\n$body${if (footer.isNotEmpty()) "\n\n$footer" else ""}"
    }
}
