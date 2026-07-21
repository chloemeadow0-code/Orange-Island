package com.orangeisland.app.workflow.geofence

import com.orangeisland.app.workflow.trigger.GeofenceProvider

/**
 * fdroid-flavor [GeofenceProvider]: a no-op. Google Play Services isn't available on fdroid
 * builds, so geofence triggers can't be honored there. The [com.orangeisland.app.workflow.trigger.GeofenceTriggerFamily]
 * checks [isAvailable] before registering and logs the skip; the workflow still runs from its
 * other entry points (manual run, etc.) — only the geofence trigger is dead.
 *
 * Independent implementation.
 */
class FdroidGeofenceProvider : GeofenceProvider {
    override val name: String = "fdroid-noop"
    override fun isAvailable(): Boolean = false
    override fun sync(requests: List<GeofenceProvider.Request>): Int = 0
    override fun clear() = Unit
}
