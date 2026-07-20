package com.orangeisland.app.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orangeisland.app.R
import com.orangeisland.app.model.MessageSegment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Composable
internal fun toolDisplayName(toolName: String?): String {
    return when (toolName) {
        "list_memory_files" -> stringResource(R.string.tool_look_up_memories)
        "read_memory_file" -> stringResource(R.string.tool_read_memory)
        "create_memory_file" -> stringResource(R.string.tool_add_memory)
        "edit_memory_file" -> stringResource(R.string.tool_edit_memory)
        "delete_memory_file" -> stringResource(R.string.tool_delete_memory)
        "update_active_memory" -> stringResource(R.string.tool_update_active_memory)
        "web_search" -> stringResource(R.string.tool_web_search)
        "web_fetch" -> stringResource(R.string.tool_web_fetch)
        "search_conversations" -> stringResource(R.string.tool_search_conversations)
        "list_shells" -> stringResource(R.string.tool_list_shells)
        "execute_shell_command" -> stringResource(R.string.tool_execute_shell)
        "list_conversations" -> stringResource(R.string.tool_list_conversations)
        "read_conversation" -> stringResource(R.string.tool_read_conversation)
        "file_read" -> stringResource(R.string.tool_file_read)
        "file_write" -> stringResource(R.string.tool_file_write)
        "file_edit" -> stringResource(R.string.tool_file_edit)
        "file_glob" -> stringResource(R.string.tool_file_glob)
        "file_grep" -> stringResource(R.string.tool_file_grep)
        "generate_image" -> stringResource(R.string.tool_generate_image)
        else -> (toolName ?: stringResource(R.string.tool_context)).split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
}

@Composable
internal fun toolSummary(seg: MessageSegment): String {
    val name = seg.toolName ?: ""
    val argsJson = try { Json.parseToJsonElement(seg.toolArgs ?: "{}").jsonObject } catch (_: Exception) { null }
    val fileName = argsJson?.get("name")?.let { (it as? JsonPrimitive)?.content }
        ?: argsJson?.get("names")?.let { names ->
            val arr = names as? kotlinx.serialization.json.JsonArray
            if (arr != null && arr.size == 1) (arr[0] as? JsonPrimitive)?.content else null
        }
    val nameCount = argsJson?.get("names")?.let { (it as? kotlinx.serialization.json.JsonArray)?.size }
    val content = seg.toolResult ?: ""
    val isError = content.startsWith("Error")
    return when (name) {
        "read_memory_file" -> when {
            isError -> stringResource(R.string.tool_read_memory_failed)
            content.isBlank() && fileName != null -> stringResource(R.string.tool_reading_memory, fileName)
            nameCount != null && nameCount > 1 -> stringResource(R.string.tool_read_memory_count, nameCount)
            fileName != null -> stringResource(R.string.tool_read_memory_name, fileName)
            else -> stringResource(R.string.tool_read_memory_success)
        }
        "create_memory_file" -> when {
            isError -> stringResource(R.string.tool_save_memory_failed)
            content.isBlank() && fileName != null -> stringResource(R.string.tool_saving_memory, fileName)
            fileName != null -> stringResource(R.string.tool_save_memory_name, fileName)
            else -> stringResource(R.string.tool_save_memory_default)
        }
        "edit_memory_file" -> when {
            isError -> stringResource(R.string.tool_edit_memory_failed)
            content.isBlank() && fileName != null -> stringResource(R.string.tool_updating_memory, fileName)
            fileName != null -> stringResource(R.string.tool_edit_memory_name, fileName)
            else -> stringResource(R.string.tool_edit_memory_default)
        }
        "delete_memory_file" -> when {
            isError -> stringResource(R.string.tool_delete_memory_failed)
            content.isBlank() && fileName != null -> stringResource(R.string.tool_removing_memory, fileName)
            fileName != null -> stringResource(R.string.tool_delete_memory_name, fileName)
            else -> stringResource(R.string.tool_delete_memory_default)
        }
        "list_memory_files" -> {
            if (isError) stringResource(R.string.tool_lookup_failed)
            else if (content.isBlank()) stringResource(R.string.tool_looking_up_memories)
            else {
                val fileCount = try {
                    Json.parseToJsonElement(content).jsonObject["files"]?.jsonArray?.size ?: 0
                } catch (_: Exception) { 0 }
                if (fileCount > 0) stringResource(R.string.tool_lookup_count, fileCount)
                else stringResource(R.string.tool_lookup_default)
            }
        }
        "update_active_memory" -> {
            if (isError) stringResource(R.string.tool_update_active_failed)
            else if (content.isBlank()) stringResource(R.string.tool_updating_active)
            else stringResource(R.string.tool_update_active_default)
        }
        "web_search" -> {
            val query = argsJson?.get("query")?.let { (it as? JsonPrimitive)?.content }
            if (isError) {
                if (query != null) stringResource(R.string.tool_web_search_error, query)
                else stringResource(R.string.tool_search_failed)
            } else if (content.isBlank()) {
                if (query != null) stringResource(R.string.tool_searching_web, query)
                else stringResource(R.string.tool_web_search_done_default)
            } else {
                val resultCount = try {
                    Json.parseToJsonElement(content).jsonObject["results"]?.jsonArray?.size ?: 0
                } catch (_: Exception) { 0 }
                if (resultCount > 0 && query != null) stringResource(R.string.tool_web_search_done, resultCount, query)
                else if (query != null) stringResource(R.string.tool_web_search_no_result, query)
                else stringResource(R.string.tool_web_search_done_default)
            }
        }
        "web_fetch" -> {
            val url = argsJson?.get("url")?.let { (it as? JsonPrimitive)?.content }
            if (isError) stringResource(R.string.tool_web_fetch_failed)
            else if (content.isEmpty()) stringResource(R.string.tool_web_fetching, url?.take(60)?.ifEmpty { "page" } ?: "web page")
            else if (url != null) stringResource(R.string.tool_web_fetch_done, url.take(60).ifEmpty { "page" })
            else stringResource(R.string.tool_web_fetch_default)
        }
        "search_conversations" -> {
            val query = argsJson?.get("query")?.let { (it as? JsonPrimitive)?.content }
            if (isError) {
                if (query != null) stringResource(R.string.tool_conversation_search_error, query)
                else stringResource(R.string.tool_search_failed)
            } else if (content.isBlank()) {
                if (query != null) stringResource(R.string.tool_searching_for, query)
                else stringResource(R.string.tool_searching_conversations_default)
            } else {
                val convCount = try {
                    Json.parseToJsonElement(content).jsonObject["results"]?.jsonArray?.size ?: 0
                } catch (_: Exception) { 0 }
                if (convCount > 0 && query != null) stringResource(R.string.tool_conversation_search_done_for, convCount, query)
                else if (query != null) stringResource(R.string.tool_conversation_search_no_result, query)
                else stringResource(R.string.tool_searching_conversations_default)
            }
        }
        "list_shells" -> {
            if (isError) stringResource(R.string.tool_shell_listing)
            else if (content.isBlank()) stringResource(R.string.tool_listing_shells)
            else {
                val deviceCount = try {
                    Json.parseToJsonElement(content).jsonObject["devices"]?.jsonArray?.size ?: 0
                } catch (_: Exception) { 0 }
                if (deviceCount > 0) stringResource(R.string.tool_shell_list_count, deviceCount)
                else stringResource(R.string.tool_shell_list_done)
            }
        }
        "execute_shell_command" -> {
            val command = argsJson?.get("command")?.let { (it as? JsonPrimitive)?.content }
            if (isError) stringResource(R.string.tool_shell_failed)
            else if (content.isBlank()) {
                if (command != null) stringResource(R.string.tool_executing_shell, command.take(80))
                else stringResource(R.string.tool_listing_shells)
            } else {
                if (command != null) stringResource(R.string.tool_executed_shell, command.take(80), content.length)
                else stringResource(R.string.tool_executed_no_output, command?.take(80) ?: "shell")
            }
        }
        "list_conversations" -> {
            if (isError) stringResource(R.string.tool_call_failed)
            else if (content.isBlank()) stringResource(R.string.tool_listing_conversations)
            else {
                val total = try {
                    (Json.parseToJsonElement(content).jsonObject["total"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                } catch (_: Exception) { 0 }
                if (total > 0) stringResource(R.string.tool_listed_conversations, total)
                else stringResource(R.string.tool_listed_no_conversations)
            }
        }
        "read_conversation" -> {
            if (isError) stringResource(R.string.tool_call_failed)
            else if (content.isBlank()) stringResource(R.string.tool_reading_conversation)
            else {
                val title = try {
                    (Json.parseToJsonElement(content).jsonObject["title"] as? JsonPrimitive)?.content
                } catch (_: Exception) { null }
                if (title != null) stringResource(R.string.tool_read_conversation_done, title.take(60))
                else stringResource(R.string.tool_read_conversation)
            }
        }
        "file_read" -> {
            val path = argsJson?.get("path")?.let { (it as? JsonPrimitive)?.content }
            if (isError) stringResource(R.string.tool_read_file_failed)
            else if (content.isBlank()) stringResource(R.string.tool_reading_file, path?.take(60) ?: "file")
            else stringResource(R.string.tool_read_file_done, path?.take(60) ?: "file")
        }
        "file_write" -> {
            val path = argsJson?.get("path")?.let { (it as? JsonPrimitive)?.content }
            if (isError) stringResource(R.string.tool_call_failed)
            else if (content.isBlank()) stringResource(R.string.tool_writing_file, path?.take(60) ?: "file")
            else stringResource(R.string.tool_wrote_file, path?.take(60) ?: "file")
        }
        "file_edit" -> {
            val path = argsJson?.get("path")?.let { (it as? JsonPrimitive)?.content }
            if (isError) stringResource(R.string.tool_call_failed)
            else if (content.isBlank()) stringResource(R.string.tool_editing_file, path?.take(60) ?: "file")
            else stringResource(R.string.tool_edited_file, path?.take(60) ?: "file")
        }
        "file_glob" -> {
            val pattern = argsJson?.get("pattern")?.let { (it as? JsonPrimitive)?.content }
            if (isError) stringResource(R.string.tool_call_failed)
            else if (content.isBlank()) stringResource(R.string.tool_finding_files, pattern?.take(60) ?: "files")
            else {
                val count = content.lines().filter { it.isNotBlank() }.size
                if (count > 0) stringResource(R.string.tool_found_files, count)
                else stringResource(R.string.tool_found_no_files)
            }
        }
        "file_grep" -> {
            val pattern = argsJson?.get("pattern")?.let { (it as? JsonPrimitive)?.content }
            if (isError) stringResource(R.string.tool_call_failed)
            else if (content.isBlank()) stringResource(R.string.tool_searching_file, pattern?.take(60) ?: "file")
            else {
                val matches = content.lines().filter { it.isNotBlank() }.size
                if (matches > 0) stringResource(R.string.tool_searched_file, matches)
                else stringResource(R.string.tool_found_no_files)
            }
        }
        "generate_image" -> when {
            isError -> stringResource(R.string.tool_call_failed)
            content.isEmpty() -> stringResource(R.string.tool_generating_image)
            else -> stringResource(R.string.tool_generated_image)
        }
        else -> {
            if (isError) stringResource(R.string.tool_call_failed)
            else stringResource(R.string.tool_done)
        }
    }
}
