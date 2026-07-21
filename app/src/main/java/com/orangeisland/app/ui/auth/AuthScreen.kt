package com.orangeisland.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R

/**
 * Root composable for the unauthenticated gate. Branches on [AuthViewModel]'s
 * current mode (login / register). On success the [isLoggedIn] flag flips and
 * MainActivity's recomposition takes the user into the main app.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.auth_app_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.auth_app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    when (state.mode) {
                        AuthMode.LOGIN -> LoginPane(viewModel, state)
                        AuthMode.REGISTER -> RegisterPane(viewModel, state)
                    }
                }
            }
        }
    }

    // One-shot error/info surface.
    state.errorCode?.let { code ->
        ErrorInfoDialog(
            message = authString(code),
            isError = true,
            onDismiss = { viewModel.clearError() }
        )
    }
    state.infoCode?.let { code ->
        ErrorInfoDialog(
            message = authString(code),
            isError = false,
            onDismiss = { viewModel.clearError() }
        )
    }
}

@Composable
private fun ErrorInfoDialog(message: String, isError: Boolean, onDismiss: () -> Unit) {
    if (message.isBlank()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (isError) R.string.auth_error_title else R.string.auth_info_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.auth_ok)) } }
    )
}

/** Translate a backend/UI error code into a localized user-facing string. */
@Composable
fun authString(code: String): String {
    val res = when (code) {
        // generic
        "fields_required" -> R.string.auth_err_fields_required
        "network_error" -> R.string.auth_err_network
        "auth_error" -> R.string.auth_err_generic
        // register
        "username_too_short" -> R.string.auth_err_username_short
        "username_taken" -> R.string.auth_err_username_taken
        "password_too_short" -> R.string.auth_err_password_short
        "invite_required" -> R.string.auth_err_invite_required
        "invalid_invitation_code" -> R.string.auth_err_invite_invalid
        // login
        "wrong_credentials" -> R.string.auth_err_wrong_credentials
        "email_provider_disabled" -> R.string.auth_err_email_provider_disabled
        "signup_disabled" -> R.string.auth_err_signup_disabled
        "email_not_confirmed_in_dashboard" -> R.string.auth_err_email_not_confirmed
        else -> R.string.auth_err_generic
    }
    return stringResource(res)
}
