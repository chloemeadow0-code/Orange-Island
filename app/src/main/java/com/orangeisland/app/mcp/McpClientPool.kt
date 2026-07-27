package com.orangeisland.app.mcp

import com.orangeisland.app.data.McpServerConfig
import com.orangeisland.app.util.DebugLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.headers
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** Per-server connection status, surfaced to the UI as the leading icon on each MCP server row. */
enum class McpStatus {
    /** No live connection (offline, error, connected-but-zero-tools). UI shows an error icon. */
    DISCONNECTED,
    /** A connect / reconnect attempt is in flight. UI shows a spinner. */
    CONNECTING,
    /** Connected AND exposes ≥1 tool. UI shows the normal extension icon. */
    READY;
}

/**
 * Cached tool list for an MCP server, with a short TTL so newly-added server-side tools
 * are picked up without forcing a full reconnect on every generation.
 */
private data class CachedTools(val tools: List<Tool>, val fetchedAt: Long)

/**
 * A live connection to one MCP server: the SDK [Client], plus the cached tool list and
 * the connect coroutine so it can be cancelled on close.
 */
private class ConnectedServer(
    val client: Client,
    val config: McpServerConfig,
    var toolsCache: CachedTools? = null,
    var connectJob: Job? = null,
)

/**
 * Long-lived pool of MCP client connections, keyed by server id.
 *
 * One [Client] per configured server is established lazily on first use and kept open for
 * subsequent requests. [listTools] caches the result for [TOOLS_CACHE_TTL_MS] to avoid
 * hammering the server on every chat turn. [callTool] dispatches to the right server
 * connection.
 *
 * The pool is process-scoped (single instance in AppContainer) so connections survive
 * ViewModel recreation; [closeAll] is called from ChatViewModel.onCleared for graceful
 * shutdown when the app is truly done.
 *
 * All public methods are safe to call from any coroutine — connection establishment is
 * serialized per-server by a mutex, and the connections map is a ConcurrentHashMap.
 */
class McpClientPool(
    /** Scope for connection-establishment coroutines. Should outlive individual requests. */
    private val ioScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "McpClientPool"
        private const val TOOLS_CACHE_TTL_MS = 60_000L
        private const val CLIENT_NAME = "orangeisland"
        private const val CLIENT_VERSION = "1.0"
        // ── Stability tuning ─────────────────────────────────────────────────
        /** Hard cap on a single connect attempt (the SDK's client.connect transport handshake). */
        private const val CONNECT_TIMEOUT_MS = 15_000L
        /** Hard cap on a single listTools / callTool round-trip once connected. */
        private const val REQUEST_TIMEOUT_MS = 20_000L
        /** How many times to retry a failing connect, with exponential backoff. */
        private const val MAX_CONNECT_ATTEMPTS = 3
        /** Interval between heartbeat probes (keeps statuses fresh and reconnects after blips). */
        private const val HEARTBEAT_INTERVAL_MS = 120_000L
        /** Fast bounds for heartbeat probes: single attempt, short timeout. Probes run every
         *  [HEARTBEAT_INTERVAL_MS], so a failed probe just retries next tick — there's no need to
         *  retry-with-backoff inside one probe (that's what made the spinner last ~50s on a dead
         *  server). Generation-time [getOrConnect] still uses the slow, retrying path. */
        private const val PROBE_CONNECT_TIMEOUT_MS = 5_000L
        private const val PROBE_REQUEST_TIMEOUT_MS = 8_000L
    }

    private val connections = ConcurrentHashMap<String, ConnectedServer>()
    private val connectLocks = ConcurrentHashMap<String, Mutex>()
    private val json = Json { ignoreUnknownKeys = true }

    /** Per-server status, observed by the MCP settings UI to render the three-state icon. Every
     *  transition goes through [setStatus] so updates are atomic + thread-safe. A server that is
     *  configured but has never been probed is simply absent from the map (UI defaults to error). */
    private val _statuses = MutableStateFlow<Map<String, McpStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, McpStatus>> = _statuses.asStateFlow()

    private fun setStatus(id: String, status: McpStatus) {
        _statuses.update { it + (id to status) }
    }

    private fun clearStatus(id: String) {
        _statuses.update { it - id }
    }

    /** Heartbeat guardian: periodically probes every enabled server so the UI shows live status
     *  and silently reconnects after a transient network blip. null until [startMonitoring]. */
    @Volatile private var heartbeatJob: Job? = null

    /**
     * Lazily connects to [config] (or returns the existing connection). Throws on real
     * failure so callers can surface a concrete error message.
     *
     * Emits CONNECTING for the duration of the (possibly retried) attempt, and DISCONNECTED if
     * every attempt fails. Retry uses exponential backoff so a transient blip doesn't cost the
     * user a hard failure.
     */
    private suspend fun getOrConnect(config: McpServerConfig): ConnectedServer {
        connections[config.id]?.let { return it }
        val lock = connectLocks.computeIfAbsent(config.id) { Mutex() }
        return lock.withLock {
            // Double-check after acquiring the lock — another caller may have connected.
            connections[config.id]?.let { return@withLock it }
            setStatus(config.id, McpStatus.CONNECTING)
            var lastError: Exception? = null
            for (attempt in 1..MAX_CONNECT_ATTEMPTS) {
                try {
                    val server = connectFresh(config)
                    connections[config.id] = server
                    return@withLock server
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    DebugLog.w(TAG, "connect attempt $attempt/$MAX_CONNECT_ATTEMPTS failed for " +
                        "'${config.name}': ${e.message}")
                    if (attempt < MAX_CONNECT_ATTEMPTS) {
                        // Exponential backoff: 1s, 2s, 4s, … between retries.
                        delay(1000L * (1L shl (attempt - 1)))
                    }
                }
            }
            setStatus(config.id, McpStatus.DISCONNECTED)
            throw lastError ?: java.io.IOException("Failed to connect to MCP server '${config.name}'")
        }
    }

    private suspend fun connectFresh(config: McpServerConfig): ConnectedServer {
        // One HttpClient per connection — SSE state is connection-scoped. CIO is a pure-Kotlin
        // engine (no JNI) so it's safe on Android minSdk 24+.
        val httpClient = HttpClient(OkHttp) {
            install(SSE)
        }
        val customHeaders = parseHeaders(config)
        val normalizedUrl = normalizeUrlHost(config.url)
        val transport = when (config.transport) {
            McpServerConfig.TRANSPORT_STREAMABLE -> StreamableHttpClientTransport(
                client = httpClient,
                url = normalizedUrl,
                requestBuilder = { applyCustomHeaders(customHeaders) },
            )
            McpServerConfig.TRANSPORT_SSE -> SseClientTransport(
                client = httpClient,
                urlString = normalizedUrl,
                requestBuilder = { applyCustomHeaders(customHeaders) },
            )
            else -> throw IllegalArgumentException("Unknown MCP transport: ${config.transport}")
        }
        val client = Client(clientInfo = Implementation(name = CLIENT_NAME, version = CLIENT_VERSION))
        // The SDK handshake can hang indefinitely on an unresponsive/blocked server; bound it so a
        // dead server surfaces as an error (and a retry) instead of stalling the whole generation.
        withTimeout(CONNECT_TIMEOUT_MS) { client.connect(transport) }
        DebugLog.w(TAG, "Connected to MCP server '${config.name}' (${config.transport} @ ${config.url})")
        return ConnectedServer(client = client, config = config)
    }

    /**
     * 把 URL 里的域名部分转换成 Punycode（ASCII 兼容编码），修复中文等非 ASCII
     * 域名（IDN）在部分底层网络库里不会自动转换、导致 DNS 查询/连接失败的问题。
     * 浏览器会自动做这层转换，但 HTTP 客户端库不一定会，所以在这里显式处理，
     * 保证发出去的请求用的是 DNS 真正认识的格式。转换失败时静默回退到原始 URL，
     * 不影响本来就是纯 ASCII 域名的正常场景。
     */
    private fun normalizeUrlHost(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return url
            val asciiHost = java.net.IDN.toASCII(host)
            if (asciiHost == host) return url
            java.net.URI(
                uri.scheme, uri.userInfo, asciiHost, uri.port,
                uri.path, uri.query, uri.fragment
            ).toString()
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to normalize MCP server URL host: ${e.message}")
            url
        }
    }

    /** Parses [McpServerConfig.headersJson] into a flat header map. Unknown shapes yield an empty map. */
    private fun parseHeaders(config: McpServerConfig): Map<String, String> = try {
        if (config.headersJson.isBlank()) return emptyMap()
        val obj = json.parseToJsonElement(config.headersJson).jsonObject
        obj.entries.mapNotNull { (k, v) ->
            when {
                v is JsonPrimitive -> k to v.content
                else -> null
            }
        }.toMap()
    } catch (e: Exception) {
        DebugLog.w(TAG, "Failed to parse headers for MCP server '${config.name}': ${e.message}")
        emptyMap()
    }

    /** Applies the configured custom headers to a Ktor request builder. */
    private fun io.ktor.client.request.HttpRequestBuilder.applyCustomHeaders(headers: Map<String, String>) {
        if (headers.isEmpty()) return
        this.headers { headers.forEach { (k, v) -> append(k, v) } }
    }

    /**
     * Lists tools exposed by [serverId], using the cache when fresh. Returns an empty list
     * on connection/protocol failure (a generation is allowed to proceed without MCP tools
     * rather than aborting the whole chat turn).
     *
     * Side-effects on status: a successful non-empty response marks the server READY; an empty
     * response or any error marks it DISCONNECTED and drops the poisoned connection. This keeps
     * the UI's three-state icon honest with what the last call actually saw.
     */
    suspend fun listTools(config: McpServerConfig): List<Tool> {
        suspend fun once(): List<Tool> {
            val server = getOrConnect(config)
            val cached = server.toolsCache
            val now = System.currentTimeMillis()
            if (cached != null && now - cached.fetchedAt < TOOLS_CACHE_TTL_MS) {
                // Cached: reflect its size into status without a network round-trip.
                setStatus(config.id, if (cached.tools.isNotEmpty()) McpStatus.READY else McpStatus.DISCONNECTED)
                return cached.tools
            }
            // Timeout is delegated to the SDK's own RequestOptions instead of an extra
            // withTimeout() wrapper — Protocol.request() already applies this timeout
            // internally (see kotlin-sdk-core Protocol.kt), so wrapping it again here
            // just added a second, redundant layer of coroutine cancellation around the
            // SDK's own suspend call.
            val tools = server.client.listTools(
                options = RequestOptions(timeout = REQUEST_TIMEOUT_MS.milliseconds)
            ).tools
            server.toolsCache = CachedTools(tools, now)
            // Per the user's decision, "connected but zero tools" is merged with "couldn't connect"
            // into the single error icon — both mean the server isn't actually useful right now.
            setStatus(config.id, if (tools.isNotEmpty()) McpStatus.READY else McpStatus.DISCONNECTED)
            return tools
        }
        return try {
            once()
        } catch (e: TimeoutCancellationException) {
            DebugLog.w(TAG, "listTools timed out for '${config.name}', retrying once after reconnect", e)
            invalidate(config.id)
            try { once() } catch (e2: CancellationException) {
                // The retry's own request was itself cut off (e.g. by a pause) mid-flight — the
                // freshly-(re)established connection is now in the same half-torn-down state the
                // outer cancellation branch below guards against. Drop it too so nothing reuses it.
                invalidate(config.id)
                throw e2
            } catch (e2: Exception) {
                DebugLog.e(TAG, "listTools retry failed for '${config.name}'", e2)
                invalidate(config.id)
                setStatus(config.id, McpStatus.DISCONNECTED)
                emptyList()
            }
        } catch (e: CancellationException) {
            // A pause/stop cuts this request off mid-flight. Unlike every other failure branch
            // here, this one used to just rethrow without calling invalidate() — leaving the
            // half-torn-down SSE/HTTP connection sitting in the pool as if it were still healthy.
            // The next call (this server or otherwise) could then reuse that connection object
            // while its underlying transport is in an inconsistent state. Drop it like every
            // other failure path does.
            invalidate(config.id)
            throw e
        } catch (e: java.io.IOException) {
            DebugLog.w(TAG, "listTools IO failed for '${config.name}', retrying once after reconnect", e)
            invalidate(config.id)
            try { once() } catch (e2: CancellationException) {
                invalidate(config.id)
                throw e2
            } catch (e2: Exception) {
                DebugLog.e(TAG, "listTools retry failed for '${config.name}'", e2)
                invalidate(config.id)
                setStatus(config.id, McpStatus.DISCONNECTED)
                emptyList()
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "listTools failed for '${config.name}'", e)
            // Drop a poisoned connection so the next call retries from scratch.
            invalidate(config.id)
            setStatus(config.id, McpStatus.DISCONNECTED)
            emptyList()
        }
    }

    /**
     * Calls a tool on [config]'s server. [argumentsJson] is the LLM-emitted arguments string
     * (already JSON-validated by the tool-call path). Returns the textual content blocks
     * joined by newlines; non-text content (image/audio) is skipped in this first version.
     */
    suspend fun callTool(config: McpServerConfig, name: String, argumentsJson: String): String {
        val args = parseArguments(argumentsJson)
        suspend fun once(): CallToolResult {
            val server = getOrConnect(config)
            // See the comment in listTools()'s once() — timeout delegated to the SDK's
            // own RequestOptions rather than an extra withTimeout() wrapper.
            return server.client.callTool(
                name = name,
                arguments = args,
                options = RequestOptions(timeout = REQUEST_TIMEOUT_MS.milliseconds)
            )
        }
        val result: CallToolResult = try {
            once()
        } catch (e: TimeoutCancellationException) {
            DebugLog.w(TAG, "callTool timed out for '${config.name}' / '$name', retrying once after reconnect", e)
            invalidate(config.id)
            try { once() } catch (e2: CancellationException) {
                invalidate(config.id)
                throw e2
            } catch (e2: Exception) {
                DebugLog.e(TAG, "callTool retry failed for '${config.name}' / '$name'", e2)
                invalidate(config.id)
                setStatus(config.id, McpStatus.DISCONNECTED)
                throw e2
            }
        } catch (e: CancellationException) {
            // A pause/stop cuts this request off mid-flight. Unlike every other failure branch
            // here, this one used to just rethrow without calling invalidate() — leaving the
            // half-torn-down SSE/HTTP connection sitting in the pool as if it were still healthy.
            // The next call (this server or otherwise) could then reuse that connection object
            // while its underlying transport is in an inconsistent state. Drop it like every
            // other failure path does.
            invalidate(config.id)
            throw e
        } catch (e: java.io.IOException) {
            DebugLog.w(TAG, "callTool IO failed for '${config.name}' / '$name', retrying once after reconnect", e)
            invalidate(config.id)
            try { once() } catch (e2: CancellationException) {
                invalidate(config.id)
                throw e2
            } catch (e2: Exception) {
                DebugLog.e(TAG, "callTool retry failed for '${config.name}' / '$name'", e2)
                invalidate(config.id)
                setStatus(config.id, McpStatus.DISCONNECTED)
                throw e2
            }
        } catch (e: Exception) {
            // A transport/protocol failure on a tool call means the connection is poisoned; drop it
            // and mark DISCONNECTED so the next call rebuilds and the UI reflects the outage.
            DebugLog.e(TAG, "callTool transport failed for '${config.name}' / '$name'", e)
            invalidate(config.id)
            setStatus(config.id, McpStatus.DISCONNECTED)
            throw e
        }
        val text = result.content
            .filterIsInstance<TextContent>()
            .joinToString("\n") { it.text }
        // isError=true means the tool itself reported an error (not a transport error);
        // surface it to the model so it can self-correct.
        return if (result.isError == true && text.isNotBlank()) "Error: $text" else text
    }

    /** Parses the LLM-emitted arguments JSON string into the Map<String, Any?> the SDK expects. */
    private fun parseArguments(argumentsJson: String): Map<String, Any?> {
        val s = argumentsJson.ifBlank { "{}" }
        val obj = json.parseToJsonElement(s).jsonObject
        // Map JSON primitives to their Kotlin equivalents; nested objects become JsonObject
        // (the SDK's toJson() handles them), which is good enough for tool arguments.
        return obj.entries.associate { (k, v) ->
            k to when (v) {
                is JsonPrimitive -> when {
                    v.isString -> v.content
                    v.content == "true" -> true
                    v.content == "false" -> false
                    v.content.toIntOrNull() != null -> v.content.toInt()
                    v.content.toLongOrNull() != null -> v.content.toLong()
                    v.content.toDoubleOrNull() != null -> v.content.toDouble()
                    else -> v.content
                }
                else -> v // object/array preserved as JsonElement for the SDK
            }
        }
    }

    /**
     * Drops the cached connection for [serverId]. Called when a server's config changes
     * (so the next call re-establishes with new URL/headers) or after a protocol error.
     */
    fun invalidate(serverId: String) {
        connections.remove(serverId)?.let { server ->
            ioScope.launchSafe {
                runCatching { server.client.close() }
            }
        }
        // A dropped connection is no longer READY — the UI should stop showing the ok icon.
        // If a monitoring loop is running it will probe again and re-promote on success.
        setStatus(serverId, McpStatus.DISCONNECTED)
    }

    /**
     * Drops any cached connection for [config.id] and immediately probes it once, updating [statuses]
     * as it goes (CONNECTING → READY / DISCONNECTED). Use this when the user toggles a server back
     * on or edits its config — without it the UI would wait up to HEARTBEAT_INTERVAL_MS for the
     * next heartbeat tick before reflecting the change. Fire-and-forget (runs on ioScope).
     */
    fun refreshStatus(config: McpServerConfig) {
        invalidate(config.id)
        ioScope.launch {
            setStatus(config.id, McpStatus.CONNECTING)
            val status = probe(config)
            setStatus(config.id, status)
        }
    }

    /**
     * Probes every server in [configs] in parallel, updating statuses to CONNECTING and then
     * READY / DISCONNECTED as each probe completes. Used when the app returns to foreground
     * or the user hits the refresh button in the MCP settings page.
     */
    fun refreshAll(configs: List<McpServerConfig>) {
        if (configs.isEmpty()) return
        ioScope.launch {
            kotlinx.coroutines.coroutineScope {
                configs.forEach { config ->
                    launch {
                        setStatus(config.id, McpStatus.CONNECTING)
                        val status = probe(config)
                        setStatus(config.id, status)
                    }
                }
            }
        }
    }

    /** Drops any connection whose config no longer appears in [activeIds]. */
    fun retainOnly(activeIds: Set<String>) {
        connections.keys.toList()
            .filter { it !in activeIds }
            .forEach { invalidate(it) }
        // Servers no longer configured at all leave the status map entirely.
        _statuses.value.keys.toList()
            .filter { it !in activeIds }
            .forEach { clearStatus(it) }
    }

    /** Closes every connection. Called from ChatViewModel.onCleared. */
    fun closeAll() {
        connections.values.toList().forEach { server ->
            runCatching { ioScope.launchSafe { server.client.close() } }
        }
        connections.clear()
        _statuses.value = emptyMap()
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Probes one server's health WITHOUT affecting the generation path: connects (if needed) and
     * runs a fresh listTools (cache-busting), then returns the resulting status. Used by the
     * heartbeat guardian so the UI's three-state icon reflects reality even between generations.
     */
    private suspend fun probe(config: McpServerConfig): McpStatus {
        // Reuse a live connection if we have one; otherwise do a SINGLE fast connect attempt.
        // We deliberately do NOT route through getOrConnect() here — that retries 3× with backoff
        // and a 15s connect timeout, so a dead server kept the spinner spinning ~50s. The heartbeat
        // runs every HEARTBEAT_INTERVAL_MS, so a failed probe simply retries next tick; one fast
        // attempt per tick is the right trade-off for status display (speed) vs generation (which
        // keeps the slow retrying path).
        var server: ConnectedServer? = connections[config.id]
        if (server == null) {
            server = try {
                connectFreshBounded(config, PROBE_CONNECT_TIMEOUT_MS)?.also { connections[config.id] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w(TAG, "heartbeat connect failed for '${config.name}': ${e.message}")
                return McpStatus.DISCONNECTED
            }
        }
        if (server == null) return McpStatus.DISCONNECTED
        val conn = server
        return try {
            val tools = withTimeout(PROBE_REQUEST_TIMEOUT_MS) { conn.client.listTools().tools }
            conn.toolsCache = CachedTools(tools, System.currentTimeMillis())
            if (tools.isNotEmpty()) McpStatus.READY else McpStatus.DISCONNECTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w(TAG, "heartbeat probe failed for '${config.name}': ${e.message}")
            invalidate(config.id)
            McpStatus.DISCONNECTED
        }
    }

    /**
     * A single connect attempt bounded by [timeoutMs] — the fast path used by [probe]. Returns the
     * connected server, or null if a concurrent caller already established the connection (caller
     * re-reads the map). Throws on connect failure. Mirrors [connectFresh] but with a caller-chosen
     * timeout so probes aren't forced to use the generation-time [CONNECT_TIMEOUT_MS].
     */
    private suspend fun connectFreshBounded(config: McpServerConfig, timeoutMs: Long): ConnectedServer? {
        val lock = connectLocks.computeIfAbsent(config.id) { Mutex() }
        return lock.withLock {
            connections[config.id]?.let { return@withLock null } // someone else connected
            val httpClient = HttpClient(OkHttp) { install(SSE) }
            val customHeaders = parseHeaders(config)
            val normalizedUrl = normalizeUrlHost(config.url)
            val transport = when (config.transport) {
                McpServerConfig.TRANSPORT_STREAMABLE -> StreamableHttpClientTransport(
                    client = httpClient, url = normalizedUrl,
                    requestBuilder = { applyCustomHeaders(customHeaders) },
                )
                McpServerConfig.TRANSPORT_SSE -> SseClientTransport(
                    client = httpClient, urlString = normalizedUrl,
                    requestBuilder = { applyCustomHeaders(customHeaders) },
                )
                else -> throw IllegalArgumentException("Unknown MCP transport: ${config.transport}")
            }
            val client = Client(clientInfo = Implementation(name = CLIENT_NAME, version = CLIENT_VERSION))
            withTimeout(timeoutMs) { client.connect(transport) }
            DebugLog.w(TAG, "Probed connect to MCP server '${config.name}'")
            ConnectedServer(client = client, config = config)
        }
    }

    /**
     * Starts (or replaces) the background heartbeat guardian. Every [HEARTBEAT_INTERVAL_MS] it
     * probes every enabled server in [configs], keeping [statuses] fresh and silently reconnecting
     * after a transient network blip. Disabled servers are probed too but their result is only
     * surfaced when they're enabled — cheaper to just skip them. Idempotent: calling again
     * cancels the previous loop. [closeAll] also cancels it.
     */
    fun startMonitoring(configs: kotlinx.coroutines.flow.Flow<List<McpServerConfig>>) {
        heartbeatJob?.cancel()
        heartbeatJob = ioScope.launch {
            // A single collector keeps `latestEnabled` fresh; the probe loop reads it each tick.
            // We do NOT block on configs.first() before the loop — that could stall if the Flow's
            // first emission is delayed. Instead the loop starts immediately and reads the latest
            // snapshot each tick (empty on the very first pass is fine; the collector catches up
            // within a frame or two).
            var latestEnabled: List<McpServerConfig> = emptyList()
            val collector = launch { configs.collect { latestEnabled = it.filter { c -> c.enabled } } }
            try {
                // Give the collector one frame to deliver its first value so the opening tick
                // (right after the user opens the page) actually has the server list.
                delay(300)
                while (isActive) {
                    val snapshot = latestEnabled
                    if (snapshot.isNotEmpty()) {
                        // Probe every enabled server in PARALLEL so the spinner duration is the
                        // slowest single probe, not the sum of all probes.
                        kotlinx.coroutines.coroutineScope {
                            snapshot.forEach { config ->
                                launch {
                                    setStatus(config.id, McpStatus.CONNECTING)
                                    val status = probe(config)
                                    setStatus(config.id, status)
                                }
                            }
                        }
                    }
                    if (!isActive) break
                    delay(HEARTBEAT_INTERVAL_MS)
                }
            } finally {
                collector.cancel()
            }
        }
    }

    private fun CoroutineScope.launchSafe(block: suspend () -> Unit): Job =
        this.launch {
            try { block() } catch (e: Exception) {
                DebugLog.w(TAG, "Background MCP task failed: ${e.message}")
            }
        }
}
