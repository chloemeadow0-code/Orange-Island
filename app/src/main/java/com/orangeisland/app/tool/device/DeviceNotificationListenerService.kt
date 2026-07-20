package com.orangeisland.app.tool.device

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.orangeisland.app.util.DebugLog
import java.util.ArrayDeque

/**
 * NotificationListenerService that captures active notifications into an in-memory ring buffer
 * for the NotificationToolProvider to read on demand.
 *
 * The service only runs while the user has enabled it under
 * Settings → Notifications & status bar → Notification access (the system surfaces this via
 * [android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS]). Until then the buffer
 * stays empty and [companionActive] returns false.
 *
 * We keep the last [BUFFER_SIZE] distinct (package, id, tag) notifications, with the most
 * recent extras (title, text, post time). Sized small because the model only needs a current
 * snapshot, not history.
 */
class DeviceNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        DebugLog.d(TAG, "listener connected")
        instance = this
        // Seed the buffer with what's already active on connection (e.g. app restarted while
        // notifications were pending). Active notifications are only delivered on Q+.
        runCatching {
            activeNotifications?.forEach { onNotificationPosted(it) }
        }
    }

    override fun onListenerDisconnected() {
        DebugLog.d(TAG, "listener disconnected")
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val entry = CapturedNotification(
            packageName = sbn.packageName,
            id = sbn.id,
            tag = sbn.tag ?: "",
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
            postTime = sbn.postTime,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        )
        ringBuffer.add(entry)
        while (ringBuffer.size > BUFFER_SIZE) ringBuffer.poll()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        ringBuffer.removeAll { it.packageName == sbn.packageName && it.id == sbn.id && it.tag == (sbn.tag ?: "") }
    }

    companion object {
        private const val TAG = "NotifListener"
        private const val BUFFER_SIZE = 200

        /** Current listener instance, or null when not bound (user hasn't granted access). */
        @Volatile private var instance: DeviceNotificationListenerService? = null

        /** True when the listener service is currently connected (i.e. user has granted access). */
        val companionActive: Boolean get() = instance != null

        private val ringBuffer: ArrayDeque<CapturedNotification> = ArrayDeque(BUFFER_SIZE)

        /** Snapshot copy of captured notifications, newest first. Safe to call off the main thread. */
        fun snapshot(): List<CapturedNotification> = synchronized(ringBuffer) {
            ringBuffer.reversed().toList()
        }
    }
}

/** Plain data the provider serializes to JSON for the model. */
data class CapturedNotification(
    val packageName: String,
    val id: Int,
    val tag: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isOngoing: Boolean
)
