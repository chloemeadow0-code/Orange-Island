package com.orangeisland.app.util

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystems

/**
 * SSH client using JSch for shell command execution (exec channel)
 * and file operations (SFTP channel). All file operations are SFTP-based,
 * no remote command assumptions (compatible with Windows/Termux/BusyBox).
 *
 * Host-key handling (trust-on-first-use):
 *  - [pinnedHostKey] set  → the server key MUST match, else the connection is
 *    rejected (defends against MITM / key changes).
 *  - [pinnedHostKey] blank + [allowUnknownHostKey] true → connect and capture the
 *    key (used by "Verify & pin" in settings, and as back-compat for un-pinned
 *    devices); the captured value is exposed via [capturedHostKey].
 *  - blank + false → unknown keys are rejected (fail-closed).
 *
 * NOT thread-safe — create a new instance per tool call.
 */
class SshClient(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String,
    private val timeoutMs: Int = 30000,
    private val pinnedHostKey: String = "",
    private val allowUnknownHostKey: Boolean = false
) {
    private var session: Session? = null

    /** The server host key (base64 of the public-key blob) seen on the last connect. */
    var capturedHostKey: String? = null
        private set

    // ── Connection ─────────────────────────────────────────

    private suspend fun getSession(): Session {
        session?.let { if (it.isConnected) return it }
        return withContext(Dispatchers.IO) {
            val jsch = JSch()
            val s = jsch.getSession(user, host, port).apply {
                setPassword(password)
                setConfig("PreferredAuthentications", "password")
                hostKeyRepository = TofuHostKeyRepository()
                // "yes" makes JSch reject NOT_INCLUDED/CHANGED keys; "no" accepts (capture mode).
                setConfig("StrictHostKeyChecking", if (allowUnknownHostKey && pinnedHostKey.isBlank()) "no" else "yes")
                connect(timeoutMs)
            }
            session = s
            s
        }
    }

    /** TOFU host-key verifier: captures the presented key and accepts only a match. */
    private inner class TofuHostKeyRepository : HostKeyRepository {
        override fun check(host: String?, key: ByteArray): Int {
            val incoming = java.util.Base64.getEncoder().encodeToString(key)
            capturedHostKey = incoming
            return when {
                pinnedHostKey.isNotBlank() && pinnedHostKey == incoming -> HostKeyRepository.OK
                pinnedHostKey.isNotBlank() -> HostKeyRepository.CHANGED   // mismatch → reject
                else -> HostKeyRepository.NOT_INCLUDED                    // unknown
            }
        }
        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID(): String = ""
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    companion object {
        /** OpenSSH-style "SHA256:…" fingerprint of a base64 host-key blob, for display. */
        fun fingerprintSha256(base64Key: String): String = try {
            val bytes = java.util.Base64.getDecoder().decode(base64Key)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            "SHA256:" + java.util.Base64.getEncoder().withoutPadding().encodeToString(digest)
        } catch (_: Exception) { "" }
    }

    fun close() {
        session?.disconnect()
        session = null
    }

    // ── Shell Command ──────────────────────────────────────

    data class CommandResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    suspend fun executeCommand(
        command: String,
        workdir: String = ""
    ): CommandResult = withContext(Dispatchers.IO) {
        val sess = getSession()
        val cmd = if (workdir.isNotBlank()) "cd ${escapeBash(workdir)} && $command" else command
        val channel = sess.openChannel("exec") as ChannelExec
        channel.setCommand(cmd)
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()
        channel.setOutputStream(stdoutStream)
        channel.setErrStream(stderrStream)
        channel.connect(timeoutMs)
        // Wait for completion (channel closes when the remote command exits)
        while (!channel.isClosed) {
            try { Thread.sleep(100) } catch (_: InterruptedException) { break }
        }
        val exitCode = channel.exitStatus
        channel.disconnect()
        CommandResult(
            stdout = stdoutStream.toString("UTF-8"),
            stderr = stderrStream.toString("UTF-8"),
            exitCode = exitCode
        )
    }

    // ── SFTP Helpers ───────────────────────────────────────

    private suspend fun <T> withSftp(block: suspend (ChannelSftp) -> T): T {
        val sess = getSession()
        return withContext(Dispatchers.IO) {
            val channel = sess.openChannel("sftp") as ChannelSftp
            channel.connect(timeoutMs)
            try {
                block(channel)
            } finally {
                channel.disconnect()
            }
        }
    }

    private fun ensureParentDirs(sftp: ChannelSftp, path: String) {
        val parts = path.split('/').filter { it.isNotBlank() }
        var current = if (path.startsWith('/')) "" else "."
        for (part in parts) {
            current += "/$part"
            if (current.isBlank() || current == ".") continue
            try { sftp.stat(current) } catch (_: Exception) {
                try { sftp.mkdir(current) } catch (_: Exception) { /* best-effort */ }
            }
        }
    }

    // ── file_read ──────────────────────────────────────────

    suspend fun fileRead(
        path: String,
        offset: Long = 0,
        limit: Long = 0
    ): String = withSftp { sftp ->
        try {
            val inputStream = sftp.get(path)
            val bytes = inputStream.readBytes()
            inputStream.close()
            val start = offset.coerceIn(0, bytes.size.toLong()).toInt()
            val end = if (limit > 0) {
                minOf(start + limit, bytes.size.toLong()).toInt()
            } else {
                bytes.size
            }
            String(bytes, start, end - start, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("SFTP read failed: ${e.message}")
        }
    }

    // ── file_write ─────────────────────────────────────────

    /**
     * Returns null on success, error message string on failure.
     */
    suspend fun fileWrite(path: String, content: String): String? = withSftp { sftp ->
        try {
            val parent = path.substringBeforeLast('/', "")
            if (parent.isNotBlank()) {
                ensureParentDirs(sftp, parent)
            }
            sftp.put(content.byteInputStream(Charsets.UTF_8), path, ChannelSftp.OVERWRITE)
            null
        } catch (e: Exception) {
            "SFTP write failed: ${e.message}"
        }
    }

    // ── file_glob ──────────────────────────────────────────

    suspend fun fileGlob(pattern: String, basePath: String = "", depth: Int? = null): List<String> =
        withSftp { sftp ->
            val base = basePath.ifBlank {
                try { sftp.pwd() } catch (_: Exception) { "/" }
            }.trimEnd('/')
            val allFiles = mutableListOf<String>()
            // null = legacy full recursion; <=0 = explicit unlimited; >=1 = max levels.
            val remaining = if (depth == null || depth <= 0) -1 else depth
            sftpListRecursive(sftp, base, allFiles, remaining)
            globMatch(allFiles, base, pattern)
        }

    // remaining: levels still allowed including the current dir's files. -1 = unlimited;
    // 1 = only this dir's files (no descent); >1 = descend with one fewer level.
    private fun sftpListRecursive(sftp: ChannelSftp, dir: String, result: MutableList<String>, remaining: Int = -1) {
        try {
            @Suppress("UNCHECKED_CAST")
            val entries = sftp.ls(dir) as? List<ChannelSftp.LsEntry> ?: return
            for (entry in entries) {
                val name = entry.filename
                if (name == "." || name == "..") continue
                val fullPath = "$dir/$name"
                if (entry.attrs.isDir) {
                    if (remaining < 0 || remaining > 1) {
                        sftpListRecursive(sftp, fullPath, result, if (remaining < 0) -1 else remaining - 1)
                    }
                } else {
                    result.add(fullPath)
                }
            }
        } catch (_: Exception) {
            // Permission denied or directory doesn't exist — skip
        }
    }

    private fun globMatch(files: List<String>, basePath: String, pattern: String): List<String> {
        val adjustedPattern = if (pattern.contains('/')) pattern else "**/$pattern"
        val fullPattern = "$basePath/$adjustedPattern"
        val pathMatcher = try {
            FileSystems.getDefault().getPathMatcher("glob:$fullPattern")
        } catch (_: Exception) {
            return emptyList()
        }
        return files.filter { f ->
            try { pathMatcher.matches(java.nio.file.Paths.get(f)) } catch (_: Exception) { false }
        }
    }

    // ── file_grep ──────────────────────────────────────────

    data class GrepMatch(
        val path: String,
        val line: Int,
        val content: String
    )

    /**
     * grep for pattern in files.
     * Strategy: try server-side grep first (fast), fall back to SFTP read+grep locally.
     */
    suspend fun fileGrep(
        pattern: String,
        basePath: String = "",
        fileGlob: String = ""
    ): Result<List<GrepMatch>> {
        val base = basePath.ifBlank { "." }

        // Try server-side grep via exec channel first
        return try {
            val grepCmd = buildString {
                // -I skips binary files (matches the local-fallback NUL heuristic below).
                append("grep -rnI ")
                if (fileGlob.isNotBlank()) append("--include='$fileGlob' ")
                append("-- ")
                append(escapeBash(pattern))
                append(" ")
                append(escapeBash(base))
            }
            val result = executeCommand(grepCmd)
            when {
                result.exitCode == 0 || result.exitCode == 1 -> {
                    // 0=matches found, 1=no matches (both valid)
                    val matches = result.stdout.lines()
                        .filter { it.isNotBlank() }
                        .mapNotNull { parseGrepLine(it) }
                    Result.success(matches)
                }
                else -> {
                    // exitCode >= 2: grep error (not installed, bad args, etc.) — fallback
                    fallbackGrep(pattern, base, fileGlob)
                }
            }
        } catch (_: Exception) {
            fallbackGrep(pattern, base, fileGlob)
        }
    }

    private suspend fun fallbackGrep(
        regex: String,
        basePath: String,
        fileGlob: String
    ): Result<List<GrepMatch>> {
        return try {
            val globPattern = fileGlob.ifBlank { "*" }
            val files = fileGlob(globPattern, basePath)
            val pattern = try {
                Regex(regex)
            } catch (e: Exception) {
                Regex(java.util.regex.Pattern.quote(regex))
            }
            val allMatches = mutableListOf<GrepMatch>()
            val maxReadSize = 500_000L // MAX_FILE_CONTENT_READ_LENGTH equivalent
            for (file in files) {
                try {
                    val content = fileRead(file, 0, maxReadSize)
                    // Skip binary files (NUL-byte heuristic), as grep -I would.
                    if (content.contains('\u0000')) continue
                    content.lines().forEachIndexed { index, line ->
                        if (pattern.containsMatchIn(line)) {
                            allMatches.add(GrepMatch(
                                path = file,
                                line = index + 1,
                                content = line.take(500)
                            ))
                        }
                    }
                } catch (_: Exception) {
                    // Skip unreadable files
                }
            }
            Result.success(allMatches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGrepLine(line: String): GrepMatch? {
        // Format: "path:lineNum:content" or "path:lineNum:col:content"
        val firstColon = line.indexOf(':')
        if (firstColon < 0) return null
        val path = line.substring(0, firstColon)
        val afterPath = line.substring(firstColon + 1)
        val secondColon = afterPath.indexOf(':')
        if (secondColon < 0) return null
        val lineNumStr = afterPath.substring(0, secondColon)
        val content = afterPath.substring(secondColon + 1).take(500)
        val lineNum = lineNumStr.toIntOrNull() ?: return null
        return GrepMatch(path = path, line = lineNum, content = content)
    }

    // ── Shell Escaping ─────────────────────────────────────

    private fun escapeBash(s: String): String {
        // Wrap in single quotes; escape any embedded single quotes as '\''
        return "'${s.replace("'", "'\\''")}'"
    }
}
