package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAutoCompressPage(viewModel: ChatViewModel, onBack: () -> Unit, onNavigateToGeneration: () -> Unit = {}) {
    val autoCompressEnabled by viewModel.settings.autoCompressEnabled.collectAsState()
    val autoCompressModel by viewModel.settings.autoCompressModel.collectAsState()
    val autoCompressPrompt by viewModel.settings.autoCompressPrompt.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val enabledModels by viewModel.settings.enabledModels.collectAsState()
    val maxContextWindow by viewModel.settings.maxContextWindow.collectAsState()
    var showModelDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_auto_compress),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("generation.md") }
    ) {
            SettingsGroupColumn {
                SettingsGroup(
                    title = stringResource(R.string.settings_auto_compress),
                    items = buildList {
                        add {
                            val autoCompressDesc = if (maxContextWindow == Int.MAX_VALUE) {
                                stringResource(R.string.auto_compress_auto_desc_unlimited)
                            } else {
                                stringResource(R.string.auto_compress_auto_desc, maxContextWindow)
                            }
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.auto_compress_auto)) },
                                supportingContent = { Text(autoCompressDesc) },
                                leadingContent = { Icon(Icons.Default.Compress, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    Switch(checked = autoCompressEnabled, onCheckedChange = { viewModel.settings.setAutoCompressEnabled(it) })
                                },
                                modifier = Modifier.clickable { viewModel.settings.setAutoCompressEnabled(!autoCompressEnabled) }
                            )
                        }
                        if (autoCompressEnabled) {
                            add {
                                val thresholdDesc = if (maxContextWindow == Int.MAX_VALUE) {
                                    stringResource(R.string.context_retain_unlimited)
                                } else {
                                    stringResource(R.string.context_retain, maxContextWindow)
                                }
                                SettingsItem(
                                    headlineContent = { Text(stringResource(R.string.auto_compress_threshold_source)) },
                                    supportingContent = { Text(thresholdDesc) },
                                    leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    },
                                    modifier = Modifier.clickable { onNavigateToGeneration() }
                                )
                            }
                        }
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.auto_compress_model)) },
                                supportingContent = {
                                    val displayName = if (autoCompressModel == null) stringResource(R.string.auto_compress_current_model) else {
                                        val alias = modelAliases[autoCompressModel!!]
                                        alias ?: com.orangeisland.app.model.ModelId.parse(autoCompressModel!!).apiModelName
                                    }
                                    Text(displayName)
                                },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { showModelDialog = true }
                            )
                        }
                    }
                )
                SettingsGroup(
                    title = stringResource(R.string.advanced_title),
                    items = listOf({
                        PromptSettingItem(
                            title = stringResource(R.string.auto_compress_prompt),
                            description = stringResource(R.string.auto_compress_prompt_desc),
                            prompt = autoCompressPrompt,
                            onClick = { showPromptDialog = true }
                        )
                    })
                )
            }
            if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showModelDialog) {
        val enabledModelsList = enabledModels.toList()
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.auto_compress_select_model), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.auto_compress_current_model), fontWeight = if (autoCompressModel == null) FontWeight.Bold else FontWeight.Normal) },
                            leadingContent = {
                                RadioButton(selected = autoCompressModel == null, onClick = {
                                    viewModel.settings.setAutoCompressModel(null)
                                    showModelDialog = false
                                })
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setAutoCompressModel(null)
                                showModelDialog = false
                            }
                        )
                    }
                    items(enabledModelsList) { model ->
                        val alias = modelAliases[model]
                        val titleParsed = com.orangeisland.app.model.ModelId.parse(model)
                        val displayName = alias ?: titleParsed.apiModelName
                        SettingsItem(
                            headlineContent = { Text(displayName, fontWeight = if (autoCompressModel == model) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text(titleParsed.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                            leadingContent = {
                                RadioButton(selected = autoCompressModel == model, onClick = {
                                    viewModel.settings.setAutoCompressModel(model)
                                    showModelDialog = false
                                })
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setAutoCompressModel(model)
                                showModelDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelDialog = false }) { Text(stringResource(R.string.provider_cancel)) } }
        )
    }

    if (showPromptDialog) {
        PromptEditDialog(
            title = stringResource(R.string.auto_compress_prompt),
            initialPrompt = autoCompressPrompt,
            onDismiss = { showPromptDialog = false },
            onSave = { viewModel.settings.setAutoCompressPrompt(it) }
        )
    }
}
