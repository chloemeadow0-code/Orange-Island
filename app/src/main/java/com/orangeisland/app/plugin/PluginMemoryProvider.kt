package com.orangeisland.app.plugin

/**
 * Contract for exposing App-native chat memories to JS plugins (both QuickJS sandbox tools
 * and WebView UI pages).
 *
 * Implementations live in the DI layer ([com.orangeisland.app.di.AppContainer]) and are backed
 * by [com.orangeisland.app.data.repository.ConversationRepository] +
 * [com.orangeisland.app.data.MemoryManager].
 *
 * All methods return JSON strings so the JS side can `JSON.parse` immediately.
 *
 * Project isolation: Orange Island conversations live inside projects (folders). Long-term
 * memories are stored per-project; the host resolves the correct scope automatically from
 * [conversationId] so the plugin doesn't have to juggle two ids.
 */
interface PluginMemoryProvider {
    /**
     * Recent messages for [conversationId] as a JSON array of lightweight objects:
     * `[{ id, role, text, timestamp, status }, …]`.
     * Empty array if the conversation does not exist.
     */
    suspend fun getChatHistory(conversationId: String, limit: Int = 50): String

    /**
     * Long-term memory entries as a JSON array of objects:
     * `[{ name, description, projectId, createdAt }, …]`.
     * When [conversationId] is non-empty, the host resolves its project and returns
     * **global + project-private** files merged (same semantics the built-in memory tools use).
     * When empty, only global memories are returned.
     */
    suspend fun getLongTermMemories(conversationId: String = ""): String

    /**
     * The active / working memory text (the free-form scratchpad the model sees every turn).
     * Currently global-scope; [conversationId] is reserved for future per-project scratchpads.
     */
    suspend fun getActiveMemory(conversationId: String = ""): String

    /**
     * Insert a user message into [conversationId]. Returns `true` if the message was persisted.
     * If the conversation does not exist yet, it is created and bound to [projectId] when provided.
     */
    suspend fun sendChatMessage(conversationId: String, text: String, projectId: String? = null): Boolean

    /**
     * Resolves the project id that owns [conversationId]. Returns `null` for ungrouped
     * conversations (global scope) or if the id is unknown.
     */
    suspend fun resolveProjectId(conversationId: String): String?

    /**
     * Full metadata for [conversationId] as a JSON object:
     * `{"id":"...","projectId":"...","modelId":"...","systemPromptId":"..."}`.
     * Any unknown field is returned as `null`.
     */
    suspend fun getConversationInfo(conversationId: String): String

    /**
     * Long-term memories for a specific [projectId] (global + project-private merged).
     * When [projectId] is blank, only global memories are returned.
     */
    suspend fun getProjectMemories(projectId: String): String

    /**
     * Create a new conversation inside [projectId] with the given title, model and prompt.
     * Returns the new conversation id as a plain string.
     */
    suspend fun createConversation(projectId: String, title: String, modelId: String?, systemPromptId: String?): String
}
