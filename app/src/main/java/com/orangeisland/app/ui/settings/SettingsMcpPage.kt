package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
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
import com.orangeisland.app.mcp.McpStatus
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMcpPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val servers by viewModel.settings.mcpServers.collectAsState()
    // Per-server connection status from the heartbeat guardian. Accessing mcpStatuses forces the
    // pool lazy, which starts monitoring — so the icons come alive as soon as this page opens.
    val statuses by viewModel.mcpStatuses.collectAsState()
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
                                // A server absent from the status map has never been probed yet
                                // (the heartbeat just started). Treat it as CONNECTING — a spinner —
                                // rather than an error icon, so the page never opens red.
                                status = statuses[server.id] ?: McpStatus.CONNECTING,
                                onEdit = { editing = server },
                                onToggle = { on ->
                                    viewModel.settings.updateMcpServer(server.copy(enabled = on))
                                    // Keep the pool in sync with the toggle immediately, instead of
                                    // waiting up to HEARTBEAT_INTERVAL_MS for the next tick.
                                    if (on) {
                                        // Re-enabled: drop any stale connection and probe right away
                                        // so the icon spins → resolves to ok/error within seconds.
                                        viewModel.mcpClientPool.refreshStatus(server)
                                    } else {
                                        // Disabled: tear down the live connection so it stops
                                        // counting as READY; the row will render the greyed icon.
                                        viewModel.mcpClientPool.invalidate(server.id)
                                    }
                                }
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
private fun ServerRow(
    server: McpServerConfig,
    status: McpStatus,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
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
        leadingContent = {
            // Disabled servers aren't probed by the heartbeat, so they get a neutral grey icon
            // regardless of their last known status — no spinner, no error.
            when {
                !server.enabled -> Icon(
                    Icons.Default.Extension, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                status == McpStatus.CONNECTING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                status == McpStatus.READY -> Icon(
                    Icons.Default.Extension, null,
                    tint = MaterialTheme.colorScheme.primary
                )
                // DISCONNECTED: covers "couldn't connect" AND "connected but no tools" per the
                // user's decision to merge both into one error indicator.
                else -> Icon(
                    Icons.Default.Error, null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
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
