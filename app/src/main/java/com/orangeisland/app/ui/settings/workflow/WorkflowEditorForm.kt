package com.orangeisland.app.ui.settings.workflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.*
import com.orangeisland.app.model.LLMNode
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
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
    onBack: () -> Unit,
    onOpenCanvas: (() -> Unit)? = null
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
            if (onOpenCanvas != null) {
                IconButton(onClick = onOpenCanvas) {
                    IslandIcon(IslandIcons.WorkflowCanvas, size = 28.dp, contentDescription = stringResource(R.string.workflow_canvas))
                }
            }
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
            },
            onAddLLM = {
                nodes.add(
                    LLMNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "思考",
                        prompt = NodeValue.Literal(""),
                        provider = "OpenAI",
                        modelId = "gpt-4o-mini",
                        systemPrompt = "",
                        temperature = 0.7f
                    )
                )
            },
            onAddNotify = {
                nodes.add(
                    NotifyNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "通知",
                        title = NodeValue.Literal("Orange Island"),
                        content = NodeValue.Literal(""),
                        priority = "default"
                    )
                )
            },
            onAddChatMessage = {
                nodes.add(
                    ChatMessageNode(
                        id = "node_${UUID.randomUUID()}",
                        label = "发消息",
                        text = NodeValue.Literal(""),
                        participant = "MODEL"
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
                Text("添加连线", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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
                                is StartNode -> "触发器: ${triggerLabel(node.trigger)}"
                                is ActionNode -> "工具: ${node.toolName}"
                                is BranchNode -> "分支"
                                is MergeNode -> "合并 (${node.reducer.name})"
                                is TransformNode -> "转换"
                                is LLMNode -> "思考 · ${node.provider}:${node.modelId}"
                                is NotifyNode -> "通知 · ${node.priority}"
                                is ChatMessageNode -> "发消息 · ${node.participant}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        IslandIcon(
                            when (node) {
                                is StartNode -> IslandIcons.WorkflowNodeStart
                                is ActionNode -> IslandIcons.WorkflowNodeAction
                                is BranchNode -> IslandIcons.WorkflowNodeBranch
                                is MergeNode -> IslandIcons.WorkflowNodeMerge
                                is TransformNode -> IslandIcons.WorkflowNodeTransform
                                is LLMNode -> IslandIcons.Model
                                is NotifyNode -> IslandIcons.WorkflowNodeNotification
                                is ChatMessageNode -> IslandIcons.WorkflowNodeChatMessage
                            },
                            size = 38.dp
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
        is ActionNode -> ActionNodeEditor(node, allNodes, onUpdate)
        is BranchNode -> BranchNodeEditor(node, onUpdate)
        is MergeNode -> MergeNodeEditor(node, onUpdate)
        is TransformNode -> TransformNodeEditor(node, allNodes, onUpdate)
        is LLMNode -> LLMNodeEditor(node, allNodes, onUpdate)
        is NotifyNode -> NotifyNodeEditor(node, allNodes, onUpdate)
        is ChatMessageNode -> ChatMessageNodeEditor(node, allNodes, onUpdate)
    }
}

@Composable
private fun StartNodeEditor(node: StartNode, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var triggerKind by remember(node.id) { mutableStateOf(triggerKindOf(node.trigger)) }
    // Schedule interval in minutes. Persisted as intervalMs in the trigger config. Defaults to
    // 15 (WorkManager's minimum periodic interval) when the user first picks "定时".
    var intervalMinutes by remember(node.id) {
        mutableStateOf(scheduleIntervalMinutes(node.trigger))
    }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("名称") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    val kinds = listOf("手动", "打开应用", "接口", "定时", "意图", "语音")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        kinds.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = triggerKind == kind,
                onClick = {
                    triggerKind = kind
                    // Preserve the existing interval when switching to (or staying on) "定时",
                    // so toggling the segment doesn't wipe a value the user just typed.
                    val newTrigger = triggerFromKind(kind, node.trigger, intervalMinutes)
                    if (kind == "定时") {
                        // triggerFromKind clamps to the 15-minute minimum; reflect that back.
                        intervalMinutes = scheduleIntervalMinutes(newTrigger)
                    }
                    onUpdate(node.copy(trigger = newTrigger))
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size)
            ) {
                Text(kind, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    // Only show the interval field for the Schedule trigger.
    if (triggerKind == "定时") {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = intervalMinutes.toString(),
            onValueChange = { input ->
                val minutes = input.toIntOrNull()
                if (minutes != null && minutes > 0) {
                    intervalMinutes = minutes
                    onUpdate(node.copy(trigger = scheduleTrigger(minutes)))
                }
            },
            label = { Text("间隔（分钟，最少 15）") },
            supportingText = { Text("WorkManager 最低 15 分钟，更小的值会被自动上调") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun ActionNodeEditor(node: ActionNode, allNodes: List<FlowNode>, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var toolName by remember(node.id) { mutableStateOf(node.toolName) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("名称") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = toolName,
        onValueChange = { toolName = it; onUpdate(node.copy(toolName = toolName)) },
        label = { Text("工具名") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        "参数",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))

    node.args.forEach { (key, value) ->
        ArgRow(
            argKey = key,
            argValue = value,
            allNodes = allNodes,
            onUpdate = { newKey, newValue ->
                val newArgs = node.args.toMutableMap()
                if (newKey != key) newArgs.remove(key)
                newArgs[newKey] = newValue
                onUpdate(node.copy(args = newArgs))
            },
            onDelete = { onUpdate(node.copy(args = node.args - key)) }
        )
        Spacer(modifier = Modifier.height(4.dp))
    }

    TextButton(
        onClick = {
            var idx = 1
            while ("arg$idx" in node.args.keys) idx++
            onUpdate(node.copy(args = node.args + ("arg$idx" to NodeValue.Literal(""))))
        }
    ) {
        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("添加参数")
    }
}

@Composable
private fun ArgRow(
    argKey: String,
    argValue: NodeValue,
    allNodes: List<FlowNode>,
    onUpdate: (String, NodeValue) -> Unit,
    onDelete: () -> Unit
) {
    var key by remember(argKey) { mutableStateOf(argKey) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; onUpdate(key, argValue) },
            label = { Text("键") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(0.35f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(0.55f)) {
            NodeValueEditor(value = argValue, allNodes = allNodes) {
                onUpdate(key, it)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NodeValueEditor(
    value: NodeValue,
    allNodes: List<FlowNode>,
    onUpdate: (NodeValue) -> Unit
) {
    val isRef = value is NodeValue.Ref
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isRef,
                onClick = { if (isRef) onUpdate(NodeValue.Literal(if (value is NodeValue.Ref) "" else (value as NodeValue.Literal).value)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("文本", style = MaterialTheme.typography.labelSmall) }
            SegmentedButton(
                selected = isRef,
                onClick = {
                    if (!isRef) {
                        val firstId = allNodes.firstOrNull()?.id ?: ""
                        onUpdate(NodeValue.Ref(firstId))
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("引用", style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        when (value) {
            is NodeValue.Literal -> OutlinedTextField(
                value = value.value,
                onValueChange = { onUpdate(NodeValue.Literal(it)) },
                label = { Text("值") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            is NodeValue.Ref -> NodeDropdown(
                label = "节点",
                selected = value.nodeId,
                options = allNodes.map { it.id },
                nodes = allNodes,
                onSelect = { onUpdate(NodeValue.Ref(it)) }
            )
        }
    }
}

@Composable
private fun BranchNodeEditor(node: BranchNode, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    val comparisons = Comparison.entries.map { it.name }
    var selectedCmp by remember(node.id) { mutableIntStateOf(Comparison.entries.indexOf(node.cmp)) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("名称") },
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
        label = { Text("名称") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransformNodeEditor(node: TransformNode, allNodes: List<FlowNode>, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var expanded by remember { mutableStateOf(false) }
    val opKind = opKindOf(node.op)
    val opKinds = listOf("固定值", "正则提取", "JSON路径", "切片", "拼接", "随机整数", "随机文本")

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("名称") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Op kind dropdown
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = opKind,
            onValueChange = {},
            readOnly = true,
            label = { Text("操作") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            opKinds.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind) },
                    onClick = {
                        expanded = false
                        onUpdate(node.copy(op = defaultOpForKind(kind)))
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (val op = node.op) {
        is TransformOp.Fixed -> {
            var value by remember(node.id) { mutableStateOf(op.value) }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; onUpdate(node.copy(op = op.copy(value = value))) },
                label = { Text("值") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        is TransformOp.Regex -> {
            var pattern by remember(node.id) { mutableStateOf(op.pattern) }
            var group by remember(node.id) { mutableIntStateOf(op.group) }
            var fallback by remember(node.id) { mutableStateOf(op.fallback) }
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it; onUpdate(node.copy(op = op.copy(pattern = pattern))) },
                label = { Text("正则表达式") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                OutlinedTextField(
                    value = group.toString(),
                    onValueChange = {
                        group = it.toIntOrNull() ?: 0
                        onUpdate(node.copy(op = op.copy(group = group)))
                    },
                    label = { Text("捕获组") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = fallback,
                    onValueChange = { fallback = it; onUpdate(node.copy(op = op.copy(fallback = fallback))) },
                    label = { Text("默认值") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
        is TransformOp.JsonPath -> {
            var path by remember(node.id) { mutableStateOf(op.path) }
            var fallback by remember(node.id) { mutableStateOf(op.fallback) }
            OutlinedTextField(
                value = path,
                onValueChange = { path = it; onUpdate(node.copy(op = op.copy(path = path))) },
                label = { Text("路径") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = fallback,
                onValueChange = { fallback = it; onUpdate(node.copy(op = op.copy(fallback = fallback))) },
                label = { Text("Fallback") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        is TransformOp.Slice -> {
            var start by remember(node.id) { mutableIntStateOf(op.start) }
            var length by remember(node.id) { mutableIntStateOf(op.length) }
            var fallback by remember(node.id) { mutableStateOf(op.fallback) }
            Row {
                OutlinedTextField(
                    value = start.toString(),
                    onValueChange = {
                        start = it.toIntOrNull() ?: 0
                        onUpdate(node.copy(op = op.copy(start = start)))
                    },
                    label = { Text("起始位置") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = length.toString(),
                    onValueChange = {
                        length = it.toIntOrNull() ?: -1
                        onUpdate(node.copy(op = op.copy(length = length)))
                    },
                    label = { Text("长度") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = fallback,
                    onValueChange = { fallback = it; onUpdate(node.copy(op = op.copy(fallback = fallback))) },
                    label = { Text("默认值") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
        is TransformOp.Join -> {
            Text(
                "输入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NodeValueEditor(value = op.input, allNodes = allNodes) {
                onUpdate(node.copy(op = op.copy(input = it)))
            }
        }
        is TransformOp.RandomInt -> {
            var min by remember(node.id) { mutableIntStateOf(op.min) }
            var max by remember(node.id) { mutableIntStateOf(op.max) }
            Row {
                OutlinedTextField(
                    value = min.toString(),
                    onValueChange = {
                        min = it.toIntOrNull() ?: 0
                        onUpdate(node.copy(op = op.copy(min = min)))
                    },
                    label = { Text("最小值") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = max.toString(),
                    onValueChange = {
                        max = it.toIntOrNull() ?: 100
                        onUpdate(node.copy(op = op.copy(max = max)))
                    },
                    label = { Text("最大值") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
        is TransformOp.RandomText -> {
            var length by remember(node.id) { mutableIntStateOf(op.length) }
            var charset by remember(node.id) { mutableStateOf(op.charset) }
            Row {
                OutlinedTextField(
                    value = length.toString(),
                    onValueChange = {
                        length = it.toIntOrNull() ?: 8
                        onUpdate(node.copy(op = op.copy(length = length)))
                    },
                    label = { Text("长度") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.4f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = charset,
                    onValueChange = { charset = it; onUpdate(node.copy(op = op.copy(charset = charset))) },
                    label = { Text("字符集") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.6f),
                    singleLine = true
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LLMNodeEditor(node: LLMNode, allNodes: List<FlowNode>, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var provider by remember(node.id) { mutableStateOf(node.provider) }
    var modelId by remember(node.id) { mutableStateOf(node.modelId) }
    var systemPrompt by remember(node.id) { mutableStateOf(node.systemPrompt) }
    var temperature by remember(node.id) { mutableFloatStateOf(node.temperature) }
    var expanded by remember { mutableStateOf(false) }

    val providers = listOf("Google", "OpenAI", "Anthropic", "DeepSeek", "Qwen", "Ollama", "Open Router")

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("名称") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text("提示词", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    NodeValueEditor(value = node.prompt, allNodes = allNodes) {
        onUpdate(node.copy(prompt = it))
    }
    Spacer(modifier = Modifier.height(8.dp))

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = provider,
            onValueChange = {},
            readOnly = true,
            label = { Text("提供商") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = { expanded = false; provider = p; onUpdate(node.copy(provider = provider)) })
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = modelId,
        onValueChange = { modelId = it; onUpdate(node.copy(modelId = modelId)) },
        label = { Text("模型 ID") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = systemPrompt,
        onValueChange = { systemPrompt = it; onUpdate(node.copy(systemPrompt = systemPrompt)) },
        label = { Text("系统提示词（可选）") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text("温度: ${"%.1f".format(temperature)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = temperature,
        onValueChange = {
            temperature = (kotlin.math.round(it * 10) / 10f).coerceIn(0f, 2f)
            onUpdate(node.copy(temperature = temperature))
        },
        valueRange = 0f..2f,
        steps = 19,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifyNodeEditor(node: NotifyNode, allNodes: List<FlowNode>, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    var priority by remember(node.id) { mutableStateOf(node.priority) }
    var expanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("名称") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text("标题", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    NodeValueEditor(value = node.title, allNodes = allNodes) {
        onUpdate(node.copy(title = it))
    }
    Spacer(modifier = Modifier.height(8.dp))

    Text("内容", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    NodeValueEditor(value = node.content, allNodes = allNodes) {
        onUpdate(node.copy(content = it))
    }
    Spacer(modifier = Modifier.height(8.dp))

    val priorities = listOf("low", "default", "high")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = priority,
            onValueChange = {},
            readOnly = true,
            label = { Text("优先级") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            priorities.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p) },
                    onClick = { expanded = false; priority = p; onUpdate(node.copy(priority = priority)) }
                )
            }
        }
    }
}

@Composable
private fun ChatMessageNodeEditor(node: ChatMessageNode, allNodes: List<FlowNode>, onUpdate: (FlowNode) -> Unit) {
    var label by remember(node.id) { mutableStateOf(node.label) }
    val participants = listOf("MODEL", "USER")
    var selected by remember(node.id) { mutableIntStateOf(participants.indexOf(node.participant).coerceAtLeast(0)) }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it; onUpdate(node.copy(label = label)) },
        label = { Text("名称") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text("消息内容", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    NodeValueEditor(value = node.text, allNodes = allNodes) {
        onUpdate(node.copy(text = it))
    }
    Spacer(modifier = Modifier.height(8.dp))

    Text("发送者", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        participants.forEachIndexed { index, p ->
            SegmentedButton(
                selected = selected == index,
                onClick = {
                    selected = index
                    onUpdate(node.copy(participant = p))
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = participants.size)
            ) {
                Text(p, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
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
                        edge.guard?.let { "守卫: ${guardLabel(it)}" } ?: "无守卫",
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
    NodeDropdown(label = "来源", selected = fromId, options = nodeIds, nodes = allNodes) {
        fromId = it
        onUpdate(edge.copy(from = fromId))
    }
    Spacer(modifier = Modifier.height(8.dp))

    // To
    NodeDropdown(label = "目标", selected = toId, options = nodeIds, nodes = allNodes) {
        toId = it
        onUpdate(edge.copy(to = toId))
    }
    Spacer(modifier = Modifier.height(8.dp))

    // Guard
    val guardTypes = listOf("无", "成功", "失败", "为真", "为假", "正则匹配")
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

    // Regex pattern input when Regex guard is selected
    if (guardType == "正则匹配") {
        Spacer(modifier = Modifier.height(8.dp))
        var pattern by remember(edge.id) {
            mutableStateOf((edge.guard as? EdgeGuard.Regex)?.pattern ?: "")
        }
        OutlinedTextField(
            value = pattern,
            onValueChange = {
                pattern = it
                onUpdate(edge.copy(guard = EdgeGuard.Regex(pattern)))
            },
            label = { Text("Pattern") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
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
    onAddTransform: () -> Unit,
    onAddLLM: () -> Unit,
    onAddNotify: () -> Unit,
    onAddChatMessage: () -> Unit
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
            DropdownMenuItem(text = { Text("开始") }, leadingIcon = { Image(painterResource(IslandIcons.WorkflowNodeStart.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddStart() })
            DropdownMenuItem(text = { Text("动作") }, leadingIcon = { Image(painterResource(IslandIcons.WorkflowNodeAction.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddAction() })
            DropdownMenuItem(text = { Text("分支") }, leadingIcon = { Image(painterResource(IslandIcons.WorkflowNodeBranch.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddBranch() })
            DropdownMenuItem(text = { Text("合并") }, leadingIcon = { Image(painterResource(IslandIcons.WorkflowNodeMerge.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddMerge() })
            DropdownMenuItem(text = { Text("转换") }, leadingIcon = { Image(painterResource(IslandIcons.WorkflowNodeTransform.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddTransform() })
            DropdownMenuItem(text = { Text("思考") }, leadingIcon = { Image(painterResource(IslandIcons.Model.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddLLM() })
            DropdownMenuItem(text = { Text("通知") }, leadingIcon = { Image(painterResource(IslandIcons.WorkflowNodeNotification.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddNotify() })
            DropdownMenuItem(text = { Text("发消息") }, leadingIcon = { Image(painterResource(IslandIcons.WorkflowNodeChatMessage.res), null, modifier = Modifier.size(20.dp)) }, onClick = { expanded = false; onAddChatMessage() })
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
    is TriggerSpec.Manual -> "手动"
    is TriggerSpec.AppOpen -> "打开应用"
    is TriggerSpec.Api -> "接口"
    is TriggerSpec.Schedule -> "定时"
    is TriggerSpec.IntentAction -> "意图"
    is TriggerSpec.Voice -> "语音"
}

/**
 * Build a [TriggerSpec] from the segmented-button [kind]. For "定时" (Schedule), [current] is the
 * node's existing trigger and [fallbackMinutes] is the in-progress editor value; the returned
 * Schedule reuses [current]'s intervalMs if it was already a Schedule, else falls back to
 * [fallbackMinutes]. This stops the segment toggle from wiping a half-typed interval.
 */
private fun triggerFromKind(kind: String, current: TriggerSpec? = null, fallbackMinutes: Int = 15): TriggerSpec = when (kind) {
    "打开应用" -> TriggerSpec.AppOpen
    "接口" -> TriggerSpec.Api
    "定时" -> {
        val minutes = (current as? TriggerSpec.Schedule)?.let(::scheduleIntervalMinutes) ?: fallbackMinutes
        scheduleTrigger(minutes)
    }
    "意图" -> TriggerSpec.IntentAction("")
    "语音" -> TriggerSpec.Voice()
    else -> TriggerSpec.Manual
}

/** Build a Schedule trigger for [minutes], clamped to WorkManager's 15-minute minimum. The
 *  interval is stored as intervalMs in the config map (the format ScheduleCalculator expects). */
private fun scheduleTrigger(minutes: Int): TriggerSpec.Schedule {
    val clamped = minutes.coerceAtLeast(15)
    return TriggerSpec.Schedule(ScheduleMode.Interval, mapOf("intervalMs" to (clamped * 60_000L).toString()))
}

/** Read the interval (in whole minutes) out of a Schedule trigger's config, defaulting to 15 when
 *  absent or unparseable. */
private fun scheduleIntervalMinutes(trigger: TriggerSpec): Int {
    val schedule = trigger as? TriggerSpec.Schedule ?: return 15
    val ms = schedule.config["intervalMs"]?.toLongOrNull() ?: return 15
    return (ms / 60_000L).toInt().coerceAtLeast(1)
}

private fun guardLabel(guard: EdgeGuard): String = when (guard) {
    is EdgeGuard.OnSuccess -> "成功"
    is EdgeGuard.OnFailure -> "失败"
    is EdgeGuard.Bool -> "布尔(${guard.expected})"
    is EdgeGuard.Regex -> "正则匹配"
}

private fun guardTypeOf(guard: EdgeGuard?): String = when (guard) {
    is EdgeGuard.OnSuccess -> "成功"
    is EdgeGuard.OnFailure -> "失败"
    is EdgeGuard.Bool -> if (guard.expected) "为真" else "为假"
    is EdgeGuard.Regex -> "正则匹配"
    null -> "无"
}

private fun guardFromType(type: String): EdgeGuard? = when (type) {
    "成功" -> EdgeGuard.OnSuccess
    "失败" -> EdgeGuard.OnFailure
    "为真" -> EdgeGuard.Bool(true)
    "为假" -> EdgeGuard.Bool(false)
    "正则匹配" -> EdgeGuard.Regex("")
    else -> null
}

private fun opKindOf(op: TransformOp): String = when (op) {
    is TransformOp.Fixed -> "固定值"
    is TransformOp.Regex -> "正则提取"
    is TransformOp.JsonPath -> "JSON路径"
    is TransformOp.Slice -> "切片"
    is TransformOp.Join -> "拼接"
    is TransformOp.RandomInt -> "随机整数"
    is TransformOp.RandomText -> "随机文本"
}

private fun defaultOpForKind(kind: String): TransformOp = when (kind) {
    "正则提取" -> TransformOp.Regex(pattern = "", group = 0, fallback = "")
    "JSON路径" -> TransformOp.JsonPath(path = "", fallback = "")
    "切片" -> TransformOp.Slice(start = 0, length = -1, fallback = "")
    "拼接" -> TransformOp.Join(input = NodeValue.Literal(""), extras = emptyList())
    "随机整数" -> TransformOp.RandomInt(min = 0, max = 100, fixed = null)
    "随机文本" -> TransformOp.RandomText(length = 8, charset = TransformOp.RandomText.ALNUM, fixed = null)
    else -> TransformOp.Fixed("")
}
