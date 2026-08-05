package com.orangeisland.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.components.providerIcon
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.util.Constants
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsVideoNarrationPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val narrationEnabledModels by viewModel.settings.videoNarrationEnabledModels.collectAsState()
    val narrationModel by viewModel.settings.videoNarrationModel.collectAsState()
    val narrationPrompt by viewModel.settings.videoNarrationPrompt.collectAsState()
    val fps by viewModel.settings.videoNarrationFps.collectAsState()
    val detail by viewModel.settings.videoNarrationDetail.collectAsState()
    val maxLongSide by viewModel.settings.videoNarrationMaxLongSide.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val enabledModels by viewModel.settings.enabledModels.collectAsState()
    var showModelDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showMenuForModel by remember { mutableStateOf<String?>(null) }
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    val availableToAdd = remember(enabledModels, narrationEnabledModels) {
        enabledModels.filter { it !in narrationEnabledModels }.sortedBy { it }
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_video_narration),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("transcription.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.video_narration_model),
                items = listOf({
                    val displayName = narrationModel?.let {
                        val parsed = com.orangeisland.app.model.ModelId.parse(it)
                        val alias = modelAliases[it]
                        alias ?: parsed.apiModelName
                    } ?: stringResource(R.string.video_narration_no_model)
                    val selectedProvider = narrationModel?.let { com.orangeisland.app.model.ModelId.parse(it).providerName }
                    val selectedIconRes = selectedProvider?.let { providerIcon(it) } ?: 0
                    val isSelectedLocal = selectedProvider.equals(Constants.PROVIDER_LOCAL, ignoreCase = true)
                    SettingsItem(
                        headlineContent = {
                            Text(
                                displayName,
                                color = if (narrationModel == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = if (narrationModel != null) {
                            { Text(selectedProvider ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                        } else null,
                        leadingContent = {
                            when {
                                narrationModel == null -> Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                isSelectedLocal -> Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                selectedIconRes != 0 -> Icon(painterResource(selectedIconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                else -> Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        },
                        modifier = Modifier.heightIn(min = 64.dp).clickable(enabled = enabledModels.isNotEmpty()) { showModelDialog = true }
                    )
                })
            )

            SettingsGroup(
                title = stringResource(R.string.video_narration_enabled_models),
                items = buildList {
                    if (narrationEnabledModels.isEmpty()) {
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.video_narration_no_models_enabled), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                supportingContent = { Text(stringResource(R.string.video_narration_no_models_hint), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                leadingContent = { Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                                modifier = Modifier.heightIn(min = 64.dp)
                            )
                        }
                    } else {
                        val sorted = narrationEnabledModels.toList().sortedBy { it }
                        for (model in sorted) {
                            val alias = modelAliases[model]
                            val parsedModel = com.orangeisland.app.model.ModelId.parse(model)
                            val displayName = alias ?: parsedModel.apiModelName
                            val providerName = parsedModel.providerName
                            add {
                                val iconRes = providerIcon(providerName)
                                val isLocal = providerName.equals(Constants.PROVIDER_LOCAL, ignoreCase = true)
                                SettingsItem(
                                    headlineContent = { Text(displayName) },
                                    supportingContent = { Text(providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                    leadingContent = {
                                        when {
                                            isLocal -> Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                            iconRes != 0 -> Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                            else -> Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        }
                                    },
                                    trailingContent = {
                                        Box {
                                            IconButton(onClick = { showMenuForModel = model }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options), modifier = Modifier.size(18.dp))
                                            }
                                            DropdownMenu(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                tonalElevation = 16.dp,
                                                expanded = showMenuForModel == model,
                                                onDismissRequest = { showMenuForModel = null },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        showMenuForModel = null
                                                        viewModel.settings.removeVideoNarrationModel(model)
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable { showMenuForModel = model }
                                )
                            }
                        }
                    }
                    if (availableToAdd.isNotEmpty()) {
                        add {
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { showAddDialog = true }.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.video_narration_add_model), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            )

            SettingsGroup(
                title = stringResource(R.string.video_narration_options),
                items = buildList {
                    add {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.video_narration_detail),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("low", "default", "high").forEach { d ->
                                    val selected = detail == d
                                    val shape = RoundedCornerShape(50)
                                    val bg by animateColorAsState(
                                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        animationSpec = tween(300)
                                    )
                                    val tc by animateColorAsState(
                                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        animationSpec = tween(300)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(shape)
                                            .background(bg, shape = shape)
                                            .clickable { viewModel.settings.setVideoNarrationDetail(d) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(d.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.labelLarge, color = tc)
                                    }
                                }
                            }
                        }
                    }
                    add {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.video_narration_fps),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "%.1f".format(fps),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.video_narration_fps_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Slider(
                                        value = fps,
                                        onValueChange = { viewModel.settings.setVideoNarrationFps(it) },
                                        valueRange = 0.2f..5f,
                                        steps = 47,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    add {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.video_narration_max_long_side),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = maxLongSide.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.video_narration_max_long_side_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Slider(
                                        value = maxLongSide.toFloat(),
                                        onValueChange = { viewModel.settings.setVideoNarrationMaxLongSide(it.toInt()) },
                                        valueRange = 320f..2048f,
                                        steps = 17,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            )

            SettingsGroup(
                title = stringResource(R.string.advanced_title),
                items = listOf {
                    PromptSettingItem(
                        title = stringResource(R.string.video_narration_prompt),
                        description = stringResource(R.string.video_narration_prompt_desc),
                        prompt = narrationPrompt,
                        onClick = { showPromptDialog = true }
                    )
                }
            )
        }
        if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showModelDialog) {
        val enabledModelsList = enabledModels.toList()
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.video_narration_select_model), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(enabledModelsList) { model ->
                        val alias = modelAliases[model]
                        val dialogParsed = com.orangeisland.app.model.ModelId.parse(model)
                        val displayName = alias ?: dialogParsed.apiModelName
                        SettingsItem(
                            headlineContent = { Text(displayName, fontWeight = if (narrationModel == model) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text(dialogParsed.providerName, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = {
                                RadioButton(selected = narrationModel == model, onClick = {
                                    viewModel.settings.setVideoNarrationModel(model)
                                    showModelDialog = false
                                })
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setVideoNarrationModel(model)
                                showModelDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelDialog = false }) { Text(stringResource(R.string.provider_cancel)) } }
        )
    }

    if (showAddDialog) {
        var selected by remember { mutableStateOf(emptySet<String>()) }
        val availableList = availableToAdd
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.video_narration_add_model), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(availableList) { model ->
                        val alias = modelAliases[model]
                        val addParsed = com.orangeisland.app.model.ModelId.parse(model)
                        val displayName = alias ?: addParsed.apiModelName
                        val checked = model in selected
                        SettingsItem(
                            headlineContent = { Text(displayName) },
                            supportingContent = { Text(addParsed.providerName, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selected = if (checked) selected - model else selected + model
                                })
                            },
                            modifier = Modifier.clickable {
                                selected = if (checked) selected - model else selected + model
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.settings.addVideoNarrationModels(selected)
                    showAddDialog = false
                }) { Text(stringResource(R.string.provider_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.provider_cancel)) }
            }
        )
    }

    if (showPromptDialog) {
        PromptEditDialog(
            title = stringResource(R.string.video_narration_prompt),
            initialPrompt = narrationPrompt,
            onDismiss = { showPromptDialog = false },
            onSave = { viewModel.settings.setVideoNarrationPrompt(it) }
        )
    }
}
