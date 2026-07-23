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
import android.app.ActivityManager
import android.os.PowerManager
import com.orangeisland.app.MainActivity
import com.orangeisland.app.R
import com.orangeisland.app.util.CrashReporter
import com.orangeisland.app.util.DebugLog
import java.util.concurrent.atomic.AtomicInteger

class OrangeIslandForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "orangeisland_generation_status"
        const val NOTIFICATION_ID = 1
        private const val COMPLETION_CHANNEL_ID = "orangeisland_completed"
        private const val COMPLETION_NOTIFICATION_ID = 2
        private const val TAG = "OrangeIslandForegroundService"
        private var instance: OrangeIslandForegroundService? = null
        private var fallbackWakeLock: PowerManager.WakeLock? = null
        private val activeCount = AtomicInteger(0)

        fun start(context: Context) {
            val appContext = context.applicationContext
            val count = activeCount.incrementAndGet()
            CrashReporter.note("FGS.start count=$count")
            if (count > 1) {
                // Already running from another generation call; no need to re-start.
                return
            }
            if (count <= 0) {
                // Defensive: should never happen because incrementAndGet always returns >= 1
                activeCount.set(1)
            }
            val intent = Intent(appContext, OrangeIslandForegroundService::class.java)
            // Diagnostic trail for the unreproducible "did not start in time" crash:
            // record process importance (foreground vs background) at start.
            val state = try {
                val info = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(info)
                "importance=${info.importance} trim=${info.lastTrimLevel}"
            } catch (e: Exception) { "importance=?" }
            CrashReporter.note("FGS.start api=${Build.VERSION.SDK_INT} $state count=$count")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                CrashReporter.note("FGS.startForegroundService ok count=$count")
            } catch (e: RuntimeException) {
                CrashReporter.note("FGS.startForegroundService threw ${e.javaClass.simpleName} count=$count")
                DebugLog.w(TAG, "Failed to start foreground service", e)
                // Fallback: acquire a partial WakeLock so generation does not run
                // unprotected when the foreground service cannot be started.
                try {
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                    fallbackWakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "OrangeIsland:generation_fallback"
                    )
                    fallbackWakeLock?.acquire(10 * 60 * 1000L)
                    CrashReporter.note("FGS.fallbackWakeLock acquired count=$count")
                } catch (wl: Exception) {
                    DebugLog.w(TAG, "Failed to acquire fallback WakeLock", wl)
                }
            }
        }

        fun releaseFallbackWakeLock() {
            runCatching {
                fallbackWakeLock?.let {
                    if (it.isHeld) it.release()
                    fallbackWakeLock = null
                }
            }.onFailure { DebugLog.w(TAG, "Failed to release fallback WakeLock", it) }
        }

        fun updateText(text: String) {
            instance?.updateNotificationText(text)
        }

        fun stop(context: Context) {
            val count = activeCount.decrementAndGet()
            CrashReporter.note("FGS.stop count=$count foregroundStarted=${instance?.foregroundStarted}")
            if (count > 0) {
                // Other generation(s) still running; keep the service alive.
                return
            }
            if (count < 0) {
                DebugLog.w(TAG, "Foreground service stop() called more times than start() (count=$count). Resetting to 0.")
                activeCount.set(0)
            }
            val intent = Intent(context, OrangeIslandForegroundService::class.java)
            context.stopService(intent)
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification while Orange Island is generating"
                setShowBadge(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        fun showCompletionNotification(context: Context, responseText: String) {
            createCompletionChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.oi_responded))
                .setContentText(if (responseText.length > 200) responseText.take(200) + "…" else responseText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(createPendingIntent(context, 1))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        if (responseText.length > 200) responseText.take(200) + "…" else responseText
                    )
                )
                .build()

            try {
                manager.notify(COMPLETION_NOTIFICATION_ID, notification)
            } catch (e: RuntimeException) {
                DebugLog.w(TAG, "Failed to show completion notification", e)
            }
        }

        private fun createCompletionChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Response Ready",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown when a response finishes generating"
                setShowBadge(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        private fun createPendingIntent(context: Context, requestCode: Int): PendingIntent {
            return PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private var currentText: String = "Generating response…"
    private var foregroundStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.note("FGS.onCreate")
        createChannel(this)
        val notification = buildGenerationNotification(currentText)
        // Must NOT catch exceptions here: if startForeground() fails, the real
        // exception (SecurityException, ForegroundServiceStartNotAllowed, etc.)
        // must propagate so Crashlytics/logs capture it. Catching + stopSelf()
        // leaves the system's 5-second timeout to fire, which only surfaces the
        // useless ForegroundServiceDidNotStartInTimeException instead.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType()
        )
        foregroundStarted = true
        CrashReporter.note("FGS.startForeground ok")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() already called in onCreate(); no re-promote needed.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    private fun updateNotificationText(text: String) {
        currentText = text
        if (!foregroundStarted) return
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildGenerationNotification(text))
        } catch (e: RuntimeException) {
            DebugLog.w(TAG, "Failed to update notification", e)
        }
    }

    private fun buildGenerationNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(createPendingIntent(this, 0))
            .build()
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
    }

    override fun onTimeout(type: Int, reason: Int) {
        CrashReporter.note("FGS.onTimeout type=$type reason=$reason")
        DebugLog.w(TAG, "Foreground service timed out: type=$type reason=$reason")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
