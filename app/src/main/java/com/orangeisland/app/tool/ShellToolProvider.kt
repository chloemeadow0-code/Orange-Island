package com.orangeisland.app.tool

import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.ShellDeviceConfig
import com.orangeisland.app.sandbox.SandboxManagerFactory
import com.orangeisland.app.util.ShellClient
import com.orangeisland.app.util.SshClient
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ShellToolProvider(
    private val sandboxFactory: SandboxManagerFactory? = null
) : ToolProvider {

    private val sandbox = sandboxFactory?.create()

    /**
     * Optional user-confirmation gate for state-changing operations on REMOTE
     * servers (SSH/Conch). Returns true to proceed, false to deny. The local
     * sandbox is proot-isolated and is never gated. Set by the owning ViewModel;
     * null = no gate (proceed).
     */
    var confirm: (suspend (server: String, summary: String) -> Boolean)? = null

    /** Run [confirm] for remote devices only; sandbox (device == null) always proceeds. */
    private suspend fun confirmRemote(device: ShellDeviceConfig?, summary: String): Boolean {
        if (device == null) return true
        return confirm?.invoke(device.name.ifBlank { "${device.type} server" }, summary) ?: true
    }

    // ── Helpers ────────────────────────────────────────────

    private fun parseToolArgs(arguments: String): Map<String, JsonElement> {
        return try {
            val argsStr = arguments.ifBlank { "{}" }
            Json.decodeFromString<Map<String, JsonElement>>(argsStr)
        } catch (_: Exception) { emptyMap() }
    }

    private fun jsonError(type: String, message: String, server: String? = null, command: String? = null): String {
        return buildJsonObject {
            if (type.isNotBlank()) put("type", type)
            put("error", "error"); put("message", message)
            if (server != null) put("server", server)
            if (command != null) put("command", command)
        }.toString()
    }

    private fun arg(args: Map<String, JsonElement>, key: String): String {
        return (args[key] as? JsonPrimitive)?.content ?: ""
    }

    private fun resolveShellDevice(serverName: String, ctx: GenerationContext): ShellDeviceConfig? {
        if (serverName.equals("Local Sandbox", ignoreCase = true)) return null
        return if (serverName.isNotBlank()) {
            ctx.shellDevices.find { it.name.equals(serverName, ignoreCase = true) }
        } else if (ctx.shellDevices.size == 1) {
            ctx.shellDevices.first()
        } else null
    }

    private fun serverNotFoundMessage(serverName: String, ctx: GenerationContext): String {
        val hasSandbox = ctx.sandboxEnabled && sandboxFactory?.isAvailable() == true
        val allNames = buildList {
            if (hasSandbox) add("\"Local Sandbox\"")
            addAll(ctx.shellDevices.map { "\"${it.name}\"" })
        }
        return if (allNames.size == 1) {
            "Unknown server: $serverName. Use ${allNames[0]} or omit the server parameter."
        } else {
            val names = allNames.joinToString(", ")
            if (serverName.isBlank()) "Multiple servers available. Use list_shells to see them, then specify one: $names."
            else "Unknown server: $serverName. Available: $names."
        }
    }

    // ── Backend sealed interface ───────────────────────────

    private sealed interface Backend {
        suspend fun executeCommand(cmd: String, workdir: String, timeoutMs: Int): String
        suspend fun fileRead(path: String, offset: Long, limit: Long): String
        suspend fun fileWrite(path: String, content: String): String?
        suspend fun fileGlob(pattern: String, basePath: String, depth: Int?): Result<List<String>>
        suspend fun fileGrep(pattern: String, basePath: String, fileGlob: String): Result<List<ShellClient.GrepMatch>>
        fun close()
    }

    private inner class ConchBackend(device: ShellDeviceConfig) : Backend {
        private val url = device.serverUrl.trimEnd('/')
        private val apiKey = device.apiKey
        private val pubKey = device.conchPublicKey
        private val deviceName = device.name

        private val client: ShellClient by lazy { ShellClient(url, apiKey, pubKey) }

        override suspend fun executeCommand(cmd: String, workdir: String, timeoutMs: Int): String {
            if (url.isBlank()) return jsonError("execute_shell_command", "Server \"$deviceName\" has no URL configured.")
            if (!client.fetchPublicKey() && apiKey.isNotBlank()) {
                return jsonError("execute_shell_command", "encryption_failed", server = deviceName)
            }
            val prepared = client.prepareRequest(cmd, timeoutMs, workdir)
            val handle = HttpClient.streamPost("${prepared.serverUrl}/execute", prepared.body, prepared.headers)
            return try {
                val output = StringBuilder()
                var exitCode: Int? = null
                var errorMessage: String? = null
                var currentEvent: String? = null
                val aesKey = client.getSessionKey()
                while (true) {
                    val line = handle.readLine() ?: break
                    when {
                        line.startsWith("event: ") -> currentEvent = line.substring(7).trim()
                        line.startsWith("data: ") -> {
                            var dataStr = line.substring(6).trim()
                            if (aesKey != null) {
                                try { dataStr = client.decryptSseData(dataStr) } catch (_: Exception) { continue }
                            }
                            val dataJson = try { Json.parseToJsonElement(dataStr).jsonObject } catch (_: Exception) { null } ?: continue
                            when (currentEvent) {
                                "line" -> {
                                    val text = (dataJson["line"] as? JsonPrimitive)?.content
                                    if (text != null) output.append(text).append('\n')
                                }
                                "result" -> exitCode = (dataJson["exit_code"] as? JsonPrimitive)?.content?.toIntOrNull()
                                "error" -> errorMessage = (dataJson["message"] as? JsonPrimitive)?.content
                            }
                        }
                    }
                }
                buildJsonObject {
                    put("type", "execute_shell_command"); put("server", deviceName); put("command", cmd)
                    if (errorMessage != null) { put("error", "execution_error"); put("message", errorMessage) }
                    else { put("exit_code", exitCode ?: -1) }
                    put("output", output.toString().trimEnd())
                }.toString()
            } catch (e: Exception) {
                jsonError("execute_shell_command", e.message ?: "Unknown error", server = deviceName, command = cmd)
            } finally { handle.close() }
        }

        override suspend fun fileRead(path: String, offset: Long, limit: Long): String {
            val result = client.fileRead(path, offset, limit)
            if (result.error != null) return jsonError("file_read", result.error, server = deviceName)
            return buildJsonObject {
                put("type", "file_read"); put("server", deviceName); put("path", path)
                put("content", result.content); put("lines", result.lines)
            }.toString()
        }

        override suspend fun fileWrite(path: String, content: String): String? =
            client.fileWrite(path, content)?.let { jsonError("file_write", it, server = deviceName) }

        override suspend fun fileGlob(pattern: String, basePath: String, depth: Int?): Result<List<String>> =
            client.fileGlob(pattern, basePath, depth)

        override suspend fun fileGrep(pattern: String, basePath: String, fileGlob: String): Result<List<ShellClient.GrepMatch>> =
            client.fileGrep(pattern, basePath, fileGlob)

        override fun close() {}
    }

    private inner class SshBackend(device: ShellDeviceConfig) : Backend {
        private val host = device.sshHost
        private val port = device.sshPort
        private val user = device.sshUser
        private val password = device.sshPassword
        private val timeout = device.timeout
        private val deviceName = device.name
        private val hostKey = device.sshHostKey

        private val client: SshClient by lazy {
            SshClient(
                host, port, user, password, timeout * 1000,
                pinnedHostKey = hostKey,
                // Un-pinned devices stay usable (capture-only); once a key is pinned it is enforced.
                allowUnknownHostKey = hostKey.isBlank()
            )
        }

        override suspend fun executeCommand(cmd: String, workdir: String, timeoutMs: Int): String {
            if (host.isBlank()) return jsonError("execute_shell_command", "SSH device \"$deviceName\" has no host configured.")
            return try {
                val result = client.executeCommand(cmd, workdir)
                buildJsonObject {
                    put("type", "execute_shell_command"); put("server", deviceName); put("command", cmd)
                    put("exit_code", result.exitCode)
                    put("output", (result.stdout + if (result.stderr.isNotBlank()) "\n${result.stderr}" else "").trimEnd())
                }.toString()
            } catch (e: Exception) {
                jsonError("execute_shell_command", e.message ?: "Unknown error", server = deviceName, command = cmd)
            }
        }

        override suspend fun fileRead(path: String, offset: Long, limit: Long): String {
            return try {
                val content = client.fileRead(path, offset, limit)
                buildJsonObject {
                    put("type", "file_read"); put("server", deviceName); put("path", path)
                    put("content", content); put("lines", content.lines().size)
                }.toString()
            } catch (e: Exception) {
                jsonError("file_read", "SFTP read failed: ${e.message}", server = deviceName)
            }
        }

        override suspend fun fileWrite(path: String, content: String): String? =
            client.fileWrite(path, content)?.let { jsonError("file_write", it, server = deviceName) }

        override suspend fun fileGlob(pattern: String, basePath: String, depth: Int?): Result<List<String>> =
            Result.success(client.fileGlob(pattern, basePath, depth))

        override suspend fun fileGrep(pattern: String, basePath: String, fileGlob: String): Result<List<ShellClient.GrepMatch>> =
            client.fileGrep(pattern, basePath, fileGlob).map { matches ->
                matches.map { ShellClient.GrepMatch(it.path, it.line, it.content) }
            }

        override fun close() { client.close() }
    }

    private inner class SandboxBackend : Backend {
        private val mgr = sandbox ?: throw IllegalStateException("Sandbox not available")

        override suspend fun executeCommand(cmd: String, workdir: String, timeoutMs: Int): String {
            if (!mgr.isAvailable()) return jsonError("execute_shell_command", "Local Sandbox is not installed.")
            return try {
                val result = mgr.executeCommand(cmd, workdir, timeoutMs)
                buildJsonObject {
                    put("type", "execute_shell_command"); put("server", "Local Sandbox"); put("command", cmd)
                    put("exit_code", result.exitCode)
                    put("output", (result.stdout + if (result.stderr.isNotBlank()) "\n${result.stderr}" else "").trimEnd())
                }.toString()
            } catch (e: Exception) {
                jsonError("execute_shell_command", e.message ?: "Unknown error", server = "Local Sandbox", command = cmd)
            }
        }

        override suspend fun fileRead(path: String, offset: Long, limit: Long): String {
            return try {
                val content = mgr.fileRead(path, offset, limit)
                buildJsonObject {
                    put("type", "file_read"); put("server", "Local Sandbox"); put("path", path)
                    put("content", content); put("lines", content.lines().size)
                }.toString()
            } catch (e: Exception) {
                jsonError("file_read", e.message ?: "Read failed", server = "Local Sandbox")
            }
        }

        override suspend fun fileWrite(path: String, content: String): String? =
            mgr.fileWrite(path, content)?.let { jsonError("file_write", it, server = "Local Sandbox") }

        override suspend fun fileGlob(pattern: String, basePath: String, depth: Int?): Result<List<String>> =
            Result.success(mgr.fileGlob(pattern, basePath, depth))

        override suspend fun fileGrep(pattern: String, basePath: String, fileGlob: String): Result<List<ShellClient.GrepMatch>> =
            mgr.fileGrep(pattern, basePath, fileGlob).map { matches ->
                matches.map { ShellClient.GrepMatch(it.path, it.line, it.content) }
            }

        override fun close() {}
    }

    private suspend fun getBackend(serverName: String, ctx: GenerationContext): Backend? {
        // Local Sandbox
        if (serverName.equals("Local Sandbox", ignoreCase = true) && ctx.sandboxEnabled) {
            if (sandbox?.isAvailable() == true) return SandboxBackend()
            if (sandbox != null) return null
        }
        if (serverName.isBlank()) {
            if (ctx.sandboxEnabled && sandbox?.isAvailable() == true) {
                return SandboxBackend()
            }
        }
        val device = resolveShellDevice(serverName, ctx) ?: return null
        return when (device.type) {
            "ssh" -> SshBackend(device)
            else -> ConchBackend(device)
        }
    }

    // ── ToolProvider interface ─────────────────────────────

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.shellEnabled) return emptyList()
        if (ctx.shellDevices.isEmpty() && !ctx.sandboxEnabled) return emptyList()

        val hasLocal = ctx.sandboxEnabled
        val allDeviceNames = buildList {
            if (hasLocal) add("Local Sandbox")
            addAll(ctx.shellDevices.map { d -> "\"${d.name}\"" })
        }
        val deviceNamesStr = allDeviceNames.joinToString(", ")

        val serverPropDesc = if (allDeviceNames.size == 1) {
            "The shell server name (optional, defaults to the only available server: ${allDeviceNames[0]})."
        } else {
            "The shell server name. Use list_shells to see available servers: $deviceNamesStr."
        }
        val shellRequiredParams = if (allDeviceNames.size == 1) listOf("command") else listOf("command", "server")

        val shellTools = listOf(
            ToolDefinition(function = ToolFunction(
                name = "list_shells",
                description = "List configured shell servers including the local sandbox (if enabled).",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "execute_shell_command",
                description = "Execute a shell command on a remote server or local sandbox.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "command" to ToolProperty("string", "The shell command to execute."),
                        "server" to ToolProperty("string", serverPropDesc),
                        "timeout_ms" to ToolProperty("integer", "Timeout in milliseconds (optional, default 30s)."),
                        "workdir" to ToolProperty("string", "Working directory (optional).")
                    ),
                    required = shellRequiredParams
                )
            ))
        )

        val fileServerProperty = if (allDeviceNames.size == 1) {
            ToolProperty("string", "The shell server name (optional, defaults to the only available server).")
        } else {
            ToolProperty("string", "The shell server name. Available: $deviceNamesStr.")
        }
        val fileRequired = if (allDeviceNames.size == 1) emptyList<String>() else listOf("server")

        val fileTools = listOf(
            ToolDefinition(function = ToolFunction(
                name = "file_read",
                description = "Read a file from a shell server or local sandbox.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to ToolProperty("string", "Absolute path to the file."),
                        "server" to fileServerProperty,
                        "offset" to ToolProperty("integer", "Byte offset (optional)."),
                        "limit" to ToolProperty("integer", "Max bytes to read (optional, default 1MB).")
                    ),
                    required = listOf("path") + fileRequired
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "file_write",
                description = "Write content to a file on a shell server or local sandbox.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to ToolProperty("string", "Absolute path to the file."),
                        "content" to ToolProperty("string", "Content to write."),
                        "server" to fileServerProperty
                    ),
                    required = listOf("path", "content") + fileRequired
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "file_edit",
                description = "Edit a file on a shell server or local sandbox by replacing old_string with new_string.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to ToolProperty("string", "Absolute path to the file."),
                        "old_string" to ToolProperty("string", "The exact text to find and replace."),
                        "new_string" to ToolProperty("string", "The replacement text."),
                        "server" to fileServerProperty,
                        "replace_all" to ToolProperty("boolean", "Replace all occurrences (optional, default false).")
                    ),
                    required = listOf("path", "old_string", "new_string") + fileRequired
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "file_glob",
                description = "List files on a shell server or local sandbox matching a glob pattern.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "pattern" to ToolProperty("string", "Glob pattern matched against file names (e.g. '*.go', '*.md')."),
                        "server" to fileServerProperty,
                        "path" to ToolProperty("string", "Base directory for the search (optional)."),
                        "depth" to ToolProperty("integer", "Max directory levels to search below 'path': 1 = base directory only, higher values recurse deeper, 0 = unlimited recursion. Omit for the server default.")
                    ),
                    required = listOf("pattern") + fileRequired
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "file_grep",
                description = "Search for a regex pattern in files on a shell server or local sandbox.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "pattern" to ToolProperty("string", "Regular expression pattern to search for."),
                        "server" to fileServerProperty,
                        "path" to ToolProperty("string", "File or directory to search in (optional)."),
                        "glob" to ToolProperty("string", "Filter files by glob pattern (optional).")
                    ),
                    required = listOf("pattern") + fileRequired
                )
            ))
        )

        return shellTools + fileTools
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        return when (name) {
            "list_shells" -> listShells(ctx)
            "execute_shell_command" -> executeShellCommand(arguments, ctx)
            "file_read" -> executeFileRead(arguments, ctx)
            "file_write" -> executeFileWrite(arguments, ctx)
            "file_edit" -> executeFileEdit(arguments, ctx)
            "file_glob" -> executeFileGlob(arguments, ctx)
            "file_grep" -> executeFileGrep(arguments, ctx)
            else -> "Unknown tool: $name"
        }
    }

    override fun handles(name: String): Boolean = name in setOf(
        "list_shells", "execute_shell_command",
        "file_read", "file_write", "file_edit", "file_glob", "file_grep"
    )

    // ── list_shells ────────────────────────────────────────

    private suspend fun listShells(ctx: GenerationContext): String {
        val items = buildList {
            val sandboxOk = ctx.sandboxEnabled && sandbox?.isAvailable() == true
            if (sandboxOk) {
                val homePath = sandbox?.sandboxHomePath
                add(buildJsonObject {
                    put("name", "Local Sandbox")
                    put("description", "Alpine Linux on-device")
                    put("type", "local")
                    // Tell the model the real home path so it doesn't guess one from the
                    // app name and write into a non-bind-mounted directory inside the rootfs
                    // (where the user can't see the files).
                    homePath?.let { put("home", it) }
                })
            }
            ctx.shellDevices.forEach { d ->
                add(buildJsonObject {
                    put("name", d.name.ifBlank { "Untitled" })
                    put("description", d.description)
                    put("type", d.type)
                    when (d.type) {
                        "ssh" -> { put("host", d.sshHost); put("port", d.sshPort) }
                        else -> put("url", d.serverUrl)
                    }
                })
            }
        }
        return buildJsonObject {
            put("type", "list_shells")
            putJsonArray("devices") { items.forEach { add(it) } }
        }.toString()
    }

    // ── Shell execution ────────────────────────────────────

    private suspend fun executeShellCommand(arguments: String, ctx: GenerationContext): String {
        val args = parseToolArgs(arguments)
        val command = arg(args, "command")
        if (command.isBlank()) return jsonError("execute_shell_command", "no_command")
        val serverName = arg(args, "server")
        val timeoutMs = (arg(args, "timeout_ms").toIntOrNull() ?: 30000).coerceIn(1000, 120000)
        val workdir = arg(args, "workdir")

        val backend = getBackend(serverName, ctx)
            ?: return jsonError("execute_shell_command", serverNotFoundMessage(serverName, ctx))
        try {
            if (!confirmRemote(resolveShellDevice(serverName, ctx), "$ $command")) {
                return jsonError("execute_shell_command", "denied_by_user: the user declined to run this command", server = serverName, command = command)
            }
            return backend.executeCommand(command, workdir, timeoutMs)
        } finally {
            backend.close()
        }
    }

    // ── File tools ─────────────────────────────────────────

    private suspend fun executeFileRead(arguments: String, ctx: GenerationContext): String {
        val args = parseToolArgs(arguments)
        val path = arg(args, "path")
        if (path.isBlank()) return jsonError("file_read", "path is required")
        val serverName = arg(args, "server")
        val offset = arg(args, "offset").toLongOrNull() ?: 0L
        val limit = arg(args, "limit").toLongOrNull() ?: 0L

        val backend = getBackend(serverName, ctx)
            ?: return jsonError("file_read", serverNotFoundMessage(serverName, ctx))
        try {
            return backend.fileRead(path, offset, limit)
        } finally {
            backend.close()
        }
    }

    private suspend fun executeFileWrite(arguments: String, ctx: GenerationContext): String {
        val args = parseToolArgs(arguments)
        val path = arg(args, "path")
        if (path.isBlank()) return jsonError("file_write", "path is required")
        val content = arg(args, "content")
        if (content.isBlank()) return jsonError("file_write", "content is required")
        val serverName = arg(args, "server")

        val backend = getBackend(serverName, ctx)
            ?: return jsonError("file_write", serverNotFoundMessage(serverName, ctx))
        try {
            if (!confirmRemote(resolveShellDevice(serverName, ctx), "write file: $path")) {
                return jsonError("file_write", "denied_by_user: the user declined to write this file", server = serverName)
            }
            val error = backend.fileWrite(path, content)
            if (error != null) return error
            return buildJsonObject {
                put("type", "file_write"); put("path", path); put("ok", true)
            }.toString()
        } finally {
            backend.close()
        }
    }

    private suspend fun executeFileEdit(arguments: String, ctx: GenerationContext): String {
        val args = parseToolArgs(arguments)
        val path = arg(args, "path")
        if (path.isBlank()) return jsonError("file_edit", "path is required")
        val oldStr = arg(args, "old_string")
        if (oldStr.isBlank()) return jsonError("file_edit", "old_string is required")
        val newStr = arg(args, "new_string")
        val replaceAll = arg(args, "replace_all").equals("true", ignoreCase = true)
        val serverName = arg(args, "server")

        val backend = getBackend(serverName, ctx)
            ?: return jsonError("file_edit", serverNotFoundMessage(serverName, ctx))
        try {
            if (!confirmRemote(resolveShellDevice(serverName, ctx), "edit file: $path")) {
                return jsonError("file_edit", "denied_by_user: the user declined to edit this file", server = serverName)
            }
            // Read the file
            val rawContent = try {
                backend.fileRead(path, 0, 0)
            } catch (e: Exception) {
                return jsonError("file_edit", "read error: ${e.message}")
            }
            // Extract actual content (Conch wraps it in JSON, others return raw text)
            val actualContent = try {
                val obj = Json.parseToJsonElement(rawContent).jsonObject
                (obj["content"] as? JsonPrimitive)?.content ?: rawContent
            } catch (_: Exception) { rawContent }

            val count = actualContent.split(oldStr).size - 1
            if (count == 0) {
                return jsonError("file_edit", "old_string not found in file")
            }
            if (count > 1 && !replaceAll) {
                return jsonError("file_edit", "Found $count matches. Use replace_all=true or provide more context.")
            }
            val replaced = actualContent.replace(oldStr, newStr)
            val writeError = backend.fileWrite(path, replaced)
            if (writeError != null) {
                return jsonError("file_edit", "write error: $writeError")
            }
            return buildJsonObject {
                put("type", "file_edit"); put("path", path)
                put("replaced", if (replaceAll) count else 1)
            }.toString()
        } finally {
            backend.close()
        }
    }

    private suspend fun executeFileGlob(arguments: String, ctx: GenerationContext): String {
        val args = parseToolArgs(arguments)
        val pattern = arg(args, "pattern")
        if (pattern.isBlank()) return jsonError("file_glob", "pattern is required")
        val serverName = arg(args, "server")
        val basePath = arg(args, "path")
        // Absent/blank → null → backward-compatible default behavior per backend.
        val depth = arg(args, "depth").toIntOrNull()

        val backend = getBackend(serverName, ctx)
            ?: return jsonError("file_glob", serverNotFoundMessage(serverName, ctx))
        try {
            val result = backend.fileGlob(pattern, basePath, depth)
            return result.fold(
                onSuccess = { files ->
                    buildJsonObject {
                        put("type", "file_glob"); put("pattern", pattern)
                        putJsonArray("files") { files.forEach { add(JsonPrimitive(it)) } }
                    }.toString()
                },
                onFailure = { e -> jsonError("file_glob", e.message ?: "Unknown error") }
            )
        } finally {
            backend.close()
        }
    }

    private suspend fun executeFileGrep(arguments: String, ctx: GenerationContext): String {
        val args = parseToolArgs(arguments)
        val pattern = arg(args, "pattern")
        if (pattern.isBlank()) return jsonError("file_grep", "pattern is required")
        val serverName = arg(args, "server")
        val basePath = arg(args, "path")
        val fileGlob = arg(args, "glob")

        val backend = getBackend(serverName, ctx)
            ?: return jsonError("file_grep", serverNotFoundMessage(serverName, ctx))
        try {
            val result = backend.fileGrep(pattern, basePath, fileGlob)
            return result.fold(
                onSuccess = { matches ->
                    buildJsonObject {
                        put("type", "file_grep"); put("pattern", pattern)
                        putJsonArray("matches") {
                            matches.forEach { m ->
                                add(buildJsonObject {
                                    put("path", m.path); put("line", m.line); put("content", m.content)
                                })
                            }
                        }
                    }.toString()
                },
                onFailure = { e -> jsonError("file_grep", e.message ?: "Unknown error") }
            )
        } finally {
            backend.close()
        }
    }
}
