package com.orangeisland.app.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.orangeisland.app.R
import com.orangeisland.app.data.ShellDeviceConfig
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsShellPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val shellEnabled by viewModel.settings.shellEnabled.collectAsState()
    val shellConfirmEnabled by viewModel.settings.shellConfirmEnabled.collectAsState()
    val shellDevices by viewModel.settings.shellDevices.collectAsState()
    val sandboxEnabled by viewModel.settings.sandboxEnabled.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var newlyAddedDeviceId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmDeviceId by remember { mutableStateOf<String?>(null) }
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    // ── Sandbox navigation ──
    var showSandboxMgmt by remember { mutableStateOf(false) }
    var sandboxEntryCount by remember { mutableIntStateOf(0) }
    BackHandler(enabled = showSandboxMgmt) { showSandboxMgmt = false }

    GuardedAnimatedContent(
        targetState = showSandboxMgmt,
        forward = showSandboxMgmt
    ) { isMgmt ->
        if (isMgmt && viewModel.sandboxManager != null) {
            key(sandboxEntryCount) {
                SettingsSandboxPage(
                    sandboxManager = viewModel.sandboxManager!!,
                    onBack = { showSandboxMgmt = false },
                    showDocFab = showDocFab
                )
            }
        } else {
            val scrollState = rememberScrollState()
            CollapsingSettingsScaffold(
                title = stringResource(R.string.shell_title),
                onBack = onBack,
                scrollState = scrollState,
                floatingActionButton = { if (showDocFab) DocumentationFab("shell.md") }
            ) {
            SettingsGroupColumn {
                SettingsGroup(title = stringResource(R.string.shell_title), items = buildList {
                add {
                    SettingsItem(
                        headlineContent = { Text(stringResource(R.string.shell_enable)) },
                        supportingContent = { Text(stringResource(R.string.shell_enable_desc)) },
                        leadingContent = { IslandIcon(IslandIcons.Terminal, size = 38.dp) },
                        trailingContent = { Switch(checked = shellEnabled, onCheckedChange = { viewModel.settings.setShellEnabled(it) }) },
                        modifier = Modifier.clickable { viewModel.settings.setShellEnabled(!shellEnabled) }
                    )
                }
                if (shellEnabled) {
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.shell_confirm_setting)) },
                            supportingContent = { Text(stringResource(R.string.shell_confirm_setting_desc)) },
                            leadingContent = { IslandIcon(IslandIcons.RemoteActionConfirmation, size = 38.dp) },
                            trailingContent = { Switch(checked = shellConfirmEnabled, onCheckedChange = { viewModel.setShellConfirmEnabled(it) }) },
                            modifier = Modifier.clickable { viewModel.setShellConfirmEnabled(!shellConfirmEnabled) }
                        )
                    }
                }
            })

            if (shellEnabled) {
                // ── Local Sandbox ───────────────────────────
                if (viewModel.isSandboxFlavor) {
                    SandboxSection(viewModel, sandboxEnabled, onManage = { sandboxEntryCount++; showSandboxMgmt = true })
                } else {
                    SandboxNotSupportedSection()
                }

                // ── Remote Devices ──────────────────────────
                SettingsGroup(title = stringResource(R.string.shell_devices), items = buildList {
                    if (shellDevices.isEmpty()) {
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.shell_no_devices), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = { IslandIcon(IslandIcons.Terminal, size = 38.dp) },
                                modifier = Modifier.heightIn(min = 64.dp)
                            )
                        }
                    } else {
                        shellDevices.forEach { device -> add { DeviceEditor(viewModel, device, scrollState, density, newlyAddedDeviceId, onNewDeviceId = { newlyAddedDeviceId = it }, onDeleteConfirm = { deleteConfirmDeviceId = it }) } }
                    }
                    add {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                                val newId = UUID.randomUUID().toString()
                                newlyAddedDeviceId = newId
                                viewModel.addShellDevice(ShellDeviceConfig(id = newId, name = "", description = ""))
                            }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.shell_add_device), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                })
            }
            }

            // ── Delete confirm dialog ──
            deleteConfirmDeviceId?.let { deviceId ->
                val device = shellDevices.find { it.id == deviceId }
                AlertDialog(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    onDismissRequest = { deleteConfirmDeviceId = null },
                    title = { Text(stringResource(R.string.shell_delete_confirm_title), fontWeight = FontWeight.Bold) },
                    text = { Text(stringResource(R.string.shell_delete_confirm_message, device?.name?.ifBlank { stringResource(R.string.search_untitled) } ?: "")) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.settings.removeShellDevice(deviceId); deleteConfirmDeviceId = null },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) }
                    },
                    dismissButton = { TextButton(onClick = { deleteConfirmDeviceId = null }) { Text(stringResource(R.string.cancel)) } }
                )
            }

            if (showDocFab) Spacer(Modifier.height(80.dp))
            } // Scaffold content
        } // else
    } // GuardedAnimatedContent
}

// ═══════════════════════════════════════════════════════════════
// Sandbox Section — toggle + manage link, all detail on dedicated page
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SandboxSection(viewModel: ChatViewModel, sandboxEnabled: Boolean, onManage: () -> Unit) {
    SettingsGroup(title = stringResource(R.string.sandbox_title), items = buildList {
        add {
            SettingsItem(
                headlineContent = { Text(stringResource(R.string.sandbox_enable)) },
                supportingContent = { Text(stringResource(R.string.sandbox_enable_desc)) },
                leadingContent = { IslandIcon(IslandIcons.Sandbox, size = 38.dp) },
                trailingContent = { Switch(checked = sandboxEnabled, onCheckedChange = { viewModel.settings.setSandboxEnabled(it) }) },
                modifier = Modifier.clickable { viewModel.settings.setSandboxEnabled(!sandboxEnabled) }
            )
        }
        if (sandboxEnabled) {
            add {
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.sandbox_manage)) },
                    supportingContent = { Text(stringResource(R.string.sandbox_manage_desc)) },
                            leadingContent = { IslandIcon(IslandIcons.Sandbox, size = 38.dp) },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, stringResource(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { onManage() }
                )
            }
        }
    })
}

@Composable
private fun SandboxNotSupportedSection() {
    SettingsGroup(title = stringResource(R.string.sandbox_title), items = listOf {
        SettingsItem(
            headlineContent = { Text(stringResource(R.string.sandbox_not_supported)) },
            supportingContent = { Text(stringResource(R.string.sandbox_not_supported_desc)) },
            leadingContent = {
                Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        )
    })
}

// ═══════════════════════════════════════════════════════════════
// Device Editor (extracted for readability)
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceEditor(
    viewModel: ChatViewModel,
    device: ShellDeviceConfig,
    scrollState: androidx.compose.foundation.ScrollState,
    density: androidx.compose.ui.unit.Density,
    newlyAddedDeviceId: String?,
    onNewDeviceId: (String?) -> Unit,
    onDeleteConfirm: (String?) -> Unit
) {
    val isNewlyAdded = device.id == newlyAddedDeviceId
    var expanded by remember(device.id) { mutableStateOf(false) }
    var nameInput by remember(device.id) { mutableStateOf(device.name) }
    var descInput by remember(device.id) { mutableStateOf(device.description) }
    var typeInput by remember(device.id) { mutableStateOf(device.type) }
    var typeMenuExpanded by remember(device.id) { mutableStateOf(false) }
    var urlInput by remember(device.id) { mutableStateOf(device.serverUrl) }
    var keyInput by remember(device.id) { mutableStateOf(device.apiKey) }
    var sshHostInput by remember(device.id) { mutableStateOf(device.sshHost) }
    var sshPortInput by remember(device.id) { mutableStateOf(device.sshPort.toString()) }
    var sshUserInput by remember(device.id) { mutableStateOf(device.sshUser) }
    var sshPwInput by remember(device.id) { mutableStateOf(device.sshPassword) }
    var timeoutInput by remember(device.id) { mutableStateOf(device.timeout) }
    var sshHostKeyInput by remember(device.id) { mutableStateOf(device.sshHostKey) }
    val nameFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var verifying by remember(device.id) { mutableStateOf(false) }
    // Captured (key, fingerprint) awaiting the user's trust decision.
    var pendingVerify by remember(device.id) { mutableStateOf<Pair<String, String>?>(null) }
    var verifyError by remember(device.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(device) {
        nameInput = device.name; descInput = device.description; typeInput = device.type
        urlInput = device.serverUrl; keyInput = device.apiKey
        sshHostInput = device.sshHost; sshPortInput = device.sshPort.toString()
        sshUserInput = device.sshUser; sshPwInput = device.sshPassword; timeoutInput = device.timeout
        sshHostKeyInput = device.sshHostKey
    }

    LaunchedEffect(isNewlyAdded) {
        if (isNewlyAdded) {
            expanded = true; delay(50); urlFocusRequester.requestFocus()
            scrollState.animateScrollTo(scrollState.maxValue + (250 * density.density).toInt(), tween(500))
            onNewDeviceId(null)
        }
    }

    Column {
        SettingsItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name.ifBlank { stringResource(R.string.search_untitled) }, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = if (typeInput == "ssh") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(if (typeInput == "ssh") "SSH" else "Conch",
                            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (typeInput == "ssh") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            },
            supportingContent = {
                if (device.description.isNotBlank()) Text(device.description)
                else if (typeInput == "ssh" && sshHostInput.isNotBlank()) Text("$sshUserInput@$sshHostInput:$sshPortInput")
            },
            leadingContent = { IslandIcon(IslandIcons.Terminal, size = 38.dp) },
            trailingContent = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, stringResource(R.string.edit))
                }
            },
            modifier = Modifier.clickable { expanded = !expanded }
        )

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Spacer(Modifier.height(8.dp))

                // Type selector
                ExposedDropdownMenuBox(expanded = typeMenuExpanded, onExpandedChange = { typeMenuExpanded = it }) {
                    OutlinedTextField(
                        value = if (typeInput == "ssh") "SSH" else "Conch",
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.shell_device_type)) },
                        leadingIcon = { Icon(Icons.Default.Cable, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuExpanded) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        DropdownMenuItem(text = { Text("Conch") }, onClick = { typeInput = "conch"; typeMenuExpanded = false }, leadingIcon = { Icon(Icons.Default.Cable, null) })
                        DropdownMenuItem(text = { Text("SSH") }, onClick = { typeInput = "ssh"; typeMenuExpanded = false }, leadingIcon = { Icon(Icons.Default.Cable, null) })
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Conditional fields
                if (typeInput == "conch") {
                    OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, label = { Text(stringResource(R.string.shell_device_url)) },
                        placeholder = { Text(stringResource(R.string.shell_device_url_hint)) }, leadingIcon = { Icon(Icons.Default.Link, null) },
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().focusRequester(urlFocusRequester))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, label = { Text(stringResource(R.string.shell_device_key)) },
                        placeholder = { Text(stringResource(R.string.shell_device_key_hint)) }, leadingIcon = { Icon(Icons.Default.Key, null) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = sshHostInput, onValueChange = { sshHostInput = it }, label = { Text(stringResource(R.string.shell_device_host)) },
                        placeholder = { Text(stringResource(R.string.shell_device_host_hint)) }, leadingIcon = { Icon(Icons.Default.Dns, null) },
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().focusRequester(urlFocusRequester))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sshPortInput, onValueChange = { sshPortInput = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.shell_device_port)) }, leadingIcon = { Icon(Icons.Default.SettingsEthernet, null) },
                            singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(0.4f))
                        OutlinedTextField(value = sshUserInput, onValueChange = { sshUserInput = it },
                            label = { Text(stringResource(R.string.shell_device_user)) }, leadingIcon = { Icon(Icons.Default.Person, null) },
                            singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(0.6f))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = sshPwInput, onValueChange = { sshPwInput = it }, label = { Text(stringResource(R.string.shell_device_password)) },
                        leadingIcon = { Icon(Icons.Default.Password, null) }, visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())

                    // ── Host-key pinning (TOFU) ──
                    Spacer(Modifier.height(10.dp))
                    val pinned = sshHostKeyInput.isNotBlank()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            if (pinned) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                            null, modifier = Modifier.size(18.dp),
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (pinned) R.string.shell_ssh_host_key_pinned else R.string.shell_ssh_host_key_not_pinned),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pinned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                            if (pinned) {
                                Text(
                                    com.orangeisland.app.util.SshClient.fingerprintSha256(sshHostKeyInput),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (pinned) {
                            TextButton(onClick = { sshHostKeyInput = "" }) { Text(stringResource(R.string.shell_ssh_unpin)) }
                        }
                    }
                    verifyError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            verifyError = null; verifying = true
                            scope.launch {
                                val result = viewModel.verifySshHostKey(
                                    sshHostInput.trim(), sshPortInput.toIntOrNull() ?: 22,
                                    sshUserInput.trim().ifBlank { "root" }, sshPwInput, timeoutInput
                                )
                                verifying = false
                                result.fold(
                                    onSuccess = { pendingVerify = it },
                                    onFailure = { verifyError = it.message }
                                )
                            }
                        },
                        enabled = !verifying && sshHostInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (verifying) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.shell_ssh_verifying))
                        } else {
                            Icon(Icons.Default.Shield, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.shell_ssh_verify_host_key))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text(stringResource(R.string.shell_device_name)) },
                    placeholder = { Text(stringResource(R.string.shell_device_name_hint)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = descInput, onValueChange = { descInput = it }, label = { Text(stringResource(R.string.shell_device_desc)) },
                    placeholder = { Text(stringResource(R.string.shell_device_desc_hint)) }, leadingIcon = { Icon(Icons.Default.Description, null) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())

                // Timeout
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.shell_device_timeout), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shell_timeout_value, timeoutInput), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Slider(value = timeoutInput.toFloat(), onValueChange = { timeoutInput = (it / 5f).roundToInt() * 5 }, valueRange = 5f..120f, steps = 22, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDeleteConfirm(device.id) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.shell_remove_device))
                    }
                    Button(onClick = {
                        viewModel.updateShellDevice(device.copy(
                            name = nameInput.trim(), description = descInput.trim(), type = typeInput,
                            serverUrl = if (typeInput == "conch") urlInput.trim() else "",
                            apiKey = if (typeInput == "conch") keyInput.trim() else "",
                            sshHost = if (typeInput == "ssh") sshHostInput.trim() else "",
                            sshPort = sshPortInput.toIntOrNull() ?: 22,
                            sshUser = if (typeInput == "ssh") sshUserInput.trim().ifBlank { "root" } else "root",
                            sshPassword = if (typeInput == "ssh") sshPwInput else "",
                            sshHostKey = if (typeInput == "ssh") sshHostKeyInput else "",
                            timeout = timeoutInput
                        )); expanded = false
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                }
            }
        }

        // ── Host-key fingerprint confirmation ──
        pendingVerify?.let { (key, fingerprint) ->
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                onDismissRequest = { pendingVerify = null },
                icon = { Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.shell_ssh_verify_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(stringResource(R.string.shell_ssh_verify_message))
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                fingerprint,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { sshHostKeyInput = key; pendingVerify = null }) {
                        Text(stringResource(R.string.shell_ssh_trust))
                    }
                },
                dismissButton = { TextButton(onClick = { pendingVerify = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
}
