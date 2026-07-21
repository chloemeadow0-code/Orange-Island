package com.orangeisland.app.workflow.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orangeisland.app.util.DebugLog

/**
 * Manifest-declared receiver for boot / package-replace broadcasts. Forwards to
 * [BootSignalSource.onBoot], which enqueues a [BootFireWorker] (the durable fire path; receivers
 * are time-limited, WorkManager is the documented way to do background work off a boot broadcast).
 *
 * Two actions are accepted:
 *  - BOOT_COMPLETED — fired once after the device finishes booting.
 *  - MY_PACKAGE_REPLACED — fired when the app is upgraded; lets a boot-triggered workflow run
 *    again after an update without waiting for the next reboot.
 *
 * Independent implementation.
 */
class WorkflowBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DebugLog.d(TAG, "received ${intent.action}, enqueuing boot fire worker(s)")
                BootSignalSource.onBoot(context)
            }
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "WorkflowBootReceiver"
    }
}
