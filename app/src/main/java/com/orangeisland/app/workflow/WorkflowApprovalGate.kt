package com.orangeisland.app.workflow

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Bridges the AI authoring tools' [WorkflowAiToolProvider.approval] callback (which runs deep in
 * the tool-dispatch path, with no UI handle) to a Compose dialog the chat screen can render.
 *
 * The flow: the model calls `workflow_create`/`_update`/`_delete`/`_set_enabled`; the tool
 * provider renders a human-readable card via [WorkflowApprovalRenderer] and calls
 * [request] (the gate's [approval] function). [request] pushes the card text + a fresh
 * [CompletableDeferred] onto the pending queue and suspends. The chat screen observes
 * [pending], shows an AlertDialog for the head item, and on Approve/Reject calls
 * [resolve] with the id + verdict — which completes the deferred and unblocks the tool call.
 *
 * The gate is constructed once in [com.orangeisland.app.di.AppContainer] and captured by the
 * WorkflowAiToolProvider (so the same instance is wired to the dispatcher and to the UI). A
 * background context (no UI observing) leaves requests pending; the deferred is cancelled when the
 * observer scope is cleared, and the tool call surfaces an error — authoring is a foreground,
 * user-witnessed action by design.
 *
 * Independent implementation.
 */
class WorkflowApprovalGate {

    /** One pending approval request. */
    data class Request(
        val id: String,
        val card: String,
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

    private val _pending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = _pending.asStateFlow()

    /** The approval callback the [WorkflowAiToolProvider] is constructed with. Suspends until the
     *  UI resolves the request, returning the user's verdict. */
    val approval: suspend (card: String) -> Boolean = { card ->
        val deferred = CompletableDeferred<Boolean>()
        val request = Request(id = "wf_approval_${UUID.randomUUID()}", card = card, deferred = deferred)
        _pending.update { it + request }
        try {
            deferred.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Observer vanished without resolving (e.g. user left the chat). Treat as a reject so
            // the authoring tool returns its "requires approval" error rather than hanging.
            false
        } finally {
            _pending.update { list -> list.filterNot { it.id == request.id } }
        }
    }

    /** Resolve the head (or any) pending request by id with the user's verdict. */
    fun resolve(id: String, approved: Boolean) {
        _pending.value.firstOrNull { it.id == id }?.complete(approved)
    }

    /** Cancel every pending request (e.g. the chat screen is leaving). */
    fun cancelAll() {
        _pending.value.forEach { it.cancel() }
        _pending.value = emptyList()
    }
}
