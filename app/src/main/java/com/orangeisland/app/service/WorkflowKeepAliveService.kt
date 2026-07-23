package com.orangeisland.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.orangeisland.app.MainActivity
import com.orangeisland.app.R
import com.orangeisland.app.util.CrashReporter
import com.orangeisland.app.util.DebugLog

/**
 * Long-running foreground service that keeps the Orange Island process alive so
 * dynamically-registered broadcast receivers (WiFi, power, headphones, screen,
 * battery, Bluetooth) and app-foreground / notification listeners remain functional.
 *
 * This service is started only when at least one enabled linear workflow depends on
 * a trigger that requires process residency. It is stopped as soon as no such
 * workflow remains, so it does not waste battery when unnecessary.
 *
 * Uses START_STICKY (unlike the generation foreground service which uses
 * START_NOT_STICKY) because the system is allowed to restart this service if it
 * kills the process under memory pressure.
 */
class WorkflowKeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "orangeisland_workflow_keepalive"
        private const val NOTIFICATION_ID = 4
        private const val TAG = "WorkflowKeepAliveService"

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, WorkflowKeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                DebugLog.d(TAG, "start requested")
            } catch (e: RuntimeException) {
                CrashReporter.note("WorkflowKeepAliveService.start threw ${e.javaClass.simpleName}")
                DebugLog.w(TAG, "Failed to start keep-alive service", e)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, WorkflowKeepAliveService::class.java)
            try {
                appContext.stopService(intent)
                DebugLog.d(TAG, "stop requested")
            } catch (e: RuntimeException) {
                DebugLog.w(TAG, "Failed to stop keep-alive service", e)
            }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.oi_keepalive_title),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.oi_keepalive_notification_title)
                setShowBadge(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        private fun createPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val notification = buildNotification()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType()
            )
            DebugLog.d(TAG, "startForeground ok")
        } catch (e: Exception) {
            // Do not crash: this is a best-effort keep-alive. If startForeground fails
            // (e.g. on some custom ROMs), the service still runs as a regular service
            // for a short window which is better than nothing.
            CrashReporter.note("WorkflowKeepAliveService.startForeground threw ${e.javaClass.simpleName}")
            DebugLog.w(TAG, "startForeground failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.oi_keepalive_notification_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(createPendingIntent(this))
            .build()
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    override fun onTimeout(type: Int, reason: Int) {
        CrashReporter.note("WorkflowKeepAliveService.onTimeout type=$type reason=$reason")
        DebugLog.w(TAG, "Foreground service timed out: type=$type reason=$reason")
        stopSelf()
    }
}
