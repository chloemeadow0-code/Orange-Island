package com.orangeisland.app.tool

import android.app.Application
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.StickyNoteEntry
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.widget.StickyNoteWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 让 AI 管理"桌面便签"。
 *
 * 数据落盘到 DataStore 的 sticky_notes_json，桌面便签小组件开屏随机展示其中一条。
 * AI 可增/改/删/查；新增时若超过 [MAX_NOTES]（默认 50）条上限，自动丢弃最旧的一条
 * （保持 FIFO 回收，避免无限膨胀）。
 *
 * 写操作完成后会主动刷新桌面便签小组件，让用户立即看到变化。
 */
class StickyNoteToolProvider(private val app: Application) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val settings by lazy { SettingsManager(app) }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunction(
            name = "list_sticky_notes",
            description = "List every sticky note the user has saved. Each has id/title/content/createdAt. " +
                "Use before editing or when the user asks what notes exist.",
            parameters = ToolParameters(properties = emptyMap(), required = emptyList())
        )),
        ToolDefinition(function = ToolFunction(
            name = "create_sticky_note",
            description = "Create a sticky note that shows up on the home-screen widget (one random note " +
                "is displayed each time the screen is turned on). Use for short reminders, quotes, " +
                "or anything the user wants to see on the lock/home screen. Max 50 notes are kept; " +
                "the oldest is dropped when full.",
            parameters = ToolParameters(
                properties = mapOf(
                    "title" to ToolProperty("string", "Optional short title (a few words)."),
                    "content" to ToolProperty("string", "The note body. Keep it concise — it shows on a small widget.")
                ),
                required = listOf("content")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "update_sticky_note",
            description = "Edit an existing sticky note by id (from list_sticky_notes).",
            parameters = ToolParameters(
                properties = mapOf(
                    "id" to ToolProperty("string", "The note's id."),
                    "title" to ToolProperty("string", "New title (optional)."),
                    "content" to ToolProperty("string", "New content (optional).")
                ),
                required = listOf("id")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "delete_sticky_note",
            description = "Remove a sticky note by id (from list_sticky_notes) or by exact content/title match.",
            parameters = ToolParameters(
                properties = mapOf(
                    "id" to ToolProperty("string", "The note's id."),
                    "match" to ToolProperty("string", "Substring to match against title/content, used only if id is omitted.")
                ),
                required = emptyList()
            )
        ))
    )

    override fun handles(name: String): Boolean =
        name == "list_sticky_notes" || name == "create_sticky_note" ||
            name == "update_sticky_note" || name == "delete_sticky_note"

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String = when (name) {
        "list_sticky_notes" -> listNotes()
        "create_sticky_note" -> createNote(arguments)
        "update_sticky_note" -> updateNote(arguments)
        "delete_sticky_note" -> deleteNote(arguments)
        else -> buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
    }

    // ── 实现 ───────────────────────────────────────────────────────────────

    private suspend fun listNotes(): String {
        val notes = settings.stickyNotes.first().sortedByDescending { it.updatedAt }
        return buildJsonObject {
            put("count", notes.size)
            put("max", MAX_NOTES)
            put("notes", buildJsonArray {
                notes.forEach { n ->
                    add(buildJsonObject {
                        put("id", n.id)
                        put("title", n.title)
                        put("content", n.content)
                        put("createdAt", n.createdAt)
                        put("updatedAt", n.updatedAt)
                    })
                }
            })
        }.toString()
    }

    private suspend fun createNote(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        fun str(k: String) = (parsed[k] as? JsonPrimitive)?.content
        val content = str("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: return buildJsonObject { put("error", "bad_args"); put("message", "content is required") }.toString()
        val title = str("title")?.trim().orEmpty()

        val now = System.currentTimeMillis()
        val entry = StickyNoteEntry(title = title, content = content, createdAt = now, updatedAt = now)

        val current = settings.stickyNotes.first().toMutableList()
        current.add(entry)
        // 超过上限：按 createdAt 升序（最旧在前），丢弃最旧的若干条
        if (current.size > MAX_NOTES) {
            val keep = current.sortedByDescending { it.createdAt }.take(MAX_NOTES)
            current.clear()
            current.addAll(keep)
        }
        settings.saveStickyNotes(current)
        refreshWidget()

        return buildJsonObject {
            put("id", entry.id)
            put("title", entry.title)
            put("content", entry.content)
            put("total", current.size)
            put("max", MAX_NOTES)
        }.toString()
    }

    private suspend fun updateNote(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        fun str(k: String) = (parsed[k] as? JsonPrimitive)?.content
        val id = str("id")?.takeIf { it.isNotBlank() }
            ?: return buildJsonObject { put("error", "bad_args"); put("message", "id is required") }.toString()
        val newTitle = str("title")?.trim()
        val newContent = str("content")?.trim()

        val current = settings.stickyNotes.first()
        val target = current.firstOrNull { it.id == id }
            ?: return buildJsonObject { put("error", "not_found"); put("id", id) }.toString()

        val updated = target.copy(
            title = newTitle ?: target.title,
            content = newContent ?: target.content,
            updatedAt = System.currentTimeMillis()
        )
        settings.saveStickyNotes(current.map { if (it.id == id) updated else it })
        refreshWidget()

        return buildJsonObject {
            put("id", updated.id)
            put("title", updated.title)
            put("content", updated.content)
            put("updatedAt", updated.updatedAt)
        }.toString()
    }

    private suspend fun deleteNote(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val id = (parsed["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val match = (parsed["match"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        if (id == null && match == null) {
            return buildJsonObject { put("error", "bad_args"); put("message", "provide id or match") }.toString()
        }
        val current = settings.stickyNotes.first()
        val target = current.firstOrNull { it.id == id }
            ?: current.firstOrNull { match != null && (it.title.contains(match, true) || it.content.contains(match, true)) }
            ?: return buildJsonObject { put("error", "not_found") }.toString()
        settings.saveStickyNotes(current - target)
        refreshWidget()

        return buildJsonObject { put("deleted", target.id); put("title", target.title) }.toString()
    }

    private fun refreshWidget() {
        try {
            StickyNoteWidgetProvider.refreshAll(app)
        } catch (_: Exception) {
            // 小组件不存在/未注册时忽略
        }
    }

    companion object {
        /** 便签最大保留条数，超出按最旧优先丢弃。 */
        const val MAX_NOTES = 50
    }
}
