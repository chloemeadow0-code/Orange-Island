package com.orangeisland.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.tool.UserInteractionGate

/**
 * Renders the head of [gate]'s pending queue as a card-style choice dialog.
 * When the model calls `ask_user_choice`, the provider suspends on the gate's
 * [UserInteractionGate.request] callback; this dialog surfaces the options so the user
 * can pick before the tool call returns.
 *
 * Single-choice mode uses radio buttons; multiple-choice mode uses checkboxes.
 * Each option is drawn as a rounded Surface card with primary-coloured highlight when
 * selected. The confirm button is disabled until at least one option is chosen.
 *
 * Hosted in [com.orangeisland.app.MainActivity]'s MainNavigation so it overlays both
 * the chat and the settings surfaces.
 */
@Composable
fun UserInteractionDialog(gate: UserInteractionGate?) {
    if (gate == null) return
    val pending by gate.pending.collectAsState()
    val head = pending.firstOrNull() ?: return

    // Reset selection state whenever the head request changes.
    var selectedIds by remember(head.id) { mutableStateOf<Set<String>>(emptySet()) }
    var customText by remember(head.id) { mutableStateOf("") }
    val isSingle = head.mode == "single"
    val hasSelection = selectedIds.isNotEmpty() || customText.isNotBlank()

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = { gate.cancel(head.id) },
        title = {
            Text(head.question, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                head.options.forEach { option ->
                    val isSelected = option.id in selectedIds
                    OptionCard(
                        option = option,
                        isSelected = isSelected,
                        isSingle = isSingle,
                        onClick = {
                            customText = ""
                            selectedIds = if (isSingle) {
                                setOf(option.id)
                            } else {
                                if (isSelected) selectedIds - option.id else selectedIds + option.id
                            }
                        }
                    )
                }
                if (head.allowCustom) {
                    Spacer(modifier = Modifier.width(4.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = {
                            customText = it
                            if (it.isNotBlank()) selectedIds = emptySet()
                        },
                        label = { Text("其他（自定义）") },
                        placeholder = { Text("输入你的回答…") },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (customText.isNotBlank()) {
                        gate.resolve(head.id, listOf("__custom__"), customText.trim())
                    } else {
                        gate.resolve(head.id, selectedIds.toList())
                    }
                },
                enabled = hasSelection,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                Text(stringResource(R.string.user_interaction_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { gate.cancel(head.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.user_interaction_cancel))
            }
        }
    )
}

@Composable
private fun OptionCard(
    option: UserInteractionGate.ChoiceOption,
    isSelected: Boolean,
    isSingle: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = shape,
        color = backgroundColor,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSingle) {
                RadioButton(
                    selected = isSelected,
                    onClick = null // handled by selectable
                )
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null // handled by selectable
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
