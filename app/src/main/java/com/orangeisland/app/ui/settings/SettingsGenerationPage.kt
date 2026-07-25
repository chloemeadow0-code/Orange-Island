package com.orangeisland.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.common.ThinkingControlPanel
import com.orangeisland.app.ui.common.thinkingControlShortLabel
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGenerationPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val maxContextWindow by viewModel.settings.maxContextWindow.collectAsState()
    val visualizeContextRollout by viewModel.settings.visualizeContextRollout.collectAsState()
    val showUsageStats by viewModel.settings.showMessageUsageStats.collectAsState()
    val defaultTemperature by viewModel.settings.defaultTemperature.collectAsState()
    val defaultMaxTokens by viewModel.settings.defaultMaxTokens.collectAsState()
    val defaultTopP by viewModel.settings.defaultTopP.collectAsState()
    val defaultFrequencyPenalty by viewModel.settings.defaultFrequencyPenalty.collectAsState()
    val defaultPresencePenalty by viewModel.settings.defaultPresencePenalty.collectAsState()
    val thinkingEnabled by viewModel.settings.thinkingEnabled.collectAsState()
    val thinkingLevel by viewModel.settings.thinkingLevel.collectAsState()
    val thinkingBudgetEnabled by viewModel.settings.thinkingBudgetEnabled.collectAsState()
    val thinkingBudgetTokens by viewModel.settings.thinkingBudgetTokens.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    CollapsingSettingsScaffold(
        title = stringResource(R.string.generation_title),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("generation.md") }
    ) {
            SettingsGroupColumn {
                // ── Section 1: Default Context Window ──
                SettingsGroup(
                    title = stringResource(R.string.context_window_default),
                    items = listOf(
                        {
                            val persistedContextWindow = maxContextWindow.toFloat()
                            var contextWindowDraft by remember { mutableFloatStateOf(persistedContextWindow) }
                            LaunchedEffect(persistedContextWindow) {
                                contextWindowDraft = persistedContextWindow
                            }
                            val contextWindowValue = contextWindowDraft.toInt().coerceIn(5, 100)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.context_window),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.context_retain, contextWindowValue),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        Slider(
                                            value = contextWindowDraft,
                                            onValueChange = { contextWindowDraft = it },
                                            onValueChangeFinished = {
                                                val committed = contextWindowDraft.toInt().coerceIn(5, 100)
                                                contextWindowDraft = committed.toFloat()
                                                if (committed != maxContextWindow) {
                                                    viewModel.settings.setMaxContextWindow(committed)
                                                }
                                            },
                                            valueRange = 5f..100f,
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        },
                        {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.context_visualize)) },
                                supportingContent = { Text(stringResource(R.string.context_visualize_desc)) },
                                leadingContent = {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    Switch(checked = visualizeContextRollout, onCheckedChange = { viewModel.settings.setVisualizeContextRollout(it) })
                                },
                                modifier = Modifier.clickable { viewModel.settings.setVisualizeContextRollout(!visualizeContextRollout) }
                            )
                        },
                        {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.show_usage_stats)) },
                                supportingContent = { Text(stringResource(R.string.show_usage_stats_desc)) },
                                leadingContent = {
                                    Icon(Icons.Default.DataUsage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    Switch(checked = showUsageStats, onCheckedChange = { viewModel.settings.setShowMessageUsageStats(it) })
                                },
                                modifier = Modifier.clickable { viewModel.settings.setShowMessageUsageStats(!showUsageStats) }
                            )
                        }
                    )
                )

                // ── Section 2: Default Thinking ──
                SettingsGroup(
                    title = stringResource(R.string.default_thinking),
                    items = listOf(
                        {
                            SettingsItem(
                                headlineContent = { Text(stringResource(R.string.gen_thinking_enabled)) },
                                supportingContent = {
                                    Text(thinkingControlShortLabel(thinkingEnabled, thinkingLevel, thinkingBudgetEnabled, thinkingBudgetTokens))
                                },
                                leadingContent = {
                                    Icon(painterResource(id = R.drawable.neurology_24), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    Switch(checked = thinkingEnabled, onCheckedChange = { viewModel.settings.setThinkingEnabled(it) })
                                },
                                modifier = Modifier.clickable { viewModel.settings.setThinkingEnabled(!thinkingEnabled) }
                            )
                        },
                        {
                            ThinkingControlPanel(
                                enabled = thinkingEnabled,
                                level = thinkingLevel,
                                budgetEnabled = thinkingBudgetEnabled,
                                budgetTokens = thinkingBudgetTokens,
                                onEnabledChange = { viewModel.settings.setThinkingEnabled(it) },
                                onLevelChange = { viewModel.settings.setThinkingLevel(it) },
                                onBudgetEnabledChange = { viewModel.settings.setThinkingBudgetEnabled(it) },
                                onBudgetTokensChange = { viewModel.settings.setThinkingBudgetTokens(it) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                showHeader = false,
                                providerName = null,
                                animateSections = true
                            )
                        }
                    )
                )

                // ── Section 3: Generation Parameters ──
                SettingsGroup(
                    title = stringResource(R.string.generation_params),
                    items = listOf(
                        {
                            GenParamSlider(
                                label = stringResource(R.string.gen_temperature),
                                desc = stringResource(R.string.gen_temperature_desc),
                                value = defaultTemperature,
                                valueRange = 0f..2f,
                                format = { v -> String.format(Locale.US, "%.2f", v) },
                                onValueChange = { viewModel.settings.setDefaultTemperature(it) },
                                onReset = { viewModel.settings.setDefaultTemperature(null) }
                            )
                        },
                        {
                            val maxTokensPresets = intArrayOf(256, 512, 1024, 2048, 4096, 8192, 16384, 32768)
                            GenParamSlider(
                                label = stringResource(R.string.gen_max_tokens),
                                desc = stringResource(R.string.gen_max_tokens_desc),
                                value = defaultMaxTokens,
                                presets = maxTokensPresets,
                                format = { it.toString() },
                                onValueChange = { viewModel.settings.setDefaultMaxTokens(it) },
                                onReset = { viewModel.settings.setDefaultMaxTokens(null) }
                            )
                        },
                        {
                            GenParamSlider(
                                label = stringResource(R.string.gen_top_p),
                                desc = stringResource(R.string.gen_top_p_desc),
                                value = defaultTopP,
                                valueRange = 0f..1f,
                                format = { v -> String.format(Locale.US, "%.2f", v) },
                                onValueChange = { viewModel.settings.setDefaultTopP(it) },
                                onReset = { viewModel.settings.setDefaultTopP(null) }
                            )
                        },
                        {
                            GenParamSlider(
                                label = stringResource(R.string.gen_frequency_penalty),
                                desc = stringResource(R.string.gen_frequency_penalty_desc),
                                value = defaultFrequencyPenalty,
                                valueRange = -2f..2f,
                                format = { v -> String.format(Locale.US, "%.2f", v) },
                                onValueChange = { viewModel.settings.setDefaultFrequencyPenalty(it) },
                                onReset = { viewModel.settings.setDefaultFrequencyPenalty(null) }
                            )
                        },
                        {
                            GenParamSlider(
                                label = stringResource(R.string.gen_presence_penalty),
                                desc = stringResource(R.string.gen_presence_penalty_desc),
                                value = defaultPresencePenalty,
                                valueRange = -2f..2f,
                                format = { v -> String.format(Locale.US, "%.2f", v) },
                                onValueChange = { viewModel.settings.setDefaultPresencePenalty(it) },
                                onReset = { viewModel.settings.setDefaultPresencePenalty(null) }
                            )
                        }
                    )
                )
            }

            if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * Generation parameter slider row.
 * Always shows the slider value. When at default, value is grey and "Default" text is shown beside it.
 * When set, value is primary-colored with a "Reset" link below the slider.
 */
@Composable
private fun GenParamSlider(
    label: String,
    desc: String,
    value: Float?,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val defaultSliderPos = (valueRange.start + valueRange.endInclusive) / 2f
    val persistedSliderPos = value ?: defaultSliderPos
    var sliderPos by remember { mutableFloatStateOf(persistedSliderPos) }
    LaunchedEffect(persistedSliderPos) {
        sliderPos = persistedSliderPos
    }
    // Reset is reflected synchronously; only the DataStore write is async. justReset
    // flips the label to "not specified" immediately and is cleared once the async
    // [value] catches up (becomes null on reset, or a new value if the user re-sets).
    var justReset by remember { mutableStateOf(false) }
    LaunchedEffect(value) { justReset = false }
    val draftChangedFromDefault = kotlin.math.abs(sliderPos - defaultSliderPos) > 0.0001f
    val hasExplicitOrDraftValue = (value != null && !justReset) || draftChangedFromDefault
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!hasExplicitOrDraftValue) {
                        Text(
                            text = stringResource(R.string.gen_not_specified),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    } else {
                        Text(
                            text = format(sliderPos),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.gen_reset),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.clickable {
                                sliderPos = defaultSliderPos
                                justReset = true
                                onReset()
                            }
                        )
                    }
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Slider(
                    value = sliderPos,
                    onValueChange = { sliderPos = it },
                    onValueChangeFinished = {
                        val committed = sliderPos.coerceIn(valueRange.start, valueRange.endInclusive)
                        val shouldCommit = value != null || kotlin.math.abs(committed - defaultSliderPos) > 0.0001f
                        sliderPos = committed
                        if (shouldCommit) {
                            if (value == null || kotlin.math.abs(value - committed) > 0.0001f) {
                                onValueChange(committed)
                            }
                        }
                    },
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

/** Int slider variant with discrete preset values (used for max tokens). */
@Composable
private fun GenParamSlider(
    label: String,
    desc: String,
    value: Int?,
    presets: IntArray,
    format: (Int) -> String,
    onValueChange: (Int) -> Unit,
    onReset: () -> Unit
) {
    fun toIndex(v: Int) = presets.indices.minByOrNull { kotlin.math.abs(presets[it] - v) } ?: 3
    val defaultIndex = 3.coerceIn(0, presets.lastIndex)
    val persistedIndex = if (value != null) toIndex(value) else defaultIndex
    var sliderPos by remember { mutableFloatStateOf(persistedIndex.toFloat()) }
    LaunchedEffect(persistedIndex) {
        sliderPos = persistedIndex.toFloat()
    }
    // Reset is reflected synchronously; only the DataStore write is async (see float variant).
    var justReset by remember { mutableStateOf(false) }
    LaunchedEffect(value) { justReset = false }
    val draftIndex = sliderPos.roundToInt().coerceIn(0, presets.lastIndex)
    val hasExplicitOrDraftValue = (value != null && !justReset) || draftIndex != defaultIndex
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!hasExplicitOrDraftValue) {
                        Text(
                            text = stringResource(R.string.gen_not_specified),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    } else {
                        Text(
                            text = format(presets[draftIndex]),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.gen_reset),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.clickable {
                                sliderPos = defaultIndex.toFloat()
                                justReset = true
                                onReset()
                            }
                        )
                    }
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Slider(
                    value = sliderPos,
                    onValueChange = { sliderPos = it },
                    onValueChangeFinished = {
                        val committedIndex = sliderPos.roundToInt().coerceIn(0, presets.lastIndex)
                        val committedValue = presets[committedIndex]
                        val shouldCommit = value != null || committedIndex != defaultIndex
                        sliderPos = committedIndex.toFloat()
                        if (shouldCommit) {
                            if (value != committedValue) {
                                onValueChange(committedValue)
                            }
                        }
                    },
                    valueRange = 0f..(presets.size - 1).toFloat(),
                    steps = presets.size - 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}
