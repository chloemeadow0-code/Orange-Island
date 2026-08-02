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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build
import com.orangeisland.app.R
import com.orangeisland.app.model.RunStatus
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.model.FlowNode
import com.orangeisland.app.model.FlowEdge
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.ActionNode
import com.orangeisland.app.model.BranchNode
import com.orangeisland.app.model.TransformNode
import com.orangeisland.app.model.NodeValue
import com.orangeisland.app.model.Comparison
import com.orangeisland.app.model.EdgeGuard
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.model.TransformOp
import com.orangeisland.app.ui.settings.CollapsingSettingsLazyScaffold
import com.orangeisland.app.ui.settings.SettingsItem
import com.orangeisland.app.util.PowerWhitelistHelper
import com.orangeisland.app.viewmodel.WorkflowViewModel
import com.orangeisland.app.workflow.WorkflowApprovalRenderer
import kotlinx.coroutines.launch
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
    onOpenLinear: (LinearWorkflow) -> Unit = {},
    onCreateGraph: (workflowId: String) -> Unit = {}
) {
    val linear by viewModel.linearWorkflows.collectAsState()
    val graphWorkflows by viewModel.workflows.collectAsState()
    val runningIds by viewModel.runningWorkflowIds.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<LinearWorkflow?>(null) }
    var showHowDialog by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }

    val titleText = stringResource(R.string.workflows_title)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        CollapsingSettingsLazyScaffold(
            title = titleText,
            onBack = onBack,
            actions = {
                // "How do workflows work?" — opens an explanatory dialog for first-time users.
                TextButton(onClick = { showHowDialog = true }) {
                    Text(stringResource(R.string.workflow_v2_how_it_works), style = MaterialTheme.typography.labelLarge)
                }
            },
            floatingActionButton = {
                // Create a new graph-mode workflow (advanced, manual). Linear (AI-authored) workflows
                // are created from chat, so this FAB targets the graph editor exclusively.
                ExtendedFloatingActionButton(
                    onClick = { showNewDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.workflow_new_graph)) }
                )
            }
        ) {
            // ── Background keep-alive card ─────────────────────────────────────────
            item(key = "keepalive") {
                KeepAliveCard(
                    onRequestIgnoreBattery = {
                        PowerWhitelistHelper.requestIgnoreBatteryOptimizations(context)
                    },
                    onOpenAutoStart = { result ->
                        val message = when (result) {
                            PowerWhitelistHelper.AutoStartResult.VENDOR_PAGE ->
                                context.getString(R.string.oi_keepalive_snackbar_vendor, Build.MANUFACTURER)
                            PowerWhitelistHelper.AutoStartResult.APP_DETAIL_FALLBACK ->
                                context.getString(R.string.oi_keepalive_snackbar_fallback)
                            PowerWhitelistHelper.AutoStartResult.FAILED ->
                                context.getString(R.string.oi_keepalive_snackbar_fallback)
                        }
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                )
            }

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
                    onLogs = { onLogs(workflow.id) },
                    onDelete = { viewModel.deleteWorkflow(workflow.id) }
                )
            }
        }

        // Extra space so nothing is hidden behind system bars.
        item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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

    if (showNewDialog) {
        GraphWorkflowTemplateDialog(
            onCancel = { showNewDialog = false },
            onCreate = { name, templateId ->
                showNewDialog = false
                val template = graphWorkflowTemplates().find { it.id == templateId }
                val wf = viewModel.createWorkflow(
                    name = name,
                    description = template?.description ?: "",
                    nodes = template?.nodes ?: emptyList(),
                    edges = template?.edges ?: emptyList()
                )
                onCreateGraph(wf.id)
            }
        )
    }
}

/**
 * Background keep-alive settings card shown at the top of the workflow list.
 * Guides the user to battery-optimisation and auto-start whitelist pages.
 */
@Composable
private fun KeepAliveCard(
    onRequestIgnoreBattery: () -> Unit,
    onOpenAutoStart: (PowerWhitelistHelper.AutoStartResult) -> Unit
) {
    val context = LocalContext.current
    var isIgnoringBattery by remember {
        mutableStateOf(PowerWhitelistHelper.isIgnoringBatteryOptimizations(context))
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IslandIcon(IslandIcons.WorkflowKeepalive, size = 38.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.oi_keepalive_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.oi_keepalive_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Battery optimisation row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isIgnoringBattery) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.oi_keepalive_allowed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(
                        onClick = {
                            onRequestIgnoreBattery()
                            // Refresh status when the user returns.
                            isIgnoringBattery = PowerWhitelistHelper.isIgnoringBatteryOptimizations(context)
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.oi_keepalive_allow_background))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-start settings row
            OutlinedButton(
                onClick = {
                    val result = PowerWhitelistHelper.openAutoStartSettings(context)
                    onOpenAutoStart(result)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.oi_keepalive_open_auto_start))
            }
        }
    }
}

/** A preset template for graph workflows: pre-built nodes + edges so users don't start blank. */
private data class GraphWorkflowTemplate(
    val id: String,
    val name: String,
    val description: String,
    val icon: IslandIcons,
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>
)

/** Returns the built-in template set. */
private fun graphWorkflowTemplates(): List<GraphWorkflowTemplate> {
    val nStart = StartNode(id = "node_start", label = "开始", trigger = TriggerSpec.Manual)
    val nSearch = ActionNode(
        id = "node_search", label = "搜索", toolName = "web_search",
        args = mapOf("query" to NodeValue.Literal("新闻"))
    )
    val nNotify = ActionNode(
        id = "node_notify", label = "通知", toolName = "send_notification",
        args = mapOf("title" to NodeValue.Literal("完成"))
    )
    val nBranch = BranchNode(
        id = "node_branch", label = "判断",
        lhs = NodeValue.Literal("是"), cmp = Comparison.EQ, rhs = NodeValue.Literal("是")
    )
    val nYes = ActionNode(id = "node_yes", label = "执行", toolName = "web_search", args = emptyMap())
    val nNo = ActionNode(id = "node_no", label = "跳过", toolName = "send_notification", args = emptyMap())
    val nTx = TransformNode(
        id = "node_tx", label = "提取",
        op = TransformOp.Regex(pattern = "\\d+", group = 0, fallback = "")
    )
    val nUse = ActionNode(
        id = "node_use", label = "使用结果", toolName = "web_search",
        args = mapOf("query" to NodeValue.Ref("node_tx"))
    )

    return listOf(
        GraphWorkflowTemplate(
            id = "sequential",
            name = "顺序执行",
            description = "依次执行多个动作：先搜索，再通知。",
            icon = IslandIcons.WorkflowNodeStart,
            nodes = listOf(nStart, nSearch, nNotify),
            edges = listOf(
                FlowEdge(id = "e1", from = nStart.id, to = nSearch.id),
                FlowEdge(id = "e2", from = nSearch.id, to = nNotify.id)
            )
        ),
        GraphWorkflowTemplate(
            id = "branch",
            name = "条件分支",
            description = "判断条件为真/假，分别执行不同的动作。",
            icon = IslandIcons.WorkflowNodeBranch,
            nodes = listOf(nStart, nBranch, nYes, nNo),
            edges = listOf(
                FlowEdge(id = "e1", from = nStart.id, to = nBranch.id),
                FlowEdge(id = "e2", from = nBranch.id, to = nYes.id, guard = EdgeGuard.Bool(true)),
                FlowEdge(id = "e3", from = nBranch.id, to = nNo.id, guard = EdgeGuard.Bool(false))
            )
        ),
        GraphWorkflowTemplate(
            id = "extract",
            name = "提取数据",
            description = "用正则表达式提取数据，再传给下游动作使用。",
            icon = IslandIcons.WorkflowNodeTransform,
            nodes = listOf(nStart, nTx, nUse),
            edges = listOf(
                FlowEdge(id = "e1", from = nStart.id, to = nTx.id),
                FlowEdge(id = "e2", from = nTx.id, to = nUse.id)
            )
        ),
        GraphWorkflowTemplate(
            id = "blank",
            name = "空白",
            description = "从零开始，自己搭建整个工作流。",
            icon = IslandIcons.Workflow,
            nodes = emptyList(),
            edges = emptyList()
        )
    )
}

/**
 * Template-picker dialog for creating a new graph-mode workflow.
 *
 * Uses a standalone [Dialog] (not AlertDialog) so text fields own their own window — under the
 * collapsing-settings scaffold + GuardedAnimatedContent, an AlertDialog's text slot does not
 * recompose on each keystroke, so typed characters don't render until the dialog is reopened.
 */
@Composable
private fun GraphWorkflowTemplateDialog(
    onCancel: () -> Unit,
    onCreate: (name: String, templateId: String) -> Unit
) {
    val templates = remember { graphWorkflowTemplates() }
    var selectedId by remember { mutableStateOf(templates.first().id) }
    var name by remember { mutableStateOf(templates.first().name) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.workflow_new_graph),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "选择一个模板快速开始，或者从零开始搭建。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Template grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { t ->
                        val selected = selectedId == t.id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedId = t.id
                                    if (name.isBlank()) name = t.name
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                IslandIcon(t.icon, size = 38.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        t.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        t.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.workflow_new_name)) },
                    singleLine = true,
                    isError = name.isBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.provider_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(name.trim(), selectedId) },
                        enabled = name.isNotBlank()
                    ) { Text(stringResource(R.string.provider_create)) }
                }
            }
        }
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
                IslandIcon(IslandIcons.Workflow, size = 38.dp)
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
    onLogs: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                IslandIcon(IslandIcons.Workflow, size = 38.dp)
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
                                leadingIcon = { IslandIcon(IslandIcons.WorkflowLog, size = 24.dp) },
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
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { showMenu = false; showDeleteConfirm = true }
                            )
                        }
                    }
                }
            },
            leadingSpacing = 16.dp
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.workflow_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.workflow_delete_text, workflow.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
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
