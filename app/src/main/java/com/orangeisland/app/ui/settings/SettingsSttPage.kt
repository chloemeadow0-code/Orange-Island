package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

/**
 * Settings page for the SiliconFlow Speech-to-Text service that powers the AI voice-call feature.
 * Mirrors [SettingsTtsPage] structure-for-structure (same composables, same debounced-commit
 * pattern) so the two provider pages look and behave identically.
 */
@Composable
fun SettingsSttPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val enabled by viewModel.settings.sttEnabled.collectAsState()
    val apiKey by viewModel.settings.sttApiKey.collectAsState()
    val model by viewModel.settings.sttModel.collectAsState()
    val baseUrl by viewModel.settings.sttBaseUrl.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    var apiKeyDraft by remember { mutableStateOf(apiKey) }
    var modelDraft by remember { mutableStateOf(model) }
    var baseUrlDraft by remember { mutableStateOf(baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey) { apiKeyDraft = apiKey }
    LaunchedEffect(model) { modelDraft = model }
    LaunchedEffect(baseUrl) { baseUrlDraft = baseUrl }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_stt),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("stt.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.tts_group_general),
                items = listOf({
                    SettingsItem(
                        headlineContent = { Text(stringResource(R.string.stt_enable)) },
                        supportingContent = { Text(stringResource(R.string.stt_enable_desc)) },
                        leadingContent = { Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = { viewModel.settings.setSttEnabled(it) })
                        },
                        modifier = Modifier.clickable { viewModel.settings.setSttEnabled(!enabled) }
                    )
                })
            )

            if (enabled) {
                SettingsGroup(
                    title = stringResource(R.string.tts_group_provider),
                    items = listOf(
                        {
                            // API Key
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.stt_api_key),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = apiKeyDraft,
                                            onValueChange = { apiKeyDraft = it },
                                            placeholder = { Text(stringResource(R.string.stt_api_key_hint)) },
                                            singleLine = true,
                                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            trailingIcon = {
                                                TextButton(onClick = { showApiKey = !showApiKey }) {
                                                    Text(if (showApiKey) stringResource(R.string.hide) else stringResource(R.string.show))
                                                }
                                            }
                                        )
                                        LaunchedEffect(apiKeyDraft) {
                                            delay(500)
                                            if (apiKeyDraft != apiKey) {
                                                viewModel.settings.setSttApiKey(apiKeyDraft)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        {
                            // Model
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.stt_model),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.stt_model_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                        )
                                        OutlinedTextField(
                                            value = modelDraft,
                                            onValueChange = { modelDraft = it },
                                            placeholder = { Text(stringResource(R.string.stt_model_hint)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                        LaunchedEffect(modelDraft) {
                                            delay(500)
                                            if (modelDraft != model) {
                                                viewModel.settings.setSttModel(modelDraft)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        {
                            // Base URL
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Api, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.stt_base_url),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.stt_base_url_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                        )
                                        OutlinedTextField(
                                            value = baseUrlDraft,
                                            onValueChange = { baseUrlDraft = it },
                                            placeholder = { Text(stringResource(R.string.stt_base_url_hint)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                        LaunchedEffect(baseUrlDraft) {
                                            delay(500)
                                            if (baseUrlDraft != baseUrl) {
                                                viewModel.settings.setSttBaseUrl(baseUrlDraft)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                )
            }
        }
    }
}
