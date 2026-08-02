package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.ui.components.clearFocusOnTap
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMemoryPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val accessSavedMemories by viewModel.settings.accessSavedMemories.collectAsState()
    val accessActiveMemory by viewModel.settings.accessActiveMemory.collectAsState()
    var activeMemoryContent by remember { mutableStateOf("") }
    var memoryFiles by remember { mutableStateOf<List<com.orangeisland.app.data.MemoryManager.MemoryFileInfo>>(emptyList()) }
    var showFileEditor by remember { mutableStateOf<String?>(null) }
    var fileEditorContent by remember { mutableStateOf("") }
    var fileEditorDesc by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }
    var newFileDesc by remember { mutableStateOf("") }
    var showDeleteFileConfirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        activeMemoryContent = viewModel.memoryManager.getActiveMemory()
        memoryFiles = viewModel.memoryManager.listFiles()
    }
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    CollapsingSettingsScaffold(
        title = stringResource(R.string.memory_title),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("memory.md") }
    ) {
            SettingsGroupColumn {
                SettingsGroup(
                    title = stringResource(R.string.memory_access_title),
                    items = listOf(
                        {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.memory_access_saved)) },
                                supportingContent = { Text(stringResource(R.string.memory_access_saved_desc)) },
                                leadingContent = { IslandIcon(IslandIcons.SavedMemoryAccess, size = 38.dp) },
                                trailingContent = {
                                    Switch(checked = accessSavedMemories, onCheckedChange = { viewModel.settings.setAccessSavedMemories(it) })
                                },
                                modifier = Modifier.clickable { viewModel.settings.setAccessSavedMemories(!accessSavedMemories) }
                            )
                        },
                        {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.memory_access_active)) },
                                supportingContent = { Text(stringResource(R.string.memory_access_active_desc)) },
                                leadingContent = { IslandIcon(IslandIcons.ActiveMemoryAccess, size = 38.dp) },
                                trailingContent = {
                                    Switch(checked = accessActiveMemory, onCheckedChange = { viewModel.settings.setAccessActiveMemory(it) })
                                },
                                modifier = Modifier.clickable { viewModel.settings.setAccessActiveMemory(!accessActiveMemory) }
                            )
                        }
                    )
                )

                SettingsGroup(
                    title = stringResource(R.string.memory_active_title),
                    items = listOf(
                        {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.memory_active_context)) },
                                supportingContent = {
                                    Text(
                                        if (activeMemoryContent.isBlank()) stringResource(R.string.memory_active_empty)
                                        else activeMemoryContent.take(100) + if (activeMemoryContent.length > 100) "..." else ""
                                    )
                                },
                                leadingContent = { IslandIcon(IslandIcons.ActiveMemoryContext, size = 38.dp) },
                                modifier = Modifier.clickable {
                                    showFileEditor = "ACTIVE_MEMORY"
                                    fileEditorContent = activeMemoryContent
                                }
                            )
                        }
                    )
                )

                SettingsGroup(
                    title = stringResource(R.string.memory_saved_title),
                items = buildList {
                    if (memoryFiles.isEmpty()) {
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.memory_no_files), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                supportingContent = { Text(stringResource(R.string.memory_create_hint), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                leadingContent = { Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                                modifier = Modifier.heightIn(min = 64.dp)
                            )
                        }
                    } else {
                        memoryFiles.forEach { file ->
                            add {
                                var showFileMenu by remember { mutableStateOf(false) }
                                val displayName = file.name.removeSuffix(".md")
                                SettingsItem(
                                    headlineContent = { Text(displayName, fontWeight = FontWeight.Medium) },
                                    supportingContent = if (file.description.isNotBlank()) {{ Text(file.description) }} else null,
                                leadingContent = { IslandIcon(IslandIcons.SavedMemoryFiles, size = 38.dp) },
                                    trailingContent = {
                                        Box {
                                            IconButton(onClick = { showFileMenu = true }) {
                                                Icon(Icons.Default.MoreVert, stringResource(R.string.menu), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                            }
                                            DropdownMenu(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                tonalElevation = 16.dp,
                                                expanded = showFileMenu,
                                                onDismissRequest = { showFileMenu = false },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.provider_edit)) },
                                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                    onClick = {
                                                        showFileMenu = false
                                                        try {
                                                            showFileEditor = file.name
                                                            fileEditorContent = viewModel.memoryManager.readFile(file.name)
                                                            fileEditorDesc = viewModel.memoryManager.getDescription(file.name)
                                                        } catch (_: Exception) {}
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        showFileMenu = false
                                                        showDeleteFileConfirm = file.name
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    add {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { showNewFileDialog = true }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.memory_add), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            )
            }
            if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // Delete file confirmation
    showDeleteFileConfirm?.let { fileName ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeleteFileConfirm = null },
            title = { Text(stringResource(R.string.memory_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.memory_delete_text, fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.memoryManager.deleteFile(fileName)
                        memoryFiles = viewModel.memoryManager.listFiles()
                        showDeleteFileConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.provider_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteFileConfirm = null }) { Text(stringResource(R.string.provider_cancel)) } }
        )
    }

    // File Editor Dialog
    showFileEditor?.let { fileName ->
        val isActiveMemory = fileName == "ACTIVE_MEMORY"
        var editFileName by remember { mutableStateOf(if (isActiveMemory) "" else fileName.removeSuffix(".md")) }
        var editContent by remember { mutableStateOf(fileEditorContent) }
        var editDesc by remember { mutableStateOf(fileEditorDesc) }

        AlertDialog(
            modifier = Modifier.clearFocusOnTap(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = {
                showFileEditor = null
                fileEditorContent = ""
                fileEditorDesc = ""
            },
            title = { Text(if (isActiveMemory) stringResource(R.string.memory_edit_active) else stringResource(R.string.memory_edit), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (isActiveMemory) {
                        Text(
                            stringResource(R.string.memory_active_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        OutlinedTextField(
                            value = editFileName,
                            onValueChange = { editFileName = it },
                            label = { Text(stringResource(R.string.memory_title_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (!isActiveMemory) {
                        OutlinedTextField(
                            value = editDesc,
                            onValueChange = { editDesc = it },
                            label = { Text(stringResource(R.string.memory_desc_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text(stringResource(R.string.memory_content_hint)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isActiveMemory) {
                        viewModel.memoryManager.updateActiveMemory(editContent)
                        activeMemoryContent = viewModel.memoryManager.getActiveMemory()
                    } else {
                        if (editFileName.isNotBlank() && editFileName != fileName.removeSuffix(".md")) {
                            viewModel.memoryManager.deleteFile(fileName)
                            viewModel.memoryManager.createFile(editFileName, editContent, editDesc)
                        } else {
                            viewModel.memoryManager.editFile(fileName, editContent, description = editDesc)
                        }
                        memoryFiles = viewModel.memoryManager.listFiles()
                    }
                    showFileEditor = null
                    fileEditorContent = ""
                    fileEditorDesc = ""
                }) { Text(stringResource(R.string.provider_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFileEditor = null
                    fileEditorContent = ""
                    fileEditorDesc = ""
                }) { Text(stringResource(R.string.provider_cancel)) }
            }
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            modifier = Modifier.clearFocusOnTap(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(stringResource(R.string.memory_add_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text(stringResource(R.string.memory_title_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFileDesc,
                        onValueChange = { newFileDesc = it },
                        label = { Text(stringResource(R.string.memory_desc_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = { newFileContent = it },
                        label = { Text(stringResource(R.string.memory_content_hint)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFileName.isNotBlank()) {
                        try {
                            viewModel.memoryManager.createFile(newFileName, newFileContent, newFileDesc)
                            memoryFiles = viewModel.memoryManager.listFiles()
                        } catch (_: Exception) {}
                    }
                    showNewFileDialog = false
                    newFileName = ""
                    newFileContent = ""
                    newFileDesc = ""
                }) { Text(stringResource(R.string.memory_create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewFileDialog = false
                    newFileName = ""
                    newFileContent = ""
                    newFileDesc = ""
                }) { Text(stringResource(R.string.provider_cancel)) }
            }
        )
    }
}
