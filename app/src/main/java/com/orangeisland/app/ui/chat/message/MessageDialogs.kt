package com.orangeisland.app.ui.chat.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.orangeisland.app.R
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.apiModelName
import com.orangeisland.app.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Read-only message metadata (timestamp + resolved model name) shown from the overflow menu. */
@Composable
internal fun MessageInfoDialog(
    message: ChatMessage,
    modelAliases: Map<String, String>,
    onDismiss: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val dateString = sdf.format(Date(message.timestamp))
    val modelDisplay = if (message.modelName != null) {
        val parsed = message.modelName?.let { com.orangeisland.app.model.ModelId.parse(it) }
        val modelId = parsed?.apiModelName ?: message.modelName
        val provider = parsed?.providerName ?: Constants.PROVIDER_UNKNOWN
        modelAliases[message.modelName] ?: ("$modelId ($provider)")
    } else stringResource(R.string.unknown)

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_info), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.time_with_label, dateString), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
                if (message.participant == Participant.MODEL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.model_with_label, modelDisplay), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.provider_close)) }
        }
    )
}

/** Destructive confirmation for deleting a single message (and its subtree). */
@Composable
internal fun MessageDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_message_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.delete_message_confirm)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
