package com.orangeisland.app.data.repository

import android.content.Context
import android.util.LruCache
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.data.local.ChatEntity
import com.orangeisland.app.data.local.EmbeddingEntity
import com.orangeisland.app.data.local.LargeTextStore
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.local.ProjectEntity
import com.orangeisland.app.model.AttachmentMeta
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.ChatConversation
import com.orangeisland.app.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConversationRepository(
    private val chatDao: ChatDao,
    private val appContext: Context
) {
    /**
     * Cache for decoded large-text fields. Room emits the full list on every
     * streaming upsert, so decoding the same unchanged messages from disk over
     * and over is a major hot-path cost. The key covers the fields that affect
     * the decoded output.
     */
    private val decodedMessageCache = LruCache<String, MessageEntity>(200)

    private fun MessageEntity.decodedCacheKey(): String = buildString {
        append(id)
        append('|')
        append(status.name)
        append('|')
        append(text.hashCode())
        append('|')
        append(thoughts.hashCode())
        append('|')
        append(toolCallJson.hashCode())
        append('|')
        append(attachmentMeta.hashCode())
    }

    private fun MessageEntity.decodeLargeTextCached(context: Context): MessageEntity {
        val key = decodedCacheKey()
        return decodedMessageCache.get(key) ?: decodeLargeText(context).also {
            decodedMessageCache.put(key, it)
        }
    }

    // ── Conversations ─────────────────────────────────────────

    fun getAllConversations(): Flow<List<ChatConversation>> =
        chatDao.getAllConversations().map { entities ->
            entities.map { ChatConversation(id = it.id, title = it.title, systemPromptId = it.systemPromptId, modelId = it.modelId, projectId = it.projectId, lastUpdated = it.lastUpdated) }
        }

    suspend fun getConversation(id: String): ChatEntity? =
        chatDao.getConversation(id)

    /** Reactive observer for a single conversation row. The chat UI subscribes to this so the
     *  compacted-history card appears/disappears the moment compactedSummary is written. */
    fun observeConversation(id: String): Flow<ChatEntity?> =
        chatDao.observeConversation(id)

    suspend fun createConversation(title: String, systemPromptId: String? = null, modelId: String? = null): String {
        val id = java.util.UUID.randomUUID().toString()
        chatDao.upsertConversation(ChatEntity(id = id, title = title, systemPromptId = systemPromptId, modelId = modelId))
        return id
    }

    /** Create or return a conversation with a specific id (used by plugin window ids). */
    suspend fun ensureConversation(
        id: String,
        title: String,
        systemPromptId: String? = null,
        modelId: String? = null,
        projectId: String? = null,
    ): String {
        chatDao.getConversation(id)?.let { return id }
        chatDao.upsertConversation(ChatEntity(
            id = id, title = title, systemPromptId = systemPromptId, modelId = modelId, projectId = projectId
        ))
        return id
    }

    suspend fun upsertConversation(entity: ChatEntity) = chatDao.upsertConversation(entity)

    suspend fun deleteConversation(id: String) {
        val messages = chatDao.getMessagesForConversation(id).first()
        deleteAttachmentFilesFromEntities(messages)
        deleteOverflowFilesFromEntities(messages)
        chatDao.deleteEmbeddingsByConversation(id)
        chatDao.deleteMessagesByConversation(id)
        chatDao.deleteConversation(id)
    }

    // ── Projects ─────────────────────────────────────────────

    fun getAllProjects(): Flow<List<ProjectEntity>> = chatDao.getAllProjects()

    suspend fun getAllProjectsList(): List<ProjectEntity> = chatDao.getAllProjectsList()

    suspend fun getProject(id: String): ProjectEntity? = chatDao.getProject(id)

    /** Creates a project and returns its id. [sortOrder] defaults to one past the current max. */
    suspend fun createProject(name: String, systemPromptId: String? = null, modelId: String? = null): String {
        val id = java.util.UUID.randomUUID().toString()
        val existing = chatDao.getAllProjectsList()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        chatDao.upsertProject(
            ProjectEntity(id = id, name = name, sortOrder = nextOrder, systemPromptId = systemPromptId, modelId = modelId)
        )
        return id
    }

    suspend fun upsertProject(project: ProjectEntity) = chatDao.upsertProject(project)

    suspend fun renameProject(id: String, name: String) {
        chatDao.getProject(id)?.let { chatDao.upsertProject(it.copy(name = name)) }
    }

    suspend fun setProjectDefaults(id: String, systemPromptId: String?, modelId: String?) {
        chatDao.getProject(id)?.let { chatDao.upsertProject(it.copy(systemPromptId = systemPromptId, modelId = modelId)) }
    }

    /** Deletes a project and detaches its conversations (they fall back to ungrouped). */
    suspend fun deleteProject(id: String) {
        chatDao.clearProjectAssignments(id)
        chatDao.deleteProject(id)
    }

    /** Moves a conversation into (or out of, when [projectId] is null) a project. */
    suspend fun moveConversation(conversationId: String, projectId: String?) =
        chatDao.setConversationProject(conversationId, projectId)

    // ── Messages ──────────────────────────────────────────────

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> =
        chatDao.getMessagesForConversation(conversationId).map { list ->
            list.map { it.decodeLargeTextCached(appContext) }
        }

    suspend fun getMessagesForConversationSnapshot(conversationId: String): List<MessageEntity> =
        chatDao.getMessagesForConversation(conversationId).first().map { it.decodeLargeTextCached(appContext) }

    /** Lightweight stuck-message lookup for the switch-conversation path. Does NOT call
     *  decodeLargeText — the status field is the only thing read or rewritten here, so
     *  there's no need to pay the overflow-file read cost for text/thoughts. */
    suspend fun getStuckMessagesForConversation(conversationId: String): List<MessageEntity> =
        chatDao.getStuckMessagesForConversation(conversationId)

    suspend fun upsertMessage(entity: MessageEntity) {
        val encoded = entity.encodeLargeText(appContext)
        chatDao.upsertMessage(encoded)
    }

    suspend fun deleteMessagesByIds(ids: List<String>) {
        val messages = chatDao.getMessagesByIds(ids)
        deleteOverflowFilesFromEntities(messages)
        chatDao.deleteMessagesByIds(ids)
    }

    suspend fun getMessagesByIds(ids: List<String>): List<MessageEntity> =
        chatDao.getMessagesByIds(ids).map { it.decodeLargeTextCached(appContext) }

    /** Recent messages across every conversation in [projectId], newest first.
     *  Used by the workflow engine to inject project chat history into LLMNode context. */
    suspend fun getRecentMessagesForProject(projectId: String, limit: Int = 20): List<MessageEntity> =
        chatDao.getRecentMessagesForProject(projectId, limit).map { it.decodeLargeTextCached(appContext) }

    /** MessageId → projectId mapping for RAG scope filtering. See [ChatDao.getProjectIdsForMessages]. */
    suspend fun getProjectIdsForMessages(ids: List<String>): Map<String, String?> =
        chatDao.getProjectIdsForMessages(ids).associate { it.messageId to it.projectId }

    // ── Branch Selection ──────────────────────────────────────

    suspend fun saveBranchSelections(conversationId: String, selections: Map<String?, String>) {
        val conversation = chatDao.getConversation(conversationId) ?: return
        val stringKeyMap = selections.mapKeys { it.key ?: "null" }
        val json = Json.encodeToString(stringKeyMap)
        if (conversation.selectedBranchesJson != json) {
            chatDao.upsertConversation(conversation.copy(selectedBranchesJson = json, lastUpdated = System.currentTimeMillis()))
        }
    }

    suspend fun restoreBranchSelections(conversationId: String): Map<String?, String> {
        val conversation = chatDao.getConversation(conversationId) ?: return emptyMap()
        val raw = conversation.selectedBranchesJson ?: return emptyMap()
        return try {
            val map = Json.decodeFromString<Map<String, String>>(raw)
            map.mapKeys { if (it.key == "null") null else it.key }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── Stuck Message Fixer ───────────────────────────────────

    suspend fun fixStuckMessages(conversationId: String) {
        val stuckMessages = chatDao.getMessagesForConversation(conversationId).first()
            .filter {
                it.status == MessageStatus.SENDING ||
                it.status == MessageStatus.THINKING ||
                it.status == MessageStatus.TOOL_CALLING ||
                it.status == MessageStatus.TRANSCRIBING
            }
        stuckMessages.forEach { msg ->
            chatDao.upsertMessage(msg.copy(status = MessageStatus.STOPPED))
        }
    }

    // ── Embeddings ────────────────────────────────────────────

    suspend fun deleteEmbeddingsByConversation(conversationId: String) =
        chatDao.deleteEmbeddingsByConversation(conversationId)

    suspend fun deleteOrphanedEmbeddings() =
        chatDao.deleteOrphanedEmbeddings()

    suspend fun deleteEmbeddingsByModel(modelId: String) =
        chatDao.deleteEmbeddingsByModel(modelId)

    suspend fun getEmbeddedMessageIdsByModel(modelId: String): List<String> =
        chatDao.getEmbeddedMessageIdsByModel(modelId)

    suspend fun upsertEmbedding(entity: EmbeddingEntity) =
        chatDao.upsertEmbedding(entity)

    suspend fun deleteAllConversations() =
        chatDao.deleteAllConversations()

    suspend fun findExistingMessageIds(ids: List<String>): List<String> =
        chatDao.findExistingMessageIds(ids)

    suspend fun getEmbeddingsByModel(modelId: String): List<EmbeddingEntity> =
        chatDao.getEmbeddingsByModel(modelId)

    suspend fun deleteEmbedding(messageId: String) =
        chatDao.deleteEmbedding(messageId)

    suspend fun getEmbeddingCountByModel(modelId: String): Int =
        chatDao.getEmbeddingCountByModel(modelId)

    suspend fun getIndexableMessageCount(): Int =
        chatDao.getIndexableMessageCount()

    // ── Search ────────────────────────────────────────────────

    suspend fun searchMessages(query: String, limit: Int = 10): List<MessageEntity> =
        chatDao.searchMessages(query, limit).map { it.decodeLargeTextCached(appContext) }

    /**
     * Scope-aware search. [projectId] = null searches only ungrouped conversations (the
     * "global" view); non-null searches only that project's conversations. Either way the
     * other scope is excluded — this is what enforces "project contents are invisible
     * outside the project" for both the drawer search and the AI's RAG retrieval.
     */
    suspend fun searchMessagesScoped(query: String, projectId: String?, limit: Int = 10): List<MessageEntity> =
        if (projectId == null) chatDao.searchMessagesGlobal(query, limit).map { it.decodeLargeTextCached(appContext) }
        else chatDao.searchMessagesInProject(query, projectId, limit).map { it.decodeLargeTextCached(appContext) }

    suspend fun getAllConversationsList(): List<ChatEntity> =
        chatDao.getAllConversationsList()

    /** Scope-filtered variant: null → ungrouped only; non-null → that project only. */
    suspend fun getConversationsListScoped(projectId: String?): List<ChatEntity> =
        if (projectId == null) chatDao.getGlobalConversationsList()
        else chatDao.getConversationsInProject(projectId)

    suspend fun getAllMessagesList(): List<MessageEntity> =
        chatDao.getAllMessagesList().map { it.decodeLargeTextCached(appContext) }

    suspend fun getAllMessagesForIndexing(): List<MessageEntity> =
        chatDao.getAllMessagesForIndexing().map { it.decodeLargeTextCached(appContext) }

    /** Deletes all on-disk attachment files referenced by [messages]. Safe to call with
     *  an empty list. Errors per-file are swallowed so one bad path never aborts a delete. */
    suspend fun deleteMessageFiles(messages: List<MessageEntity>) = deleteAttachmentFilesFromEntities(messages)

    /** Overload for the in-memory [ChatMessage] form used by the VM's cascade-delete path. */
    fun deleteMessageFiles(messages: List<ChatMessage>) {
        for (msg in messages) {
            for (imagePath in msg.images) {
                runCatching { java.io.File(imagePath).delete() }
            }
            msg.attachmentMeta?.items?.forEach { item ->
                val uri = item.originalUri ?: return@forEach
                if ((item.type == "video" || item.type == "image" || item.type == "file") &&
                    uri.startsWith("file://")
                ) {
                    runCatching { java.io.File(uri.removePrefix("file://")).delete() }
                }
            }
        }
    }

    private fun deleteAttachmentFilesFromEntities(messages: List<MessageEntity>) {
        for (msg in messages) {
            for (imagePath in msg.images) {
                runCatching { java.io.File(imagePath).delete() }
            }
            if (msg.attachmentMeta != null) {
                runCatching {
                    val meta = Json.decodeFromString<AttachmentMeta>(msg.attachmentMeta)
                    for (item in meta.items) {
                        val uri = item.originalUri ?: continue
                        if ((item.type == "video" || item.type == "image" || item.type == "file") &&
                            uri.startsWith("file://")
                        ) {
                            runCatching { java.io.File(uri.removePrefix("file://")).delete() }
                        }
                    }
                }
            }
        }
    }

    private fun deleteOverflowFilesFromEntities(messages: List<MessageEntity>) {
        for (msg in messages) {
            LargeTextStore.deleteIfOverflow(appContext, msg.text)
            LargeTextStore.deleteIfOverflow(appContext, msg.thoughts)
        }
    }
}
