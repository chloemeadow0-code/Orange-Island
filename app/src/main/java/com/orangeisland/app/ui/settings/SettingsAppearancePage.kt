package com.orangeisland.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.ToolCallDisplayModes
import com.orangeisland.app.ui.theme.ColorSchemePreset
import com.orangeisland.app.ui.theme.SchemeStyle
import com.orangeisland.app.ui.theme.colorSchemeForPreset
import com.orangeisland.app.util.readFontName
import com.orangeisland.app.viewmodel.ChatViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearancePage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val themeMode by viewModel.settings.themeMode.collectAsState()
    val colorSchemeName by viewModel.settings.colorScheme.collectAsState()
    val schemeStyleName by viewModel.settings.schemeStyle.collectAsState()
    val dynamicColor by viewModel.settings.dynamicColor.collectAsState()
    val blurEffectsEnabled by viewModel.settings.blurEffectsEnabled.collectAsState()
    val codeBlockWrapEnabled by viewModel.settings.codeBlockWrapEnabled.collectAsState()
    val hapticsEnabled by viewModel.settings.hapticsEnabled.collectAsState()
    val toolCallDisplayMode by viewModel.settings.toolCallDisplayMode.collectAsState()
    val fontPreference by viewModel.settings.fontPreference.collectAsState()
    val customFontPath by viewModel.settings.customFontPath.collectAsState()
    val customFontName by viewModel.settings.customFontName.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Clear custom font when switching away from custom
    LaunchedEffect(fontPreference) {
        if (fontPreference != "custom" && customFontPath.isNotBlank()) {
            File(customFontPath).delete()
            viewModel.settings.setCustomFontPath("")
            viewModel.settings.setCustomFontName("")
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    // Delete old custom font file if it exists
                    customFontPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
                    val dest = File(context.filesDir, "custom_font_${java.util.UUID.randomUUID()}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    // Validate magic bytes (TTF: 0x00010000, OTTO, true, typ1)
                    val magic = ByteArray(4)
                    dest.inputStream().use { it.read(magic) }
                    val validFont = when {
                        magic[0] == 0.toByte() && magic[1] == 1.toByte() && magic[2] == 0.toByte() && magic[3] == 0.toByte() -> true
                        magic[0] == 'O'.code.toByte() && magic[1] == 'T'.code.toByte() && magic[2] == 'T'.code.toByte() && magic[3] == 'O'.code.toByte() -> true
                        magic[0] == 't'.code.toByte() && magic[1] == 'r'.code.toByte() && magic[2] == 'u'.code.toByte() && magic[3] == 'e'.code.toByte() -> true
                        magic[0] == 't'.code.toByte() && magic[1] == 'y'.code.toByte() && magic[2] == 'p'.code.toByte() && magic[3] == '1'.code.toByte() -> true
                        else -> false
                    }
                    if (!validFont) {
                        dest.delete()
                        withContext(Dispatchers.Main) {
                            viewModel.emitSnackbar(context.getString(R.string.font_invalid_file))
                        }
                        return@launch
                    }
                    val fontName = readFontName(dest)
                    withContext(Dispatchers.Main) {
                        viewModel.settings.setCustomFontPath(dest.absolutePath)
                        viewModel.settings.setCustomFontName(fontName)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val isDynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val currentPreset = try { ColorSchemePreset.valueOf(colorSchemeName) } catch (_: Exception) { ColorSchemePreset.MIDNIGHT }
    val currentStyle = try { SchemeStyle.valueOf(schemeStyleName) } catch (_: Exception) { SchemeStyle.TONAL_SPOT }
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemDark
    }
    CollapsingSettingsScaffold(
        title = stringResource(R.string.appearance_title),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("appearance.md") }
    ) {
            SettingsGroupColumn {
                // ── Theme Mode ──
                SettingsGroup(
                    title = stringResource(R.string.theme_mode),
                    items = listOf {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedLabel = when (themeMode) {
                            "LIGHT" -> stringResource(R.string.theme_mode_light)
                            "DARK" -> stringResource(R.string.theme_mode_dark)
                            else -> stringResource(R.string.theme_mode_follow_device)
                        }
                        val selectedIcon = when (themeMode) {
                            "LIGHT" -> Icons.Default.LightMode
                            "DARK" -> Icons.Default.DarkMode
                            else -> Icons.Default.SettingsBrightness
                        }
                        val options = listOf(
                            "LIGHT" to Pair(stringResource(R.string.theme_mode_light), Icons.Default.LightMode),
                            "DARK" to Pair(stringResource(R.string.theme_mode_dark), Icons.Default.DarkMode),
                            "FOLLOW_DEVICE" to Pair(stringResource(R.string.theme_mode_follow_device), Icons.Default.SettingsBrightness)
                        )
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.theme_mode)) },
                            supportingContent = { Text(selectedLabel) },
                            leadingContent = {
                                Icon(selectedIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            },
                            trailingContent = {
                                Box {
                                    Text(
                                        selectedLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(96.dp).padding(end = 4.dp),
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 16.dp,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        options.forEach { (mode, pair) ->
                                            val (label, icon) = pair
                                            val isSelected = when (mode) {
                                                "LIGHT" -> themeMode == "LIGHT"
                                                "DARK" -> themeMode == "DARK"
                                                else -> themeMode != "LIGHT" && themeMode != "DARK"
                                            }
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                leadingIcon = {
                                                    if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                },
                                                trailingIcon = { Icon(icon, null) },
                                                onClick = { viewModel.settings.setThemeMode(mode); expanded = false }
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable { expanded = true }
                        )
                    }
                )

                // ── Interface ──
                SettingsGroup(
                    title = stringResource(R.string.appearance_interface),
                    items = buildList {
                        if (isDynamicAvailable) {
                            add {
                                SettingsItem(
                                    headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                                    supportingContent = { Text(stringResource(R.string.dynamic_color_desc)) },
                                    trailingContent = {
                                        Switch(
                                            checked = dynamicColor,
                                            onCheckedChange = { viewModel.settings.setDynamicColor(it) }
                                        )
                                    },
                                    modifier = Modifier.clickable { viewModel.settings.setDynamicColor(!dynamicColor) }
                                )
                            }
                        }
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.blur_effects)) },
                                supportingContent = { Text(stringResource(R.string.blur_effects_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = blurEffectsEnabled,
                                        onCheckedChange = { viewModel.settings.setBlurEffectsEnabled(it) }
                                    )
                                },
                                modifier = Modifier.clickable { viewModel.settings.setBlurEffectsEnabled(!blurEffectsEnabled) }
                            )
                        }
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.code_block_wrap)) },
                                supportingContent = { Text(stringResource(R.string.code_block_wrap_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = codeBlockWrapEnabled,
                                        onCheckedChange = { viewModel.settings.setCodeBlockWrapEnabled(it) }
                                    )
                                },
                                modifier = Modifier.clickable { viewModel.settings.setCodeBlockWrapEnabled(!codeBlockWrapEnabled) }
                            )
                        }
                        add {
                            var expanded by remember { mutableStateOf(false) }
                            val normalizedToolCallDisplayMode = ToolCallDisplayModes.normalize(toolCallDisplayMode)
                            val selectedLabel = when (normalizedToolCallDisplayMode) {
                                ToolCallDisplayModes.GROUPED_TIMELINE -> stringResource(R.string.tool_call_display_mode_grouped_timeline)
                                ToolCallDisplayModes.COMPACT -> stringResource(R.string.tool_call_display_mode_compact)
                                else -> stringResource(R.string.tool_call_display_mode_timeline)
                            }
                            val selectedDescription = when (normalizedToolCallDisplayMode) {
                                ToolCallDisplayModes.GROUPED_TIMELINE -> stringResource(R.string.tool_call_display_mode_grouped_timeline_desc)
                                ToolCallDisplayModes.COMPACT -> stringResource(R.string.tool_call_display_mode_compact_desc)
                                else -> stringResource(R.string.tool_call_display_mode_timeline_desc)
                            }
                            val options = listOf(
                                ToolCallDisplayModes.TIMELINE to stringResource(R.string.tool_call_display_mode_timeline),
                                ToolCallDisplayModes.GROUPED_TIMELINE to stringResource(R.string.tool_call_display_mode_grouped_timeline),
                                ToolCallDisplayModes.COMPACT to stringResource(R.string.tool_call_display_mode_compact)
                            )
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.tool_call_display_mode)) },
                                supportingContent = { Text(selectedDescription) },
                                trailingContent = {
                                    Box {
                                        Text(
                                            selectedLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(72.dp).padding(end = 4.dp),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        DropdownMenu(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            tonalElevation = 16.dp,
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            options.forEach { (mode, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    leadingIcon = {
                                                        if (normalizedToolCallDisplayMode == mode) {
                                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.settings.setToolCallDisplayMode(mode)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { expanded = true }
                            )
                        }
                        add {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.haptic_feedback)) },
                                supportingContent = { Text(stringResource(R.string.haptic_feedback_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = hapticsEnabled,
                                        onCheckedChange = { viewModel.settings.setHapticsEnabled(it) }
                                    )
                                },
                                modifier = Modifier.clickable { viewModel.settings.setHapticsEnabled(!hapticsEnabled) }
                            )
                        }
                    }
                )

                val schemeAlpha = if (dynamicColor && isDynamicAvailable) 0.38f else 1f
                SettingsGroup(
                    title = stringResource(R.string.color_scheme),
                    items = listOf(
                        {
                            var expanded by remember { mutableStateOf(false) }
                            val currentLabel = presetDisplayName(currentPreset)
                            val currentPrimary = remember(currentPreset, currentStyle, isDark) {
                                colorSchemeForPreset(currentPreset, currentStyle, isDark).primary
                            }
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.color_scheme)) },
                                supportingContent = { Text(currentLabel) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(currentPrimary)
                                    )
                                },
                                trailingContent = {
                                    Box {
                                        Text(
                                            currentLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(96.dp).padding(end = 4.dp),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            tonalElevation = 16.dp,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            ColorSchemePreset.entries.forEach { preset ->
                                                val presetPrimary = remember(preset, currentStyle, isDark) {
                                                    colorSchemeForPreset(preset, currentStyle, isDark).primary
                                                }
                                                DropdownMenuItem(
                                                    text = { Text(presetDisplayName(preset)) },
                                                    leadingIcon = {
                                                        if (preset == currentPreset) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                    },
                                                    trailingIcon = {
                                                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(presetPrimary))
                                                    },
                                                    onClick = { viewModel.settings.setColorScheme(preset.name); expanded = false }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.alpha(schemeAlpha).clickable(enabled = schemeAlpha > 0.5f) { expanded = true }
                            )
                        },
                        {
                            var expanded by remember { mutableStateOf(false) }
                            val currentLabel = styleDisplayName(currentStyle)
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.scheme_style)) },
                                supportingContent = { Text(currentLabel) },
                                trailingContent = {
                                    Box {
                                        Text(
                                            currentLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(96.dp).padding(end = 4.dp),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            tonalElevation = 16.dp,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            SchemeStyle.entries.forEach { style ->
                                                DropdownMenuItem(
                                                    text = { Text(styleDisplayName(style)) },
                                                    leadingIcon = {
                                                        if (style == currentStyle) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                    },
                                                    onClick = { viewModel.settings.setSchemeStyle(style.name); expanded = false }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.alpha(schemeAlpha).clickable(enabled = schemeAlpha > 0.5f) { expanded = true }
                            )
                        }
                    )
                )

                // ── Font ──
                SettingsGroup(
                    title = stringResource(R.string.font_title),
                    items = buildList {
                        add {
                            var expanded by remember { mutableStateOf(false) }
                            val selectedLabel = when (fontPreference) {
                                "system" -> stringResource(R.string.font_system_default)
                                "custom" -> stringResource(R.string.font_custom)
                                else -> stringResource(R.string.font_app_default)
                            }
                            val options = listOf(
                                "app_default" to stringResource(R.string.font_app_default),
                                "system" to stringResource(R.string.font_system_default),
                                "custom" to stringResource(R.string.font_custom)
                            )
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.font_title)) },
                                supportingContent = { Text(selectedLabel) },
                                trailingContent = {
                                    Box {
                                        Text(
                                            selectedLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(96.dp).padding(end = 4.dp),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            tonalElevation = 16.dp,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            options.forEach { (value, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    leadingIcon = {
                                                        if (fontPreference == value) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                    },
                                                    onClick = { viewModel.settings.setFontPreference(value); expanded = false }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { expanded = true }
                            )
                        }
                        if (fontPreference == "custom") {
                            add {
                                val hasFont = customFontName.isNotBlank() && customFontPath.isNotBlank() && File(customFontPath).exists()
                                SettingsItem(
                                    headlineContent = {
                                        Text(
                                            text = if (hasFont) customFontName else stringResource(R.string.font_no_file_selected),
                                            fontWeight = if (hasFont) FontWeight.Medium else FontWeight.Normal,
                                            color = if (hasFont) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = stringResource(R.string.font_custom_pick),
                                            color = if (hasFont) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (hasFont) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable { fontPickerLauncher.launch(arrayOf("font/*", "*/*")) }
                                )
                            }
                        }
                    }
                )
            }
            if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun presetDisplayName(preset: ColorSchemePreset): String = when (preset) {
    ColorSchemePreset.MIDNIGHT -> stringResource(R.string.color_scheme_midnight)
    ColorSchemePreset.NORDIC -> stringResource(R.string.color_scheme_nordic)
    ColorSchemePreset.FOREST -> stringResource(R.string.color_scheme_forest)
    ColorSchemePreset.SUNSET -> stringResource(R.string.color_scheme_sunset)
    ColorSchemePreset.ROSE -> stringResource(R.string.color_scheme_rose)
    ColorSchemePreset.LAVENDER -> stringResource(R.string.color_scheme_lavender)
    ColorSchemePreset.SLATE -> stringResource(R.string.color_scheme_slate)
    ColorSchemePreset.OCEAN -> stringResource(R.string.color_scheme_ocean)
}

@Composable
private fun styleDisplayName(style: SchemeStyle): String = when (style) {
    SchemeStyle.TONAL_SPOT -> stringResource(R.string.scheme_style_tonal_spot)
    SchemeStyle.EXPRESSIVE -> stringResource(R.string.scheme_style_expressive)
    SchemeStyle.VIBRANT -> stringResource(R.string.scheme_style_vibrant)
    SchemeStyle.NEUTRAL -> stringResource(R.string.scheme_style_neutral)
}
