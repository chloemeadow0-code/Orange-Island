package com.orangeisland.app.tool

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class MemoryToolProvider(
    private val memoryManager: MemoryManager
) : ToolProvider {

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.accessSavedMemories && !ctx.accessActiveMemory) return emptyList()
        val tools = mutableListOf<ToolDefinition>()
        if (ctx.accessSavedMemories) {
            tools.addAll(
                listOf(
                    ToolDefinition(
                        function = ToolFunction(
                            name = "list_memory_files",
                            description = "List all files in the memory database with their names and descriptions. When working inside a project, this also includes the project's private files (tagged scope=\"project\"); global files are tagged scope=\"global\".",
                            parameters = ToolParameters(properties = emptyMap())
                        )
                    ),
                    ToolDefinition(
                        function = ToolFunction(
                            name = "read_memory_file",
                            description = "Read the content of one or more files from the memory database.",
                            parameters = ToolParameters(
                                properties = mapOf(
                                    "name" to ToolProperty("string", "The file name to read."),
                                    "names" to ToolProperty(
                                        "array",
                                        "Multiple file names to read in one call.",
                                        items = ToolProperty("string", "A file name.")
                                    )
                                ),
                                required = emptyList()
                            )
                        )
                    ),
                    ToolDefinition(
                        function = ToolFunction(
                            name = "create_memory_file",
                            description = "Create a new file in the memory database with the given content and optional description. When working inside a project, new files are stored in that project's private memory (invisible outside the project); otherwise they go to the global store.",
                            parameters = ToolParameters(
                                properties = mapOf(
                                    "name" to ToolProperty("string", "The file name to create (e.g., 'notes.md')."),
                                    "content" to ToolProperty("string", "The markdown content for the file."),
                                    "description" to ToolProperty(
                                        "string",
                                        "A short description of what this file contains (optional)."
                                    )
                                ),
                                required = listOf("name", "content")
                            )
                        )
                    ),
                    ToolDefinition(
                        function = ToolFunction(
                            name = "edit_memory_file",
                            description = "Edit, rename, or update the description of a file in the memory database. Use 'old_string' + 'new_string' for precise string replacement — the old_string must match exactly once in the file. Use 'content' for full rewrites (mutually exclusive with old_string). At least one of 'content', 'old_string', 'new_name', or 'description' must be provided.",
                            parameters = ToolParameters(
                                properties = mapOf(
                                    "name" to ToolProperty("string", "The current file name to edit."),
                                    "content" to ToolProperty(
                                        "string",
                                        "The new markdown content (full rewrite). Omit to keep existing content. Mutually exclusive with 'old_string'."
                                    ),
                                    "old_string" to ToolProperty(
                                        "string",
                                        "Exact string to find and replace. Must match exactly once in the file. Mutually exclusive with 'content'."
                                    ),
                                    "new_string" to ToolProperty(
                                        "string",
                                        "Replacement string for old_string. Pass empty string to delete the matched text. Required when old_string is provided."
                                    ),
                                    "new_name" to ToolProperty("string", "New file name to rename to. Omit to keep existing name."),
                                    "description" to ToolProperty(
                                        "string",
                                        "A short description of the file contents. Omit to keep existing description. Pass empty string to remove."
                                    )
                                ),
                                required = listOf("name")
                            )
                        )
                    ),
                    ToolDefinition(
                        function = ToolFunction(
                            name = "delete_memory_file",
                            description = "Delete a file from the memory database.",
                            parameters = ToolParameters(
                                properties = mapOf("name" to ToolProperty("string", "The file name to delete.")),
                                required = listOf("name")
                            )
                        )
                    )
                )
            )
        }
        if (ctx.accessActiveMemory) {
            tools.add(
                ToolDefinition(
                    function = ToolFunction(
                        name = "update_active_memory",
                        description = "Update the active memory context. Modes: 'replace' (overwrite with 'content'), 'append' (add 'content' to end), 'prepend' (add 'content' to beginning), 'patch' (find 'old_string' exactly once and replace with 'new_string'). Default is replace.",
                        parameters = ToolParameters(
                            properties = mapOf(
                                "content" to ToolProperty("string", "The content to write (for replace/append/prepend modes)."),
                                "mode" to ToolProperty(
                                    "string",
                                    "One of: replace, append, prepend, patch. Default is replace."
                                ),
                                "old_string" to ToolProperty(
                                    "string",
                                    "Exact string to find and replace in the active memory. Required for patch mode. Must match exactly once."
                                ),
                                "new_string" to ToolProperty(
                                    "string",
                                    "Replacement string for old_string in patch mode. Pass empty string to delete the matched text."
                                )
                            ),
                            required = listOf("content")
                        )
                    )
                )
            )
        }
        return tools
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        val argsStr = arguments.ifBlank { "{}" }
        val args =
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        fun arg(key: String): String =
            (args[key] as? JsonPrimitive)?.content ?: ""

        return when (name) {
            "list_memory_files" -> {
                // When in a project, expose both the global memory store AND the project's
                // private files — tagged with their origin so the model knows where each lives.
                val files = memoryManager.listFilesMerged(ctx.projectId)
                if (files.isEmpty()) {
                    buildJsonObject {
                        put("type", "list_memory_files")
                        putJsonArray("files") {}
                    }.toString()
                } else {
                    buildJsonObject {
                        put("type", "list_memory_files")
                        put("scope", if (ctx.projectId != null) "project" else "global")
                        putJsonArray("files") {
                            files.forEach { f ->
                                add(
                                    buildJsonObject {
                                        put("name", f.name)
                                        put("description", f.description)
                                        // Only surface the scope tag when projects are in play;
                                        // in global-only mode every file is global, so omit the
                                        // field to keep responses compact.
                                        if (ctx.projectId != null) {
                                            put("scope", if (f.projectId == null) "global" else "project")
                                        }
                                    }
                                )
                            }
                        }
                    }.toString()
                }
            }

            "read_memory_file" -> {
                val singleName = arg("name")
                val namesArray = args["names"] as? JsonArray
                // Helper: try the project dir first, fall back to global. This lets the model
                // read a file by name without knowing which scope it lives in.
                fun readScoped(fileName: String): String =
                    try {
                        if (ctx.projectId != null) memoryManager.readFile(fileName, ctx.projectId)
                        else memoryManager.readFile(fileName, null)
                    } catch (_: IllegalArgumentException) {
                        memoryManager.readFile(fileName, null)
                    }
                if (namesArray != null && namesArray.isNotEmpty()) {
                    val names = namesArray.map {
                        (it as? JsonPrimitive)?.content ?: ""
                    }.filter { it.isNotEmpty() }
                    names.joinToString("\n\n") { nm -> "--- $nm ---\n${readScoped(nm)}" }
                } else if (singleName.isNotEmpty()) {
                    readScoped(singleName)
                } else {
                    "Error: No file name provided. Use 'name' for a single file or 'names' for multiple files."
                }
            }

            "create_memory_file" -> memoryManager.createFile(
                arg("name"),
                arg("content"),
                arg("description"),
                // New files land in the project's private store when scoped to a project;
                // otherwise they go to the global store (existing behavior).
                ctx.projectId
            )

            "edit_memory_file" -> {
                val editContent = arg("content").ifBlank { null }
                val oldStr = arg("old_string").ifBlank { null }
                val newStr = arg("new_string")
                val newName = arg("new_name").ifBlank { null }
                val descArg = arg("description")
                val desc = if (args.containsKey("description")) descArg else null
                // Resolve the file's actual scope before editing: a project-scoped call may
                // target a file that only exists globally, so look in both places.
                val editFileName = arg("name")
                val targetScope: String? = when {
                    ctx.projectId != null && runCatching { memoryManager.readFile(editFileName, ctx.projectId) }.isSuccess -> ctx.projectId
                    runCatching { memoryManager.readFile(editFileName, null) }.isSuccess -> null
                    else -> return "Error: File not found: $editFileName"
                }
                if (editContent != null && oldStr != null) {
                    "Error: 'content' and 'old_string' are mutually exclusive. Use one or the other."
                } else if (oldStr != null && !args.containsKey("new_string")) {
                    "Error: 'old_string' requires 'new_string' (pass empty string to delete)."
                } else if (editContent == null && oldStr == null && newName == null && desc == null) {
                    "Error: At least 'content', 'old_string', 'new_name', or 'description' must be provided."
                } else {
                    memoryManager.editFile(
                        editFileName,
                        editContent,
                        newName,
                        desc,
                        oldStr,
                        newStr,
                        targetScope
                    )
                }
            }

            "delete_memory_file" -> {
                // Same scope-resolution as edit: find which store actually holds the file.
                val fileName = arg("name")
                val targetScope = when {
                    ctx.projectId != null && runCatching { memoryManager.readFile(fileName, ctx.projectId) }.isSuccess -> ctx.projectId
                    runCatching { memoryManager.readFile(fileName, null) }.isSuccess -> null
                    else -> return "Error: File not found: $fileName"
                }
                memoryManager.deleteFile(fileName, targetScope)
            }

            "update_active_memory" -> {
                // Active memory stays global by design — it's the always-on context injected
                // into every request regardless of project. Projects isolate the *file* store.
                val mode = arg("mode").ifBlank { "replace" }
                val oldStr = arg("old_string").ifBlank { null }
                val newStr = arg("new_string").ifBlank { null }
                if (mode == "patch" && oldStr == null) {
                    "Error: 'old_string' is required for patch mode."
                } else {
                    memoryManager.updateActiveMemory(arg("content"), mode, oldStr, newStr)
                }
            }

            else -> "Unknown tool: $name"
        }
    }

    override fun handles(name: String): Boolean = name in setOf(
        "list_memory_files",
        "read_memory_file",
        "create_memory_file",
        "edit_memory_file",
        "delete_memory_file",
        "update_active_memory"
    )
}
