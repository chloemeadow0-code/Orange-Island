package com.orangeisland.app.workflow.trigger

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bridge from [com.orangeisland.app.tool.automation.AutomationAccessibilityService] (the one
 * service that already runs to expose UI-automation tools and therefore receives
 * TYPE_WINDOW_STATE_CHANGED events) to consumers that want the live foreground package:
 *
 *  - [AppForegroundTriggerFamily] subscribes for the `app_launched` / `app_closed` /
 *    `app_foreground_duration` linear triggers.
 *  - [com.orangeisland.app.workflow.linear.DeviceContextProvider] reads the cached last-known
 *    value for `foreground_app_*` conditions.
 *
 * **Separation of concerns**: the accessibility service owns event delivery and gesture dispatch
 * for automation; the App Lock service ([com.orangeisland.app.tool.device.AppLockAccessibilityService])
 * owns app-lock interception. Neither one knows about workflows — the service just hands the
 * foreground package to this dispatcher, and the dispatcher fans it out. This keeps the trigger
 * layer decoupled from the accessibility plumbing.
 *
 * Independent implementation.
 */
object AppForegroundDispatcher {

    /** Functional listeners invoked on every foreground transition. */
    private val listeners = CopyOnWriteArrayList<(String?) -> Unit>()

    /** Tracks how many workflow-side consumers currently need foreground events. The trigger
     *  registry bumps this on every resync; when it's zero AND there are no ad-hoc listeners,
     *  the accessibility service can short-circuit its hot path (every TYPE_WINDOW_STATE_CHANGED
     *  writes the cache — too costly to run 24/7 for a feature most users won't enable). */
    @Volatile private var consumerCount: Int = 0

    /** The last package reported as foreground, or null if we've never seen one. */
    @Volatile var lastKnown: String? = null
        private set

    /** Register a foreground-transition listener. Returns a remover. */
    fun addListener(listener: (String?) -> Unit): Runnable {
        listeners.add(listener)
        return Runnable { listeners.remove(listener) }
    }

    /** Called by the trigger registry on resync: [count] is the number of workflows with an
     *  app_* trigger, plus the number of conditions that read the foreground app. */
    fun setConsumerCount(count: Int) {
        consumerCount = count.coerceAtLeast(0)
    }

    /** True when at least one workflow trigger or condition is interested in foreground events.
     *  The accessibility service checks this to skip its cache-write hot path when nobody cares. */
    fun hasConsumers(): Boolean = consumerCount > 0 || listeners.isNotEmpty()

    /** Called by [AutomationAccessibilityService] on every TYPE_WINDOW_STATE_CHANGED. Safe to
     *  invoke from the main thread (listeners are responsible for their own thread safety). */
    fun onForegroundChange(packageName: String?) {
        if (!hasConsumers()) return
        val prev = lastKnown
        if (prev == packageName) return
        lastKnown = packageName
        listeners.forEach { runCatching { it(packageName) } }
    }
}
