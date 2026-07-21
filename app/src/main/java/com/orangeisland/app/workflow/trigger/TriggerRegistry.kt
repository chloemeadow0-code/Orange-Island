package com.orangeisland.app.workflow.trigger

import android.content.Context
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates every [TriggerFamily]. On app start (and whenever the enabled-workflow set changes),
 * it buckets the workflows by which family handles each one's trigger, then calls each family's
 * [TriggerFamily.sync] with its bucket. Families register/unregister their OS hooks accordingly,
 * so a workflow that's deleted or disabled immediately stops consuming battery.
 *
 * The flow is debounced 500ms so a burst of edits (the user typing in the editor, or a batch AI
 * update) doesn't thrash receivers on and off.
 *
 * The fire callback is set once (by the app wiring that owns the linear engine) before [start].
 *
 * Independent implementation.
 */
class TriggerRegistry(
    private val context: Context,
    private val repository: WorkflowRepository,
    private val scope: CoroutineScope,
    private val familyProvider: () -> List<TriggerFamily>
) {
    @Volatile private var fireCallback: TriggerFireCallback? = null
    private val syncMutex = Mutex()
    private var observeJob: Job? = null

    /** Bind the engine callback, then start watching the workflow table. Idempotent. */
    fun start(callback: TriggerFireCallback) {
        fireCallback = callback
        if (observeJob?.isActive == true) return
        observeJob = scope.launch(Dispatchers.IO) {
            // Re-sync whenever the enabled-linear-workflow set changes. DistinctUntilChanged on the
            // id+enabled+updatedAt+trigger signature avoids re-syncs for runs-stats-only updates.
            repository.observeEnabledLinear()
                .map { list -> list.signature() }
                .distinctUntilChanged()
                .debounce(500)
                .collect { resync() }
        }
    }

    /** Force a one-shot resync (call after enabling a permission, for instance). */
    suspend fun resync() = syncMutex.withLock {
        val callback = fireCallback ?: return@withLock
        val enabled = runCatching { repository.getEnabledLinear() }.getOrDefault(emptyList())
        for (family in families) {
            val matching = enabled.filter { family.handles(it.trigger) }
            runCatching { family.sync(matching, callback) }
                .onFailure { DebugLog.e(TAG, "family ${family.name} sync failed", it) }
        }
    }

    /** Stop observing and tear down every family. */
    suspend fun shutdown() {
        observeJob?.cancel()
        for (family in families) {
            runCatching { family.shutdown() }
        }
    }

    /** The family list, lazily created once (families hold receiver refs, so we want one set). */
    private val families: List<TriggerFamily> by lazy { familyProvider() }

    /** A stable signature of the workflow set for change detection. */
    private fun List<LinearWorkflow>.signature(): String =
        joinToString(",") { "${it.id}:${it.enabled}:${it.updatedAt}:${it.trigger::class.simpleName}" }

    companion object {
        private const val TAG = "TriggerRegistry"
    }
}
