package com.orangeisland.app.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import com.orangeisland.app.R
import com.orangeisland.app.workflow.WorkflowApprovalGate

/**
 * Renders the head of [gate]'s pending queue as an AlertDialog. When the model calls an AI
 * authoring tool (workflow_create / _update / _delete / _set_enabled) the provider suspends on the
 * gate's [WorkflowApprovalGate.approval] callback; this dialog surfaces that card so the user can
 * approve or reject before the definition is persisted.
 *
 * Multi-line card text is scrollable (a create card with several conditions/actions is long); the
 * Approve button uses the primary colour, Reject the error colour. On either tap the gate's head
 * request is resolved, the deferred completes, and the tool call returns.
 *
 * Hosted in [com.orangeisland.app.MainActivity]'s MainNavigation so it overlays both the chat and
 * the settings surfaces — the model can author a workflow from either context.
 *
 * Independent implementation.
 */
@Composable
fun WorkflowApprovalDialog(gate: WorkflowApprovalGate?) {
    if (gate == null) return
    val pending by gate.pending.collectAsState()
    val head = pending.firstOrNull() ?: return
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = {
            // Dismiss-as-reject: closing the dialog without a tap rejects (the authoring tool then
            // returns its "requires approval" error, so the model knows not to retry silently).
            gate.resolve(head.id, approved = false)
        },
        title = {
            Text(stringResource(R.string.workflow_v2_approval_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                // Monospace-style card: preserve newlines. The card is pre-rendered by
                // WorkflowApprovalRenderer (Chinese human-readable text).
                Text(
                    text = head.card,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { gate.resolve(head.id, approved = true) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.workflow_v2_approval_approve))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { gate.resolve(head.id, approved = false) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.workflow_v2_approval_reject))
            }
        }
    )
}
