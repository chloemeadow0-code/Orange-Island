package com.orangeisland.app.workflow.trigger

import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Trigger family for [LinearTrigger.BootCompleted]. There is no runtime-registered listener — the
 * manifest-declared [com.orangeisland.app.workflow.trigger.WorkflowBootReceiver] receives
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED and forwards to [WorkflowBootDispatcher.onBoot], which the
 * family publishes itself into via [bind].
 *
 * [sync] simply records the latest matching set + callback so [onBoot] knows whom to fire. The
 * cold-boot race (BOOT_COMPLETED can arrive before the registry's repo flow has emitted) is
 * handled the same way as the cron path: [onBoot] reads the enabled BootCompleted workflows
 * straight from the repository when the in-memory snapshot is empty, so a workflow authored
 * before reboot still fires on the next boot.
 *
 * Independent implementation.
 */
class BootTriggerFamily(
    private val scope: CoroutineScope,
    private val repository: com.orangeisland.app.data.repository.WorkflowRepository,
    private val runnerFire: suspend (workflowId: String, trigger: LinearTrigger) -> Unit
) : TriggerFamily {

    override val name: String = "boot"

    @Volatile private var matching: List<LinearWorkflow> = emptyList()
    @Volatile private var callback: TriggerFireCallback? = null

    override fun handles(trigger: LinearTrigger): Boolean = trigger is LinearTrigger.BootCompleted

    override suspend fun sync(matching: List<LinearWorkflow>, callback: TriggerFireCallback) {
        this.matching = matching
        this.callback = callback
    }

    /** Called by [WorkflowBootDispatcher.onBoot]. Fires each matching workflow through the
     *  registry callback when present, falling back to a repository-driven fire path otherwise. */
    fun onBoot() {
        scope.launch(Dispatchers.IO) {
            val cb = callback
            val snap = matching
            if (cb != null && snap.isNotEmpty()) {
                for (wf in snap) {
                    if (wf.trigger !is LinearTrigger.BootCompleted) continue
                    runCatching { cb.onFire(wf.id, wf.trigger) }
                        .onFailure { DebugLog.e(TAG, "boot fire failed for wf=${wf.id}", it) }
                }
                return@launch
            }
            // Cold-boot fallback: the receiver may have been invoked before the registry's flow
            // emitted. Read enabled linear workflows directly and fire the boot ones through the
            // injected runner path (which re-checks enabled / cooldown / conditions).
            runCatching {
                repository.getEnabledLinear().filter { it.trigger is LinearTrigger.BootCompleted }
            }.getOrDefault(emptyList()).forEach { wf ->
                runCatching { runnerFire(wf.id, wf.trigger) }
                    .onFailure { DebugLog.e(TAG, "boot fallback fire failed for wf=${wf.id}", it) }
            }
        }
    }

    override suspend fun shutdown() {
        matching = emptyList()
        callback = null
    }

    companion object {
        private const val TAG = "BootTriggerFamily"
    }
}

/**
 * Bridge from the manifest-declared [WorkflowBootReceiver] to the live [BootTriggerFamily]. The
 * receiver runs in a fresh process before the app's singletons exist, so it cannot hold a direct
 * reference — instead it routes through this object, which the family registers itself with via
 * [bind] when the [com.orangeisland.app.di.AppContainer] wires the registry.
 *
 * A nullable provider avoids a hard dependency on the family: when the registry isn't up yet
 * (early boot, or workflows disabled in settings) the receiver's call is a silent no-op.
 */
object WorkflowBootDispatcher {
    @Volatile private var familyRef: BootTriggerFamily? = null

    /** Called by [com.orangeisland.app.di.AppContainer] once the family is constructed. */
    fun bind(family: BootTriggerFamily) {
        familyRef = family
    }

    /** Called by [WorkflowBootReceiver.onReceive]. Safe to call from any thread. */
    fun onBoot() = runCatching { familyRef?.onBoot() }.getOrDefault(Unit)
}
