package com.orangeisland.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.ConversationSettings
import com.orangeisland.app.ui.components.clearFocusOnTap
import com.orangeisland.app.viewmodel.ChatViewModel

/** Rename-conversation dialog. Owns its own editable text, seeded from [initialName]. */
@Composable
internal fun ChatRenameDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        modifier = Modifier.clearFocusOnTap(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_chat), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Delete-conversation confirmation dialog. */
@Composable
internal fun ChatDeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_chat), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.delete_chat_confirm)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Per-conversation system-prompt selector dialog. */
@Composable
internal fun ChatSystemPromptDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val isNewChatMode by viewModel.isNewChatMode.collectAsState()
    val systemPrompts by viewModel.settings.systemPrompts.collectAsState()
    val activeSystemPromptId by viewModel.settings.activeSystemPromptId.collectAsState()

    val currentConversation = conversations.find { it.id == currentConversationId }
    val pendingPrompt by viewModel.pendingSystemPromptId.collectAsState()
    var selectedPromptId by remember(currentConversationId, pendingPrompt, currentConversation?.systemPromptId) {
        mutableStateOf(if (isNewChatMode) pendingPrompt else currentConversation?.systemPromptId)
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_prompt), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedPromptId = null }.padding(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedPromptId == null,
                            onClick = { selectedPromptId = null }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val globalDefaultTitle = systemPrompts.find { it.id == activeSystemPromptId }?.title ?: stringResource(R.string.no_system_prompt)
                        Text(stringResource(R.string.global_default_format, globalDefaultTitle))
                    }
                }
                items(systemPrompts, key = { it.id }) { prompt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedPromptId = prompt.id }.padding(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedPromptId == prompt.id,
                            onClick = { selectedPromptId = prompt.id }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(prompt.title)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isNewChatMode) {
                    viewModel.setPendingSystemPrompt(selectedPromptId)
                } else {
                    currentConversationId?.let { id ->
                        viewModel.setConversationSystemPrompt(id, selectedPromptId)
                    }
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Advanced (per-conversation generation params) dialog wrapper: resolves the active
 * conversation's overrides + global defaults, then defers to [AdvancedSettingsDialog].
 */
@Composable
internal fun ChatAdvancedSettingsDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val conversationSettings by viewModel.settings.conversationSettings.collectAsState()
    val maxContextWindow by viewModel.settings.maxContextWindow.collectAsState()
    val defaultTemperature by viewModel.settings.defaultTemperature.collectAsState()
    val defaultMaxTokens by viewModel.settings.defaultMaxTokens.collectAsState()
    val defaultTopP by viewModel.settings.defaultTopP.collectAsState()
    val defaultFrequencyPenalty by viewModel.settings.defaultFrequencyPenalty.collectAsState()
    val defaultPresencePenalty by viewModel.settings.defaultPresencePenalty.collectAsState()

    val currentId = currentConversationId
    val overrides = if (currentId != null) conversationSettings[currentId] ?: ConversationSettings()
        else ConversationSettings()
    val defaults = ConversationSettings(
        contextWindow = maxContextWindow,
        temperature = defaultTemperature,
        maxTokens = defaultMaxTokens,
        topP = defaultTopP,
        frequencyPenalty = defaultFrequencyPenalty,
        presencePenalty = defaultPresencePenalty
    )
    AdvancedSettingsDialog(
        overrides = overrides,
        globalDefaults = defaults,
        onSave = { settings ->
            if (currentId != null) {
                viewModel.settings.setConversationSettings(currentId, settings)
            } else {
                viewModel.setPendingConversationSettings(settings)
            }
            onDismiss()
        },
        onResetToDefaults = {
            if (currentId != null) {
                viewModel.settings.setConversationSettings(currentId, null)
            } else {
                viewModel.setPendingConversationSettings(null)
            }
        },
        onDismiss = onDismiss
    )
}
