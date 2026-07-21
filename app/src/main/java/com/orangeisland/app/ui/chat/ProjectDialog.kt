package com.orangeisland.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.components.clearFocusOnTap

/**
 * Create-project dialog. Owns its own editable name + optional defaults (model/prompt);
 * on [onConfirm] the host writes the project via the view-model.
 *
 * The model/prompt pickers here intentionally reuse the SAME sources as the per-conversation
 * dialogs ([ChatSystemPromptDialog]) so "project default" and "chat override" stay in lock-step.
 *
 * @param initialModelId current project default model (null = use global default).
 * @param initialPromptId current project default system prompt (null = use global default).
 * @param availableModels selectable model ids (already resolved by the host).
 * @param promptList pairs of (id, title) for available system prompts.
 */
@Composable
internal fun ProjectCreateDialog(
    initialModelId: String? = null,
    initialPromptId: String? = null,
    availableModels: List<Pair<String, String>> = emptyList(),
    promptList: List<Pair<String, String>> = emptyList(),
    globalDefaultPromptTitle: String,
    globalDefaultModelTitle: String,
    onConfirm: (name: String, modelId: String?, promptId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var advancedOpen by remember { mutableStateOf(false) }
    var modelId by remember { mutableStateOf(initialModelId) }
    var promptId by remember { mutableStateOf(initialPromptId) }

    AlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_project), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(stringResource(R.string.project_name_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advancedOpen = !advancedOpen }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.project_advanced),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = advancedOpen) {
                    Column {
                        // Default model picker
                        Text(
                            stringResource(R.string.project_default_model),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        ProjectPickerColumn(
                            options = availableModels,
                            selectedId = modelId,
                            nullLabel = stringResource(R.string.project_use_global) + " ($globalDefaultModelTitle)",
                            onSelect = { modelId = it }
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        // Default system prompt picker
                        Text(
                            stringResource(R.string.project_default_prompt),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        ProjectPickerColumn(
                            options = promptList,
                            selectedId = promptId,
                            nullLabel = stringResource(R.string.project_use_global) + " ($globalDefaultPromptTitle)",
                            onSelect = { promptId = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), modelId, promptId) }
            ) {
                Text(stringResource(R.string.provider_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Rename-project dialog. Mirrors [ChatRenameDialog] exactly, but for project entities. */
@Composable
internal fun ProjectRenameDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_project), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Edit a project's defaults (model + system prompt) AND manage its private memory store.
 * Same shape as the advanced section of [ProjectCreateDialog] for the defaults, plus a
 * memory section that lists/creates/deletes files in the project's private memory dir.
 */
@Composable
internal fun ProjectSettingsDialog(
    projectName: String,
    initialModelId: String?,
    initialPromptId: String?,
    availableModels: List<Pair<String, String>>,
    promptList: List<Pair<String, String>>,
    globalDefaultPromptTitle: String,
    globalDefaultModelTitle: String,
    memoryFiles: List<com.orangeisland.app.data.MemoryManager.MemoryFileInfo>,
    onCreateMemoryFile: (name: String, content: String, description: String) -> Unit,
    onDeleteMemoryFile: (name: String) -> Unit,
    onSave: (modelId: String?, promptId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var modelId by remember { mutableStateOf(initialModelId) }
    var promptId by remember { mutableStateOf(initialPromptId) }
    // Inline "new memory file" form state — kept local to avoid spawning another dialog.
    var showMemoryForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(projectName, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                item {
                    Text(
                        stringResource(R.string.project_default_model),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    ProjectPickerColumn(
                        options = availableModels,
                        selectedId = modelId,
                        nullLabel = stringResource(R.string.project_use_global) + " ($globalDefaultModelTitle)",
                        onSelect = { modelId = it }
                    )
                }
                item {
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text(
                        stringResource(R.string.project_default_prompt),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    ProjectPickerColumn(
                        options = promptList,
                        selectedId = promptId,
                        nullLabel = stringResource(R.string.project_use_global) + " ($globalDefaultPromptTitle)",
                        onSelect = { promptId = it }
                    )
                }
                // ── Project-private memory store ──
                item {
                    Spacer(modifier = Modifier.padding(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.project_memory_title),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            newName = ""; newDesc = ""; newContent = ""
                            showMemoryForm = !showMemoryForm
                        }) {
                            Text(stringResource(R.string.memory_add))
                        }
                    }
                    Text(
                        stringResource(R.string.project_memory_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showMemoryForm) {
                    item {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                placeholder = { Text(stringResource(R.string.project_memory_file_hint)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            OutlinedTextField(
                                value = newDesc,
                                onValueChange = { newDesc = it },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                placeholder = { Text(stringResource(R.string.project_memory_desc_hint)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            OutlinedTextField(
                                value = newContent,
                                onValueChange = { newContent = it },
                                shape = RoundedCornerShape(16.dp),
                                placeholder = { Text(stringResource(R.string.project_memory_content_hint)) },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                TextButton(onClick = { showMemoryForm = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    enabled = newName.isNotBlank() && newContent.isNotBlank(),
                                    onClick = {
                                        onCreateMemoryFile(newName.trim(), newContent, newDesc.trim())
                                        showMemoryForm = false
                                        newName = ""; newDesc = ""; newContent = ""
                                    }
                                ) {
                                    Text(stringResource(R.string.provider_save))
                                }
                            }
                        }
                    }
                }
                if (memoryFiles.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.project_memory_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                } else {
                    items(memoryFiles) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                if (file.description.isNotBlank()) {
                                    Text(
                                        file.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            TextButton(
                                onClick = { onDeleteMemoryFile(file.name) },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(modelId, promptId) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Delete-project confirmation. Conversations inside are NOT deleted (reassured in body). */
@Composable
internal fun ProjectDeleteConfirmDialog(
    projectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_project), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.delete_project_confirm, projectName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Radio picker shared by create/settings dialogs. The first row is always "use global
 * default" (id = null); the rest mirror the option list.
 */
@Composable
private fun ProjectPickerColumn(
    options: List<Pair<String, String>>,
    selectedId: String?,
    nullLabel: String,
    onSelect: (String?) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 4.dp)
        ) {
            RadioButton(selected = selectedId == null, onClick = { onSelect(null) })
            Spacer(modifier = Modifier.width(8.dp))
            Text(nullLabel)
        }
        options.forEach { (id, title) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onSelect(id) }.padding(vertical = 4.dp)
            ) {
                RadioButton(selected = selectedId == id, onClick = { onSelect(id) })
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, maxLines = 1)
            }
        }
    }
}
