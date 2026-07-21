package com.orangeisland.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R

// ── Shared field ─────────────────────────────────────────────────────────────

/**
 * Outlined text field with an above label — mirrors the [ProxyLabeledField] style
 * used in SettingsProxyPage. Keeps a local [draft] so typing isn't reset when the
 * backing store emits.
 */
@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true
) {
    var draft by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != draft) draft = value }
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; onValueChange(it) },
            placeholder = placeholder?.let { ph -> { Text(ph, style = MaterialTheme.typography.bodyMedium) } },
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Login ────────────────────────────────────────────────────────────────────

@Composable
fun LoginPane(viewModel: AuthViewModel, state: AuthUiState) {
    AuthField(
        label = stringResource(R.string.auth_username),
        value = viewModel.loginUsername,
        onValueChange = { viewModel.loginUsername = it }
    )
    Spacer(Modifier.height(12.dp))
    AuthField(
        label = stringResource(R.string.auth_password),
        value = viewModel.loginPassword,
        onValueChange = { viewModel.loginPassword = it },
        password = true,
        keyboard = KeyboardType.Password
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = viewModel::login,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.auth_login))
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(
        onClick = { viewModel.setMode(AuthMode.REGISTER) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.auth_no_account))
    }
}

// ── Register ─────────────────────────────────────────────────────────────────

@Composable
fun RegisterPane(viewModel: AuthViewModel, state: AuthUiState) {
    AuthField(
        label = stringResource(R.string.auth_username),
        value = viewModel.regUsername,
        onValueChange = { viewModel.regUsername = it }
    )
    Spacer(Modifier.height(12.dp))
    AuthField(
        label = stringResource(R.string.auth_password),
        value = viewModel.regPassword,
        onValueChange = { viewModel.regPassword = it },
        password = true,
        keyboard = KeyboardType.Password
    )
    Spacer(Modifier.height(12.dp))
    AuthField(
        label = stringResource(R.string.auth_invite_code),
        value = viewModel.regInviteCode,
        onValueChange = { viewModel.regInviteCode = it.uppercase() },
        placeholder = stringResource(R.string.auth_invite_code_placeholder)
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = viewModel::register,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.auth_register))
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(
        onClick = { viewModel.setMode(AuthMode.LOGIN) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.auth_have_account))
    }
}
