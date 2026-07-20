package com.orangeisland.app.tool.device

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.PermissionController
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar tool backed by [CalendarContract] (the standard Android ContentProvider).
 *
 * Three tools:
 *  - [list_calendar_events] — upcoming events from now, optional limit (default 10).
 *  - [create_calendar_event] — inserts a new event into the user's primary calendar.
 *  - [delete_calendar_event] — deletes by event id.
 *
 * Times are accepted as ISO-8601 (e.g. '2026-07-20T19:30:00'); the tool parses them as
 * local time. The model gets a structured JSON response; failures (permission, parse,
 * provider error) come back as {'error': ..., 'message': ...}.
 */
class CalendarToolProvider(
    private val app: Application,
    private val permissionController: PermissionController,
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.calendarEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "list_calendar_events",
                description = "List the user's upcoming calendar events starting from now. " +
                    "Returns each event's id, title, start/end time (ISO-8601), location, and " +
                    "description. Use when the user asks 'what's on my calendar', '我今天的日程', etc.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "limit" to ToolProperty("integer", "Max events to return (default 10, max 50).")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "create_calendar_event",
                description = "Create a new event in the user's primary calendar. Times are " +
                    "ISO-8601 local time, e.g. '2026-07-20T19:30:00'. Returns the new event id.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "title" to ToolProperty("string", "Event title."),
                        "start" to ToolProperty("string", "Start time ISO-8601 local, e.g. '2026-07-20T19:30:00'."),
                        "end" to ToolProperty("string", "End time ISO-8601 local. Optional — defaults to start + 1 hour."),
                        "location" to ToolProperty("string", "Optional location text."),
                        "description" to ToolProperty("string", "Optional description / notes.")
                    ),
                    required = listOf("title", "start")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "delete_calendar_event",
                description = "Delete a calendar event by id. Use list_calendar_events first to " +
                    "find the id. Returns {'deleted': true} or an error.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "event_id" to ToolProperty("string", "The numeric event id to delete.")
                    ),
                    required = listOf("event_id")
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (!permissionController.isGranted(PermissionController.Tool.CALENDAR)) {
            return error("permission_denied",
                "Calendar permission not granted. Ask the user to enable Calendar in Settings → Device Access.")
        }
        return when (name) {
            "list_calendar_events" -> listEvents(arguments)
            "create_calendar_event" -> createEvent(arguments)
            "delete_calendar_event" -> deleteEvent(arguments)
            else -> unknownTool(name)
        }
    }

    override fun handles(name: String): Boolean =
        name in setOf("list_calendar_events", "create_calendar_event", "delete_calendar_event")

    // ── Internals ─────────────────────────────────────────────

    private fun listEvents(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val limit = ((parsed["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 10).coerceIn(1, 50)
        val now = System.currentTimeMillis()
        val cols = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ALL_DAY
        )
        val events = mutableListOf<JsonObject_stub>()
        try {
            app.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, cols,
                "${CalendarContract.Events.DTSTART} >= ?",
                arrayOf(now.toString()),
                "${CalendarContract.Events.DTSTART} ASC LIMIT $limit"
            )?.use { c ->
                while (c.moveToNext()) {
                    events.add(JsonObject_stub(
                        id = c.getLong(0),
                        title = c.getString(1) ?: "",
                        start = c.getLong(2),
                        end = c.getLong(3).takeIf { it > 0 } ?: (c.getLong(2) + 60 * 60 * 1000),
                        location = c.getString(4) ?: "",
                        description = c.getString(5) ?: "",
                        allDay = c.getInt(6) == 1
                    ))
                }
            }
        } catch (e: Exception) {
            DebugLog.e("CalendarTool", "list query failed", e)
            return error("query_failed", "Calendar query failed: ${e.message}")
        }
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }
        val arr = buildJsonArray {
            events.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id)
                    put("title", e.title)
                    put("start", iso.format(java.util.Date(e.start)))
                    put("end", iso.format(java.util.Date(e.end)))
                    if (e.location.isNotBlank()) put("location", e.location)
                    if (e.description.isNotBlank()) put("description", e.description)
                    put("all_day", e.allDay)
                })
            }
        }
        return buildJsonObject { put("events", arr); put("count", events.size) }.toString()
    }

    private fun createEvent(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val title = (parsed["title"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: return error("no_title", "Missing 'title'.")
        val startStr = (parsed["start"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: return error("no_start", "Missing 'start'.")
        val start = parseIsoLocal(startStr) ?: return error("bad_start", "Could not parse 'start' as ISO-8601 local time.")
        val end = (parsed["end"] as? JsonPrimitive)?.content?.let { parseIsoLocal(it) } ?: (start + 60 * 60 * 1000)
        val location = (parsed["location"] as? JsonPrimitive)?.content ?: ""
        val description = (parsed["description"] as? JsonPrimitive)?.content ?: ""

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            if (location.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
            if (description.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.CALENDAR_ID, primaryCalendarId()
                ?: return error("no_calendar", "No writable calendar found on device."))
        }
        return try {
            val uri: Uri? = app.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val id = uri?.let { ContentUris.parseId(it) }
                ?: return error("insert_failed", "Calendar insert returned no uri.")
            buildJsonObject { put("created", true); put("event_id", id) }.toString()
        } catch (e: Exception) {
            DebugLog.e("CalendarTool", "create failed", e)
            error("insert_failed", "Calendar insert failed: ${e.message}")
        }
    }

    private fun deleteEvent(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val idStr = (parsed["event_id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: return error("no_event_id", "Missing 'event_id'.")
        val id = idStr.toLongOrNull() ?: return error("bad_event_id", "'event_id' must be numeric.")
        return try {
            val deleted = app.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null
            )
            if (deleted > 0) buildJsonObject { put("deleted", true); put("event_id", id) }.toString()
            else error("not_found", "No event with id $id.")
        } catch (e: Exception) {
            DebugLog.e("CalendarTool", "delete failed", e)
            error("delete_failed", "Calendar delete failed: ${e.message}")
        }
    }

    /** Returns the id of the first writable calendar (primary preferred), or null. */
    private fun primaryCalendarId(): Long? {
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        )
        var best: Long? = null
        try {
            app.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val access = c.getInt(1)
                    if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue  // need write
                    val id = c.getLong(0)
                    val isPrimary = c.getInt(2) == 1
                    if (isPrimary) return id
                    if (best == null) best = id
                }
            }
        } catch (_: Exception) { }
        return best
    }

    private fun parseIsoLocal(s: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .parse(s)?.time
    } catch (e: Exception) { null }

    private data class JsonObject_stub(
        val id: Long, val title: String, val start: Long, val end: Long,
        val location: String, val description: String, val allDay: Boolean
    )

    private fun error(type: String, message: String): String =
        buildJsonObject { put("error", type); put("message", message) }.toString()

    private fun unknownTool(name: String): String =
        buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
}
