package com.orangeisland.app.workflow.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.workflow.trigger.GeofenceProvider

/**
 * Play-flavor [GeofenceProvider] backed by Google Play Services
 * [GeofencingClient]. Each geofence is keyed by workflow id (used as the Play Services
 * requestId), so add/remove are workflow-scoped and the receiver can map an incoming transition
 * back to the workflow via [com.orangeisland.app.data.repository.WorkflowRepository.getLinear].
 *
 * Required permissions at register-time:
 *  - `ACCESS_FINE_LOCATION` (Android 6+)
 *  - `ACCESS_BACKGROUND_LOCATION` (Android 10+ — needed for triggers when the app isn't open).
 *
 * When either permission is missing or Play Services isn't on the device, [isAvailable] returns
 * false and [sync] / [clear] are silent no-ops; the [GeofenceTriggerFamily] logs the skip.
 *
 * Independent implementation.
 */
class PlayGeofenceProvider(
    private val context: Context
) : GeofenceProvider {

    override val name: String = "play"

    private val client: GeofencingClient? by lazy {
        runCatching { LocationServices.getGeofencingClient(context) }.getOrNull()
    }

    @Volatile private var registeredIds: Set<String> = emptySet()

    override fun isAvailable(): Boolean {
        if (client == null) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    override fun sync(requests: List<GeofenceProvider.Request>): Int {
        val c = client ?: return 0
        val targetIds = requests.map { it.id }.toSet()
        val toRemove = registeredIds - targetIds

        // Remove geofences that fell out of the matching set.
        if (toRemove.isNotEmpty()) {
            runCatching { Tasks.await(c.removeGeofences(toRemove.toList())) }
                .onFailure { DebugLog.w(TAG, "removeGeofences failed", it) }
        }

        // Add all target geofences. We re-add the whole set (Play Services dedupes by requestId),
        // which keeps the diff simple and handles updates transparently.
        if (requests.isNotEmpty()) {
            val geofences = requests.map { r ->
                val transition = when (r.direction) {
                    GeofenceProvider.Direction.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
                    GeofenceProvider.Direction.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
                }
                Geofence.Builder()
                    .setRequestId(r.id)
                    .setCircularRegion(r.lat, r.lng, r.radiusM.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(transition)
                    .build()
            }
            val req = GeofencingRequest.Builder()
                .setInitialTrigger(0)   // don't fire immediately on register
                .addGeofences(geofences)
                .build()
            @Suppress("MissingPermission")   // checked in isAvailable()
            runCatching { Tasks.await(c.addGeofences(req, PlayGeofenceReceiver.buildPendingIntent(context))) }
                .onFailure { DebugLog.w(TAG, "addGeofences failed", it) }
        }
        registeredIds = targetIds
        return targetIds.size
    }

    override fun clear() {
        val c = client ?: return
        if (registeredIds.isNotEmpty()) {
            runCatching { Tasks.await(c.removeGeofences(registeredIds.toList())) }
                .onFailure { DebugLog.w(TAG, "clear failed", it) }
        }
        registeredIds = emptySet()
    }

    companion object {
        private const val TAG = "PlayGeofenceProvider"
    }
}
