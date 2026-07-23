package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow

/**
 * Decides whether the app needs a persistent process for dynamically-registered
 * trigger listeners to stay alive.
 *
 * Triggers that rely on Manifest receivers or external scheduling (BootCompleted,
 * TimeCron, Geofence) do **not** need keep-alive because the OS or Play Services
 * will restart the app/worker when the event fires.
 *
 * Triggers that rely on dynamically-registered receivers or internal listeners
 * (WiFi, power, headphones, screen, battery, Bluetooth, app-foreground,
 * notification) **do** need keep-alive because they die with the process.
 */
object KeepAliveNeedEvaluator {

    fun needsKeepAlive(workflows: List<LinearWorkflow>): Boolean {
        return workflows.any { it.enabled && it.trigger.needsKeepAlive() }
    }

    private fun LinearTrigger.needsKeepAlive(): Boolean = when (this) {
        is LinearTrigger.WifiConnected,
        is LinearTrigger.WifiDisconnected,
        is LinearTrigger.PowerConnected,
        is LinearTrigger.PowerDisconnected,
        is LinearTrigger.HeadphonesPlugged,
        is LinearTrigger.HeadphonesUnplugged,
        is LinearTrigger.ScreenOn,
        is LinearTrigger.ScreenOff,
        is LinearTrigger.BatteryBelow,
        is LinearTrigger.BatteryAbove,
        is LinearTrigger.BluetoothConnected,
        is LinearTrigger.BluetoothDisconnected,
        is LinearTrigger.AppLaunched,
        is LinearTrigger.AppClosed,
        is LinearTrigger.AppForegroundDuration,
        is LinearTrigger.NotificationReceived -> true

        is LinearTrigger.Manual,
        is LinearTrigger.TimeCron,
        is LinearTrigger.GeofenceEnter,
        is LinearTrigger.GeofenceExit,
        is LinearTrigger.BootCompleted -> false
    }
}
