package com.orangeisland.app.ui.settings.workflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.*
import com.orangeisland.app.ui.settings.CollapsingSettingsScaffold
import com.orangeisland.app.ui.settings.SettingsItem
import java.util.UUID

/**
 * Form-based workflow editor. Precedes the visual canvas (D5) but is fully functional:
 * edit name/description, add/remove nodes and edges, configure triggers, tools, branches,
 * merges, and transforms.
 *
 * State is held locally in remember fields; the caller receives the final [Workflow] on save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorForm(
    workflow: Workflow,
    onSave: (Workflow) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(workflow.id) { mutableStateOf(workflow.name) }
    var description by remember(workflow.id) { mutableStateOf(workflow.description) }
    var enabled by remember(workflow.id) { mutableStateOf(workflow.enabled) }

    val nodes = remember(workflow.id) {
        mutableStateListOf<FlowNode>().apply { addAll(workflow.nodes) }
    }
    val edges = remember(workflow.id) {
        mutableStateListOf<FlowEdge>().apply { addAll(workflow.edges) }
    }

    var nameError by remember { mutableStateOf(false) }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.workflow_editor_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                if (name.isBlank()) {
                    nameError = true
                    return@IconButton
                }
                onSave(
                    workflow.copy(
                        name = name.trim(),
                        description = description.trim(),
                        enabled = enabled,
                        nodes = nodes.toList(),
                        edges = edges.toList(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }) {
                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.workflow_save))
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text(stringResource(R.string.workflow_name_hint)) },
            isError = nameError,
            supportingText = if (nameError) { { Text(stringResource(R.string.template_title_required)) } } else null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.workflow_description_hint)) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsItem(
                headlineContent = { Text(stringResource(R.string.workflow_status_running)) },
                supportingContent = { Text(if (enabled) "Enabled" else "Disabled") },
                trailingContent = {
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, modifier = Modifier.scale(0.85f))
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Nodes ───────────────────────────────────────────────

        Text(
            text = stringResource(R.string.workflow_nodes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        nodes.forEachIndexed { index, node ->
            NodeEditorCard(
                node = node,
                allNodes = nodes,
                onUpdate = { updated -> nodes[index] = updated },
                onDelete = { nodes.removeAt(index) }
            )
            if (index < nodes.lastIndex) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AddNodeRow(
            onAddStart = {
                nodes.add(
                    StartNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "Start",
                        trigger = TriggerSpec.Manual
                    )
                )
            },
            onAddAction = {
                nodes.add(
                    ActionNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "Action",
                        toolName = "web_search",
                        args = emptyMap()
                    )
                )
            },
            onAddBranch = {
                nodes.add(
                    BranchNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "Branch",
                        lhs = NodeValue.Literal(""),
                        cmp = Comparison.EQ,
                        rhs = NodeValue.Literal("")
                    )
                )
            },
            onAddMerge = {
                nodes.add(
                    MergeNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "Merge",
                        reducer = Reducer.ALL_TRUE
                    )
                )
            },
            onAddTransform = {
                nodes.add(
                    TransformNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "Transform",
                        op = TransformOp.Fixed("")
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Edges ───────────────────────────────────────────────

        Text(
            text = stringResource(R.string.workflow_edges),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        edges.forEachIndexed { index, edge ->
            EdgeEditorCard(
                edge = edge,
                allNodes = nodes,
                onUpdate = { updated -> edges[index] = updated },
                onDelete = { edges.removeAt(index) }
            )
            if (index < edges.lastIndex) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable {
                    val from = nodes.firstOrNull()?.id ?: ""
                    val to = nodes.getOrNull(1)?.id ?: ""
                    edges.add(
                        FlowEdge(
                            id = "edge_${UUID.randomUUID()}",
                            from = from,
                            to = to
                        )
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Edge", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Node Editor Card ─────────────────────────────────────────

@Composable
private fun NodeEditorCard(
    node: FlowNode,
    allNodes: List<FlowNode>,
    onUpdate: (FlowNode) -> Unit,
    onDelete: () -> Unit
) {
    val expanded = remember { MutableTransitionState(false) }
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            SettingsItem(
                headlineContent = {
                    Text(
                        buildString {
                            append(node.label.ifBlank { node.kind })
                            append(" · ")
                            append(node.id.take(8))
                        },
                        fontWeight = FontWeight.Medium
                    )
                },
                supportingContent = {
                    Text(
                        when (node) {
                            is StartNode -> "Trigger: ${triggerLabel(node.trigger)}"
                            is ActionNode -> "Tool: ${node.toolName}"
                            is BranchNode -> "Branch"
                            is MergeNode -> "Merge (${node.reducer.name})"
                            is TransformNode -> "Transform"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        when (node) {
                            is StartNode -> Icons.Default.PlayArrow
                            is ActionNode -> Icons.Default.AutoAwesome
                            is BranchNode -> Icons.Default.CallSplit
                            is MergeNode -> Icons.Default.MergeType
                            is TransformNode -> Icons.Default.Transform
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { expanded.targetState = !expanded.targetState }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (expanded.targetState) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; onDelete() }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.clickable { expanded.targetState = !expanded.targetState }
            )

            AnimatedVisibility(
                visibleState = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    NodeDetailEditor(node = node, allNodes = allNodes, onUpdate = onUpdate)
                }
            }
        }
    }
}

@Composable
private fun NodeDetailEditor(
    node: FlowNode,
    allNodes: List<FlowNode>,
    onUpdate: (FlowNode) -> Unit
) {
    when (node) {
        is StartNode -> StartNodeEditor(node, onUpdate)
        is ActionNode -> ActionNodeEditor(node, onUpdate)
        is BranchNode -> BranchNodeEditor(node, onUpdate)
        is MergeNode -> MergeNodeEditor(node, onUpdate)
        is TransformNode -> TransformNodeEditor(node, onUpdate)
    }
}

@Composable
private fun StartNodeEditor(node: StartNode, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var triggerKind by remember(node.id) { mutableStateOf(triggerKindOf(node.trigger)) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("Label") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    val kinds = listOf("Manual", "AppOpen", "Api", "Schedule", "Intent", "Voice")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        kinds.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = triggerKind == kind,
                onClick = {
                    triggerKind = kind
                    onUpdate(node.copy(trigger = triggerFromKind(kind)))
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size)
            ) {
                Text(kind, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActionNodeEditor(node: ActionNode, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var toolName by remember(node.id) { mutableStateOf(node.toolName) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("Label") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = toolName,
        onValueChange = { toolName = it; onUpdate(node.copy(toolName = toolName)) },
        label = { Text("Tool Name") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun BranchNodeEditor(node: BranchNode, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    val comparisons = Comparison.entries.map { it.name }
    var selectedCmp by remember(node.id) { mutableIntStateOf(Comparison.entries.indexOf(node.cmp)) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("Label") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        comparisons.forEachIndexed { index, name ->
            SegmentedButton(
                selected = selectedCmp == index,
                onClick = {
                    selectedCmp = index
                    onUpdate(node.copy(cmp = Comparison.entries[index]))
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = comparisons.size)
            ) {
                Text(name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MergeNodeEditor(node: MergeNode, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    val reducers = Reducer.entries.map { it.name }
    var selectedRed by remember(node.id) { mutableIntStateOf(Reducer.entries.indexOf(node.reducer)) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("Label") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        reducers.forEachIndexed { index, name ->
            SegmentedButton(
                selected = selectedRed == index,
                onClick = {
                    selectedRed = index
                    onUpdate(node.copy(reducer = Reducer.entries[index]))
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = reducers.size)
            ) {
                Text(name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TransformNodeEditor(node: TransformNode, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("Label") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    // Simplified: only Fixed op exposed in form editor. Full op set belongs on canvas.
    Spacer(modifier = Modifier.height(8.dp))
    val fixedValue = (node.op as? TransformOp.Fixed)?.value ?: ""
    var value by remember(node.id) { mutableStateOf(fixedValue) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onUpdate(node.copy(op = TransformOp.Fixed(value)))
        },
        label = { Text("Fixed Value") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

// ── Edge Editor Card ─────────────────────────────────────────

@Composable
private fun EdgeEditorCard(
    edge: FlowEdge,
    allNodes: List<FlowNode>,
    onUpdate: (FlowEdge) -> Unit,
    onDelete: () -> Unit
) {
    val expanded = remember { MutableTransitionState(false) }
    var showMenu by remember { mutableStateOf(false) }

    val fromNode = allNodes.find { it.id == edge.from }
    val toNode = allNodes.find { it.id == edge.to }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            SettingsItem(
                headlineContent = {
                    Text(
                        "${fromNode?.label?.take(12) ?: edge.from.take(8)} → ${toNode?.label?.take(12) ?: edge.to.take(8)}",
                        fontWeight = FontWeight.Medium
                    )
                },
                supportingContent = {
                    Text(
                        edge.guard?.let { "Guard: ${guardLabel(it)}" } ?: "No guard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { expanded.targetState = !expanded.targetState }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (expanded.targetState) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; onDelete() }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.clickable { expanded.targetState = !expanded.targetState }
            )

            AnimatedVisibility(
                visibleState = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    EdgeDetailEditor(edge = edge, allNodes = allNodes, onUpdate = onUpdate)
                }
            }
        }
    }
}

@Composable
private fun EdgeDetailEditor(
    edge: FlowEdge,
    allNodes: List<FlowNode>,
    onUpdate: (FlowEdge) -> Unit
) {
    var fromId by remember(edge.id) { mutableStateOf(edge.from) }
    var toId by remember(edge.id) { mutableStateOf(edge.to) }
    var guardType by remember(edge.id) { mutableStateOf(guardTypeOf(edge.guard)) }

    val nodeIds = allNodes.map { it.id }

    // From
    NodeDropdown(label = "From", selected = fromId, options = nodeIds, nodes = allNodes) {
        fromId = it
        onUpdate(edge.copy(from = fromId))
    }
    Spacer(modifier = Modifier.height(8.dp))

    // To
    NodeDropdown(label = "To", selected = toId, options = nodeIds, nodes = allNodes) {
        toId = it
        onUpdate(edge.copy(to = toId))
    }
    Spacer(modifier = Modifier.height(8.dp))

    // Guard
    val guardTypes = listOf("None", "OnSuccess", "OnFailure", "BoolTrue", "BoolFalse")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        guardTypes.forEachIndexed { index, type ->
            SegmentedButton(
                selected = guardType == type,
                onClick = {
                    guardType = type
                    onUpdate(edge.copy(guard = guardFromType(type)))
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = guardTypes.size)
            ) {
                Text(type, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDropdown(
    label: String,
    selected: String,
    options: List<String>,
    nodes: List<FlowNode>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = nodes.find { it.id == selected }?.label?.takeIf { it.isNotBlank() } ?: selected.take(8),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { id ->
                val node = nodes.find { it.id == id }
                DropdownMenuItem(
                    text = { Text(node?.label?.takeIf { it.isNotBlank() } ?: id.take(8)) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Add Node Row ─────────────────────────────────────────────

@Composable
private fun AddNodeRow(
    onAddStart: () -> Unit,
    onAddAction: () -> Unit,
    onAddBranch: () -> Unit,
    onAddMerge: () -> Unit,
    onAddTransform: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.workflow_add_node), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }

    AnimatedVisibility(visible = expanded) {
        Column {
            DropdownMenuItem(text = { Text("Start") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { expanded = false; onAddStart() })
            DropdownMenuItem(text = { Text("Action") }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }, onClick = { expanded = false; onAddAction() })
            DropdownMenuItem(text = { Text("Branch") }, leadingIcon = { Icon(Icons.Default.CallSplit, null) }, onClick = { expanded = false; onAddBranch() })
            DropdownMenuItem(text = { Text("Merge") }, leadingIcon = { Icon(Icons.Default.MergeType, null) }, onClick = { expanded = false; onAddMerge() })
            DropdownMenuItem(text = { Text("Transform") }, leadingIcon = { Icon(Icons.Default.Transform, null) }, onClick = { expanded = false; onAddTransform() })
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────

private fun triggerLabel(trigger: TriggerSpec): String = when (trigger) {
    is TriggerSpec.Manual -> "Manual"
    is TriggerSpec.AppOpen -> "App Open"
    is TriggerSpec.Api -> "API"
    is TriggerSpec.Schedule -> "Schedule"
    is TriggerSpec.IntentAction -> "Intent"
    is TriggerSpec.Voice -> "Voice"
}

private fun triggerKindOf(trigger: TriggerSpec): String = when (trigger) {
    is TriggerSpec.Manual -> "Manual"
    is TriggerSpec.AppOpen -> "AppOpen"
    is TriggerSpec.Api -> "Api"
    is TriggerSpec.Schedule -> "Schedule"
    is TriggerSpec.IntentAction -> "Intent"
    is TriggerSpec.Voice -> "Voice"
}

private fun triggerFromKind(kind: String): TriggerSpec = when (kind) {
    "AppOpen" -> TriggerSpec.AppOpen
    "Api" -> TriggerSpec.Api
    "Schedule" -> TriggerSpec.Schedule(ScheduleMode.Interval, emptyMap())
    "Intent" -> TriggerSpec.IntentAction("")
    "Voice" -> TriggerSpec.Voice()
    else -> TriggerSpec.Manual
}

private fun guardLabel(guard: EdgeGuard): String = when (guard) {
    is EdgeGuard.OnSuccess -> "OnSuccess"
    is EdgeGuard.OnFailure -> "OnFailure"
    is EdgeGuard.Bool -> "Bool(${guard.expected})"
    is EdgeGuard.Regex -> "Regex"
}

private fun guardTypeOf(guard: EdgeGuard?): String = when (guard) {
    is EdgeGuard.OnSuccess -> "OnSuccess"
    is EdgeGuard.OnFailure -> "OnFailure"
    is EdgeGuard.Bool -> if (guard.expected) "BoolTrue" else "BoolFalse"
    is EdgeGuard.Regex -> "Regex"
    null -> "None"
}

private fun guardFromType(type: String): EdgeGuard? = when (type) {
    "OnSuccess" -> EdgeGuard.OnSuccess
    "OnFailure" -> EdgeGuard.OnFailure
    "BoolTrue" -> EdgeGuard.Bool(true)
    "BoolFalse" -> EdgeGuard.Bool(false)
    else -> null
}
