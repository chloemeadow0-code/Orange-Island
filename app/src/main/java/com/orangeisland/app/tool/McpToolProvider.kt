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
import java.util.concurrent.ConcurrentHashMap

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
 *
 * MCP tool/server names are free text. Before they reach the LLM they are run through
 * [allocateApiName], which sanitizes both segments, caps the composite length, and dedups
 * collisions — without this a single mis-named remote tool 400s the whole request with
 * "Invalid tool use format".
 */
class McpToolProvider(
    private val pool: McpClientPool,
) : ToolProvider {

    /**
     * Maps the API-safe mangled name the LLM sees back to the original (un-sanitized) MCP tool
     * name, so [execute] can call the real tool after sanitization. Populated on every
     * [definitions] pass; overwrites are idempotent and the LLM only ever emits names from the
     * most recent pass, so stale entries are harmless.
     */
    private val originalToolNames = ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "McpToolProvider"
        const val PREFIX = "mcp__"
        private const val SEPARATOR = "__"

        // OpenAI-, Anthropic- and Gemini-compatible providers all cap function names at 64 chars
        // and restrict them to [a-zA-Z0-9_-]. We stay inside that envelope so one mis-named MCP
        // tool can't 400 the whole request body.
        internal const val MAX_FUNCTION_NAME_LEN = 64
        private const val MAX_SERVER_SEGMENT = 30

        /**
         * Builds a unique, API-safe function name for one MCP tool. Pass a per-[definitions]-pass
         * [used] set so two tools that collapse to the same name (e.g. `foo.bar` and `foo_bar`)
         * still get distinct names.
         *
         * Both segments are sanitized (see [sanitizeName]); the server segment is capped at
         * [MAX_SERVER_SEGMENT] to leave room for the tool name; the whole string is capped at
         * [MAX_FUNCTION_NAME_LEN]; and on collision a `_2`, `_3`, … suffix is appended (the base
         * tool is shrunk so the total still fits).
         */
        internal fun allocateApiName(serverName: String, toolName: String, used: MutableSet<String>): String {
            val server = serverSegment(serverName)
            val baseTool = sanitizeName(toolName)
            val fixedOverhead = PREFIX.length + server.length + SEPARATOR.length
            var name = join(server, baseTool, fixedOverhead, "")
            if (used.add(name)) return name
            var n = 2
            while (n < 100) {
                val suffix = "_$n"
                name = join(server, baseTool, fixedOverhead, suffix)
                if (used.add(name)) return name
                n++
            }
            return name // 99-way collision from one server is absurd; give up dedupping.
        }

        /** Server segment as it appears inside the mangled name (sanitized + length-capped). */
        internal fun serverSegment(serverName: String): String =
            sanitizeName(serverName).take(MAX_SERVER_SEGMENT)

        /**
         * Collapses every run of non-`[A-Za-z0-9]` characters in [s] to a single `_` and trims
         * leading/trailing `_`. MCP server/tool names are free text and frequently contain `.`,
         * `/`, `:`, spaces or Unicode — without this, providers reject the request. Collapsing
         * (rather than 1:1 replacement) keeps the `__` separator unambiguous: neither segment
         * can ever contain `__` itself, so [parsePrefixedName]'s `indexOf` always lands on the
         * real separator.
         */
        internal fun sanitizeName(s: String): String {
            val out = StringBuilder(s.length)
            var prevUnderscore = true // start in "underscore just emitted" state → trims leading runs
            for (c in s) {
                if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') {
                    out.append(c)
                    prevUnderscore = false
                } else if (!prevUnderscore) {
                    out.append('_')
                    prevUnderscore = true
                }
            }
            return out.toString().trimEnd('_').ifEmpty { "x" }
        }

        private fun join(server: String, baseTool: String, fixedOverhead: Int, suffix: String): String {
            val budget = (MAX_FUNCTION_NAME_LEN - fixedOverhead - suffix.length).coerceAtLeast(1)
            return PREFIX + server + SEPARATOR + baseTool.take(budget) + suffix
        }
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
        // Names already issued in this pass — local so collisions are deduped per generation,
        // not across the whole process lifetime.
        val used = mutableSetOf<String>()
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
                    if (tool.name in server.disabledToolNames) continue
                    val apiName = allocateApiName(server.name, tool.name, used)
                    originalToolNames[apiName] = tool.name
                    all += tool.toToolDefinition(serverName = server.name, apiName = apiName)
                }
            }
        }
        return all
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        val parsed = parsePrefixedName(name) ?: return "Unknown MCP tool: $name"
        val (serverSegmentName, _) = parsed
        val server = activeServers(ctx).firstOrNull { serverSegment(it.name) == serverSegmentName }
            ?: return "MCP server not active: $serverSegmentName"
        // The name the LLM emitted is sanitized; recover the original so we call the real tool.
        val originalName = originalToolNames[name] ?: parsed.second
        return try {
            pool.callTool(server, originalName, arguments)
        } catch (e: Exception) {
            DebugLog.e(TAG, "callTool failed for '$name'", e)
            "MCP tool '$name' failed: ${e.localizedMessage ?: e::class.simpleName}"
        }
    }

    override fun handles(name: String): Boolean = name.startsWith(PREFIX)

    // ── Name (de)mangling ──────────────────────────────────────

    /** Inverts an `mcp__{server}__{tool}` name back to its two sanitized segments. Returns null
     *  if [name] isn't a valid MCP-prefixed tool. Because [sanitizeName] collapses `__` runs,
     *  the first `__` after the prefix is always the real separator. */
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

    // ── MCP → Orange Island tool-schema translation ─────────────

    /**
     * Converts an MCP [Tool] to the app's [ToolDefinition]. [apiName] is the already-sanitized
     * function name produced by [allocateApiName]; the original [Tool.name] is preserved only in
     * the description fallback and in [originalToolNames] for execution.
     *
     * MCP inputSchema is a free-form JSON-Schema object; the app's [ToolProperty] only models
     * `{type, description, items}` (a deliberately small subset that covers the vast majority
     * of real-world tool schemas). Unknown property types fall back to "string" with the
     * original description preserved, and `$defs`/`allOf`/`oneOf` are dropped — the model
     * still receives name + description, which is usually sufficient to call the tool.
     */
    private fun Tool.toToolDefinition(serverName: String, apiName: String): ToolDefinition {
        val props = inputSchema.properties ?: emptyMap()
        val required = inputSchema.required ?: emptyList()
        val properties = props.mapValues { (propName, schema) ->
            val obj = schema as? JsonObject
            val type = (obj?.get("type") as? JsonPrimitive)?.content ?: "string"
            val desc = (obj?.get("description") as? JsonPrimitive)?.content
                ?: "Parameter $propName"
            val items = (obj?.get("items") as? JsonObject)?.let { itemsSchema ->
                val itemType = (itemsSchema["type"] as? JsonPrimitive)?.content ?: "string"
                val itemDesc = (itemsSchema["description"] as? JsonPrimitive)?.content ?: desc
                ToolProperty(type = itemType, description = itemDesc)
            }
            ToolProperty(type = type, description = desc, items = items)
        }
        return ToolDefinition(
            function = ToolFunction(
                name = apiName,
                description = buildString {
                    append(description?.takeIf { it.isNotBlank() } ?: this@toToolDefinition.name)
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
