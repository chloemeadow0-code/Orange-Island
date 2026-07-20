package com.orangeisland.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.api.ProviderDefaults
import com.orangeisland.app.ui.components.clearFocusOnTap
import com.orangeisland.app.util.Constants
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File

private data class SearchMethodOption(val key: String, @androidx.annotation.StringRes val labelRes: Int)

private val searchMethods = listOf(
    SearchMethodOption("keyword", R.string.search_method_keyword),
    SearchMethodOption(Constants.SEARCH_METHOD_RAG, R.string.search_method_rag)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val accessPastConversations by viewModel.settings.accessPastConversations.collectAsState()
    val autoCacheEnabled by viewModel.settings.autoCacheEnabled.collectAsState()
    val modelSearchMethod by viewModel.settings.modelSearchMethod.collectAsState()
    val manualSearchMethod by viewModel.settings.manualSearchMethod.collectAsState()
    val embeddingModels by viewModel.settings.embeddingModels.collectAsState()
    val activeEmbeddingModelId by viewModel.settings.activeEmbeddingModelId.collectAsState()
    val cachingProgress by viewModel.cachingProgress.collectAsState()
    val cacheCounts by viewModel.cacheCounts.collectAsState()
    val searchContextWindow by viewModel.settings.searchContextWindow.collectAsState()
    val searchMatchLimit by viewModel.settings.searchMatchLimit.collectAsState()
    val ragThreshold by viewModel.settings.ragThreshold.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadCacheCounts() }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showLocalDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showRecacheConfirm by remember { mutableStateOf<String?>(null) }
    var showMenuForModel by remember { mutableStateOf<String?>(null) }
    var localThreshold by remember { mutableFloatStateOf(ragThreshold) }
    LaunchedEffect(ragThreshold) { localThreshold = ragThreshold }
    var renameText by remember { mutableStateOf("") }
    // Embedding provider presets
    val embeddingProviders = listOf(
        EmbeddingProviderPreset(Constants.PROVIDER_OPENAI, ProviderDefaults.embeddingBaseUrl(Constants.PROVIDER_OPENAI), listOf("text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002")),
        EmbeddingProviderPreset("Mistral", ProviderDefaults.embeddingBaseUrl("Mistral"), listOf("mistral-embed")),
        EmbeddingProviderPreset("Voyage AI", ProviderDefaults.embeddingBaseUrl("Voyage AI"), listOf("voyage-3-large", "voyage-3-lite", "voyage-code-3")),
        EmbeddingProviderPreset("SiliconFlow", ProviderDefaults.embeddingBaseUrl("SiliconFlow"), listOf("BAAI/bge-m3", "BAAI/bge-large-en-v1.5")),
        EmbeddingProviderPreset(Constants.PROVIDER_OPEN_ROUTER, ProviderDefaults.embeddingBaseUrl(Constants.PROVIDER_OPEN_ROUTER), listOf("openai/text-embedding-3-small", "openai/text-embedding-3-large")),
        EmbeddingProviderPreset(Constants.PROVIDER_OLLAMA, ProviderDefaults.embeddingBaseUrl(Constants.PROVIDER_OLLAMA), emptyList()),
        EmbeddingProviderPreset("Custom", "", emptyList())
    )
    val remoteState = rememberRemoteEmbeddingDialogState(embeddingProviders.size)
    var localName by remember { mutableStateOf("") }
    var localFilePath by remember { mutableStateOf("") }
    var localBatchSize by remember { mutableStateOf("8") }
    var isImporting by remember { mutableStateOf(false) }
    var showGgufError by remember { mutableStateOf(false) }

    LaunchedEffect(embeddingModels.size) {
        showMenuForModel = null
    }
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    CollapsingSettingsScaffold(
        title = stringResource(R.string.search_title),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("search.md") }
    ) {
            SettingsGroupColumn {
                SettingsGroup(
                    title = stringResource(R.string.memory_access_title),
                    items = listOf(
                        {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.memory_access_past)) },
                                supportingContent = { Text(stringResource(R.string.memory_access_past_desc)) },
                                leadingContent = { Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    Switch(checked = accessPastConversations, onCheckedChange = { viewModel.settings.setAccessPastConversations(it) })
                                },
                                modifier = Modifier.clickable { viewModel.settings.setAccessPastConversations(!accessPastConversations) }
                            )
                        }
                    )
                )

                SettingsGroup(
                    title = stringResource(R.string.auto_cache_title),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.auto_cache)) },
                            supportingContent = { Text(stringResource(R.string.auto_cache_desc)) },
                            leadingContent = { Icon(Icons.Default.Cached, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Switch(checked = autoCacheEnabled, onCheckedChange = { viewModel.settings.setAutoCacheEnabled(it) })
                            },
                            modifier = Modifier.clickable { viewModel.settings.setAutoCacheEnabled(!autoCacheEnabled) }
                        )
                    }
                )
            )

            SettingsGroup(
                title = stringResource(R.string.search_methods_title),
                items = buildList {
                    add {
                        var expanded by remember { mutableStateOf(false) }
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.model_search_method)) },
                            supportingContent = { Text(stringResource(R.string.model_search_method_desc)) },
                            leadingContent = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Box {
                                    Text(
                                        searchMethods.find { it.key == modelSearchMethod }?.let { stringResource(it.labelRes) } ?: "",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(80.dp),
                                        textAlign = TextAlign.Center
                                    )
                                    DropdownMenu(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 16.dp,
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        val noEmbedding = embeddingModels.isEmpty()
                                        searchMethods.forEach { method ->
                                            val ragDisabled = method.key == Constants.SEARCH_METHOD_RAG && noEmbedding
                                            DropdownMenuItem(
                                                text = { Text(stringResource(method.labelRes), color = if (ragDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface) },
                                                leadingIcon = {
                                                    if (modelSearchMethod == method.key)
                                                        Icon(Icons.Default.Check, null, tint = if (ragDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary)
                                                },
                                                onClick = {
                                                    if (!ragDisabled) {
                                                        viewModel.settings.setModelSearchMethod(method.key)
                                                        expanded = false
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable { expanded = true }
                        )
                    }
                    add {
                        var expanded by remember { mutableStateOf(false) }
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.manual_search_method)) },
                            supportingContent = { Text(stringResource(R.string.manual_search_method_desc)) },
                            leadingContent = { Icon(Icons.Default.ManageSearch, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                Box {
                                    Text(
                                        searchMethods.find { it.key == manualSearchMethod }?.let { stringResource(it.labelRes) } ?: "",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(80.dp),
                                        textAlign = TextAlign.Center
                                    )
                                    DropdownMenu(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 16.dp,
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        val noEmbedding = embeddingModels.isEmpty()
                                        searchMethods.forEach { method ->
                                            val ragDisabled = method.key == Constants.SEARCH_METHOD_RAG && noEmbedding
                                            DropdownMenuItem(
                                                text = { Text(stringResource(method.labelRes), color = if (ragDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface) },
                                                leadingIcon = {
                                                    if (manualSearchMethod == method.key)
                                                        Icon(Icons.Default.Check, null, tint = if (ragDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary)
                                                },
                                                onClick = {
                                                    if (!ragDisabled) {
                                                        viewModel.settings.setManualSearchMethod(method.key)
                                                        expanded = false
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable { expanded = true }
                        )
                    }
                }
            )

            SettingsGroup(
                title = stringResource(R.string.embedding_title),
                items = buildList {
                    if (embeddingModels.isEmpty()) {
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.no_embedding_models), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = { Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                                modifier = Modifier.heightIn(min = 64.dp)
                            )
                        }
                    } else {
                        embeddingModels.forEach { model ->
                            add {
                                val isActive = model.id == activeEmbeddingModelId
                                val progress = cachingProgress[model.id]
                                val isCaching = progress != null
                                val counts = cacheCounts[model.id]
                                val allCached = counts != null && counts.second > 0 && counts.first >= counts.second
                                SettingsItem(
                                    headlineContent = { Text(model.name) },
                                    supportingContent = {
                                        val typeLabel = if (model.type == com.orangeisland.app.data.EmbeddingModelType.REMOTE)
                                            stringResource(R.string.embedding_type_remote)
                                        else stringResource(R.string.embedding_type_local)
                                        val cacheLabel = if (isCaching) {
                                            "${progress!!.second - progress!!.first} ${stringResource(R.string.not_cached)} (${progress!!.first}/${progress!!.second})"
                                        } else if (counts != null && counts.second > 0) {
                                            val notCached = (counts.second - counts.first).coerceAtLeast(0)
                                            if (notCached == 0) stringResource(R.string.cached)
                                            else "${notCached} ${stringResource(R.string.not_cached)} (${counts.first}/${counts.second})"
                                        } else {
                                            stringResource(R.string.not_cached)
                                        }
                                        Text("$typeLabel · $cacheLabel")
                                    },
                                    leadingContent = {
                                        RadioButton(
                                            selected = isActive,
                                            onClick = { viewModel.setActiveEmbeddingModel(model.id) }
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            if (!isCaching) {
                                                TextButton(onClick = {
                                                    if (allCached) {
                                                        showRecacheConfirm = model.id
                                                    } else {
                                                        viewModel.cacheMessagesForModel(model.id)
                                                    }
                                                }) { Text(if (allCached) stringResource(R.string.recache_action) else stringResource(R.string.cache_action)) }
                                            }
                                            if (isCaching) {
                                                val ratio = progress!!.first.toFloat() / progress.second.toFloat()
                                                CircularProgressIndicator(
                                                    progress = { ratio },
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                            Box {
                                                IconButton(onClick = { showMenuForModel = model.id }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
                                                }
                                                DropdownMenu(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                    tonalElevation = 16.dp,
                                                    expanded = showMenuForModel == model.id,
                                                    onDismissRequest = { showMenuForModel = null },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.edit)) },
                                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                        onClick = {
                                                            showMenuForModel = null
                                                            renameText = model.name
                                                            showRenameDialog = model.id
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                        onClick = {
                                                            showMenuForModel = null
                                                            showDeleteDialog = model.id
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable { viewModel.setActiveEmbeddingModel(model.id) }
                                )
                            }
                        }
                    }
                    add {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = {
                                remoteState.prepareForNew(
                                    embeddingProviders[0],
                                    viewModel.resolveEmbeddingKeyForProviderExact(Constants.PROVIDER_OPENAI)?.key ?: ""
                                )
                                showRemoteDialog = true
                            }) { Text(stringResource(R.string.add_remote_model)) }
                            TextButton(onClick = {
                                localName = ""
                                localFilePath = ""
                                localBatchSize = "8"
                                showLocalDialog = true
                            }) { Text(stringResource(R.string.add_local_model)) }
                        }
                    }
                }
            )

            SettingsGroup(
                title = stringResource(R.string.advanced_title),
                items = listOf(
                    {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.Top
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.text_compare_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.search_context_label),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.search_context_desc, searchContextWindow),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Slider(
                                        value = searchContextWindow.toFloat(),
                                        onValueChange = { viewModel.settings.setSearchContextWindow(it.toInt()) },
                                        valueRange = 4f..32f,
                                        steps = 6,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    },
                    {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.Top
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.text_compare_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.search_match_label),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.search_match_desc, searchMatchLimit),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Slider(
                                        value = searchMatchLimit.toFloat(),
                                        onValueChange = { viewModel.settings.setSearchMatchLimit(it.toInt()) },
                                        valueRange = 5f..30f,
                                        steps = 4,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    },
                    {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.Top
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.text_compare_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.rag_threshold_label),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "≥ ${"%.2f".format(localThreshold)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Slider(
                                        value = localThreshold,
                                        onValueChange = { localThreshold = it },
                                        onValueChangeFinished = { viewModel.settings.setRagThreshold(localThreshold) },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                )
            )
            }
            if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }

        if (showRemoteDialog) {
            AddRemoteEmbeddingDialog(
                state = remoteState,
                providers = embeddingProviders,
                viewModel = viewModel,
                onDismiss = { showRemoteDialog = false },
            )
        }

        if (showGgufError) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { showGgufError = false },
                title = { Text(stringResource(R.string.import_invalid_gguf_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.import_invalid_gguf_desc)) },
                confirmButton = { TextButton(onClick = { showGgufError = false }) { Text(stringResource(R.string.ok)) } }
            )
        }

        if (showLocalDialog) {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    isImporting = true
                    scope.launch {
                        try {
                            val destFile = File(context.filesDir, "embedding_${java.util.UUID.randomUUID()}.gguf")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            // Validate GGUF magic bytes
                            val magic = ByteArray(4)
                            destFile.inputStream().use { it.read(magic) }
                            if (magic[0] != 'G'.code.toByte() || magic[1] != 'G'.code.toByte()
                                || magic[2] != 'U'.code.toByte() || magic[3] != 'F'.code.toByte()) {
                                destFile.delete()
                                showGgufError = true
                            } else {
                                localFilePath = destFile.absolutePath
                            }
                        } catch (_: Exception) { }
                        isImporting = false
                    }
                }
            }
            AlertDialog(
                modifier = Modifier.clearFocusOnTap(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = {
                    if (localFilePath.isNotBlank()) File(localFilePath).delete()
                    showLocalDialog = false
                },
                title = { Text(stringResource(R.string.add_local_model), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = localName,
                            onValueChange = { localName = it },
                            label = { Text(stringResource(R.string.model_name_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = localBatchSize,
                            onValueChange = { localBatchSize = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.embedding_batch_size)) },
                            supportingText = { Text(stringResource(R.string.embedding_batch_size_desc)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (localFilePath.isNotBlank()) {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.local_model_ready)) },
                                leadingContent = {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        } else {
                            TextButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.import_model))
                            }
                        }
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = 16.dp), strokeWidth = 2.dp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (localName.isNotBlank() && localFilePath.isNotBlank()) {
                            viewModel.addEmbeddingModel(
                                com.orangeisland.app.data.EmbeddingModelConfig(
                                    name = localName,
                                    type = com.orangeisland.app.data.EmbeddingModelType.LOCAL,
                                    localFilePath = localFilePath,
                                    batchSize = localBatchSize.toIntOrNull() ?: 8
                                )
                            )
                            showLocalDialog = false
                        }
                    },
                        enabled = localName.isNotBlank() && localFilePath.isNotBlank()
                    ) { Text(stringResource(R.string.add)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (localFilePath.isNotBlank()) File(localFilePath).delete()
                        showLocalDialog = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showRecacheConfirm != null) {
            val modelId = showRecacheConfirm!!
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { showRecacheConfirm = null },
                title = { Text(stringResource(R.string.recache_confirm_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.recache_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.cacheMessagesForModel(modelId, recache = true)
                        showRecacheConfirm = null
                    }) { Text(stringResource(R.string.recache_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRecacheConfirm = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showDeleteDialog != null) {
            val modelId = showDeleteDialog!!
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { showDeleteDialog = null },
                title = { Text(stringResource(R.string.delete_model_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.delete_model_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteEmbeddingModel(modelId)
                        showDeleteDialog = null
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showRenameDialog != null) {
            val modelId = showRenameDialog!!
            var editBatchSize by remember(modelId) {
                val model = viewModel.settings.embeddingModels.value.find { it.id == modelId }
                mutableStateOf((model?.batchSize ?: 8).toString())
            }
            AlertDialog(
                modifier = Modifier.clearFocusOnTap(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { showRenameDialog = null },
                title = { Text(stringResource(R.string.edit), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text(stringResource(R.string.model_name_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editBatchSize,
                            onValueChange = { editBatchSize = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.embedding_batch_size)) },
                            supportingText = { Text(stringResource(R.string.embedding_batch_size_desc)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameEmbeddingModel(modelId, renameText, editBatchSize.toIntOrNull() ?: 8)
                            showRenameDialog = null
                        }
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}
