package com.orangeisland.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MessageConverters {
    @TypeConverter
    fun fromParticipant(value: Participant) = value.name
    @TypeConverter
    fun toParticipant(value: String) = Participant.valueOf(value)

    @TypeConverter
    fun fromStatus(value: MessageStatus) = value.name
    @TypeConverter
    fun toStatus(value: String) = MessageStatus.valueOf(value)
    
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return if (value != null) Json.encodeToString(value) else ""
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(value)
        } catch (_: Exception) {
            // Backward compatibility: old format used "|||" delimiter
            value.split("|||")
        }
    }
}

@Entity(tableName = "conversations")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val selectedBranchesJson: String? = null,
    val systemPromptId: String? = null,
    val modelId: String? = null,
    // null = ungrouped. Inherits project-level defaults (model/prompt) only when the
    // conversation itself does not override them.
    val projectId: String? = null,
    // Auto-compressed history: compactedSummary is the running summary of messages at or
    // before compactedUpToTimestamp. buildApiPath drops those messages from the request
    // path and appends the summary to the system prompt instead, so long chats keep their
    // long-term context without overflowing the context window.
    val compactedSummary: String? = null,
    val compactedUpToTimestamp: Long? = null
)

/**
 * A user-created project (folder) that groups related conversations and can carry
 * project-level defaults (model + system prompt) inherited by newly created chats.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val systemPromptId: String? = null,
    val modelId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "embeddings",
    indices = [Index(value = ["messageId", "modelId"], unique = true)]
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val modelId: String,
    val embedding: ByteArray,
    val chunkText: String,
    val dimension: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return id == other.id && messageId == other.messageId && modelId == other.modelId
            && embedding.contentEquals(other.embedding) && chunkText == other.chunkText && dimension == other.dimension
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + modelId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + chunkText.hashCode()
        result = 31 * result + dimension
        return result
    }
}

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"])],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val parentId: String? = null,
    val text: String,
    val images: List<String> = emptyList(),
    val audio: List<String> = emptyList(),
    val thoughts: String? = null,
    val thoughtTitle: String? = null,
    val tokenCount: Int = 0,
    val cachedTokenCount: Int = 0,
    val contextMessageCount: Int = 0,
    val status: MessageStatus = MessageStatus.SUCCESS,
    val participant: Participant,
    val timestamp: Long,
    val thoughtTimeMs: Long? = null,
    val generationDurationMs: Long? = null,
    val modelName: String? = null,
    val toolCallJson: String? = null,
    val attachmentMeta: String? = null
) {
    /** Encode large text fields before writing to DB. */
    fun encodeLargeText(context: Context): MessageEntity = copy(
        text = LargeTextStore.encode(context, id, "text", text) ?: text,
        thoughts = LargeTextStore.encode(context, id, "thoughts", thoughts) ?: thoughts
    )

    /** Decode pointer fields after reading from DB. */
    fun decodeLargeText(context: Context): MessageEntity = copy(
        text = LargeTextStore.decode(context, text) ?: text,
        thoughts = LargeTextStore.decode(context, thoughts) ?: thoughts
    )

    /**
     * Cache key covering every field that may be modified independently and affects
     * the decoded entity or its mapped UI form. This is the single source of truth
     * for both [ConversationRepository.decodedMessageCache] and
     * [ChatViewModel.chatMessageCache] so the two caches never drift apart.
     */
    fun cacheKey(): String = buildString {
        append(id)
        append('|')
        append(parentId)
        append('|')
        append(status.name)
        append('|')
        append(text.hashCode())
        append('|')
        append(images.hashCode())
        append('|')
        append(audio.hashCode())
        append('|')
        append(thoughts.hashCode())
        append('|')
        append(thoughtTitle)
        append('|')
        append(tokenCount)
        append('|')
        append(cachedTokenCount)
        append('|')
        append(contextMessageCount)
        append('|')
        append(thoughtTimeMs)
        append('|')
        append(generationDurationMs)
        append('|')
        append(modelName)
        append('|')
        append(toolCallJson.hashCode())
        append('|')
        append(attachmentMeta.hashCode())
    }
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY lastUpdated DESC")
    fun getAllConversations(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): ChatEntity?

    /** Reactive single-conversation observer. Used so the chat UI can react to compactedSummary
     *  changes (e.g. after auto-compress) without re-selecting the whole conversations list. */
    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun observeConversation(conversationId: String): Flow<ChatEntity?>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    /** Lightweight stuck-status lookup: filters at the SQL level instead of fetching +
     *  decoding the entire conversation just to find a handful of stuck messages. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status IN ('SENDING','THINKING','TOOL_CALLING','TRANSCRIBING')")
    suspend fun getStuckMessagesForConversation(conversationId: String): List<MessageEntity>

    @Upsert
    suspend fun upsertConversation(conversation: ChatEntity)

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<String>)

    // ── Projects ──────────────────────────────────────────────

    @Query("SELECT * FROM projects ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllProjectsList(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProject(projectId: String): ProjectEntity?

    @Upsert
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    /** Reassigns a conversation to [projectId] (null = ungrouped) without touching its content. */
    @Query("UPDATE conversations SET projectId = :projectId WHERE id = :conversationId")
    suspend fun setConversationProject(conversationId: String, projectId: String?)

    /** Detaches every conversation from [projectId] on delete; chats themselves are preserved. */
    @Query("UPDATE conversations SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProjectAssignments(projectId: String)

    @Query("DELETE FROM embeddings WHERE messageId IN (SELECT id FROM messages WHERE conversationId = :conversationId)")
    suspend fun deleteEmbeddingsByConversation(conversationId: String)

    @Query("DELETE FROM embeddings WHERE messageId NOT IN (SELECT id FROM messages)")
    suspend fun deleteOrphanedEmbeddings()

    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE (m.text LIKE '%' || :query || '%' OR c.title LIKE '%' || :query || '%') AND m.participant IN ('USER', 'MODEL') AND m.text != '' AND m.id NOT LIKE 'tool_%' AND m.id NOT LIKE 'result_%' ORDER BY m.timestamp DESC LIMIT :limit")
    suspend fun searchMessages(query: String, limit: Int = 10): List<MessageEntity>

    /**
     * Project-scoped message search: only matches conversations whose [projectId] equals
     * the given value. Used when the drawer / AI search runs from inside a project —
     * results from other projects or ungrouped chats never leak in.
     */
    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE c.projectId = :projectId AND (m.text LIKE '%' || :query || '%' OR c.title LIKE '%' || :query || '%') AND m.participant IN ('USER', 'MODEL') AND m.text != '' AND m.id NOT LIKE 'tool_%' AND m.id NOT LIKE 'result_%' ORDER BY m.timestamp DESC LIMIT :limit")
    suspend fun searchMessagesInProject(query: String, projectId: String, limit: Int = 10): List<MessageEntity>

    /**
     * Global-scope message search: matches only ungrouped conversations (projectId IS NULL).
     * Conversations inside any project are invisible here, mirroring the drawer's visibility
     * rule that "project contents are hidden from the global view".
     */
    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE c.projectId IS NULL AND (m.text LIKE '%' || :query || '%' OR c.title LIKE '%' || :query || '%') AND m.participant IN ('USER', 'MODEL') AND m.text != '' AND m.id NOT LIKE 'tool_%' AND m.id NOT LIKE 'result_%' ORDER BY m.timestamp DESC LIMIT :limit")
    suspend fun searchMessagesGlobal(query: String, limit: Int = 10): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForConversation(conversationId: String): MessageEntity?

    /**
     * Returns the most-recent messages across every conversation in [projectId],
     * ordered by timestamp descending (newest first). Used by the workflow engine
     * to inject project chat history into an LLMNode's context.
     */
    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE c.projectId = :projectId AND m.participant IN ('USER', 'MODEL') AND m.text != '' AND m.id NOT LIKE 'tool_%' AND m.id NOT LIKE 'result_%' ORDER BY m.timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForProject(projectId: String, limit: Int = 20): List<MessageEntity>

    // Embeddings
    @Upsert
    suspend fun upsertEmbedding(embedding: EmbeddingEntity)

    @Query("SELECT * FROM embeddings WHERE messageId = :messageId LIMIT 1")
    suspend fun getEmbedding(messageId: String): EmbeddingEntity?

    @Query("SELECT * FROM embeddings")
    suspend fun getAllEmbeddings(): List<EmbeddingEntity>

    @Query("DELETE FROM embeddings WHERE messageId = :messageId")
    suspend fun deleteEmbedding(messageId: String)

    @Query("SELECT * FROM embeddings WHERE modelId = :modelId")
    suspend fun getEmbeddingsByModel(modelId: String): List<EmbeddingEntity>

    @Query("DELETE FROM embeddings WHERE modelId = :modelId")
    suspend fun deleteEmbeddingsByModel(modelId: String)

    @Query("SELECT COUNT(*) FROM embeddings WHERE modelId = :modelId")
    suspend fun getEmbeddingCountByModel(modelId: String): Int

    @Query("SELECT messageId FROM embeddings WHERE modelId = :modelId")
    suspend fun getEmbeddedMessageIdsByModel(modelId: String): List<String>

    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE m.participant IN ('USER', 'MODEL') AND m.text != '' AND m.id NOT LIKE 'tool_%' AND m.id NOT LIKE 'result_%'")
    suspend fun getAllMessagesForIndexing(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE m.participant IN ('USER', 'MODEL') AND m.text != '' AND m.id NOT LIKE 'tool_%' AND m.id NOT LIKE 'result_%'")
    suspend fun getIndexableMessageCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun countMessagesInConversation(conversationId: String): Int

    @Query("SELECT MAX(timestamp) FROM messages")
    suspend fun getLatestMessageTimestamp(): Long?

    // ── 使用统计查询（供桌面统计小组件使用）──────────────────────────────
    // 所有统计都过滤掉 tool_/result_ 工具行，只统计真实 USER/MODEL 消息。
    // tokenCount 只写在 MODEL 消息上（整轮 prompt+completion 合计）。

    /** 指定角色、指定时间点之后的真实消息条数。 */
    @Query("SELECT COUNT(*) FROM messages WHERE participant = :role AND timestamp >= :since AND id NOT LIKE 'tool_%' AND id NOT LIKE 'result_%'")
    suspend fun countMessagesByRoleSince(role: String, since: Long): Int

    /** 指定时间点之后的总 token 数（全部记在 MODEL 行上）。 */
    @Query("SELECT COALESCE(SUM(tokenCount), 0) FROM messages WHERE participant = 'MODEL' AND timestamp >= :since")
    suspend fun sumTokensSince(since: Long): Long

    /** 指定时间点之后的总使用时长（毫秒，仅 MODEL 行有值）。 */
    @Query("SELECT COALESCE(SUM(generationDurationMs), 0) FROM messages WHERE participant = 'MODEL' AND timestamp >= :since")
    suspend fun sumDurationSince(since: Long): Long

    /** 拉取指定角色、指定时间点之后的所有消息文本（字数需在 Kotlin 里算，因为有 overflow 指针）。 */
    @Query("SELECT * FROM messages WHERE participant = :role AND timestamp >= :since AND id NOT LIKE 'tool_%' AND id NOT LIKE 'result_%'")
    suspend fun getMessagesByRoleSince(role: String, since: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<String>): List<MessageEntity>

    /**
     * Returns messageId → projectId for the given message ids, so RAG can filter semantic
     * hits by the same scope rule as keyword search without joining on every candidate.
     * Messages whose conversation was deleted are simply absent from the result.
     */
    @Query("SELECT m.id AS messageId, c.projectId AS projectId FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE m.id IN (:ids)")
    suspend fun getProjectIdsForMessages(ids: List<String>): List<MessageProjectId>

    /** Lightweight join row for [getProjectIdsForMessages]. */
    data class MessageProjectId(val messageId: String, val projectId: String?)

    // Bulk export/import
    @Query("SELECT * FROM conversations")
    suspend fun getAllConversationsList(): List<ChatEntity>

    /** Scope-filtered variant for RAG list_conversations: matches the same rule as search. */
    @Query("SELECT * FROM conversations WHERE projectId IS NULL ORDER BY lastUpdated DESC")
    suspend fun getGlobalConversationsList(): List<ChatEntity>

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY lastUpdated DESC")
    suspend fun getConversationsInProject(projectId: String): List<ChatEntity>

    @Query("SELECT * FROM messages")
    suspend fun getAllMessagesList(): List<MessageEntity>

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("SELECT id FROM messages WHERE id IN (:ids)")
    suspend fun findExistingMessageIds(ids: List<String>): List<String>

    /**
     * Atomically replaces the entire conversation/message/project dataset.
     * Runs inside a Room transaction so old data is never visible as partially deleted.
     * Called by DataImporter on REPLACE strategy.
     */
    @androidx.room.Transaction
    suspend fun replaceAllConversationsAndMessages(
        conversations: List<ChatEntity>,
        messages: List<MessageEntity>,
        projects: List<ProjectEntity>
    ) {
        deleteAllConversations()
        // messages are cascade-deleted by conversation deletion if FK is set,
        // but we also explicitly clear projects to be safe.
        getAllProjectsList().forEach { deleteProject(it.id) }
        projects.forEach { upsertProject(it) }
        conversations.forEach { upsertConversation(it) }
        messages.forEach { upsertMessage(it) }
    }
}

@Database(
    entities = [ChatEntity::class, MessageEntity::class, EmbeddingEntity::class, ProjectEntity::class, WorkflowEntity::class, WorkflowRunEntity::class],
    version = ChatDatabase.CURRENT_VERSION,
    exportSchema = true
)@TypeConverters(MessageConverters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun workflowDao(): WorkflowDao

    companion object {
        const val CURRENT_VERSION = 21
        const val DB_NAME = "orangeisland_db"

        private fun buildMigrations(context: Context): List<Migration> {
            val appContext = context.applicationContext
            return listOf(
            // v1 → v2 added messages.images (List<String> stored as TEXT via converter,
            // NOT NULL with "" representing an empty list). This step was missing, so any
            // device still on schema v1 crashed on launch with "migration 1 to 2 not found".
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN images TEXT NOT NULL DEFAULT ''")
                }
            },
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN selectedBranchesJson TEXT")
                }
            },
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN thoughtTimeMs INTEGER")
                }
            },
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN modelName TEXT")
                }
            },
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN systemPromptId TEXT")
                }
            },
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN modelId TEXT")
                }
            },
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN thoughtTitle TEXT")
                }
            },
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN toolCallJson TEXT")
                }
            },
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS embeddings (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            messageId TEXT NOT NULL,
                            embedding BLOB NOT NULL,
                            chunkText TEXT NOT NULL,
                            dimension INTEGER NOT NULL
                        )
                    """)
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_embeddings_messageId ON embeddings (messageId)")
                }
            },
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE embeddings ADD COLUMN modelId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("DROP INDEX IF EXISTS index_embeddings_messageId")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_embeddings_messageId_modelId ON embeddings (messageId, modelId)")
                }
            },
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN attachmentMeta TEXT")
                }
            },
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // New projects table for ChatGPT-style conversation grouping.
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS projects (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            sortOrder INTEGER NOT NULL,
                            systemPromptId TEXT,
                            modelId TEXT,
                            createdAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    // null = ungrouped (the default for every pre-existing conversation).
                    db.execSQL("ALTER TABLE conversations ADD COLUMN projectId TEXT")
                }
            },
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // New workflows + workflow_runs tables for the Workflow feature.
                    // The graph (nodes + edges) is stored as a JSON blob in graphJson; see
                    // [WorkflowEntity] for the rationale. Column types/NOT NULL mirror what Room
                    // generates from the @Entity data classes so the schema hash matches.
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS workflows (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL,
                            graphJson TEXT NOT NULL,
                            enabled INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            lastRunAt INTEGER,
                            lastRunStatus TEXT,
                            totalRuns INTEGER NOT NULL,
                            successRuns INTEGER NOT NULL,
                            failedRuns INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS workflow_runs (
                            runId TEXT NOT NULL PRIMARY KEY,
                            workflowId TEXT NOT NULL,
                            workflowName TEXT NOT NULL,
                            startNodeId TEXT,
                            startedAt INTEGER NOT NULL,
                            finishedAt INTEGER,
                            status TEXT NOT NULL,
                            message TEXT NOT NULL,
                            logsJson TEXT,
                            FOREIGN KEY(workflowId) REFERENCES workflows(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_workflow_runs_workflowId ON workflow_runs(workflowId)")
                }
            },
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Workflow v2: add columns to support AI-authored linear workflows (trigger +
                    // conditions + actions) alongside the existing node-graph mode, plus the
                    // per-day fire counter + cooldown/daily-cap fields the linear engine enforces.
                    // Every new column has a default so existing rows stay valid as graph workflows.
                    db.execSQL("ALTER TABLE workflows ADD COLUMN mode TEXT NOT NULL DEFAULT 'graph'")
                    db.execSQL("ALTER TABLE workflows ADD COLUMN cooldownMs INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE workflows ADD COLUMN maxRunsPerDay INTEGER")
                    db.execSQL("ALTER TABLE workflows ADD COLUMN runsTodayCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE workflows ADD COLUMN runsTodayDate TEXT NOT NULL DEFAULT ''")
                }
            },
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Workflow project binding: allows a workflow to inherit a project's chat
                    // history, system prompt, and model configuration at execution time.
                    db.execSQL("ALTER TABLE workflows ADD COLUMN projectId TEXT")
                    db.execSQL("ALTER TABLE workflows ADD COLUMN systemPromptId TEXT")
                    db.execSQL("ALTER TABLE workflows ADD COLUMN modelId TEXT")
                }
            },
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // TTS audio attachments: stores a JSON-encoded list of local file paths
                    // for AI-generated voice messages (speak tool output).
                    db.execSQL("ALTER TABLE messages ADD COLUMN audio TEXT NOT NULL DEFAULT ''")
                }
            },
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Cache-hit token tracking (Anthropic/OpenAI/Gemini prompt caching) + the
                    // context message count actually sent to the model for this reply.
                    db.execSQL("ALTER TABLE messages ADD COLUMN cachedTokenCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE messages ADD COLUMN contextMessageCount INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Total wall-clock time from generation start to completion, shown
                    // alongside the token/cache usage stats. Nullable — old messages have
                    // no recorded duration, and showing "unknown" beats faking a 0.
                    db.execSQL("ALTER TABLE messages ADD COLUMN generationDurationMs INTEGER")
                }
            },
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Auto-compress: stores a running summary of older conversation history
                    // plus the watermark timestamp up to which messages have been folded in.
                    // Both nullable — most conversations are never compressed.
                    db.execSQL("ALTER TABLE conversations ADD COLUMN compactedSummary TEXT")
                    db.execSQL("ALTER TABLE conversations ADD COLUMN compactedUpToTimestamp INTEGER")
                }
            },
            object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // CursorWindow crash fix: offload oversized text/thoughts to external files.
                    // This migration must NOT read large columns directly (that would trigger the
                    // same CursorWindow crash). We use length() to find offenders, then substr()
                    // to read them in small chunks.
                    val threshold = LargeTextStore.THRESHOLD_CHARS
                    val chunkSize = 500 * 1024 // 500KB chars per substr read
                    val overflowDir = java.io.File(appContext.filesDir, "text_overflow")
                    overflowDir.mkdirs()

                    try {
                        // Step 1: find all message ids whose text or thoughts exceed the threshold.
                        val cursor = db.query(
                            "SELECT id, length(text) AS tlen, length(thoughts) AS thlen FROM messages WHERE length(text) > $threshold OR length(thoughts) > $threshold",
                            arrayOf<Any>()
                        )
                        val offenders = mutableListOf<Triple<String, Int, Int>>()
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(0)
                            val tlen = cursor.getInt(1)
                            val thlen = cursor.getInt(2)
                            offenders.add(Triple(id, tlen, thlen))
                        }
                        cursor.close()

                        for ((id, tlen, thlen) in offenders) {
                            try {
                                // --- text ---
                                var textPointer: String? = null
                                if (tlen > threshold) {
                                    val sb = StringBuilder(tlen)
                                    var offset = 1
                                    while (offset <= tlen) {
                                        val c = db.query(
                                            "SELECT substr(text, ?, ?) FROM messages WHERE id = ?",
                                            arrayOf(offset.toString(), chunkSize.toString(), id)
                                        )
                                        if (c.moveToFirst()) {
                                            val piece = c.getString(0)
                                            if (piece != null) sb.append(piece)
                                        }
                                        c.close()
                                        offset += chunkSize
                                    }
                                    val fullText = sb.toString()
                                    val textFileName = "${id}_text_${java.util.UUID.randomUUID()}.txt"
                                    val outFile = java.io.File(overflowDir, textFileName)
                                    outFile.writeText(fullText, Charsets.UTF_8)
                                    textPointer = "oi-overflow://v1/$textFileName"
                                }

                                // --- thoughts ---
                                var thoughtsPointer: String? = null
                                if (thlen > threshold) {
                                    val sb = StringBuilder(thlen)
                                    var offset = 1
                                    while (offset <= thlen) {
                                        val c = db.query(
                                            "SELECT substr(thoughts, ?, ?) FROM messages WHERE id = ?",
                                            arrayOf(offset.toString(), chunkSize.toString(), id)
                                        )
                                        if (c.moveToFirst()) {
                                            val piece = c.getString(0)
                                            if (piece != null) sb.append(piece)
                                        }
                                        c.close()
                                        offset += chunkSize
                                    }
                                    val fullThoughts = sb.toString()
                                    val thFileName = "${id}_thoughts_${java.util.UUID.randomUUID()}.txt"
                                    val outFile = java.io.File(overflowDir, thFileName)
                                    outFile.writeText(fullThoughts, Charsets.UTF_8)
                                    thoughtsPointer = "oi-overflow://v1/$thFileName"
                                }

                                // Update the row with pointer(s). Only update the columns that actually changed.
                                if (textPointer != null && thoughtsPointer != null) {
                                    db.execSQL(
                                        "UPDATE messages SET text = ?, thoughts = ? WHERE id = ?",
                                        arrayOf(textPointer, thoughtsPointer, id)
                                    )
                                } else if (textPointer != null) {
                                    db.execSQL(
                                        "UPDATE messages SET text = ? WHERE id = ?",
                                        arrayOf(textPointer, id)
                                    )
                                } else if (thoughtsPointer != null) {
                                    db.execSQL(
                                        "UPDATE messages SET thoughts = ? WHERE id = ?",
                                        arrayOf(thoughtsPointer, id)
                                    )
                                }
                            } catch (e: Exception) {
                                // Log and continue — one bad message must not abort the whole migration.
                                android.util.Log.e(
                                    "ChatDatabase",
                                    "Migration(20,21) failed for message id=$id tlen=$tlen thlen=$thlen",
                                    e
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // If the whole migration query fails, log it but do NOT throw —
                        // otherwise the user can never open the app again.
                        android.util.Log.e("ChatDatabase", "Migration(20,21) top-level failure", e)
                    }
                }
            }
        )
        }

        fun getStoredVersion(context: Context): Int {
            val dbPath = context.getDatabasePath(DB_NAME)
            if (!dbPath.exists()) return 0
            return try {
                val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
                val version = db.version
                db.close()
                version
            } catch (e: Exception) {
                0
            }
        }

    fun build(context: Context): ChatDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            ChatDatabase::class.java,
            DB_NAME
        ).addMigrations(*buildMigrations(context.applicationContext).toTypedArray())
            .fallbackToDestructiveMigration(false)
            // Multiple ChatDatabase instances can exist simultaneously pointing at the same
            // file — the main app's AppContainer-owned instance, plus a fresh one built by
            // every background Worker (WorkflowWorker, HealthSyncWorker, AutoBackupWorker,
            // etc., which intentionally rebuild their own instance since a Worker may run in
            // a process without the app's AppContainer). Without this flag, a write through
            // one instance never notifies the other instances' Flow observers, so a workflow
            // firing while the app is open silently writes the message but the UI never
            // refreshes until the app is force-restarted (a fresh instance re-queries from
            // scratch). This flag makes all instances on the same file notify each other.
            .enableMultiInstanceInvalidation()
            .build()
    }
    }
}
