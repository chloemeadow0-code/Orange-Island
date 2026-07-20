package com.orangeisland.app.ui.settings.datacontrol

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.settings.SettingsGroup
import com.orangeisland.app.ui.settings.SettingsItem
import com.orangeisland.app.viewmodel.ChatViewModel

private fun categoryLabelRes(key: String): Int = when (key) {
    "conversations" -> R.string.export_category_conversations
    "memories" -> R.string.export_category_memories
    "system_prompts" -> R.string.export_category_system_prompts
    "settings" -> R.string.export_category_settings
    "api_keys" -> R.string.export_category_api_keys
    else -> R.string.export_category_settings // fallback, never hit
}

/** Decode a SAF content:// URI into a human-readable path. */
private fun resolveDisplayPath(uri: String): String {
    if (!uri.startsWith("content://")) {
        return uri.ifBlank { "Download/OrangeIsland/Backup" }
    }
    // Decode percent-encoded characters (%3A → :, %2F → /, etc.)
    val decoded = Uri.decode(uri)
    // Extract the last meaningful path segment after the authority
    val segment = decoded.substringAfterLast("/")
        .replace("primary:", "Internal storage/")
        .replace("home:", "Internal storage/")
    // If it starts with a storage volume ID like "msd:" or "XXXX-XXXX:", show "SD Card"
    if (segment.matches(Regex("^[A-Za-z0-9]+[-:].*")) && !segment.startsWith("Internal")) {
        val volume = segment.substringBefore(":").substringBefore("-")
        return "SD Card ($volume)"
    }
    return segment.ifEmpty { "Selected folder" }
}

@Composable
internal fun AutoBackupSection(viewModel: ChatViewModel) {
    val autoBackupEnabled by viewModel.settings.autoBackupEnabled.collectAsState()
    val autoBackupPeriodHours by viewModel.settings.autoBackupPeriodHours.collectAsState()
    val autoDeleteEnabled by viewModel.settings.autoDeleteEnabled.collectAsState()
    val autoDeletePeriodHours by viewModel.settings.autoDeletePeriodHours.collectAsState()

    SettingsGroup(title = stringResource(R.string.auto_backup_title), items = buildList {
        // Toggle
        add {
            SettingsItem(
                headlineContent = { Text(stringResource(R.string.auto_backup_title)) },
                supportingContent = { Text(stringResource(R.string.auto_backup_subtitle)) },
                leadingContent = {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Switch(checked = autoBackupEnabled, onCheckedChange = { viewModel.setAutoBackupEnabled(it) })
                },
                modifier = Modifier.clickable { viewModel.setAutoBackupEnabled(!autoBackupEnabled) }
            )
        }

        if (autoBackupEnabled) {
            // Backup period
            add {
                AutoBackupPeriodDropdown(
                    currentHours = autoBackupPeriodHours,
                    onSelect = { viewModel.setAutoBackupPeriodHours(it) }
                )
            }

            // Categories
            add { AutoBackupCategoriesItem(viewModel) }

            // Directory
            add { AutoBackupDirectoryItem(viewModel) }

            // Auto delete toggle
            add {
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.auto_delete_title)) },
                    supportingContent = { Text(stringResource(R.string.auto_delete_subtitle)) },
                    leadingContent = {
                        Icon(Icons.Default.AutoDelete, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Switch(checked = autoDeleteEnabled, onCheckedChange = { viewModel.setAutoDeleteEnabled(it) })
                    },
                    modifier = Modifier.clickable { viewModel.setAutoDeleteEnabled(!autoDeleteEnabled) }
                )
            }

            // Auto delete period (constrained)
            if (autoDeleteEnabled) {
                add {
                    AutoDeletePeriodDropdown(
                        currentHours = autoDeletePeriodHours,
                        backupHours = autoBackupPeriodHours,
                        onSelect = { viewModel.setAutoDeletePeriodHours(it) }
                    )
                }
            }
        }
    })
}

@Composable
private fun AutoBackupPeriodDropdown(currentHours: Int, onSelect: (Int) -> Unit) {
    val periods = listOf(24 to R.string.auto_backup_period_1d,
        72 to R.string.auto_backup_period_3d,
        120 to R.string.auto_backup_period_5d,
        168 to R.string.auto_backup_period_1w,
        720 to R.string.auto_backup_period_1mo)
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = periods.find { it.first == currentHours }?.second ?: R.string.auto_backup_period_1d

    Box {
        SettingsItem(
            headlineContent = { Text(stringResource(R.string.auto_backup_period_label)) },
            supportingContent = { Text(stringResource(currentLabel), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingContent = {
                Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            periods.forEach { (hours, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = { onSelect(hours); expanded = false },
                    leadingIcon = if (hours == currentHours) {{ Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }} else {{}}
                )
            }
        }
    }
}

@Composable
private fun AutoDeletePeriodDropdown(currentHours: Int, backupHours: Int, onSelect: (Int) -> Unit) {
    val allPeriods = listOf(168 to R.string.auto_delete_period_1w,
        720 to R.string.auto_delete_period_1mo,
        8760 to R.string.auto_delete_period_1y)
    // Only show periods STRICTLY greater than backup period
    val validPeriods = allPeriods.filter { it.first > backupHours }
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = validPeriods.find { it.first == currentHours }?.second ?: validPeriods.firstOrNull()?.second ?: R.string.auto_delete_period_1w

    Box {
        SettingsItem(
            headlineContent = { Text(stringResource(R.string.auto_delete_period_label)) },
            supportingContent = { Text(stringResource(currentLabel), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingContent = {
                Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            validPeriods.forEach { (hours, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = { onSelect(hours); expanded = false },
                    leadingIcon = if (hours == currentHours) {{ Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }} else {{}}
                )
            }
        }
    }
}

@Composable
private fun AutoBackupDirectoryItem(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val directory by viewModel.settings.autoBackupDirectory.collectAsState()

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setAutoBackupDirectory(uri.toString())
        }
    }

    SettingsItem(
        headlineContent = { Text(stringResource(R.string.auto_backup_directory_label)) },
        supportingContent = {
            val displayPath = resolveDisplayPath(directory)
            Text(displayPath, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Default.FolderOpen, stringResource(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        },
        modifier = Modifier.clickable { dirPickerLauncher.launch(null) }
    )
}

@Composable
private fun AutoBackupCategoriesItem(viewModel: ChatViewModel) {
    val categories by viewModel.settings.autoBackupCategories.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val selectedKeys = categories.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val displaySummary = when {
        selectedKeys.isEmpty() -> stringResource(R.string.auto_backup_categories_desc)
        selectedKeys.size >= 4 -> stringResource(R.string.auto_backup_categories_desc)
        else -> selectedKeys.map { key -> stringResource(categoryLabelRes(key)) }.joinToString(", ")
    }

    SettingsItem(
        headlineContent = { Text(stringResource(R.string.auto_backup_categories_label)) },
        supportingContent = { Text(displaySummary, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Icon(Icons.Default.Topic, null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, stringResource(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        AutoBackupCategoriesDialog(
            selectedKeys = selectedKeys,
            onDismiss = { showDialog = false },
            onConfirm = { newKeys ->
                viewModel.setAutoBackupCategories(newKeys.joinToString(","))
                showDialog = false
            }
        )
    }
}

@Composable
private fun AutoBackupCategoriesDialog(
    selectedKeys: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val categoryOptions = listOf(
        "conversations" to R.string.export_category_conversations,
        "memories" to R.string.export_category_memories,
        "system_prompts" to R.string.export_category_system_prompts,
        "settings" to R.string.export_category_settings,
        "api_keys" to R.string.export_category_api_keys
    )
    var current by remember { mutableStateOf(selectedKeys) }
    val apiKeysChecked = "api_keys" in current
    val anyChecked = current.isNotEmpty()

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_backup_categories_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                categoryOptions.forEach { (key, labelRes) ->
                    CheckRow(
                        checked = key in current,
                        onToggle = { checked ->
                            current = if (checked) current + key else current - key
                        },
                        label = stringResource(labelRes)
                    )
                }
                if (apiKeysChecked) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.auto_backup_api_key_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }, enabled = anyChecked) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
