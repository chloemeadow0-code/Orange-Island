package com.orangeisland.app.plugin

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.InstalledPlugin
import com.orangeisland.app.data.PluginToolParam
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Maps the API-safe mangled name the LLM sees back to the original (un-sanitized) plugin
     * tool name, so [execute] can call the real tool after sanitization. Populated on every
     * [definitions] pass; overwrites are idempotent and the LLM only ever emits names from the
     * most recent pass, so stale entries are harmless.
     */
    private val originalToolNames = ConcurrentHashMap<String, String>()

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
        val used = mutableSetOf<String>()
        return plugins.flatMap { plugin ->
            plugin.manifest.tools.map { tool ->
                val apiName = allocateApiName(plugin.id, tool.name, used)
                originalToolNames[apiName] = tool.name
                ToolDefinition(
                    function = ToolFunction(
                        name = apiName,
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
        val (pluginId, _) = parsed
        val plugin = activePlugins(ctx).firstOrNull { sanitizeId(it.id) == pluginId }
            ?: return "Plugin not active: $pluginId"
        // Recover the original tool name in case [name] was sanitized for API compliance.
        val originalName = originalToolNames[name] ?: return "Unknown plugin tool: $name"
        return sandbox.callTool(plugin, originalName, arguments)
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

    /**
     * Builds a unique, API-safe function name for one plugin tool. Both segments are sanitized
     * (see [sanitizeToolName]); on collision a `_2`, `_3`, … suffix is appended.
     */
    private fun allocateApiName(pluginId: String, toolName: String, used: MutableSet<String>): String {
        val sanitizedPluginId = sanitizeId(pluginId)
        val sanitizedToolName = sanitizeToolName(toolName)
        var name = PREFIX + sanitizedPluginId + SEPARATOR + sanitizedToolName
        if (used.add(name)) return name
        var n = 2
        while (n < 100) {
            val candidate = PREFIX + sanitizedPluginId + SEPARATOR + sanitizedToolName + "_$n"
            if (used.add(candidate)) return candidate
            n++
        }
        return name // 99-way collision is absurd; give up dedupping.
    }

    /** Inverts a plugin-prefixed name back to its two sanitized segments. Returns null if [name]
     *  isn't a valid plugin-prefixed tool. */
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
     * separator stays unambiguous.
     */
    private fun sanitizeId(id: String): String = id.replace('.', '_').replace('-', '_')

    /**
     * Collapses every run of non-`[A-Za-z0-9]` characters in [s] to a single `_` and trims
     * leading/trailing `_`. Plugin tool names come from third-party manifest.json and may contain
     * spaces, Unicode, or symbols — without this the LLM provider rejects the whole request.
     */
    private fun sanitizeToolName(s: String): String {
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
        return out.toString().trimEnd('_').ifEmpty { "tool" }
    }

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
