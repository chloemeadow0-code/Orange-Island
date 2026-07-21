package com.orangeisland.app.ui.settings.workflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
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
import com.orangeisland.app.model.LinearAction
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.ui.settings.CollapsingSettingsLazyScaffold
import com.orangeisland.app.viewmodel.WorkflowViewModel
import com.orangeisland.app.workflow.WorkflowApprovalRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/**
 * Read-only detail page for an AI-authored linear workflow. Shows the trigger summary, the
 * condition list (rendered with [WorkflowApprovalRenderer.conditionText]), an ordered action
 * list with expandable args, run statistics, recent run history, and a bottom action bar:
 * Run now / Edit in chat / Delete.
 *
 * Used by [SettingsWorkflowPage] when the user taps a **linear-mode** workflow in the list.
 * Graph-mode workflows still route to [WorkflowEditorForm] — they can't be summarised by a
 * trigger+conditions+actions card.
 *
 * Independent implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDetailPage(
    workflow: LinearWorkflow,
    viewModel: WorkflowViewModel,
    onBack: () -> Unit,
    onEditInChat: (prefilledPrompt: String) -> Unit
) {
    val runs by viewModel.runs.collectAsState()
    val runningIds by viewModel.runningWorkflowIds.collectAsState()
    val isRunning = workflow.id in runningIds
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Prefilled prompt for the "Edit in chat" button — built here (Composable scope) so the
    // onClick lambda doesn't need to call stringResource (which is a @Composable).
    val editPrompt = stringResource(R.string.workflow_v2_edit_prompt, workflow.name)

    CollapsingSettingsLazyScaffold(
        title = workflow.name,
        onBack = onBack,
        actions = {
            // Enabled / disabled toggle in the top bar — one-tap control without scrolling.
            Switch(
                checked = workflow.enabled,
                onCheckedChange = { viewModel.setEnabled(workflow.id, it) },
                modifier = Modifier.scale(0.85f)
            )
        }
    ) {
        item(key = "header") {
            DetailHeader(workflow)
        }
        item(key = "trigger") {
            SectionCard(title = stringResource(R.string.workflow_v2_trigger)) {
                Text(
                    WorkflowApprovalRenderer.triggerText(workflow.trigger),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        item(key = "conditions") {
            SectionCard(title = stringResource(R.string.workflow_v2_conditions)) {
                if (workflow.conditions.isEmpty()) {
                    Text(
                        stringResource(R.string.workflow_v2_conditions_always),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    workflow.conditions.forEach { c ->
                        Text(
                            "• " + WorkflowApprovalRenderer.conditionText(c),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        items(workflow.actions, key = { "action_${it.tool}_${it.hashCode()}" }) { action ->
            ActionRow(action)
        }
        item(key = "stats") {
            SectionCard(title = stringResource(R.string.workflow_v2_stats)) {
                StatsRow(workflow, runs)
            }
        }
        item(key = "history_header") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.workflow_v2_run_history),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (runs.isEmpty()) {
            item(key = "history_empty") {
                Text(
                    stringResource(R.string.workflow_v2_never),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(runs.take(20), key = { it.runId }) { run ->
                RunHistoryRow(run)
            }
        }
        // Bottom action bar sits above the list so it's always reachable; the extra spacer keeps
        // the last list item from being hidden behind it.
        item(key = "action_spacer") { Spacer(modifier = Modifier.height(96.dp)) }
    }

    // Bottom action bar overlay.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { viewModel.runWorkflow(workflow.id) },
                    enabled = workflow.enabled && !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.workflow_v2_run_now))
                }
                OutlinedButton(
                    onClick = {
                        onEditInChat(editPrompt)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.workflow_v2_edit_in_chat))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.workflow_v2_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(stringResource(R.string.workflow_delete_title), fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.workflow_delete_text, workflow.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWorkflow(workflow.id)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.provider_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.provider_cancel))
                }
            }
        )
    }
}

/** Title + status header at the top of the detail page. */
@Composable
private fun DetailHeader(workflow: LinearWorkflow) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                workflow.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (workflow.enabled) stringResource(R.string.workflow_v2_enabled)
                        else stringResource(R.string.workflow_v2_disabled)
                    )
                }
            )
        }
        if (workflow.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                workflow.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A titled rounded card wrapping arbitrary content. */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** One action row: numbered tool name, expandable to show the args JSON. */
@Composable
private fun ActionRow(action: LinearAction) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${action.tool}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.workflow_v2_args),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.workflow_v2_args),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        prettyArgs(action.args),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Format the action's args JSON object as a multi-line `key = value` list (sensitive keys masked). */
private fun prettyArgs(args: JsonObject): String {
    if (args.isEmpty()) return "—"
    return args.entries.joinToString("\n") { (k, v) ->
        val value = if (isSensitive(k)) "***" else v.toString().trim('"')
        "$k = $value"
    }
}

private val SENSITIVE = setOf(
    "token", "password", "passphrase", "private_key", "privatekey",
    "secret", "api_key", "apikey", "authorization", "auth_token", "access_token",
    "client_secret", "credential", "credentials"
)

private fun isSensitive(key: String): Boolean {
    val lower = key.lowercase().replace("-", "_").replace(" ", "_")
    return SENSITIVE.any { part -> lower == part || lower.contains(part) }
}

/** Two-column stats grid: last run, runs today, cooldown, daily cap. */
@Composable
private fun StatsRow(workflow: LinearWorkflow, runs: List<com.orangeisland.app.data.local.WorkflowRunEntity>) {
    val lastRun = runs.firstOrNull()
    val runsToday = runs.count {
        isToday(it.startedAt) && (it.status == RunStatus.SUCCESS.name || it.status == RunStatus.FAILED.name)
    }
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatLine(
            stringResource(R.string.workflow_v2_last_run),
            lastRun?.let { fmt.format(Date(it.startedAt)) } ?: stringResource(R.string.workflow_v2_never)
        )
        StatLine(stringResource(R.string.workflow_v2_runs_today), runsToday.toString())
        StatLine(
            stringResource(R.string.workflow_v2_cooldown),
            if (workflow.cooldownMs == 0L) stringResource(R.string.workflow_v2_none)
            else WorkflowApprovalRenderer.formatDuration(workflow.cooldownMs)
        )
        StatLine(
            stringResource(R.string.workflow_v2_daily_cap),
            workflow.maxRunsPerDay?.let { "$it" } ?: stringResource(R.string.workflow_v2_no_limit)
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunHistoryRow(run: com.orangeisland.app.data.local.WorkflowRunEntity) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
    val (icon, color) = when (run.status) {
        RunStatus.SUCCESS.name -> Icons.Default.Check to MaterialTheme.colorScheme.primary
        RunStatus.FAILED.name -> Icons.Default.Close to MaterialTheme.colorScheme.error
        RunStatus.RUNNING.name -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.tertiary
        else -> Icons.Default.Remove to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                fmt.format(Date(run.startedAt)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                run.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun isToday(epochMs: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val cal2 = java.util.Calendar.getInstance()
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
        cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}
