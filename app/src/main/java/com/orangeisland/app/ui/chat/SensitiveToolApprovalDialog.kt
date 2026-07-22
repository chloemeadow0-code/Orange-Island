package com.orangeisland.app.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.orangeisland.app.R
import com.orangeisland.app.tool.SensitiveToolApprovalGate

@Composable
fun SensitiveToolApprovalDialog(gate: SensitiveToolApprovalGate?) {
    if (gate == null) return
    val pending by gate.pending.collectAsState()
    val head = pending.firstOrNull() ?: return
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = {
            // Dismiss-as-reject: closing the dialog without a tap rejects.
            gate.resolve(head.id, approved = false)
        },
        title = {
            Text(
                stringResource(R.string.sensitive_tool_approval_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = buildString {
                    appendLine("AI 想要调用「${head.toolName}」工具。")
                    appendLine()
                    append(head.description)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = { gate.resolve(head.id, approved = true) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.sensitive_tool_approve))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { gate.resolve(head.id, approved = false) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.sensitive_tool_reject))
            }
        }
    )
}
