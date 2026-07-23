package com.orangeisland.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orangeisland.app.data.local.WorkflowRunEntity
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.workflow.NodeState
import com.orangeisland.app.workflow.RunResult
import com.orangeisland.app.workflow.TriggerSource
import com.orangeisland.app.workflow.WorkflowRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ViewModel for the workflow subsystem: CRUD, manual trigger, live run-state, and run history.
 *
 * State is exposed as hot [StateFlow]s so Compose screens collect uniformly. The runner is
 * constructed per-run (AppContainer pattern) so the destructive-confirmation and node-state
 * callbacks can be wired into ViewModel state.
 *
 * Independent implementation.
 */
class WorkflowViewModel(
    private val repository: WorkflowRepository,
    private val runnerFactory: (
        onConfirmDestructive: (suspend (toolName: String, args: String) -> Boolean)?,
        onNodeState: ((String, NodeState) -> Unit)?
    ) -> WorkflowRunner
) : ViewModel() {

    // ── UI State ──────────────────────────────────────────────

    /** All workflows, newest first. */
    val workflows: StateFlow<List<Workflow>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** All AI-authored (linear-mode) workflows, newest first — drives the v2 list screen. Each
     *  row carries its trigger for a summary badge and the mirrored run-stat fields. */
    val linearWorkflows: StateFlow<List<com.orangeisland.app.model.LinearWorkflow>> =
        repository.observeAllLinear()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The workflow currently being edited or inspected. */
    private val _selectedWorkflow = MutableStateFlow<Workflow?>(null)
    val selectedWorkflow: StateFlow<Workflow?> = _selectedWorkflow.asStateFlow()

    /** The linear workflow currently being inspected (detail page), or null. Set alongside
     *  [selectedWorkflow] by [selectLinearWorkflow]; kept separate because graph-mode workflows
     *  don't decode as [com.orangeisland.app.model.LinearWorkflow]. */
    private val _selectedLinear = MutableStateFlow<com.orangeisland.app.model.LinearWorkflow?>(null)
    val selectedLinear: StateFlow<com.orangeisland.app.model.LinearWorkflow?> = _selectedLinear.asStateFlow()

    /** Run history for the selected workflow. */
    private val _runs = MutableStateFlow<List<WorkflowRunEntity>>(emptyList())
    val runs: StateFlow<List<WorkflowRunEntity>> = _runs.asStateFlow()

    /** IDs of workflows currently executing (manual foreground runs). */
    private val _runningWorkflowIds = MutableStateFlow<Set<String>>(emptySet())
    val runningWorkflowIds: StateFlow<Set<String>> = _runningWorkflowIds.asStateFlow()

    /** Node states for the active run (canvas highlight). */
    private val _activeNodeStates = MutableStateFlow<Map<String, NodeState>>(emptyMap())
    val activeNodeStates: StateFlow<Map<String, NodeState>> = _activeNodeStates.asStateFlow()

    /** Which workflow the active node states belong to. */
    private val _activeRunWorkflowId = MutableStateFlow<String?>(null)
    val activeRunWorkflowId: StateFlow<String?> = _activeRunWorkflowId.asStateFlow()

    /** Pending destructive-tool confirmations. */
    data class PendingConfirmation(
        val id: String,
        val workflowId: String,
        val toolName: String,
        val args: String,
        val onResult: (Boolean) -> Unit
    )

    private val _pendingConfirmations = MutableStateFlow<List<PendingConfirmation>>(emptyList())
    val pendingConfirmations: StateFlow<List<PendingConfirmation>> = _pendingConfirmations.asStateFlow()

    /** One-shot events (run finished, errors). */
    sealed class Event {
        data class RunCompleted(val workflowId: String, val success: Boolean, val message: String) : Event()
        data class Error(val message: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(replay = 0)
    val events = _events.asSharedFlow()

    // ── CRUD ──────────────────────────────────────────────────

    /** Load a single workflow into [selectedWorkflow] and start observing its runs. */
    fun selectWorkflow(id: String?) {
        if (id == null) {
            _selectedWorkflow.value = null
            _selectedLinear.value = null
            _runs.value = emptyList()
            return
        }
        viewModelScope.launch {
            val wf = repository.get(id)
            _selectedWorkflow.value = wf
            // Linear workflows are decoded separately so the detail page can render the trigger /
            // conditions / actions card. Falls back to null for graph-mode rows.
            _selectedLinear.value = repository.getLinear(id)
            if (wf != null) {
                loadRuns(id)
            }
        }
    }

    /** Convenience for the detail page: load by id and expose the linear model directly. */
    fun selectLinearWorkflow(id: String?) {
        selectWorkflow(id)
    }

    fun createWorkflow(
        name: String,
        description: String = "",
        nodes: List<com.orangeisland.app.model.FlowNode> = emptyList(),
        edges: List<com.orangeisland.app.model.FlowEdge> = emptyList()
    ): Workflow {
        val now = System.currentTimeMillis()
        val wf = Workflow(
            id = "wf_${UUID.randomUUID()}",
            name = name.trim(),
            description = description.trim(),
            nodes = nodes,
            edges = edges,
            enabled = true,
            createdAt = now,
            updatedAt = now
        )
        // Persist, then publish as the selected workflow INSIDE the same coroutine so the editor
        // never sees a blank page: a fire-and-forget upsert followed by an async selectWorkflow
        // races (the select can read before the upsert commits → null → blank editor). Setting
        // selectedWorkflow to the just-built object right after upsert guarantees the editor has
        // a non-null workflow to render as soon as navigation lands on it.
        viewModelScope.launch {
            repository.upsert(wf)
            _selectedWorkflow.value = wf
            _selectedLinear.value = null
        }
        // Optimistically publish immediately too, so the editor (which reads selectedWorkflow
        // synchronously on first composition) doesn't flash empty before the coroutine runs.
        _selectedWorkflow.value = wf
        _selectedLinear.value = null
        return wf
    }

    fun saveWorkflow(workflow: Workflow) {
        viewModelScope.launch {
            repository.upsert(workflow)
            if (_selectedWorkflow.value?.id == workflow.id) {
                _selectedWorkflow.value = workflow
            }
        }
    }

    fun deleteWorkflow(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            if (_selectedWorkflow.value?.id == id) {
                _selectedWorkflow.value = null
                _selectedLinear.value = null
                _runs.value = emptyList()
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
        }
    }

    fun duplicateWorkflow(id: String) {
        viewModelScope.launch {
            val source = repository.get(id) ?: return@launch
            val now = System.currentTimeMillis()
            val copy = source.copy(
                id = "wf_${UUID.randomUUID()}",
                name = "${source.name} (copy)",
                createdAt = now,
                updatedAt = now
            )
            repository.upsert(copy)
        }
    }

    // ── Trigger / Run ─────────────────────────────────────────

    /** Launch a foreground manual run. Live state streams into [runningWorkflowIds] + [activeNodeStates]. */
    fun runWorkflow(id: String, startNodeId: String? = null) {
        viewModelScope.launch {
            if (id in _runningWorkflowIds.value) {
                _events.emit(Event.Error("Workflow is already running"))
                return@launch
            }
            _runningWorkflowIds.value += id
            _activeRunWorkflowId.value = id
            _activeNodeStates.value = emptyMap()

            val runner = runnerFactory(
                { toolName, args ->
                    requestConfirmation(id, toolName, args)
                },
                { nodeId, state ->
                    _activeNodeStates.value = _activeNodeStates.value.toMutableMap().apply {
                        put(nodeId, state)
                    }
                }
            )

            val result = try {
                runner.run(
                    workflowId = id,
                    mode = WorkflowRunner.Mode.FOREGROUND,
                    source = TriggerSource.Manual,
                    startNodeId = startNodeId
                )
            } catch (e: Exception) {
                _events.emit(Event.Error(e.message ?: "Run failed"))
                RunResult(
                    workflowId = id,
                    runId = "none",
                    success = false,
                    message = e.message ?: "Exception",
                    startedAt = System.currentTimeMillis(),
                    finishedAt = System.currentTimeMillis(),
                    states = emptyMap(),
                    logs = emptyList()
                )
            } finally {
                _runningWorkflowIds.value -= id
                if (_activeRunWorkflowId.value == id) {
                    _activeRunWorkflowId.value = null
                    _activeNodeStates.value = emptyMap()
                }
            }

            _events.emit(Event.RunCompleted(id, result.success, result.message))
            // Refresh runs for the selected workflow if it matches
            if (_selectedWorkflow.value?.id == id) {
                loadRuns(id)
            }
        }
    }

    /** Resolve a pending destructive-tool confirmation. Called by the UI dialog. */
    fun resolveConfirmation(id: String, allow: Boolean) {
        val list = _pendingConfirmations.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx == -1) return
        val pending = list[idx]
        _pendingConfirmations.value = list.toMutableList().apply { removeAt(idx) }
        pending.onResult(allow)
    }

    // ── Run history ───────────────────────────────────────────

    fun loadRuns(workflowId: String) {
        viewModelScope.launch {
            _runs.value = withContext(Dispatchers.IO) {
                repository.getRecentRuns(workflowId, limit = 50)
            }
        }
    }

    /** Observe runs reactively (for the log page). */
    fun observeRuns(workflowId: String): Flow<List<WorkflowRunEntity>> =
        repository.observeRuns(workflowId)

    suspend fun getRun(runId: String): WorkflowRunEntity? = repository.getRun(runId)

    // ── Helpers ───────────────────────────────────────────────

    private suspend fun requestConfirmation(
        workflowId: String,
        toolName: String,
        args: String
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val confirmation = PendingConfirmation(
                id = "confirm_${UUID.randomUUID()}",
                workflowId = workflowId,
                toolName = toolName,
                args = args,
                onResult = { allow ->
                    if (continuation.isActive) continuation.resumeWith(Result.success(allow))
                }
            )
            _pendingConfirmations.value += confirmation
            // Clean up if the calling coroutine is cancelled (e.g. user leaves the page or the
            // ViewModel is cleared). Without this, the PendingConfirmation leaks in the list and
            // its continuation is never resumed — a suspended coroutine + memory hold.
            continuation.invokeOnCancellation {
                _pendingConfirmations.value = _pendingConfirmations.value.filterNot { it.id == confirmation.id }
            }
        }
    }
}
