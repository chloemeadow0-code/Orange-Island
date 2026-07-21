package com.orangeisland.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MemoryManager(context: Context) {
    private val appContext: Context = context
    private val memoryDir: File =
        File(context.filesDir, "memory_db").also { it.mkdirs() }

    private val activeMemoryFile: File =
        File(context.filesDir, "active_memory.md")

    private val projectsMemoryRoot: File =
        File(context.filesDir, "memory_db_projects").also { it.mkdirs() }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    data class MemoryFileInfo(
        val name: String,
        val description: String = "",
        // Origin bucket so callers (tool provider, UI) can show where a file lives.
        // null = global; non-null = the project id this file belongs to.
        val projectId: String? = null
    )

    /**
     * Resolves the memory directory for [projectId]. null → the global `memory_db/`
     * (shared with every conversation). Non-null → a project-private subdirectory
     * `memory_db_projects/<projectId>/`, created on demand. The project-private area is
     * invisible to conversations outside that project.
     */
    private fun dirFor(projectId: String?): File =
        if (projectId == null) memoryDir
        else File(projectsMemoryRoot, sanitizeProjectId(projectId)).also { it.mkdirs() }

    private fun metaFileFor(projectId: String?): File = File(dirFor(projectId), "memory_meta.json")

    /** Project ids are UUIDs but we sanitize defensively against path traversal all the same. */
    private fun sanitizeProjectId(projectId: String): String =
        projectId.replace(Regex("""[^A-Za-z0-9_-]"""), "_")

    @Synchronized
    fun getActiveMemory(): String =
        if (activeMemoryFile.exists()) activeMemoryFile.readText() else ""

    @Synchronized
    fun updateActiveMemory(
        content: String,
        mode: String = "replace",
        oldString: String? = null,
        newString: String? = null
    ): String =
        when (mode) {
            "append" -> {
                activeMemoryFile.appendText("\n$content")
                "Appended to active memory."
            }
            "prepend" -> {
                val existing = getActiveMemory()
                activeMemoryFile.writeText("$content\n$existing")
                "Prepended to active memory."
            }
            "patch" -> {
                if (oldString == null) throw IllegalArgumentException("old_string is required for patch mode")
                val existing = getActiveMemory()
                val count = existing.countOccurrences(oldString)
                if (count == 0)
                    throw IllegalArgumentException("old_string not found in active memory")
                if (count > 1)
                    throw IllegalArgumentException("old_string matches $count times in active memory — must be unique")
                activeMemoryFile.writeText(existing.replace(oldString, newString ?: ""))
                "Active memory patched."
            }
            else -> {
                activeMemoryFile.writeText(content)
                "Active memory updated."
            }
        }

    @Synchronized
    private fun loadMeta(projectId: String?): MutableMap<String, String> {
        val file = metaFileFor(projectId)
        return if (file.exists()) {
            try { json.decodeFromString<MutableMap<String, String>>(file.readText()) }
            catch (_: Exception) { mutableMapOf() }
        } else mutableMapOf()
    }

    @Synchronized
    private fun saveMeta(meta: Map<String, String>, projectId: String?) {
        metaFileFor(projectId).writeText(json.encodeToString(meta))
    }

    @Synchronized
    fun getDescription(name: String, projectId: String? = null): String {
        val resolved = resolveFile(name, projectId)
        if (!resolved.exists()) return ""
        return loadMeta(projectId)[resolved.name] ?: ""
    }

    @Synchronized
    fun setDescription(name: String, description: String, projectId: String? = null) {
        val resolved = resolveFile(name, projectId)
        if (!resolved.exists()) throw IllegalArgumentException("File not found: $name")
        val meta = loadMeta(projectId)
        if (description.isBlank()) meta.remove(resolved.name) else meta[resolved.name] = description
        saveMeta(meta, projectId)
    }

    /**
     * Lists `.md` files in the given scope. With [projectId] = null only the global memory_db
     * is listed (existing behavior). Callers that need "global + project" should use
     * [listFilesMerged] instead.
     */
    @Synchronized
    fun listFiles(projectId: String? = null): List<MemoryFileInfo> {
        val meta = loadMeta(projectId)
        return dirFor(projectId).listFiles()
            ?.filter { it.extension == "md" }
            ?.map { MemoryFileInfo(it.name, meta[it.name] ?: "", projectId) }
            ?.sortedBy { it.name } ?: emptyList()
    }

    /**
     * Convenience for tool calls: returns global files + (when [projectId] is non-null)
     * the project's private files in one list, tagged with [MemoryFileInfo.projectId] so
     * the caller can disambiguate or display the origin.
     */
    @Synchronized
    fun listFilesMerged(projectId: String?): List<MemoryFileInfo> {
        val global = listFiles(null)
        return if (projectId == null) global else global + listFiles(projectId)
    }

    /**
     * Global memory_meta.json accessor (kept for export/import which operates on the
     * global scope only). Prefer the project-aware overloads elsewhere.
     */
    fun getMetaJson(projectId: String? = null): String {
        val file = metaFileFor(projectId)
        return if (file.exists()) file.readText() else "{}"
    }

    fun saveMetaJson(jsonStr: String, projectId: String? = null) {
        metaFileFor(projectId).writeText(jsonStr)
    }

    @Synchronized
    fun readFile(name: String, projectId: String? = null): String {
        val file = resolveFile(name, projectId)
        if (!file.exists()) throw IllegalArgumentException("File not found: $name")
        return file.readText()
    }

    @Synchronized
    fun createFile(name: String, content: String, description: String = "", projectId: String? = null): String {
        val file = resolveFile(name, projectId)
        if (file.exists()) throw IllegalArgumentException("File already exists: ${file.name}")
        file.writeText(content)
        if (description.isNotBlank()) {
            val meta = loadMeta(projectId)
            meta[file.name] = description
            saveMeta(meta, projectId)
        }
        return "Created ${file.name}"
    }

    @Synchronized
    fun editFile(
        name: String, content: String? = null, newName: String? = null, description: String? = null,
        oldString: String? = null, newString: String? = null, projectId: String? = null
    ): String {
        val file = resolveFile(name, projectId)
        if (!file.exists()) throw IllegalArgumentException("File not found: $name")
        val meta = loadMeta(projectId)
        var renamedFile: File? = null
        if (oldString != null) {
            val fileText = file.readText()
            val count = fileText.countOccurrences(oldString)
            if (count == 0)
                throw IllegalArgumentException("old_string not found in ${file.name}")
            if (count > 1)
                throw IllegalArgumentException("old_string matches $count times in ${file.name} — must be unique")
            file.writeText(fileText.replace(oldString, newString ?: ""))
        } else if (content != null) {
            file.writeText(content)
        }
        if (newName != null && newName != name) {
            renamedFile = resolveFile(newName, projectId)
            if (renamedFile.exists()) throw IllegalArgumentException("Target file already exists: ${renamedFile.name}")
            file.renameTo(renamedFile)
            val desc = meta.remove(file.name)
            if (desc != null) meta[renamedFile.name] = desc
        }
        if (description != null) {
            if (description.isBlank()) meta.remove((renamedFile ?: file).name)
            else meta[(renamedFile ?: file).name] = description
        }
        saveMeta(meta, projectId)
        val targetName = newName?.let { resolveFile(it, projectId).name } ?: file.name
        if (oldString != null && newName != null) return "Replaced in and renamed to $targetName"
        if (oldString != null) return "Replaced in $targetName"
        if (content != null && newName != null) return "Updated and renamed to $targetName"
        if (content != null) return "Updated $targetName"
        if (newName != null) return "Renamed to $targetName"
        if (description != null) return "Updated description of $targetName"
        return "No changes made."
    }

    private fun String.countOccurrences(substring: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            idx = indexOf(substring, idx)
            if (idx < 0) break
            count++
            idx += substring.length
        }
        return count
    }

    @Synchronized
    fun deleteFile(name: String, projectId: String? = null): String {
        val file = resolveFile(name, projectId)
        if (!file.exists()) throw IllegalArgumentException("File not found: $name")
        file.delete()
        val meta = loadMeta(projectId)
        meta.remove(file.name)
        saveMeta(meta, projectId)
        return "Deleted ${file.name}"
    }

    private fun resolveFile(name: String, projectId: String? = null): File {
        val sanitized = name.replace(Regex("""[/\\]"""), "_")
        val file = File(dirFor(projectId), if (sanitized.endsWith(".md")) sanitized else "$sanitized.md")
        val canonicalPath = file.canonicalPath
        val canonicalDir = dirFor(projectId).canonicalPath
        if (!canonicalPath.startsWith(canonicalDir)) {
            throw IllegalArgumentException("Invalid file name: $name")
        }
        return file
    }

    /**
     * Lists every project id that currently has a private memory directory. Used by the
     * exporter to walk project memory bundles without knowing project ids ahead of time.
     */
    fun listProjectIds(): List<String> =
        projectsMemoryRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()

    /**
     * Export helper: returns the raw files (md + meta) for a project's private memory, so
     * DataExporter can stream them into the zip without poking private paths. Each pair is
     * (relativeName, absoluteFile) — the caller controls zip layout.
     */
    fun projectMemoryFilesForExport(projectId: String): List<Pair<String, java.io.File>> {
        val dir = dirFor(projectId)
        val files = dir.listFiles()?.toList() ?: emptyList()
        // Skip nothing here — meta + md all go in, mirror of the global export.
        return files.map { it.name to it }
    }

    /**
     * Import helper: writes a single memory file (content bytes) into a project's private
     * store, used by DataImporter when restoring project bundles. Idempotent — overwrites
     * any existing file with the same name.
     */
    @Synchronized
    fun writeProjectMemoryBytes(projectId: String, name: String, bytes: ByteArray) {
        val dir = dirFor(projectId)
        java.io.File(dir, name).writeBytes(bytes)
    }
}
