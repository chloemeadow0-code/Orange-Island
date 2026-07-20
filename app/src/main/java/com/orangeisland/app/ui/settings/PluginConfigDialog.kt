package com.orangeisland.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.PluginConfigField
import com.orangeisland.app.util.noOpBringIntoView

/**
 * Modal form rendered from a plugin's `manifest.config`. Each [PluginConfigField] becomes one
 * input; `required` fields must be non-blank before Save is accepted.
 *
 * Values are pre-filled from the previously-stored [initial] map (keyed by field name). On Save,
 * [onSave] receives the new map (all fields, including blanks); the caller persists it per
 * plugin id and it becomes the `__OI_PLUGIN_CONFIG` global.
 *
 * Currently only the "string" field type is rendered (as an OutlinedTextField); unknown types
 * fall back to a text field too, so future types can be added without breaking old builds.
 */
@Composable
fun PluginConfigDialog(
    pluginName: String,
    fields: List<PluginConfigField>,
    initial: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    // One mutable draft per field, seeded from persisted values. Edits stay local until Save so
    // the user can cancel without leaving half-written state behind.
    var drafts by remember(fields) {
        mutableStateOf(fields.associate { it.name to (initial[it.name] ?: "") })
    }
    var showRequiredError by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_config_title, pluginName), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fields.forEach { field ->
                    val value = drafts[field.name] ?: ""
                    ConfigFieldInput(
                        field = field,
                        value = value,
                        onValueChange = { v ->
                            drafts = drafts.toMutableMap().also { it[field.name] = v }
                            showRequiredError = false
                        },
                        showError = showRequiredError && field.required && value.isBlank(),
                    )
                }
                if (showRequiredError) {
                    Text(
                        stringResource(R.string.plugin_config_missing_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Block save if any required field is blank; the per-field error flags flip on.
                    val missing = fields.any { it.required && (drafts[it.name]?.isBlank() ?: true) }
                    if (missing) {
                        showRequiredError = true
                    } else {
                        onSave(drafts)
                    }
                },
            ) { Text(stringResource(R.string.plugin_config_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.plugin_config_cancel)) }
        },
    )
}

@Composable
private fun ConfigFieldInput(
    field: PluginConfigField,
    value: String,
    onValueChange: (String) -> Unit,
    showError: Boolean,
) {
    val label = buildString {
        append(field.label.ifBlank { field.name })
        if (field.required) append(" *")
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (field.description.isNotBlank()) {
            Text(
                field.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.noOpBringIntoView().padding(top = 6.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = field.placeholder.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                isError = showError,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
