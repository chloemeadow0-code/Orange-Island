package com.orangeisland.app.ui.settings.workflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.ui.settings.CollapsingSettingsLazyScaffold
import com.orangeisland.app.ui.settings.SettingsItem
import com.orangeisland.app.viewmodel.WorkflowViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Workflow list screen: browse, enable/disable, run, edit, duplicate, delete, and view logs.
 *
 * Uses [CollapsingSettingsLazyScaffold] so the collapsing title matches every other settings page.
 * Navigation to the editor and log pages is caller-supplied via [onEdit] and [onLogs].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListPage(
    viewModel: WorkflowViewModel,
    onBack: () -> Unit,
    onEdit: (Workflow) -> Unit,
    onLogs: (String) -> Unit
) {
    val workflows by viewModel.workflows.collectAsState()
    val runningIds by viewModel.runningWorkflowIds.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<Workflow?>(null) }

    val titleText = stringResource(R.string.workflows_title)
    val newWorkflowName = stringResource(R.string.workflow_new_name)
    val addLabel = stringResource(R.string.workflows_add)
    val runLabel = stringResource(R.string.workflow_run)
    val logsLabel = stringResource(R.string.workflow_logs)
    val editLabel = stringResource(R.string.provider_edit)
    val duplicateLabel = stringResource(R.string.prompts_duplicate)
    val deleteLabel = stringResource(R.string.provider_delete)
    val optionsLabel = stringResource(R.string.options)
    val emptyTitle = stringResource(R.string.workflows_empty_title)
    val emptyDesc = stringResource(R.string.workflows_empty_desc)
    val deleteTitle = stringResource(R.string.workflow_delete_title)
    val cancelLabel = stringResource(R.string.provider_cancel)

    CollapsingSettingsLazyScaffold(
        title = titleText,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val created = viewModel.createWorkflow(
                        name = newWorkflowName,
                        description = ""
                    )
                    onEdit(created)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = addLabel)
            }
        }
    ) {
        if (workflows.isEmpty()) {
            item(key = "empty") {
                EmptyWorkflowItem()
            }
        }

        items(workflows, key = { it.id }) { workflow ->
            val isRunning = workflow.id in runningIds
            WorkflowCard(
                workflow = workflow,
                isRunning = isRunning,
                onClick = { onEdit(workflow) },
                onRun = { viewModel.runWorkflow(workflow.id) },
                onToggleEnabled = { viewModel.setEnabled(workflow.id, !workflow.enabled) },
                onEdit = { onEdit(workflow) },
                onDuplicate = { viewModel.duplicateWorkflow(workflow.id) },
                onDelete = { showDeleteConfirm = workflow },
                onLogs = { onLogs(workflow.id) }
            )
        }

        // Extra space so the FAB never covers the last card.
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    showDeleteConfirm?.let { wf ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.workflow_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.workflow_delete_text, wf.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWorkflow(wf.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.provider_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.provider_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyWorkflowItem() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SettingsItem(
            headlineContent = {
                Text(
                    stringResource(R.string.workflows_empty_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.workflows_empty_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            modifier = Modifier.heightIn(min = 64.dp)
        )
    }
}

@Composable
private fun WorkflowCard(
    workflow: Workflow,
    isRunning: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onLogs: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick)
    ) {
        SettingsItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        workflow.name,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isRunning) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            supportingContent = {
                val meta = buildString {
                    if (workflow.description.isNotBlank()) {
                        append(workflow.description)
                        append(" 鈥? ")
                    }
                    append(statusText(workflow))
                }
                Text(
                    meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = if (workflow.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Run button (only when enabled and not already running)
                    if (workflow.enabled && !isRunning) {
                        IconButton(onClick = onRun, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.workflow_run),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Enable toggle
                    Switch(
                        checked = workflow.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.scale(0.8f)
                    )

                    // Overflow menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.options),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 16.dp,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.provider_edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { showMenu = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workflow_logs)) },
                                leadingIcon = { Icon(Icons.Default.History, null) },
                                onClick = { showMenu = false; onLogs() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.prompts_duplicate)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        null,
                                        modifier = Modifier.scale(0.9f)
                                    )
                                },
                                onClick = { showMenu = false; onDuplicate() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.provider_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }
            },
            leadingSpacing = 16.dp
        )
    }
}

/** Returns a short human-readable status line for the workflow card. */
private fun statusText(workflow: Workflow): String {
    val runInfo = when (workflow.lastRunStatus) {
        RunStatus.SUCCESS.name -> "✓"
        RunStatus.FAILED.name -> "✗"
        RunStatus.RUNNING.name -> "…"
        RunStatus.CANCELLED.name -> "○"
        else -> ""
    }
    val date = workflow.lastRunAt?.let {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it))
    } ?: ""
    return buildString {
        if (!workflow.enabled) append("Disabled")
        else append("Enabled")
        if (runInfo.isNotEmpty()) {
            append(" · $runInfo")
            if (date.isNotEmpty()) append(" $date")
        }
    }
}
