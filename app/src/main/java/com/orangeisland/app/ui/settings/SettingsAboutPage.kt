package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    var showLogs by remember { mutableStateOf(false) }
    var showOssLicenses by remember { mutableStateOf(false) }
    var showOssNotice by remember { mutableStateOf(false) }

    if (showLogs) {
        SettingsLogsPage(onBack = { showLogs = false })
        return
    }

    if (showOssLicenses) {
        SettingsOssLicensesPage(onBack = { showOssLicenses = false })
        return
    }

    var showSandboxLicenses by remember { mutableStateOf(false) }

    if (showSandboxLicenses) {
        SettingsSandboxLicensesPage(onBack = { showSandboxLicenses = false })
        return
    }

    val context = LocalContext.current
    val packageInfo = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
    }
    val versionName = packageInfo?.versionName ?: "?"
    val versionCode = packageInfo?.longVersionCode ?: 0

    val autoUpdateCheck by viewModel.settings.autoUpdateCheck.collectAsState()
    var isCheckingUpdates by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CollapsingSettingsScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.about_info), items = listOf({
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.about_version)) },
                    supportingContent = { Text("v$versionName ($versionCode)") },
                    leadingContent = { IslandIcon(IslandIcons.About, size = 38.dp) }
                )
            }, {
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.about_developer)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.about_developer_name_line1))
                            Text(stringResource(R.string.about_developer_name_line2))
                        }
                    },
                    leadingContent = { IslandIcon(IslandIcons.AboutDeveloper, size = 38.dp) }
                )
            }))

            SettingsGroup(
                title = stringResource(R.string.about_updates),
                items = listOf({
                    SettingsItem(
                        modifier = Modifier.clickable(enabled = !isCheckingUpdates) {
                            scope.launch {
                                isCheckingUpdates = true
                                viewModel.triggerManualUpdateCheck()
                                isCheckingUpdates = false
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.about_check_updates)) },
                        supportingContent = {
                            Text(
                                if (isCheckingUpdates) stringResource(R.string.about_checking)
                                else stringResource(R.string.about_check_updates_desc)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    )
                }, {
                    SettingsItem(
                        headlineContent = { Text(stringResource(R.string.about_auto_update)) },
                        supportingContent = { Text(stringResource(R.string.about_auto_update_desc)) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Update,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoUpdateCheck,
                                onCheckedChange = { checked ->
                                    scope.launch { viewModel.settings.setAutoUpdateCheck(checked) }
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            scope.launch { viewModel.settings.setAutoUpdateCheck(!autoUpdateCheck) }
                        }
                    )
                })
            )

            SettingsGroup(title = stringResource(R.string.about_licenses), items = buildList {
                add({
                    SettingsItem(
                        modifier = Modifier.clickable { showOssNotice = true },
                        headlineContent = { Text(stringResource(R.string.about_oss_notice_title)) },
                        supportingContent = { Text(stringResource(R.string.about_oss_notice_body)) },
                        leadingContent = { IslandIcon(IslandIcons.Documentation, size = 38.dp) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    )
                })
                // Only offered by builds that actually bundle the sandbox components.
                if (hasSandboxLicenseAssets(context)) {
                    add({
                        SettingsItem(
                            modifier = Modifier.clickable { showSandboxLicenses = true },
                            headlineContent = { Text(stringResource(R.string.about_sandbox_licenses)) },
                            supportingContent = { Text(stringResource(R.string.about_sandbox_licenses_desc)) },
                            leadingContent = { IslandIcon(IslandIcons.Documentation, size = 38.dp) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        )
                    })
                }
                add({
                    SettingsItem(
                        modifier = Modifier.clickable { showOssLicenses = true },
                        headlineContent = { Text(stringResource(R.string.about_third_party_licenses)) },
                        supportingContent = { Text(stringResource(R.string.about_third_party_licenses_desc)) },
                        leadingContent = { IslandIcon(IslandIcons.Documentation, size = 38.dp) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    )
                })
            })

            SettingsGroup(title = stringResource(R.string.settings_group_logs), items = listOf({
                SettingsItem(
                    modifier = Modifier.clickable { showLogs = true },
                    headlineContent = { Text(stringResource(R.string.settings_logs)) },
                    supportingContent = { Text(stringResource(R.string.settings_logs_desc)) },
                    leadingContent = { IslandIcon(IslandIcons.Logs, size = 38.dp) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                )
            }))
        }
    }

    if (showOssNotice) {
        AlertDialog(
            onDismissRequest = { showOssNotice = false },
            title = { Text(stringResource(R.string.about_oss_notice_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.about_oss_notice_body))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Copyright (c) 2026 newo-ether",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.mit_license_full),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.about_bundled_components_heading),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.about_bundled_components_intro))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.bundled_component_llama))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.bundled_component_proot))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.bundled_component_talloc))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.bundled_component_jlatexmath))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.gpl_2_0_license_full),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.lgpl_3_0_license_full),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.gpl_3_0_license_full),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOssNotice = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
