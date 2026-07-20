package com.orangeisland.app.data

import com.orangeisland.app.model.AttachmentItem
import com.orangeisland.app.model.AttachmentMeta
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.zip.ZipInputStream
import java.util.Locale

class ClaudeChatImporter {

    @Serializable
    data class ClaudeConversation(
        val uuid: String,
        val name: String = "",
        val summary: String = "",
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("updated_at") val updatedAt: String = "",
        @SerialName("chat_messages") val chatMessages: List<ClaudeMessage> = emptyList()
    )

    @Serializable
    data class ClaudeMessage(
        val uuid: String,
        val text: String = "",
        val sender: String = "",
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("parent_message_uuid") val parentMessageUuid: String? = null,
        val attachments: List<ClaudeAttachment> = emptyList(),
        val files: List<ClaudeFile> = emptyList(),
        val content: List<ClaudeContent> = emptyList()
    )

    @Serializable
    data class ClaudeContent(
        val type: String = "",
        val text: String = "",
        val thinking: String = "",
        val citations: List<ClaudeCitation> = emptyList()
    )

    @Serializable
    data class ClaudeCitation(
        val uuid: String = "",
        val title: String? = null
    )

    @Serializable
    data class ClaudeAttachment(
        @SerialName("file_name") val fileName: String = "",
        @SerialName("file_size") val fileSize: Long = 0,
        @SerialName("file_type") val fileType: String = "",
        @SerialName("extracted_content") val extractedContent: String? = null
    )

    @Serializable
    data class ClaudeFile(
        @SerialName("file_uuid") val fileUuid: String = "",
        @SerialName("file_name") val fileName: String = "",
        @SerialName("file_size") val fileSize: Long = 0,
        @SerialName("file_type") val fileType: String = "",
        @SerialName("extracted_content") val extractedContent: String? = null
    )

    private fun toAttachmentItem(attachment: Any): AttachmentItem {
        return when (attachment) {
            is ClaudeAttachment -> toAttachmentItem(attachment)
            is ClaudeFile -> toAttachmentItem(attachment)
            else -> AttachmentItem(type = "file", fileName = null, textContent = null, mimeType = null)
        }
    }

    private fun toAttachmentItem(att: ClaudeAttachment): AttachmentItem {
        val isImage = att.fileType.startsWith("image/") || isImageFile(att.fileName)
        val isText = !isImage && (isTextFile(att.fileName) || att.fileType.contains("text"))
        return AttachmentItem(
            type = if (isImage) "image" else "file",
            fileName = att.fileName,
            textContent = if (isText) att.extractedContent else null,
            mimeType = att.fileType
        )
    }

    private fun toAttachmentItem(att: ClaudeFile): AttachmentItem {
        val isImage = att.fileType.startsWith("image/") || isImageFile(att.fileName)
        val isText = !isImage && (isTextFile(att.fileName) || att.fileType.contains("text"))
        return AttachmentItem(
            type = if (isImage) "image" else "file",
            fileName = att.fileName,
            textContent = if (isText) att.extractedContent else null,
            mimeType = att.fileType
        )
    }

    data class ConversationSummary(
        val uuid: String,
        val title: String,
        val messageCount: Int
    )

    data class ImportPreview(
        val conversations: List<ConversationSummary> = emptyList(),
        val conversationCount: Int,
        val totalMessageCount: Int,
        val humanMessageCount: Int,
        val assistantMessageCount: Int,
        val hasAttachments: Boolean
    )

    data class ImportResult(
        val conversationsImported: Int = 0,
        val messagesImported: Int = 0,
        val errors: List<String> = emptyList()
    )

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Streams and parses a Claude export without buffering the whole file.
     * [openStream] is a factory so a ZIP archive can be probed and then have
     * its entries decoded from fresh reads.
     *
     * Handles a raw JSON document (either the wrapped `{"conversations": [...]}`
     * form or a bare `[...]` array) as well as a Claude data-export ZIP, picking
     * the entry whose name mentions "conversation" and otherwise falling back to
     * the first entry that yields conversations.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun extractAndParse(openStream: () -> InputStream): Result<ClaudeConversations> = runCatching {
        val zipped = BufferedInputStream(openStream()).use { isZip(it) }
        if (zipped) {
            ZipInputStream(BufferedInputStream(openStream())).use { zipInput ->
                var fallback: ClaudeConversations? = null
                var entry = zipInput.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".json")) {
                        val convs = decodeConversations { NonClosingInputStream(zipInput) }.getOrNull()
                        if (convs != null && convs.conversations.isNotEmpty()) {
                            if (entry.name.contains("conversation", ignoreCase = true)) {
                                return@runCatching convs
                            }
                            if (fallback == null) fallback = convs
                        }
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
                fallback ?: throw Exception("No conversation data found in ZIP archive")
            }
        } else {
            decodeConversations { BufferedInputStream(openStream()) }.getOrThrow()
        }
    }

    /**
     * Decodes a single conversations document from [openContent], selecting the
     * wrapped-object vs bare-array shape by peeking the first character so the
     * stream is parsed in a single pass.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun decodeConversations(openContent: () -> InputStream): Result<ClaudeConversations> = runCatching {
        BufferedInputStream(openContent()).use { input ->
            when (peekFirstNonWhitespace(input)) {
                '{' -> jsonParser.decodeFromStream<ClaudeConversations>(input)
                '[' -> ClaudeConversations(jsonParser.decodeFromStream<List<ClaudeConversation>>(input))
                else -> throw Exception("Unrecognized JSON format")
            }
        }
    }

    fun preview(conversations: ClaudeConversations): ImportPreview {
        val messages = conversations.conversations.flatMap { it.chatMessages }
        val hasAttachments = conversations.conversations.any { conv ->
            conv.chatMessages.any { msg ->
                msg.attachments.isNotEmpty() || msg.files.isNotEmpty()
            }
        }
        val summaries = conversations.conversations
            .sortedByDescending { conv -> iso8601ToMillis(conv.updatedAt) }
            .map { conv ->
                ConversationSummary(
                    uuid = conv.uuid,
                    title = conv.name.ifEmpty { conv.summary.take(50).ifEmpty { "Untitled" } },
                    messageCount = conv.chatMessages.size
                )
            }
        return ImportPreview(
            conversations = summaries,
            conversationCount = conversations.conversations.size,
            totalMessageCount = messages.size,
            humanMessageCount = messages.count { it.sender == "human" },
            assistantMessageCount = messages.count { it.sender == "assistant" },
            hasAttachments = hasAttachments
        )
    }

    fun toImportFormat(conversations: ClaudeConversations, selectedIds: Set<String>? = null): ImportConversations {
        val chatEntities = mutableListOf<ImportChatEntity>()
        val messageEntities = mutableListOf<ImportMessageEntity>()

        val filteredConversations = if (selectedIds != null) {
            conversations.conversations.filter { it.uuid in selectedIds }
        } else {
            conversations.conversations
        }

        for (conv in filteredConversations) {
            chatEntities.add(
                ImportChatEntity(
                    id = conv.uuid,
                    title = conv.name.ifEmpty { conv.summary.take(50).ifEmpty { "Untitled" } },
                    lastUpdated = iso8601ToMillis(conv.updatedAt),
                    selectedBranchesJson = null,
                    systemPromptId = null,
                    modelId = null
                )
            )

            for (msg in conv.chatMessages) {
                // Always prefer content blocks over raw text to properly exclude thinking
                val mergedText = if (msg.content.isNotEmpty()) {
                    msg.content.filter { it.type != "thinking" }.map { it.text }.joinToString("")
                } else {
                    msg.text
                }

                // Claude export does not embed image binary data; images are metadata-only in the files array

                val attachmentMeta = buildAttachmentMeta(conv.uuid, msg)

                val parentId = if (msg.parentMessageUuid != null &&
                    msg.parentMessageUuid != "00000000-0000-4000-8000-000000000000"
                ) msg.parentMessageUuid else null

                val thoughts = msg.content.find { it.type == "thinking" }?.thinking

                messageEntities.add(
                    ImportMessageEntity(
                        id = msg.uuid,
                        conversationId = conv.uuid,
                        parentId = parentId,
                        text = mergedText,
                        images = emptyList(),
                        thoughts = thoughts,
                        thoughtTitle = null,
                        tokenCount = 0,
                        status = "SUCCESS",
                        participant = if (msg.sender == "human") "USER" else "MODEL",
                        timestamp = iso8601ToMillis(msg.createdAt),
                        thoughtTimeMs = null,
                        modelName = null,
                        toolCallJson = null,
                        attachmentMeta = attachmentMeta
                    )
                )
            }
        }

        return ImportConversations(chatEntities, messageEntities)
    }

    private fun buildAttachmentMeta(conversationId: String, msg: ClaudeMessage): String? {
        val allAttachments = msg.attachments + msg.files
        if (allAttachments.isEmpty()) return null

        val attachmentItems = allAttachments.map { attachment -> toAttachmentItem(attachment) }

        val meta = AttachmentMeta(items = attachmentItems)
        return kotlinx.serialization.json.Json.encodeToString(AttachmentMeta.serializer(), meta)
    }

    private fun isTextFile(fileName: String): Boolean {
        val textExtensions = setOf(".txt", ".json", ".cpp", ".py", ".js", ".html", ".xml", ".md", ".java", ".kt", ".rs", ".go", ".ts", ".c", ".h", ".yaml", ".yml", ".toml", ".cfg", ".ini", ".properties")
        return fileName.lowercase().let { name ->
            textExtensions.any { name.endsWith(it) }
        }
    }

    private fun isImageFile(fileName: String): Boolean {
        val imageExtensions = setOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".heic", ".heif")
        return fileName.lowercase().let { name ->
            imageExtensions.any { name.endsWith(it) }
        }
    }

    private fun iso8601ToMillis(iso8601: String): Long {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            dateFormat.parse(iso8601)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    @Serializable
    data class ClaudeConversations(
        val conversations: List<ClaudeConversation>
    )

    @Serializable
    data class ImportChatEntity(
        val id: String,
        val title: String,
        val lastUpdated: Long,
        val selectedBranchesJson: String? = null,
        val systemPromptId: String? = null,
        val modelId: String? = null
    )

    @Serializable
    data class ImportMessageEntity(
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
    data class ImportConversations(
        val conversations: List<ImportChatEntity>,
        val messages: List<ImportMessageEntity>
    )

  }
