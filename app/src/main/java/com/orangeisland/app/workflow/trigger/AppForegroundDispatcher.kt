package com.orangeisland.app.workflow.trigger

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Publishes the live foreground package name from the automation accessibility service to anyone
 * who wants it. The service calls [publish] on every TYPE_WINDOW_STATE_CHANGED transition; this
 * object dedupes by package and fans out to subscribers.
 *
 * Separation of concerns: the accessibility service owns event delivery and gesture dispatch for
 * automation; the App Lock service ([com.orangeisland.app.tool.device.AppLockAccessibilityService])
 * owns app-lock interception. Neither knows about workflows — the service just hands the
 * foreground package here, and this object fans it out. This keeps the trigger layer decoupled
 * from the accessibility plumbing.
 *
 * Independent implementation.
 */
object AppForegroundDispatcher {

    private val listeners = CopyOnWriteArrayList<(String?) -> Unit>()

    /** The last package published as foreground, or null if none yet. */
    @Volatile var lastKnown: String? = null
        private set

    /** Subscribe to foreground transitions. Returns a remover. */
    fun subscribe(listener: (String?) -> Unit): Runnable {
        listeners.add(listener)
        return Runnable { listeners.remove(listener) }
    }

    /** Called by the accessibility service on TYPE_WINDOW_STATE_CHANGED. Safe to invoke from any
     *  thread; listeners are responsible for their own thread safety. */
    fun publish(packageName: String?) {
        if (packageName == lastKnown) return
        lastKnown = packageName
        listeners.forEach { runCatching { it(packageName) } }
    }
}
