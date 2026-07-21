package com.orangeisland.app.tool.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/**
 * AccessibilityService that exposes the device UI to the AI as a small set of automation tools:
 * tap / swipe / scroll / global action (back/home/recents) / inspect the on-screen window tree.
 *
 * Lifecycle: the system binds exactly one instance once the user enables the service under
 * Settings → Accessibility, and unbinds it when they disable it. We publish the live instance
 * into [instance] from [onServiceConnected] so [AutomationBridge] (and through it, the tool
 * providers) can reach it without holding a context.
 *
 * Scope and limits:
 *  - We intentionally do NOT call performGlobalAction(GLOBAL_ACTION_HOME) defensively before
 *    dispatching a gesture; doing so bounced the user off the activity they were in.
 *  - Gesture dispatch is async on the platform side. We bridge it to a coroutine-friendly
 *    CompletableDeferred via the per-gesture [GestureResultCallback].
 *  - AccessibilityNodeInfo objects are recycled aggressively because the framework allocates
 *    them from a small pool; leaking one eventually starves the whole service.
 *  - Window-tree inspection is read-only and capped (max children / max depth) so a hostile
 *    or huge UI can't make the LLM OOM the device.
 */
class AutomationAccessibilityService : AccessibilityService() {

    /** Single-thread executor for the screenshot callback. Owned by the service. */
    private val executor: java.util.concurrent.Executor =
        java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        INSTANCE.set(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        INSTANCE.compareAndSet(this, null)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Forward window-state transitions to the workflow trigger layer's foreground dispatcher.
        // The dispatcher dedupes by package; when no workflow subscribes to app_* triggers its
        // listener list is empty and the fan-out is a no-op, so the service's primary job
        // (gesture / window-tree tools) is unaffected.
        if (event != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                com.orangeisland.app.workflow.trigger.AppForegroundDispatcher.publish(pkg)
            }
        }
    }
    override fun onInterrupt() { /* no-op */ }

    /**
     * Dispatch a single-stroke gesture and suspend until the platform reports whether it was
     * actually performed. The OS may cancel a gesture (e.g. a system dialog popped up mid-swipe);
     * we surface that honestly as a failure rather than timing out.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun dispatchGesture(
        path: Path,
        startTimeMs: Long,
        durationMs: Long
    ): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, startTimeMs, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val result = CompletableDeferred<Boolean>()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { result.complete(true) }
            override fun onCancelled(g: GestureDescription?) { result.complete(false) }
        }, null)
        return result.await()
    }

    /**
     * Take a screenshot via the Android 11+ AccessibilityService.takeScreenshot API.
     * Returns the raw [Bitmap] (caller owns its lifecycle and must recycle it), or null on
     * failure (e.g. the screen was off, or the OS denied the capture).
     *
     * Exposed as a suspend fn so callers don't have to deal with the platform's
     * TakeScreenshotCallback. The success path copies the SDK-provided Bitmap because the
     * framework reclaims the original moments after the callback returns — without the copy,
     * downstream encode-to-PNG would crash with "Canvas: trying to use a recycled bitmap".
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotAsync(): Bitmap? {
        val result = CompletableDeferred<Bitmap?>()
        // Android 11+: signature is takeScreenshot(displayId, executor, callback).
        // displayId 0 == the default display (the only one a phone screen typically has).
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hardware = screenshot.hardwareBuffer ?: run { result.complete(null); return }
                try {
                    val src = Bitmap.wrapHardwareBuffer(hardware, screenshot.colorSpace)
                    val copy = src?.copy(Bitmap.Config.ARGB_8888, false)
                    result.complete(copy)
                } catch (t: Throwable) {
                    DebugLog.e("AutomationService", "screenshot wrap/copy failed", t)
                    result.complete(null)
                } finally {
                    runCatching { hardware.close() }
                }
            }

            override fun onFailure(errorCode: Int) {
                DebugLog.e("AutomationService", "screenshot failed: errorCode=$errorCode")
                result.complete(null)
            }
        })
        return result.await()
    }

    companion object {
        /** The live service instance, or null when the user has disabled it. */
        val INSTANCE: AtomicReference<AutomationAccessibilityService?> = AtomicReference(null)

        /** True iff the user has enabled this service under Settings → Accessibility. */
        fun isEnabled(context: android.content.Context): Boolean {
            val expected = android.content.ComponentName(context, AutomationAccessibilityService::class.java)
            val flat = runCatching {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull() ?: return false
            if (flat.isBlank()) return false
            val on = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (!on) return false
            return flat.split(":").any { android.content.ComponentName.unflattenFromString(it) == expected }
        }
    }
}

/**
 * Bridge that the tool providers use to talk to the live [AutomationAccessibilityService]. All
 * entry points return a structured JSON-ready [AutomationResult] instead of throwing; callers
 * convert it to a string. A null service (user hasn't enabled accessibility, or disabled it) is
 * reported as a typed [AutomationResult.ServiceUnavailable] so the model can ask the user to
 * grant permission rather than getting an opaque error.
 */
internal object AutomationBridge {

    /** The single point of failure for "the service isn't connected right now". */
    private const val SERVICE_DOWN_GUIDANCE =
        "The Orange Island automation service is not enabled. Ask the user to open " +
            "Settings → Accessibility → Orange Island UI Automation and turn it on."

    fun serviceActive(): Boolean = AutomationAccessibilityService.INSTANCE.get() != null

    suspend fun tap(x: Float, y: Float, durationMs: Long): AutomationResult {
        val svc = AutomationAccessibilityService.INSTANCE.get()
            ?: return AutomationResult.ServiceUnavailable(SERVICE_DOWN_GUIDANCE)
        val path = Path().apply { moveTo(x, y) }
        val ok = runCatching { svc.dispatchGesture(path, 0L, durationMs) }
            .getOrElse {
                DebugLog.e("AutomationBridge", "tap dispatch failed", it)
                return AutomationResult.Failure("dispatch_failed: ${it.message}")
            }
        return if (ok) AutomationResult.Success("tap", mapOf("x" to x, "y" to y))
        else AutomationResult.Failure("gesture_cancelled_or_timeout")
    }

    suspend fun swipe(
        startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long
    ): AutomationResult {
        val svc = AutomationAccessibilityService.INSTANCE.get()
            ?: return AutomationResult.ServiceUnavailable(SERVICE_DOWN_GUIDANCE)
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val ok = runCatching { svc.dispatchGesture(path, 0L, durationMs) }
            .getOrElse {
                DebugLog.e("AutomationBridge", "swipe dispatch failed", it)
                return AutomationResult.Failure("dispatch_failed: ${it.message}")
            }
        return if (ok) AutomationResult.Success("swipe", mapOf(
            "start_x" to startX, "start_y" to startY, "end_x" to endX, "end_y" to endY
        )) else AutomationResult.Failure("gesture_cancelled_or_timeout")
    }

    /** Scroll by walking the tree for a scrollable node and using its own scroll action,
     *  falling back to a swipe gesture when no scrollable node exists (covers plain ImageViews). */
    suspend fun scroll(direction: ScrollDirection, anchorX: Float?, anchorY: Float?): AutomationResult {
        val svc = AutomationAccessibilityService.INSTANCE.get()
            ?: return AutomationResult.ServiceUnavailable(SERVICE_DOWN_GUIDANCE)
        val root = currentRoot(svc) ?: return AutomationResult.Failure("no_active_window")
        try {
            val target = when {
                anchorX != null && anchorY != null ->
                    findNodeRecursive(root) { it.isScrollable && it.contains(anchorX, anchorY) }
                else -> findNodeRecursive(root) { it.isScrollable }
            }
            if (target != null) {
                try {
                    val action = if (direction == ScrollDirection.DOWN || direction == ScrollDirection.RIGHT)
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    val ok = runCatching { target.performAction(action) }.getOrDefault(false)
                    if (ok) return AutomationResult.Success("scroll", mapOf(
                        "mode" to "node", "direction" to direction.key
                    ))
                    return AutomationResult.Failure("node_scroll_action_rejected")
                } finally {
                    runCatching { target.recycle() }
                }
            }
        } finally {
            runCatching { root.recycle() }
        }

        // Gesture fallback: a third-screen swipe from the geometric center.
        val w = svc.resources.displayMetrics.widthPixels
        val h = svc.resources.displayMetrics.heightPixels
        val cx = w / 2f; val cy = h / 2f
        val spanX = (w / 3f).coerceAtLeast(100f)
        val spanY = (h / 3f).coerceAtLeast(100f)
        val (sx, sy, ex, ey) = when (direction) {
            ScrollDirection.UP -> FloatQuadruple(cx, cy + spanY / 2, cx, cy - spanY / 2)
            ScrollDirection.DOWN -> FloatQuadruple(cx, cy - spanY / 2, cx, cy + spanY / 2)
            ScrollDirection.LEFT -> FloatQuadruple(cx + spanX / 2, cy, cx - spanX / 2, cy)
            ScrollDirection.RIGHT -> FloatQuadruple(cx - spanX / 2, cy, cx + spanX / 2, cy)
        }
        val path = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
        val ok = runCatching { svc.dispatchGesture(path, 0L, 300L) }.getOrDefault(false)
        return if (ok) AutomationResult.Success("scroll", mapOf(
            "mode" to "swipe_fallback", "direction" to direction.key
        )) else AutomationResult.Failure("swipe_fallback_cancelled_or_timeout")
    }

    /** Run [AccessibilityService.GLOBAL_ACTION_*] constants. Maps a friendly name to the int. */
    fun globalAction(action: String): AutomationResult {
        val svc = AutomationAccessibilityService.INSTANCE.get()
            ?: return AutomationResult.ServiceUnavailable(SERVICE_DOWN_GUIDANCE)
        val code = GLOBAL_ACTIONS[action]
            ?: return AutomationResult.Failure("unknown_action: valid actions are ${GLOBAL_ACTIONS.keys}")
        val ok = runCatching { svc.performGlobalAction(code) }.getOrDefault(false)
        return if (ok) AutomationResult.Success("global_action", mapOf("action" to action))
        else AutomationResult.Failure("rejected_by_os: action '$action' not available on this device/state")
    }

    /** Snapshot the current window tree as a flat JSON-able node list (capped). Read-only. */
    fun snapshotWindowTree(maxNodes: Int): AutomationResult {
        val svc = AutomationAccessibilityService.INSTANCE.get()
            ?: return AutomationResult.ServiceUnavailable(SERVICE_DOWN_GUIDANCE)
        val root = currentRoot(svc) ?: return AutomationResult.Failure("no_active_window")
        val collected = ArrayList<NodeSnapshot>(maxNodes.coerceAtLeast(8))
        try {
            walkTree(root, maxNodes, collected)
        } finally {
            root.recycle()
        }
        return AutomationResult.Snapshot(collected)
    }

    /**
     * Capture the current screen into a fresh PNG in the app's cache dir. Returns the file, or
     * null on any failure (screen off, OS denied, IO error, pre-Android-11). The caller owns the
     * file and should delete it after use.
     *
     * Requires Android 11+ (API 30) for [AccessibilityService.takeScreenshot]. On older devices
     * this is a hard "not supported" — MediaProjection would be the alternative, but we don't
     * want to introduce a foreground-service-with-notification requirement for a single tool.
     */
    suspend fun captureScreenToFile(): java.io.File? {
        val svc = AutomationAccessibilityService.INSTANCE.get() ?: return null
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
        val bitmap = runCatching { svc.takeScreenshotAsync() }.getOrNull() ?: return null
        try {
            val outFile = java.io.File(svc.cacheDir, "ui_read_screen_${System.currentTimeMillis()}.png")
            val ok = runCatching {
                outFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
            }.getOrDefault(false)
            return if (ok) outFile else null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Write text into an editable node. Resolution order:
     *  1. Find the target node via [selector] (text / content_description / view_id / coords).
     *  2. Walk up the parent chain until [AccessibilityNodeInfo.isEditable] is true — many
     *     apps wrap their EditText inside a non-editable container.
     *  3. Try [AccessibilityNodeInfo.ACTION_SET_TEXT] first (works on most native fields,
     *     supports any Unicode — the IME never runs).
     *  4. If SET_TEXT is rejected, fall back to [AccessibilityNodeInfo.ACTION_FOCUS] +
     *     writing the text to the system clipboard + [AccessibilityNodeInfo.ACTION_PASTE].
     *     This covers WebView/Compose fields that ignore SET_TEXT but accept paste.
     *  5. Restore the original clipboard contents afterwards so we don't clobber the user's
     *     clipboard as a side effect of typing.
     */
    fun setText(selector: TextSelector, value: String, nth: Int): AutomationResult {
        val svc = AutomationAccessibilityService.INSTANCE.get()
            ?: return AutomationResult.ServiceUnavailable(SERVICE_DOWN_GUIDANCE)
        val root = currentRoot(svc) ?: return AutomationResult.Failure("no_active_window")
        try {
            val matches = collectMatches(root, selector, value)
            if (matches.isEmpty()) return AutomationResult.Failure(
                "no_match: no node matched $selector='$value'"
            )
            // nth is 0-indexed. Validate BEFORE taking, so an out-of-range call doesn't leak.
            if (nth !in matches.indices) {
                matches.forEach { runCatching { it.recycle() } }
                return AutomationResult.Failure(
                    "nth_out_of_range: matched ${matches.size} node(s); nth must be in 0..${matches.size - 1}"
                )
            }
            // Recycle all matches except the one we're keeping (the nth).
            val target = matches[nth]
            for (i in matches.indices) {
                if (i != nth) runCatching { matches[i].recycle() }
            }
            // Resolve + write within a nested try so the inner finally owns only the nodes
            // allocated by THIS block (target + visited + resolved). The outer finally still owns root.
            return resolveAndWrite(svc, target, selector, value)
        } finally {
            root.recycle()
        }
    }

    /** Find the editable ancestor of [target], then write [value] via SET_TEXT (or PASTE fallback).
     *  Owns the lifetime of [target] and every node visited walking up the parent chain. */
    private fun resolveAndWrite(
        svc: AccessibilityService,
        target: AccessibilityNodeInfo,
        selector: TextSelector,
        value: String
    ): AutomationResult {
        // Walk up the parent chain to find the editable ancestor (a common pattern: the matched
        // node is a label/preview, the real EditText is its parent or grandparent).
        val visited = ArrayList<AccessibilityNodeInfo>()
        var cursor: AccessibilityNodeInfo? = target
        while (cursor != null && !cursor.isEditable) {
            // Don't add `target` itself to visited — its lifetime is owned by this block.
            if (cursor !== target) visited.add(cursor)
            val parent = runCatching { cursor.parent }.getOrNull()
            if (parent == null) break
            cursor = parent
        }
        val resolved: AccessibilityNodeInfo? = cursor
        try {
            if (resolved == null || !resolved.isEditable) {
                return AutomationResult.Failure(
                    "node_not_editable: neither the matched node nor any of its ancestors is " +
                        "editable. Some surfaces (Terminals, games, drawn-from-canvas UI) don't " +
                        "expose editable nodes; the input can't be filled this way."
                )
            }
            // Step 3: ACTION_SET_TEXT (preferred — no IME, no clipboard, full Unicode).
            val setOk = runCatching {
                val args = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        value
                    )
                }
                resolved.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }.getOrDefault(false)
            if (setOk) return AutomationResult.Success("set_text", mapOf(
                "mode" to "set_text",
                "selector" to selector.key,
                "value" to value,
                "chars" to value.length
            ))

            // Step 4: paste fallback for fields that ignore SET_TEXT.
            val pasted = pasteViaClipboard(svc, resolved, value)
            return if (pasted) AutomationResult.Success("set_text", mapOf(
                "mode" to "paste_fallback",
                "selector" to selector.key,
                "value" to value,
                "chars" to value.length
            )) else AutomationResult.Failure("action_rejected: SET_TEXT and PASTE both failed")
        } finally {
            // Recycle every intermediate node we walked through. `target` may equal `resolved`
            // (matched node was itself editable) — in that case neither should be recycled here.
            visited.forEach { runCatching { it.recycle() } }
            if (resolved !== null && resolved !== target) runCatching { resolved.recycle() }
            // `target` is always ours now: recycle it whether or not it was the resolved editable.
            // (When resolved === target, the recycle call above was skipped, so this is the only
            // owner; when resolved !== target, target still needs recycling.)
            runCatching { target.recycle() }
        }
    }

    /**
     * Paste fallback: stash the user's current clipboard, focus the target, push [text] onto the
     * clipboard, fire ACTION_PASTE, then restore the original clipboard. Returns true iff paste
     * reported success.
     */
    private fun pasteViaClipboard(
        svc: AccessibilityService,
        target: AccessibilityNodeInfo,
        text: String
    ): Boolean {
        val cm = svc.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return false
        val previous = runCatching { cm.primaryClip }.getOrNull()
        // Focus first — some fields reject PASTE without focus.
        runCatching { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        runCatching {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("oi_automation", text))
        }
        val pasted = runCatching {
            target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)
        // Always restore the original clipboard, even on failure — typing must not trash it.
        if (previous != null) {
            runCatching { cm.setPrimaryClip(previous) }
        }
        return pasted
    }

    /**
     * Find all nodes matching [selector] under [root]. The caller owns every returned node and
     * must recycle each. Non-matching visited nodes are recycled internally.
     */
    private fun collectMatches(
        root: AccessibilityNodeInfo,
        selector: TextSelector,
        value: String
    ): ArrayList<AccessibilityNodeInfo> {
        // The SDK's built-in finders are efficient for the index/id axes; for content_description
        // and coords we walk the tree ourselves.
        val seed: List<AccessibilityNodeInfo> = when (selector) {
            TextSelector.VIEW_ID -> runCatching {
                root.findAccessibilityNodeInfosByViewId(value).orEmpty()
            }.getOrDefault(emptyList())
            TextSelector.TEXT -> runCatching {
                // SDK finder is substring-based; tighten to exact equality so "搜索" doesn't match
                // "搜索历史" — matching the model's natural-language intent better.
                root.findAccessibilityNodeInfosByText(value).orEmpty().filter {
                    it.text?.toString() == value
                }
            }.getOrDefault(emptyList())
            else -> emptyList() // content_description / coords handled by the walk below
        }
        if (selector == TextSelector.VIEW_ID || selector == TextSelector.TEXT) {
            return ArrayList(seed)
        }
        // Tree walk for content_description and coords. Recycles every non-matching visited node.
        val out = ArrayList<AccessibilityNodeInfo>()
        val pred: (AccessibilityNodeInfo) -> Boolean = when (selector) {
            TextSelector.CONTENT_DESCRIPTION -> { n ->
                n.contentDescription?.toString() == value
            }
            TextSelector.COORDS -> { n ->
                // value is "x,y" — parse lazily once and capture; fall back to no-match on
                // malformed input so a typo doesn't crash the tool.
                val parsed = parseCoords(value)
                if (parsed == null) false else n.contains(parsed.a, parsed.b)
            }
            else -> { _ -> false }
        }
        collectMatchingRecursive(root, pred, out)
        return out
    }

    /** DFS that collects every matching node. The caller owns the matches; siblings are recycled. */
    private fun collectMatchingRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
        out: ArrayList<AccessibilityNodeInfo>
    ) {
        if (predicate(node)) {
            out.add(node)
            // Don't recurse into a matched node — callers usually want the outermost match.
            return
        }
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            collectMatchingRecursive(child, predicate, out)
            // Every child was either added to `out` (owned by caller) or fully traversed and is
            // therefore safe to recycle now.
            if (child !in out) runCatching { child.recycle() }
        }
    }

    /** Parse "x,y" → (Float, Float). Returns null on malformed input. */
    private fun parseCoords(s: String): FloatQuadruple? {
        val parts = s.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (parts.size != 2) return null
        // Reuse FloatQuadruple as a 2-tuple: end_x/end_y stay 0 (unused by callers).
        return FloatQuadruple(parts[0], parts[1], 0f, 0f)
    }

    // ── internals ─────────────────────────────────────────────

    private fun currentRoot(svc: AccessibilityService): AccessibilityNodeInfo? =
        runCatching { svc.rootInActiveWindow }.getOrNull()

    /** Depth-first search that recycles every visited non-matching node. The matched node
     *  (the one returned to the caller) is NOT recycled — the caller owns it. */
    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val match = findNodeRecursive(child, predicate)
            if (match != null) {
                // Hand the match up the stack; recycle the child unless it IS the match.
                if (match !== child) runCatching { child.recycle() }
                return match
            }
            runCatching { child.recycle() }
        }
        return null
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        budget: Int,
        out: ArrayList<NodeSnapshot>
    ) {
        if (out.size >= budget) return
        out.add(NodeSnapshot.from(node))
        for (i in 0 until node.childCount) {
            if (out.size >= budget) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            try { walkTree(child, budget, out) } finally { child.recycle() }
        }
    }

    /** Friendly-name → GLOBAL_ACTION_* code table. */
    private val GLOBAL_ACTIONS: Map<String, Int> = mapOf(
        "back" to AccessibilityService.GLOBAL_ACTION_BACK,
        "home" to AccessibilityService.GLOBAL_ACTION_HOME,
        "recents" to AccessibilityService.GLOBAL_ACTION_RECENTS,
        "notifications" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
        "quick_settings" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        "power_dialog" to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
        "lock_screen" to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
        "split_screen" to AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN,
        "take_screenshot" to AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    )
}

/** Horizontal result type the tool providers turn into JSON. */
internal sealed class AutomationResult {
    data class Success(val kind: String, val details: Map<String, Any?>) : AutomationResult()
    data class Failure(val reason: String) : AutomationResult()
    data class ServiceUnavailable(val guidance: String) : AutomationResult()
    data class Snapshot(val nodes: List<NodeSnapshot>) : AutomationResult()
}

internal enum class ScrollDirection(val key: String) {
    UP("up"), DOWN("down"), LEFT("left"), RIGHT("right")
}

/** How [AutomationBridge.setText] locates the target node. Exposed to the LLM as the `by` arg. */
internal enum class TextSelector(val key: String) {
    TEXT("text"),                       // exact-match node.text
    CONTENT_DESCRIPTION("content_description"),  // exact-match node.contentDescription
    VIEW_ID("view_id"),                 // resource name (e.g. "com.foo:id/search_box")
    COORDS("coords")                    // value is "x,y" — match the node containing the point
}

/** Plain four-float holder (Kotlin has no built-in quadruple). */
internal data class FloatQuadruple(val a: Float, val b: Float, val c: Float, val d: Float)

/** True iff (x,y) in screen-space falls inside this node's bounds. */
private fun AccessibilityNodeInfo.contains(x: Float, y: Float): Boolean {
    val r = android.graphics.Rect()
    runCatching { getBoundsInScreen(r) }
    return r.left <= x && x <= r.right && r.top <= y && y <= r.bottom
}

/** JSON-able projection of an AccessibilityNodeInfo. Cheap fields only — no node reference kept. */
internal data class NodeSnapshot(
    val cls: String?,
    val text: String?,
    val desc: String?,
    val viewId: String?,
    val boundsInScreen: android.graphics.Rect?,
    val clickable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val childCount: Int
) {
    companion object {
        fun from(node: AccessibilityNodeInfo): NodeSnapshot {
            val bounds = android.graphics.Rect()
            runCatching { node.getBoundsInScreen(bounds) }
            return NodeSnapshot(
                cls = node.className?.toString(),
                text = node.text?.toString(),
                desc = node.contentDescription?.toString(),
                viewId = node.viewIdResourceName,
                boundsInScreen = bounds.takeIf { !it.isEmpty },
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                childCount = node.childCount
            )
        }
    }
}
