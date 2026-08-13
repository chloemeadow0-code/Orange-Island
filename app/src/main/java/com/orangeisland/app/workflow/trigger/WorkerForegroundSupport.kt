package com.orangeisland.app.workflow.trigger

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.orangeisland.app.R
import com.orangeisland.app.service.OrangeIslandForegroundService

/**
 * Shared helper for promoting CoroutineWorkers to foreground execution while they
 * run LLM generation or other long-running tasks.
 *
 * Reuses the existing generation-notification channel from [OrangeIslandForegroundService]
 * so we don't create duplicate low-priority channels, but uses a distinct notification
 * ID so the worker notification does not collide with the generation service notification.
 */
const val WORKER_FGS_NOTIFICATION_ID = 3

fun buildWorkerForegroundInfo(context: Context, tag: String): ForegroundInfo {
    OrangeIslandForegroundService.createChannel(context)
    val notification = buildWorkerNotification(context, tag)
    val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
        0
    }
    return ForegroundInfo(WORKER_FGS_NOTIFICATION_ID, notification, foregroundType)
}

private fun buildWorkerNotification(context: Context, tag: String): Notification {
    return NotificationCompat.Builder(context, OrangeIslandForegroundService.CHANNEL_ID)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.oi_keepalive_worker_notification_title, tag))
        .setSmallIcon(R.drawable.ic_notification)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()
}
