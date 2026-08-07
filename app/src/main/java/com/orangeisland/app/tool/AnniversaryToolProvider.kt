package com.orangeisland.app.tool

import android.app.Application
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.AnniversaryEntry
import com.orangeisland.app.data.AnniversaryUtils
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lets the model read/manage the user's built-in anniversaries (纪念日): 在一起纪念日、生日,
 * or any recurring/one-time date the user wants remembered. Backed by the same DataStore key
 * [SettingsManager.anniversaries] the settings UI reads, via its own [SettingsManager] instance
 * (cheap to construct — same pattern as other background-safe tool providers).
 *
 * Awareness for "the model brings it up on its own" is handled separately, as a suffix appended
 * to the system prompt in [com.orangeisland.app.viewmodel.GenerationRequestBuilder] — these tools
 * are for when the model needs to actively look up / add / remove an entry mid-conversation.
 */
class AnniversaryToolProvider(private val app: Application) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val settings by lazy { SettingsManager(app) }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunction(
            name = "list_anniversaries",
            description = "List every anniversary/important date the user has saved (纪念日/生日/等), " +
                "each with how many days until its next occurrence and (for recurring ones) which " +
                "year it'll be. Use when the user asks about a date, or before adding a new one to " +
                "check for duplicates.",
            parameters = ToolParameters(properties = emptyMap(), required = emptyList())
        )),
        ToolDefinition(function = ToolFunction(
            name = "add_anniversary",
            description = "Save a new anniversary/important date. Use when the user tells you a date " +
                "to remember (e.g. '记住我们1月3号在一起的').",
            parameters = ToolParameters(
                properties = mapOf(
                    "name" to ToolProperty("string", "Short label for this date, e.g. '在一起纪念日', '猫猫生日'."),
                    "year" to ToolProperty("integer", "Year of the original/anchor date, e.g. 2023."),
                    "month" to ToolProperty("integer", "Month 1-12."),
                    "day" to ToolProperty("integer", "Day 1-31."),
                    "recurring" to ToolProperty("string", "\"true\" if it repeats every year on month/day (anniversaries, birthdays), \"false\" if it's a one-time date. Defaults to true.")
                ),
                required = listOf("name", "year", "month", "day")
            )
        )),
        ToolDefinition(function = ToolFunction(
            name = "delete_anniversary",
            description = "Remove a saved anniversary by its id (from list_anniversaries) or exact name.",
            parameters = ToolParameters(
                properties = mapOf(
                    "id" to ToolProperty("string", "The entry's id, from list_anniversaries."),
                    "name" to ToolProperty("string", "Exact name match, used only if id is omitted.")
                ),
                required = emptyList()
            )
        ))
    )

    override fun handles(name: String): Boolean =
        name == "list_anniversaries" || name == "add_anniversary" || name == "delete_anniversary"

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String = when (name) {
        "list_anniversaries" -> listAnniversaries()
        "add_anniversary" -> addAnniversary(arguments)
        "delete_anniversary" -> deleteAnniversary(arguments)
        else -> buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
    }

    private suspend fun listAnniversaries(): String {
        val entries = settings.anniversaries.first().sortedBy { AnniversaryUtils.daysUntilNext(it) }
        return buildJsonObject {
            put("count", entries.size)
            put("anniversaries", buildJsonArray {
                entries.forEach { e ->
                    add(buildJsonObject {
                        put("id", e.id)
                        put("name", e.name)
                        put("date", AnniversaryUtils.formatDate(e))
                        put("recurring", e.recurring)
                        put("daysUntilNext", AnniversaryUtils.daysUntilNext(e))
                        if (e.recurring) put("yearNumber", AnniversaryUtils.yearsSince(e))
                    })
                }
            })
        }.toString()
    }

    private suspend fun addAnniversary(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        fun str(k: String) = (parsed[k] as? JsonPrimitive)?.content
        val name = str("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: return buildJsonObject { put("error", "bad_args"); put("message", "name is required") }.toString()
        val year = str("year")?.toIntOrNull()
        val month = str("month")?.toIntOrNull()
        val day = str("day")?.toIntOrNull()
        if (year == null || month !in 1..12 || day !in 1..31) {
            return buildJsonObject { put("error", "bad_args"); put("message", "year/month/day invalid") }.toString()
        }
        val recurring = str("recurring")?.equals("false", ignoreCase = true)?.not() ?: true
        val entry = AnniversaryEntry(name = name, year = year, month = month!!, day = day!!, recurring = recurring)
        settings.saveAnniversaries(settings.anniversaries.first() + entry)
        return buildJsonObject {
            put("id", entry.id)
            put("name", entry.name)
            put("date", AnniversaryUtils.formatDate(entry))
            put("daysUntilNext", AnniversaryUtils.daysUntilNext(entry))
        }.toString()
    }

    private suspend fun deleteAnniversary(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val id = (parsed["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val name = (parsed["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        if (id == null && name == null) {
            return buildJsonObject { put("error", "bad_args"); put("message", "provide id or name") }.toString()
        }
        val current = settings.anniversaries.first()
        val target = current.firstOrNull { it.id == id } ?: current.firstOrNull { it.name == name }
            ?: return buildJsonObject { put("error", "not_found") }.toString()
        settings.saveAnniversaries(current - target)
        return buildJsonObject { put("deleted", target.id); put("name", target.name) }.toString()
    }
}
