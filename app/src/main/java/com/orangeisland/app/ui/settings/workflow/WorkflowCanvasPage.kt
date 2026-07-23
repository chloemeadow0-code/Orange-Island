package com.orangeisland.app.ui.settings.workflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeisland.app.R
import com.orangeisland.app.model.*
import com.orangeisland.app.workflow.NodeState
import kotlin.math.roundToInt

/**
 * Visual canvas page: drag-and-drop node cards, Bézier edges, and live run highlighting.
 *
 * This is the D5 deliverable. State is held locally; [onSave] emits the mutated [Workflow]
 * (with updated node positions) when the user taps the top-bar save button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowCanvasPage(
    workflow: Workflow,
    nodeStates: Map<String, NodeState>,
    onSave: (Workflow) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(workflow.id) { mutableStateOf(workflow.name) }
    val nodes = remember(workflow.id) {
        mutableStateListOf<FlowNode>().apply { addAll(workflow.nodes) }
    }
    val edges = remember(workflow.id) {
        mutableStateListOf<FlowEdge>().apply { addAll(workflow.edges) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSave(workflow.copy(name = name, nodes = nodes.toList(), edges = edges.toList(), updatedAt = System.currentTimeMillis()))
                    }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.workflow_save))
                    }
                }
            )
        }
    ) { padding ->
        WorkflowCanvas(
            nodes = nodes,
            edges = edges,
            nodeStates = nodeStates,
            onNodeMove = { id, pos ->
                val idx = nodes.indexOfFirst { it.id == id }
                if (idx != -1) {
                    nodes[idx] = when (val n = nodes[idx]) {
                        is StartNode -> n.copy(pos = pos)
                        is ActionNode -> n.copy(pos = pos)
                        is BranchNode -> n.copy(pos = pos)
                        is MergeNode -> n.copy(pos = pos)
                        is TransformNode -> n.copy(pos = pos)
                        is LLMNode -> n.copy(pos = pos)
                        is NotifyNode -> n.copy(pos = pos)
                        is ChatMessageNode -> n.copy(pos = pos)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/**
 * The canvas composable: draws edges underneath draggable node cards.
 *
 * @param nodes mutable node list (positions are read from [FlowNode.pos])
 * @param edges edge list
 * @param nodeStates live engine state for highlighting
 * @param onNodeMove callback when a drag gesture mutates a node's position
 */
@Composable
private fun WorkflowCanvas(
    nodes: List<FlowNode>,
    edges: List<FlowEdge>,
    nodeStates: Map<String, NodeState>,
    onNodeMove: (String, FlowNode.Vec2) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Card geometry (dp)
    val cardWidthDp = 140.dp
    val cardHeightDp = 72.dp
    val cardWidthPx = with(density) { cardWidthDp.toPx() }
    val cardHeightPx = with(density) { cardHeightDp.toPx() }

    // Convert node pos (assumed in dp) to px offsets for drawing
    fun nodeCenterPx(node: FlowNode): Offset {
        val x = with(density) { node.pos.x.dp.toPx() } + cardWidthPx / 2f
        val y = with(density) { node.pos.y.dp.toPx() } + cardHeightPx / 2f
        return Offset(x, y)
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        // ── Edges (drawn first, behind nodes) ─────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            edges.forEach { edge ->
                val fromNode = nodes.find { it.id == edge.from }
                val toNode = nodes.find { it.id == edge.to }
                if (fromNode == null || toNode == null) return@forEach

                val start = nodeCenterPx(fromNode)
                val end = nodeCenterPx(toNode)

                // Cubic Bézier with horizontal control arms
                val dx = (end.x - start.x).coerceAtLeast(60f)
                val cp1 = Offset(start.x + dx * 0.5f, start.y)
                val cp2 = Offset(end.x - dx * 0.5f, end.y)

                val edgeColor = when (edge.guard) {
                    is EdgeGuard.OnSuccess -> Color(0xFF4CAF50)
                    is EdgeGuard.OnFailure -> Color(0xFFE91E63)
                    is EdgeGuard.Bool -> if (edge.guard.expected) Color(0xFF2196F3) else Color(0xFFFF9800)
                    is EdgeGuard.Regex -> Color(0xFF9C27B0)
                    null -> Color.Gray.copy(alpha = 0.6f)
                }

                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)
                    },
                    color = edgeColor,
                    style = Stroke(width = 2.5f)
                )

                // Arrowhead at end
                val angle = kotlin.math.atan2(end.y - cp2.y, end.x - cp2.x)
                val arrowLen = 12f
                val arrowAngle = 0.5f
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(
                            end.x - arrowLen * kotlin.math.cos(angle - arrowAngle),
                            end.y - arrowLen * kotlin.math.sin(angle - arrowAngle)
                        )
                        lineTo(
                            end.x - arrowLen * kotlin.math.cos(angle + arrowAngle),
                            end.y - arrowLen * kotlin.math.sin(angle + arrowAngle)
                        )
                        close()
                    },
                    color = edgeColor
                )
            }
        }

        // ── Node cards ────────────────────────────────────────
        nodes.forEach { node ->
            val state = nodeStates[node.id]
            val xDp = node.pos.x.dp
            val yDp = node.pos.y.dp

            NodeCard(
                node = node,
                state = state,
                onMove = { deltaX, deltaY ->
                    val newX = node.pos.x + with(density) { deltaX.toDp().value }
                    val newY = node.pos.y + with(density) { deltaY.toDp().value }
                    onNodeMove(node.id, FlowNode.Vec2(newX, newY))
                },
                modifier = Modifier.offset(x = xDp, y = yDp)
            )
        }
    }
}

@Composable
private fun NodeCard(
    node: FlowNode,
    state: NodeState?,
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val (typeLabel, typeColor) = when (node) {
        is StartNode -> "START" to MaterialTheme.colorScheme.primary
        is ActionNode -> "ACTION" to MaterialTheme.colorScheme.secondary
        is BranchNode -> "BRANCH" to Color(0xFFFF9800)
        is MergeNode -> "MERGE" to Color(0xFF9C27B0)
        is TransformNode -> "XFORM" to Color(0xFF009688)
        is LLMNode -> "LLM" to Color(0xFF3F51B5)
        is NotifyNode -> "NOTIFY" to Color(0xFFE53935)
        is ChatMessageNode -> "CHAT" to Color(0xFF00BCD4)
    }

    val glowColor = when (state) {
        is NodeState.Running -> Color(0xFF2196F3).copy(alpha = 0.5f)
        is NodeState.Done -> Color(0xFF4CAF50).copy(alpha = 0.4f)
        is NodeState.Errored -> Color(0xFFE91E63).copy(alpha = 0.5f)
        is NodeState.Skipped -> Color.Gray.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val borderColor = when (state) {
        is NodeState.Running -> Color(0xFF2196F3)
        is NodeState.Done -> Color(0xFF4CAF50)
        is NodeState.Errored -> Color(0xFFE91E63)
        is NodeState.Skipped -> Color.Gray
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(width = 140.dp, height = 72.dp)
            .shadow(
                elevation = if (state is NodeState.Running) 8.dp else 2.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = glowColor
            )
            .border(
                width = if (borderColor != Color.Transparent) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(node.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = typeLabel,
                color = typeColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = node.label.ifBlank { node.id.take(6) },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
