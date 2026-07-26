package com.orangeisland.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orangeisland.app.R
import com.orangeisland.app.viewmodel.ChatViewModel
import com.orangeisland.app.viewmodel.PermissionController

/**
 * Hub page for the Device Access tools. Lists the five tools (device info, location,
 * notification, screen usage, calendar) with a per-tool enable switch and a live permission
 * indicator. Designed as a single scrollable list rather than five sub-pages so the user
 * sees the whole surface area and its permission status at a glance.
 *
 * Permission UX follows Android's split:
 *  - Runtime permissions (location, calendar) → requested in-app via
 *    [ActivityResultContracts.RequestMultiplePermissions] the moment the user flips the switch.
 *  - Special permissions (notification listener, usage access) → cannot be requested in-app;
 *    the row shows a "open system settings" affordance driven by [PermissionController].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDeviceAccessPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenHealthPage: () -> Unit = {}
) {
    val settings = viewModel.settings
    val deviceInfoEnabled by settings.deviceInfoEnabled.collectAsState()
    val locationEnabled by settings.locationEnabled.collectAsState()
    val calendarEnabled by settings.calendarEnabled.collectAsState()
    val notificationEnabled by settings.notificationEnabled.collectAsState()
    val usageStatsEnabled by settings.usageStatsEnabled.collectAsState()
    val navigationEnabled by settings.navigationEnabled.collectAsState()
    val appLockEnabled by settings.appLockEnabled.collectAsState()
    val toastEnabled by settings.toastEnabled.collectAsState()
    val alarmEnabled by settings.alarmEnabled.collectAsState()
    val healthToolEnabled by settings.healthToolEnabled.collectAsState()
    val uiAutomationEnabled by settings.uiAutomationEnabled.collectAsState()
    val userInteractionEnabled by settings.userInteractionEnabled.collectAsState()
    val environmentAwarenessEnabled by settings.environmentAwarenessEnabled.collectAsState()
    val amapApiKey by settings.amapApiKey.collectAsState()
    val pc = viewModel.permissionController
    var amapKeyDraft by remember(amapApiKey) { mutableStateOf(amapApiKey) }

    // Health / Gadgetbridge / Sync state
    val gadgetbridgeEnabled by settings.gadgetbridgeEnabled.collectAsState()
    val gadgetbridgeDbPath by settings.gadgetbridgeDbPath.collectAsState()
    var gadgetbridgeDbPathDraft by remember(gadgetbridgeDbPath) { mutableStateOf(gadgetbridgeDbPath) }
    val healthSyncEnabled by settings.healthSyncEnabled.collectAsState()
    val healthSyncSupabaseUrl by settings.healthSyncSupabaseUrl.collectAsState()
    var healthSyncUrlDraft by remember(healthSyncSupabaseUrl) { mutableStateOf(healthSyncSupabaseUrl) }
    val healthSyncSupabaseApiKey by settings.healthSyncSupabaseApiKey.collectAsState()
    var healthSyncKeyDraft by remember(healthSyncSupabaseApiKey) { mutableStateOf(healthSyncSupabaseApiKey) }
    val healthSyncTableName by settings.healthSyncTableName.collectAsState()
    var healthSyncTableDraft by remember(healthSyncTableName) { mutableStateOf(healthSyncTableName) }

    // Re-query special-permission state whenever this page comes back to the foreground
    // (the user may have just toggled the listener / usage-access in system Settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                pc.refreshNotificationListenerState()
                pc.refreshAccessibilityState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notificationListenerGranted by pc.notificationListenerEnabledFlow.collectAsState()
    val accessibilityGranted by pc.accessibilityEnabledFlow.collectAsState()
    val uiAutomationAccessibilityGranted by pc.uiAutomationAccessibilityEnabledFlow.collectAsState()

    // Single launcher covers both runtime-permission tools (location + calendar); the contract
    // accepts whatever permission array we hand to launch() at call time.
    val runtimePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* outcome is reflected on next permission-state read; nothing to do here */ }

    fun requestLocation() {
        runtimePermLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }
    fun requestCalendar() {
        runtimePermLauncher.launch(arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        ))
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.device_access_title),
        onBack = onBack,
        scrollState = rememberScrollState(),
    ) {
        SettingsGroupColumn(spacing = 16.dp) {
            SettingsGroup(
                title = stringResource(R.string.device_access_group_tools),
                titleStyle = MaterialTheme.typography.titleSmall,
                items = buildList {
                // Device info — no permission
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_device_info_title),
                        desc = stringResource(R.string.device_access_device_info_desc),
                        icon = Icons.Default.BatteryFull,
                        checked = deviceInfoEnabled,
                        onCheckedChange = { settings.setDeviceInfoEnabled(it) },
                        permissionState = PermissionState.NotRequired
                    )
                }
                // Location
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_location_title),
                        desc = stringResource(R.string.device_access_location_desc),
                        icon = Icons.Default.LocationOn,
                        checked = locationEnabled,
                        onCheckedChange = { on ->
                            settings.setLocationEnabled(on)
                            if (on) requestLocation()
                        },
                        permissionState = if (pc.isGranted(PermissionController.Tool.LOCATION))
                            PermissionState.Granted else PermissionState.RuntimeNeeded
                    )
                }
                // Calendar
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_calendar_title),
                        desc = stringResource(R.string.device_access_calendar_desc),
                        icon = Icons.Default.CalendarMonth,
                        checked = calendarEnabled,
                        onCheckedChange = { on ->
                            settings.setCalendarEnabled(on)
                            if (on) requestCalendar()
                        },
                        permissionState = if (pc.isGranted(PermissionController.Tool.CALENDAR))
                            PermissionState.Granted else PermissionState.RuntimeNeeded
                    )
                }
                // Notifications
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_notification_title),
                        desc = stringResource(R.string.device_access_notification_desc),
                        icon = Icons.Default.Notifications,
                        checked = notificationEnabled,
                        onCheckedChange = { settings.setNotificationEnabled(it) },
                        permissionState = if (notificationListenerGranted)
                            PermissionState.Granted else PermissionState.SpecialNeeded(
                            onClick = { pc.openSystemSettings(PermissionController.Tool.NOTIFICATION) }
                        )
                    )
                }
                // Usage stats
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_usage_title),
                        desc = stringResource(R.string.device_access_usage_desc),
                        icon = Icons.Default.Timeline,
                        checked = usageStatsEnabled,
                        onCheckedChange = { settings.setUsageStatsEnabled(it) },
                        permissionState = if (pc.isGranted(PermissionController.Tool.USAGE_STATS))
                            PermissionState.Granted else PermissionState.SpecialNeeded(
                            onClick = { pc.openSystemSettings(PermissionController.Tool.USAGE_STATS) }
                        )
                    )
                }
                // Navigation (open URL, open app, share, settings)
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_navigation_title),
                        desc = stringResource(R.string.device_access_navigation_desc),
                        icon = Icons.Default.OpenInNew,
                        checked = navigationEnabled,
                        onCheckedChange = { settings.setNavigationEnabled(it) },
                        permissionState = PermissionState.NotRequired
                    )
                }
                // App Lock
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_app_lock_title),
                        desc = stringResource(R.string.device_access_app_lock_desc),
                        icon = Icons.Default.Lock,
                        checked = appLockEnabled,
                        onCheckedChange = { settings.setAppLockEnabled(it) },
                        permissionState = if (accessibilityGranted)
                            PermissionState.Granted else PermissionState.SpecialNeeded(
                            onClick = { pc.openSystemSettings(PermissionController.Tool.ACCESSIBILITY) }
                        )
                    )
                }
                // Toast
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_toast_title),
                        desc = stringResource(R.string.device_access_toast_desc),
                        icon = Icons.Outlined.NotificationsActive,
                        checked = toastEnabled,
                        onCheckedChange = { settings.setToastEnabled(it) },
                        permissionState = PermissionState.NotRequired
                    )
                }
                // Alarm / Timer (system clock app delegate, no permission needed)
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_alarm_title),
                        desc = stringResource(R.string.device_access_alarm_desc),
                        icon = Icons.Default.Alarm,
                        checked = alarmEnabled,
                        onCheckedChange = { settings.setAlarmEnabled(it) },
                        permissionState = PermissionState.NotRequired
                    )
                }
                // User Interaction (ask_user_choice card dialog)
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_user_interaction_title),
                        desc = stringResource(R.string.device_access_user_interaction_desc),
                        icon = Icons.Default.Chat,
                        checked = userInteractionEnabled,
                        onCheckedChange = { settings.setUserInteractionEnabled(it) },
                        permissionState = PermissionState.NotRequired
                    )
                }
                // UI Automation (tap/swipe/scroll/global-action/inspect)
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_ui_automation_title),
                        desc = stringResource(R.string.device_access_ui_automation_desc),
                        icon = Icons.Default.TouchApp,
                        checked = uiAutomationEnabled,
                        onCheckedChange = { settings.setUiAutomationEnabled(it) },
                        permissionState = if (uiAutomationAccessibilityGranted)
                            PermissionState.Granted else PermissionState.SpecialNeeded(
                            onClick = { pc.openSystemSettings(PermissionController.Tool.UI_AUTOMATION) }
                        )
                    )
                }
                // Environment Awareness — injects {app_context} into the system prompt
                add {
                    ToolToggleRow(
                        title = stringResource(R.string.device_access_env_awareness_title),
                        desc = stringResource(R.string.device_access_env_awareness_desc),
                        icon = Icons.Default.Smartphone,
                        checked = environmentAwarenessEnabled,
                        onCheckedChange = { settings.setEnvironmentAwarenessEnabled(it) },
                        permissionState = PermissionState.NotRequired
                    )
                }
            })

            // Amap key — only relevant when location is enabled.
            if (locationEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.device_access_amap_key_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    Text(
                        stringResource(R.string.device_access_amap_key_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amapKeyDraft,
                        onValueChange = { amapKeyDraft = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = {
                            Text(
                                stringResource(R.string.device_access_amap_key_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { settings.setAmapApiKey(amapKeyDraft.trim()) },
                        enabled = amapKeyDraft.trim() != amapApiKey,
                    ) { Text(stringResource(R.string.save)) }
                }
            }

            // ── 健康与后台同步 ─────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "健康与后台同步",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Gadgetbridge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("Gadgetbridge", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "读取手环健康数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = gadgetbridgeEnabled,
                                onCheckedChange = { settings.setGadgetbridgeEnabled(it) },
                            )
                        }
                        if (gadgetbridgeEnabled) {
                            OutlinedTextField(
                                value = gadgetbridgeDbPathDraft,
                                onValueChange = { gadgetbridgeDbPathDraft = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                placeholder = {
                                    Text(
                                        "数据库路径（留空使用默认）",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { settings.setGadgetbridgeDbPath(gadgetbridgeDbPathDraft.trim()) },
                                    enabled = gadgetbridgeDbPathDraft.trim() != gadgetbridgeDbPath,
                                ) { Text(stringResource(R.string.save)) }
                                Button(onClick = onOpenHealthPage) { Text("查看健康数据") }
                            }
                        }

                        // 允许 AI 读取健康数据
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("允许 AI 读取健康数据", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "AI 能查看步数、心率、睡眠等数据并回答相关问题（会发送给你使用的模型服务商）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = healthToolEnabled, onCheckedChange = { settings.setHealthToolEnabled(it) })
                        }

                        HorizontalDivider()

                        // 后台同步
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("后台同步到 Supabase", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "每15分钟自动上传健康+设备数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = healthSyncEnabled,
                                onCheckedChange = { settings.setHealthSyncEnabled(it) },
                            )
                        }
                        if (healthSyncEnabled) {
                            OutlinedTextField(
                                value = healthSyncUrlDraft,
                                onValueChange = { healthSyncUrlDraft = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                placeholder = {
                                    Text(
                                        "Supabase URL",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = healthSyncKeyDraft,
                                onValueChange = { healthSyncKeyDraft = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                placeholder = {
                                    Text(
                                        "Supabase Anon Key",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = healthSyncTableDraft,
                                onValueChange = { healthSyncTableDraft = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                placeholder = {
                                    Text(
                                        "表名（默认 device_data）",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    settings.setHealthSyncSupabaseUrl(healthSyncUrlDraft.trim())
                                    settings.setHealthSyncSupabaseApiKey(healthSyncKeyDraft.trim())
                                    settings.setHealthSyncTableName(healthSyncTableDraft.trim().ifBlank { "device_data" })
                                },
                                enabled = healthSyncUrlDraft.trim() != healthSyncSupabaseUrl ||
                                    healthSyncKeyDraft.trim() != healthSyncSupabaseApiKey ||
                                    healthSyncTableDraft.trim() != healthSyncTableName,
                            ) { Text(stringResource(R.string.save)) }
                        }

                        HorizontalDivider()

                        // 权限状态提示（只读）
                        Text(
                            "同步所需权限状态",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PermissionChipRow("位置", if (pc.isGranted(PermissionController.Tool.LOCATION)) PermissionState.Granted else PermissionState.RuntimeNeeded)
                            PermissionChipRow("应用使用", if (pc.isGranted(PermissionController.Tool.USAGE_STATS)) PermissionState.Granted else PermissionState.SpecialNeeded({}))
                            PermissionChipRow("通知监听", if (notificationListenerGranted) PermissionState.Granted else PermissionState.SpecialNeeded({}))
                        }
                    }
                }
            }
        }
    }
}

/** Visual state of a tool's permission chip. */
private sealed class PermissionState {
    object NotRequired : PermissionState()
    object Granted : PermissionState()
    object RuntimeNeeded : PermissionState()
    data class SpecialNeeded(val onClick: () -> Unit) : PermissionState()
}

@Composable
private fun ToolToggleRow(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    permissionState: PermissionState,
) {
    SettingsItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                PermissionChip(permissionState)
            }
        },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun PermissionChip(state: PermissionState) {
    val context = LocalContext.current
    when (state) {
        PermissionState.NotRequired -> Text(
            stringResource(R.string.device_access_perm_not_required),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PermissionState.Granted -> Text(
            stringResource(R.string.device_access_perm_granted),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        PermissionState.RuntimeNeeded -> Text(
            stringResource(R.string.device_access_perm_runtime_needed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
        is PermissionState.SpecialNeeded -> TextButton(
            onClick = state.onClick,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            Text(
                stringResource(R.string.device_access_perm_open_settings),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PermissionChipRow(
    label: String,
    state: PermissionState
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        when (state) {
            PermissionState.Granted -> Text(
                stringResource(R.string.device_access_perm_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            PermissionState.NotRequired -> Text(
                stringResource(R.string.device_access_perm_not_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionState.RuntimeNeeded -> Text(
                "权限未授予",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            is PermissionState.SpecialNeeded -> Text(
                "权限未授予",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
