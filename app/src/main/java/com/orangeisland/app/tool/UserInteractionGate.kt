package com.orangeisland.app.tool

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Interactive choice gate — lets AI tools suspend until the user picks from a card-style
 * option list. Modelled after [WorkflowApprovalGate] to keep the approval/interaction
 * pattern consistent across the app.
 *
 * When a tool calls [request], the gate pushes a [Request] onto [pending] and suspends on
 * a [CompletableDeferred]. The chat screen observes [pending] and renders a
 * [com.orangeisland.app.ui.chat.UserInteractionDialog] for the head item. On confirm/cancel
 * the deferred completes with a JSON result string, unblocking the tool call.
 */
class UserInteractionGate {

    /** One selectable option shown inside the dialog card. */
    data class ChoiceOption(
        val id: String,
        val label: String,
        val description: String? = null
    )

    /** One pending choice request. */
    data class Request(
        val id: String,
        val question: String,
        val options: List<ChoiceOption>,
        val mode: String,               // "single" or "multiple"
        val allowCustom: Boolean,       // when true, show a text field for free-form input
        private val deferred: CompletableDeferred<String>
    ) {
        /** Complete the deferred with a JSON result string. Safe to call once. */
        fun complete(resultJson: String) {
            if (deferred.isActive) deferred.complete(resultJson)
        }

        /** Cancel the deferred (e.g. observer cleared without a verdict). */
        fun cancel() {
            if (deferred.isActive) deferred.cancel()
        }
    }

    private val _pending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = _pending.asStateFlow()

    /**
     * The interaction callback consumed by [UserInteractionToolProvider].
     * Suspends until the UI resolves the request, returning a JSON result.
     */
    val request: suspend (question: String, options: List<ChoiceOption>, mode: String, allowCustom: Boolean) -> String =
        request@{ question, options, mode, allowCustom ->
            val deferred = CompletableDeferred<String>()
            val request = Request(
                id = "ui_choice_${UUID.randomUUID()}",
                question = question,
                options = options,
                mode = mode.lowercase(),
                allowCustom = allowCustom,
                deferred = deferred
            )
            _pending.update { it + request }
            try {
                deferred.await()
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Observer vanished without resolving (e.g. user left the chat).
                buildUserCancelledJson()
            } finally {
                _pending.update { list -> list.filterNot { it.id == request.id } }
            }
        }

    /** Resolve a pending request by id with the user's selected option ids.
     *  [customText] is passed when the user entered free-form text instead of (or in addition to)
     *  picking preset options. */
    fun resolve(id: String, selectedIds: List<String>, customText: String = "") {
        val request = _pending.value.firstOrNull { it.id == id } ?: return
        val result = buildJsonResult(selectedIds, request.mode, customText)
        request.complete(result)
    }

    /** Cancel a specific pending request (treat as user-rejected). */
    fun cancel(id: String) {
        _pending.value.firstOrNull { it.id == id }?.cancel()
    }

    /** Cancel every pending request (e.g. the chat screen is leaving). */
    fun cancelAll() {
        _pending.value.forEach { it.cancel() }
        _pending.value = emptyList()
    }

    // ── Helpers ────────────────────────────────────────────

    private fun buildJsonResult(selectedIds: List<String>, mode: String, customText: String = ""): String =
        kotlinx.serialization.json.buildJsonObject {
            put("success", kotlinx.serialization.json.JsonPrimitive(true))
            put("selected_ids", kotlinx.serialization.json.JsonArray(
                selectedIds.map { kotlinx.serialization.json.JsonPrimitive(it) }
            ))
            put("mode", kotlinx.serialization.json.JsonPrimitive(mode))
            if (customText.isNotBlank()) {
                put("custom_text", kotlinx.serialization.json.JsonPrimitive(customText))
            }
        }.toString()

    private fun buildUserCancelledJson(): String =
        kotlinx.serialization.json.buildJsonObject {
            put("success", kotlinx.serialization.json.JsonPrimitive(false))
            put("error", kotlinx.serialization.json.JsonPrimitive("user_cancelled"))
            put("message", kotlinx.serialization.json.JsonPrimitive("用户取消了选择"))
        }.toString()
}
