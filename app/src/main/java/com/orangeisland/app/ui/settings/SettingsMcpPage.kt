package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.McpServerConfig
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMcpPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val servers by viewModel.settings.mcpServers.collectAsState()
    val scrollState = rememberScrollState()
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var creating by remember { mutableStateOf(false) }

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
                    servers.forEach { server ->
                        add {
                            ServerRow(
                                server = server,
                                onEdit = { editing = server },
                                onToggle = { on -> viewModel.settings.updateMcpServer(server.copy(enabled = on)) }
                            )
                        }
                    }
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

    }

    // Create / edit detail screen
    if (creating) {
        McpServerDetailScreen(
            existing = null,
            existingNames = servers.map { it.name }.toSet(),
            viewModel = viewModel,
            onBack = { creating = false },
            onSave = { config ->
                viewModel.settings.addMcpServer(config)
                creating = false
            },
            onDelete = null
        )
    }
    editing?.let { config ->
        McpServerDetailScreen(
            existing = config,
            existingNames = servers.filter { it.id != config.id }.map { it.name }.toSet(),
            viewModel = viewModel,
            onBack = { editing = null },
            onSave = { updated ->
                viewModel.settings.updateMcpServer(updated)
                editing = null
            },
            onDelete = {
                viewModel.settings.deleteMcpServer(config.id)
                editing = null
            }
        )
    }
}

@Composable
private fun ServerRow(server: McpServerConfig, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    SettingsItem(
        headlineContent = {
            Text(
                server.name.ifBlank { server.url },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                transportLabel(server.transport),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = { Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Switch(checked = server.enabled, onCheckedChange = onToggle)
        },
        modifier = Modifier.clickable { onEdit() }
    )
}

@Composable
internal fun transportLabel(transport: String): String = when (transport) {
    McpServerConfig.TRANSPORT_SSE -> stringResource(R.string.mcp_transport_sse)
    else -> stringResource(R.string.mcp_transport_streamable)
}
