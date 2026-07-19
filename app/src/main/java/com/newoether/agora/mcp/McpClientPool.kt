package com.newoether.agora.mcp

import com.newoether.agora.data.McpServerConfig
import com.newoether.agora.util.DebugLog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.headers
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

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
        private const val CLIENT_NAME = "agora"
        private const val CLIENT_VERSION = "1.0"
    }

    private val connections = ConcurrentHashMap<String, ConnectedServer>()
    private val connectLocks = ConcurrentHashMap<String, Mutex>()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lazily connects to [config] (or returns the existing connection). Throws on real
     * failure so callers can surface a concrete error message.
     */
    private suspend fun getOrConnect(config: McpServerConfig): ConnectedServer {
        connections[config.id]?.let { return it }
        val lock = connectLocks.computeIfAbsent(config.id) { Mutex() }
        val connected = lock.withLock {
            // Double-check after acquiring the lock — another caller may have connected.
            connections[config.id]?.let { return@withLock it }
            val server = connectFresh(config)
            connections[config.id] = server
            server
        }
        return connected
    }

    private suspend fun connectFresh(config: McpServerConfig): ConnectedServer {
        // One HttpClient per connection — SSE state is connection-scoped. CIO is a pure-Kotlin
        // engine (no JNI) so it's safe on Android minSdk 24+.
        val httpClient = HttpClient {
            install(SSE)
        }
        val customHeaders = parseHeaders(config)
        val transport = when (config.transport) {
            McpServerConfig.TRANSPORT_STREAMABLE -> StreamableHttpClientTransport(
                client = httpClient,
                url = config.url,
                requestBuilder = { applyCustomHeaders(customHeaders) },
            )
            McpServerConfig.TRANSPORT_SSE -> SseClientTransport(
                client = httpClient,
                urlString = config.url,
                requestBuilder = { applyCustomHeaders(customHeaders) },
            )
            else -> throw IllegalArgumentException("Unknown MCP transport: ${config.transport}")
        }
        val client = Client(clientInfo = Implementation(name = CLIENT_NAME, version = CLIENT_VERSION))
        client.connect(transport)
        DebugLog.w(TAG, "Connected to MCP server '${config.name}' (${config.transport} @ ${config.url})")
        return ConnectedServer(client = client, config = config)
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
     */
    suspend fun listTools(config: McpServerConfig): List<Tool> {
        return try {
            val server = getOrConnect(config)
            val cached = server.toolsCache
            val now = System.currentTimeMillis()
            if (cached != null && now - cached.fetchedAt < TOOLS_CACHE_TTL_MS) {
                return cached.tools
            }
            val tools = server.client.listTools().tools
            server.toolsCache = CachedTools(tools, now)
            tools
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.e(TAG, "listTools failed for '${config.name}'", e)
            // Drop a poisoned connection so the next call retries from scratch.
            invalidate(config.id)
            emptyList()
        }
    }

    /**
     * Calls a tool on [config]'s server. [argumentsJson] is the LLM-emitted arguments string
     * (already JSON-validated by the tool-call path). Returns the textual content blocks
     * joined by newlines; non-text content (image/audio) is skipped in this first version.
     */
    suspend fun callTool(config: McpServerConfig, name: String, argumentsJson: String): String {
        val server = getOrConnect(config)
        val args = parseArguments(argumentsJson)
        val result: CallToolResult = server.client.callTool(
            name = name,
            arguments = args,
        )
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
    }

    /** Drops any connection whose config no longer appears in [activeIds]. */
    fun retainOnly(activeIds: Set<String>) {
        connections.keys.toList()
            .filter { it !in activeIds }
            .forEach { invalidate(it) }
    }

    /** Closes every connection. Called from ChatViewModel.onCleared. */
    fun closeAll() {
        connections.values.toList().forEach { server ->
            runCatching { ioScope.launchSafe { server.client.close() } }
        }
        connections.clear()
    }

    private fun CoroutineScope.launchSafe(block: suspend () -> Unit): Job =
        this.launch {
            try { block() } catch (e: Exception) {
                DebugLog.w(TAG, "Background MCP task failed: ${e.message}")
            }
        }
}
