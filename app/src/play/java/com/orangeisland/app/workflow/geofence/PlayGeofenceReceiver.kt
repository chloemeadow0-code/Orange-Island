package com.orangeisland.app.workflow.geofence

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.orangeisland.app.workflow.trigger.GeofenceFireWorker

/**
 * Manifest-declared receiver for Play Services geofence transitions (play flavor only).
 *
 * On fdroid this class doesn't exist (Play Services isn't available there), so the fdroid
 * manifest doesn't declare it and the no-op [FdroidGeofenceProvider] never registers a
 * PendingIntent.
 *
 * The receiver maps Play Services transitions to triggering geofence requestIds and hands each to
 * [GeofenceFireWorker.enqueue] (WorkManager is the documented way to do background work off a
 * broadcast — receivers are time-limited, and a geofence-triggered workflow can outlast that
 * window). The requestId encodes the workflow id + direction (see
 * [com.orangeisland.app.workflow.trigger.GeofenceSignalSource.encodeRequestId]), so the worker
 * routes the fire without any lookup table or app-wide singleton.
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
        // Only ENTER / EXIT transitions are registered (see PlayGeofenceProvider), so anything
        // else is noise.
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_EXIT -> Unit
            else -> {
                Log.d(TAG, "ignoring transition ${event.geofenceTransition}")
                return
            }
        }
        val ids = event.triggeringGeofences?.map { it.requestId }?.filter { it.isNotBlank() }.orEmpty()
        if (ids.isEmpty()) return
        // Enqueue first, then release the broadcast lease — WorkManager persists the request, so
        // the lease only needs to cover the enqueue (not the fire).
        val pendingResult = goAsync()
        try {
            ids.forEach { GeofenceFireWorker.enqueue(context, it) }
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
