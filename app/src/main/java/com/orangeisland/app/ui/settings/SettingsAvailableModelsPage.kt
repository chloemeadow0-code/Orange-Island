package com.orangeisland.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import com.orangeisland.app.ui.components.clearFocusOnTap
import com.orangeisland.app.ui.components.providerIcon
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.noOpBringIntoView
import com.orangeisland.app.viewmodel.ChatViewModel

internal fun mergeProviderModels(
    availableModels: Map<String, List<String>>,
    manualModels: Map<String, List<String>>
): Map<String, List<String>> = buildMap {
    for (entry in availableModels.entries + manualModels.entries) {
        val existing = getOrDefault(entry.key, emptyList())
        put(entry.key, (existing + entry.value).distinct())
    }
}.filterValues { it.isNotEmpty() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAvailableModelsPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val enabledModels by viewModel.settings.enabledModels.collectAsState()
    val availableModels by viewModel.settings.availableModels.collectAsState()
    val manualModels by viewModel.settings.manualModels.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val contextLimits by viewModel.settings.modelContextLimits.collectAsState()
    val lastFingerprint by viewModel.settings.lastModelsFetchFingerprint.collectAsState()

    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var showModelAliasDialog by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (selectedProvider != null) {
            selectedProvider = null
        } else {
            onBack()
        }
    }

    // Auto-fetch models when entering the page if provider config has changed
    LaunchedEffect(Unit) {
        val current = viewModel.computeProviderFingerprint()
        if (current != lastFingerprint) {
            viewModel.fetchAvailableModels()
        }
    }

    GuardedAnimatedContent(
        targetState = selectedProvider,
        forward = selectedProvider != null
    ) { provider ->
        if (provider == null) {
            val providers = mergeProviderModels(availableModels, manualModels).toSortedMap()
            val allKeys = listOf("__sync__") + providers.keys.toList()

            CollapsingSettingsLazyScaffold(
                title = stringResource(R.string.models_available),
                onBack = onBack
            ) {
                allKeys.forEachIndexed { index, key ->
                    val isFirst = index == 0
                    val isLast = index == allKeys.lastIndex
                    val shape = when {
                        allKeys.size == 1 -> RoundedCornerShape(24.dp)
                        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                        isLast -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(5.dp)
                    }

                    item(key = key) {
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (key == "__sync__") {
                                SettingsItem(
                                    headlineContent = { Text(stringResource(R.string.models_sync)) },
                                    supportingContent = { Text(stringResource(R.string.models_sync_desc)) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    modifier = Modifier.clickable { viewModel.fetchAvailableModels() }
                                )
                            } else {
                                val name = key
                                val models = providers[name]!!
                                val headerIconRes = providerIcon(name)
                                val isLocalHeader = name.equals(Constants.PROVIDER_LOCAL, ignoreCase = true)

                                SettingsItem(
                                    headlineContent = { Text(name) },
                                    supportingContent = { Text(stringResource(R.string.models_count, models.size)) },
                                    leadingContent = {
                                        when {
                                            isLocalHeader -> Icon(
                                                Icons.Default.AutoAwesome,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            headerIconRes != 0 -> Icon(
                                                painterResource(headerIconRes),
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            else -> Icon(
                                                Icons.Default.Cloud,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    },
                                    modifier = Modifier.clickable { selectedProvider = name }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val name = provider
            val providers = mergeProviderModels(availableModels, manualModels)
            val providerModels = providers[name] ?: emptyList()
            val searchState = rememberTextFieldState()

            val filteredModels = remember(searchState.text, providerModels, modelAliases) {
                val query = searchState.text.toString().trim().lowercase()
                if (query.isEmpty()) providerModels
                else providerModels.filter { model ->
                    val alias = modelAliases[model]
                    val parsed = ModelId.parse(model)
                    val displayName = alias ?: parsed.apiModelName
                    displayName.lowercase().contains(query)
                }
            }

            CollapsingSettingsLazyScaffold(
                title = name,
                onBack = { selectedProvider = null }
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        state = searchState,
                        label = { Text(stringResource(R.string.models_search_hint)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        lineLimits = TextFieldLineLimits.SingleLine
                    )
                }

                if (filteredModels.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.models_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    filteredModels.forEachIndexed { index, model ->
                        val isFirst = index == 0
                        val isLast = index == filteredModels.lastIndex
                        val shape = when {
                            filteredModels.size == 1 -> RoundedCornerShape(24.dp)
                            isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                            isLast -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(5.dp)
                        }

                        item(key = model) {
                            Surface(
                                shape = shape,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val isEnabled = enabledModels.contains(model)
                                val alias = modelAliases[model]
                                val parsed = ModelId.parse(model)
                                val displayName = alias ?: parsed.apiModelName

                                SettingsItem(
                                    headlineContent = { Text(displayName) },
                                    supportingContent = if (alias != null) {
                                        { Text(parsed.apiModelName) }
                                    } else null,
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { showModelAliasDialog = model }) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.models_rename),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Checkbox(
                                                checked = isEnabled,
                                                onCheckedChange = {
                                                    viewModel.settings.setEnabledModels(
                                                        if (it) enabledModels + model else enabledModels - model
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Model Config Dialog ──
            showModelAliasDialog?.let { model ->
                val aliasState = rememberTextFieldState(modelAliases[model] ?: "")
                val contextLimitState = rememberTextFieldState(
                    contextLimits[model]?.toString() ?: ""
                )

                AlertDialog(
                    modifier = Modifier.clearFocusOnTap(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    onDismissRequest = { showModelAliasDialog = null },
                    title = { Text(stringResource(R.string.model_edit_config), fontWeight = FontWeight.Bold) },
                    text = {
                        val parsed = ModelId.parse(model)
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.models_rename_current, parsed.apiModelName),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.noOpBringIntoView()) {
                                OutlinedTextField(
                                    state = aliasState,
                                    label = { Text(stringResource(R.string.models_alias_hint)) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(parsed.apiModelName) }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(modifier = Modifier.noOpBringIntoView()) {
                                OutlinedTextField(
                                    state = contextLimitState,
                                    label = { Text(stringResource(R.string.model_context_window_label)) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.model_context_window_hint)) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.settings.updateModelAlias(model, aliasState.text.toString())
                            val limitText = contextLimitState.text.toString().trim()
                            val limitValue = limitText.toIntOrNull()
                            viewModel.settings.updateModelContextLimit(model, limitValue)
                            showModelAliasDialog = null
                        }) { Text(stringResource(R.string.provider_save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showModelAliasDialog = null }) {
                            Text(stringResource(R.string.provider_cancel))
                        }
                    }
                )
            }
        }
    }
}
