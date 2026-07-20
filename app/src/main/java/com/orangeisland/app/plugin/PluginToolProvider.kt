package com.orangeisland.app.plugin

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.InstalledPlugin
import com.orangeisland.app.data.PluginToolParam
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext

/**
 * Bridges user-installed JS plugins into the LLM tool-calling pipeline.
 *
 * Each plugin tool is exposed with a namespaced name `plugin__<sanitizedId>__<toolName>` so it
 * cannot collide with built-in tools (web_search, memory_*, …), MCP tools (`mcp__…`), or another
 * plugin that happens to share a tool name.
 *
 * The active plugin set is resolved per-generation from [GenerationContext]: the loader scans
 * disk once at request-build time, and the list is filtered by [GenerationContext.pluginIds]
 * (null = use all globally enabled plugins; non-empty = exactly those plugin ids).
 *
 * Tool *definitions* are pure functions of the manifest (no JS execution needed); tool
 * *execution* delegates to [PluginSandbox] which actually runs the JS function.
 */
class PluginToolProvider(
    private val loader: PluginLoader,
    private val sandbox: PluginSandbox,
    private val settings: com.orangeisland.app.data.repository.SettingsRepository,
) : ToolProvider {

    companion object {
        const val PREFIX = "plugin__"
        private const val SEPARATOR = "__"
        private const val TAG = "PluginToolProvider"
    }

    /** Latest snapshot of installed plugins (re-scanned on demand at request-build time). */
    private var cachedPlugins: List<InstalledPlugin>? = null

    /** Resolves the active plugin set for this generation. See [GenerationContext.pluginIds]. */
    private fun activePlugins(ctx: GenerationContext): List<InstalledPlugin> {
        // ToolProvider.definitions() is synchronous, but plugin scanning reads disk. runBlocking
        // is acceptable here because callers already run on Dispatchers.IO (buildApiPath is
        // suspend), and the scan only reads small manifest.json files.
        val all = cachedPlugins ?: kotlinx.coroutines.runBlocking {
            loader.scan(settings.enabledPluginIds.value)
        }.also { cachedPlugins = it }
        val ids = ctx.pluginIds
        return when {
            // Explicit per-conversation selection (may include plugins disabled globally).
            ids != null -> all.filter { it.id in ids }
            // Inherit: all globally enabled plugins.
            else -> all.filter { it.enabled }
        }
    }

    /**
     * Reloads the on-disk plugin list at request-build time (cheap; only reads manifest.json
     * files). [GenerationManager] calls this once per generation so newly installed/uninstalled
     * plugins are picked up without restarting the app.
     */
    suspend fun refreshPluginList() {
        cachedPlugins = loader.scan(settings.enabledPluginIds.value)
    }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        val plugins = activePlugins(ctx)
        if (plugins.isEmpty()) return emptyList()
        return plugins.flatMap { plugin ->
            plugin.manifest.tools.map { tool ->
                ToolDefinition(
                    function = ToolFunction(
                        name = prefixedName(plugin.id, tool.name),
                        description = buildString {
                            append(tool.description.ifBlank { tool.name })
                            append("  [Plugin: ${plugin.manifest.name}]")
                        },
                        parameters = tool.parameters.toToolParameters(),
                    )
                )
            }
        }
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        val parsed = parsePrefixedName(name) ?: return "Unknown plugin tool: $name"
        val (pluginId, toolName) = parsed
        val plugin = activePlugins(ctx).firstOrNull { sanitizeId(it.id) == pluginId }
            ?: return "Plugin not active: $pluginId"
        return sandbox.callTool(plugin, toolName, arguments)
    }

    override fun handles(name: String): Boolean = name.startsWith(PREFIX)

    /** Drops the cached plugin list so the next definitions()/execute() re-scans disk. */
    fun invalidateCache() { cachedPlugins = null }

    /** Notifies the sandbox that a plugin's main.js changed and its runtime must be rebuilt. */
    fun reloadPlugin(pluginId: String) {
        sandbox.reload(pluginId)
        invalidateCache()
    }

    /** Notifies the sandbox that a plugin was uninstalled and its runtime should be torn down. */
    fun forgetPlugin(pluginId: String) {
        sandbox.invalidate(pluginId)
        invalidateCache()
    }

    // ── Name (de)mangling ──────────────────────────────────────

    /** Builds the namespaced tool name seen by the LLM. */
    private fun prefixedName(pluginId: String, toolName: String): String =
        PREFIX + sanitizeId(pluginId) + SEPARATOR + toolName

    /** Inverts [prefixedName]. Returns null if [name] isn't a valid plugin-prefixed tool. */
    private fun parsePrefixedName(name: String): Pair<String, String>? {
        if (!name.startsWith(PREFIX)) return null
        val rest = name.removePrefix(PREFIX)
        val sepIdx = rest.indexOf(SEPARATOR)
        if (sepIdx <= 0) return null
        val pluginId = rest.substring(0, sepIdx)
        val toolName = rest.substring(sepIdx + SEPARATOR.length)
        if (pluginId.isEmpty() || toolName.isEmpty()) return null
        return pluginId to toolName
    }

    /**
     * Sanitizes a plugin id for use in the tool-name segment. Plugin ids are validated at
     * install time (`[a-z0-9_.-]`), but `.`, `-` are replaced with `_` here so the `__`
     * separator stays unambiguous — a tool name like `plugin__com_example_my_plugin__tool`
     * parses cleanly because the id segment never contains `__` itself.
     */
    private fun sanitizeId(id: String): String = id.replace('.', '_').replace('-', '_')

    // ── Manifest → Orange Island schema ─────────────────────────────

    /** Maps plugin-tool parameters onto Orange Island's [ToolParameters] (a small JSON-Schema subset). */
    private fun List<PluginToolParam>.toToolParameters(): ToolParameters {
        val required = filter { it.required }.map { it.name }
        val props = associate { p ->
            p.name to ToolProperty(
                type = normalizeType(p.type),
                description = p.description.ifBlank { "Parameter ${p.name}" },
                items = null,
            )
        }
        return ToolParameters(properties = props, required = required)
    }

    private fun normalizeType(t: String): String = when (t.lowercase()) {
        "int", "integer" -> "integer"
        "number", "float", "double" -> "number"
        "bool", "boolean" -> "boolean"
        "array" -> "array"
        else -> "string"
    }
}
