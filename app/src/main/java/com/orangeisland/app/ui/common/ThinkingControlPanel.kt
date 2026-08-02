package com.orangeisland.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.model.ThinkingLevels
import kotlin.math.abs
import kotlin.math.roundToInt

private val BudgetToggleToSliderSpacing = 32.dp
private val AdvancedChevronSize = 18.dp

@Composable
fun ThinkingControlPanel(
    enabled: Boolean,
    level: String,
    budgetEnabled: Boolean,
    budgetTokens: Int,
    onEnabledChange: (Boolean) -> Unit,
    onLevelChange: (String) -> Unit,
    onBudgetEnabledChange: (Boolean) -> Unit,
    onBudgetTokensChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    providerName: String? = null,
    animateSections: Boolean = false
) {
    val normalizedEffort = ThinkingLevels.normalize(level)
    val providerRange = ThinkingLevels.effortRangeForProvider(providerName)
    val maxIndex = providerRange.last
    val clampedIndex = ThinkingLevels.indexForEffort(normalizedEffort).coerceIn(providerRange)
    var sliderPosition by remember(clampedIndex) { mutableFloatStateOf(clampedIndex.toFloat()) }
    var showAdvanced by rememberSaveable { mutableStateOf(budgetEnabled) }
    val sliderEnabled = enabled && !budgetEnabled

    LaunchedEffect(budgetEnabled) {
        if (budgetEnabled) showAdvanced = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IslandIcon(IslandIcons.Thinking, size = 38.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gen_thinking_enabled),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = thinkingControlShortLabel(enabled, normalizedEffort, budgetEnabled, budgetTokens),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().alpha(if (sliderEnabled) 1f else 0.38f),
            verticalAlignment = Alignment.Top
        ) {
            IslandIcon(IslandIcons.Thinking, size = 38.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.thinking_effort_label),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = effortLabel(ThinkingLevels.effortForIndex(sliderPosition.roundToInt())),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.thinking_effort_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Slider(
                    value = sliderPosition,
                    onValueChange = { if (sliderEnabled) sliderPosition = it },
                    onValueChangeFinished = {
                        if (sliderEnabled) {
                            val idx = sliderPosition.roundToInt().coerceIn(providerRange)
                            sliderPosition = idx.toFloat()
                            onEnabledChange(true)
                            onLevelChange(ThinkingLevels.effortForIndex(idx))
                        }
                    },
                    valueRange = 0f..maxIndex.toFloat(),
                    steps = if (maxIndex > 0) maxIndex - 1 else 0,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = sliderEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val chevronRotation by animateFloatAsState(
            targetValue = if (showAdvanced) 180f else 0f,
            animationSpec = if (animateSections) tween(400) else tween(0),
            label = "advancedChevronRotation"
        )
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (showAdvanced) stringResource(R.string.thinking_advanced_hide) else stringResource(R.string.advanced_settings))
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(AdvancedChevronSize)
                        .rotate(chevronRotation)
                )
            }
        }

        MaybeAnimatedVisibility(visible = showAdvanced, animate = animateSections) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.38f)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IslandIcon(IslandIcons.Thinking, size = 38.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.thinking_use_budget),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.thinking_budget_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = budgetEnabled,
                        onCheckedChange = if (enabled) { { checked ->
                            if (checked) {
                                onEnabledChange(true)
                                if (budgetTokens < 1) {
                                    onBudgetTokensChange(ThinkingLevels.DefaultBudgetTokens)
                                }
                            }
                            onBudgetEnabledChange(checked)
                        } } else { { } },
                        enabled = enabled
                    )
                }

                MaybeAnimatedVisibility(visible = budgetEnabled, animate = animateSections) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(BudgetToggleToSliderSpacing))
                        val budgetPresets = ThinkingLevels.budgetPresets
                        val currentBudget = budgetTokens.coerceAtLeast(1)
                        val budgetIndex = budgetPresets.indices.minByOrNull {
                            abs(budgetPresets[it] - currentBudget)
                        } ?: 1
                        var budgetSliderPos by remember(currentBudget) { mutableFloatStateOf(budgetIndex.toFloat()) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            IslandIcon(IslandIcons.Thinking, size = 38.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.thinking_budget_input_label),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = stringResource(R.string.thinking_budget_tokens, budgetPresets[budgetSliderPos.roundToInt()]),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = budgetSliderPos,
                                    onValueChange = { if (enabled) budgetSliderPos = it },
                                    onValueChangeFinished = {
                                        if (enabled) {
                                            val idx = budgetSliderPos.roundToInt().coerceIn(0, budgetPresets.lastIndex)
                                            budgetSliderPos = idx.toFloat()
                                            onEnabledChange(true)
                                            onBudgetTokensChange(budgetPresets[idx])
                                        }
                                    },
                                    valueRange = 0f..(budgetPresets.size - 1).toFloat(),
                                    steps = budgetPresets.size - 2,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    enabled = enabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaybeAnimatedVisibility(
    visible: Boolean,
    animate: Boolean,
    content: @Composable () -> Unit
) {
    if (animate) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)) + expandVertically(
                animationSpec = tween(400),
                expandFrom = Alignment.Top
            ),
            exit = fadeOut(animationSpec = tween(400)) + shrinkVertically(
                animationSpec = tween(400),
                shrinkTowards = Alignment.Top
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    } else if (visible) {
        content()
    }
}

@Composable
fun thinkingControlShortLabel(
    enabled: Boolean,
    level: String,
    budgetEnabled: Boolean = false,
    budgetTokens: Int = ThinkingLevels.DefaultBudgetTokens
): String {
    if (!enabled) return stringResource(R.string.thinking_control_off)
    val effortText = effortLabel(ThinkingLevels.normalize(level))
    if (!budgetEnabled) return effortText
    return stringResource(R.string.thinking_budget_tokens, budgetTokens.coerceAtLeast(1))
}

@Composable
private fun effortLabel(effort: String): String = when (effort) {
    "minimal" -> stringResource(R.string.gen_thinking_level_minimal)
    "low" -> stringResource(R.string.gen_thinking_level_low)
    "medium" -> stringResource(R.string.gen_thinking_level_medium)
    "high" -> stringResource(R.string.gen_thinking_level_high)
    "xhigh" -> stringResource(R.string.gen_thinking_level_xhigh)
    "max" -> stringResource(R.string.gen_thinking_level_max)
    else -> effort
}
