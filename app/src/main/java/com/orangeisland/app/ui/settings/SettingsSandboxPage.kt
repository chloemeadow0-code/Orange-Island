package com.orangeisland.app.ui.settings

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeisland.app.R
import com.orangeisland.app.sandbox.openSandboxHome
import com.orangeisland.app.sandbox.SandboxManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSandboxPage(sandboxManager: SandboxManager, onBack: () -> Unit, showDocFab: Boolean = false) {
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Core state — use fast sync check for instant first paint, confirm async
    var available by remember { mutableStateOf(sandboxManager.isAvailableSync()) }
    var backendPackagesLoading by remember { mutableStateOf(false) }
    var attemptedInstall by remember { mutableStateOf(false) } // set when user taps Install (this session)
    var installError by remember { mutableStateOf<String?>(null) }
    var installPkg by rememberSaveable { mutableStateOf(sandboxManager.pendingPkgName) }
    var lastInstallResult by remember { mutableStateOf<Boolean?>(null) } // local: success/fail for button state
    var deleteConfirm by remember { mutableStateOf<String?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }
    // Sync persisted text field back to sandbox manager
    LaunchedEffect(installPkg) { sandboxManager.pendingPkgName = installPkg }

    // Collected from sandbox manager backend
    val terminalOutput by sandboxManager.terminalOutput.collectAsState()
    val isBusy by sandboxManager.isBusy.collectAsState()
    // Rootfs install runs on the manager's own scope, so its state survives leaving/re-entering.
    val installingRootfs by sandboxManager.isInstallingRootfs.collectAsState()
    val downloadProgress by sandboxManager.downloadProgress.collectAsState()
    val showTerminal = terminalOutput.isNotBlank()

    fun clearAllState() { installPkg = ""; installError = null; deleteConfirm = null; lastInstallResult = null }

    fun installPackage(name: String) {
        if (isBusy) return
        installPkg = name; lastInstallResult = null; sandboxManager.installPackage(name)
    }

    fun upgradePackages() {
        if (isBusy) return
        lastInstallResult = null
        sandboxManager.upgradePackages()
    }

    LaunchedEffect(Unit) {
        try {
            val confirmed = sandboxManager.isAvailable()
            if (available != confirmed) available = confirmed
            if (available) sandboxManager.refreshPackageList()
        } catch (_: Exception) {}
    }

    // When a user-initiated rootfs install finishes, re-check availability and surface any error.
    LaunchedEffect(installingRootfs) {
        if (!installingRootfs && attemptedInstall) {
            attemptedInstall = false
            try {
                available = sandboxManager.isAvailable()
                if (available) { installError = null; sandboxManager.refreshPackageList() }
                else installError = sandboxManager.lastError ?: ctx.getString(R.string.sandbox_install_failed)
            } catch (e: Exception) { installError = e.message }
        }
    }

    // Package list comes directly from backend — always live
    val backendPackages by sandboxManager.packageList.collectAsState()

    val quickPkgs = listOf("python3", "git", "curl", "wget", "openssh", "nodejs", "build-base", "htop")
    val pkgCount = backendPackages.size
    var diskUsageMB by remember { mutableStateOf(0L) }
    LaunchedEffect(backendPackages.size) {
        try { diskUsageMB = sandboxManager.getDiskUsageMB() } catch (_: Exception) {}
    }

    CollapsingSettingsLazyScaffold(
        title = stringResource(R.string.sandbox_mgmt_title),
        onBack = onBack,
        listState = listState,
        floatingActionButton = { if (showDocFab) DocumentationFab("sandbox.md") }
    ) {
            // ═══ Dashboard ═══
                item {
                    SettingsGroup(title = stringResource(R.string.sandbox_env), items = listOf({
                        if (!available) {
                            // Not installed
                            SettingsItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.sandbox_alpine_version),
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Text(stringResource(R.string.sandbox_not_installed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (installingRootfs) {
                                            Spacer(Modifier.height(8.dp))
                                            val p = downloadProgress
                                            if (p != null) {
                                                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(4.dp))
                                            } else {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                if (p != null) stringResource(R.string.sandbox_downloading_rootfs, (p * 100).toInt())
                                                else stringResource(R.string.sandbox_extracting_rootfs),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        installError?.let { err ->
                                            Spacer(Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    err,
                                                    modifier = Modifier.padding(12.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        if (installingRootfs) Icons.Default.HourglassTop else Icons.Default.Warning,
                                        null,
                                        tint = if (installingRootfs) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                },
                                trailingContent = {
                                    if (!installingRootfs && !isBusy) {
                                        TextButton(onClick = {
                                            clearAllState()
                                            attemptedInstall = true
                                            sandboxManager.installRootfs()
                                        }) { Text(stringResource(R.string.sandbox_install), style = MaterialTheme.typography.labelMedium) }
                                    }
                                }
                            )
                        } else {
                            // Ready dashboard
                            SettingsItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.sandbox_alpine_version),
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            LinearProgressIndicator(
                                                progress = { (diskUsageMB.toFloat() / 2048f).coerceIn(0f, 1f) },
                                                modifier = Modifier.weight(0.3f).height(6.dp),
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                if (diskUsageMB < 1000) stringResource(R.string.sandbox_disk_usage_mb, diskUsageMB.coerceAtLeast(1))
                                                else stringResource(R.string.sandbox_disk_usage_gb, diskUsageMB / 1024f),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            stringResource(R.string.sandbox_dashboard_summary, pkgCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    TextButton(
                                        onClick = { upgradePackages() },
                                        enabled = !isBusy
                                    ) {
                                        Text(stringResource(R.string.sandbox_upgrade), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            )
                        }
                    }))
                }

                if (available) {
                    // ═══ Browse Files ═══
                    item {
                        SettingsGroup(
                            title = stringResource(R.string.sandbox_browse_files),
                            items = listOf({
                                SettingsItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.sandbox_browse_files),
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            stringResource(R.string.sandbox_browse_files_desc),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Folder,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        val homeDir = sandboxManager.getSandboxHomeDir()
                                        if (homeDir != null) {
                                            homeDir.mkdirs()
                                            try {
                                                ctx.openSandboxHome()
                                            } catch (e: Exception) {
                                                Log.w("SettingsSandboxPage", "Failed to open file manager", e)
                                            }
                                        }
                                    }
                                )
                            })
                        )
                    }

                    // ═══ Install Packages ═══
                    item {
                        SettingsGroup(title = stringResource(R.string.sandbox_install_packages), items = listOf({
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                // Input row
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = installPkg,
                                        onValueChange = { installPkg = it; lastInstallResult = null },
                                        label = { Text(stringResource(R.string.sandbox_package_name)) },
                                        placeholder = { Text(stringResource(R.string.sandbox_package_placeholder)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val installDone = installPkg.isNotBlank() && lastInstallResult != null && !isBusy
                                    val btnBgColor by animateColorAsState(
                                        targetValue = when {
                                            isBusy || installPkg.isBlank() -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            lastInstallResult == true -> MaterialTheme.colorScheme.secondaryContainer
                                            lastInstallResult == false -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        animationSpec = tween(400)
                                    )
                                    val btnContentColor by animateColorAsState(
                                        targetValue = when {
                                            isBusy || installPkg.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            lastInstallResult == true -> MaterialTheme.colorScheme.onSecondaryContainer
                                            lastInstallResult == false -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> MaterialTheme.colorScheme.onPrimary
                                        },
                                        animationSpec = tween(400)
                                    )
                                    Button(
                                        onClick = { if (installPkg.isNotBlank() && !isBusy && lastInstallResult == null) installPackage(installPkg.trim()) },
                                        enabled = true,
                                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = btnBgColor,
                                            contentColor = btnContentColor,
                                            disabledContainerColor = btnBgColor,
                                            disabledContentColor = btnContentColor
                                        ),
                                        modifier = Modifier.height(56.dp).widthIn(min = 110.dp).offset(y = 4.dp)
                                    ) {
                                        if (isBusy) {
                                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                                        } else if (installDone) {
                                            Text(if (lastInstallResult == true) stringResource(R.string.sandbox_installed_label) else stringResource(R.string.sandbox_failed_label))
                                        } else {
                                            Text(stringResource(R.string.sandbox_install))
                                        }
                                    }
                                }

                                // Terminal output (fixed-height, terminal theme, auto-scroll)
                                AnimatedVisibility(
                                    visible = showTerminal && terminalOutput.isNotBlank(),
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    val termScroll = rememberScrollState()
                                    LaunchedEffect(terminalOutput) { termScroll.animateScrollTo(termScroll.maxValue) }
                                    val terminalFg = MaterialTheme.colorScheme.onSurface
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 4.dp,
                                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(260.dp)
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                terminalOutput,
                                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                                                    .verticalScroll(termScroll)
                                                    .nestedScroll(object : NestedScrollConnection {
                                                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = available
                                                        override suspend fun onPreFling(available: Velocity): Velocity = available
                                                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
                                                    }),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily(Font(R.font.jetbrains_mono_regular)),
                                                    lineHeight = 18.sp
                                                ),
                                                color = terminalFg
                                            )
                                        }
                                    }
                                }

                                // Quick install chips
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.sandbox_quick_install) + ":",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    quickPkgs.forEach { pkg ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { installPackage(pkg) },
                                            enabled = !isBusy,
                                            label = { Text(pkg, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }))
                    }

                    // ═══ Installed Packages ═══
                    // Section header
                    item(key = "installed_header") {
                        Text(
                            text = stringResource(R.string.sandbox_installed_fmt, pkgCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    // Each package as its own LazyColumn item — avoids composing all
                    // packages in a single frame when the list is large.
                    when {
                        backendPackagesLoading -> item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            }
                        }
                        backendPackages.isEmpty() -> item(key = "empty") {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SettingsItem(
                                    headlineContent = {
                                        Text(stringResource(R.string.sandbox_no_packages), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    }
                                )
                            }
                        }
                        else -> items(backendPackages.size, key = { backendPackages[it].name }) { idx ->
                            val pkg = backendPackages[idx]
                            val isFirst = idx == 0
                            val isLast = idx == backendPackages.lastIndex
                            val shape = when {
                                backendPackages.size == 1 -> RoundedCornerShape(24.dp)
                                isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                isLast -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                else -> RoundedCornerShape(5.dp)
                            }
                            Surface(
                                shape = shape,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth().then(if (idx > 0) Modifier.padding(top = 2.dp) else Modifier)
                            ) {
                                SettingsItem(
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(pkg.name, fontWeight = FontWeight.Medium)
                                            if (pkg.version.isNotBlank()) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                                    Text("v${pkg.version}", Modifier.padding(horizontal = 4.dp, vertical = 1.dp), maxLines = 1,
                                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                }
                                            }
                                        }
                                    },
                                    supportingContent = {
                                        if (pkg.description.isNotBlank()) Text(pkg.description.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                    leadingContent = { Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary) },
                                    trailingContent = {
                                        IconButton(onClick = { deleteConfirm = pkg.name }, enabled = !isBusy, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Close, stringResource(R.string.sandbox_remove_content_desc), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // ═══ Danger Zone ═══
                    item {
                        Spacer(Modifier.height(24.dp))
                        SettingsGroup(bottomPadding = 0.dp, title = stringResource(R.string.sandbox_danger_zone), items = listOf({
                            SettingsItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.sandbox_reset),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.sandbox_reset_desc),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier.clickable { resetConfirm = true }
                            )
                        }))
                        Spacer(Modifier.height(16.dp))
                    }

                    // Doc FAB clearance
                    if (showDocFab) {
                        item(key = "doc_spacer") { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
    }

    // ── Delete confirm dialog ──
    deleteConfirm?.let { pkgName ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { deleteConfirm = null },
            title = { Text(stringResource(R.string.sandbox_remove_pkg_title, pkgName), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.sandbox_remove_pkg_desc, pkgName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isBusy) return@TextButton
                        scope.launch {
                            deleteConfirm = null
                            sandboxManager.removePackage(pkgName)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ── Reset confirm dialog ──
    if (resetConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { resetConfirm = false },
            title = { Text(stringResource(R.string.sandbox_reset), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.sandbox_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            sandboxManager.reset(); available = false
                            clearAllState()
                        }
                        resetConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
