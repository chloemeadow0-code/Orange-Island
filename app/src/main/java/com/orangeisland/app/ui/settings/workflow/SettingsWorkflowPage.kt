package com.orangeisland.app.ui.settings.workflow

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    onBack: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf("list") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var logsId by rememberSaveable { mutableStateOf<String?>(null) }

    GuardedAnimatedContent(
        targetState = screen,
        forward = screen != "list"
    ) { current ->
        when (current) {
            "list" -> WorkflowListPage(
                viewModel = viewModel,
                onBack = onBack,
                onEdit = { workflow ->
                    viewModel.selectWorkflow(workflow.id)
                    editId = workflow.id
                    screen = "edit"
                },
                onLogs = { workflowId ->
                    viewModel.selectWorkflow(workflowId)
                    logsId = workflowId
                    screen = "logs"
                }
            )

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
