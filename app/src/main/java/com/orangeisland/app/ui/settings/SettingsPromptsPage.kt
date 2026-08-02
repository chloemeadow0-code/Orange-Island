package com.orangeisland.app.ui.settings

import com.orangeisland.app.ui.components.DialogWindowEdgeToEdge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.data.DefaultSystemPrompt
import com.orangeisland.app.data.PromptTemplateItem
import com.orangeisland.app.data.SystemPromptEntry
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPromptsPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val systemPrompts by viewModel.settings.systemPrompts.collectAsState()
    val activeSystemPromptId by viewModel.settings.activeSystemPromptId.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()
    var editingEntry by remember { mutableStateOf<SystemPromptEntry?>(null) }
    var showDeletePromptConfirm by remember { mutableStateOf<SystemPromptEntry?>(null) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templateSheetState = rememberModalBottomSheetState()

    // Animate the sheet closed first, then flip to the editor page — avoids the sheet popping away
    // at the same instant the page transition starts.
    val pickTemplate: (SystemPromptEntry) -> Unit = { entry ->
        scope.launch { templateSheetState.hide() }.invokeOnCompletion {
            if (!templateSheetState.isVisible) {
                showTemplatePicker = false
                editingEntry = entry
            }
        }
    }

    BackHandler(enabled = editingEntry != null) {
        editingEntry = null
    }

    GuardedAnimatedContent(
        targetState = editingEntry,
        forward = editingEntry != null
    ) { currentEntry ->
        if (currentEntry != null) {
            SystemPromptEditorPage(
                entry = currentEntry,
                onSave = { title, systemItems, userPrependItems, userPostpendItems ->
                    if (systemPrompts.any { it.id == currentEntry.id }) {
                        viewModel.settings.updateSystemPrompt(currentEntry.id, title, systemItems, userPrependItems, userPostpendItems)
                        viewModel.onSystemPromptEdited(currentEntry.id, title)
                    } else {
                        viewModel.settings.addSystemPrompt(title, systemItems, userPrependItems, userPostpendItems)
                    }
                    editingEntry = null
                },
                onBack = { editingEntry = null },
                showDocFab = showDocFab
            )
        } else {
            PromptList(
                systemPrompts = systemPrompts,
                activeSystemPromptId = activeSystemPromptId,
                onSelectPrompt = { viewModel.settings.setActiveSystemPrompt(it) },
                onEdit = { editingEntry = it },
                onDuplicate = { entry ->
                    val copyTitle = context.getString(R.string.prompts_duplicate_title, entry.title)
                    editingEntry = entry.duplicateAsDraft(copyTitle)
                },
                onAdd = { showTemplatePicker = true },
                onDeleteRequest = { showDeletePromptConfirm = it },
                onBack = onBack
            )
        }
    }

    if (showTemplatePicker) {
        ModalBottomSheet(
            onDismissRequest = { showTemplatePicker = false },
            sheetState = templateSheetState,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DialogWindowEdgeToEdge()
            Text(
                text = stringResource(R.string.prompts_template_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsItem(
                headlineContent = { Text(stringResource(R.string.prompts_template_blank), fontWeight = FontWeight.Medium) },
                supportingContent = { Text(stringResource(R.string.prompts_template_blank_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingContent = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth().clickable {
                    pickTemplate(SystemPromptEntry(title = ""))
                }
            )
            SettingsItem(
                headlineContent = { Text(stringResource(R.string.prompts_template_default), fontWeight = FontWeight.Medium) },
                supportingContent = { Text(stringResource(R.string.prompts_template_default_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingContent = { IslandIcon(IslandIcons.SystemPrompt, size = 38.dp) },
                modifier = Modifier.fillMaxWidth().clickable {
                    pickTemplate(DefaultSystemPrompt.create(java.util.Locale.getDefault()))
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    showDeletePromptConfirm?.let { entry ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeletePromptConfirm = null },
            title = { Text(stringResource(R.string.prompts_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.prompts_delete_text, entry.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.settings.deleteSystemPrompt(entry.id)
                        showDeletePromptConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.provider_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeletePromptConfirm = null }) { Text(stringResource(R.string.provider_cancel)) } }
        )
    }
}

private fun SystemPromptEntry.duplicateAsDraft(title: String): SystemPromptEntry =
    copy(
        id = UUID.randomUUID().toString(),
        title = title,
        content = "",
        systemItems = resolvedSystemItems.copyWithNewIds(),
        userPrependItems = userPrependItems.copyWithNewIds(),
        userPostpendItems = userPostpendItems.copyWithNewIds()
    )

private fun List<PromptTemplateItem>.copyWithNewIds(): List<PromptTemplateItem> =
    map { it.copy(id = UUID.randomUUID().toString()) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptList(
    systemPrompts: List<SystemPromptEntry>,
    activeSystemPromptId: String?,
    onSelectPrompt: (String) -> Unit,
    onEdit: (SystemPromptEntry) -> Unit,
    onDuplicate: (SystemPromptEntry) -> Unit,
    onAdd: () -> Unit,
    onDeleteRequest: (SystemPromptEntry) -> Unit,
    onBack: () -> Unit
) {
    CollapsingSettingsScaffold(
        title = stringResource(R.string.prompts_title),
        onBack = onBack
    ) {
            val promptItems: List<@Composable () -> Unit> = buildList {
                if (systemPrompts.isEmpty()) {
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.prompts_empty_title), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            supportingContent = { Text(stringResource(R.string.prompts_empty_desc), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            leadingContent = { IslandIcon(IslandIcons.SystemPrompt, size = 38.dp) },
                            modifier = Modifier.heightIn(min = 64.dp)
                        )
                    }
                }
                systemPrompts.forEach { entry ->
                    add {
                        var showMenu by remember { mutableStateOf(false) }
                        SettingsItem(
                            headlineContent = { Text(entry.title, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                val preview = entry.resolvedSystemItems
                                    .firstOrNull { it.value.isNotBlank() }
                                    ?.value
                                    ?: entry.content
                                val text = preview.ifBlank { stringResource(R.string.prompts_empty_preview) }
                                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                RadioButton(selected = entry.id == activeSystemPromptId, onClick = { onSelectPrompt(entry.id) }, modifier = Modifier.size(24.dp))
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options), modifier = Modifier.size(18.dp))
                                    }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 16.dp, shape = RoundedCornerShape(12.dp)) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.provider_edit)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenu = false; onEdit(entry) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.prompts_duplicate)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.scale(0.9f)) }, onClick = { showMenu = false; onDuplicate(entry) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteRequest(entry) })
                                    }
                                }
                            },
                            leadingSpacing = 16.dp,
                            modifier = Modifier.clickable { onSelectPrompt(entry.id) }.padding(start = 8.dp)
                        )
                    }
                }

                add {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onAdd() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.prompts_add), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            SettingsGroupColumn {
                SettingsGroup(title = stringResource(R.string.prompts_title), items = promptItems)
            }
    }
}
