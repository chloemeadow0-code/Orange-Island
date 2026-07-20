package com.orangeisland.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timeline
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
fun SettingsDeviceAccessPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings
    val deviceInfoEnabled by settings.deviceInfoEnabled.collectAsState()
    val locationEnabled by settings.locationEnabled.collectAsState()
    val calendarEnabled by settings.calendarEnabled.collectAsState()
    val notificationEnabled by settings.notificationEnabled.collectAsState()
    val usageStatsEnabled by settings.usageStatsEnabled.collectAsState()
    val amapApiKey by settings.amapApiKey.collectAsState()
    val pc = viewModel.permissionController
    var amapKeyDraft by remember(amapApiKey) { mutableStateOf(amapApiKey) }

    // Re-query special-permission state whenever this page comes back to the foreground
    // (the user may have just toggled the listener / usage-access in system Settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) pc.refreshNotificationListenerState()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notificationListenerGranted by pc.notificationListenerEnabledFlow.collectAsState()

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
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.device_access_group_tools), items = buildList {
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
            })

            // Amap key — only relevant when location is enabled.
            if (locationEnabled) {
                SettingsGroup(title = stringResource(R.string.device_access_amap_key_title), items = listOf {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Text(
                            stringResource(R.string.device_access_amap_key_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amapKeyDraft,
                            onValueChange = { amapKeyDraft = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.device_access_amap_key_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { settings.setAmapApiKey(amapKeyDraft.trim()) },
                            enabled = amapKeyDraft.trim() != amapApiKey,
                        ) { Text(stringResource(R.string.save)) }
                    }
                })
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
