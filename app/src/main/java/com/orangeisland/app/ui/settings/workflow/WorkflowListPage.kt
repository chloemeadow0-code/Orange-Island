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
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.ui.settings.CollapsingSettingsLazyScaffold
import com.orangeisland.app.ui.settings.SettingsItem
import com.orangeisland.app.viewmodel.WorkflowViewModel
import com.orangeisland.app.workflow.WorkflowApprovalRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Workflow list screen. The AI-first v2 list shows linear workflows (the kind the assistant
 * authors) with a trigger-summary badge and a one-tap open into the read-only detail card. The
 * legacy graph-mode workflows (advanced users) are listed underneath in a separate section so the
 * two surfaces stay visually distinct. Empty state nudges the user toward the chat ("tell the
 * assistant to create one"); a "How do workflows work?" button opens an explanatory dialog.
 *
 * Independent implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListPage(
    viewModel: WorkflowViewModel,
    onBack: () -> Unit,
    onEdit: (Workflow) -> Unit,
    onLogs: (String) -> Unit,
    onOpenLinear: (LinearWorkflow) -> Unit = {}
) {
    val linear by viewModel.linearWorkflows.collectAsState()
    val graphWorkflows by viewModel.workflows.collectAsState()
    val runningIds by viewModel.runningWorkflowIds.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<LinearWorkflow?>(null) }
    var showHowDialog by remember { mutableStateOf(false) }

    val titleText = stringResource(R.string.workflows_title)

    CollapsingSettingsLazyScaffold(
        title = titleText,
        onBack = onBack,
        actions = {
            // "How do workflows work?" — opens an explanatory dialog for first-time users.
            TextButton(onClick = { showHowDialog = true }) {
                Text(stringResource(R.string.workflow_v2_how_it_works), style = MaterialTheme.typography.labelLarge)
            }
        }
    ) {
        // ── Empty state ────────────────────────────────────────────────────────
        if (linear.isEmpty() && graphWorkflows.isEmpty()) {
            item(key = "empty") { EmptyWorkflowItem() }
        }

        // ── Linear (AI-authored) workflows ─────────────────────────────────────
        if (linear.isNotEmpty()) {
            item(key = "linear_header") {
                Text(
                    stringResource(R.string.workflow_v2_mode_linear),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(linear, key = { "linear_${it.id}" }) { wf ->
                LinearWorkflowCard(
                    workflow = wf,
                    isRunning = wf.id in runningIds,
                    onClick = { onOpenLinear(wf) },
                    onRun = { viewModel.runWorkflow(wf.id) },
                    onToggleEnabled = { viewModel.setEnabled(wf.id, !wf.enabled) },
                    onDelete = { showDeleteConfirm = wf }
                )
            }
        }

        // ── Graph-mode (advanced) workflows ────────────────────────────────────
        if (graphWorkflows.isNotEmpty()) {
            item(key = "graph_header") {
                Text(
                    stringResource(R.string.workflow_v2_mode_graph),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            items(graphWorkflows, key = { "graph_${it.id}" }) { workflow ->
                val isRunning = workflow.id in runningIds
                GraphWorkflowCard(
                    workflow = workflow,
                    isRunning = isRunning,
                    onClick = { onEdit(workflow) },
                    onRun = { viewModel.runWorkflow(workflow.id) },
                    onToggleEnabled = { viewModel.setEnabled(workflow.id, !workflow.enabled) },
                    onEdit = { onEdit(workflow) },
                    onDuplicate = { viewModel.duplicateWorkflow(workflow.id) },
                    onLogs = { onLogs(workflow.id) }
                )
            }
        }

        // Extra space so nothing is hidden behind system bars.
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

    if (showHowDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showHowDialog = false },
            title = { Text(stringResource(R.string.workflow_v2_how_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.workflow_v2_how_body)) },
            confirmButton = {
                TextButton(onClick = { showHowDialog = false }) {
                    Text(stringResource(R.string.workflow_v2_how_close))
                }
            }
        )
    }
}

/** Empty-state card pointing the user to the chat ("tell the assistant to create one"). */
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
                    stringResource(R.string.workflow_v2_empty_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            },
            modifier = Modifier.heightIn(min = 64.dp)
        )
    }
}

/** A linear workflow row: name + trigger-summary badge + enable toggle + run/delete. */
@Composable
private fun LinearWorkflowCard(
    workflow: LinearWorkflow,
    isRunning: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
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
                Column {
                    Text(
                        WorkflowApprovalRenderer.triggerText(workflow.trigger),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        linearStatusText(workflow),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (workflow.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Switch(
                        checked = workflow.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.scale(0.8f)
                    )
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
                                text = {
                                    Text(
                                        stringResource(R.string.provider_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
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

/** Short status line for the linear card: enabled/disabled · last run. */
private fun linearStatusText(workflow: LinearWorkflow): String {
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
        append(if (workflow.enabled) "Enabled" else "Disabled")
        if (runInfo.isNotEmpty()) {
            append(" · $runInfo")
            if (date.isNotEmpty()) append(" $date")
        }
    }
}

/** The legacy graph-mode workflow card (kept from the original list page, untouched behavior). */
@Composable
private fun GraphWorkflowCard(
    workflow: Workflow,
    isRunning: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
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
                        append(" \u00B7 ")
                    }
                    append(graphStatusText(workflow))
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
                        }
                    }
                }
            },
            leadingSpacing = 16.dp
        )
    }
}

/** Returns a short human-readable status line for the graph-mode card. */
private fun graphStatusText(workflow: Workflow): String {
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
