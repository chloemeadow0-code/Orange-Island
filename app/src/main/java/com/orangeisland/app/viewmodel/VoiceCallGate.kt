package com.orangeisland.app.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Bridges the AI `make_voice_call` tool (which runs deep in the tool-dispatch path, with no UI
 * handle) to the full-screen incoming-call UI the chat screen renders — exactly the way
 * [com.orangeisland.app.workflow.WorkflowApprovalGate] and [com.orangeisland.app.tool.
 * SensitiveToolApprovalGate] bridge their tools to a dialog.
 *
 * Flow: the model decides it wants to talk and calls `make_voice_call(reason)`. The tool provider
 * calls [request] with a human-readable [reason]; [request] pushes a fresh [IncomingCall] (carrying a
 * [CompletableDeferred]) onto the queue and **suspends**. The chat screen observes [pending] and
 * renders a phone-style incoming-call screen (ring + Accept/Decline). When the user answers,
 * [accept] completes the deferred with `true` and the call loop starts; if they decline (or the
 * observer vanishes) it completes `false` and the tool returns a "declined" message.
 *
 * Constructed once in [com.orangeisland.app.di.AppContainer] and shared between the
 * [com.orangeisland.app.tool.VoiceCallToolProvider] (tool side) and the incoming-call UI (render
 * side), so a single instance is wired to both. In a background context with no UI observing,
 * requests sit pending until the observer scope is cleared, at which point the deferred is
 * cancelled and the tool surfaces an error — an AI voice call is a foreground, user-witnessed
 * action by design.
 */
class VoiceCallGate {

    /** One incoming-call request, suspended on the user's answer. */
    data class IncomingCall(
        val id: String,
        val reason: String,
        private val deferred: CompletableDeferred<Boolean>
    ) {
        /** Complete the deferred with the user's verdict (true = answer, false = decline). */
        fun complete(answered: Boolean) {
            if (deferred.isActive) deferred.complete(answered)
        }

        /** Cancel the deferred (e.g. observer cleared without an answer). */
        fun cancel() {
            if (deferred.isActive) deferred.cancel()
        }
    }

    private val _pending = MutableStateFlow<List<IncomingCall>>(emptyList())
    val pending: StateFlow<List<IncomingCall>> = _pending.asStateFlow()

    /** The callback consumed by [com.orangeisland.app.tool.VoiceCallToolProvider]. Pushes an
     *  incoming-call request and suspends until the user answers or declines. Returns true if the
     *  user answered, false otherwise (declined or observer vanished). */
    suspend fun request(reason: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val call = IncomingCall(id = "voice_call_${UUID.randomUUID()}", reason = reason, deferred = deferred)
        _pending.update { it + call }
        return try {
            deferred.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Observer vanished (e.g. user left the chat) without answering. Treat as decline.
            false
        } finally {
            _pending.update { list -> list.filterNot { it.id == call.id } }
        }
    }

    /** Answer (true) or decline (false) the head pending request by id. */
    fun resolve(id: String, answered: Boolean) {
        _pending.value.firstOrNull { it.id == id }?.complete(answered)
    }

    /** Cancel every pending request (e.g. the chat screen is leaving). */
    fun cancelAll() {
        _pending.value.forEach { it.cancel() }
        _pending.value = emptyList()
    }
}
