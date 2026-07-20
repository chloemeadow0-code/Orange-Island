package com.orangeisland.app.viewmodel

import com.orangeisland.app.data.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Coordinates user confirmation of shell commands issued by remote MCP / shell servers.
 *
 * Owns the pending-confirmation [StateFlow], the per-session trust set, and the
 * suspend/await handshake between the generation pipeline (which asks) and the UI
 * (which answers). Extracted from [ChatViewModel] as a single responsibility so the
 * trust policy lives in one place and is independently testable.
 */
class ShellConfirmationController(private val settings: SettingsRepository) {
    data class PendingShellCommand(
        val server: String,
        val summary: String,
        val deferred: CompletableDeferred<Boolean>
    )

    private val _pendingShellCommand = MutableStateFlow<PendingShellCommand?>(null)
    val pendingShellCommand: StateFlow<PendingShellCommand?> = _pendingShellCommand.asStateFlow()

    // Servers the user chose to trust for the rest of this app session.
    private val sessionAllowedServers = Collections.synchronizedSet(mutableSetOf<String>())

    /** Suspends until the user resolves the prompt; returns whether the command may run. */
    suspend fun confirm(server: String, summary: String): Boolean {
        if (!settings.shellConfirmEnabled.value) return true
        if (sessionAllowedServers.contains(server)) return true
        val deferred = CompletableDeferred<Boolean>()
        _pendingShellCommand.value = PendingShellCommand(server, summary, deferred)
        return try { deferred.await() } finally {
            if (_pendingShellCommand.value?.deferred === deferred) _pendingShellCommand.value = null
        }
    }

    /** Called by the UI to resolve a pending confirmation. */
    fun resolve(allow: Boolean, alwaysAllowServer: Boolean = false) {
        val pending = _pendingShellCommand.value ?: return
        if (allow && alwaysAllowServer) sessionAllowedServers.add(pending.server)
        pending.deferred.complete(allow)
        _pendingShellCommand.value = null
    }

    fun setEnabled(enabled: Boolean) = settings.setShellConfirmEnabled(enabled)
}
