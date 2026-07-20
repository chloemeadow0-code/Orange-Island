package com.orangeisland.app.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class GptChatImporter {

    @Serializable
    data class GptConversation(
        @SerialName("conversation_id") val conversationId: String = "",
        val title: String = "",
        @SerialName("create_time") val createTime: Double = 0.0,
        @SerialName("update_time") val updateTime: Double = 0.0,
        @SerialName("current_node") val currentNode: String? = null,
        val mapping: Map<String, GptMappingNode> = emptyMap()
    )

    @Serializable
    data class GptMappingNode(
        val id: String = "",
        val message: GptMessage? = null,
        val parent: String? = null
    )

    @Serializable
    data class GptMessage(
        val id: String = "",
        val author: GptAuthor? = null,
        val content: GptContent? = null,
        @SerialName("create_time") val createTime: Double? = null,
        val metadata: GptMetadata? = null
    )

    @Serializable
    data class GptAuthor(
        val role: String = "",
        val name: String? = null
    )

    @Serializable
    data class GptContent(
        @SerialName("content_type") val contentType: String = "",
        val parts: List<JsonElement>? = null,
        val text: String? = null,
        val result: String? = null,
        @SerialName("content") val reasoningText: String? = null,
        val language: String? = null,
        val thoughts: List<GptThought>? = null
    )

    @Serializable
    data class GptThought(
        val content: String = "",
        val summary: String = ""
    )

    @Serializable
    data class GptMetadata(
        @SerialName("model_slug") val modelSlug: String? = null,
        @SerialName("parent_id") val parentId: String? = null,
        @SerialName("is_complete") val isComplete: Boolean? = null,
        @SerialName("finish_details") val finishDetails: GptFinishDetails? = null,
        @SerialName("finished_duration_sec") val finishedDurationSec: Int? = null
    )

    @Serializable
    data class GptFinishDetails(
        val type: String = ""
    )

    data class ConversationSummary(
        val uuid: String,
        val title: String,
        val messageCount: Int
    )

    data class ImportPreview(
        val conversations: List<ConversationSummary> = emptyList(),
        val conversationCount: Int,
        val totalMessageCount: Int,
        val userMessageCount: Int,
        val assistantMessageCount: Int,
        val hasAttachments: Boolean
    )

    data class ImportResult(
        val conversationsImported: Int = 0,
        val messagesImported: Int = 0,
        val thoughtsMessageCount: Int = 0,
        val errors: List<String> = emptyList()
    )

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Streams and parses a ChatGPT export without ever holding the whole file
     * in memory. [openStream] is a factory so the source can be re-read when a
     * ZIP archive must be probed before its entries are decoded.
     *
     * Accepts either a raw `conversations.json` array or a ChatGPT ZIP export
     * containing `conversations.json` (optionally split into `conversations-N.json`).
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun extractAndParse(openStream: () -> InputStream): Result<List<GptConversation>> {
        return try {
            BufferedInputStream(openStream()).use { input ->
                if (isZip(input)) {
                    val zipInput = ZipInputStream(input)
                    val allConversations = mutableListOf<GptConversation>()
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory &&
                            (entry.name == "conversations.json" ||
                                entry.name.matches(Regex("conversations-\\d+\\.json")))
                        ) {
                            allConversations += jsonParser.decodeFromStream<List<GptConversation>>(
                                NonClosingInputStream(zipInput)
                            )
                        }
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                    if (allConversations.isNotEmpty()) Result.success(allConversations)
                    else Result.failure(Exception("No conversation data found in ZIP archive"))
                } else {
                    val list = jsonParser.decodeFromStream<List<GptConversation>>(input)
                    if (list.isNotEmpty()) Result.success(list)
                    else Result.failure(Exception("No conversations found in JSON"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun preview(conversations: List<GptConversation>): ImportPreview {
        val summaries = conversations
            .sortedByDescending { it.updateTime }
            .map { conv ->
                val messages = getMessageChain(conv)
                ConversationSummary(
                    uuid = conv.conversationId,
                    title = conv.title.ifEmpty { "Untitled" },
                    messageCount = messages.size
                )
            }
        val allMessages = conversations.flatMap { getMessageChain(it) }
        return ImportPreview(
            conversations = summaries,
            conversationCount = conversations.size,
            totalMessageCount = allMessages.size,
            userMessageCount = allMessages.count { it.author?.role == "user" },
            assistantMessageCount = allMessages.count { it.author?.role == "assistant" },
            hasAttachments = allMessages.any { msg ->
                msg.content?.contentType == "multimodal_text" ||
                msg.content?.parts?.any { part ->
                    part is kotlinx.serialization.json.JsonObject &&
                    part.jsonObject["content_type"]?.jsonPrimitive?.content == "image_asset_pointer"
                } == true
            }
        )
    }

    fun toImportFormat(conversations: List<GptConversation>, selectedIds: Set<String>? = null): ClaudeChatImporter.ImportConversations {
        val chatEntities = mutableListOf<ClaudeChatImporter.ImportChatEntity>()
        val messageEntities = mutableListOf<ClaudeChatImporter.ImportMessageEntity>()

        val filtered = if (selectedIds != null) {
            conversations.filter { it.conversationId in selectedIds }
        } else {
            conversations
        }

        for (conv in filtered) {
            val messages = getMessageChain(conv)
            if (messages.isEmpty()) continue

            // Build raw parent map from mapping tree (node ID -> parent node ID)
            val rawParentMap = mutableMapOf<String, String?>()
            for ((nodeId, node) in conv.mapping) {
                if (node.message != null && node.parent != null && node.parent != "client-created-root") {
                    val parentNode = conv.mapping[node.parent]
                    if (parentNode?.message != null) {
                        rawParentMap[node.message.id] = parentNode.message.id
                    }
                }
            }

            // First pass: build intermediate message data
            data class RawMsg(
                val id: String,
                val rawParentId: String?,
                val text: String,
                val thoughts: String?,
                val thoughtTitle: String?,
                val contentType: String,
                val participant: String,
                val status: String,
                val timestamp: Long,
                val thoughtTimeMs: Long?,
                val modelName: String?,
                val authorRole: String
            )
            val rawMessages = messages.map { msg ->
                val role = msg.author?.role ?: "user"
                val participant = when (role) {
                    "assistant" -> "MODEL"
                    "tool" -> "MODEL"
                    "system" -> "MODEL"
                    else -> "USER"
                }
                val contentType = msg.content?.contentType ?: "text"
                val text = when (contentType) {
                    "text" -> msg.content?.parts?.joinToString("") { extractTextFromPart(it) } ?: ""
                    "multimodal_text" -> msg.content?.parts?.joinToString("") { extractTextFromPart(it) } ?: ""
                    "code" -> msg.content?.text ?: ""
                    "execution_output" -> msg.content?.text ?: msg.content?.result ?: ""
                    "tether_quote" -> msg.content?.text ?: msg.content?.result ?: ""
                    "tether_browsing_display" -> msg.content?.text ?: msg.content?.result ?: ""
                    "user_editable_context" -> msg.content?.parts?.joinToString("") { extractTextFromPart(it) } ?: ""
                    else -> msg.content?.parts?.joinToString("") { extractTextFromPart(it) } ?: ""
                }
                val thoughts = when (contentType) {
                    "thoughts" -> msg.content?.thoughts?.joinToString("\n\n") { it.content }
                    else -> null
                }
                val thoughtTitle = when (contentType) {
                    "thoughts" -> msg.content?.thoughts?.firstOrNull()?.summary
                    "reasoning_recap" -> msg.content?.reasoningText
                    else -> null
                }
                val thoughtTimeMs = when (contentType) {
                    "reasoning_recap" -> msg.metadata?.finishedDurationSec?.let { it * 1000L }
                    else -> null
                }
                val modelName = msg.metadata?.modelSlug?.let { "OpenAI:$it" }
                val timestamp = if (msg.createTime != null) (msg.createTime * 1000).toLong() else System.currentTimeMillis()
                val status = when {
                    msg.metadata?.isComplete == false -> "STOPPED"
                    msg.metadata?.finishDetails?.type == "stop" -> "SUCCESS"
                    msg.metadata?.finishDetails?.type != null -> "STOPPED"
                    else -> "SUCCESS"
                }
                RawMsg(msg.id, rawParentMap[msg.id], cleanCitationMarkers(text), thoughts, thoughtTitle, contentType, participant, status, timestamp, thoughtTimeMs, modelName, role)
            }

            val rawById = rawMessages.associateBy { it.id }

            // Merge pass: GPT stores thinking and tool results as separate nodes.
            // We merge them into the next non-ephemeral message so they appear inline.
            val mergeTypes = setOf("thoughts", "reasoning_recap", "execution_output", "tether_quote", "tether_browsing_display", "code")
            val removedIds = mutableSetOf<String>()
            val mergedThoughts = mutableMapOf<String, String>()
            val mergedThoughtTitle = mutableMapOf<String, String?>()
            val mergedThoughtTimeMs = mutableMapOf<String, Long>()
            val mergedTextSuffix = mutableMapOf<String, StringBuilder>()

            // Find indices of "keep" messages (not merge types, not tool role)
            val keepIndices = rawMessages.indices.filter { rawMessages[it].contentType !in mergeTypes && rawMessages[it].authorRole != "tool" }
            fun nextKeepIdx(after: Int): Int? = keepIndices.firstOrNull { it > after }

            for (i in rawMessages.indices) {
                val rm = rawMessages[i]
                if (rm.contentType !in mergeTypes && rm.authorRole != "tool") continue
                removedIds.add(rm.id)
                val targetIdx = nextKeepIdx(i)
                val target = if (targetIdx != null) rawMessages[targetIdx] else null
                when (rm.contentType) {
                    "thoughts" -> {
                        if (target != null && !rm.thoughts.isNullOrBlank()) {
                            mergedThoughts[target.id] = rm.thoughts
                            mergedThoughtTitle[target.id] = rm.thoughtTitle
                        }
                    }
                    "reasoning_recap" -> {
                        if (target != null) {
                            if (rm.thoughtTimeMs != null && rm.thoughtTimeMs > 0) {
                                mergedThoughtTimeMs[target.id] = rm.thoughtTimeMs
                            }
                            if (!rm.thoughtTitle.isNullOrBlank()) {
                                mergedThoughtTitle[target.id] = rm.thoughtTitle
                            }
                        }
                    }
                    "code" -> {
                        if (target != null && rm.text.isNotBlank()) {
                            val existing = mergedThoughts[target.id]
                            mergedThoughts[target.id] = if (existing != null) "$existing\n\n${rm.text}" else rm.text
                        }
                    }
                    "execution_output", "tether_quote", "tether_browsing_display" -> {
                        if (target != null && rm.text.isNotBlank()) {
                            val sb = mergedTextSuffix.getOrPut(target.id) { StringBuilder() }
                            if (sb.isNotEmpty()) sb.append("\n\n")
                            sb.append(rm.text)
                        }
                    }
                    else -> {
                        // tool role messages
                        if (target != null && rm.text.isNotBlank()) {
                            val sb = mergedTextSuffix.getOrPut(target.id) { StringBuilder() }
                            if (sb.isNotEmpty()) sb.append("\n\n")
                            sb.append(rm.text)
                        }
                    }
                }
            }

            // Apply fallback text for surviving messages with empty text
            val fallbackTexts = mutableMapOf<String, String>()
            for (rm in rawMessages) {
                if (rm.id in removedIds) continue
                if (rm.text.isNotBlank()) continue
                val fb = when (rm.contentType) {
                    "multimodal_text" -> "[Image]"
                    else -> null
                }
                if (fb != null) fallbackTexts[rm.id] = fb
            }

            // Skip messages with no real content (empty text + no thoughts, after merge + fallback)
            val skippedIds = mutableSetOf<String>()
            for (rm in rawMessages) {
                if (rm.id in removedIds) continue
                val effectiveText = fallbackTexts[rm.id] ?: rm.text
                val effectiveThoughts = mergedThoughts[rm.id] ?: rm.thoughts
                if (effectiveText.isBlank() && effectiveThoughts.isNullOrBlank()) {
                    skippedIds.add(rm.id)
                }
            }
            val allRemoved = removedIds + skippedIds

            // Cascade parent references through all removed messages
            fun cascadeParent(id: String?): String? {
                var pid = id
                while (pid != null && pid in allRemoved) {
                    pid = rawById[pid]?.rawParentId
                }
                return pid
            }

            // Second pass: emit only real messages
            var convMsgCount = 0
            for (rm in rawMessages) {
                if (rm.id in allRemoved) continue
                val parentId = cascadeParent(rm.rawParentId)
                val baseText = fallbackTexts[rm.id] ?: rm.text
                val suffix = mergedTextSuffix[rm.id]?.toString()
                val finalText = if (suffix != null && baseText.isNotBlank()) "$baseText\n\n$suffix"
                    else if (suffix != null) suffix
                    else baseText
                val finalThoughts = mergedThoughts[rm.id] ?: rm.thoughts
                val finalThoughtTitle = mergedThoughtTitle[rm.id] ?: rm.thoughtTitle
                val finalThoughtTimeMs = mergedThoughtTimeMs[rm.id]
                messageEntities.add(
                    ClaudeChatImporter.ImportMessageEntity(
                        id = rm.id,
                        conversationId = conv.conversationId,
                        parentId = parentId,
                        text = finalText,
                        images = emptyList(),
                        thoughts = finalThoughts,
                        thoughtTitle = finalThoughtTitle,
                        tokenCount = 0,
                        status = rm.status,
                        participant = rm.participant,
                        timestamp = rm.timestamp,
                        thoughtTimeMs = finalThoughtTimeMs,
                        modelName = rm.modelName,
                        toolCallJson = null,
                        attachmentMeta = null
                    )
                )
                convMsgCount++
            }

            if (convMsgCount > 0) {
                chatEntities.add(
                    ClaudeChatImporter.ImportChatEntity(
                        id = conv.conversationId,
                        title = conv.title.ifEmpty { "Untitled" },
                        lastUpdated = (conv.updateTime * 1000).toLong(),
                        selectedBranchesJson = null,
                        systemPromptId = null,
                        modelId = null
                    )
                )
            }
        }

        return ClaudeChatImporter.ImportConversations(chatEntities, messageEntities)
    }

    private fun getMessageChain(conv: GptConversation): List<GptMessage> {
        val mapping = conv.mapping
        if (mapping.isEmpty()) return emptyList()

        val chain = mutableListOf<GptMessage>()
        val visited = mutableSetOf<String>()
        var nodeId = conv.currentNode

        while (nodeId != null && nodeId != "client-created-root" && visited.add(nodeId)) {
            val node = mapping[nodeId] ?: break
            if (node.message != null) {
                chain.add(node.message)
            }
            nodeId = node.parent
        }

        return chain.reversed()
    }

    private fun extractTextFromPart(part: JsonElement): String {
        return try {
            when {
                part is kotlinx.serialization.json.JsonPrimitive && part.isString -> part.content
                part is kotlinx.serialization.json.JsonObject -> {
                    val obj = part
                    val partType = obj["content_type"]?.jsonPrimitive?.content ?: ""
                    when (partType) {
                        "image_asset_pointer" -> ""
                        "tether_quote", "tether_browsing_display" -> obj["text"]?.jsonPrimitive?.content ?: ""
                        else -> obj["text"]?.jsonPrimitive?.content ?: ""
                    }
                }
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** Strip GPT citation markers from text.
     *  GPT uses PUA chars (U+E200-U+E202) for delimited citations like
     *  "citeturn0search7turn0search12" and
     *  CJK-bracket markers like "【turn0search20】". */
    private fun cleanCitationMarkers(text: String): String {
        var cleaned = text.replace(Regex("(cite|filecite)[^]+"), "")
        cleaned = cleaned.replace("", "").replace("", "").replace("", "")
        cleaned = cleaned.replace(Regex("【turn\\d+[a-z]+\\d+】"), "")
        return cleaned
    }

    private fun convertTimestamp(unixSeconds: Double): Long {
        return (unixSeconds * 1000).toLong()
    }
}
