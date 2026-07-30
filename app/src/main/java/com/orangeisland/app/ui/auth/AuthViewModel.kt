package com.orangeisland.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orangeisland.app.data.repository.AuthResult
import com.orangeisland.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which form the auth screen is currently showing. */
enum class AuthMode { LOGIN, REGISTER }

/**
 * Single state object for the auth screen. [mode] drives which form is visible;
 * [loading] gates the submit button; [errorCode] (when non-null) is surfaced as a
 * translated dialog and then cleared on next interaction.
 */
data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val loading: Boolean = false,
    val errorCode: String? = null,
    val infoCode: String? = null
)

/**
 * Orchestrates registration / login against [AuthRepository].
 *
 * The username/password/invite strings live as plain `var` fields on this VM
 * (mirroring how SettingsProxyPage keeps drafts in the composable) — they are
 * UI-scoped inputs, not persisted state, so DataStore is not involved.
 */
class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    // Live form inputs (not part of [ui] — they're typed into, not derived).
    var loginUsername: String = ""
    var loginPassword: String = ""
    var regUsername: String = ""
    var regPassword: String = ""
    var regInviteCode: String = ""

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    val isLoggedIn: StateFlow<Boolean?> get() = auth.isLoggedIn

    fun setMode(mode: AuthMode) {
        _ui.value = _ui.value.copy(mode = mode, errorCode = null, infoCode = null)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(errorCode = null, infoCode = null)
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    fun login() {
        if (loginUsername.isBlank() || loginPassword.isBlank()) {
            _ui.value = _ui.value.copy(errorCode = "fields_required")
            return
        }
        _ui.value = _ui.value.copy(loading = true, errorCode = null)
        viewModelScope.launch {
            val r = auth.login(loginUsername, loginPassword)
            _ui.value = when (r) {
                is AuthResult.Success -> _ui.value.copy(loading = false)
                is AuthResult.Error -> _ui.value.copy(loading = false, errorCode = r.message)
            }
        }
    }

    // ── Register (single-shot, no email/OTP) ──────────────────────────────────

    fun register() {
        val errs = validateRegisterForm()
        if (errs != null) {
            _ui.value = _ui.value.copy(errorCode = errs)
            return
        }
        _ui.value = _ui.value.copy(loading = true, errorCode = null)
        viewModelScope.launch {
            val r = auth.register(regUsername, regPassword, regInviteCode)
            _ui.value = when (r) {
                is AuthResult.Success -> _ui.value.copy(loading = false)
                is AuthResult.Error -> _ui.value.copy(loading = false, errorCode = r.message)
            }
        }
    }

    // ── Logout (used from anywhere once authenticated) ────────────────────────

    fun logout() = viewModelScope.launch { auth.logout() }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateRegisterForm(): String? {
        if (regUsername.trim().length < 3) return "username_too_short"
        if (regPassword.length < 6) return "password_too_short"
        if (regInviteCode.isBlank()) return "invite_required"
        return null
    }
}
