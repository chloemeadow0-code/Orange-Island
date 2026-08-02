package com.orangeisland.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.data.McpServerConfig
import com.orangeisland.app.viewmodel.ChatViewModel
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * MCP 服务器详情页：新建 / 编辑一个服务器，分两个 tab（基础设置 / 工具）。
 * 复用 CollapsingSettingsScaffold + PillTabSwitcher，跟其它设置子页面风格统一。
 */
@Composable
internal fun McpServerDetailScreen(
    existing: McpServerConfig?,
    existingNames: Set<String>,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val name = remember { mutableStateOf(existing?.name ?: "") }
    val url = remember { mutableStateOf(existing?.url ?: "") }
    val transport = remember { mutableStateOf(existing?.transport ?: McpServerConfig.TRANSPORT_STREAMABLE) }
    val enabled = remember { mutableStateOf(existing?.enabled ?: true) }
    val nameError = remember { mutableStateOf<String?>(null) }
    val urlError = remember { mutableStateOf<String?>(null) }

    // Headers 用 (name, value) 对的列表表示，保存时再序列化回 JSON。
    val headerPairs = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            val parsed = try {
                kotlinx.serialization.json.Json.parseToJsonElement(existing?.headersJson ?: "{}")
                    .let { it as? kotlinx.serialization.json.JsonObject }
            } catch (_: Exception) { null }
            parsed?.entries?.forEach { (k, v) ->
                val value = (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                add(k to value)
            }
        }
    }

    val disabledTools = remember {
        mutableStateListOf<String>().apply { addAll(existing?.disabledToolNames ?: emptySet()) }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val msgInvalidName = stringResource(R.string.mcp_invalid_name)
    val msgNameTaken = stringResource(R.string.mcp_name_taken)
    val msgInvalidUrl = stringResource(R.string.mcp_invalid_url)

    fun buildConfig(): McpServerConfig? {
        val n = name.value.trim()
        val u = url.value.trim().let { if (it.endsWith("/")) it.dropLast(1) else it }
        when {
            n.isEmpty() -> { nameError.value = msgInvalidName; return null }
            n in existingNames -> { nameError.value = msgNameTaken; return null }
            !u.startsWith("http://", true) && !u.startsWith("https://", true) -> { urlError.value = msgInvalidUrl; return null }
        }
        val headersMap = headerPairs.filter { it.first.isNotBlank() }.associate { it.first to it.second }
        val headersJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(headersMap.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
        )
        return (existing ?: McpServerConfig(id = UUID.randomUUID().toString(), name = n, url = u)).copy(
            name = n, url = u, transport = transport.value, headersJson = headersJson, enabled = enabled.value,
            disabledToolNames = disabledTools.toSet()
        )
    }

    CollapsingSettingsScaffold(
        title = existing?.name?.ifBlank { stringResource(R.string.mcp_new_server_title) } ?: stringResource(R.string.mcp_new_server_title),
        onBack = onBack,
        actions = {
            TextButton(onClick = { buildConfig()?.let(onSave) }) {
                Text(stringResource(R.string.mcp_save))
            }
        }
    ) {
        PillTabSwitcher(
            tabs = listOf(stringResource(R.string.mcp_basic_tab), stringResource(R.string.mcp_tools_tab)),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedTab == 0) {
            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it; nameError.value = null },
                label = { Text(stringResource(R.string.mcp_server_name)) },
                isError = nameError.value != null,
                supportingText = nameError.value?.let { { Text(it) } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url.value,
                onValueChange = { url.value = it; urlError.value = null },
                label = { Text(stringResource(R.string.mcp_server_url)) },
                placeholder = { Text(stringResource(R.string.mcp_server_url_hint)) },
                isError = urlError.value != null,
                supportingText = urlError.value?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.mcp_transport), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row {
                FilterChip(
                    selected = transport.value == McpServerConfig.TRANSPORT_STREAMABLE,
                    onClick = { transport.value = McpServerConfig.TRANSPORT_STREAMABLE },
                    label = { Text(stringResource(R.string.mcp_transport_streamable)) }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = transport.value == McpServerConfig.TRANSPORT_SSE,
                    onClick = { transport.value = McpServerConfig.TRANSPORT_SSE },
                    label = { Text(stringResource(R.string.mcp_transport_sse)) }
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled.value, onCheckedChange = { enabled.value = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mcp_enabled))
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.mcp_headers), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.mcp_headers_example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            headerPairs.forEachIndexed { index, pair ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = pair.first,
                        onValueChange = { headerPairs[index] = it to pair.second },
                        placeholder = { Text(stringResource(R.string.mcp_header_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = pair.second,
                        onValueChange = { headerPairs[index] = pair.first to it },
                        placeholder = { Text(stringResource(R.string.mcp_header_value_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { headerPairs.removeAt(index) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp))
                    }
                }
            }
            OutlinedButton(
                onClick = { headerPairs.add("" to "") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.mcp_add_header))
            }
            if (onDelete != null) {
                Spacer(Modifier.height(28.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mcp_delete_server))
                }
            }
        } else {
            McpToolsTab(
                existing = existing,
                viewModel = viewModel,
                disabledTools = disabledTools,
                onToggleTool = { toolName, enabled ->
                    if (enabled) disabledTools.remove(toolName) else disabledTools.add(toolName)
                }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.mcp_delete), fontWeight = FontWeight.Bold) },
            text = { Text(existing?.name?.ifBlank { existing.url } ?: "") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete?.invoke() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.mcp_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.mcp_cancel)) } }
        )
    }
}

@Composable
private fun McpToolsTab(
    existing: McpServerConfig?,
    viewModel: ChatViewModel,
    disabledTools: List<String>,
    onToggleTool: (toolName: String, enabled: Boolean) -> Unit,
) {
    var loading by remember(existing?.id) { mutableStateOf(existing != null) }
    var tools by remember(existing?.id) { mutableStateOf<List<Tool>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        val config = existing ?: return
        loading = true
        scope.launch {
            tools = viewModel.fetchMcpTools(config)
            loading = false
        }
    }

    LaunchedEffect(existing?.id) {
        if (existing != null) refresh()
    }

    if (existing == null) {
        Text(
            stringResource(R.string.mcp_tools_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { refresh() }, enabled = !loading) {
            Text(stringResource(R.string.mcp_tools_refresh))
        }
    }

    when {
        loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.mcp_tools_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        tools.isEmpty() -> {
            Text(
                stringResource(R.string.mcp_tools_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> {
            Text(
                stringResource(R.string.mcp_tool_toggle_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            tools.forEach { tool ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Image(
                        painterResource(R.drawable.island_mcp_tool),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tool.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        tool.description?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = tool.name !in disabledTools,
                        onCheckedChange = { enabled -> onToggleTool(tool.name, enabled) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}
