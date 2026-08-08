package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
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
    val sunoUrl by viewModel.settings.musicStudioSunoApiUrl.collectAsStateWithLifecycle()
    val sunoKey by viewModel.settings.musicStudioSunoApiKey.collectAsStateWithLifecycle()

    var sunoUrlDraft by remember { mutableStateOf(sunoUrl) }
    var sunoKeyDraft by remember { mutableStateOf(sunoKey) }
    var showSunoKey by remember { mutableStateOf(false) }

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
                // Suno is the only generation provider.
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

                // Voice replacement (RVC) config lives on the per-track detail page now, not here.
            }
        }
    }
}
