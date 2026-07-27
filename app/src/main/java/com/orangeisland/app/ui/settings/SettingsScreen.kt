package com.orangeisland.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.orangeisland.app.R
import com.orangeisland.app.plugin.PluginMemoryProvider
import com.orangeisland.app.ui.common.DecorativeImage
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.ui.settings.datacontrol.SettingsDataControlPage
import com.orangeisland.app.viewmodel.ChatViewModel

/** When true, [SettingsGroup] inside a [SettingsGroupColumn] suppresses its own bottom padding
 *  (spacing is handled by the column's [Arrangement.spacedBy] instead). */
val LocalSettingsGroupSpacing = staticCompositionLocalOf { false }

/** Settings page content container: uniform 24dp spacing between groups (and any other elements),
 *  with zero trailing after the last element. */
@Composable
fun SettingsGroupColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalSettingsGroupSpacing provides true) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    titleStyle: TextStyle = MaterialTheme.typography.labelLarge,
    items: List<@Composable () -> Unit>
) {
    val effectiveBottom = if (LocalSettingsGroupSpacing.current) 0.dp else bottomPadding
    Column(modifier = modifier.fillMaxWidth().padding(bottom = effectiveBottom)) {
        Text(
            text = title,
            style = titleStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                val isFirst = index == 0
                val isLast = index == items.lastIndex
                val shape = when {
                    items.size == 1 -> RoundedCornerShape(24.dp)
                    isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                    isLast -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(5.dp)
                }
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item()
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    leadingSpacing: Dp = 16.dp,
) {
    val verticalPadding = if (supportingContent == null) 12.dp else 16.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                leadingContent()
            }
            Spacer(modifier = Modifier.width(leadingSpacing))
        }
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                headlineContent()
            }
            if (supportingContent != null) {
                Spacer(modifier = Modifier.height(3.dp))
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    supportingContent()
                }
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingContent()
        }
    }
}

private data class SettingsCategory(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int
)

private data class SettingsGroupData(
    val titleRes: Int? = null,
    val items: List<SettingsCategory>
)

private val settingsGroups = listOf(
    SettingsGroupData(titleRes = R.string.settings_group_services, items = listOf(
        SettingsCategory("provider", R.string.settings_provider, R.string.settings_provider_desc, IslandIcons.Provider.res),
        SettingsCategory("models", R.string.settings_models, R.string.settings_models_desc, IslandIcons.Model.res),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_responses, items = listOf(
        SettingsCategory("prompts", R.string.settings_prompts, R.string.settings_prompts_desc, IslandIcons.Prompts.res),
        SettingsCategory("generation", R.string.settings_generation, R.string.settings_generation_desc, IslandIcons.Generation.res),
        SettingsCategory("titlegen", R.string.settings_title_gen, R.string.settings_title_gen_desc, IslandIcons.TitleGeneration.res),
        SettingsCategory("autocompress", R.string.settings_auto_compress, R.string.settings_auto_compress_desc, IslandIcons.Generation.res),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_tools, items = listOf(
        SettingsCategory("websearch", R.string.settings_web_search, R.string.settings_web_search_desc, IslandIcons.WebSearch.res),
        SettingsCategory("shell", R.string.shell_title, R.string.shell_desc, IslandIcons.Terminal.res),
        SettingsCategory("mcp", R.string.mcp_title, R.string.mcp_desc, IslandIcons.McpServer.res),
        SettingsCategory("plugins", R.string.plugin_title, R.string.plugin_desc, IslandIcons.Plugin.res),
        SettingsCategory("device_access", R.string.device_access_title, R.string.device_access_desc, IslandIcons.DeviceAccess.res),
        SettingsCategory("workflows", R.string.settings_workflows, R.string.settings_workflows_desc, IslandIcons.Workflow.res),
        SettingsCategory("tts", R.string.settings_tts, R.string.settings_tts_desc, IslandIcons.VoiceSynthesis.res),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_network, items = listOf(
        SettingsCategory("proxy", R.string.settings_proxy, R.string.settings_proxy_desc, IslandIcons.Proxy.res),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_memory_data, items = listOf(
        SettingsCategory("memory", R.string.settings_memory, R.string.settings_memory_desc, IslandIcons.Memory.res),
        SettingsCategory("datacontrol", R.string.settings_data_control, R.string.settings_data_control_desc, IslandIcons.DataControl.res),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_appearance_language, items = listOf(
        SettingsCategory("appearance", R.string.settings_appearance, R.string.settings_appearance_desc, IslandIcons.Appearance.res),
        SettingsCategory("customcolors", R.string.settings_custom_colors, R.string.settings_custom_colors_desc, IslandIcons.CustomColors.res),
        SettingsCategory("illustrations", R.string.illustrations_title, R.string.illustration_chat_background, IslandIcons.Illustrations.res),
        SettingsCategory("language", R.string.language_title, R.string.language_desc, IslandIcons.Language.res),
    )),
    SettingsGroupData(titleRes = R.string.settings_group_about, items = listOf(
        SettingsCategory("about", R.string.settings_about, R.string.settings_about_desc, IslandIcons.About.res),
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    workflowViewModel: com.orangeisland.app.viewmodel.WorkflowViewModel,
    initialCategory: String? = null,
    onEditWorkflowInChat: (prefilledPrompt: String) -> Unit = {},
    onOpenHealthPage: () -> Unit = {},
    memoryProvider: PluginMemoryProvider? = null,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(initialCategory) }

    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            selectedCategory = initialCategory
        }
    }
    val listState = rememberLazyListState()
    val isSyncingModels by viewModel.isSyncingModels.collectAsState()
    val fetchingModelsMessage = stringResource(R.string.snackbar_fetching_models)

    LaunchedEffect(isSyncingModels) {
        if (isSyncingModels) {
            viewModel.emitSnackbar(fetchingModelsMessage)
        }
    }

    BackHandler {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GuardedAnimatedContent(
            targetState = selectedCategory,
            forward = selectedCategory != null
        ) { category ->
            when (category) {
                "provider" -> SettingsProviderPage(viewModel, onBack = { selectedCategory = null })
                "prompts" -> SettingsPromptsPage(viewModel, onBack = { selectedCategory = null })
                "models" -> SettingsModelsPage(
                    viewModel,
                    onBack = { selectedCategory = null }
                )
                "generation" -> SettingsGenerationPage(viewModel, onBack = { selectedCategory = null })
                "websearch" -> SettingsWebSearchPage(viewModel, onBack = { selectedCategory = null })
                "shell" -> SettingsShellPage(viewModel, onBack = { selectedCategory = null })
                "mcp" -> SettingsMcpPage(viewModel, onBack = { selectedCategory = null })
                "plugins" -> SettingsPluginPage(
                    viewModel,
                    onBack = { selectedCategory = null }
                )
                "device_access" -> SettingsDeviceAccessPage(
                    viewModel = viewModel,
                    onBack = { selectedCategory = null },
                    onOpenHealthPage = onOpenHealthPage
                )
                "proxy" -> SettingsProxyPage(viewModel, onBack = { selectedCategory = null })
                "language" -> SettingsLanguagePage(viewModel, onBack = { selectedCategory = null })
                "titlegen" -> SettingsTitleGenPage(viewModel, onBack = { selectedCategory = null })
                "autocompress" -> SettingsAutoCompressPage(viewModel, onBack = { selectedCategory = null })
                "memory" -> SettingsMemoryPage(viewModel, onBack = { selectedCategory = null })
                "datacontrol" -> SettingsDataControlPage(viewModel, onBack = { selectedCategory = null })
                "appearance" -> SettingsAppearancePage(viewModel, onBack = { selectedCategory = null })
                "customcolors" -> SettingsCustomColorsPage(viewModel, onBack = { selectedCategory = null })
                "illustrations" -> SettingsIllustrationsPage(viewModel, onBack = { selectedCategory = null })
                "logs" -> SettingsLogsPage(onBack = { selectedCategory = null })
                "about" -> SettingsAboutPage(viewModel, onBack = { selectedCategory = null })
                "workflows" -> com.orangeisland.app.ui.settings.workflow.SettingsWorkflowPage(
                    viewModel = workflowViewModel,
                    onBack = { selectedCategory = null },
                    onEditInChat = onEditWorkflowInChat
                )
                "tts" -> SettingsTtsPage(viewModel, onBack = { selectedCategory = null })
                else -> {
                    CollapsingSettingsLazyScaffold(
                        title = stringResource(R.string.settings_title),
                        onBack = onBack,
                        listState = listState
                    ) {
                        items(settingsGroups.size) { groupIndex ->
                            val group = settingsGroups[groupIndex]
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (group.titleRes != null) {
                                    Text(
                                        text = stringResource(group.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                group.items.forEachIndexed { index, cat ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    val isFirst = index == 0
                                    val isLast = index == group.items.lastIndex
                                    val shape = when {
                                        group.items.size == 1 -> RoundedCornerShape(24.dp)
                                        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                        isLast -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                        else -> RoundedCornerShape(5.dp)
                                    }
                                    Surface(
                                        shape = shape,
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 1.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .clickable { selectedCategory = cat.key }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IslandIcon(
                                                res = cat.iconRes,
                                                size = 42.dp,
                                                modifier = Modifier,
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(cat.titleRes),
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = stringResource(cat.descriptionRes),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                            if (groupIndex < settingsGroups.size - 1) {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // Orange Island footer decoration at the end of the settings list
                        // (UI Playground v0.4). Non-interactive background art, sunk toward
                        // the bottom-end corner of the page.
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, end = 0.dp, bottom = 24.dp),
                                contentAlignment = Alignment.BottomEnd,
                            ) {
                                DecorativeImage(
                                    res = R.drawable.island_deco_island,
                                    width = 240.dp,
                                    alpha = 0.9f,
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}
