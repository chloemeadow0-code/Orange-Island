package com.orangeisland.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.InstalledPlugin
import com.orangeisland.app.plugin.PluginMemoryProvider
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPluginPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    memoryProvider: PluginMemoryProvider? = null,
) {
    val settings = viewModel.settings
    val enabledIds by settings.enabledPluginIds.collectAsState()
    val pluginConfigs by settings.pluginConfigs.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loader = viewModel.pluginLoader

    // Installed plugins are re-scanned whenever this key changes (after install/uninstall/enable).
    var scanRevision by remember { mutableIntStateOf(0) }
    var scanned by remember(scanRevision) { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    LaunchedEffect(scanRevision, enabledIds) {
        if (loader != null) {
            scanned = withContext(Dispatchers.IO) { loader.scan(enabledIds) }
        }
    }

    var installError by remember { mutableStateOf<String?>(null) }
    var installOk by remember { mutableStateOf<String?>(null) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    /** When non-null, renders [PluginWebViewPage] full-screen instead of the list. */
    var openUiFor by remember { mutableStateOf<InstalledPlugin?>(null) }
    /** When non-null, opens the per-plugin config dialog. */
    var configTarget by remember { mutableStateOf<InstalledPlugin?>(null) }

    /**
     * Opens the plugin's UI, gating on config first: if the plugin declares `config` fields and
     * the user hasn't filled any of them yet, show the config dialog instead (the dialog's Save
     * path then re-invokes this, at which point values exist and we proceed to the UI).
     */
    fun openPluginUiOrConfig(plugin: InstalledPlugin) {
        val hasConfig = plugin.manifest.config.isNotEmpty()
        val hasStored = pluginConfigs[plugin.id]?.isNotEmpty() == true
        if (hasConfig && !hasStored) {
            configTarget = plugin
        } else {
            openUiFor = plugin
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && loader != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { loader.installFromZip(uri) }
                result.fold(
                    onSuccess = { manifest ->
                        installError = null
                        installOk = manifest.name
                        // New plugins default to enabled so the user sees them work immediately.
                        settings.setPluginEnabled(manifest.id, true)
                        scanRevision++
                    },
                    onFailure = { e ->
                        installOk = null
                        installError = e.message ?: e::class.simpleName
                    },
                )
            }
        }
    }

    // Route between the plugin list and a plugin's WebView UI page (if it has one).
    val openPlugin = openUiFor
    val sandbox = viewModel.pluginSandbox
    if (openPlugin != null && sandbox != null) {
        PluginWebViewPage(
            plugin = openPlugin,
            sandbox = sandbox,
            onBack = { openUiFor = null },
            memoryProvider = memoryProvider,
        )
        return
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.plugin_title),
        onBack = onBack,
        scrollState = rememberScrollState(),
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.plugin_title), items = buildList {
                if (loader == null) {
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.plugin_no_plugins), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                            modifier = Modifier.heightIn(min = 64.dp)
                        )
                    }
                } else if (scanned.isEmpty()) {
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.plugin_no_plugins), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                            modifier = Modifier.heightIn(min = 64.dp)
                        )
                    }
                } else {
                    scanned.forEach { plugin ->
                        add {
                            PluginRow(
                                plugin = plugin,
                                onToggle = { on -> settings.setPluginEnabled(plugin.id, on) },
                                onDelete = { deleteConfirmId = plugin.id },
                                onOpenUi = plugin.uiHtmlFile?.let { { openPluginUiOrConfig(plugin) } },
                                onConfigure = if (plugin.manifest.config.isNotEmpty()) {
                                    { configTarget = plugin }
                                } else null,
                            )
                        }
                    }
                }
                add {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { zipPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.plugin_import), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            })
        }

        installError?.let { msg ->
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { installError = null },
                title = { Text(stringResource(R.string.plugin_install_failed, msg), fontWeight = FontWeight.Bold) },
                confirmButton = { TextButton(onClick = { installError = null }) { Text("OK") } },
            )
        }
        installOk?.let { name ->
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { installOk = null },
                title = { Text(stringResource(R.string.plugin_install_success, name), fontWeight = FontWeight.Bold) },
                confirmButton = { TextButton(onClick = { installOk = null }) { Text("OK") } },
            )
        }
    }

    deleteConfirmId?.let { id ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { deleteConfirmId = null },
            title = { Text(stringResource(R.string.plugin_delete_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(scanned.find { it.id == id }?.manifest?.name ?: id) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val l = loader
                        if (l != null) {
                            scope.launch {
                                withContext(Dispatchers.IO) { l.uninstall(id) }
                                viewModel.pluginSandbox?.invalidate(id)
                                scanRevision++
                            }
                        }
                        deleteConfirmId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.plugin_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text(stringResource(R.string.plugin_cancel)) } }
        )
    }

    configTarget?.let { plugin ->
        PluginConfigDialog(
            pluginName = plugin.manifest.name.ifBlank { plugin.id },
            fields = plugin.manifest.config,
            initial = pluginConfigs[plugin.id] ?: emptyMap(),
            onDismiss = { configTarget = null },
            onSave = { values ->
                // Await the persist before navigating: PluginWebViewPage reads the config
                // synchronously on first compose, so if we navigate before the write commits
                // the page sees an empty config and falls back to default nicknames.
                scope.launch {
                    viewModel.settings.savePluginConfigAwait(plugin.id, values)
                    configTarget = null
                    if (plugin.uiHtmlFile != null) {
                        openUiFor = plugin
                    }
                }
            },
        )
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenUi: (() -> Unit)? = null,
    onConfigure: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plugin.manifest.name.ifBlank { plugin.id },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (plugin.manifest.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        plugin.manifest.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onConfigure != null) {
                    IconButton(onClick = onConfigure, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onOpenUi != null) {
                    IconButton(onClick = onOpenUi, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Web, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Switch(checked = plugin.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val metaParts = buildList {
            add(stringResource(R.string.plugin_version, plugin.manifest.version))
            if (plugin.manifest.author.isNotBlank()) {
                add(stringResource(R.string.plugin_author, plugin.manifest.author))
            }
            add(stringResource(R.string.plugin_tools_count, plugin.manifest.tools.size))
        }
        Text(
            metaParts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
