package com.orangeisland.app.workflow.trigger

import android.content.Context
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Signal source for [LinearTrigger.GeofenceEnter] / [LinearTrigger.GeofenceExit]. Reconciles the
 * flavor-specific [GeofenceProvider] against the current matching set: each geofence's requestId
 * encodes both the workflow id and the direction so the receiver (play flavor) can re-derive
 * which workflow to fire from the requestId alone.
 *
 * The provider owns its OS-level client and PendingIntent; this source only translates between
 * the workflow model and the provider's [GeofenceProvider.Request] shape. No app-wide singleton
 * dispatcher — the provider constructs its PendingIntent against the play-flavor receiver
 * directly, and the receiver routes transitions through a [GeofenceFireWorker] (WorkManager) for
 * durability, mirroring the boot path.
 *
 * Independent implementation.
 */
object GeofenceSignalSource {

    fun start(
        context: Context,
        scope: CoroutineScope,
        repository: WorkflowRepository,
        starter: WorkflowStarter
    ): Job = scope.launch(Dispatchers.IO) {
        repository.observeEnabledLinear().collectLatest { all ->
            val provider = resolveProvider(context)
            if (!provider.isAvailable()) {
                provider.clear()
                return@collectLatest
            }
            val requests = all.mapNotNull { wf -> toRequest(wf) }
            // The provider reconciles add/remove/update by requestId. Each request's id is the
            // requestId Play Services echoes back in a transition.
            runCatching { provider.sync(requests) }
                .onFailure { DebugLog.w("GeofenceSignalSource", "provider sync failed", it) }
        }
    }

    private fun toRequest(wf: LinearWorkflow): GeofenceProvider.Request? = when (val t = wf.trigger) {
        is LinearTrigger.GeofenceEnter -> GeofenceProvider.Request(
            id = encodeRequestId(wf.id, GeofenceProvider.Direction.ENTER),
            workflow = wf, direction = GeofenceProvider.Direction.ENTER,
            lat = t.lat, lng = t.lng, radiusM = t.radiusM
        )
        is LinearTrigger.GeofenceExit -> GeofenceProvider.Request(
            id = encodeRequestId(wf.id, GeofenceProvider.Direction.EXIT),
            workflow = wf, direction = GeofenceProvider.Direction.EXIT,
            lat = t.lat, lng = t.lng, radiusM = t.radiusM
        )
        else -> null
    }

    /** Encode the workflow id + direction into the geofence requestId so the receiver can route a
     *  transition without a lookup table. Format: "<workflowId>#<enter|exit>". */
    fun encodeRequestId(workflowId: String, direction: GeofenceProvider.Direction): String =
        "$workflowId#${direction.name.lowercase()}"

    /** Inverse of [encodeRequestId]. Returns null on malformed input. */
    fun decodeRequestId(requestId: String): Pair<String, GeofenceProvider.Direction>? {
        val idx = requestId.lastIndexOf('#')
        if (idx <= 0 || idx == requestId.length - 1) return null
        val wid = requestId.substring(0, idx)
        val dir = when (requestId.substring(idx + 1)) {
            "enter" -> GeofenceProvider.Direction.ENTER
            "exit" -> GeofenceProvider.Direction.EXIT
            else -> return null
        }
        return wid to dir
    }

    /** Resolve the flavor-specific provider by reflection (mirrors sandboxManagerFactory). */
    private fun resolveProvider(context: Context): GeofenceProvider = try {
        Class.forName("com.orangeisland.app.workflow.geofence.PlayGeofenceProvider")
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as GeofenceProvider
    } catch (_: ClassNotFoundException) {
        try {
            Class.forName("com.orangeisland.app.workflow.geofence.FdroidGeofenceProvider")
                .getDeclaredConstructor()
                .newInstance() as GeofenceProvider
        } catch (_: ClassNotFoundException) {
            GeofenceProvider.NoopFallback
        }
    }
}
