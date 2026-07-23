package com.orangeisland.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.ModelId
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.ui.components.providerIcon
import com.orangeisland.app.util.Constants
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModelsPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val enabledModels by viewModel.settings.enabledModels.collectAsState()
    val availableModels by viewModel.settings.availableModels.collectAsState()
    val manualModels by viewModel.settings.manualModels.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val selectedModel by viewModel.settings.selectedModel.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    var showActiveModelDialog by remember { mutableStateOf(false) }
    var subScreen by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler {
        if (subScreen != null) {
            subScreen = null
        } else {
            onBack()
        }
    }

    GuardedAnimatedContent(
        targetState = subScreen,
        forward = subScreen != null
    ) { screen ->
        when (screen) {
            "available" -> SettingsAvailableModelsPage(
                viewModel = viewModel,
                onBack = { subScreen = null }
            )
            "multimodal" -> SettingsMultimodalModelsPage(
                viewModel = viewModel,
                onBack = { subScreen = null }
            )
            else -> {
                CollapsingSettingsLazyScaffold(
                    title = stringResource(R.string.models_title),
                    onBack = onBack,
                    floatingActionButton = {
                        if (showDocFab) DocumentationFab("models.md")
                    }
                ) {
                    // ── Default Model section ──
                    item(key = "section_default") {
                        val activeAlias = modelAliases[selectedModel]
                        val activeParsed = ModelId.parse(selectedModel)
                        val providerName = activeParsed.providerName
                        val activeDisplayName = activeAlias ?: activeParsed.apiModelName
                        val activeIconRes = providerIcon(providerName)
                        val isActiveLocal = providerName.equals(Constants.PROVIDER_LOCAL, ignoreCase = true)
                        val hasEnabledModels = enabledModels.isNotEmpty()

                        SettingsGroup(
                            title = stringResource(R.string.models_default),
                            items = listOf({
                                SettingsItem(
                                    headlineContent = {
                                        Text(
                                            if (!hasEnabledModels) stringResource(R.string.models_no_models) else activeDisplayName,
                                            color = if (!hasEnabledModels) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = if (hasEnabledModels) {
                                        { Text(providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                                    } else null,
                                    leadingContent = {
                                        val tint = if (hasEnabledModels) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        when {
                                            !hasEnabledModels -> Icon(Icons.Default.Chat, null, tint = tint, modifier = Modifier.size(24.dp))
                                            isActiveLocal -> Icon(Icons.Default.AutoAwesome, null, tint = tint, modifier = Modifier.size(24.dp))
                                            activeIconRes != 0 -> Icon(painterResource(activeIconRes), null, tint = tint, modifier = Modifier.size(24.dp))
                                            else -> Icon(Icons.Default.Chat, null, tint = tint, modifier = Modifier.size(24.dp))
                                        }
                                    },
                                    modifier = Modifier.clickable(enabled = hasEnabledModels) { showActiveModelDialog = true }
                                )
                            })
                        )
                    }

                    // ── Directory rows: Available & Multimodal ──
                    item(key = "directory_rows") {
                        val totalCount = mergeProviderModels(availableModels, manualModels).values.flatten().size
                        val rows = listOf<@Composable () -> Unit>(
                            {
                                SettingsItem(
                                    headlineContent = { Text(stringResource(R.string.models_available)) },
                                    supportingContent = { Text(stringResource(R.string.models_available_desc)) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    trailingContent = {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = totalCount.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.clickable { subScreen = "available" }
                                )
                            },
                            {
                                SettingsItem(
                                    headlineContent = { Text(stringResource(R.string.models_multimodal)) },
                                    supportingContent = { Text(stringResource(R.string.models_multimodal_desc)) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable { subScreen = "multimodal" }
                                )
                            }
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            rows.forEachIndexed { index, content ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                val shape = when {
                                    rows.size == 1 -> RoundedCornerShape(24.dp)
                                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                    index == rows.lastIndex -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                    else -> RoundedCornerShape(5.dp)
                                }
                                Surface(
                                    shape = shape,
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    content()
                                }
                            }
                        }
                    }

                    if (showDocFab) {
                        item(key = "doc_spacer") { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // ── Active Model Dialog ──
    if (showActiveModelDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showActiveModelDialog = false },
            title = { Text(stringResource(R.string.models_select_default), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(enabledModels.toList()) { model ->
                        val alias = modelAliases[model]
                        val parsed = ModelId.parse(model)
                        val displayName = alias ?: parsed.apiModelName
                        val providerName = parsed.providerName

                        SettingsItem(
                            headlineContent = {
                                Text(displayName, fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal)
                            },
                            supportingContent = {
                                Text(providerName, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = model == selectedModel,
                                    onClick = {
                                        viewModel.settings.setSelectedModel(model)
                                        showActiveModelDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setSelectedModel(model)
                                showActiveModelDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActiveModelDialog = false }) {
                    Text(stringResource(R.string.provider_close))
                }
            }
        )
    }
}
