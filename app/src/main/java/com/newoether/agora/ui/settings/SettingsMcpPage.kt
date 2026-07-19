package com.newoether.agora.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.McpServerConfig
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMcpPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val servers by viewModel.settings.mcpServers.collectAsState()
    val scrollState = rememberScrollState()
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.mcp_title),
        onBack = onBack,
        scrollState = scrollState,
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.mcp_title), items = buildList {
                if (servers.isEmpty()) {
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.mcp_no_servers), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                            modifier = Modifier.heightIn(min = 64.dp)
                        )
                    }
                } else {
                    servers.forEach { server -> add { ServerRow(server, viewModel, onEdit = { editing = server }) } }
                }
                add {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { creating = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.mcp_add_server), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            })
        }

        // Delete confirmation
        deleteConfirmId?.let { id ->
            val server = servers.find { it.id == id }
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { deleteConfirmId = null },
                title = { Text(stringResource(R.string.mcp_delete), fontWeight = FontWeight.Bold) },
                text = { Text(server?.name?.ifBlank { server.url } ?: "") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.settings.deleteMcpServer(id); deleteConfirmId = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.mcp_delete)) }
                },
                dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text(stringResource(R.string.mcp_cancel)) } }
            )
        }
    }

    // Create / edit dialog
    if (creating) {
        McpServerDialog(
            existing = null,
            existingNames = servers.map { it.name }.toSet(),
            onDismiss = { creating = false },
            onSave = { config ->
                viewModel.settings.addMcpServer(config)
                creating = false
            }
        )
    }
    editing?.let { config ->
        McpServerDialog(
            existing = config,
            existingNames = servers.filter { it.id != config.id }.map { it.name }.toSet(),
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.settings.updateMcpServer(updated)
                editing = null
            }
        )
    }
}

@Composable
private fun ServerRow(server: McpServerConfig, viewModel: ChatViewModel, onEdit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    SettingsItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name.ifBlank { server.url }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = onEdit,
                    label = { Text(transportLabel(server.transport), style = MaterialTheme.typography.labelSmall) }
                )
            }
        },
        supportingContent = {
            Column {
                Text(server.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                testResult?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = { Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                try { viewModel.testMcpConnection(server) }
                                catch (e: Exception) { e.message ?: "Error" }
                            }
                            testResult = r
                            testing = false
                        }
                    }) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.width(4.dp))
                Switch(checked = server.enabled, onCheckedChange = {
                    viewModel.settings.updateMcpServer(server.copy(enabled = it))
                })
            }
        },
        modifier = Modifier.clickable { onEdit() }
    )
}

@Composable
private fun transportLabel(transport: String): String = when (transport) {
    McpServerConfig.TRANSPORT_SSE -> stringResource(R.string.mcp_transport_sse)
    else -> stringResource(R.string.mcp_transport_streamable)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerDialog(
    existing: McpServerConfig?,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
) {
    val name = remember { mutableStateOf(existing?.name ?: "") }
    val url = remember { mutableStateOf(existing?.url ?: "") }
    val transport = remember { mutableStateOf(existing?.transport ?: McpServerConfig.TRANSPORT_STREAMABLE) }
    val headers = remember { mutableStateOf(existing?.headersJson ?: "{}") }
    val enabled = remember { mutableStateOf(existing?.enabled ?: true) }
    val nameError = remember { mutableStateOf<String?>(null) }
    val urlError = remember { mutableStateOf<String?>(null) }
    val headersError = remember { mutableStateOf<String?>(null) }

    // Validation messages are pre-resolved so the non-Composable onClick handler can set them.
    val msgInvalidName = stringResource(R.string.mcp_invalid_name)
    val msgNameTaken = stringResource(R.string.mcp_name_taken)
    val msgInvalidUrl = stringResource(R.string.mcp_invalid_url)
    val msgInvalidHeaders = stringResource(R.string.mcp_invalid_headers)

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.mcp_add_server else R.string.mcp_edit_server), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it; nameError.value = null },
                    label = { Text(stringResource(R.string.mcp_server_name)) },
                    isError = nameError.value != null,
                    supportingText = nameError.value?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url.value,
                    onValueChange = { url.value = it; urlError.value = null },
                    label = { Text(stringResource(R.string.mcp_server_url)) },
                    placeholder = { Text(stringResource(R.string.mcp_server_url_hint)) },
                    isError = urlError.value != null,
                    supportingText = urlError.value?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.mcp_transport), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = transport.value == McpServerConfig.TRANSPORT_STREAMABLE,
                        onClick = { transport.value = McpServerConfig.TRANSPORT_STREAMABLE },
                        label = { Text(stringResource(R.string.mcp_transport_streamable)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = transport.value == McpServerConfig.TRANSPORT_SSE,
                        onClick = { transport.value = McpServerConfig.TRANSPORT_SSE },
                        label = { Text(stringResource(R.string.mcp_transport_sse)) }
                    )
                }
                OutlinedTextField(
                    value = headers.value,
                    onValueChange = { headers.value = it; headersError.value = null },
                    label = { Text(stringResource(R.string.mcp_headers)) },
                    placeholder = { Text(stringResource(R.string.mcp_headers_hint)) },
                    isError = headersError.value != null,
                    supportingText = headersError.value?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled.value, onCheckedChange = { enabled.value = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mcp_enabled))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Validate
                val n = name.value.trim()
                val u = url.value.trim().let { if (it.endsWith("/")) it.dropLast(1) else it }
                val h = headers.value.trim().ifBlank { "{}" }
                when {
                    n.isEmpty() -> nameError.value = msgInvalidName
                    n in existingNames -> nameError.value = msgNameTaken
                    !u.startsWith("http://", true) && !u.startsWith("https://", true) -> urlError.value = msgInvalidUrl
                    else -> {
                        val headersOk = try {
                            kotlinx.serialization.json.Json.parseToJsonElement(h); true
                        } catch (_: Exception) { false }
                        if (!headersOk) {
                            headersError.value = msgInvalidHeaders
                        } else {
                            onSave(
                                (existing ?: McpServerConfig(id = UUID.randomUUID().toString(), name = n, url = u)).copy(
                                    name = n, url = u, transport = transport.value, headersJson = h, enabled = enabled.value
                                )
                            )
                        }
                    }
                }
            }) { Text(stringResource(R.string.mcp_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mcp_cancel)) } }
    )
}
