package com.orangeisland.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.ChatConversation
import com.orangeisland.app.ui.components.clearFocusOnTap
import com.orangeisland.app.ui.settings.CollapsingSettingsLazyScaffold
import com.orangeisland.app.ui.settings.CollapsingSettingsScaffold
import com.orangeisland.app.ui.settings.SettingsGroup
import com.orangeisland.app.ui.settings.SettingsGroupColumn
import com.orangeisland.app.ui.settings.SettingsItem
import java.text.SimpleDateFormat
import java.util.Locale

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
 * Internal navigation state for [ProjectSettingsScreen]. Each route corresponds to a
 * full-screen sub-page; selection on a picker sub-page applies immediately and pops
 * back to [Main].
 */
private sealed class ProjectSettingsRoute {
    object Main : ProjectSettingsRoute()
    object EditDetails : ProjectSettingsRoute()
    object MemoryList : ProjectSettingsRoute()
    object MemoryCreate : ProjectSettingsRoute()
}

/**
 * Full-screen replacement for the old single-AlertDialog project settings. Renders as a
 * workbench-style project home: title + ⋮ menu (edit/delete), two card tiles (memory /
 * prompt defaults), a "recent conversations" list, and a floating "new chat" button.
 * Edit-details and memory CRUD live on their own sub-pages.
 */
@Composable
internal fun ProjectSettingsScreen(
    projectId: String,
    projectName: String,
    initialModelId: String?,
    initialPromptId: String?,
    availableModels: List<Pair<String, String>>,
    promptList: List<Pair<String, String>>,
    promptPreviewContent: Map<String, String> = emptyMap(),
    globalDefaultPromptTitle: String,
    globalDefaultModelTitle: String,
    memoryFiles: List<com.orangeisland.app.data.MemoryManager.MemoryFileInfo>,
    conversationsInProject: List<ChatConversation>,
    onCreateMemoryFile: (name: String, content: String, description: String) -> Unit,
    onDeleteMemoryFile: (name: String) -> Unit,
    onEditMemoryFile: (name: String, newContent: String, newDescription: String) -> Unit,
    onSave: (name: String, modelId: String?, promptId: String?) -> Unit,
    onOpenConversation: (String) -> Unit,
    onCreateChatInProject: () -> Unit,
    onDeleteProject: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var route by remember { mutableStateOf<ProjectSettingsRoute>(ProjectSettingsRoute.Main) }
    var modelId by remember { mutableStateOf(initialModelId) }
    var promptId by remember { mutableStateOf(initialPromptId) }
    var editingName by remember { mutableStateOf(projectName) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedMemoryFile by remember {
        mutableStateOf<com.orangeisland.app.data.MemoryManager.MemoryFileInfo?>(null)
    }
    var pendingDeleteFile by remember {
        mutableStateOf<com.orangeisland.app.data.MemoryManager.MemoryFileInfo?>(null)
    }

    when (val r = route) {
        ProjectSettingsRoute.Main -> {
            val dateFmt = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.project_edit_details)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { showMenu = false; editingName = projectName; route = ProjectSettingsRoute.EditDetails }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; onDeleteProject() }
                                )
                            }
                        }
                    }
                    Text(projectName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.project_conversations_count, conversationsInProject.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f).clickable { route = ProjectSettingsRoute.MemoryList }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.project_memory_card_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.project_files_count, memoryFiles.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f).clickable { editingName = projectName; route = ProjectSettingsRoute.EditDetails }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.project_prompt_card_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    promptList.find { it.first == promptId }?.second ?: (stringResource(R.string.project_use_global) + " ($globalDefaultPromptTitle)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.project_recent_conversations),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val sortedConvs = remember(conversationsInProject) { conversationsInProject.sortedByDescending { it.lastUpdated } }
                    sortedConvs.forEach { conv ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenConversation(conv.id) }
                                .padding(vertical = 10.dp, horizontal = 2.dp)
                        ) {
                            Text(conv.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                dateFmt.format(java.util.Date(conv.lastUpdated)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    Spacer(modifier = Modifier.height(96.dp))
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onCreateChatInProject() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.project_new_chat_entry), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }

        ProjectSettingsRoute.EditDetails -> {
            CollapsingSettingsScaffold(
                title = stringResource(R.string.project_edit_details),
                onBack = { route = ProjectSettingsRoute.Main },
                actions = {
                    TextButton(onClick = {
                        onSave(editingName.trim().ifBlank { projectName }, modelId, promptId)
                        route = ProjectSettingsRoute.Main
                    }) { Text(stringResource(R.string.provider_save)) }
                }
            ) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(10.dp))
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
                Spacer(modifier = Modifier.padding(10.dp))
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
                Spacer(modifier = Modifier.padding(10.dp))
                Text(
                    "ID（长按复制）",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                // Generate a deterministic plugin window id from the project id so it stays
                // stable across visits while still being "random-looking" to the user.
                val pluginWindowId = remember(projectId) {
                    "win_" + java.util.UUID.nameUUIDFromBytes(projectId.toByteArray())
                        .toString().replace("-", "").take(16)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        listOf(
                            "项目 ID" to projectId,
                            "模型 ID" to (modelId ?: "默认"),
                            "提示词 ID" to (promptId ?: "默认"),
                            "插件窗口 ID" to pluginWindowId
                        ).forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer {
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ProjectSettingsRoute.MemoryList -> {
            CollapsingSettingsLazyScaffold(
                title = stringResource(R.string.project_memory_entry),
                onBack = { route = ProjectSettingsRoute.Main },
                actions = {
                    TextButton(onClick = { route = ProjectSettingsRoute.MemoryCreate }) {
                        Text(stringResource(R.string.memory_add))
                    }
                }
            ) {
                item {
                    Text(
                        stringResource(R.string.project_memory_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                if (memoryFiles.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.project_memory_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(memoryFiles) { file ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMemoryFile = file }
                                .padding(vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name.removeSuffix(".md"), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                                if (file.description.isNotBlank()) {
                                    Text(
                                        file.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (file.createdAt > 0L) {
                                    val memoryManager = remember { com.orangeisland.app.data.MemoryManager(context) }
                                    Text(
                                        stringResource(R.string.project_memory_created_at, memoryManager.formatCreatedAt(file.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            IconButton(onClick = { pendingDeleteFile = file }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }

        ProjectSettingsRoute.MemoryCreate -> {
            var newName by remember { mutableStateOf("") }
            var newDesc by remember { mutableStateOf("") }
            var newContent by remember { mutableStateOf("") }
            CollapsingSettingsScaffold(
                title = stringResource(R.string.project_memory_new_title),
                onBack = { route = ProjectSettingsRoute.MemoryList },
                actions = {
                    TextButton(
                        enabled = newName.isNotBlank() && newContent.isNotBlank(),
                        onClick = {
                            onCreateMemoryFile(newName.trim(), newContent, newDesc.trim())
                            route = ProjectSettingsRoute.MemoryList
                        }
                    ) { Text(stringResource(R.string.provider_save)) }
                }
            ) {
                SettingsGroupColumn {
                    SettingsGroup(
                        title = "",
                        items = listOf(
                            {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    OutlinedTextField(
                                        value = newName, onValueChange = { newName = it }, singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        placeholder = { Text(stringResource(R.string.project_memory_filename_hint)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newDesc, onValueChange = { newDesc = it }, singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        placeholder = { Text(stringResource(R.string.project_memory_desc_hint2)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newContent, onValueChange = { newContent = it },
                                        shape = RoundedCornerShape(16.dp),
                                        placeholder = { Text(stringResource(R.string.project_memory_content_hint2)) },
                                        minLines = 8,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        )
                    )
                }
            }
        }
    }

    // ── 记忆详情/编辑弹窗（Dialog，不是子页面）──
    selectedMemoryFile?.let { file ->
        MemoryDetailEditDialog(
            file = file,
            onSave = { newDesc, newContent ->
                onEditMemoryFile(file.name, newContent, newDesc)
                selectedMemoryFile = null
            },
            onRequestDelete = {
                pendingDeleteFile = file
                selectedMemoryFile = null
            },
            onDismiss = { selectedMemoryFile = null }
        )
    }

    // ── 删除二次确认 ──
    pendingDeleteFile?.let { file ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { pendingDeleteFile = null },
            title = { Text(stringResource(R.string.project_memory_delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.project_memory_delete_confirm_text, file.name.removeSuffix(".md"))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMemoryFile(file.name)
                        pendingDeleteFile = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFile = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * 详情/编辑弹窗：预填当前内容与描述，保存时回调 [onSave]，删除走二次确认。
 */
@Composable
private fun MemoryDetailEditDialog(
    file: com.orangeisland.app.data.MemoryManager.MemoryFileInfo,
    onSave: (newDescription: String, newContent: String) -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val memoryManager = remember { com.orangeisland.app.data.MemoryManager(context) }
    var content by remember(file.name) {
        mutableStateOf(
            try { memoryManager.readFile(file.name, file.projectId) } catch (_: Exception) { "" }
        )
    }
    var desc by remember(file.name) { mutableStateOf(file.description) }

    AlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(file.name.removeSuffix(".md"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (file.createdAt > 0L) {
                    Text(
                        stringResource(R.string.project_memory_created_at, memoryManager.formatCreatedAt(file.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it }, singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(stringResource(R.string.project_memory_desc_hint2)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    shape = RoundedCornerShape(16.dp),
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = onRequestDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { onSave(desc.trim(), content) }) {
                    Text(stringResource(R.string.provider_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
    onSelect: (String?) -> Unit,
    previewContent: Map<String, String>? = null
) {
    var previewingId by remember { mutableStateOf<String?>(null) }
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
                Text(title, maxLines = 1, modifier = Modifier.weight(1f))
                if (previewContent != null) {
                    IconButton(
                        onClick = { previewingId = id },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.project_prompt_preview), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    if (previewContent != null) {
        previewingId?.let { id ->
            val title = options.find { it.first == id }?.second ?: ""
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { previewingId = null },
                title = { Text(title, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(
                                previewContent[id]?.ifBlank { stringResource(R.string.project_prompt_preview_empty) }
                                    ?: stringResource(R.string.project_prompt_preview_empty),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { previewingId = null }) { Text(stringResource(R.string.ok)) }
                }
            )
        }
    }
}
