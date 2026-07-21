package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow

/**
 * One trigger family owns a category of OS signals (WiFi broadcasts, geofence transitions, the
 * notification listener, …) and reconciles its registrations against the set of enabled workflows
 * that use one of its triggers.
 *
 * Lifecycle (driven by [TriggerRegistry]):
 *   1. [sync] is called whenever the enabled-workflow set changes. The family diffs against its
 *      own last-known set and registers/unregisters its OS-level hooks accordingly. Passing an
 *      empty list fully tears the family down (battery-friendly: zero listeners when nobody needs
 *      the signal).
 *   2. On each signal arrival, the family iterates the workflows it currently knows about,
 *      decides which ones match (e.g. SSID equality, package-name equality), and fires the
 *      callback for each.
 *
 * Families are stateful — they hold BroadcastReceiver registrations, geofence client refs, etc.
 * Construction happens once at app start; [sync] is safe to call repeatedly (it diffs).
 *
 * Independent implementation.
 */
interface TriggerFamily {
    /** Stable identifier for logs. */
    val name: String

    /** True if this family handles [trigger] (e.g. the WiFi family handles WifiConnected/Disconnected). */
    fun handles(trigger: LinearTrigger): Boolean

    /**
     * Reconcile the family's OS-level registration state with [matching] — the subset of currently
     * enabled workflows whose trigger this family handles. Pass an empty list to fully unregister.
     */
    suspend fun sync(matching: List<LinearWorkflow>, callback: TriggerFireCallback)

    /** Tear down everything (app shutdown — best effort). */
    suspend fun shutdown()
}

/** Callback a family invokes when one of its signals matches a workflow. The registry routes
 *  this to the linear engine's fire path. */
fun interface TriggerFireCallback {
    suspend fun onFire(workflowId: String, trigger: LinearTrigger)
}
