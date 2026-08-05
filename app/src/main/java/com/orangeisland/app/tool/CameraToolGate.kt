package com.orangeisland.app.tool

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Bridges the AI `take_photo` tool to the camera-capture UI.
 *
 * When a tool calls [request], the gate pushes a [CaptureRequest] onto [pending]
 * and suspends on a [CompletableDeferred]. The chat screen observes [pending] and
 * renders a camera launcher (via ActivityResultContracts.TakePicture). On capture
 * complete the deferred resolves with the absolute image file path; on cancel it
 * resolves with null.
 *
 * Constructed once in [com.orangeisland.app.di.AppContainer] and shared between
 * [com.orangeisland.app.tool.device.CameraToolProvider] (tool side) and the chat
 * UI (render side).
 */
class CameraToolGate {

    /** One pending camera capture request. */
    data class CaptureRequest(
        val id: String,
        private val deferred: CompletableDeferred<String?>
    ) {
        /** Complete the deferred with the captured image file path. */
        fun complete(imagePath: String) {
            if (deferred.isActive) deferred.complete(imagePath)
        }

        /** Cancel the deferred (e.g. user dismissed or observer vanished). */
        fun cancel() {
            if (deferred.isActive) deferred.complete(null)
        }
    }

    private val _pending = MutableStateFlow<List<CaptureRequest>>(emptyList())
    val pending: StateFlow<List<CaptureRequest>> = _pending.asStateFlow()

    /**
     * The callback consumed by [com.orangeisland.app.tool.device.CameraToolProvider].
     * Pushes a capture request and suspends until the UI resolves it.
     * @return Absolute file path of the captured photo, or null if cancelled.
     */
    suspend fun request(): String? {
        val deferred = CompletableDeferred<String?>()
        val req = CaptureRequest(id = "camera_${UUID.randomUUID()}", deferred = deferred)
        _pending.update { it + req }
        return try {
            deferred.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            null
        } finally {
            _pending.update { list -> list.filterNot { it.id == req.id } }
        }
    }

    /** Resolve a pending request by id with the captured image path. */
    fun resolve(id: String, imagePath: String) {
        _pending.value.firstOrNull { it.id == id }?.complete(imagePath)
    }

    /** Cancel a specific pending request (treat as user-cancelled). */
    fun cancel(id: String) {
        _pending.value.firstOrNull { it.id == id }?.cancel()
    }

    /** Cancel every pending request (e.g. the chat screen is leaving). */
    fun cancelAll() {
        _pending.value.forEach { it.cancel() }
        _pending.value = emptyList()
    }
}
