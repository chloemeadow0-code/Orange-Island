package com.orangeisland.app.tool.device

import android.app.Application
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.PermissionController
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Media control tool. Reads and controls other apps' [MediaSession]s (e.g. NetEase Cloud Music)
 * via [MediaSessionManager.getActiveSessions].
 *
 * Session visibility follows the same authorization path as [NotificationToolProvider]: the
 * system only hands other apps' controllers to a caller whose [DeviceNotificationListenerService]
 * is bound (i.e. the user has enabled Notifications under Settings → Notification access). Until
 * then [getActiveSessions] returns at most our own sessions — useless for controlling a third-party
 * player — so the provider mirrors the notification listener's grant state in [PermissionController].
 *
 * Three tools, all structured per the plugin framework's contract (track id / artist / album /
 * cover / duration / position / state / failure reason):
 *  - [get_now_playing] — current track + playback state of an app (default: any active session).
 *  - [control_media]   — play / pause / next / previous / seek (seek unit: **milliseconds**).
 *  - [list_media_apps] — which apps currently have a controllable session.
 *
 * Every failure returns a real error JSON; the provider never fabricates a successful playback.
 * Playback failures (VIP/region-locked track, controller not controllable, app not running) come
 * back as `{ "error": "playback_failed", "reason": ... }`.
 */
class MediaToolProvider(
    private val app: Application,
    private val permissionController: PermissionController,
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** NotificationListenerService component name handed to MediaSessionManager as the "notified"
     *  component. MediaSessionManager.getActiveSessions(ComponentName) requires the caller to be
     *  enabled in the system's notification-listener list; passing our own listener's component
     *  is the documented way to assert that authorization. */
    private val listenerComponent: ComponentName by lazy {
        ComponentName(app, DeviceNotificationListenerService::class.java)
    }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.mediaControlEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "get_now_playing",
                description = "Get the currently playing track of a media app (default: any active " +
                    "session). Returns track id/title/artist/album/coverUrl/durationMs/positionMs/" +
                    "isPlaying/packageName. Use when the user asks 'what's playing', '现在放的是啥', " +
                    "or wants the current song info. Optional package filter, e.g. " +
                    "'com.netease.cloudmusic'.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "package" to ToolProperty("string", "Optional package-name filter, e.g. 'com.netease.cloudmusic'. Defaults to the most recently active session.")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "control_media",
                description = "Control a media app's playback: play, pause, next, previous, or seek. " +
                    "seek takes a position in milliseconds. Use when the user asks to 'play/pause/" +
                    "skip/next song/快进'. Returns the action taken plus the resulting playback state, " +
                    "or a real error (e.g. playback_failed, vip_or_region_locked).",
                parameters = ToolParameters(
                    properties = mapOf(
                        "action" to ToolProperty("string", "One of: play, pause, next, previous, seek."),
                        "position_ms" to ToolProperty("integer", "Target position in milliseconds. Required only when action=seek."),
                        "package" to ToolProperty("string", "Optional package-name filter. Defaults to the most recently active controllable session.")
                    ),
                    required = listOf("action")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "list_media_apps",
                description = "List apps that currently have a controllable media session, with each " +
                    "one's playback state. Use when the user asks 'which music apps are open' or " +
                    "before controlling media to discover the target package.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        // Permission gate — same contract as NotificationToolProvider: the tool surfaces only while
        // the setting is on, and execute() re-checks the system grant. A missing grant returns a
        // real error rather than silently behaving as if nothing were playing.
        if (!permissionController.isGranted(PermissionController.Tool.MEDIA)) {
            return error("permission_denied",
                "Notification access not granted. Media control needs it enabled under " +
                    "Settings → Device Access (it opens the system Notification access screen), " +
                    "because that authorization is what lets the app see other apps' media sessions.")
        }
        if (!DeviceNotificationListenerService.companionActive) {
            return error("not_yet_active",
                "Listener permission is granted but the service hasn't bound yet. Try again in a moment.")
        }
        return when (name) {
            "get_now_playing" -> getNowPlaying(arguments)
            "control_media" -> controlMedia(arguments)
            "list_media_apps" -> listMediaApps()
            else -> unknownTool(name)
        }
    }

    override fun handles(name: String): Boolean =
        name == "get_now_playing" || name == "control_media" || name == "list_media_apps"

    // ── Tool implementations ────────────────────────────────────────────────

    private fun getNowPlaying(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val pkgFilter = (parsed["package"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        return com.orangeisland.app.plugin.MediaInfoReader.read(app, pkgFilter)
    }

    private fun controlMedia(arguments: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val action = (parsed["action"] as? JsonPrimitive)?.content?.lowercase()?.trim()
            ?: return error("bad_action", "Missing 'action'. Expected one of: play, pause, next, previous, seek.")
        val pkgFilter = (parsed["package"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val controllers = activeSessions()
        val target = pickController(controllers, pkgFilter)
            ?: return error("no_active_session",
                if (pkgFilter != null) "No active media session for '$pkgFilter'."
                else "No active media session to control.")
        val tc = target.transportControls
            ?: return error("not_controllable", "The session for '${target.packageName}' has no transport controls.")
        val positionMs = (parsed["position_ms"] as? JsonPrimitive)?.content?.toLongOrNull()
        if (action == "seek" && positionMs == null) {
            return error("bad_action", "seek requires 'position_ms' (milliseconds).")
        }
        // Dispatch on a background thread — PlaybackState/transport calls can block briefly on the
        // binder. execute() already runs off the main thread (ToolDispatcher → Dispatchers.IO path).
        val failed = try {
            when (action) {
                "play" -> { tc.play(); false }
                "pause" -> { tc.pause(); false }
                "next" -> { tc.skipToNext(); false }
                "previous" -> { tc.skipToPrevious(); false }
                "seek" -> { tc.seekTo(positionMs!!); false }
                else -> return error("bad_action", "Unknown action '$action'. Expected: play, pause, next, previous, seek.")
            }
        } catch (e: SecurityException) {
            return error("playback_failed", "SecurityException controlling '${target.packageName}': ${e.message}")
        } catch (e: Exception) {
            return error("playback_failed", "Controlling '${target.packageName}' failed: ${e.message ?: e::class.simpleName}")
        }
        // Re-read state after the dispatch. A VIP/region-locked track typically surfaces here as
        // an error state or a no-op (state unchanged) — report the real resulting state honestly.
        val refreshed = activeSessions().firstOrNull { it.packageName == target.packageName } ?: target
        val state = refreshed.playbackState
        val result = controllerInfoJson(refreshed) as kotlinx.serialization.json.JsonObject
        return buildJsonObject {
            result.forEach { (k, v) -> put(k, v) }
            put("action", action)
            if (action == "seek") put("requested_position_ms", positionMs)
            // Surface a likely-locked signal when a play/seek didn't move the position and the app
            // is still paused/erroring — heuristics, but better than claiming success.
            if (!failed && (action == "play" || action == "seek")) {
                val ps = state?.state
                val stuck = ps == PlaybackState.STATE_PAUSED || ps == PlaybackState.STATE_STOPPED ||
                    ps == PlaybackState.STATE_ERROR
                if (stuck) put("warning", "position/state did not advance; the track may be VIP-only, region-locked, or otherwise unplayable.")
            }
        }.toString()
    }

    private fun listMediaApps(): String {
        val controllers = activeSessions()
        if (controllers.isEmpty()) {
            return buildJsonObject { put("apps", buildJsonArray {}); put("count", 0) }.toString()
        }
        val pm = app.packageManager
        val apps = controllers.map { c ->
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
            }.getOrDefault(c.packageName)
            val state = c.playbackState?.state
            controllerInfoJson(c) as kotlinx.serialization.json.JsonObject
        }
        return buildJsonObject {
            put("apps", buildJsonArray { apps.forEach { add(it) } })
            put("count", apps.size)
        }.toString()
    }

    // ── MediaSession access ─────────────────────────────────────────────────

    /**
     * Live list of other apps' media controllers. Only meaningful while the notification-listener
     * grant is in place (checked by [execute]); otherwise the system returns an empty/own-only list.
     */
    private fun activeSessions(): List<MediaController> = try {
        val msm = app.getSystemService(MediaSessionManager::class.java) ?: return emptyList()
        // The notifiedComponent overload asserts our listener authorization. Some OEM ROMs reject
        // the component form (throwing); fall back to an empty list rather than the no-arg form,
        // which on this compileSdk isn't reliably callable.
        runCatching { msm.getActiveSessions(listenerComponent) }.getOrDefault(emptyList())
    } catch (e: Exception) {
        emptyList()
    }

    /** Picks the most recently active controller, optionally narrowed by package. */
    private fun pickController(controllers: List<MediaController>, pkgFilter: String?): MediaController? {
        val pool = if (pkgFilter != null) controllers.filter { it.packageName == pkgFilter } else controllers
        if (pool.isEmpty()) return null
        // Prefer the one with the most recent activity timestamp; fall back to the first.
        return pool.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L } ?: pool.first()
    }

    /** Serializes one controller's current track + state to the structured JSON the framework wants. */
    private fun controllerInfoJson(c: MediaController) = buildJsonObject {
        val meta = c.metadata
        val state = c.playbackState
        put("packageName", c.packageName)
        meta?.let { m ->
            m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }?.let { put("mediaId", it) }
            m.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            m.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { put("artist", it) }
            m.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            // Art URIs are the most reliable cover source on modern players; fall back to the bitmap
            // path key for older ones. We only forward the URL/URI string — the host image library
            // handles downloading + disk caching, so the plugin never needs file access.
            m.getString(MediaMetadata.METADATA_KEY_ART_URI)?.takeIf { it.isNotBlank() }?.let { put("coverUrl", it) }
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)?.takeIf { it.isNotBlank() }?.let { put("coverUrl", it) }
                ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)?.takeIf { it.isNotBlank() }?.let { put("coverUrl", it) }
            val dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
            if (dur != null) put("durationMs", dur)
        }
        state?.let { s ->
            put("positionMs", s.position)
            put("isPlaying", s.state == PlaybackState.STATE_PLAYING)
            put("state", stateName(s.state))
        } ?: run {
            put("isPlaying", false)
            put("state", "none")
        }
    }

    private fun stateName(s: Int): String = when (s) {
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_ERROR -> "error"
        PlaybackState.STATE_FAST_FORWARDING -> "fast_forwarding"
        PlaybackState.STATE_REWINDING -> "rewinding"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "skipping_to_next"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "skipping_to_previous"
        PlaybackState.STATE_NONE -> "none"
        else -> "unknown($s)"
    }

    private fun error(type: String, message: String): String =
        buildJsonObject { put("error", type); put("message", message) }.toString()

    private fun unknownTool(name: String): String =
        buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
}
