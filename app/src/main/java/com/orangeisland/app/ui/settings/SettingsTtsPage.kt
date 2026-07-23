package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
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

@Composable
fun SettingsTtsPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val enabled by viewModel.settings.ttsEnabled.collectAsState()
    val provider by viewModel.settings.ttsProvider.collectAsState()
    val apiKey by viewModel.settings.ttsApiKey.collectAsState()
    val voiceId by viewModel.settings.ttsVoiceId.collectAsState()
    val model by viewModel.settings.ttsModel.collectAsState()
    val speed by viewModel.settings.ttsSpeed.collectAsState()
    val outputFormat by viewModel.settings.ttsOutputFormat.collectAsState()
    val stability by viewModel.settings.ttsStability.collectAsState()
    val similarityBoost by viewModel.settings.ttsSimilarityBoost.collectAsState()
    val style by viewModel.settings.ttsStyle.collectAsState()
    val volume by viewModel.settings.ttsVolume.collectAsState()
    val pitch by viewModel.settings.ttsPitch.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    var apiKeyDraft by remember { mutableStateOf(apiKey) }
    var voiceIdDraft by remember { mutableStateOf(voiceId) }
    var showApiKey by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var formatExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey) { apiKeyDraft = apiKey }
    LaunchedEffect(voiceId) { voiceIdDraft = voiceId }

    val isElevenLabs = provider.lowercase() == "elevenlabs"

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_tts),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("tts.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.tts_group_general),
                items = listOf({
                    SettingsItem(
                        headlineContent = { Text(stringResource(R.string.tts_enable)) },
                        supportingContent = { Text(stringResource(R.string.tts_enable_desc)) },
                        leadingContent = { Icon(Icons.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = { viewModel.settings.setTtsEnabled(it) })
                        },
                        modifier = Modifier.clickable { viewModel.settings.setTtsEnabled(!enabled) }
                    )
                })
            )

            if (enabled) {
                SettingsGroup(
                    title = stringResource(R.string.tts_group_provider),
                    items = listOf(
                        {
                            // Provider selector
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.tts_provider)) },
                                supportingContent = { Text(stringResource(R.string.tts_provider_desc)) },
                                leadingContent = { Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    Box {
                                        Text(
                                            when (provider.lowercase()) {
                                                "elevenlabs" -> "ElevenLabs"
                                                "minimax" -> "MiniMax"
                                                else -> provider
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { providerExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = providerExpanded,
                                            onDismissRequest = { providerExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("ElevenLabs") },
                                                onClick = {
                                                    viewModel.settings.setTtsProvider("elevenlabs")
                                                    providerExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("MiniMax") },
                                                onClick = {
                                                    viewModel.settings.setTtsProvider("minimax")
                                                    providerExpanded = false
                                                }
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { providerExpanded = true }
                            )
                        },
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
                                            stringResource(R.string.tts_api_key),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = apiKeyDraft,
                                            onValueChange = { apiKeyDraft = it },
                                            placeholder = { Text(stringResource(R.string.tts_api_key_hint)) },
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
                                                viewModel.settings.setTtsApiKey(apiKeyDraft)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        {
                            // Voice ID
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.tts_voice_id),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.tts_voice_id_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                        )
                                        OutlinedTextField(
                                            value = voiceIdDraft,
                                            onValueChange = { voiceIdDraft = it },
                                            placeholder = { Text(stringResource(R.string.tts_voice_id_hint)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                        LaunchedEffect(voiceIdDraft) {
                                            delay(500)
                                            if (voiceIdDraft != voiceId) {
                                                viewModel.settings.setTtsVoiceId(voiceIdDraft)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        {
                            // Model input (free text so users can enter any new model ID)
                            var modelDraft by remember { mutableStateOf(model) }
                            LaunchedEffect(model) { modelDraft = model }
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
                                            stringResource(R.string.tts_model),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.tts_model_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                        )
                                        OutlinedTextField(
                                            value = modelDraft,
                                            onValueChange = { modelDraft = it },
                                            placeholder = {
                                                Text(
                                                    if (isElevenLabs)
                                                        stringResource(R.string.tts_model_hint_elevenlabs)
                                                    else
                                                        stringResource(R.string.tts_model_hint_minimax)
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                        LaunchedEffect(modelDraft) {
                                            delay(500)
                                            if (modelDraft != model) {
                                                viewModel.settings.setTtsModel(modelDraft)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        {
                            // Speed slider
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        stringResource(R.string.tts_speed),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${"%.1f".format(speed)}x",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = speed,
                                    onValueChange = { viewModel.settings.setTtsSpeed(it) },
                                    valueRange = if (isElevenLabs) 0.7f..1.2f else 0.5f..2.0f,
                                    steps = if (isElevenLabs) 4 else 14,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        {
                            // Output format selector
                            val formats = if (isElevenLabs) listOf(
                                "" to stringResource(R.string.tts_format_default),
                                "mp3_44100_128" to "MP3 128kbps",
                                "mp3_44100_192" to "MP3 192kbps",
                                "mp3_44100_64" to "MP3 64kbps",
                                "mp3_44100_32" to "MP3 32kbps",
                                "pcm_24000" to "PCM 24kHz",
                                "pcm_16000" to "PCM 16kHz"
                            ) else listOf(
                                "" to stringResource(R.string.tts_format_default),
                                "mp3" to "MP3",
                                "wav" to "WAV",
                                "pcm" to "PCM",
                                "ogg" to "OGG"
                            )
                            val currentLabel = formats.find { it.first == outputFormat }?.second ?: outputFormat
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.tts_output_format)) },
                                supportingContent = { Text(stringResource(R.string.tts_output_format_desc)) },
                                leadingContent = { Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    Box {
                                        Text(
                                            currentLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { formatExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = formatExpanded,
                                            onDismissRequest = { formatExpanded = false }
                                        ) {
                                            formats.forEach { (id, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        viewModel.settings.setTtsOutputFormat(id)
                                                        formatExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { formatExpanded = true }
                            )
                        }
                    )
                )

                // Provider-specific advanced settings
                if (isElevenLabs) {
                    SettingsGroup(
                        title = stringResource(R.string.tts_group_voice_settings),
                        items = listOf(
                            {
                                FloatSliderItem(
                                    label = stringResource(R.string.tts_stability),
                                    value = stability,
                                    range = 0f..1f,
                                    steps = 19,
                                    onValueChange = { viewModel.settings.setTtsStability(it) }
                                )
                            },
                            {
                                FloatSliderItem(
                                    label = stringResource(R.string.tts_similarity_boost),
                                    value = similarityBoost,
                                    range = 0f..1f,
                                    steps = 19,
                                    onValueChange = { viewModel.settings.setTtsSimilarityBoost(it) }
                                )
                            },
                            {
                                FloatSliderItem(
                                    label = stringResource(R.string.tts_style),
                                    value = style,
                                    range = 0f..1f,
                                    steps = 19,
                                    onValueChange = { viewModel.settings.setTtsStyle(it) }
                                )
                            }
                        )
                    )
                } else {
                    SettingsGroup(
                        title = stringResource(R.string.tts_group_voice_settings),
                        items = listOf(
                            {
                                FloatSliderItem(
                                    label = stringResource(R.string.tts_volume),
                                    value = volume,
                                    range = 0.1f..10.0f,
                                    steps = 98,
                                    onValueChange = { viewModel.settings.setTtsVolume(it) }
                                )
                            },
                            {
                                FloatSliderItem(
                                    label = stringResource(R.string.tts_pitch),
                                    value = pitch,
                                    range = -12f..12f,
                                    steps = 23,
                                    onValueChange = { viewModel.settings.setTtsPitch(it) }
                                )
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatSliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${"%.2f".format(value)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
