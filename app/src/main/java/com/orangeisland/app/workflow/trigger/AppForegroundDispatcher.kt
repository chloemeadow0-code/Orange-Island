package com.orangeisland.app.workflow.trigger

import android.content.Context
import com.orangeisland.app.util.NoisePackageFilter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Publishes the live foreground package name from the automation accessibility service to anyone
 * who wants it. The service calls [publish] on every TYPE_WINDOW_STATE_CHANGED transition; this
 * object filters out noise packages, dedupes by package, and fans out to subscribers.
 *
 * Separation of concerns: the accessibility service owns event delivery and gesture dispatch for
 * automation; the App Lock service ([com.orangeisland.app.tool.device.AppLockAccessibilityService])
 * owns app-lock interception. Neither knows about workflows — the service just hands the
 * foreground package here, and this object fans it out. This keeps the trigger layer decoupled
 * from the accessibility plumbing.
 *
 * Noise filtering: TYPE_WINDOW_STATE_CHANGED also fires for surfaces the user doesn't think of as
 * "apps" — the IME popping up while typing, the notification shade (SystemUI), the Launcher seen
 * between two real apps. Without filtering, those pollute both the AppLaunched / AppClosed
 * workflow triggers and (historically) the {app_context} snapshot. We drop them at the source so
 * every downstream subscriber benefits. Noise packages are NOT recorded as [lastKnown], which
 * keeps the dedupe correct: if the user is in App A, opens the keyboard (filtered), then returns
 * to App A, subscribers won't see a spurious A→A transition.
 *
 * Independent implementation.
 */
object AppForegroundDispatcher {

    private val listeners = CopyOnWriteArrayList<(String?) -> Unit>()

    /** The last package published as foreground, or null if none yet. Noise packages never set
     *  this — see the [publish] contract. */
    @Volatile var lastKnown: String? = null
        private set

    /** Subscribe to foreground transitions. Returns a remover. */
    fun subscribe(listener: (String?) -> Unit): Runnable {
        listeners.add(listener)
        return Runnable { listeners.remove(listener) }
    }

    /** Called by the accessibility service on TYPE_WINDOW_STATE_CHANGED. Safe to invoke from any
     *  thread; listeners are responsible for their own thread safety.
     *
     *  [context] is required to resolve the device's IME / Launcher package sets; the caller (the
     *  accessibility service) passes itself since a service is a Context. Noise packages (IME,
     *  SystemUI, Launcher) are dropped before dedupe / fan-out and never recorded in [lastKnown]. */
    fun publish(context: Context, packageName: String?) {
        if (packageName == lastKnown) return
        if (packageName != null && NoisePackageFilter.isNoise(context, packageName)) return
        lastKnown = packageName
        listeners.forEach { runCatching { it(packageName) } }
    }
}
