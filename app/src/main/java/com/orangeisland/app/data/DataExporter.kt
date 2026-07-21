package com.orangeisland.app.data

import android.content.Context
import android.net.Uri
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.data.local.ChatEntity
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.local.ProjectEntity
import com.orangeisland.app.model.AttachmentMeta
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DataExporter(
    private val context: Context,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager,
    private val memoryManager: MemoryManager
) {
    enum class ExportCategory(val manifestKey: String) {
        CONVERSATIONS("conversations"),
        MEMORIES("memories"),
        SYSTEM_PROMPTS("system_prompts"),
        SETTINGS("settings"),
        API_KEYS("api_keys");

        companion object {
            fun fromManifestKey(key: String): ExportCategory? =
                entries.find { it.manifestKey == key }
        }
    }

    @Serializable
    private data class ExportManifest(
        @SerialName("orangeisland_export_version") val version: Int = 1,
        @SerialName("app_version") val appVersion: String,
        @SerialName("exported_at") val exportedAt: String,
        val categories: List<String>,
        @SerialName("has_api_keys") val hasApiKeys: Boolean = false
    )

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
        // null = ungrouped. Optional so older export files (pre-projects) still import.
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
        val selectedModel: String,
        val availableModels: Map<String, List<String>>,
        val enabledModels: Set<String>,
        val modelAliases: Map<String, String>,
        val maxContextWindow: Int,
        val visualizeContextRollout: Boolean,
        val codeExecutionEnabled: Boolean,
        val googleSearchEnabled: Boolean,
        val thinkingEnabled: Boolean,
        val thinkingLevel: String,
        val thinkingBudgetEnabled: Boolean,
        val thinkingBudgetTokens: Int,
        val autoCacheEnabled: Boolean,
        val providerBaseUrls: Map<String, String>,
        val titleGenerationEnabled: Boolean,
        val titleGenerationModel: String?,
        val titleGenerationPrompt: String? = null,
        val accessPastConversations: Boolean,
        val accessSavedMemories: Boolean,
        val accessActiveMemory: Boolean,
        val ragSearchEnabled: Boolean,
        val modelSearchMethod: String,
        val manualSearchMethod: String,
        val embeddingModels: List<EmbeddingModelConfig>,
        val activeEmbeddingModelId: String,
        val appLanguage: String,
        val webSearchEnabled: Boolean,
        val webSearchProvider: String,
        val webSearchBaseUrl: String,
        val ragThreshold: Float,
        val shellEnabled: Boolean = false,
        val shellDevices: List<ShellDeviceConfig> = emptyList(),
        val customProviders: List<CustomProviderConfig> = emptyList(),
        val manualModels: Map<String, List<String>> = emptyMap(),
        val localChatModels: List<LocalChatModelConfig>,
        @SerialName("active_system_prompt_id") val activeSystemPromptId: String?
    )

    @Serializable
    private data class ExportApiKeys(
        val apiKeys: List<ApiKeyEntry>,
        val activeApiKeyIds: Map<String, String>,
        val webSearchApiKeys: Map<String, String>,
        val shellApiKeys: Map<String, String> = emptyMap()
    )

    data class ExportResult(
        val imagesExported: Int = 0
    )

    private fun openImageStream(imgUri: String): java.io.InputStream? {
        val uri = Uri.parse(imgUri)
        // Handle content:// and file:// URIs
        if (uri.scheme == "content" || uri.scheme == "file") {
            return try { context.contentResolver.openInputStream(uri) } catch (_: Exception) { null }
        }
        // Handle bare file paths (from processImages)
        val file = java.io.File(imgUri)
        if (file.exists()) return try { file.inputStream() } catch (_: Exception) { null }
        return null
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun export(
        uri: Uri,
        categories: Set<ExportCategory>,
        includeApiKeys: Boolean,
        onProgress: (Float) -> Unit = {}
    ): ExportResult {
        val appInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = appInfo.versionName ?: "unknown"
        val exportedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .format(java.util.Date())

        val manifest = ExportManifest(
            appVersion = appVersion,
            exportedAt = exportedAt,
            categories = categories.map { it.manifestKey },
            hasApiKeys = includeApiKeys && categories.contains(ExportCategory.API_KEYS)
        )

        var imagesExportedTotal = 0
        val totalSteps = categories.size + 1 // +1 for manifest
        var completed = 0
        fun step() { completed++; onProgress(completed.toFloat() / totalSteps) }

        context.contentResolver.openOutputStream(uri)?.use { raw ->
            val zip = ZipOutputStream(BufferedOutputStream(raw))

            // Manifest
            zip.putNextEntry(ZipEntry("manifest.json"))
            Json.encodeToStream(manifest, zip)
            zip.closeEntry()
            step()

            // Conversations
            if (ExportCategory.CONVERSATIONS in categories) {
                val allMessages = chatDao.getAllMessagesList()
                val imageMap = mutableMapOf<String, List<String>>() // messageId -> list of image URIs to keep

                // Export image files alongside the JSON
                for (msg in allMessages) {
                    if (msg.images.isEmpty()) continue
                    val surviving = mutableListOf<String>()
                    for ((idx, imgUri) in msg.images.withIndex()) {
                        try {
                            val inStream = openImageStream(imgUri)
                            val bytes = inStream?.readBytes()
                            inStream?.close()
                            if (bytes != null && bytes.isNotEmpty()) {
                                zip.putNextEntry(ZipEntry("images/${msg.id}/$idx"))
                                zip.write(bytes)
                                zip.closeEntry()
                                surviving.add(imgUri)
                            }
                        } catch (_: Exception) {
                            // File not accessible — drop from export
                        }
                    }
                    imagesExportedTotal += surviving.size
                    if (surviving.isNotEmpty()) {
                        imageMap[msg.id] = surviving
                    }

                    // Export video files referenced by attachmentMeta
                    val meta = try {
                        msg.attachmentMeta?.let { Json.decodeFromString<AttachmentMeta>(it) }
                    } catch (_: Exception) { null }
                    if (meta != null) {
                        for (item in meta.items) {
                            if (item.type != "video" || item.originalUri.isNullOrBlank()) continue
                            val uri = item.originalUri
                            // Handle file:// URIs (local copies)
                            if (uri.startsWith("file://")) {
                                val filePath = uri.removePrefix("file://")
                                val file = java.io.File(filePath)
                                if (file.exists()) {
                                    try {
                                        val bytes = file.readBytes()
                                        if (bytes.isNotEmpty()) {
                                            zip.putNextEntry(ZipEntry("videos/${msg.id}/${item.imageIndex ?: 0}"))
                                            zip.write(bytes)
                                            zip.closeEntry()
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }

                val conversations = chatDao.getAllConversationsList().map { c ->
                    ExportChatEntity(c.id, c.title, c.lastUpdated, c.selectedBranchesJson, c.systemPromptId, c.modelId, c.projectId)
                }
                val messages = allMessages.map { m ->
                    // Only include images that were successfully exported
                    val exportedImages = imageMap[m.id] ?: emptyList()
                    ExportMessageEntity(m.id, m.conversationId, m.parentId, m.text, exportedImages,
                        m.thoughts, m.thoughtTitle, m.tokenCount, m.status.name, m.participant.name,
                        m.timestamp, m.thoughtTimeMs, m.modelName, m.toolCallJson, m.attachmentMeta)
                }
                zip.putNextEntry(ZipEntry("conversations.json"))
                Json.encodeToStream(ExportConversations(conversations, messages), zip)
                zip.closeEntry()

                // Projects — written as a sibling entry so older importers that don't know
                // about it simply skip the unknown entry, and we can import without projects.
                val projects = chatDao.getAllProjectsList().map { p ->
                    ExportProject(p.id, p.name, p.sortOrder, p.systemPromptId, p.modelId, p.createdAt)
                }
                zip.putNextEntry(ZipEntry("projects.json"))
                Json.encodeToStream(ExportProjects(projects), zip)
                zip.closeEntry()
                step()
            }

            // Memories
            if (ExportCategory.MEMORIES in categories) {
                val activeMemory = memoryManager.getActiveMemory()
                if (activeMemory.isNotEmpty()) {
                    zip.putNextEntry(ZipEntry("memories/active_memory.md"))
                    zip.write(activeMemory.toByteArray())
                    zip.closeEntry()
                }
                for (file in memoryManager.listFiles()) {
                    val content = memoryManager.readFile(file.name)
                    zip.putNextEntry(ZipEntry("memories/memory_db/${file.name}"))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
                val metaJson = memoryManager.getMetaJson()
                if (metaJson != "{}") {
                    zip.putNextEntry(ZipEntry("memories/memory_db/memory_meta.json"))
                    zip.write(metaJson.toByteArray())
                    zip.closeEntry()
                }
                // Project-private memory bundles — each project's files live under a
                // dedicated subdirectory so import can restore them to the right scope.
                // Only projects that actually exist on disk are walked.
                for (projectId in memoryManager.listProjectIds()) {
                    for ((relName, file) in memoryManager.projectMemoryFilesForExport(projectId)) {
                        if (!file.exists()) continue
                        try {
                            val bytes = file.readBytes()
                            if (bytes.isEmpty()) continue
                            zip.putNextEntry(ZipEntry("memories/projects/$projectId/$relName"))
                            zip.write(bytes)
                            zip.closeEntry()
                        } catch (_: Exception) {
                            // Skip unreadable files rather than failing the whole export.
                        }
                    }
                }
                step()
            }

            // System Prompts
            if (ExportCategory.SYSTEM_PROMPTS in categories) {
                val prompts = settingsManager.systemPrompts.first()
                zip.putNextEntry(ZipEntry("system_prompts.json"))
                Json.encodeToStream(prompts, zip)
                zip.closeEntry()
                step()
            }

            // Settings
            if (ExportCategory.SETTINGS in categories) {
                val settings = ExportSettings(
                    selectedModel = settingsManager.selectedModel.first(),
                    availableModels = settingsManager.availableModels.first(),
                    enabledModels = settingsManager.enabledModels.first(),
                    modelAliases = settingsManager.modelAliases.first(),
                    maxContextWindow = settingsManager.maxContextWindow.first(),
                    visualizeContextRollout = settingsManager.visualizeContextRollout.first(),
                    codeExecutionEnabled = settingsManager.codeExecutionEnabled.first(),
                    googleSearchEnabled = settingsManager.googleSearchEnabled.first(),
                    thinkingEnabled = settingsManager.thinkingEnabled.first(),
                    thinkingLevel = settingsManager.thinkingLevel.first(),
                    thinkingBudgetEnabled = settingsManager.thinkingBudgetEnabled.first(),
                    thinkingBudgetTokens = settingsManager.thinkingBudgetTokens.first(),
                    autoCacheEnabled = settingsManager.autoCacheEnabled.first(),
                    providerBaseUrls = settingsManager.providerBaseUrls.first(),
                    titleGenerationEnabled = settingsManager.titleGenerationEnabled.first(),
                    titleGenerationModel = settingsManager.titleGenerationModel.first(),
                    titleGenerationPrompt = settingsManager.titleGenerationPrompt.first(),
                    accessPastConversations = settingsManager.accessPastConversations.first(),
                    accessSavedMemories = settingsManager.accessSavedMemories.first(),
                    accessActiveMemory = settingsManager.accessActiveMemory.first(),
                    ragSearchEnabled = settingsManager.ragSearchEnabled.first(),
                    modelSearchMethod = settingsManager.modelSearchMethod.first(),
                    manualSearchMethod = settingsManager.manualSearchMethod.first(),
                    embeddingModels = settingsManager.embeddingModels.first().map { it.copy(localFilePath = "") },
                    activeEmbeddingModelId = "", // cleared — embedding models are local GGUF, don't transfer
                    appLanguage = settingsManager.appLanguage.first(),
                    webSearchEnabled = settingsManager.webSearchEnabled.first(),
                    webSearchProvider = settingsManager.webSearchProvider.first(),
                    webSearchBaseUrl = settingsManager.webSearchBaseUrl.first(),
                    ragThreshold = settingsManager.ragThreshold.first(),
                    shellEnabled = settingsManager.shellEnabled.first(),
                    shellDevices = settingsManager.shellDevices.first().map { d ->
                        if (includeApiKeys) d else d.copy(apiKey = "")
                    },
                    customProviders = settingsManager.customProviders.first(),
                    manualModels = settingsManager.manualModels.first(),
                    localChatModels = settingsManager.localChatModels.first().map { it.copy(localFilePath = "") },
                    activeSystemPromptId = settingsManager.activeSystemPromptId.first()
                )
                zip.putNextEntry(ZipEntry("settings.json"))
                Json.encodeToStream(settings, zip)
                zip.closeEntry()
                step()

                // Extra settings (separate file to keep data class size manageable)
                val extra = ExportExtraSettings.toJsonObject(settingsManager, includeApiKeys)
                zip.putNextEntry(ZipEntry("extra_settings.json"))
                Json.encodeToStream(extra, zip)
                zip.closeEntry()
            }

            // API Keys (opt-in)
            if (includeApiKeys && ExportCategory.API_KEYS in categories) {
                val keys = ExportApiKeys(
                    apiKeys = settingsManager.apiKeys.first(),
                    activeApiKeyIds = settingsManager.activeApiKeyIds.first(),
                    webSearchApiKeys = settingsManager.webSearchApiKeys.first(),
                    shellApiKeys = settingsManager.shellDevices.first()
                        .filter { it.apiKey.isNotBlank() }
                        .associate { it.name to it.apiKey }
                )
                zip.putNextEntry(ZipEntry("api_keys.json"))
                Json.encodeToStream(keys, zip)
                zip.closeEntry()
                step()
            }

            // ── Custom font file ──
            val fontPath = settingsManager.customFontPath.first()
            if (fontPath.isNotBlank()) {
                val fontFile = File(fontPath)
                if (fontFile.exists()) {
                    zip.putNextEntry(ZipEntry("custom_font/${fontFile.name}"))
                    fontFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            zip.finish()
            zip.flush()
        }

        onProgress(1f)
        return ExportResult(imagesExported = imagesExportedTotal)
    }
}
