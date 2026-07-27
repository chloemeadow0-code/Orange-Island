package com.orangeisland.app.ui.settings.workflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.local.WorkflowRunEntity
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.ui.settings.CollapsingSettingsLazyScaffold
import com.orangeisland.app.ui.settings.SettingsItem
import com.orangeisland.app.viewmodel.WorkflowViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Run-history screen for a single workflow. Lists every execution with status, timing, and message.
 * Tapping a row opens a bottom sheet with the full log JSON.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunLogPage(
    workflowId: String,
    workflowName: String,
    viewModel: WorkflowViewModel,
    onBack: () -> Unit
) {
    val runs by viewModel.observeRuns(workflowId).collectAsState(initial = emptyList())
    var selectedRun by remember { mutableStateOf<WorkflowRunEntity?>(null) }

    CollapsingSettingsLazyScaffold(
        title = stringResource(R.string.workflow_log_title),
        onBack = onBack
    ) {
        if (runs.isEmpty()) {
            item(key = "empty") {
                EmptyRunLogItem()
            }
        }

        items(runs, key = { it.runId }) { run ->
            RunLogCard(
                run = run,
                onClick = { selectedRun = run }
            )
        }
    }

    selectedRun?.let { run ->
        ModalBottomSheet(
            onDismissRequest = { selectedRun = null },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            RunDetailSheet(run = run)
        }
    }
}

@Composable
private fun EmptyRunLogItem() {
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
                    stringResource(R.string.workflow_log_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            modifier = Modifier.heightIn(min = 64.dp)
        )
    }
}

@Composable
private fun RunLogCard(
    run: WorkflowRunEntity,
    onClick: () -> Unit
) {
    val status = runStatusOf(run.status)
    val (icon, tint) = statusIcon(status)
    val duration = run.durationText()

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
                Text(run.message.ifBlank { run.status }, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Text(
                    buildString {
                        append(run.startedAt.formatDateTime())
                        if (duration.isNotBlank()) {
                            append(" · ")
                            append(duration)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            },
            trailingContent = {
                StatusChip(status = status)
            }
        )
    }
}

@Composable
private fun StatusChip(status: RunStatus) {
    val (container, content) = when (status) {
        RunStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        RunStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        RunStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        RunStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RunDetailSheet(run: WorkflowRunEntity) {
    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Text(
            text = run.workflowName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        DetailRow(label = "Status", value = run.status)
        DetailRow(label = "Message", value = run.message)
        DetailRow(label = "Started", value = run.startedAt.formatDateTime())
        run.finishedAt?.let {
            DetailRow(label = "Finished", value = it.formatDateTime())
            DetailRow(label = "Duration", value = run.durationText())
        }
        run.startNodeId?.let {
            DetailRow(label = "Start Node", value = it)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!run.logsJson.isNullOrBlank()) {
            Text("Logs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = formatRunLogs(run.logsJson),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun runStatusOf(name: String): RunStatus =
    RunStatus.entries.find { it.name == name } ?: RunStatus.FAILED

private fun statusIcon(status: RunStatus): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> {
    return when (status) {
        RunStatus.SUCCESS -> Icons.Default.Check to androidx.compose.ui.graphics.Color(0xFF4CAF50)
        RunStatus.FAILED -> Icons.Default.Close to androidx.compose.ui.graphics.Color(0xFFE91E63)
        RunStatus.RUNNING -> Icons.Default.HourglassEmpty to androidx.compose.ui.graphics.Color(0xFF2196F3)
        RunStatus.CANCELLED -> Icons.Default.Block to androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    }
}

private fun Long.formatDateTime(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))

private fun WorkflowRunEntity.durationText(): String {
    val end = finishedAt ?: return ""
    val ms = end - startedAt
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> "${ms / 1000}s"
        else -> String.format(Locale.getDefault(), "%d:%02d", ms / 60_000, (ms % 60_000) / 1000)
    }
}

/**
 * Pretty-print a run's [WorkflowRunEntity.logsJson] (a JSON array of RunLogEntry) into one line per
 * entry, e.g. `[DEBUG] (查前台app) Calling tool get_foreground_app`. Falls back to the raw JSON if
 * parsing fails so the user is never left with nothing. Without this the sheet showed a single
 * unscrollable wall of minified JSON that truncated past 240dp.
 */
private fun formatRunLogs(logsJson: String): String {
    return try {
        val arr = org.json.JSONArray(logsJson)
        buildString {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val level = obj.optString("level", "")
                val nodeLabel = obj.optString("nodeLabel", "").takeIf { it.isNotBlank() && it != "null" }
                val message = obj.optString("message", "")
                if (isNotEmpty()) append('\n')
                append('[').append(level).append("] ")
                if (nodeLabel != null) append('(').append(nodeLabel).append(") ")
                append(message)
            }
        }
    } catch (_: Exception) {
        // Not valid JSON — show the raw text so nothing is hidden.
        logsJson
    }
}
