package com.orangeisland.app.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.orangeisland.app.OrangeIslandApplication
import com.orangeisland.app.service.DesktopPetService
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the desktop pet after device boot (or app upgrade) IF the user had it
 * enabled. This is the "auto-restore" half of persistence; the [PetController]
 * is the in-process observer that reconciles during normal app runs.
 *
 * Because the persisted `petEnabled` flag lives in DataStore (which loads
 * asynchronously), we read it on a background coroutine rather than blocking
 * [onReceive]. `goAsync()` keeps the broadcast alive briefly while the read
 * completes; we finish it inside the coroutine.
 *
 * Guarded exactly like the controller: only start when enabled, overlay granted,
 * and API 26+. A boot with the permission revoked is a no-op (the user will see
 * the pet disabled in settings next time they open the app).
 */
class PetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!Settings.canDrawOverlays(context)) {
            DebugLog.d(TAG, "Boot: overlay not granted, skipping pet auto-start")
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = context.applicationContext as? OrangeIslandApplication
                val enabled = app?.container?.settingsRepository?.petEnabled?.first() ?: false
                if (enabled) {
                    DebugLog.d(TAG, "Boot: pet enabled, starting service")
                    DesktopPetService.start(context)
                }
            } catch (e: Exception) {
                DebugLog.w(TAG, "Boot pet-start check failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PetBootReceiver"
    }
}
