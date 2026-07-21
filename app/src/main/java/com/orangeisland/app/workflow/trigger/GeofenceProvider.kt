package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearWorkflow

/**
 * Flavor-specific geofence backend. The [GeofenceTriggerFamily] is flavor-agnostic: it computes
 * the diff between registered and requested geofences and hands the add/remove ops to whichever
 * provider the build includes.
 *
 *  - **play flavor** ([com.orangeisland.app.workflow.geofence.PlayGeofenceProvider]): backed by
 *    Google Play Services `GeofencingClient`. Registers via a PendingIntent routed through
 *    [GeofenceTriggerReceiver]; transitions are delivered to [GeofenceProvider.onTransition].
 *  - **fdroid flavor** ([com.orangeisland.app.workflow.geofence.FdroidGeofenceProvider]): a
 *    no-op. Geofence triggers are simply unsupported on fdroid (Play Services isn't available
 *    there); the family logs the skip rather than failing the whole registry.
 *
 * A provider is constructed once and held for the app lifetime by [GeofenceTriggerFamily]; it
 * owns its OS-level resources (client ref, PendingIntent).
 *
 * Independent implementation.
 */
interface GeofenceProvider {

    /** The provider's display name for logs ("play" / "fdroid-noop"). */
    val name: String

    /** True when this build can actually register geofences (Play Services present, permissions
     *  granted). When false, [register] is a no-op and the family logs the skip. */
    fun isAvailable(): Boolean

    /** Sync the active geofence set to [requests]. Each request is keyed by workflow id, so the
     *  provider can add new, remove gone, and update changed geofences. Returns the count of
     *  geofences that ended up registered (for logging). */
    fun sync(requests: List<Request>): Int

    /** Remove every geofence the provider currently holds. */
    fun clear()

    /** One geofence to register. The [id] is the workflow id (used as Play Services requestId). */
    data class Request(
        val id: String,
        val workflow: LinearWorkflow,
        val direction: Direction,
        val lat: Double,
        val lng: Double,
        val radiusM: Int
    )

    enum class Direction { ENTER, EXIT }

    /** Last-resort no-op used by [com.orangeisland.app.di.AppContainer] when reflection can't find
     *  either flavor provider (should never happen — exactly one flavor source set is compiled in,
     *  but the defensive fallback keeps the app from crashing at startup if it does). */
    object NoopFallback : GeofenceProvider {
        override val name: String = "noop-fallback"
        override fun isAvailable(): Boolean = false
        override fun sync(requests: List<Request>): Int = 0
        override fun clear() = Unit
    }
}
