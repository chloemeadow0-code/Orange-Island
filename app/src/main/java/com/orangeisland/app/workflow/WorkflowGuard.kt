package com.orangeisland.app.workflow

import com.orangeisland.app.model.ActionNode
import com.orangeisland.app.tool.ToolDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The four safety boundaries every ActionNode must clear before the engine dispatches its tool:
 *
 * 1. **Destructive-tool confirmation** â€?a known set of tools that mutate the device (shell writes,
 *    automation taps, app-lock, notifications, calendar writes) require explicit user consent.
 *    Foreground runs raise a Compose dialog via [onConfirmDestructive]; background runs deny.
 * 2. **Intent signature permission** â€?enforced at the manifest/receiver level, not here, but
 *    documented for completeness: only same-signature apps may fire WorkflowIntentReceiver.
 * 3. **Run-time + tool-call hard caps** â€?a runaway loop (cycle that slipped past [GraphBuilder],
 *    or a plugin tool that re-triggers) cannot run forever; the engine checks before every node.
 * 4. **Background tool whitelist** â€?when the run was launched from the background (WorkManager or
 *    Intent receiver), only read-only / low-impact tools are allowed. A background run that hits
 *    `execute_shell_command` is denied before it can do anything.
 *
 * Independent implementation. The specific tool classifications and limit defaults are Orange
 * Island's own policy choices.
 *
 * @param confirmDestructive suspending gate; returns true to allow, false to deny. Null in
 *   background context â†?destructive tools are always denied there.
 */
class WorkflowGuard(
    private val startedAt: () -> Long,
    private val maxRunMs: Long,
    private val maxToolCalls: Int,
    private val backgroundMode: Boolean,
    private val backgroundSafeOnly: Boolean,
    private val confirmDestructive: (suspend (toolName: String, args: String) -> Boolean)?
) {
    /** Live tally; the engine increments before each ActionNode dispatch and [check] reads it. */
    var toolCallCount: Int = 0
        private set

    /** Thrown (â†?caught by the engine â†?run cancelled) when a hard limit is exceeded. */
    fun checkBudget() {
        val elapsed = System.currentTimeMillis() - startedAt()
        if (elapsed > maxRunMs) {
            throw WorkflowLimitExceeded("Run exceeded ${maxRunMs}ms budget (elapsed=${elapsed}ms)")
        }
        if (toolCallCount >= maxToolCalls) {
            throw WorkflowLimitExceeded("Run exceeded $maxToolCalls tool-call cap (made=$toolCallCount)")
        }
    }

    /**
     * Pre-flight check for an [ActionNode]. Returns a [Verdict]:
     *  - [Verdict.Allow]            â†?dispatch the tool.
     *  - [Verdict.Deny]             â†?mark the node Errored with [message]; do not dispatch.
     *
     * Cooperative cancellation is also honoured: if the run's coroutine was cancelled, this
     * throws CancellationException rather than returning, so the engine's normal cancel path fires.
     */
    suspend fun preflight(node: ActionNode, resolvedArgs: String): Verdict =
        preflightTool(node.toolName, resolvedArgs)

    /**
     * Linear-mode preflight. Same checks as [preflight] but takes a
     * [com.orangeisland.app.model.LinearAction] directly (the linear engine doesn't deal in
     * [ActionNode]s). The args payload is the action's JSON args object serialised by the caller.
     */
    suspend fun preflightForLinear(action: com.orangeisland.app.model.LinearAction): Verdict =
        preflightTool(action.tool, action.args.toString())

    /** Shared core of both preflight entry points. */
    private suspend fun preflightTool(tool: String, resolvedArgs: String): Verdict {
        currentCoroutineContext().ensureActive()
        checkBudget()

        if (backgroundMode && backgroundSafeOnly && tool !in BACKGROUND_SAFE_TOOLS) {
            return Verdict.Deny("Tool '$tool' is not allowed in background-triggered workflows")
        }
        if (tool in DESTRUCTIVE_TOOLS) {
            val allowed = confirmDestructive?.invoke(tool, resolvedArgs) ?: false
            if (!allowed) {
                return Verdict.Deny(if (backgroundMode)
                    "Destructive tool '$tool' requires foreground confirmation"
                    else "User declined to run destructive tool '$tool'")
            }
        }
        toolCallCount++
        return Verdict.Allow
    }

    sealed class Verdict {
        data object Allow : Verdict()
        data class Deny(val message: String) : Verdict()
    }

    companion object {
        /**
         * Tools that mutate the device or the outside world and therefore require explicit consent.
         * Names match what [ToolDispatcher] routes (built-in names; plugin/mcp tools are prefixed
         * and fall through as non-destructive by default â€?they are user-authored and run in a
         * sandbox, so the bar is lower; a future policy may extend this).
         */
        val DESTRUCTIVE_TOOLS: Set<String> = setOf(
            "execute_shell_command", "file_write", "file_edit",
            "delete_memory_file", "edit_memory_file", "create_memory_file", "update_active_memory",
            "ui_tap", "ui_swipe", "ui_scroll", "ui_global_action", "ui_text_input",
            "lock_app", "unlock_app", "set_pin",
            "create_calendar_event", "delete_calendar_event",
            "generate_image"
        )

        /**
         * Tools considered safe to run unattended from the background: read-only or pure-query.
         * Anything not listed here is blocked in background runs when [backgroundSafeOnly] is set.
         *
         * The Navigation launching tools (open_app/open_url/open_settings/share_text) are included
         * here so background-triggered workflows can switch the screen to another app/page. They are
         * not read-only, but their only side-effect is starting an Activity; the Android 10+ system
         * layer (not this guard) gates the actual launch on the SYSTEM_ALERT_WINDOW permission, and
         * [com.orangeisland.app.tool.NavigationToolProvider.backgroundLaunchGuard] returns a clear
         * `background_activity_blocked` error when that permission is missing ¡ª so an unpermitted
         * background launch fails loudly instead of silently dropping.
         */
        val BACKGROUND_SAFE_TOOLS: Set<String> = setOf(
            "web_search", "web_fetch",
            "list_memory_files", "read_memory_file", "read_active_memory",
            "search_conversations", "list_conversations",
            "get_device_info", "get_battery_status", "get_location",
            "get_calendar_events", "get_notifications", "get_app_usage", "get_foreground_app",
            "ui_inspect", "list_installed_apps",
            "file_read", "file_glob", "file_grep",
            "open_app", "open_url", "open_settings", "share_text"
        )
    }
}
