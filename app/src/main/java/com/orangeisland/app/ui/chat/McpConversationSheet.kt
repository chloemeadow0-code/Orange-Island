package com.orangeisland.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.McpServerConfig

/**
 * Per-conversation MCP server activation sheet. Opened from the chat composer's tool menu.
 *
 * - "Inherit" mode ([selectedIds] == null): uses all globally-enabled servers (default).
 * - Explicit selection ([selectedIds] != null): exactly the checked servers are active for
 *   this conversation, regardless of the global enabled flag.
 *
 * Empty selection is a valid "disable MCP for this conversation" state — McpToolProvider
 * treats an empty list as "no MCP tools" (vs null = inherit globals).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpConversationSheet(
    servers: List<McpServerConfig>,
    selectedIds: List<String>?,
    onInherit: () -> Unit,
    onToggle: (id: String, on: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val modalSheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.mcp_conversation_select),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            // Inherit-globals row: visible as "default" — selecting it clears the override.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedIds == null,
                    onCheckedChange = { if (it) onInherit() }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.mcp_conversation_inherit),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (servers.isEmpty()) {
                Text(
                    text = stringResource(R.string.mcp_no_servers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    servers.forEach { server ->
                        // In inherit-mode (selectedIds == null) every server is considered "on"
                        // (because globals are active). Checking the box transitions to explicit
                        // selection; unchecking it disables just this one for the conversation.
                        val inheritMode = selectedIds == null
                        val checked = if (inheritMode) true else selectedIds!!.contains(server.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(server.id, it) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Extension,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(server.name.ifBlank { server.url }, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    server.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
