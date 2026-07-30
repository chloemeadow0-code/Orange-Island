package com.orangeisland.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeisland.app.R
import com.orangeisland.app.data.UsageLogManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLogsPage(onBack: () -> Unit) {
    val entries by UsageLogManager.entries.collectAsState()
    val visibleEntries = entries.filter { entry ->
        when (entry.type) {
            UsageLogManager.Type.REQUEST, UsageLogManager.Type.TOOL -> true
            UsageLogManager.Type.MODEL, UsageLogManager.Type.CONVERSATION -> entry.isError
            else -> true
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showClearDialog = true },
                        enabled = visibleEntries.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.logs_clear))
                    }
                }
            )
        }
    ) { padding ->
        if (visibleEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.logs_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(visibleEntries.asReversed(), key = { it.id }) { entry ->
                    LogItemCard(entry)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.logs_clear_confirm_title)) },
            text = { Text(stringResource(R.string.logs_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    UsageLogManager.clear()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.logs_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LogItemCard(entry: UsageLogManager.Entry) {
    var expanded by remember { mutableStateOf(false) }

    val icon = when (entry.type) {
        UsageLogManager.Type.MODEL -> Icons.Default.Chat
        UsageLogManager.Type.TOOL -> Icons.Default.Build
        UsageLogManager.Type.REQUEST -> Icons.Default.Cloud
        UsageLogManager.Type.CONVERSATION -> Icons.Default.Message
        UsageLogManager.Type.SYNC -> Icons.Default.Sync
        UsageLogManager.Type.SECURITY -> Icons.Default.Security
    }
    val color = when (entry.type) {
        UsageLogManager.Type.MODEL -> MaterialTheme.colorScheme.primary
        UsageLogManager.Type.TOOL -> MaterialTheme.colorScheme.secondary
        UsageLogManager.Type.REQUEST -> MaterialTheme.colorScheme.tertiary
        UsageLogManager.Type.CONVERSATION -> Color(0xFFFF9800)
        UsageLogManager.Type.SYNC -> Color(0xFF009688)
        UsageLogManager.Type.SECURITY -> MaterialTheme.colorScheme.error
    }
    val containerColor = when (entry.type) {
        UsageLogManager.Type.MODEL -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        UsageLogManager.Type.TOOL -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        UsageLogManager.Type.REQUEST -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        UsageLogManager.Type.CONVERSATION -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        UsageLogManager.Type.SYNC -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        UsageLogManager.Type.SECURITY -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    }
    val labelRes = when (entry.type) {
        UsageLogManager.Type.MODEL -> R.string.logs_type_model
        UsageLogManager.Type.TOOL -> R.string.logs_type_tool
        UsageLogManager.Type.REQUEST -> R.string.logs_type_request
        UsageLogManager.Type.CONVERSATION -> R.string.logs_type_conversation
        UsageLogManager.Type.SYNC -> R.string.logs_type_sync
        UsageLogManager.Type.SECURITY -> R.string.logs_type_security
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entry.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.details.take(120) + if (entry.details.length > 120) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            AnimatedVisibility(
                visible = expanded && entry.details.isNotBlank(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
