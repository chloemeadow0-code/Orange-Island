package com.orangeisland.app.plugin

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.orangeisland.app.data.InstalledPlugin
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * JavaScript ↔ Kotlin bridge injected into a plugin's WebView as `window.orangeisland`.
 *
 * Exposes one method to HTML/JS:
 * ```js
 * orangeisland.call("tool_name", { city: "Beijing" }, function(resultJson) { ... });
 * ```
 * The callback is optional; results also arrive via `window.orangeisland.onMessage = (data) => {}`
 * for server-initiated pushes (currently unused in v1 but reserved).
 *
 * On the Kotlin side each `call()` is enqueued onto a [Channel] and drained by a single
 * coroutine that invokes [PluginSandbox.callTool]. This is the safe way to cross from the
 * WebKit JS thread (where JavascriptInterface methods run) into the coroutine world —
 * [JavascriptInterface] methods must return quickly and never block.
 *
 * Result delivery uses [WebView.evaluateJavascript] on the main thread, calling a global
 * `orangeisland.__deliver(id, resultJson)` shim that dispatches to the original callback (if a
 * callback id was supplied) or to `window.orangeisland.onMessage` (otherwise).
 *
 * Tool invocation is restricted to the plugin's own manifest.tools — one plugin's UI can
 * never invoke another plugin's tools.
 *
 * Lifecycle: the bridge owns a child [SupervisorJob] scope; [close] cancels it and nulls the
 * WebView reference so the page tearing down doesn't leak.
 */
class OrangeIslandWebViewBridge(
    private val plugin: InstalledPlugin,
    private val sandbox: PluginSandbox,
    /** App-lifetime scope; the bridge gets a child supervisor under it. */
    parentScope: CoroutineScope,
    /** Snapshot of host values exposed read-only to the page via [getConfig] / [getDeviceId].
     *  Updated by the Compose host (PluginWebViewPage) as DataStore resolves them. */
    @Volatile var deviceUserId: String = "",
    @Volatile var pluginConfigJson: String = "{}",
    /** Provides read access to the host app's chat memories for this plugin's UI page. */
    private val memoryProvider: PluginMemoryProvider? = null,
    /**
     * Optional read-only media-info provider: returns the current now-playing track as a JSON
     * string (same shape as the `get_now_playing` tool). When null, `orangeisland.getMediaInfo()`
     * returns an error JSON instead of throwing. Wired by [PluginWebViewPage] from the host's
     * MediaSessionManager; the bridge itself stays decoupled from Android media APIs.
     */
    private val mediaInfoProvider: ((packageFilter: String?) -> String)? = null,
) {
    companion object {
        private const val TAG = "OrangeIslandWebViewBridge"
        /** Name under which this object is registered with `WebView.addJavascriptInterface`.
         *  The bootstrap script reads `window.<this>.bridgeCall` and merges it into the
         *  page-facing `window.orangeisland`. */
        const val bridgeObjectName: String = "__oiNative"
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
     * `orangeisland.call(tool, args, cb)` that generates a callback id, then delegates here.
     *
     * Registered under name `__oiNative` (see [bridgeObjectName]) so the bootstrap can
     * merge it into the friendlier `window.orangeisland` without name collisions.
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

    /**
     * Synchronous read of the plugin's user-filled config as a JSON string (e.g.
     * `{"user_nickname":"Alice"}`). Called from the page via `orangeisland.getConfig()`.
     *
     * This is the reliable, race-free path for the page to read host values: @JavascriptInterface
     * methods are invoked synchronously on the WebKit thread, so there's no timing dependency on
     * when (or whether) host globals get injected via evaluateJavascript.
     */
    @JavascriptInterface
    fun getConfig(): String = pluginConfigJson

    /** Synchronous read of the per-install device id. Called via `orangeisland.getDeviceId()`. */
    @JavascriptInterface
    fun getDeviceId(): String = deviceUserId

    /** Read recent messages for [conversationId] (JSON array). Limit defaults to 50. */
    @JavascriptInterface
    fun getChatHistory(conversationId: String, limit: Int): String {
        val provider = memoryProvider ?: return "[]"
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.getChatHistory(conversationId, limit.coerceIn(1, 200))
            }
        }.getOrElse { errorJson("memory_error", it.message ?: "read failed") }
    }

    /** Read long-term memories scoped to [conversationId]'s project (global+project merged). */
    @JavascriptInterface
    fun getLongTermMemories(conversationId: String): String {
        val provider = memoryProvider ?: return "[]"
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.getLongTermMemories(conversationId)
            }
        }.getOrElse { errorJson("memory_error", it.message ?: "read failed") }
    }

    /** Read the active / working memory text. */
    @JavascriptInterface
    fun getActiveMemory(conversationId: String): String {
        val provider = memoryProvider ?: return ""
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.getActiveMemory(conversationId)
            }
        }.getOrElse { errorJson("memory_error", it.message ?: "read failed") }
    }

    /** Send a user message into [conversationId]. Returns `"true"` or `"false"`.
     *  When the plugin config contains a `projectId`, the conversation is bound to that project on creation. */
    @JavascriptInterface
    fun sendChatMessage(conversationId: String, text: String): String {
        val provider = memoryProvider ?: return "false"
        val projectId = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(pluginConfigJson)
                .jsonObject["projectId"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.sendChatMessage(conversationId, text, projectId).toString()
            }
        }.getOrElse { "false" }
    }

    /** Resolve the project id that owns [conversationId]. */
    @JavascriptInterface
    fun resolveProjectId(conversationId: String): String {
        val provider = memoryProvider ?: return ""
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.resolveProjectId(conversationId) ?: ""
            }
        }.getOrDefault("")
    }

    /** Full metadata for [conversationId] as a JSON object:
     *  `{"id":"...","projectId":"...","modelId":"...","systemPromptId":"..."}`.
     */
    @JavascriptInterface
    fun getConversationInfo(conversationId: String): String {
        val provider = memoryProvider ?: return "{}"
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.getConversationInfo(conversationId)
            }
        }.getOrElse { "{}" }
    }

    /** Read long-term memories for a specific [projectId] (global + project-private merged). */
    @JavascriptInterface
    fun getProjectMemories(projectId: String): String {
        val provider = memoryProvider ?: return "[]"
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.getProjectMemories(projectId)
            }
        }.getOrElse { "[]" }
    }

    /**
     * Read-only current now-playing info (JSON string), delegating to [mediaInfoProvider].
     * Called via `orangeisland.getMediaInfo(package?)`. Returns an error JSON when no provider
     * is wired or no session is active — never throws. Read-only by design: the bridge exposes no
     * media-control surface, so a plugin page cannot pause/skip another app's playback on its own.
     */
    @JavascriptInterface
    fun getMediaInfo(packageFilter: String?): String {
        val provider = mediaInfoProvider ?: return errorJson("media_unavailable", "Media info not wired up on this host")
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider(packageFilter?.takeIf { it.isNotBlank() })
            }
        }.getOrElse { errorJson("media_error", it.message ?: "read failed") }
    }

    /** Create a new conversation inside [projectId]. Returns the new conversation id or "". */
    @JavascriptInterface
    fun createConversation(projectId: String, title: String, modelId: String, systemPromptId: String): String {
        val provider = memoryProvider ?: return ""
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.createConversation(projectId, title, modelId.ifBlank { null }, systemPromptId.ifBlank { null })
            }
        }.getOrElse { "" }
    }

    /** Reserved for future host鈫抪age pushes (e.g. tool-call notifications). No-op in v1. */
    fun pushToPage(payload: String) {
        evaluate("if (typeof window.orangeisland.onMessage === 'function') { window.orangeisland.onMessage(${jsonEncodeJsString(payload)}); }")
    }

    private suspend fun handle(call: Call) {
        val result = sandbox.callTool(plugin, call.tool, call.args)
        deliver(call.callbackId, result)
    }

    private fun deliver(callbackId: String?, resultJson: String) {
        // The bootstrap we inject defines window.orangeisland.__deliver(id, json) to dispatch to the
        // page-registered callback (or just log if none). We pass resultJson as a JS string
        // literal to avoid any quoting pitfalls.
        evaluate("window.orangeisland.__deliver && window.orangeisland.__deliver(${jsonEncodeJsString(callbackId ?: "null")}, ${jsonEncodeJsString(resultJson)});")
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
     * The bootstrap script prepended to the plugin's HTML. Defines the page-facing `orangeisland`
     * global on top of the native `__oiNative` JavascriptInterface.
     *
     * The native object only has `bridgeCall(tool, argsJson, callbackId)`; this bootstrap
     * wraps it in a friendlier `orangeisland.call(tool, args, cb)` that auto-generates callback ids
     * and registers the callback for later dispatch via `orangeisland.__deliver`.
     */
    val bootstrapScript: String = """
        (function() {
            if (window.orangeisland) return;
            var native = window.${bridgeObjectName};
            if (!native) { console.error('Orange Island bridge missing'); return; }
            var nextId = 0;
            var callbacks = {};
            // Synchronous getters — read the host's current values via @JavascriptInterface.
            // Always up-to-date because the bridge is a live Kotlin object the host mutates.
            function readConfig() {
                try { return JSON.parse(native.getConfig() || '{}') || {}; }
                catch (e) { return {}; }
            }
            window.orangeisland = {
                onMessage: null,
                // Snapshot getters: read live from the host each time they're accessed.
                get config() { return readConfig(); },
                get deviceId() { return native.getDeviceId() || ''; },
                call: function(tool, args, cb) {
                    var id = '__cb_' + (nextId++);
                    if (typeof cb === 'function') callbacks[id] = cb;
                    native.bridgeCall(tool, typeof args === 'string' ? args : JSON.stringify(args || {}), id);
                    return id;
                },
                // Memory accessors — read the host app's chat context and long-term memories.
                // All calls are synchronous (backed by @JavascriptInterface) and return parsed JSON.
                getChatHistory: function(conversationId, limit) {
                    try { return JSON.parse(native.getChatHistory(conversationId, limit || 50) || '[]'); }
                    catch (e) { console.error('getChatHistory error: ' + e); return []; }
                },
                getLongTermMemories: function(conversationId) {
                    try { return JSON.parse(native.getLongTermMemories(conversationId || '') || '[]'); }
                    catch (e) { console.error('getLongTermMemories error: ' + e); return []; }
                },
                getActiveMemory: function(conversationId) {
                    try { return native.getActiveMemory(conversationId || '') || ''; }
                    catch (e) { console.error('getActiveMemory error: ' + e); return ''; }
                },
                sendChatMessage: function(conversationId, text) {
                    try { return native.sendChatMessage(conversationId || '', text || '') === 'true'; }
                    catch (e) { console.error('sendChatMessage error: ' + e); return false; }
                },
                resolveProjectId: function(conversationId) {
                    try { return native.resolveProjectId(conversationId || '') || ''; }
                    catch (e) { console.error('resolveProjectId error: ' + e); return ''; }
                },
                getConversationInfo: function(conversationId) {
                    try { return JSON.parse(native.getConversationInfo(conversationId || '') || '{}'); }
                    catch (e) { console.error('getConversationInfo error: ' + e); return {}; }
                },
                getProjectMemories: function(projectId) {
                    try { return JSON.parse(native.getProjectMemories(projectId || '') || '[]'); }
                    catch (e) { console.error('getProjectMemories error: ' + e); return []; }
                },
                // Read-only current now-playing info. Returns the parsed JSON object the
                // get_now_playing tool produces (track/artist/album/coverUrl/…/isPlaying), or an
                // {error} object. No control surface — pages cannot pause/skip from here.
                getMediaInfo: function(packageFilter) {
                    try { return JSON.parse(native.getMediaInfo(packageFilter || null) || '{}'); }
                    catch (e) { console.error('getMediaInfo error: ' + e); return { error: 'media_error' }; }
                },
                createConversation: function(projectId, title, modelId, systemPromptId) {
                    try { return native.createConversation(projectId || '', title || '', modelId || '', systemPromptId || '') || ''; }
                    catch (e) { console.error('createConversation error: ' + e); return ''; }
                },
                __deliver: function(id, json) {
                    var cb = callbacks[id];
                    if (cb) {
                        delete callbacks[id];
                        try { cb(json); } catch (e) { console.error('callback error: ' + e); }
                    }
                }
            };
            // Also mirror them as plain globals (__OI_PLUGIN_CONFIG / __OI_USER_ID) for
            // plugins written against the sandbox contract (QuickJS globals).
            try {
                Object.defineProperty(globalThis, '__OI_PLUGIN_CONFIG', { get: function() { return readConfig(); }, configurable: true });
                Object.defineProperty(globalThis, '__OI_USER_ID', { get: function() { return native.getDeviceId() || ''; }, configurable: true });
            } catch (e) { /* defineProperty may throw on some engines — page still has orangeisland.config */ }
        })();
    """.trimIndent()

    private fun jsonEncodeJsString(s: String): String = JsonPrimitive(s).toString()

    private fun errorJson(type: String, message: String): String = buildJsonObject {
        put("error", type)
        put("message", message)
    }.toString()

    private data class Call(val callbackId: String, val tool: String, val args: String)
}
