package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.tool.device.CapturedNotification
import com.orangeisland.app.tool.device.DeviceNotificationListenerService
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Trigger family for [LinearTrigger.NotificationReceived], fed by
 * [DeviceNotificationListenerService]'s live-posted observer. The family subscribes only when at
 * least one matching workflow exists, so a workflow that nobody uses costs zero (the listener
 * service keeps its ring buffer regardless, but our per-notification fan-out is gated).
 *
 * Each of the trigger's filters is AND-combined; `null` filters match anything:
 *  - [LinearTrigger.NotificationReceived.packageName]   — exact-equality on the source package.
 *  - [LinearTrigger.NotificationReceived.titleContains] — substring match (case-sensitive).
 *  - [LinearTrigger.NotificationReceived.textContains]  — substring match (case-sensitive).
 *  - [LinearTrigger.NotificationReceived.titleMatches]  — Java regex (full-match not required;
 *    `find()` is used so `/foo|bar/` works as "contains foo or bar").
 *  - [LinearTrigger.NotificationReceived.textMatches]   — Java regex.
 *
 * Independent implementation.
 */
class NotificationTriggerFamily(
    private val scope: CoroutineScope
) : TriggerFamily {

    override val name: String = "notification"

    @Volatile private var matching: List<LinearWorkflow> = emptyList()
    @Volatile private var fireCallback: TriggerFireCallback? = null
    private val observerRemover = AtomicReference<Runnable?>(null)

    override fun handles(trigger: LinearTrigger): Boolean =
        trigger is LinearTrigger.NotificationReceived

    override suspend fun sync(matching: List<LinearWorkflow>, callback: TriggerFireCallback) {
        this.matching = matching
        this.fireCallback = callback
        // Subscribe when we have work; unsubscribe when the matching set empties so the listener
        // service's per-notification fan-out drops us.
        if (matching.isNotEmpty() && observerRemover.get() == null) {
            val remover = DeviceNotificationListenerService.observePosted(::onNotificationPosted)
            observerRemover.set(remover)
        } else if (matching.isEmpty()) {
            observerRemover.getAndSet(null)?.run()
        }
    }

    override suspend fun shutdown() {
        matching = emptyList()
        fireCallback = null
        observerRemover.getAndSet(null)?.run()
    }

    /** Called by the listener service on every posted notification (binder thread). */
    private fun onNotificationPosted(n: CapturedNotification) {
        val cb = fireCallback ?: return
        val snap = matching
        if (snap.isEmpty()) return
        val fires = mutableListOf<Pair<String, LinearTrigger.NotificationReceived>>()
        for (wf in snap) {
            val t = wf.trigger as? LinearTrigger.NotificationReceived ?: continue
            if (matches(t, n)) fires += wf.id to t
        }
        if (fires.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            for ((wfId, t) in fires) {
                runCatching { cb.onFire(wfId, t) }
                    .onFailure { DebugLog.w(TAG, "notification fire failed for $wfId", it) }
            }
        }
    }

    /** AND-combine every non-null filter against the captured notification. */
    private fun matches(t: LinearTrigger.NotificationReceived, n: CapturedNotification): Boolean {
        if (t.packageName != null && t.packageName != n.packageName) return false
        if (t.titleContains != null && !n.title.contains(t.titleContains)) return false
        if (t.textContains != null && !n.text.contains(t.textContains)) return false
        if (t.titleMatches != null && !Regex(t.titleMatches).containsMatchIn(n.title)) return false
        if (t.textMatches != null && !Regex(t.textMatches).containsMatchIn(n.text)) return false
        return true
    }

    companion object {
        private const val TAG = "NotificationFamily"
    }
}
