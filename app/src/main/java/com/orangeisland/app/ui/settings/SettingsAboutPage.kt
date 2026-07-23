package com.orangeisland.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
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

    val context = LocalContext.current
    val packageInfo = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
    }
    val versionName = packageInfo?.versionName ?: "?"
    val versionCode = packageInfo?.longVersionCode ?: 0

    CollapsingSettingsScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.about_info), items = listOf({
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.about_version)) },
                    supportingContent = { Text("v$versionName ($versionCode)") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
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
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }))

            SettingsGroup(title = stringResource(R.string.about_licenses), items = listOf({
                SettingsItem(
                    modifier = Modifier.clickable { showOssNotice = true },
                    headlineContent = { Text(stringResource(R.string.about_oss_notice_title)) },
                    supportingContent = { Text(stringResource(R.string.about_oss_notice_body)) },
                    leadingContent = { Icon(Icons.Default.Balance, contentDescription = null) },
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
                    modifier = Modifier.clickable { showOssLicenses = true },
                    headlineContent = { Text(stringResource(R.string.about_third_party_licenses)) },
                    supportingContent = { Text(stringResource(R.string.about_third_party_licenses_desc)) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                )
            }))

            SettingsGroup(title = stringResource(R.string.settings_group_logs), items = listOf({
                SettingsItem(
                    modifier = Modifier.clickable { showLogs = true },
                    headlineContent = { Text(stringResource(R.string.settings_logs)) },
                    supportingContent = { Text(stringResource(R.string.settings_logs_desc)) },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
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
                Column {
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
