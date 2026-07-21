package com.orangeisland.app.workflow.geofence

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.orangeisland.app.workflow.trigger.GeofenceProvider
import com.orangeisland.app.workflow.trigger.GeofenceTriggerDispatcher

/**
 * Manifest-declared receiver for Play Services geofence transitions (play flavor only).
 *
 * On fdroid this class doesn't exist (Play Services isn't available there), so the fdroid
 * manifest doesn't declare it and [FdroidGeofenceProvider] never registers a PendingIntent.
 *
 * The receiver maps Play Services transitions to [GeofenceProvider.Direction] and forwards to
 * [GeofenceTriggerDispatcher.onTransition], which routes through the live family (warm process)
 * or the cold-start repository fallback.
 *
 * Independent implementation.
 */
class PlayGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = runCatching { GeofencingEvent.fromIntent(intent) }.getOrNull()
        if (event == null) {
            Log.w(TAG, "no geofencing event in intent")
            return
        }
        if (event.hasError()) {
            Log.w(TAG, "geofencing error: ${event.errorCode}")
            return
        }
        val direction = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceProvider.Direction.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceProvider.Direction.EXIT
            else -> {
                Log.d(TAG, "ignoring transition ${event.geofenceTransition}")
                return
            }
        }
        val ids = event.triggeringGeofences?.map { it.requestId }?.filter { it.isNotBlank() }.orEmpty()
        if (ids.isEmpty()) return
        // Hold the broadcast lease briefly so the dispatcher's coroutine has a chance to launch.
        // The family's fire launches onto a long-lived scope, so we release immediately — the
        // lease protects only the launch, not the full fire (a fully-durable cold path would use
        // WorkManager, out of scope for v2).
        val pendingResult = goAsync()
        try {
            GeofenceTriggerDispatcher.onTransition(ids, direction)
        } finally {
            runCatching { pendingResult.finish() }
        }
    }

    companion object {
        private const val TAG = "PlayGeofenceReceiver"

        /** Action the PendingIntent uses; the manifest receiver's intent-filter matches it. */
        const val ACTION = "com.orangeisland.app.GEOFENCE_TRANSITION"

        /** Build the PendingIntent the Play Services client uses to deliver transitions. */
        fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PlayGeofenceReceiver::class.java).apply { action = ACTION }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }
    }
}
