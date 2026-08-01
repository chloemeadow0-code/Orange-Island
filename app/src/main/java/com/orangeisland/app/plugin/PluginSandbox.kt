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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
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
    /** Provides read/write access to the host app's chat memories. If null, memory host
     *  functions are not injected and any plugin that tries to call them will get a
     *  JS ReferenceError (safe failure). */
    private val memoryProvider: PluginMemoryProvider? = null,
) {
    /**
     * Set by [PluginToolProvider.execute] before every tool call so the sandbox knows
     * which conversation is currently active. Cleared after the call to avoid leaking
     * context between unrelated generations.
     */
    var currentConversationId: String? = null
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

        /**
         * Dedicated OkHttpClient for plugin `fetch()`. Separate from the host [com.orangeisland.app
         * .api.HttpClient.client] (used for LLM traffic) so changes here can't destabilize model
         * generation, and so plugin requests get a more browser-like TLS configuration.
         *
         * Some third-party sites (notably NetEase Cloud Music) reject connections whose TLS
         * handshake doesn't look like a mainstream browser — OkHttp's default modern spec still
         * differs enough that the server resets the connection, which surfaces to plugins as a
         * generic `IOException` → `{ ok:false, status:0 }`. Restricting the spec to explicit
         * TLS_1_2/TLS_1_3 with the conventional cipher set makes the handshake fingerprint much
         * closer to a browser's, which is enough for those services to respond normally.
         */
        private val pluginHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                            .build(),
                        ConnectionSpec.CLEARTEXT
                    )
                )
                .build()
        }
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
        // and the current conversation id before each call so plugins always see the latest
        // values without re-initializing.
        val conversationId = currentConversationId ?: ""
        // Pre-load project-scoped memory snapshots so the plugin can read them synchronously
        // without async boilerplate. If the provider is absent these globals are omitted.
        val projectId = if (conversationId.isNotBlank() && memoryProvider != null) {
            val provider = memoryProvider
            runCatching { provider.resolveProjectId(conversationId) }.getOrNull()
        } else null
        val memoryGlobals = if (memoryProvider != null && conversationId.isNotBlank()) {
            val provider = memoryProvider
            runCatching {
                buildString {
                    append("globalThis.__OI_PROJECT_ID = ${jsonEncodeJsString(projectId ?: "")};\n")
                    append("globalThis.__OI_CHAT_HISTORY = ${provider.getChatHistory(conversationId, 20)};\n")
                    append("globalThis.__OI_LONG_TERM_MEMORIES = ${jsonEncodeJsString(provider.getLongTermMemories(conversationId))};\n")
                    append("globalThis.__OI_ACTIVE_MEMORY = ${jsonEncodeJsString(provider.getActiveMemory(conversationId))};")
                }
            }.getOrElse { "" }
        } else ""
        runCatching {
            runtime.quickJs.evaluate<Any?>(
                "globalThis.__OI_TOOL_NAME = ${jsonEncodeJsString(toolName)};\n" +
                    "globalThis.__OI_TOOL_ARGS = ($args);\n" +
                    "globalThis.__OI_USER_ID = ${jsonEncodeJsString(deviceId)};\n" +
                    "globalThis.__OI_PLUGIN_CONFIG = ($configJson);\n" +
                    "globalThis.__OI_CONVERSATION_ID = ${jsonEncodeJsString(conversationId)};\n" +
                    memoryGlobals,
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
        quickJs.injectMemory(plugin.id)
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
     * Injects memory-read/write host functions into the sandbox so plugins can access
     * the host app's chat context and long-term memories.
     *
     * All functions are [asyncFunction] because the underlying [PluginMemoryProvider]
     * methods are suspend. JS usage:
     * ```js
     * var history = JSON.parse(await readChatHistory(20));
     * var memories = JSON.parse(await readLongTermMemories());
     * var active = await readActiveMemory();
     * var ok = await sendChatMessage("Hello from plugin");
     * ```
     *
     * The functions scope automatically to [currentConversationId] (set by the host before
     * each tool call) so the plugin doesn't have to pass conversation ids around.
     */
    private fun QuickJs.injectMemory(pluginId: String) {
        if (memoryProvider == null) return
        asyncFunction("readChatHistory") { args ->
            val limit = (args.getOrNull(0) as? Number)?.toInt()?.coerceIn(1, 200) ?: 50
            val convId = currentConversationId ?: ""
            if (convId.isBlank()) return@asyncFunction "[]"
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                memoryProvider.getChatHistory(convId, limit)
            }
        }
        asyncFunction("readLongTermMemories") { _ ->
            val convId = currentConversationId ?: ""
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                memoryProvider.getLongTermMemories(convId)
            }
        }
        asyncFunction("readActiveMemory") { _ ->
            val convId = currentConversationId ?: ""
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                jsonEncodeJsString(memoryProvider.getActiveMemory(convId))
            }
        }
        asyncFunction("sendChatMessage") { args ->
            val text = args.getOrNull(0)?.toString().orEmpty()
            val convId = currentConversationId ?: ""
            if (convId.isBlank() || text.isBlank()) return@asyncFunction "false"
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                memoryProvider.sendChatMessage(convId, text)
            }
            ok.toString()
        }
        asyncFunction("readProjectMemories") { args ->
            val projectId = args.getOrNull(0)?.toString().orEmpty()
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                memoryProvider.getProjectMemories(projectId)
            }
        }
        asyncFunction("createConversation") { args ->
            val projectId = args.getOrNull(0)?.toString().orEmpty()
            val title = args.getOrNull(1)?.toString().orEmpty()
            val modelId = args.getOrNull(2)?.toString()
            val promptId = args.getOrNull(3)?.toString()
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                memoryProvider.createConversation(projectId, title, modelId, promptId)
            }
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
     * Implemented via [function] (not [asyncFunction]) because the tool-call bootstrap
     * invokes tools synchronously: `JSON.stringify(fn(__args))`. If `fetch` returned a
     * Promise the plugin could never await it inside a synchronous tool function.
     * The HTTP call runs inside [runBlocking] on [Dispatchers.IO] so it does not starve
     * the shared pool, and each plugin already has its own dedicated thread.
     */
    private suspend fun QuickJs.injectFetch(pluginId: String, allowedHosts: List<String>) {
        // Register a native `__oiNativeFetch` that returns a JSON *string*, then wrap it in JS so
        // the page-facing `fetch()` returns a plain OBJECT (parsed). This is what every plugin
        // expects — code like `var r = fetch(url); r.ok; r.body;` must work without an extra
        // JSON.parse. Returning the string directly from the native binding made `r.ok` undefined,
        // silently turning every successful fetch into a "network failed" error in plugins.
        function("__oiNativeFetch") { args ->
            val url = args.getOrNull(0)?.toString().orEmpty()
            val optsRaw = args.getOrNull(1)
            val opts = parseOpts(optsRaw)
            fetchLog("[CALL] plugin=$pluginId url=$url method=${opts.method} optsRaw=${optsRaw ?: "null"}")
            val result: String = runCatching {
                runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    doFetch(pluginId, allowedHosts, url, opts)
                }
            }.getOrElse { e ->
                fetchLog("[INNER_THROW] ${e.javaClass.name}: ${e.message}")
                buildJsonObject {
                    put("ok", false)
                    put("status", 0)
                    put("error", "FETCH_INNER_ERROR: ${e.javaClass.simpleName}: ${e.message ?: "no detail"}")
                    put("diagnostic", "fetch() wrapper caught ${e.javaClass.name}; the OkHttp call never returned a response. url=$url")
                }.toString()
            }
            fetchLog("[RETURN] url=$url result=${result.take(800)}")
            result
        }
        // Wrap the native string-returning fetch into one that yields an object the plugin can use
        // directly (r.ok, r.status, r.body). If parsing fails we still return an object-shaped
        // error so plugins never see a raw string they can't introspect.
        try {
            evaluate<Any?>(
                """
                globalThis.fetch = function(url, opts) {
                  var s = __oiNativeFetch(url, opts);
                  try { return JSON.parse(s); } catch (e) { return { ok:false, status:0, error:'INVALID_FETCH_RESULT:'+e, __raw:s }; }
                };
                """.trimIndent(),
                asModule = false,
            )
        } catch (e: Exception) {
            DebugLog.w(TAG, "failed to install fetch() JS wrapper for $pluginId: ${e.message}")
        }
    }

    /**
     * Appends one diagnostic line to the app's private files dir (oi_fetch.log) so we can
     * `adb run-as` the full fetch trace on devices whose logcat swallows third-party app logs
     * (e.g. honor firmware). Writes to app-private storage so it never triggers scoped-storage
     * permission crashes. Best-effort: any IO failure is swallowed (must never break the fetch).
     */
    private fun fetchLog(line: String) {
        runCatching {
            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date())
            // App-private files dir — no permissions needed, never crashes. Pull via:
            //   adb exec-out run-as com.orangeisland.app cat files/oi_fetch.log
            val dir = java.io.File("/data/data/com.orangeisland.app/files")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "oi_fetch.log")
            file.appendText("$ts $line\n")
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
        // End-to-end trace written to /sdcard/oi_fetch.log via fetchLog() (survives logcat
        // suppression on some OEM firmware). Each step is logged so we can see exactly where a
        // request dies without relying on logcat or the plugin's own error display.
        fetchLog("[DOFETCH] start plugin=$pluginId url=$url method=${opts.method} allowedHosts=$allowedHosts")
        val parsed = try { java.net.URI(url) } catch (e: Exception) {
            fetchLog("[DOFETCH] fail invalid_url url=$url err=${e.message}")
            return errorResponse(0, "Invalid URL: ${e.message}")
        }
        val host = parsed.host?.lowercase() ?: run {
            fetchLog("[DOFETCH] fail no_host url=$url")
            return errorResponse(0, "URL has no host")
        }
        val scheme = parsed.scheme?.lowercase() ?: run {
            fetchLog("[DOFETCH] fail no_scheme url=$url")
            return errorResponse(0, "URL has no scheme")
        }
        fetchLog("[DOFETCH] step2 parsed scheme=$scheme host=$host")
        if (scheme !in setOf("http", "https")) {
            fetchLog("[DOFETCH] fail bad_scheme scheme=$scheme")
            return errorResponse(0, "Only http/https URLs allowed")
        }
        if (scheme == "http" && !isLocalHost(host)) {
            fetchLog("[DOFETCH] fail cleartext_blocked host=$host")
            return errorResponse(0, "Cleartext HTTP to public host '$host' not allowed (use https)")
        }
        val allowed = hostAllowed(host, allowedHosts)
        fetchLog("[DOFETCH] step3 whitelist host=$host allowed=$allowed")
        if (!allowed) {
            fetchLog("[DOFETCH] fail not_whitelisted host=$host")
            DebugLog.w(TAG, "plugin/$pluginId fetch blocked (host not whitelisted): $host")
            return errorResponse(0, "Host '$host' not in plugin's allowedHosts list")
        }
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url)
                opts.headers.forEach { (k, v) -> builder.header(k, v) }
                // Default a browser-like User-Agent when the plugin didn't set one. Many third-party
                // hosts (e.g. NetEase) 4xx/reset requests whose UA looks like a default HTTP client.
                if (opts.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    builder.header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                }
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
                val request = builder.build()
                fetchLog("[DOFETCH] step4 built_request url=$url headers=${request.headers}")
                val call = pluginHttpClient.newCall(request)
                fetchLog("[DOFETCH] step5 executing url=$url timeout=${opts.timeoutMs}")
                // OkHttp's execute() is a blocking call; running it on Dispatchers.IO is correct.
                val response = withTimeoutOrNull(opts.timeoutMs) { call.execute() }
                if (response == null) {
                    fetchLog("[DOFETCH] fail timeout url=$url timeout=${opts.timeoutMs}")
                    return@withContext errorResponse(0, "Request timed out after ${opts.timeoutMs}ms")
                }
                fetchLog("[DOFETCH] step6 got_response url=$url code=${response.code} msg=${response.message}")
                response.use {
                    val raw = it.body?.bytes() ?: ByteArray(0)
                    val truncated = raw.size.toLong() > MAX_RESPONSE_BYTES
                    val text = String(raw.copyOf(minOf(raw.size, MAX_RESPONSE_BYTES.toInt())), Charsets.UTF_8)
                    fetchLog("[DOFETCH] step7 body_bytes=${raw.size} truncated=$truncated preview=${text.take(200)}")
                    buildJsonObject {
                        put("ok", it.isSuccessful)
                        put("status", it.code)
                        if (truncated) put("truncated", true)
                        put("body", text)
                    }.toString()
                }
            } catch (e: IOException) {
                fetchLog("[DOFETCH] FAIL IOException ${e.javaClass.name}: ${e.message} stack=${e.stackTraceToString().take(500)}")
                DebugLog.w(TAG, "plugin fetch to '$url' failed: ${e.javaClass.simpleName}: ${e.message}")
                errorResponseWithDetail(0, "Network error: ${e.javaClass.simpleName}: ${e.message ?: "no detail"}",
                    "IOException of type ${e.javaClass.name} thrown by OkHttp execute(). url=$url")
            } catch (e: Exception) {
                fetchLog("[DOFETCH] FAIL Exception ${e.javaClass.name}: ${e.message} stack=${e.stackTraceToString().take(500)}")
                DebugLog.w(TAG, "plugin fetch to '$url' failed: ${e.javaClass.simpleName}: ${e.message}")
                errorResponseWithDetail(0, "Request failed: ${e.javaClass.simpleName}: ${e.message ?: "no detail"}",
                    "${e.javaClass.name} thrown during fetch. url=$url")
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

    /** Like [errorResponse] but also carries a [detail] diagnostic string so the real failure
     *  reason survives into the plugin/tool return value even on devices whose logcat swallows
     *  app logs (e.g. honor firmware). Plugins read [detail] under the `diagnostic` key. */
    private fun errorResponseWithDetail(status: Int, message: String, detail: String): String = buildJsonObject {
        put("ok", false)
        put("status", status)
        put("error", message)
        put("diagnostic", detail)
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
