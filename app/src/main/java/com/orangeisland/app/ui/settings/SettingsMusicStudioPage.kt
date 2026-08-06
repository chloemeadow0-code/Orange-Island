package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangeisland.app.R
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMusicStudioPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val enabled by viewModel.settings.musicStudioEnabled.collectAsStateWithLifecycle()
    val provider by viewModel.settings.musicStudioProvider.collectAsStateWithLifecycle()
    val sunoUrl by viewModel.settings.musicStudioSunoApiUrl.collectAsStateWithLifecycle()
    val sunoKey by viewModel.settings.musicStudioSunoApiKey.collectAsStateWithLifecycle()
    val replicateKey by viewModel.settings.musicStudioReplicateApiKey.collectAsStateWithLifecycle()
    val replicateModelVersion by viewModel.settings.musicStudioReplicateModelVersion.collectAsStateWithLifecycle()
    val rvcModelUrl by viewModel.settings.musicStudioRvcModelUrl.collectAsStateWithLifecycle()
    val voiceReplacementEnabled by viewModel.settings.musicStudioVoiceReplacementEnabled.collectAsStateWithLifecycle()

    var sunoUrlDraft by remember { mutableStateOf(sunoUrl) }
    var sunoKeyDraft by remember { mutableStateOf(sunoKey) }
    var replicateKeyDraft by remember { mutableStateOf(replicateKey) }
    var replicateModelDraft by remember { mutableStateOf(replicateModelVersion) }
    var rvcModelUrlDraft by remember { mutableStateOf(rvcModelUrl) }
    var showSunoKey by remember { mutableStateOf(false) }
    var showReplicateKey by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    val providers = listOf("suno" to "Suno", "replicate" to "Replicate")

    CollapsingSettingsScaffold(
        title = stringResource(R.string.music_studio_title),
        onBack = onBack,
        floatingActionButton = { DocumentationFab("music_studio.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.music_studio_group_general),
                items = listOf({
                    SettingsItem(
                        headlineContent = { Text(stringResource(R.string.music_studio_enable)) },
                        supportingContent = { Text(stringResource(R.string.music_studio_enable_desc)) },
                        leadingContent = { IslandIcon(IslandIcons.VoiceSynthesis, size = 38.dp) },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { viewModel.settings.setMusicStudioEnabled(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.settings.setMusicStudioEnabled(!enabled) }
                    )
                })
            )

            if (enabled) {
                SettingsGroup(
                    title = stringResource(R.string.music_studio_group_provider),
                    items = listOf({
                        ExposedDropdownMenuBox(
                            expanded = providerExpanded,
                            onExpandedChange = { providerExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = providers.find { it.first == provider }?.second ?: provider,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.music_studio_provider)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            DropdownMenu(
                                expanded = providerExpanded,
                                onDismissRequest = { providerExpanded = false }
                            ) {
                                providers.forEach { (id, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.settings.setMusicStudioProvider(id)
                                            providerExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    })
                )

                if (provider == "suno") {
                    SettingsGroup(
                        title = "Suno",
                        items = listOf({
                            OutlinedTextField(
                                value = sunoUrlDraft,
                                onValueChange = {
                                    sunoUrlDraft = it
                                    viewModel.settings.setMusicStudioSunoApiUrl(it)
                                },
                                label = { Text(stringResource(R.string.music_studio_suno_url)) },
                                leadingIcon = { Icon(Icons.Default.Link, null) },
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }, {
                            OutlinedTextField(
                                value = sunoKeyDraft,
                                onValueChange = {
                                    sunoKeyDraft = it
                                    viewModel.settings.setMusicStudioSunoApiKey(it)
                                },
                                label = { Text(stringResource(R.string.music_studio_suno_key)) },
                                leadingIcon = { Icon(Icons.Default.Key, null) },
                                visualTransformation = if (showSunoKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    TextButton(onClick = { showSunoKey = !showSunoKey }) {
                                        Text(stringResource(if (showSunoKey) R.string.hide else R.string.show))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        })
                    )
                }

                SettingsGroup(
                    title = stringResource(R.string.music_studio_group_voice_replacement),
                    items = listOf({
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.settings.setMusicStudioVoiceReplacementEnabled(!voiceReplacementEnabled) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.music_studio_voice_replacement),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.music_studio_voice_replacement_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = voiceReplacementEnabled,
                                onCheckedChange = { viewModel.settings.setMusicStudioVoiceReplacementEnabled(it) }
                            )
                        }
                    }, {
                        OutlinedTextField(
                            value = replicateKeyDraft,
                            onValueChange = {
                                replicateKeyDraft = it
                                viewModel.settings.setMusicStudioReplicateApiKey(it)
                            },
                            label = { Text(stringResource(R.string.music_studio_replicate_key)) },
                            leadingIcon = { Icon(Icons.Default.Key, null) },
                            visualTransformation = if (showReplicateKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                TextButton(onClick = { showReplicateKey = !showReplicateKey }) {
                                    Text(stringResource(if (showReplicateKey) R.string.hide else R.string.show))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }, {
                        OutlinedTextField(
                            value = replicateModelDraft,
                            onValueChange = {
                                replicateModelDraft = it
                                viewModel.settings.setMusicStudioReplicateModelVersion(it)
                            },
                            label = { Text(stringResource(R.string.music_studio_replicate_model_version)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }, {
                        OutlinedTextField(
                            value = rvcModelUrlDraft,
                            onValueChange = {
                                rvcModelUrlDraft = it
                                viewModel.settings.setMusicStudioRvcModelUrl(it)
                            },
                            label = { Text(stringResource(R.string.music_studio_rvc_model_url)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    })
                )
            }
        }
    }
}
