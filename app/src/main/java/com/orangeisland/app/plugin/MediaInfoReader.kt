package com.orangeisland.app.plugin

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.orangeisland.app.tool.device.DeviceNotificationListenerService
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Shared, dependency-light reader for "what's playing right now", used by both the
 * [com.orangeisland.app.tool.device.MediaToolProvider] tool surface and the plugin WebView bridge
 * (`orangeisland.getMediaInfo()`). Keeping the serialization here in one place guarantees the JSON
 * shape the model sees via the tool and the shape a plugin UI page sees are identical.
 *
 * Session visibility relies on the same authorization path as the notification listener: until the
 * user enables our [DeviceNotificationListenerService] under Notification access,
 * [MediaSessionManager.getActiveSessions] returns at most our own sessions. This helper therefore
 * returns a structured `permission_denied` error rather than an empty success when the listener is
 * not bound, so callers never mistake "no grant" for "nothing playing".
 */
object MediaInfoReader {

    /** Returns now-playing info for the most active session (optionally filtered by package). */
    fun read(context: Context, packageFilter: String?): String {
        val listenerComponent = ComponentName(context, DeviceNotificationListenerService::class.java)
        val controllers = try {
            val msm = context.getSystemService(MediaSessionManager::class.java)
                ?: return error("media_error", "MediaSessionManager unavailable")
            // The notifiedComponent overload asserts our notification-listener authorization.
            // Some OEM ROMs reject it (throwing); fall back to an empty list so the caller sees a
            // clean "no session" instead of a crash.
            runCatching { msm.getActiveSessions(listenerComponent) }.getOrDefault(emptyList())
        } catch (e: Exception) {
            return error("media_error", e.message ?: "getActiveSessions failed")
        }
        if (!DeviceNotificationListenerService.companionActive) {
            return error("permission_denied",
                "Notification access not granted. Enable it under Settings → Device Access so the app can see other apps' media sessions.")
        }
        val pool = if (packageFilter != null) controllers.filter { it.packageName == packageFilter } else controllers
        val target = pool.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            ?: pool.firstOrNull()
            ?: return error("no_active_session",
                if (packageFilter != null) "No active media session for '$packageFilter'."
                else "No active media session.")
        return controllerInfoJson(target).toString()
    }

    private fun controllerInfoJson(c: MediaController) = buildJsonObject {
        val meta = c.metadata
        val state = c.playbackState
        put("packageName", c.packageName)
        meta?.let { m ->
            m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }?.let { put("mediaId", it) }
            m.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            m.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { put("artist", it) }
            m.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { put("album", it) }
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
}
