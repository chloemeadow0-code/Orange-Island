package com.newoether.agora.plugin

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.newoether.agora.data.InstalledPlugin
import com.newoether.agora.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JavaScript ↔ Kotlin bridge injected into a plugin's WebView as `window.agora`.
 *
 * Exposes one method to HTML/JS:
 * ```js
 * agora.call("tool_name", { city: "Beijing" }, function(resultJson) { ... });
 * ```
 * The callback is optional; results also arrive via `window.agora.onMessage = (data) => {}`
 * for server-initiated pushes (currently unused in v1 but reserved).
 *
 * On the Kotlin side each `call()` is enqueued onto a [Channel] and drained by a single
 * coroutine that invokes [PluginSandbox.callTool]. This is the safe way to cross from the
 * WebKit JS thread (where JavascriptInterface methods run) into the coroutine world —
 * [JavascriptInterface] methods must return quickly and never block.
 *
 * Result delivery uses [WebView.evaluateJavascript] on the main thread, calling a global
 * `agora.__deliver(id, resultJson)` shim that dispatches to the original callback (if a
 * callback id was supplied) or to `window.agora.onMessage` (otherwise).
 *
 * Tool invocation is restricted to the plugin's own manifest.tools — one plugin's UI can
 * never invoke another plugin's tools.
 *
 * Lifecycle: the bridge owns a child [SupervisorJob] scope; [close] cancels it and nulls the
 * WebView reference so the page tearing down doesn't leak.
 */
class AgoraWebViewBridge(
    private val plugin: InstalledPlugin,
    private val sandbox: PluginSandbox,
    /** App-lifetime scope; the bridge gets a child supervisor under it. */
    parentScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "AgoraWebViewBridge"
        /** Name under which this object is registered with `WebView.addJavascriptInterface`.
         *  The bootstrap script reads `window.<this>.bridgeCall` and merges it into the
         *  page-facing `window.agora`. */
        const val bridgeObjectName: String = "__agoraNative"
    }

    /** Set by the Compose host once the WebView is created. Calls before this are rejected. */
    @Volatile var webView: WebView? = null

    private val supervisor = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(supervisor + Dispatchers.Main)
    private val queue = Channel<Call>(capacity = Channel.UNLIMITED)
    private val pumpJob: Job

    /** Tool names this plugin's UI is allowed to invoke (its own manifest.tools). */
    private val allowedTools: Set<String> = plugin.manifest.tools.map { it.name }.toSet()

    init {
        // Drain the queue on Dispatchers.Main so evaluateJavascript calls land on the right thread.
        pumpJob = scope.launch {
            for (call in queue) {
                runCatching { handle(call) }
                    .onFailure { DebugLog.w(TAG, "call ${call.tool} failed: ${it.message}") }
            }
        }
    }

    /** Stop draining and release the WebView reference. Idempotent. */
    fun close() {
        queue.close()
        pumpJob.cancel()
        supervisor.cancel()
        webView = null
    }

    /**
     * Invoked from JS by the bootstrap script. The bootstrap exposes the user-facing
     * `agora.call(tool, args, cb)` that generates a callback id, then delegates here.
     *
     * Registered under name `__agoraNative` (see [bridgeObjectName]) so the bootstrap can
     * merge it into the friendlier `window.agora` without name collisions.
     */
    @JavascriptInterface
    fun bridgeCall(tool: String, argsJson: String, callbackId: String) {
        if (tool !in allowedTools) {
            DebugLog.w(TAG, "plugin/${plugin.id} UI rejected call to disallowed tool: $tool")
            deliver(callbackId, errorJson("disallowed_tool", "Tool '$tool' not in this plugin's manifest"))
            return
        }
        queue.trySend(Call(callbackId, tool, argsJson))
    }

    /** Reserved for future host→page pushes (e.g. tool-call notifications). No-op in v1. */
    fun pushToPage(payload: String) {
        evaluate("if (typeof window.agora.onMessage === 'function') { window.agora.onMessage(${jsonEncodeJsString(payload)}); }")
    }

    private suspend fun handle(call: Call) {
        val result = sandbox.callTool(plugin, call.tool, call.args)
        deliver(call.callbackId, result)
    }

    private fun deliver(callbackId: String?, resultJson: String) {
        // The bootstrap we inject defines window.agora.__deliver(id, json) to dispatch to the
        // page-registered callback (or just log if none). We pass resultJson as a JS string
        // literal to avoid any quoting pitfalls.
        evaluate("window.agora.__deliver && window.agora.__deliver(${jsonEncodeJsString(callbackId ?: "null")}, ${jsonEncodeJsString(resultJson)});")
    }

    private fun evaluate(js: String) {
        val wv = webView ?: return
        try {
            wv.evaluateJavascript(js, null)
        } catch (e: Exception) {
            DebugLog.w(TAG, "evaluateJavascript failed: ${e.message}")
        }
    }

    /**
     * The bootstrap script prepended to the plugin's HTML. Defines the page-facing `agora`
     * global on top of the native `__agoraNative` JavascriptInterface.
     *
     * The native object only has `bridgeCall(tool, argsJson, callbackId)`; this bootstrap
     * wraps it in a friendlier `agora.call(tool, args, cb)` that auto-generates callback ids
     * and registers the callback for later dispatch via `agora.__deliver`.
     */
    val bootstrapScript: String = """
        (function() {
            if (window.agora) return;
            var native = window.${bridgeObjectName};
            if (!native) { console.error('Agora bridge missing'); return; }
            var nextId = 0;
            var callbacks = {};
            window.agora = {
                onMessage: null,
                call: function(tool, args, cb) {
                    var id = '__cb_' + (nextId++);
                    if (typeof cb === 'function') callbacks[id] = cb;
                    native.bridgeCall(tool, typeof args === 'string' ? args : JSON.stringify(args || {}), id);
                    return id;
                },
                __deliver: function(id, json) {
                    var cb = callbacks[id];
                    if (cb) {
                        delete callbacks[id];
                        try { cb(json); } catch (e) { console.error('callback error: ' + e); }
                    }
                }
            };
        })();
    """.trimIndent()

    private fun jsonEncodeJsString(s: String): String = JsonPrimitive(s).toString()

    private fun errorJson(type: String, message: String): String = buildJsonObject {
        put("error", type)
        put("message", message)
    }.toString()

    private data class Call(val callbackId: String, val tool: String, val args: String)
}
