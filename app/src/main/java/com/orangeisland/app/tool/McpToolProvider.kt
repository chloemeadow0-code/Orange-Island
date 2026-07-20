package com.orangeisland.app.tool

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.McpServerConfig
import com.orangeisland.app.mcp.McpClientPool
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Bridges remote MCP (Model Context Protocol) servers into the LLM tool-calling pipeline.
 *
 * Each configured MCP server's tools are exposed as function-calling tools with a namespaced
 * name `mcp__{serverName}__{toolName}` so they cannot collide with built-in tools (web_search,
 * memory_*, shell_*, …) or with tools from another MCP server that happens to share a name.
 *
 * The provider is the single integration point between [McpClientPool] (transport + SDK) and
 * the [ToolProvider] abstraction used by [com.orangeisland.app.viewmodel.GenerationManager].
 *
 * Definitions are built lazily per-generation: the tool list is fetched once per
 * [McpClientPool.TOOLS_CACHE_TTL_MS] window, so the cost of a typical chat turn is one cached
 * listTools() call per active server.
 */
class McpToolProvider(
    private val pool: McpClientPool,
) : ToolProvider {

    companion object {
        private const val TAG = "McpToolProvider"
        const val PREFIX = "mcp__"
        private const val SEPARATOR = "__"
    }

    /** Resolves which MCP servers are active for this generation, per the inheritance rules
     *  documented on [GenerationContext.mcpServerIds]. */
    private fun activeServers(ctx: GenerationContext): List<McpServerConfig> {
        val ids = ctx.mcpServerIds
        return when {
            // Explicit per-conversation selection (may include servers disabled globally).
            ids != null -> ctx.mcpServers.filter { it.id in ids }
            // Inherit: all globally-enabled servers.
            else -> ctx.mcpServers.filter { it.enabled }
        }
    }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        val servers = activeServers(ctx)
        if (servers.isEmpty()) return emptyList()
        val all = mutableListOf<ToolDefinition>()
        // ToolProvider.definitions() is synchronous (built-in tools are pure functions of ctx),
        // but MCP requires a network round-trip to listTools(). runBlocking bridges the gap;
        // callers already run on Dispatchers.IO (buildApiPath is suspend), and listTools()
        // is cached so the cost is one call per server per minute.
        kotlinx.coroutines.runBlocking {
            for (server in servers) {
                val tools = try {
                    pool.listTools(server)
                } catch (e: Exception) {
                    DebugLog.w(TAG, "Failed to list tools for '${server.name}': ${e.message}")
                    emptyList()
                }
                for (tool in tools) {
                    all += tool.toToolDefinition(serverName = server.name)
                }
            }
        }
        return all
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        val parsed = parsePrefixedName(name) ?: return "Unknown MCP tool: $name"
        val (serverName, toolName) = parsed
        val server = activeServers(ctx).firstOrNull { sanitize(it.name) == serverName }
            ?: return "MCP server not active: $serverName"
        return try {
            pool.callTool(server, toolName, arguments)
        } catch (e: Exception) {
            DebugLog.e(TAG, "callTool failed for '$name'", e)
            "MCP tool '$name' failed: ${e.localizedMessage ?: e::class.simpleName}"
        }
    }

    override fun handles(name: String): Boolean = name.startsWith(PREFIX)

    // ── Name (de)mangling ──────────────────────────────────────

    /** Builds the namespaced tool name seen by the LLM. */
    private fun prefixedName(serverName: String, toolName: String): String =
        "$PREFIX${sanitize(serverName)}$SEPARATOR$toolName"

    /** Inverts [prefixedName]. Returns null if [name] isn't a valid MCP-prefixed tool. */
    private fun parsePrefixedName(name: String): Pair<String, String>? {
        if (!name.startsWith(PREFIX)) return null
        val rest = name.removePrefix(PREFIX)
        val sepIdx = rest.indexOf(SEPARATOR)
        if (sepIdx <= 0) return null
        val server = rest.substring(0, sepIdx)
        val tool = rest.substring(sepIdx + SEPARATOR.length)
        if (server.isEmpty() || tool.isEmpty()) return null
        return server to tool
    }

    /**
     * Sanitizes a server name for use in the tool-name segment. MCP server names are
     * user-defined free text; we collapse anything that isn't `[A-Za-z0-9_]` to `_` so the
     * `mcp__server__tool` boundary stays unambiguous and LLM-tokenizer-friendly.
     */
    private fun sanitize(name: String): String =
        name.map { if (it.isLetterOrDigit() || it == '_') it else '_' }.joinToString("")

    // ── MCP → Orange Island tool-schema translation ─────────────

    /**
     * Converts an MCP [Tool] to the app's [ToolDefinition].
     *
     * MCP inputSchema is a free-form JSON-Schema object; the app's [ToolProperty] only models
     * `{type, description, items}` (a deliberately small subset that covers the vast majority
     * of real-world tool schemas). Unknown property types fall back to "string" with the
     * original description preserved, and `$defs`/`allOf`/`oneOf` are dropped — the model
     * still receives name + description, which is usually sufficient to call the tool.
     */
    private fun Tool.toToolDefinition(serverName: String): ToolDefinition {
        val props = inputSchema.properties ?: emptyMap()
        val required = inputSchema.required ?: emptyList()
        val properties = props.mapValues { (propName, schema) ->
            val obj = schema as? JsonObject
            val type = (obj?.get("type") as? JsonPrimitive)?.content ?: "string"
            val desc = (obj?.get("description") as? JsonPrimitive)?.content
                ?: "Parameter $propName"
            val items = (obj?.get("items") as? JsonObject)?.let { itemsSchema ->
                val itemType = (itemsSchema["type"] as? JsonPrimitive)?.content ?: "string"
                ToolProperty(type = itemType, description = desc)
            }
            ToolProperty(type = type, description = desc, items = items)
        }
        return ToolDefinition(
            function = ToolFunction(
                name = prefixedName(serverName, name),
                description = buildString {
                    append(description?.takeIf { it.isNotBlank() } ?: name)
                    append("  [MCP server: $serverName]")
                },
                parameters = ToolParameters(
                    properties = properties,
                    required = required
                )
            )
        )
    }
}
