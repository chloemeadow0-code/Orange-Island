package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Signal source for app-foreground triggers, fed by [AppForegroundDispatcher]:
 *
 *  - [LinearTrigger.AppLaunched] / graph [TriggerSpec.AppOpen] — fires when [packageName] becomes foreground.
 *  - [LinearTrigger.AppClosed]   — fires when [packageName] leaves the foreground.
 *  - [LinearTrigger.AppForegroundDuration] — fires after [packageName] has stayed continuously
 *    in the foreground for ≥ [LinearTrigger.AppForegroundDuration.minutes].
 *
 * Both linear and graph (canvas) workflows are monitored: linear via [WorkflowRepository.observeEnabledLinear],
 * graph via [WorkflowRepository.observeAll] filtered to enabled workflows whose start node carries
 * an [TriggerSpec.AppOpen] with a non-blank [TriggerSpec.AppOpen.packageName].
 *
 * Transitions are detected by diffing the new package against the previous one. The duration
 * variant arms a per-workflow delayed coroutine on enter; the coroutine re-checks
 * [AppForegroundDispatcher.lastKnown] before firing so a mid-window app switch cancels the fire.
 *
 * Independent implementation.
 */
object AppForegroundSignalSource {

    fun start(
        scope: CoroutineScope,
        repository: WorkflowRepository,
        starter: WorkflowStarter
    ): Job = scope.launch(Dispatchers.IO) {
        // Current matching set (linear), refreshed on every enabled-workflow emission.
        var matchingLinear: List<LinearWorkflow> = emptyList()
        // Current matching set (graph AppOpen), keyed by workflow id → target package.
        var matchingGraph: List<Pair<String, String>> = emptyList()
        val setJob = scope.launch {
            repository.observeEnabledLinear().collectLatest { all ->
                matchingLinear = all.filter {
                    it.trigger is LinearTrigger.AppLaunched ||
                        it.trigger is LinearTrigger.AppClosed ||
                        it.trigger is LinearTrigger.AppForegroundDuration
                }
            }
        }
        val graphJob = scope.launch {
            repository.observeAll().collectLatest { all ->
                // Map each enabled graph workflow to (id, packageName) for its first AppOpen start node.
                // Only AppOpen nodes with a non-blank package are actionable; a blank package means
                // "unconfigured" and would fire on every app switch, which is never what the user wants.
                matchingGraph = all
                    .filter { it.enabled }
                    .mapNotNull { wf -> appOpenTarget(wf)?.let { pkg -> wf.id to pkg } }
            }
        }
        // Per-workflow duration timers; replaced wholesale on every enter transition.
        var timers: List<Job> = emptyList()
        var prevPackage: String? = null

        val remover = AppForegroundDispatcher.subscribe { newPackage ->
            val currentLinear = matchingLinear
            val currentGraph = matchingGraph
            if ((currentLinear.isEmpty() && currentGraph.isEmpty()) || newPackage == prevPackage) return@subscribe
            val prev = prevPackage
            prevPackage = newPackage

            // AppLaunched / AppClosed / graph AppOpen fire immediately on the transition.
            val immediate = mutableListOf<Pair<String, LinearTrigger>>()
            currentLinear.mapNotNull { wf ->
                when (val t = wf.trigger) {
                    is LinearTrigger.AppLaunched -> if (t.packageName == newPackage) wf.id to t else null
                    is LinearTrigger.AppClosed -> if (prev != null && t.packageName == prev) wf.id to t else null
                    else -> null
                }
            }.forEach { immediate.add(it) }
            if (immediate.isNotEmpty()) {
                scope.launch {
                    immediate.forEach { (id, _) -> runCatching { starter.start(id) } }
                }
            }
            // Graph AppOpen: fire any graph workflow whose target package just became foreground.
            if (currentGraph.isNotEmpty()) {
                val graphHits = currentGraph.filter { (_, pkg) -> pkg == newPackage }
                if (graphHits.isNotEmpty()) {
                    scope.launch {
                        graphHits.forEach { (id, _) -> runCatching { starter.start(id) } }
                    }
                }
            }

            // Cancel any in-flight duration timers from the previous foreground app.
            timers.forEach { runCatching { it.cancel() } }
            timers = currentLinear.mapNotNull { wf ->
                val t = wf.trigger as? LinearTrigger.AppForegroundDuration ?: return@mapNotNull null
                if (t.packageName != newPackage) return@mapNotNull null
                scope.launch {
                    delay(t.minutes.toLong() * 60_000L)
                    // Re-check: user might have switched away during the wait.
                    if (AppForegroundDispatcher.lastKnown == t.packageName) {
                        runCatching { starter.start(wf.id) }
                            .onFailure { DebugLog.w("AppForegroundSignalSource", "duration fire failed", it) }
                    }
                }
            }
        }
        // The remover is held for the source's lifetime; app-lifetime sources never tear down.
    }

    /** If [wf] has a start node with an [TriggerSpec.AppOpen] trigger carrying a non-blank target
     *  package, return that package; otherwise null. Used to decide which graph workflows to fire
     *  on a foreground transition. */
    private fun appOpenTarget(wf: Workflow): String? {
        val spec = wf.nodes.firstNotNullOfOrNull { n ->
            (n as? com.orangeisland.app.model.StartNode)?.trigger as? TriggerSpec.AppOpen
        } ?: return null
        val pkg = spec.packageName
        return pkg.ifBlank { null }
    }
}
