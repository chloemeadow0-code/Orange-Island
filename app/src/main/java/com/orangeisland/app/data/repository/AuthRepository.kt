package com.orangeisland.app.data.repository

import com.orangeisland.app.BuildConfig
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.UsageLogManager
import com.orangeisland.app.util.DebugLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Result of an auth operation. UI consumes [error] for toasts.
 */
sealed interface AuthResult {
    data object Success : AuthResult
    /** A user-readable failure (bad code, wrong password, network, …). */
    data class Error(val message: String, val fatal: Boolean = false) : AuthResult
}

/**
 * Bridges Supabase Auth + Postgrest with the local session mirror in [SettingsManager].
 *
 * This app does NOT collect real emails. Supabase Auth is email-only at the protocol
 * level, so we synthesize a deterministic fake address from the username:
 *
 *     alice  →  alice@users.orangeisland.local
 *
 * Email confirmation MUST be turned off in Supabase Studio (Authentication →
 * Sign In / Providers → Email → Confirm email = OFF), so sign-up returns a session
 * immediately and no email is ever sent.
 *
 * Flow:
 *   Register: [register]  — check invite (non-consuming) → signUpWith(Email) →
 *                            complete_registration RPC (consume invite + write profile)
 *                            → set local session
 *   Login:    [login]     — signInWith(Email, password) using the synthesized address
 *   Logout:   [logout]
 */
class AuthRepository(
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope
) {
    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }

    /**
     * Logged-in flag for the MainActivity gate. Backed by the local DataStore flag,
     * which is fast and survives process restart.
     */
    val isLoggedIn: StateFlow<Boolean> = settingsManager.loggedIn
        .stateIn(scope, SharingStarted.Eagerly, false)

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * One-shot registration. Validates the invite (non-consuming) first so the
     * user gets instant feedback, then signs up with the synthesized email and
     * the user's password, and finally calls `complete_registration` which
     * atomically consumes the invite slot and writes the profile row.
     */
    suspend fun register(username: String, password: String, inviteCode: String): AuthResult {
        val uname = username.trim()
        if (uname.length < 3) return AuthResult.Error("username_too_short")
        try {
            // 1. Invite still valid + not exhausted (non-consuming pre-check).
            val ok = supabase.postgrest.rpc(
                function = "check_invitation_code",
                parameters = buildJsonObject {
                    put("p_code", JsonPrimitive(inviteCode))
                }
            ).decodeAs<Boolean>()
            if (!ok) return AuthResult.Error("invalid_invitation_code")

            // 2. Create the auth account (synthesized email; confirmation OFF →
            //    Supabase returns a session right away).
            val synthEmail = synthEmail(uname)
            supabase.auth.signUpWith(Email) {
                this.email = synthEmail
                this.password = password
            }

            // 3. Atomically consume the invite and persist the profile row.
            //    If this fails (e.g. invite raced to exhaustion), the half-created
            //    auth user is left for the backoffice to clean up — the client
            //    surfaces the error and signs out.
            supabase.postgrest.rpc(
                function = "complete_registration",
                parameters = buildJsonObject {
                    put("p_username", JsonPrimitive(uname))
                    put("p_invite_code", JsonPrimitive(inviteCode))
                }
            )

            settingsManager.saveAuthSession(loggedIn = true, userName = uname, userEmail = synthEmail)
            UsageLogManager.log(
                UsageLogManager.Type.SYNC,
                "auth_register",
                "username=$uname"
            )
            return AuthResult.Success
        } catch (e: Exception) {
            DebugLog.e(TAG, "register failed", e)
            // Best-effort cleanup so the user can retry without a phantom account lingering.
            runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
            return AuthResult.Error(translateAuthError(e))
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /** Username + password login. Username is mapped to the synthesized email. */
    suspend fun login(username: String, password: String): AuthResult {
        try {
            val uname = username.trim()
            supabase.auth.signInWith(Email) {
                this.email = synthEmail(uname)
                this.password = password
            }
            settingsManager.saveAuthSession(loggedIn = true, userName = uname, userEmail = synthEmail(uname))
            UsageLogManager.log(
                UsageLogManager.Type.SYNC,
                "auth_login",
                "username=$uname"
            )
            return AuthResult.Success
        } catch (e: Exception) {
            DebugLog.e(TAG, "login failed", e)
            return AuthResult.Error(translateAuthError(e))
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    suspend fun logout() {
        runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
        settingsManager.clearAuthSession()
        UsageLogManager.log(
            UsageLogManager.Type.SYNC,
            "auth_logout",
            ""
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Deterministically map a username to a fake in-app email. */
    private fun synthEmail(username: String): String =
        username.trim().lowercase() + SYNTH_EMAIL_SUFFIX

    /** Map Supabase/ktor exceptions to a short error code the UI translates. */
    private fun translateAuthError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("email_provider_disabled", true) -> "email_provider_disabled"
            msg.contains("signup_disabled", true) -> "signup_disabled"
            msg.contains("Invalid login", true) -> "wrong_credentials"
            msg.contains("already", true) &&
                (msg.contains("registered", true) || msg.contains("exists", true)) -> "username_taken"
            msg.contains("Email not confirmed", true) -> "email_not_confirmed_in_dashboard"
            msg.contains("network", true) || msg.contains("failed to connect", true) -> "network_error"
            else -> "auth_error"
        }
    }

    @Serializable
    private data class ProfileRow(val username: String = "", val email: String = "")

    private companion object {
        const val TAG = "AuthRepository"
        const val SYNTH_EMAIL_SUFFIX = "@users.orangeisland.local"
    }
}
