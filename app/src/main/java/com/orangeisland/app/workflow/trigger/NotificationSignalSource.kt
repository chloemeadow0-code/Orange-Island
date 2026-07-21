package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.tool.device.DeviceNotificationListenerService
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Signal source for [LinearTrigger.NotificationReceived]. Subscribes to
 * [DeviceNotificationListenerService.observePosted] (the listener service's live-posted stream)
 * and routes each posted notification to the workflows whose filters match.
 *
 * **Filter index**: workflows are bucketed by their `packageName` filter into a map so a posted
 * notification only has to test the workflows keyed on its own package (plus the wildcard ones
 * with no package filter), instead of iterating every workflow on every post.
 *
 * Independent implementation.
 */
object NotificationSignalSource {

    fun start(
        scope: CoroutineScope,
        repository: WorkflowRepository,
        starter: WorkflowStarter
    ): Job = scope.launch(Dispatchers.IO) {
        var index = NotificationFilterIndex(emptyList())
        val indexJob = scope.launch {
            repository.observeEnabledLinear().collectLatest { all ->
                index = NotificationFilterIndex(
                    all.filter { it.trigger is LinearTrigger.NotificationReceived }
                )
            }
        }
        try {
            // observePosted returns a Runnable remover; we hold it for the source's lifetime.
            // (An app-lifetime source never tears down, so the leak is acceptable. The listener
            // service holds listeners in a CopyOnWriteArrayList so an unused entry is cheap.)
            DeviceNotificationListenerService.observePosted { n ->
                val hits = index.match(n.packageName, n.title, n.text)
                if (hits.isNotEmpty()) {
                    scope.launch {
                        hits.forEach { runCatching { starter.start(it.id) } }
                    }
                }
            }
        } catch (t: Throwable) {
            DebugLog.e("NotificationSignalSource", "subscribe failed", t)
            indexJob.cancel()
        }
    }

    /**
     * Pre-indexed set of notification filters. Built once per workflow-set change; query is
     * O(1 + bucket size) instead of O(n workflows).
     */
    private class NotificationFilterIndex(workflows: List<LinearWorkflow>) {
        /** package name → workflows whose packageName filter is exactly that; "*" = no filter. */
        private val byPackage: Map<String, List<LinearWorkflow>> =
            workflows.groupBy { (it.trigger as LinearTrigger.NotificationReceived).packageName ?: "*" }

        fun match(pkg: String, title: String, text: String): List<LinearWorkflow> {
            val candidates = byPackage[pkg].orEmpty() + byPackage["*"].orEmpty()
            return candidates.filter { wf ->
                val t = wf.trigger as LinearTrigger.NotificationReceived
                (t.titleContains == null || title.contains(t.titleContains)) &&
                    (t.textContains == null || text.contains(t.textContains)) &&
                    (t.titleMatches == null || safeRegexFind(t.titleMatches, title)) &&
                    (t.textMatches == null || safeRegexFind(t.textMatches, text))
            }
        }

        /** Regex find that never throws on a bad pattern (defensive; authoring-time validation
         *  already rejects un-compilable patterns, but stored rows from before that tightening
         *  could still exist). */
        private fun safeRegexFind(pattern: String, input: String): Boolean =
            runCatching { Regex(pattern).containsMatchIn(input) }.getOrDefault(false)
    }
}
