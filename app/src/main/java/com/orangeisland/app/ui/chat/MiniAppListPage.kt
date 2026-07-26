package com.orangeisland.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.MiniAppEntry
import com.orangeisland.app.ui.settings.CollapsingSettingsLazyScaffold
import com.orangeisland.app.ui.settings.SettingsGroup
import com.orangeisland.app.ui.settings.SettingsItem

/**
 * Mini App manager — lists user-added web apps, lets the user add, remove, and open them.
 *
 * Tapping a row opens [MiniAppPage] in a full-screen WebView.
 *
 * UI follows the same iOS-style collapsing-title + rounded-card pattern as the Plugin settings page
 * ([com.orangeisland.app.ui.settings.SettingsPluginPage]) for visual consistency.
 */
@Composable
fun MiniAppListPage(
    entries: List<MiniAppEntry>,
    onBack: () -> Unit,
    onAdd: (MiniAppEntry) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (MiniAppEntry) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MiniAppEntry?>(null) }

    // Intercept the system back gesture so it closes this page instead of
    // bubbling up to the Activity (which would exit the app on a swipe-back).
    BackHandler { onBack() }

    CollapsingSettingsLazyScaffold(
        title = stringResource(R.string.mini_app_title),
        onBack = onBack,
    ) {
        item {
            SettingsGroup(
                title = stringResource(R.string.mini_app_title),
                items = buildList {
                    if (entries.isEmpty()) {
                        add {
                            SettingsItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.mini_app_empty),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                },
                                modifier = Modifier.heightIn(min = 64.dp)
                            )
                        }
                    } else {
                        entries.forEach { entry ->
                            add {
                                MiniAppRow(
                                    entry = entry,
                                    onOpen = { onOpen(entry) },
                                    onDelete = { deleteTarget = entry }
                                )
                            }
                        }
                    }
                    // ── Add button (same style as Plugin import button) ──
                    add {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { showAddDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Add,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.mini_app_add),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    // Add dialog
    if (showAddDialog) {
        var nameDraft by remember { mutableStateOf("") }
        var urlDraft by remember { mutableStateOf("") }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.mini_app_add), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text(stringResource(R.string.mini_app_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text(stringResource(R.string.mini_app_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = nameDraft.trim()
                        val trimmedUrl = urlDraft.trim()
                        if (trimmedName.isNotBlank() && trimmedUrl.isNotBlank()) {
                            onAdd(MiniAppEntry(name = trimmedName, url = trimmedUrl))
                            showAddDialog = false
                        }
                    },
                    enabled = nameDraft.trim().isNotBlank() && urlDraft.trim().isNotBlank()
                ) { Text(stringResource(R.string.mini_app_add_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.mini_app_cancel))
                }
            }
        )
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.mini_app_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.mini_app_delete_text, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(target.id); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.mini_app_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.mini_app_cancel))
                }
            }
        )
    }
}

@Composable
private fun MiniAppRow(
    entry: MiniAppEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = {
            Text(
                entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                entry.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.OpenInBrowser,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
