package com.orangeisland.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MemoryManager(context: Context) {
    private val memoryDir: File =
        File(context.filesDir, "memory_db").also { it.mkdirs() }

    private val activeMemoryFile: File =
        File(context.filesDir, "active_memory.md")

    private val metaFile: File =
        File(memoryDir, "memory_meta.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    data class MemoryFileInfo(
        val name: String,
        val description: String = ""
    )

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
    private fun loadMeta(): MutableMap<String, String> =
        if (metaFile.exists()) {
            try { json.decodeFromString<MutableMap<String, String>>(metaFile.readText()) }
            catch (_: Exception) { mutableMapOf() }
        } else mutableMapOf()

    @Synchronized
    private fun saveMeta(meta: Map<String, String>) {
        metaFile.writeText(json.encodeToString(meta))
    }

    @Synchronized
    fun getDescription(name: String): String {
        val resolved = resolveFile(name)
        if (!resolved.exists()) return ""
        return loadMeta()[resolved.name] ?: ""
    }

    @Synchronized
    fun setDescription(name: String, description: String) {
        val resolved = resolveFile(name)
        if (!resolved.exists()) throw IllegalArgumentException("File not found: $name")
        val meta = loadMeta()
        if (description.isBlank()) meta.remove(resolved.name) else meta[resolved.name] = description
        saveMeta(meta)
    }

    @Synchronized
    fun listFiles(): List<MemoryFileInfo> {
        val meta = loadMeta()
        return memoryDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.map { MemoryFileInfo(it.name, meta[it.name] ?: "") }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun getMetaJson(): String =
        if (metaFile.exists()) metaFile.readText() else "{}"

    fun saveMetaJson(jsonStr: String) {
        metaFile.writeText(jsonStr)
    }

    @Synchronized
    fun readFile(name: String): String {
        val file = resolveFile(name)
        if (!file.exists()) throw IllegalArgumentException("File not found: $name")
        return file.readText()
    }

    @Synchronized
    fun createFile(name: String, content: String, description: String = ""): String {
        val file = resolveFile(name)
        if (file.exists()) throw IllegalArgumentException("File already exists: ${file.name}")
        file.writeText(content)
        if (description.isNotBlank()) {
            val meta = loadMeta()
            meta[file.name] = description
            saveMeta(meta)
        }
        return "Created ${file.name}"
    }

    @Synchronized
    fun editFile(name: String, content: String? = null, newName: String? = null, description: String? = null, oldString: String? = null, newString: String? = null): String {
        val file = resolveFile(name)
        if (!file.exists()) throw IllegalArgumentException("File not found: $name")
        val meta = loadMeta()
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
            renamedFile = resolveFile(newName)
            if (renamedFile.exists()) throw IllegalArgumentException("Target file already exists: ${renamedFile.name}")
            file.renameTo(renamedFile)
            val desc = meta.remove(file.name)
            if (desc != null) meta[renamedFile.name] = desc
        }
        if (description != null) {
            if (description.isBlank()) meta.remove((renamedFile ?: file).name)
            else meta[(renamedFile ?: file).name] = description
        }
        saveMeta(meta)
        val targetName = newName?.let { resolveFile(it).name } ?: file.name
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
    fun deleteFile(name: String): String {
        val file = resolveFile(name)
        if (!file.exists()) throw IllegalArgumentException("File not found: $name")
        file.delete()
        val meta = loadMeta()
        meta.remove(file.name)
        saveMeta(meta)
        return "Deleted ${file.name}"
    }

    private fun resolveFile(name: String): File {
        val sanitized = name.replace(Regex("""[/\\]"""), "_")
        val file = File(memoryDir, if (sanitized.endsWith(".md")) sanitized else "$sanitized.md")
        val canonicalPath = file.canonicalPath
        val canonicalDir = memoryDir.canonicalPath
        if (!canonicalPath.startsWith(canonicalDir)) {
            throw IllegalArgumentException("Invalid file name: $name")
        }
        return file
    }
}
