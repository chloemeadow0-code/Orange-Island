package com.orangeisland.app.tool.device

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * NotificationListenerService skeleton.
 *
 * Declared here (and registered in AndroidManifest.xml in the notification commit) so the
 * [com.orangeisland.app.viewmodel.PermissionController] can reference its [ComponentName] for
 * the "is this listener enabled?" check before the actual capture logic is wired in.
 *
 * The full implementation (capturing active notifications into a ring buffer that the
 * NotificationToolProvider reads) lands in a follow-up commit. For now this is an empty
 * listener — it binds cleanly but does nothing.
 */
class DeviceNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {}
    override fun onListenerDisconnected() {}
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
