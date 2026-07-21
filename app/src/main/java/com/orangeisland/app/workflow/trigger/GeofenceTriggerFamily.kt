package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Trigger family for [LinearTrigger.GeofenceEnter] / [LinearTrigger.GeofenceExit]. Delegates the
 * actual OS-level geofencing to a flavor-specific [GeofenceProvider] (Play Services on play,
 * no-op on fdroid). The family owns:
 *
 *  - the diff between the last-synced set and the current matching set (add / remove / update
 *    each geofence keyed by workflow id),
 *  - the in-memory fire callback that Play Services transitions route through, plus a
 *    cold-process fallback that reads the workflow straight from the repository (the geofence
 *    requestId IS the workflow id) when the process was just woken.
 *
 * Independent implementation.
 *
 * @param provider the flavor-specific backend. Constructed once and held for the app lifetime.
 */
class GeofenceTriggerFamily(
    private val provider: GeofenceProvider,
    private val scope: CoroutineScope,
    private val repository: com.orangeisland.app.data.repository.WorkflowRepository,
    private val runnerFire: suspend (workflowId: String, trigger: LinearTrigger) -> Unit
) : TriggerFamily {

    override val name: String = "geofence"

    @Volatile private var fireCallback: TriggerFireCallback? = null
    @Volatile private var registered: Map<String, GeofenceProvider.Request> = emptyMap()

    init {
        // Publish this family so the play-flavor receiver
        // (com.orangeisland.app.workflow.geofence.PlayGeofenceReceiver) can reach it when Play
        // Services delivers a transition while the process is up.
        GeofenceTriggerDispatcher.bind(this)
    }

    override fun handles(trigger: LinearTrigger): Boolean =
        trigger is LinearTrigger.GeofenceEnter || trigger is LinearTrigger.GeofenceExit

    override suspend fun sync(matching: List<LinearWorkflow>, callback: TriggerFireCallback) {
        fireCallback = callback
        if (!provider.isAvailable()) {
            DebugLog.w(TAG, "geofence provider '${provider.name}' unavailable; skipping registration")
            if (registered.isNotEmpty()) { provider.clear(); registered = emptyMap() }
            return
        }
        // Build the request set from the matching workflows.
        val target = matching.mapNotNull { wf -> toRequest(wf) }.associateBy { it.id }
        val toRemove = registered.keys - target.keys
        val toAdd = target.filter { (id, req) -> registered[id] != req }

        if (toRemove.isNotEmpty() || toAdd.isNotEmpty()) {
            // The provider's sync() reconciles in one call (Play Services remove/add are idempotent).
            val kept = provider.sync(target.values.toList())
            DebugLog.d(TAG, "geofence synced: ${target.size} target, ${toAdd.size} added, ${toRemove.size} removed ($kept live)")
        }
        registered = target
    }

    override suspend fun shutdown() {
        provider.clear()
        registered = emptyMap()
        fireCallback = null
    }

    /** Called by [GeofenceTriggerDispatcher] when Play Services delivers a transition. */
    fun onTransition(workflowIds: List<String>, direction: GeofenceProvider.Direction) {
        val snap = registered
        scope.launch(Dispatchers.IO) {
            for (id in workflowIds) {
                val req = snap[id]
                val inMemoryCb = fireCallback
                if (req != null && inMemoryCb != null && req.direction == direction) {
                    runCatching { inMemoryCb.onFire(id, req.workflow.trigger) }
                        .onFailure { DebugLog.w(TAG, "geofence fire failed for $id", it) }
                    continue
                }
                // Cold-process fallback: the family isn't synced yet (process just woken) —
                // read the workflow by id (== requestId) and fire through the runner path.
                runCatching {
                    val wf = repository.getLinear(id) ?: return@runCatching
                    if (!wf.enabled) return@runCatching
                    val matches = when (direction) {
                        GeofenceProvider.Direction.ENTER -> wf.trigger is LinearTrigger.GeofenceEnter
                        GeofenceProvider.Direction.EXIT -> wf.trigger is LinearTrigger.GeofenceExit
                    }
                    if (matches) runnerFire(id, wf.trigger)
                }.onFailure { DebugLog.w(TAG, "geofence cold-start fire failed for $id", it) }
            }
        }
    }

    private fun toRequest(wf: LinearWorkflow): GeofenceProvider.Request? = when (val t = wf.trigger) {
        is LinearTrigger.GeofenceEnter ->
            GeofenceProvider.Request(wf.id, wf, GeofenceProvider.Direction.ENTER, t.lat, t.lng, t.radiusM)
        is LinearTrigger.GeofenceExit ->
            GeofenceProvider.Request(wf.id, wf, GeofenceProvider.Direction.EXIT, t.lat, t.lng, t.radiusM)
        else -> null
    }

    companion object {
        private const val TAG = "GeofenceFamily"
    }
}

/**
 * App-wide bridge so the play-flavor's manifest-declared receiver
 * (com.orangeisland.app.workflow.geofence.PlayGeofenceReceiver) can find the live family at
 * fire time (the receiver may run in a fresh process before the registry is up). Bound by
 * [com.orangeisland.app.di.AppContainer] when the family is constructed.
 */
object GeofenceTriggerDispatcher {
    @Volatile private var familyRef: GeofenceTriggerFamily? = null

    fun bind(family: GeofenceTriggerFamily) { familyRef = family }
    fun get(): GeofenceTriggerFamily? = familyRef

    /** Called by the play-flavor receiver (PlayGeofenceReceiver) when Play Services delivers a
     *  transition. */
    fun onTransition(workflowIds: List<String>, direction: GeofenceProvider.Direction) {
        runCatching { familyRef?.onTransition(workflowIds, direction) }.getOrNull()
    }
}
