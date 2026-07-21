package com.orangeisland.app.workflow.linear

import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearAction
import com.orangeisland.app.model.LinearFireStatus
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.workflow.RunLogger
import com.orangeisland.app.workflow.WorkflowGuard
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Executes a linear (trigger + conditions + actions) workflow.
 *
 * Fire flow:
 *  1. Load the definition; bail SKIPPED_DISABLED if it's off.
 *  2. Cooldown gate — bail SKIPPED_COOLDOWN if the last *actual* fire (SUCCESS/FAILED) was within
 *     [LinearWorkflow.cooldownMs]. (Skips don't reset the cooldown, matching the cap's accounting.)
 *  3. Daily-cap gate — bail SKIPPED_DAILY_CAP if today's actual fires ≥ [LinearWorkflow.maxRunsPerDay].
 *  4. Condition gate — build a [DeviceContext] snapshot via [contextProvider], bail
 *     SKIPPED_CONDITIONS if any condition fails (AND-combined, invert-aware).
 *  5. Execute actions in order. Each action goes through the same [WorkflowGuard.preflight] the
 *     graph engine uses (background whitelist, destructive-tool confirmation, time/call budget),
 *     then runs via [toolRunner] under a per-action timeout. Fail-fast: a failure stops the run.
 *  6. Record the outcome via [repository.recordLinearRunEnd].
 *
 * The engine itself owns the cooldown/cap/condition logic and stays Android-free; device-state
 * reads live in [contextProvider] and tool dispatch in [toolRunner], both injected so the core
 * is unit-testable.
 *
 * Independent implementation.
 *
 * @param guard null in unit tests (no destructive/budget checks); non-null in production where
 *   it carries the WorkflowGuard configured for this run's mode (FOREGROUND vs BACKGROUND).
 */
class LinearEngine(
    private val repository: WorkflowRepository,
    private val contextProvider: suspend () -> DeviceContext,
    private val toolRunner: ToolRunner,
    private val guard: WorkflowGuard?,
    private val runId: String,
    private val logger: RunLogger = RunLogger()
) {
    /** Functional interface for running one tool. Returns the tool's result string. */
    fun interface ToolRunner {
        suspend fun run(action: LinearAction): String
    }

    data class Outcome(val status: LinearFireStatus, val message: String, val summary: String)

    suspend fun fire(workflowId: String): Outcome {
        val def = repository.getLinear(workflowId)
            ?: return finish(workflowId, LinearFireStatus.FAILED, "workflow not found or not linear-mode", "")
        if (!def.enabled) return finish(workflowId, LinearFireStatus.SKIPPED_DISABLED, "disabled", "")

        // Cooldown gate.
        if (def.cooldownMs > 0) {
            val last = repository.lastActualFireAtMs(workflowId)
            if (last != null && System.currentTimeMillis() < last + def.cooldownMs) {
                return finish(workflowId, LinearFireStatus.SKIPPED_COOLDOWN, "within cooldown", "")
            }
        }
        // Daily-cap gate.
        if (def.maxRunsPerDay != null) {
            val today = repository.runsTodayCount(workflowId)
            if (today >= def.maxRunsPerDay) {
                return finish(workflowId, LinearFireStatus.SKIPPED_DAILY_CAP, "daily cap reached ($today/${def.maxRunsPerDay})", "")
            }
        }

        // Condition gate.
        val ctx = contextProvider()
        if (!ConditionEvaluator.allPass(def.conditions, ctx)) {
            val failed = def.conditions.filterNot { ConditionEvaluator.evaluate(it, ctx) }
                .joinToString(",") { it::class.simpleName ?: "?" }
            return finish(workflowId, LinearFireStatus.SKIPPED_CONDITIONS, "conditions failed: $failed", "")
        }

        // Execute actions in order (fail-fast).
        logger.debug("Linear fire '${def.name}': ${def.actions.size} action(s)")
        val outputs = mutableListOf<String>()
        def.actions.forEachIndexed { idx, action ->
            currentCoroutineContext().ensureActive()
            // Guard preflight (budget + background whitelist + destructive confirmation).
            guard?.let { g ->
                // Wrap as an ActionNode-equivalent check: guard.preflight takes toolName + args JSON.
                val verdict = g.preflightForLinear(action)
                if (verdict is WorkflowGuard.Verdict.Deny) {
                    logger.warn("Action[$idx] ${action.tool} blocked: ${verdict.message}")
                    return finish(workflowId, LinearFireStatus.FAILED,
                        "action[$idx] ${action.tool} blocked: ${verdict.message}", outputs.joinToString("\n"))
                }
            }
            logger.debug("Action[$idx] ${action.tool}")
            val out = withTimeoutOrNull(action.timeoutMs) { toolRunner.run(action) }
            if (out == null) {
                logger.error("Action[$idx] ${action.tool} timed out after ${action.timeoutMs}ms")
                return finish(workflowId, LinearFireStatus.FAILED,
                    "action[$idx] ${action.tool} timed out", outputs.joinToString("\n"))
            }
            outputs += "[$idx] ${action.tool}: ${out.take(200)}"
        }
        return finish(workflowId, LinearFireStatus.SUCCESS, "completed", outputs.joinToString("\n").take(2000))
    }

    private suspend fun finish(workflowId: String, status: LinearFireStatus, message: String, summary: String): Outcome {
        repository.recordLinearRunEnd(runId, status, message, null)
        return Outcome(status, message, summary)
    }
}
