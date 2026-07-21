package com.orangeisland.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.data.local.ChatEntity
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.local.ProjectEntity
import com.orangeisland.app.model.AttachmentMeta
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.ThinkingLevels
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile

class DataImporter(
    private val context: Context,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager,
    private val memoryManager: MemoryManager
) {
    enum class ImportStrategy { MERGE, REPLACE, SKIP }

    private val importJson = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ImportManifest(
        @SerialName("orangeisland_export_version") val version: Int = 1,
        @SerialName("app_version") val appVersion: String = "",
        @SerialName("exported_at") val exportedAt: String = "",
        val categories: List<String> = emptyList(),
        @SerialName("has_api_keys") val hasApiKeys: Boolean = false
    )

    data class ImportPreview(
        val manifest: ImportManifest,
        val conversationCount: Int = 0,
        val memoryCount: Int = 0,
        val systemPromptCount: Int = 0,
        val settingsPresent: Boolean = false,
        val apiKeysPresent: Boolean = false
    )

    data class ImportResult(
        val conversationsImported: Int = 0,
        val memoriesImported: Int = 0,
        val systemPromptsImported: Int = 0,
        val settingsImported: Boolean = false,
        val apiKeysImported: Boolean = false,
        val errors: List<String> = emptyList()
    )

    private fun detectImageExtension(bytes: ByteArray): String {
        if (bytes.size < 4) return "jpg"
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() -> "gif"
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() -> "webp"
            else -> "jpg"
        }
    }

    private fun detectVideoExtension(bytes: ByteArray): String {
        if (bytes.size < 4) return "mp4"
        return when {
            bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "webm"
            else -> "mp4"
        }
    }

    /**
     * On-demand, memory-bounded reader over a backup ZIP. Entries are decoded
     * only when requested and one at a time, so large image/video blobs never
     * accumulate in memory (the previous implementation buffered *every* entry
     * into a `Map<String, ByteArray>` up front — a real OOM risk for backups with
     * many media attachments). The SAF stream is first copied to a temp file
     * because [ZipFile] needs random access; [close] disposes both.
     */
    private class Archive private constructor(
        private val zip: ZipFile,
        private val tmp: File
    ) : Closeable {
        fun has(name: String): Boolean = zip.getEntry(name) != null
        fun bytes(name: String): ByteArray? =
            zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        /** Map-style accessor so existing `archive["x"]` call sites read unchanged. */
        operator fun get(name: String): ByteArray? = bytes(name)
        fun stream(name: String): InputStream? = zip.getEntry(name)?.let { zip.getInputStream(it) }
        fun names(): List<String> =
            zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()

        override fun close() {
            try { zip.close() } finally { tmp.delete() }
        }

        companion object {
            fun open(context: Context, uri: Uri): Archive? {
                // Copy SAF content to a temp file so we can use ZipFile (random access,
                // more reliable than ZipInputStream).
                val tmp = File(context.cacheDir, "orangeisland_import_tmp.zip")
                return try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { out -> input.copyTo(out) }
                    } ?: run { tmp.delete(); return null }
                    Archive(ZipFile(tmp), tmp)
                } catch (_: Exception) {
                    tmp.delete()
                    null
                }
            }
        }
    }

    suspend fun readManifest(uri: Uri): ImportManifest? {
        return withContext(Dispatchers.IO) {
            Archive.open(context, uri)?.use { archive ->
                val manifestJson = archive["manifest.json"]?.decodeToString() ?: return@use null
                try {
                    importJson.decodeFromString<ImportManifest>(manifestJson)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun preview(uri: Uri): ImportPreview {
        return withContext(Dispatchers.IO) {
            val empty = ImportPreview(ImportManifest(version = 0))
            val archive = Archive.open(context, uri) ?: return@withContext empty
            archive.use {
                val manifestJson = archive["manifest.json"]?.decodeToString() ?: return@use empty
                val manifest = try {
                    importJson.decodeFromString<ImportManifest>(manifestJson)
                } catch (_: Exception) {
                    return@use empty
                }

                var conversationCount = 0
                var systemPromptCount = 0
                val memoryCount = archive.names().count { it.startsWith("memories/") }
                val settingsPresent = archive.has("settings.json")
                val apiKeysPresent = archive.has("api_keys.json")

                archive.stream("conversations.json")?.use { stream ->
                    try {
                        conversationCount = importJson.decodeFromStream<ExportConversations>(stream).conversations.size
                    } catch (e: Exception) { DebugLog.e("DataImporter", "Failed to parse conversations.json", e) }
                }

                archive["system_prompts.json"]?.let { json ->
                    try {
                        val data = importJson.decodeFromString<List<SystemPromptEntry>>(json.decodeToString())
                        systemPromptCount = data.size
                    } catch (e: Exception) { DebugLog.e("DataImporter", "Failed to parse system_prompts.json", e) }
                }

                ImportPreview(
                    manifest = manifest,
                    conversationCount = conversationCount,
                    memoryCount = memoryCount,
                    systemPromptCount = systemPromptCount,
                    settingsPresent = settingsPresent,
                    apiKeysPresent = apiKeysPresent
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun import(
        uri: Uri,
        decisions: Map<DataExporter.ExportCategory, DataImporter.ImportStrategy>,
        onProgress: (Float) -> Unit = {}
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            val archive = Archive.open(context, uri)
                ?: return@withContext ImportResult(errors = listOf("Could not open backup archive"))
            val errors = mutableListOf<String>()
            var conversationsImported = 0
            var memoriesImported = 0
            var systemPromptsImported = 0
            var settingsImported = false
            var apiKeysImported = false

            val activeCategories = decisions.filter { it.value != ImportStrategy.SKIP }.keys
            val totalSteps = activeCategories.size
            var completed = 0
            fun step() { completed++; onProgress(completed.toFloat() / totalSteps.coerceAtLeast(1)) }

            // Conversations
            val convDecision = decisions[DataExporter.ExportCategory.CONVERSATIONS]
            if (convDecision != null && convDecision != ImportStrategy.SKIP) {
                val videoCleanupList = mutableListOf<java.io.File>()
                try {
                    archive.stream("conversations.json")?.use { stream ->
                        val data = importJson.decodeFromStream<ExportConversations>(stream)
                        val convEntities = data.conversations.map { c ->
                            ChatEntity(c.id, c.title, c.lastUpdated, c.selectedBranchesJson, c.systemPromptId, c.modelId, c.projectId)
                        }
                        val msgEntities = data.messages.map { m ->
                            MessageEntity(m.id, m.conversationId, m.parentId, m.text, m.images,
                                m.thoughts, m.thoughtTitle, m.tokenCount,
                                try { MessageStatus.valueOf(m.status) } catch (_: Exception) { MessageStatus.SUCCESS },
                                try { Participant.valueOf(m.participant) } catch (_: Exception) { Participant.MODEL },
                                m.timestamp, m.thoughtTimeMs, m.modelName, m.toolCallJson, m.attachmentMeta)
                        }
                        // Restore image files from ZIP to app storage
                        val imagesDir = java.io.File(context.filesDir, "images")
                        imagesDir.mkdirs()
                        val restoredImages = mutableMapOf<String, MutableList<String>>() // messageId -> file paths
                        for (path in archive.names()) {
                            if (!path.startsWith("images/")) continue
                            val bytes = archive.bytes(path) ?: continue
                            // path format: images/<messageId>/<index>
                            val parts = path.removePrefix("images/").split("/")
                            if (parts.size == 2) {
                                val msgId = parts[0]
                                val ext = detectImageExtension(bytes)
                                val imgFile = java.io.File(imagesDir, "${msgId}_${parts[1]}.$ext")
                                imgFile.writeBytes(bytes)
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    imgFile
                                )
                                restoredImages.getOrPut(msgId) { mutableListOf() }.add(contentUri.toString())
                            }
                        }

                        // Restore video files from ZIP to app storage
                        val restoredVideos = mutableMapOf<String, String>() // messageId -> local file path
                        for (path in archive.names()) {
                            if (!path.startsWith("videos/")) continue
                            val bytes = archive.bytes(path) ?: continue
                            // path format: videos/<messageId>/<index>
                            val parts = path.removePrefix("videos/").split("/")
                            if (parts.size == 2 && bytes.isNotEmpty()) {
                                val msgId = parts[0]
                                val ext = detectVideoExtension(bytes)
                                val vidFile = java.io.File(context.filesDir, "vid_import_${java.util.UUID.randomUUID()}.$ext")
                                vidFile.writeBytes(bytes)
                                videoCleanupList.add(vidFile)
                                restoredVideos[msgId] = "file://${vidFile.absolutePath}"
                            }
                        }

                        // Update message entities with restored image paths
                        val finalMsgEntities = msgEntities.map { msg ->
                            val imgs = restoredImages[msg.id]
                            var updated = if (imgs != null) msg.copy(images = imgs) else msg
                            // Update attachmentMeta originalUri for videos
                            val videoPath = restoredVideos[msg.id]
                            if (videoPath != null && updated.attachmentMeta != null) {
                                try {
                                    val meta = importJson.decodeFromString<AttachmentMeta>(updated.attachmentMeta!!)
                                    val adjustedItems = meta.items.map { item ->
                                        if (item.type == "video") item.copy(originalUri = videoPath) else item
                                    }
                                    updated = updated.copy(attachmentMeta = Json.encodeToString(AttachmentMeta(items = adjustedItems)))
                                } catch (e: Exception) { DebugLog.e("DataImporter", "Failed to parse attachment metadata", e) }
                            }
                            updated
                        }

                        if (convDecision == ImportStrategy.REPLACE) {
                            chatDao.deleteAllConversations()
                            convEntities.forEach { chatDao.upsertConversation(it) }
                            finalMsgEntities.forEach { chatDao.upsertMessage(it) }
                            conversationsImported = data.conversations.size
                        } else {
                            // MERGE: upsert conversations, insert new messages,
                            // and update existing messages that have restored images
                            convEntities.forEach { chatDao.upsertConversation(it) }
                            val existingIds = chatDao.findExistingMessageIds(finalMsgEntities.map { it.id })
                            val existingSet = existingIds.toSet()
                            finalMsgEntities.filter { it.id !in existingSet }.forEach { chatDao.upsertMessage(it) }
                            finalMsgEntities.filter { it.id in existingSet && it.images.isNotEmpty() }.forEach { chatDao.upsertMessage(it) }
                            conversationsImported = data.conversations.size
                        }
                    }
                } catch (e: Exception) {
                    // Clean up restored video files on error
                    for (f in videoCleanupList) { try { f.delete() } catch (_: Exception) {} }
                    errors.add("Conversations: ${e.localizedMessage ?: "Unknown error"}")
                }

                // Projects ride on the same CONVERSATIONS decision — they're the folders that
                // hold the conversations. projects.json is optional (pre-projects exports have
                // none; conversations just import as ungrouped).
                try {
                    archive.stream("projects.json")?.use { stream ->
                        val data = importJson.decodeFromStream<ExportProjects>(stream)
                        if (convDecision == ImportStrategy.REPLACE) {
                            // Detach every conversation then drop all projects so REPLACE truly
                            // mirrors the source state. Conversations were already cleared above,
                            // so this only matters if the user re-imports over existing data.
                            chatDao.getAllProjectsList().forEach { chatDao.deleteProject(it.id) }
                        }
                        data.projects.forEach { p ->
                            chatDao.upsertProject(
                                ProjectEntity(p.id, p.name, p.sortOrder, p.systemPromptId, p.modelId, p.createdAt)
                            )
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Projects: ${e.localizedMessage ?: "Unknown error"}")
                }
                step()
            }

            // Memories
            val memDecision = decisions[DataExporter.ExportCategory.MEMORIES]
            if (memDecision != null && memDecision != ImportStrategy.SKIP) {
                try {
                    val memNames = archive.names().filter { it.startsWith("memories/") }
                    if (memDecision == ImportStrategy.REPLACE) {
                        for (file in memoryManager.listFiles()) {
                            memoryManager.deleteFile(file.name)
                        }
                        val activeMem = memoryManager.getActiveMemory()
                        if (activeMem.isNotEmpty()) {
                            memoryManager.updateActiveMemory("", "replace")
                        }
                    }
                    val existingNames = memoryManager.listFiles().map { it.name }.toSet()
                    for (path in memNames) {
                        // Project-private memory bundle: memories/projects/<projectId>/<file>.
                        // Written as raw bytes via the project-scoped helper so each file lands
                        // in the right project dir. These are skipped by the String-decoding
                        // branch below.
                        if (path.startsWith("memories/projects/")) {
                            val rest = path.removePrefix("memories/projects/")
                            val slashIdx = rest.indexOf('/')
                            if (slashIdx <= 0) continue
                            val projectId = rest.substring(0, slashIdx)
                            val fileName = rest.substring(slashIdx + 1)
                            if (fileName.isBlank()) continue
                            val bytes = archive.bytes(path) ?: continue
                            try {
                                memoryManager.writeProjectMemoryBytes(projectId, fileName, bytes)
                                memoriesImported++
                            } catch (_: Exception) {
                                // Skip files that can't be written (bad name, IO error).
                            }
                            continue
                        }
                        val text = archive.bytes(path)?.decodeToString() ?: continue
                        if (path == "memories/active_memory.md" && text.isNotBlank()) {
                            if (memDecision == ImportStrategy.REPLACE || memoryManager.getActiveMemory().isEmpty()) {
                                memoryManager.updateActiveMemory(text, "replace")
                            }
                            memoriesImported++
                        } else if (path == "memories/memory_db/memory_meta.json") {
                            if (memDecision == ImportStrategy.REPLACE || memoryManager.getMetaJson() == "{}") {
                                memoryManager.saveMetaJson(text)
                            }
                        } else if (path.startsWith("memories/memory_db/")) {
                            val name = path.removePrefix("memories/memory_db/")
                            if (memDecision == ImportStrategy.REPLACE || name !in existingNames) {
                                try {
                                    memoryManager.createFile(name, text)
                                } catch (_: Exception) {
                                    memoryManager.editFile(name, text)
                                }
                            }
                            memoriesImported++
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Memories: ${e.localizedMessage ?: "Unknown error"}")
                }
                step()
            }

            // System Prompts
            val promptsDecision = decisions[DataExporter.ExportCategory.SYSTEM_PROMPTS]
            if (promptsDecision != null && promptsDecision != ImportStrategy.SKIP) {
                try {
                    archive["system_prompts.json"]?.decodeToString()?.let { json ->
                        val prompts = importJson.decodeFromString<List<SystemPromptEntry>>(json)
                        if (promptsDecision == ImportStrategy.REPLACE) {
                            settingsManager.saveSystemPrompts(prompts)
                        } else {
                            // MERGE: append with new IDs
                            val existing = settingsManager.systemPrompts.first().toMutableList()
                            val existingTitles = existing.map { it.title }.toSet()
                            for (p in prompts) {
                                val newId = UUID.randomUUID().toString()
                                val title = if (p.title in existingTitles) "${p.title} (imported)" else p.title
                                existing.add(p.copy(id = newId, title = title))
                            }
                            settingsManager.saveSystemPrompts(existing)
                        }
                        systemPromptsImported = prompts.size
                    }
                } catch (e: Exception) {
                    errors.add("System prompts: ${e.localizedMessage ?: "Unknown error"}")
                }
                step()
            }

            // Settings
            val settingsDecision = decisions[DataExporter.ExportCategory.SETTINGS]
            if (settingsDecision != null && settingsDecision != ImportStrategy.SKIP) {
                try {
                    archive["settings.json"]?.decodeToString()?.let { json ->
                        val s = importJson.decodeFromString<ExportSettings>(json)
                        settingsManager.saveSelectedModel(s.selectedModel)
                        for ((provider, models) in s.availableModels) {
                            settingsManager.saveAvailableModels(provider, models)
                        }
                        settingsManager.saveEnabledModels(s.enabledModels)
                        settingsManager.saveModelAliases(s.modelAliases)
                        settingsManager.saveMaxContextWindow(s.maxContextWindow)
                        settingsManager.saveVisualizeContextRollout(s.visualizeContextRollout)
                        settingsManager.saveCodeExecutionEnabled(s.codeExecutionEnabled)
                        settingsManager.saveGoogleSearchEnabled(s.googleSearchEnabled)
                        settingsManager.saveThinkingEnabled(s.thinkingEnabled)
                        val legacyBudgetTokens = ThinkingLevels.legacyBudgetTokens(s.thinkingLevel)
                        settingsManager.saveThinkingLevel(ThinkingLevels.normalize(s.thinkingLevel))
                        settingsManager.saveThinkingBudgetEnabled(s.thinkingBudgetEnabled || legacyBudgetTokens != null)
                        settingsManager.saveThinkingBudgetTokens(s.thinkingBudgetTokens ?: legacyBudgetTokens ?: ThinkingLevels.DefaultBudgetTokens)
                        settingsManager.saveAutoCacheEnabled(s.autoCacheEnabled)
                        for ((provider, url) in s.providerBaseUrls) {
                            settingsManager.saveProviderBaseUrl(provider, url)
                        }
                        settingsManager.saveTitleGenerationEnabled(s.titleGenerationEnabled)
                        s.titleGenerationModel?.let { settingsManager.saveTitleGenerationModel(it) }
                        s.titleGenerationPrompt?.let { settingsManager.saveTitleGenerationPrompt(it) }
                        settingsManager.saveAccessPastConversations(s.accessPastConversations)
                        settingsManager.saveAccessSavedMemories(s.accessSavedMemories)
                        settingsManager.saveAccessActiveMemory(s.accessActiveMemory)
                        settingsManager.saveRagSearchEnabled(s.ragSearchEnabled)
                        settingsManager.saveModelSearchMethod(s.modelSearchMethod)
                        settingsManager.saveManualSearchMethod(s.manualSearchMethod)
                        // Skip embedding models — local GGUF/index, don't transfer across devices
                        settingsManager.saveCustomProviders(s.customProviders)
                        for ((provider, models) in s.manualModels) {
                            settingsManager.saveManualModelsForProvider(provider, models)
                        }
                        settingsManager.saveAppLanguage(s.appLanguage)
                        settingsManager.saveWebSearchEnabled(s.webSearchEnabled)
                        settingsManager.saveWebSearchProvider(s.webSearchProvider)
                        settingsManager.saveWebSearchBaseUrl(s.webSearchBaseUrl)
                        settingsManager.saveRagThreshold(s.ragThreshold)
                        settingsManager.saveShellEnabled(s.shellEnabled)
                        settingsManager.saveShellDevices(s.shellDevices)
                        // Skip local chat models — GGUF files don't exist on this device
                        s.activeSystemPromptId?.let { settingsManager.setActiveSystemPromptId(it) }
                        settingsImported = true
                    }

                    // Restore extra settings if present (hoisted — independent of settings.json)
                    archive["extra_settings.json"]?.decodeToString()?.let { json ->
                        try {
                            val obj = Json.parseToJsonElement(json).jsonObject
                            ExportExtraSettings.restoreFromJsonObject(obj, settingsManager)
                        } catch (_: Exception) { /* older exports may not have extra_settings.json */ }
                    }

                    // Restore custom font file
                    for (path in archive.names()) {
                        if (!path.startsWith("custom_font/")) continue
                        val bytes = archive.bytes(path) ?: continue
                        val fileName = path.removePrefix("custom_font/")
                        val fontFile = java.io.File(context.filesDir, "custom_font_$fileName")
                        fontFile.writeBytes(bytes)
                        // Update the font path to point to the restored file
                        settingsManager.saveCustomFontPath(fontFile.absolutePath)
                        // Re-read font name from the restored file
                        try {
                            val name = com.orangeisland.app.util.readFontName(fontFile)
                            settingsManager.saveCustomFontName(name)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    errors.add("Settings: ${e.localizedMessage ?: "Unknown error"}")
                }
                step()
            }

            // API Keys
            val keysDecision = decisions[DataExporter.ExportCategory.API_KEYS]
            if (keysDecision != null && keysDecision != ImportStrategy.SKIP) {
                try {
                    archive["api_keys.json"]?.decodeToString()?.let { json ->
                        val data = importJson.decodeFromString<ExportApiKeys>(json)
                        if (keysDecision == ImportStrategy.REPLACE) {
                            settingsManager.saveApiKeys(data.apiKeys)
                            data.webSearchApiKeys.forEach { (provider, key) ->
                                settingsManager.saveWebSearchApiKey(provider, key)
                            }
                            data.shellApiKeys.forEach { (name, key) ->
                                val devices = settingsManager.shellDevices.first().toMutableList()
                                val idx = devices.indexOfFirst { it.name == name }
                                if (idx >= 0) {
                                    devices[idx] = devices[idx].copy(apiKey = key)
                                } else {
                                    devices.add(ShellDeviceConfig(name = name, apiKey = key))
                                }
                                settingsManager.saveShellDevices(devices)
                            }
                        } else {
                            // MERGE: add non-duplicate keys
                            val existing = settingsManager.apiKeys.first().toMutableList()
                            val existingProviders = existing.map { it.provider to it.key }.toSet()
                            for (key in data.apiKeys) {
                                if ((key.provider to key.key) !in existingProviders) {
                                    existing.add(key)
                                }
                            }
                            settingsManager.saveApiKeys(existing)
                            data.webSearchApiKeys.forEach { (provider, key) ->
                                val current = settingsManager.webSearchApiKeys.first()
                                if (provider !in current) {
                                    settingsManager.saveWebSearchApiKey(provider, key)
                                }
                            }
                            val currentDevices = settingsManager.shellDevices.first().toMutableList()
                            var changed = false
                            data.shellApiKeys.forEach { (name, key) ->
                                val idx = currentDevices.indexOfFirst { it.name == name }
                                if (idx >= 0 && currentDevices[idx].apiKey.isBlank()) {
                                    currentDevices[idx] = currentDevices[idx].copy(apiKey = key)
                                    changed = true
                                } else if (idx < 0) {
                                    currentDevices.add(ShellDeviceConfig(name = name, apiKey = key))
                                    changed = true
                                }
                            }
                            if (changed) settingsManager.saveShellDevices(currentDevices)
                        }
                        // Apply active key IDs
                        for ((provider, id) in data.activeApiKeyIds) {
                            settingsManager.setActiveApiKeyId(provider, id)
                        }
                        apiKeysImported = true
                    }
                } catch (e: Exception) {
                    errors.add("API keys: ${e.localizedMessage ?: "Unknown error"}")
                }
                step()
            }

            archive.close()
            onProgress(1f)
            ImportResult(
                conversationsImported = conversationsImported,
                memoriesImported = memoriesImported,
                systemPromptsImported = systemPromptsImported,
                settingsImported = settingsImported,
                apiKeysImported = apiKeysImported,
                errors = errors
            )
        }
    }

    // Internal data classes for parsing export files
    @Serializable
    private data class ExportConversations(
        val conversations: List<ExportChatEntity>,
        val messages: List<ExportMessageEntity>
    )

    @Serializable
    private data class ExportChatEntity(
        val id: String,
        val title: String,
        val lastUpdated: Long,
        val selectedBranchesJson: String? = null,
        val systemPromptId: String? = null,
        val modelId: String? = null,
        // Optional so older exports (pre-projects) import as null = ungrouped.
        val projectId: String? = null
    )

    @Serializable
    private data class ExportProjects(
        val projects: List<ExportProject>
    )

    @Serializable
    private data class ExportProject(
        val id: String,
        val name: String,
        val sortOrder: Int = 0,
        val systemPromptId: String? = null,
        val modelId: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    @Serializable
    private data class ExportMessageEntity(
        val id: String,
        val conversationId: String,
        val parentId: String? = null,
        val text: String,
        val images: List<String> = emptyList(),
        val thoughts: String? = null,
        val thoughtTitle: String? = null,
        val tokenCount: Int = 0,
        val status: String = "SUCCESS",
        val participant: String = "MODEL",
        val timestamp: Long,
        val thoughtTimeMs: Long? = null,
        val modelName: String? = null,
        val toolCallJson: String? = null,
        val attachmentMeta: String? = null
    )

    @Serializable
    private data class ExportSettings(
        val selectedModel: String = "",
        val availableModels: Map<String, List<String>> = emptyMap(),
        val enabledModels: Set<String> = emptySet(),
        val modelAliases: Map<String, String> = emptyMap(),
        val maxContextWindow: Int = 20,
        val visualizeContextRollout: Boolean = false,
        val codeExecutionEnabled: Boolean = false,
        val googleSearchEnabled: Boolean = false,
        val thinkingEnabled: Boolean = true,
        val thinkingLevel: String = "medium",
        val thinkingBudgetEnabled: Boolean = false,
        val thinkingBudgetTokens: Int? = null,
        val autoCacheEnabled: Boolean = true,
        val providerBaseUrls: Map<String, String> = emptyMap(),
        val titleGenerationEnabled: Boolean = true,
        val titleGenerationModel: String? = null,
        val titleGenerationPrompt: String? = null,
        val accessPastConversations: Boolean = true,
        val accessSavedMemories: Boolean = true,
        val accessActiveMemory: Boolean = true,
        val ragSearchEnabled: Boolean = false,
        val modelSearchMethod: String = "keyword",
        val manualSearchMethod: String = "keyword",
        val embeddingModels: List<EmbeddingModelConfig> = emptyList(),
        val activeEmbeddingModelId: String = "",
        val appLanguage: String = "system",
        val webSearchEnabled: Boolean = false,
        val webSearchProvider: String = "brave",
        val webSearchBaseUrl: String = "",
        val ragThreshold: Float = 0.5f,
        val shellEnabled: Boolean = false,
        val shellDevices: List<ShellDeviceConfig> = emptyList(),
        val customProviders: List<CustomProviderConfig> = emptyList(),
        val manualModels: Map<String, List<String>> = emptyMap(),
        val localChatModels: List<LocalChatModelConfig> = emptyList(),
        @SerialName("active_system_prompt_id") val activeSystemPromptId: String? = null
    )

    @Serializable
    private data class ExportApiKeys(
        val apiKeys: List<ApiKeyEntry> = emptyList(),
        val activeApiKeyIds: Map<String, String> = emptyMap(),
        val webSearchApiKeys: Map<String, String> = emptyMap(),
        val shellApiKeys: Map<String, String> = emptyMap()
    )
}
