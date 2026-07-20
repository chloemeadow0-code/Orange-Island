package com.orangeisland.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.orangeisland.app.R
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsClaudeImportPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importProgress by viewModel.claudeImportProgress.collectAsState()
    val importResult by viewModel.claudeImportResult.collectAsState()
    val importPreview by viewModel.claudeImportPreview.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var importStrategy by remember { mutableStateOf<ImportStrategy>(ImportStrategy.MERGE) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            fileUri = uri
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(nameIdx) else null
            }
            fileName = name
            // Preview the file
            viewModel.previewClaudeChat(uri)
        }
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.claude_import_title),
        onBack = onBack
    ) {
            // File selection card
            SettingsGroupColumn {
                SettingsGroup(
                    title = stringResource(R.string.claude_import_title),
                    items = buildList {
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.claude_import_select_file)) },
                            supportingContent = {
                                if (fileName != null) {
                                    Text(fileName!!)
                                } else {
                                    Text(stringResource(R.string.claude_import_no_file))
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable { filePickerLauncher.launch(arrayOf("application/zip", "*/*")) }
                        )
                    }
                    if (importPreview != null) {
                        add {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        stringResource(R.string.claude_import_preview_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    val p = importPreview!!
                                    Text("${stringResource(R.string.claude_import_conversations)}: ${p.conversationCount}")
                                    Text("${stringResource(R.string.claude_import_messages)}: ${p.totalMessageCount}")
                                    Text("${stringResource(R.string.claude_import_human)}: ${p.humanMessageCount}")
                                    Text("${stringResource(R.string.claude_import_assistant)}: ${p.assistantMessageCount}")
                                    Text(
                                        if (p.hasAttachments) stringResource(R.string.claude_import_has_attachments)
                                        else stringResource(R.string.claude_import_no_attachments)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(onClick = { showImportDialog = true }) {
                                        Text(stringResource(R.string.claude_import_import))
                                    }
                                }
                            }
                        }
                    }
                }
            )
            }
    }

    // Import strategy dialog
    if (showImportDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.claude_import_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.claude_import_strategy),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    StrategyChip(
                        stringResource(R.string.claude_import_replace),
                        importStrategy == ImportStrategy.REPLACE
                    ) { importStrategy = ImportStrategy.REPLACE }
                    StrategyChip(
                        stringResource(R.string.claude_import_merge),
                        importStrategy == ImportStrategy.MERGE
                    ) { importStrategy = ImportStrategy.MERGE }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.import_strategy_merge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    fileUri?.let {
                        scope.launch {
                            viewModel.importClaudeChat(it, importStrategy, emptySet())
                        }
                    }
                }) {
                    Text(stringResource(R.string.claude_import_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Success dialog
    if (showSuccessDialog && importResult != null) {
        val result = importResult!!
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showSuccessDialog = false },
            title = { Text(stringResource(R.string.claude_import_success), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("${stringResource(R.string.claude_import_conversations)}: ${result.conversationsImported}")
                    Text("${stringResource(R.string.claude_import_messages)}: ${result.messagesImported}")
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Errors: ${result.errors.joinToString(", ")}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    viewModel.clearClaudeImportState()
                }) {
                    Text(stringResource(R.string.provider_close))
                }
            }
        )
    }

    // Progress overlay
    if (importProgress != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.claude_import_progress), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { importProgress!! },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(importProgress!! * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

enum class ImportStrategy { MERGE, REPLACE }

@Composable
private fun StrategyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(50)
    )
}
