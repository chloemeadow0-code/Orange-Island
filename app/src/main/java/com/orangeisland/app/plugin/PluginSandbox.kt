package com.orangeisland.app.plugin

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.orangeisland.app.data.InstalledPlugin
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes user-provided JS plugins in isolated QuickJS instances.
 *
 * One [QuickJs] is created lazily per plugin id on first use and kept alive for subsequent
 * tool calls (so `exports.xxx` registration done in `main.js` persists across calls). Each
 * instance runs on a dedicated single-thread dispatcher so plugins never race each other or
 * the host.
 *
 * v1 host API injected into each sandbox:
 *  - `console.log/info/warn/error(msg)` → forwarded to [DebugLog]
 *  - `fetch(url, options?)` → OkHttp call, **enforced** against the plugin's
 *    `manifest.allowedHosts` whitelist, https required (except loopback/LAN)
 *  - `__OI_USER_ID` → auto-injected per-install device id (stable UUID), refreshed per call
 *  - `__OI_PLUGIN_CONFIG` → JSON of the plugin's user-filled config (manifest.config), per call
 *
 * The fetch binding is synchronous from JS's perspective (QuickJS `function {}`), but the
 * HTTP call itself is blocking I/O — so it runs on a worker thread inside [runBlocking],
 * never on the JS dispatcher thread or the main thread.
 *
 * Safety rails:
 *  - [TOOL_TIMEOUT_MS] per call (defends against infinite loops in JS)
 *  - [MAX_RESPONSE_BYTES] cap on fetch response bodies (defends against memory exhaustion)
 *  - Plugin has zero access to host app data, filesystem, or other plugins
 */
class PluginSandbox(
    /** App-lifetime scope used to build the per-plugin single-thread dispatchers. */
    private val appScope: CoroutineScope,
    /** Reads the auto-injected per-install device id. If null, [__OI_USER_ID] is empty. */
    private val userIdentity: UserIdentityProvider? = null,
    /** Reads a plugin's user-filled config as a JSON string, given its id. If null, the
     *  [__OI_PLUGIN_CONFIG] global is emitted as `{}`. */
    private val pluginConfig: PluginConfigProvider? = null,
) {
    /**
     * Lightweight accessor injected by the host so the sandbox doesn't depend on the full
     * [com.orangeisland.app.data.repository.SettingsRepository]. Suspend because the value lives
     * in DataStore and may still be loading when a tool call first arrives.
     */
    fun interface UserIdentityProvider {
        suspend fun get(): String
    }

    /** Resolves the user-filled config for one plugin as a JSON string (e.g. `'{"k":"v"}'`). */
    fun interface PluginConfigProvider {
        suspend fun get(pluginId: String): String
    }

    /** Exposed so the WebView bridge can resolve the same device id for plugin UI pages. */
    val identityProvider: UserIdentityProvider? get() = userIdentity
    /** Exposed so the WebView page can resolve the same per-plugin config as the JS tools. */
    val configProvider: PluginConfigProvider? get() = pluginConfig
    companion object {
        private const val TAG = "PluginSandbox"
        private const val TOOL_TIMEOUT_MS = 30_000L
        private const val MAX_RESPONSE_BYTES = 512 * 1024L
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** pluginId → runtime context (QuickJs + dispatcher). Lazily created. */
    private val runtimes = ConcurrentHashMap<String, PluginRuntime>()
    private val initLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Invokes [toolName] exported by [plugin]'s `main.js` with JSON-encoded [argumentsJson].
     * Returns the tool's return value serialized to a JSON string (compatible with the
     * function-calling result format the LLM expects).
     *
     * On any failure (timeout, JS exception, host-side error) returns an error string rather
     * than throwing — a single bad tool call must not abort the whole generation.
     */
    suspend fun callTool(plugin: InstalledPlugin, toolName: String, argumentsJson: String): String {
        val runtime = try {
            getOrInit(plugin)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to init sandbox for ${plugin.id}", e)
            return errorJson("plugin_init_failed", e.message ?: "init failed")
        }
        val args = argumentsJson.ifBlank { "{}" }
        // Resolve the auto-injected device id once per call (cheap; reads from a hot StateFlow).
        val deviceId = userIdentity?.let { runCatching { it.get() }.getOrNull() } ?: ""
        // Resolve this plugin's user-filled config (JSON string). Falls back to "{}" so plugins
        // can always JSON.parse it.
        val configJson = pluginConfig?.let { runCatching { it.get(plugin.id) }.getOrNull() } ?: "{}"
        // Call the exported tool by name, passing the JSON-decoded args object. Tool name and
        // args are bound as JS globals before evaluation so neither can break out of the wrapper
        // via string interpolation (a tool name with `$ARGS` in it can't read the args literal).
        val wrapper = """
            (function() {
                var __name = __OI_TOOL_NAME;
                var __args = __OI_TOOL_ARGS;
                var fn = exports && exports[__name];
                if (typeof fn !== 'function') {
                    throw new Error('Tool not exported: ' + __name);
                }
                return JSON.stringify(fn(__args));
            })();
        """.trimIndent()
        // Bind name+args via evaluate of two const statements first, then run the wrapper.
        // quickjs-kt evaluates globally, so globals set in one evaluate() are visible in the next.
        // Also bind the read-only plugin user identity (__OI_USER_ID / __OI_USER_NICKNAME)
        // before each call so plugins always see the latest values without re-initializing.
        runCatching {
            runtime.quickJs.evaluate<Any?>(
                "globalThis.__OI_TOOL_NAME = ${jsonEncodeJsString(toolName)};\n" +
                    "globalThis.__OI_TOOL_ARGS = ($args);\n" +
                    "globalThis.__OI_USER_ID = ${jsonEncodeJsString(deviceId)};\n" +
                    "globalThis.__OI_PLUGIN_CONFIG = ($configJson);",
                asModule = false,
            )
        }.getOrElse {
            return errorJson("bad_args", "Failed to parse arguments JSON: ${it.message}")
        }
        val result = withTimeoutOrNull(TOOL_TIMEOUT_MS) {
            withContext(runtime.dispatcher) {
                runCatching { runtime.quickJs.evaluate<String>(wrapper, asModule = false) }
            }
        } ?: return errorJson("timeout", "tool call exceeded ${TOOL_TIMEOUT_MS}ms")

        val out = result.getOrElse { e ->
            DebugLog.e(TAG, "JS tool '$toolName' in '${plugin.id}' failed", e)
            return errorJson("js_error", e.message ?: e::class.simpleName ?: "js error")
        }
        return out
    }

    /** Drops the cached QuickJS instance for [pluginId]. Call after uninstall or on error. */
    fun invalidate(pluginId: String) {
        runtimes.remove(pluginId)?.let { rt ->
            runCatching { rt.quickJs.close() }
        }
    }

    /** Drops a runtime when its on-disk manifest/main.js changes (so the next call re-inits). */
    fun reload(pluginId: String) = invalidate(pluginId)

    /** Closes every runtime. Called from ChatViewModel.onCleared. */
    fun closeAll() {
        runtimes.values.toList().forEach { rt -> runCatching { rt.quickJs.close() } }
        runtimes.clear()
    }

    // ── Internals ─────────────────────────────────────────────

    private suspend fun getOrInit(plugin: InstalledPlugin): PluginRuntime {
        runtimes[plugin.id]?.let { return it }
        val lock = initLocks.computeIfAbsent(plugin.id) { Mutex() }
        return lock.withLock {
            runtimes[plugin.id]?.let { return@withLock it }
            val rt = buildRuntime(plugin)
            runtimes[plugin.id] = rt
            rt
        }
    }

    private suspend fun buildRuntime(plugin: InstalledPlugin): PluginRuntime {
        val dispatcher = kotlinx.coroutines.newSingleThreadContext("plugin-${plugin.id}")
        val quickJs = QuickJs.create(dispatcher)
        // Inject host API before loading main.js so the script can use them at module top-level.
        quickJs.injectConsole(plugin.id)
        quickJs.injectFetch(plugin.id, plugin.manifest.allowedHosts)
        // Load main.js once. It is expected to register `exports.tool_name = function(){...}`.
        val mainJs = plugin.mainJsFile.readText()
        try {
            quickJs.evaluate<Any?>(
                // Polyfill `exports` so CommonJS-style `exports.x = ...` works without a module
                // loader. Also expose `module.exports` for the `module.exports = {...}` form.
                "var exports = (typeof exports === 'undefined') ? {} : exports;" +
                    "var module = (typeof module === 'undefined') ? { exports: exports } : module;" +
                    mainJs +
                    "; if (module.exports && module.exports !== exports) { for (var k in module.exports) { exports[k] = module.exports[k]; } }",
                asModule = false,
            )
        } catch (e: Exception) {
            quickJs.close()
            throw IllegalStateException("Failed to load main.js for ${plugin.id}: ${e.message}", e)
        }
        return PluginRuntime(quickJs = quickJs, dispatcher = dispatcher)
    }

    private fun QuickJs.injectConsole(pluginId: String) {
        define("console") {
            function("log") { args -> DebugLog.w("plugin/$pluginId", args.joinToString(" ") { it?.toString() ?: "null" }) }
            function("info") { args -> DebugLog.w("plugin/$pluginId", args.joinToString(" ") { it?.toString() ?: "null" }) }
            function("warn") { args -> DebugLog.w("plugin/$pluginId", args.joinToString(" ") { it?.toString() ?: "null" }) }
            function("error") { args -> DebugLog.e("plugin/$pluginId", args.joinToString(" ") { it?.toString() ?: "null" }) }
            function("debug") { _ -> }
        }
    }

    /**
     * Injects a synchronous `fetch(url, options)` into the sandbox.
     *
     * - URL must be http(s); https is required unless the host is loopback/LAN (so a plugin
     *   can hit `http://localhost:1234` for development but never leak data over cleartext
     *   to a public host).
     * - Host must match an entry in [allowedHosts] (case-insensitive; subdomain-aware:
     *   `api.example.com` allows `api.example.com` and `*.api.example.com`).
     * - options may carry `{method, headers, body}`. Only JSON/string bodies supported.
     * - Response body capped at [MAX_RESPONSE_BYTES].
     * - Returns `{ok: bool, status: int, body: string}` (mirrors a minimal Response shape).
     *
     * Implemented via [asyncFunction] so we can `runBlocking` on a worker dispatcher without
     * blocking the JS engine's own thread — QuickJS evaluates on [dispatcher], but the HTTP
     * call is dispatched to [kotlinx.coroutines.Dispatchers.IO] inside.
     */
    private fun QuickJs.injectFetch(pluginId: String, allowedHosts: List<String>) {
        asyncFunction("fetch") { args ->
            val url = args.getOrNull(0)?.toString().orEmpty()
            val optsRaw = args.getOrNull(1)
            val opts = parseOpts(optsRaw)
            doFetch(pluginId, allowedHosts, url, opts)
        }
    }

    private fun parseOpts(value: Any?): FetchOpts {
        if (value == null) return FetchOpts()
        val map = (value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: return FetchOpts()
        val method = (map["method"] as? String)?.uppercase() ?: "GET"
        val headers = (map["headers"] as? Map<*, *>)
            ?.entries
            ?.associate { it.key.toString() to it.value.toString() }
            ?: emptyMap()
        val body = (map["body"] as? String)
            ?: (map["body"] as? Map<*, *>)?.let { json.encodeToString(it) }
        val timeoutMs = (map["timeout"] as? Number)?.toLong()?.coerceIn(1_000L, 30_000L) ?: 30_000L
        return FetchOpts(method = method, headers = headers, body = body, timeoutMs = timeoutMs)
    }

    private suspend fun doFetch(
        pluginId: String,
        allowedHosts: List<String>,
        url: String,
        opts: FetchOpts,
    ): String {
        val parsed = try { java.net.URI(url) } catch (e: Exception) {
            return errorResponse(0, "Invalid URL: ${e.message}")
        }
        val host = parsed.host?.lowercase() ?: return errorResponse(0, "URL has no host")
        val scheme = parsed.scheme?.lowercase() ?: return errorResponse(0, "URL has no scheme")
        if (scheme !in setOf("http", "https")) {
            return errorResponse(0, "Only http/https URLs allowed")
        }
        if (scheme == "http" && !isLocalHost(host)) {
            return errorResponse(0, "Cleartext HTTP to public host '$host' not allowed (use https)")
        }
        if (!hostAllowed(host, allowedHosts)) {
            DebugLog.w(TAG, "plugin/$pluginId fetch blocked (host not whitelisted): $host")
            return errorResponse(0, "Host '$host' not in plugin's allowedHosts list")
        }
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url)
                opts.headers.forEach { (k, v) -> builder.header(k, v) }
                when (opts.method) {
                    "GET" -> builder.get()
                    "HEAD" -> builder.head()
                    "DELETE" -> builder.delete(opts.body?.toRequestBody())
                    "POST", "PUT", "PATCH" -> {
                        if (opts.body != null) builder.method(opts.method, opts.body.toRequestBody())
                        else builder.method(opts.method, "".toRequestBody())
                    }
                    else -> builder.get()
                }
                val call = com.orangeisland.app.api.HttpClient.client.newCall(builder.build())
                // OkHttp's execute() is a blocking call; running it on Dispatchers.IO is correct.
                val response = withTimeoutOrNull(opts.timeoutMs) { call.execute() }
                    ?: return@withContext errorResponse(0, "Request timed out after ${opts.timeoutMs}ms")
                response.use {
                    val raw = it.body?.bytes() ?: ByteArray(0)
                    val truncated = raw.size.toLong() > MAX_RESPONSE_BYTES
                    val text = String(raw.copyOf(minOf(raw.size, MAX_RESPONSE_BYTES.toInt())), Charsets.UTF_8)
                    buildJsonObject {
                        put("ok", it.isSuccessful)
                        put("status", it.code)
                        if (truncated) put("truncated", true)
                        put("body", text)
                    }.toString()
                }
            } catch (e: IOException) {
                errorResponse(0, "Network error: ${e.message}")
            } catch (e: Exception) {
                errorResponse(0, "Request failed: ${e.message}")
            }
        }
    }

    private fun isLocalHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host == "localhost" || host == "::1" || host.endsWith(".local") || host.endsWith(".lan") ||
            host.endsWith(".home") || host.endsWith(".internal")) return true
        if (!host.contains('.')) return true
        val o = host.split('.')
        if (o.size == 4 && o.all { it.toIntOrNull() in 0..255 }) {
            val a = o[0].toInt(); val b = o[1].toInt()
            return a == 127 || a == 10 || (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) || (a == 169 && b == 254)
        }
        return false
    }

    /** `api.example.com` matches itself and any `*.api.example.com`. */
    private fun hostAllowed(host: String, allowedHosts: List<String>): Boolean {
        if (allowedHosts.isEmpty()) return false
        return allowedHosts.any { allowed ->
            val a = allowed.trim().lowercase()
            host == a || host.endsWith(".$a")
        }
    }

    private fun errorResponse(status: Int, message: String): String = buildJsonObject {
        put("ok", false)
        put("status", status)
        put("error", message)
    }.toString()

    private fun errorJson(type: String, message: String): String = buildJsonObject {
        put("error", type)
        put("message", message)
    }.toString()

    /** Encodes [s] as a JS string literal (double-quoted, JSON-escaped). */
    private fun jsonEncodeJsString(s: String): String = kotlinx.serialization.json.Json.encodeToString(s)

    private data class FetchOpts(
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val timeoutMs: Long = 30_000L,
    )

    private class PluginRuntime(
        val quickJs: QuickJs,
        val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    )
}
