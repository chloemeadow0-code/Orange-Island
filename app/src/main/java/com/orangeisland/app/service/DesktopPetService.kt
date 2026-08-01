package com.orangeisland.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.orangeisland.app.MainActivity
import com.orangeisland.app.OrangeIslandApplication
import com.orangeisland.app.R
import com.orangeisland.app.pet.PetEventBus
import com.orangeisland.app.pet.PetView
import com.orangeisland.app.util.CrashReporter
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service that draws the Mikan desktop pet as a floating overlay over
 * other apps, keeping it alive while the app process runs and (via START_STICKY +
 * [com.orangeisland.app.pet.PetBootReceiver]) after reboots.
 *
 * Lifecycle mirrors [WorkflowKeepAliveService]: it promotes itself to the
 * foreground first (so the process is allowed to draw an overlay even when
 * backgrounded), then — only if [Settings.canDrawOverlays] is granted — adds the
 * [PetView] window. If the overlay permission is missing the service still runs
 * (foreground notification shown) but renders nothing; the controller stops it
 * once the user toggles the pet off.
 *
 * The window uses [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY], which
 * requires API 26+. The pet feature is gated to API 26+ at the controller level;
 * on older devices this service is never started.
 */
class DesktopPetService : Service(), PetView.Host {

    private lateinit var windowManager: WindowManager
    private var petView: PetView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var addedToWindow = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var eventsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Channel MUST exist before we build the notification, and startForeground MUST succeed
        // before onCreate returns — otherwise the system throws
        // ForegroundServiceDidNotStartInTimeException and kills the whole process (which on a
        // debug build takes the plugin page down with it). If promoting to foreground fails for
        // any reason we stop ourselves immediately so the system sees a clean stop, not a timeout.
        createChannel(this)
        if (!startForegroundCompat()) {
            DebugLog.w(TAG, "startForeground failed; stopping service to avoid ANR/crash")
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!hasOverlayPermission()) {
            DebugLog.w(TAG, "Overlay permission not granted; pet will stay invisible")
            return
        }
        addPetWindow()
        observeEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: the system may restart us after a kill; [PetController]
        // reconciles with the persisted petEnabled flag, so a spurious restart is
        // harmless (the controller will stop us if the user turned the pet off).
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        eventsJob?.cancel()
        scope.cancel()
        removePetWindow()
        super.onDestroy()
    }

    // ── PetView.Host ──────────────────────────────────────────
    override fun moveBy(dx: Int, dy: Int) {
        val params = windowParams ?: return
        params.x += dx
        params.y += dy
        clampToScreen(params)
        try {
            if (addedToWindow) windowManager.updateViewLayout(petView, params)
        } catch (e: Exception) {
            DebugLog.w(TAG, "updateViewLayout failed", e)
        }
    }

    override fun onDoubleTap() {
        // Open the chat — the pet is a friendly shortcut back into the app.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { startActivity(intent) }
    }

    override fun onSleepChanged(asleep: Boolean) {
        DebugLog.d(TAG, if (asleep) "pet fell asleep" else "pet woke up")
    }

    // ── Window setup ──────────────────────────────────────────
    private fun addPetWindow() {
        if (petView != null) return
        val view = PetView(this).apply {
            attachHost(this@DesktopPetService)
            prime()
            setOnLongClickListener {
                // Long-press: quick toast hint, and let the controller handle a
                // future "hide" affordance. For now just confirm it's alive.
                Toast.makeText(context, getString(R.string.pet_name), Toast.LENGTH_SHORT).show()
                true
            }
        }
        val (savedX, savedY) = loadSavedPosition()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        try {
            windowManager.addView(view, params)
            petView = view
            windowParams = params
            addedToWindow = true
            DebugLog.d(TAG, "Pet window added at ($savedX,$savedY)")
        } catch (e: Exception) {
            CrashReporter.note("DesktopPetService.addView threw ${e.javaClass.simpleName}")
            DebugLog.w(TAG, "addView failed", e)
        }
    }

    private fun removePetWindow() {
        val view = petView ?: return
        try {
            if (addedToWindow) windowManager.removeView(view)
        } catch (e: Exception) {
            DebugLog.w(TAG, "removeView failed", e)
        }
        petView = null
        windowParams = null
        addedToWindow = false
    }

    /** Keep the pet on-screen after a drag/walk nudges it past an edge. */
    private fun clampToScreen(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val halfW = (petView?.measuredWidth ?: 0) / 2
        val halfH = (petView?.measuredHeight ?: 0) / 2
        val minX = -halfW
        val maxX = metrics.widthPixels - halfW
        val minY = 0
        val maxY = metrics.heightPixels - halfH
        if (params.x < minX) params.x = minX
        if (params.x > maxX) params.x = maxX
        if (params.y < minY) params.y = minY
        if (params.y > maxY) params.y = maxY
    }

    private fun loadSavedPosition(): Pair<Int, Int> {
        val repo = (application as OrangeIslandApplication).container.settingsRepository
        val x = repo.petPosX.value
        val y = repo.petPosY.value
        val metrics = resources.displayMetrics
        // Int.MIN_VALUE sentinel = never set → default to lower-right corner.
        val defaultX = metrics.widthPixels - (120 * resources.displayMetrics.density).toInt()
        val defaultY = metrics.heightPixels - (180 * resources.displayMetrics.density).toInt()
        return (if (x == Int.MIN_VALUE) defaultX else x) to (if (y == Int.MIN_VALUE) defaultY else y)
    }

    // ── Event bus → pet reactions ─────────────────────────────
    private fun observeEvents() {
        eventsJob = scope.launch {
            PetEventBus.events.collect { event ->
                val view = petView ?: run {
                    DebugLog.d(TAG, "event arrived but petView is null: $event")
                    return@collect
                }
                DebugLog.d(TAG, "event received: $event")
                when (event) {
                    is PetEventBus.Event.Bubble -> {
                        // TG-style nudge: shake the pet in place to flag the new reply,
                        // then surface the text as a bubble.
                        view.wobble()
                        showBubbleToast(event.text)
                    }
                    is PetEventBus.Event.Expression -> view.setExpression(event.name)
                    PetEventBus.Event.Wave -> {
                        view.wobble()
                        view.setExpression("wave")
                    }
                }
            }
        }
    }

    private fun showBubbleToast(text: String) {
        mainHandler().post {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mainHandler() = android.os.Handler(android.os.Looper.getMainLooper())

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || Settings.canDrawOverlays(this)

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ── Foreground notification (mirrors WorkflowKeepAliveService) ──
    /** Promotes the service to foreground. Returns true on success, false on failure — callers
     *  must [stopSelf] on false so the system sees a clean stop instead of waiting for the
     *  foreground-start timeout and then killing the process. */
    private fun startForegroundCompat(): Boolean {
        val notification = try {
            buildNotification()
        } catch (e: Exception) {
            CrashReporter.note("DesktopPetService.buildNotification threw ${e.javaClass.simpleName}")
            DebugLog.w(TAG, "buildNotification failed", e)
            return false
        }
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType()
            )
            true
        } catch (e: Exception) {
            CrashReporter.note("DesktopPetService.startForeground threw ${e.javaClass.simpleName}")
            DebugLog.w(TAG, "startForeground failed", e)
            false
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.pet_notification_text))
        .setSmallIcon(R.drawable.ic_notification)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(createPendingIntent(this))
        .build()

    private fun createPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else 0

    companion object {
        private const val TAG = "DesktopPetService"
        const val CHANNEL_ID = "orangeisland_desktop_pet"
        private const val NOTIFICATION_ID = 5

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, DesktopPetService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                DebugLog.d(TAG, "start requested")
            } catch (e: RuntimeException) {
                CrashReporter.note("DesktopPetService.start threw ${e.javaClass.simpleName}")
                DebugLog.w(TAG, "Failed to start pet service", e)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, DesktopPetService::class.java)
            try {
                appContext.stopService(intent)
                DebugLog.d(TAG, "stop requested")
            } catch (e: RuntimeException) {
                DebugLog.w(TAG, "Failed to stop pet service", e)
            }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pet_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.pet_notification_text)
                setShowBadge(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
