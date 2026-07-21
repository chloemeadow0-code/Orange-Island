package com.orangeisland.app.workflow.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orangeisland.app.util.DebugLog

/**
 * Manifest-declared receiver for boot / package-replace broadcasts. Forwards to
 * [WorkflowBootDispatcher.onBoot]; the dispatcher is a no-op until the [BootTriggerFamily] is
 * bound by [com.orangeisland.app.di.AppContainer], which happens once the app process is up.
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
                DebugLog.d(TAG, "received ${intent.action}, dispatching to boot family")
                WorkflowBootDispatcher.onBoot()
            }
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "WorkflowBootReceiver"
    }
}
