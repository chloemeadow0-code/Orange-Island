package com.orangeisland.app.plugin

import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Production implementation of [PluginMemoryProvider]. Backed by
 * [ConversationRepository] (messages) and [MemoryManager] (long-term + active memory).
 *
 * Project isolation:
 *  - [getChatHistory] reads messages for the requested conversation regardless of project.
 *  - [getLongTermMemories] resolves the conversation's [projectId] and returns
 *    `global memories + project-private memories` merged (same rule as the built-in memory tools).
 *  - [sendChatMessage] inserts a user message into the target conversation.
 */
class AppPluginMemoryProvider(
    private val conversations: ConversationRepository,
    private val memoryManager: MemoryManager,
) : PluginMemoryProvider {

    companion object {
        private const val TAG = "AppPluginMemoryProvider"
        private val json = Json { prettyPrint = false }
    }

    /** Called after a user message is successfully persisted. The host (ChatViewModel) can
     *  use this to trigger LLM generation so the AI replies to plugin-sent messages. */
    var onMessageSent: ((conversationId: String, text: String) -> Unit)? = null

    override suspend fun getChatHistory(conversationId: String, limit: Int): String {
        return try {
            val msgs = conversations.getMessagesForConversationSnapshot(conversationId)
                .sortedBy { it.timestamp }
                .takeLast(limit.coerceIn(1, 500))
            buildJsonArray {
                msgs.forEach { m ->
                    add(messageToJson(m))
                }
            }.toString()
        } catch (e: Exception) {
            DebugLog.e(TAG, "getChatHistory failed", e)
            "[]"
        }
    }

    override suspend fun getLongTermMemories(conversationId: String): String {
        return try {
            val projectId = if (conversationId.isNotBlank()) resolveProjectId(conversationId) else null
            val files = memoryManager.listFilesMerged(projectId)
            buildJsonArray {
                files.forEach { info ->
                    buildJsonObject {
                        put("name", info.name)
                        put("description", info.description)
                        put("projectId", info.projectId ?: "")
                        put("createdAt", info.createdAt)
                    }.let { add(it) }
                }
            }.toString()
        } catch (e: Exception) {
            DebugLog.e(TAG, "getLongTermMemories failed", e)
            "[]"
        }
    }

    override suspend fun getActiveMemory(conversationId: String): String {
        return try {
            // Active memory is currently global-scope only.
            memoryManager.getActiveMemory()
        } catch (e: Exception) {
            DebugLog.e(TAG, "getActiveMemory failed", e)
            ""
        }
    }

    override suspend fun sendChatMessage(conversationId: String, text: String, projectId: String?): Boolean {
        if (text.isBlank()) return false
        return try {
            // Ensure the conversation exists — plugin window ids may not correspond to a real
            // conversation yet, so create one on first send using the id directly.
            // When projectId is provided, bind the new conversation to that project.
            conversations.ensureConversation(
                id = conversationId,
                title = "插件对话",
                projectId = projectId,
            )
            // Do NOT insert the user message here — ChatViewModel.sendMessage will write it
            // uniformly via MessageGenerationController, ensuring correct parentId and avoiding
            // duplicate messages. We just fire the callback so the host triggers generation.
            onMessageSent?.invoke(conversationId, text)
            true
        } catch (e: Exception) {
            DebugLog.e(TAG, "sendChatMessage failed", e)
            false
        }
    }

    override suspend fun resolveProjectId(conversationId: String): String? {
        return try {
            conversations.getConversation(conversationId)?.projectId
        } catch (e: Exception) {
            DebugLog.e(TAG, "resolveProjectId failed", e)
            null
        }
    }

    override suspend fun getConversationInfo(conversationId: String): String {
        return try {
            val entity = conversations.getConversation(conversationId)
            buildJsonObject {
                put("id", conversationId)
                put("projectId", entity?.projectId ?: "")
                put("modelId", entity?.modelId ?: "")
                put("systemPromptId", entity?.systemPromptId ?: "")
            }.toString()
        } catch (e: Exception) {
            DebugLog.e(TAG, "getConversationInfo failed", e)
            buildJsonObject {
                put("id", conversationId)
                put("projectId", "")
                put("modelId", "")
                put("systemPromptId", "")
            }.toString()
        }
    }

    override suspend fun getProjectMemories(projectId: String): String {
        return try {
            val files = memoryManager.listFilesMerged(projectId.ifBlank { null })
            buildJsonArray {
                files.forEach { info ->
                    buildJsonObject {
                        put("name", info.name)
                        put("description", info.description)
                        put("projectId", info.projectId ?: "")
                        put("createdAt", info.createdAt)
                    }.let { add(it) }
                }
            }.toString()
        } catch (e: Exception) {
            DebugLog.e(TAG, "getProjectMemories failed", e)
            "[]"
        }
    }

    override suspend fun createConversation(
        projectId: String,
        title: String,
        modelId: String?,
        systemPromptId: String?,
    ): String {
        return try {
            conversations.createConversation(
                title = title,
                systemPromptId = systemPromptId,
                modelId = modelId,
            ).also { convId ->
                // Move the newly created conversation into the target project.
                if (projectId.isNotBlank()) {
                    conversations.moveConversation(convId, projectId)
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "createConversation failed", e)
            ""
        }
    }

    private fun messageToJson(m: MessageEntity) = buildJsonObject {
        put("id", m.id)
        put("role", when (m.participant) {
            Participant.USER -> "user"
            Participant.MODEL -> "assistant"
            Participant.ERROR -> "error"
            Participant.SYSTEM -> "system"
        })
        put("text", m.text)
        put("timestamp", m.timestamp)
        put("status", m.status.name.lowercase())
        if (m.modelName != null) put("model", m.modelName)
    }
}
