package com.orangeisland.app.tool

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Approval gate for sensitive device-access tools (location, notifications, usage stats).
 *
 * When [autoApprove] is false (the default), every AI-driven call to a gated tool suspends
 * until the UI resolves the pending request. If the user rejects (or the observer vanishes),
 * the tool returns an "approval_denied" error so the model knows not to retry silently.
 *
 * When [autoApprove] is true, the gate short-circuits and immediately returns true without
 * showing any dialog.
 *
 * Modelled after [com.orangeisland.app.workflow.WorkflowApprovalGate] to keep the approval
 * pattern consistent across the app.
 */
class SensitiveToolApprovalGate {

    /** One pending approval request. */
    data class Request(
        val id: String,
        val toolName: String,
        val description: String,
        private val deferred: CompletableDeferred<Boolean>
    ) {
        /** Complete the deferred with the user's verdict. Safe to call once. */
        fun complete(approved: Boolean) {
            if (deferred.isActive) deferred.complete(approved)
        }

        /** Cancel the deferred (e.g. observer cleared without a verdict). */
        fun cancel() {
            if (deferred.isActive) deferred.cancel()
        }
    }

    /** When true, the [approval] callback bypasses the dialog and returns true immediately. */
    @Volatile
    var autoApprove: Boolean = false

    private val _pending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = _pending.asStateFlow()

    /**
     * The approval callback consumed by the sensitive [ToolProvider]s.
     * Suspends until the UI resolves the request, returning the user's verdict.
     */
    val approval: suspend (toolName: String, description: String) -> Boolean = approval@{ toolName, description ->
        if (autoApprove) return@approval true

        val deferred = CompletableDeferred<Boolean>()
        val request = Request(
            id = "st_approval_${UUID.randomUUID()}",
            toolName = toolName,
            description = description,
            deferred = deferred
        )
        _pending.update { it + request }
        try {
            deferred.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Observer vanished without resolving (e.g. user left the chat). Treat as reject.
            false
        } finally {
            _pending.update { list -> list.filterNot { it.id == request.id } }
        }
    }

    /** Resolve a pending request by id with the user's verdict. */
    fun resolve(id: String, approved: Boolean) {
        _pending.value.firstOrNull { it.id == id }?.complete(approved)
    }

    /** Cancel every pending request (e.g. the chat screen is leaving). */
    fun cancelAll() {
        _pending.value.forEach { it.cancel() }
        _pending.value = emptyList()
    }
}
