package com.orangeisland.app.ui.settings.workflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.orangeisland.app.model.Workflow
import com.orangeisland.app.ui.settings.GuardedAnimatedContent
import com.orangeisland.app.viewmodel.WorkflowViewModel

/**
 * Settings-embedded workflow host. Manages sub-navigation between list, editor, and log pages
 * so the user never leaves the settings overlay.
 */
@Composable
fun SettingsWorkflowPage(
    viewModel: WorkflowViewModel,
    onBack: () -> Unit,
    onEditInChat: (prefilledPrompt: String) -> Unit = {}
) {
    var screen by rememberSaveable { mutableStateOf("list") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var logsId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    GuardedAnimatedContent(
        targetState = screen,
        forward = screen != "list"
    ) { current ->
        when (current) {
            "list" -> WorkflowListPage(
                viewModel = viewModel,
                onBack = onBack,
                // Tap a linear row: open the read-only detail card. We already have the
                // LinearWorkflow object, so set it directly into selectedLinear to skip the
                // async decode and render the detail page immediately.
                onOpenLinear = { wf ->
                    viewModel.selectLinearWorkflow(wf.id)
                    editId = wf.id
                    detailId = wf.id
                    screen = "detail_or_edit"
                },
                // Tap a graph row: open the node editor.
                onEdit = { workflow ->
                    viewModel.selectWorkflow(workflow.id)
                    editId = workflow.id
                    detailId = workflow.id
                    screen = "detail_or_edit"
                },
                onLogs = { workflowId ->
                    viewModel.selectWorkflow(workflowId)
                    logsId = workflowId
                    screen = "logs"
                }
            )

            // First-hop resolution: decode the row's mode and route to the detail card (linear) or
            // the editor (graph). A separate state keeps the list→detail transition animated.
            "detail_or_edit" -> {
                val linear by viewModel.selectedLinear.collectAsState()
                val graph by viewModel.selectedWorkflow.collectAsState()
                val id = detailId
                when {
                    // Linear: detail card.
                    linear != null && linear?.id == id -> WorkflowDetailPage(
                        workflow = linear!!,
                        viewModel = viewModel,
                        onBack = { screen = "list" },
                        onEditInChat = onEditInChat
                    )
                    // Graph: editor (legacy path).
                    graph != null && graph?.id == id -> {
                        editId = id
                        screen = "edit"
                    }
                    // Loading: blank until the async select completes.
                    else -> Box(Modifier.fillMaxSize())
                }
            }

            "edit" -> {
                val workflow by viewModel.selectedWorkflow.collectAsState()
                val wf = workflow
                if (wf != null && wf.id == editId) {
                    WorkflowEditorForm(
                        workflow = wf,
                        onSave = { updated ->
                            viewModel.saveWorkflow(updated)
                            screen = "list"
                        },
                        onBack = { screen = "list" },
                        onOpenCanvas = {
                            // Persist any in-flight edits before switching so the canvas sees them.
                            screen = "canvas"
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { screen = "list" }
                }
            }

            "canvas" -> {
                val workflow by viewModel.selectedWorkflow.collectAsState()
                val nodeStates by viewModel.activeNodeStates.collectAsState()
                val wf = workflow
                if (wf != null && wf.id == editId) {
                    WorkflowCanvasPage(
                        workflow = wf,
                        nodeStates = nodeStates,
                        onSave = { updated ->
                            viewModel.saveWorkflow(updated)
                            screen = "edit"
                        },
                        onBack = { screen = "edit" }
                    )
                } else {
                    LaunchedEffect(Unit) { screen = "list" }
                }
            }

            "logs" -> {
                val workflow by viewModel.selectedWorkflow.collectAsState()
                val wf = workflow
                if (wf != null && wf.id == logsId) {
                    WorkflowRunLogPage(
                        workflowId = logsId!!,
                        workflowName = wf.name,
                        viewModel = viewModel,
                        onBack = { screen = "list" }
                    )
                } else {
                    LaunchedEffect(Unit) { screen = "list" }
                }
            }

            else -> {
                LaunchedEffect(Unit) { screen = "list" }
            }
        }
    }
}
