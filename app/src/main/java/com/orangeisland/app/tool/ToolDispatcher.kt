package com.orangeisland.app.tool

import android.app.Application
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.mcp.McpClientPool
import com.orangeisland.app.plugin.PluginToolProvider
import com.orangeisland.app.sandbox.SandboxManagerFactory
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.PermissionController

/**
 * Central, app-wide dispatcher for every [ToolProvider] the app ships.
 *
 * Historically the 16 tool providers lived as private fields inside [com.orangeisland.app.viewmodel.
 * GenerationManager], which made them unreachable from anywhere except the LLM streaming loop. The
 * Workflow engine needs to invoke the exact same tools (memory, web search, shell, MCP, plugins,
 * device-access, automation, …) without going through an LLM, so the provider graph has been lifted
 * here. [com.orangeisland.app.viewmodel.GenerationManager] now delegates to this class; future
 * non-LLM callers (workflow execution, background workers, AI-driven workflow tools) do the same.
 *
 * Nothing about the provider wiring changed in the move — the same providers are constructed with
 * the same dependencies, in the same order, and the shell-confirmation forwarding is preserved.
 * The only additions are the public entry points ([execute], [allDefinitions]) and the optional
 * [onConfirmDestructive] hook used by the Workflow guard layer.
 *
 * Independent implementation. The provider/tool-calling plumbing is Orange Island's own; this
 * class simply re-exposes it outside the generation pipeline.
 */
class ToolDispatcher(
    private val app: Application,
    private val conversations: ConversationRepository,
    private val memoryManager: MemoryManager,
    private val llmProviders: Map<String, LlmProvider>,
    private val appContext: android.content.Context,
    private val sandboxFactory: SandboxManagerFactory? = null,
    /** Optional MCP client pool. When null, MCP tools are disabled (e.g. during title
     *  generation where external tools should not run). */
    private val mcpPool: McpClientPool? = null,
    /** Optional JS-plugin tool provider. When null, plugin tools are disabled. */
    private val pluginToolProvider: PluginToolProvider? = null,
    /** Permission state for the Device Access tools. Null when device tools should not run
     *  (e.g. title generation). */
    private val permissionController: PermissionController? = null,
    /** Optional Workflow AI tool provider (workflow_list/get/run). Null when workflow tools are
     *  disabled (e.g. title generation, or before the workflow feature is wired up). */
    private val workflowToolProvider: ToolProvider? = null
) {
    companion object {
        /** Shell-provider tool names that are pure file I/O (split out from command execution). */
        val FILE_TOOL_NAMES = setOf("file_read", "file_write", "file_edit", "file_glob", "file_grep")
    }

    // ── Provider construction (lifted verbatim from GenerationManager) ───────

    private val memoryToolProvider = MemoryToolProvider(memoryManager)
    private val webSearchToolProvider = WebSearchToolProvider()
    private val ragToolProvider = RagToolProvider(conversations)
    private val imageGenToolProvider = ImageGenToolProvider(app)
    private val deviceInfoToolProvider = com.orangeisland.app.tool.device.DeviceInfoToolProvider(app)
    private val locationToolProvider = permissionController?.let {
        com.orangeisland.app.tool.device.LocationToolProvider(app, it)
    }
    private val calendarToolProvider = permissionController?.let {
        com.orangeisland.app.tool.device.CalendarToolProvider(app, it)
    }
    private val notificationToolProvider = permissionController?.let {
        com.orangeisland.app.tool.device.NotificationToolProvider(app, it)
    }
    private val usageStatsToolProvider = permissionController?.let {
        com.orangeisland.app.tool.device.UsageStatsToolProvider(app, it)
    }
    private val navigationToolProvider = com.orangeisland.app.tool.NavigationToolProvider(app)
    private val appLockToolProvider = com.orangeisland.app.tool.AppLockToolProvider(app)
    private val toastToolProvider = com.orangeisland.app.tool.ToastToolProvider(app)
    private val automationToolProvider =
        com.orangeisland.app.tool.automation.AutomationToolProvider(llmProviders)
    val shellToolProvider = ShellToolProvider(sandboxFactory).also { stp ->
        // Forward to the ViewModel-provided gate at call time (read the var lazily).
        stp.confirm = { server, summary -> onConfirmShellCommand?.invoke(server, summary) ?: true }
    }
    private val mcpToolProvider = mcpPool?.let { com.orangeisland.app.tool.McpToolProvider(it) }

    /** Every active provider, in dispatch order. [handles] is queried in this order, so earlier
     *  providers win on name collisions. Names are namespaced (plugin__/mcp__) to avoid this in
     *  practice, but the order is still deterministic. */
    val all: List<ToolProvider> = buildList {
        add(memoryToolProvider); add(webSearchToolProvider); add(ragToolProvider)
        add(imageGenToolProvider); add(deviceInfoToolProvider); add(shellToolProvider)
        locationToolProvider?.let { add(it) }
        calendarToolProvider?.let { add(it) }
        notificationToolProvider?.let { add(it) }
        usageStatsToolProvider?.let { add(it) }
        add(navigationToolProvider)
        add(appLockToolProvider)
        add(toastToolProvider)
        add(automationToolProvider)
        mcpToolProvider?.let { add(it) }
        pluginToolProvider?.let { add(it) }
        workflowToolProvider?.let { add(it) }
    }

    // ── Confirmation hooks ──────────────────────────────────────────────────

    /** User-confirmation gate for remote shell mutations. Set by the ViewModel.
     *  Returns true to proceed, false to deny. */
    var onConfirmShellCommand: (suspend (server: String, summary: String) -> Boolean)? = null

    /** User-confirmation gate for any destructive tool invoked outside the LLM loop (e.g. by the
     *  Workflow engine). Returns true to proceed, false to deny. Null = no gate installed, in
     *  which case callers decide their own default (the Workflow guard treats null as "deny in
     *  background, allow in foreground"). */
    var onConfirmDestructive: (suspend (toolName: String, args: String) -> Boolean)? = null

    // ── Public entry points ─────────────────────────────────────────────────

    /** Routes a tool call by name. Returns the provider's result string (usually JSON), or an
     *  "Unknown tool" / "Error executing" message string on failure — never throws, matching the
     *  original GenerationManager.executeTool contract so LLM-loop callers are unaffected. */
    suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        return try {
            for (provider in all) {
                if (provider.handles(name)) {
                    return provider.execute(name, arguments, ctx)
                }
            }
            "Unknown tool: $name"
        } catch (e: Exception) {
            "Error executing tool '$name': ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /** Aggregate of every provider's [ToolProvider.definitions], used by the workflow editor to
     *  populate the tool picker and by [com.orangeisland.app.workflow.WorkflowGuard] to classify
     *  tools. Equivalent to summing the per-category builders below. */
    fun allDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        all.flatMap { runCatching { it.definitions(ctx) }.getOrDefault(emptyList()) }

    /** Re-scans installed JS plugins so newly installed/uninstalled ones are picked up without an
     *  app restart. GenerationManager calls this once per generation; the workflow engine calls it
     *  before listing tools in the editor. */
    suspend fun refreshPlugins() {
        pluginToolProvider?.refreshPluginList()
    }

    // ── Category-scoped definition builders (preserved for GenerationManager) ─

    fun imageGenDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        imageGenToolProvider.definitions(ctx)

    fun memoryDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        memoryToolProvider.definitions(ctx)

    fun webSearchDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        webSearchToolProvider.definitions(ctx)

    fun ragDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        ragToolProvider.definitions(ctx)

    /** Semantic message search over conversation history. Exposed so [com.orangeisland.app.viewmodel.
     *  GenerationManager] and the in-app search box share one embedding-search implementation. */
    suspend fun semanticSearch(
        query: String,
        limit: Int,
        ctx: GenerationContext
    ): List<Pair<com.orangeisland.app.data.local.MessageEntity, Float>> =
        ragToolProvider.semanticSearch(query, limit, ctx)

    /** Shell execution tools, excluding pure file I/O (those are surfaced separately). */
    fun shellDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        shellToolProvider.definitions(ctx).filter { it.function.name !in FILE_TOOL_NAMES }

    /** Pure file-I/O tools (read/write/edit/glob/grep) exposed by the shell provider. */
    fun fileDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        shellToolProvider.definitions(ctx).filter { it.function.name in FILE_TOOL_NAMES }

    fun mcpDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        mcpToolProvider?.definitions(ctx) ?: emptyList()

    fun pluginDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        pluginToolProvider?.definitions(ctx) ?: emptyList()

    fun navigationDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        navigationToolProvider.definitions(ctx)

    fun appLockDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        appLockToolProvider.definitions(ctx)

    fun toastDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        toastToolProvider.definitions(ctx)

    fun automationDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        automationToolProvider.definitions(ctx)

    /** Workflow tools (workflow_list/get/run/create/update/delete/set_enabled). Each is surfaced to
     *  the LLM so it can read, fire, and AI-author linear workflows. Empty when the workflow feature
     *  is not wired up (e.g. title generation). */
    fun workflowDefinitions(ctx: GenerationContext): List<ToolDefinition> =
        workflowToolProvider?.definitions(ctx) ?: emptyList()

    /** Device access tools (battery, location, calendar, notifications, usage stats). Each
     *  provider internally checks its own enable flag in [GenerationContext]. */
    fun deviceDefinitions(ctx: GenerationContext): List<ToolDefinition> = buildList {
        addAll(deviceInfoToolProvider.definitions(ctx))
        locationToolProvider?.let { addAll(it.definitions(ctx)) }
        calendarToolProvider?.let { addAll(it.definitions(ctx)) }
        notificationToolProvider?.let { addAll(it.definitions(ctx)) }
        usageStatsToolProvider?.let { addAll(it.definitions(ctx)) }
    }

    // ── Pass-through helpers ────────────────────────────────────────────────

    /** Drains image URLs queued by the most recent image-generation tool call. Called by the LLM
     *  loop right after a generate_image call so the URLs render inline. */
    fun drainGeneratedImages(): List<String> = imageGenToolProvider.drainImages()
}
