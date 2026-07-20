package com.orangeisland.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.components.ColorMath
import com.orangeisland.app.ui.components.ColorSwatchPickerDialog
import com.orangeisland.app.viewmodel.ChatViewModel

private data class ColorSlot(
    val labelRes: Int,
    val value: Long?,
    val fallback: Color,
    val onPick: (Long?) -> Unit,
)

@Composable
fun SettingsCustomColorsPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val s = viewModel.settings
    val chatText by s.customColorChatText.collectAsState()
    val globalText by s.customColorGlobalText.collectAsState()
    val userBubble by s.customColorUserBubble.collectAsState()
    val assistantBubble by s.customColorAssistantBubble.collectAsState()
    val reasoningPanel by s.customColorReasoningPanel.collectAsState()
    val chatBackground by s.customColorChatBackground.collectAsState()
    val accent by s.customColorAccent.collectAsState()
    val inputField by s.customColorInputField.collectAsState()
    val recentColors by s.recentCustomColors.collectAsState()

    val bubbleAlpha by s.transparencyMessageBubble.collectAsState()
    val reasoningAlpha by s.transparencyReasoningPanel.collectAsState()
    val drawerAlpha by s.transparencyDrawerItem.collectAsState()
    val topBarAlpha by s.transparencyTopBar.collectAsState()

    var activeSlot by remember { mutableStateOf<ColorSlot?>(null) }

    val slots = listOf(
        ColorSlot(R.string.custom_color_chat_text, chatText, MaterialTheme.colorScheme.onSurface) { s.setCustomColorChatText(it) },
        ColorSlot(R.string.custom_color_global_text, globalText, MaterialTheme.colorScheme.onBackground) { s.setCustomColorGlobalText(it) },
        ColorSlot(R.string.custom_color_user_bubble, userBubble, MaterialTheme.colorScheme.primaryContainer) { s.setCustomColorUserBubble(it) },
        ColorSlot(R.string.custom_color_assistant_bubble, assistantBubble, MaterialTheme.colorScheme.surfaceContainerHigh) { s.setCustomColorAssistantBubble(it) },
        ColorSlot(R.string.custom_color_reasoning_panel, reasoningPanel, MaterialTheme.colorScheme.surfaceContainerHigh) { s.setCustomColorReasoningPanel(it) },
        ColorSlot(R.string.custom_color_chat_background, chatBackground, MaterialTheme.colorScheme.background) { s.setCustomColorChatBackground(it) },
        ColorSlot(R.string.custom_color_accent, accent, MaterialTheme.colorScheme.primary) { s.setCustomColorAccent(it) },
        ColorSlot(R.string.custom_color_input_field, inputField, MaterialTheme.colorScheme.surface) { s.setCustomColorInputField(it) },
    )

    activeSlot?.let { slot ->
        ColorSwatchPickerDialog(
            initialArgb = slot.value,
            fallback = slot.fallback,
            recentColors = recentColors,
            onRecentColorsChanged = { /* addRecentCustomColor is invoked in onPick */ },
            onPick = { argb ->
                slot.onPick(argb)
                if (argb != null) s.addRecentCustomColor(argb)
            },
            onDismiss = { activeSlot = null }
        )
    }

    CollapsingSettingsScaffold(title = stringResource(R.string.settings_custom_colors), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.custom_colors_section_colors),
                items = slots.map { slot ->
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(slot.labelRes)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape)
                                            .background(slot.value?.let { ColorMath.argbToColor(it) } ?: slot.fallback)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = { activeSlot = slot }) { Text(stringResource(R.string.edit)) }
                                }
                            },
                            modifier = Modifier.clickable { activeSlot = slot }
                        )
                    }
                }
            )
            SettingsGroup(
                title = stringResource(R.string.custom_colors_section_transparency),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.transparency_message_bubble)) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(value = bubbleAlpha, onValueChange = { s.setTransparencyMessageBubble(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                                    Text("${(bubbleAlpha * 100).toInt()}%")
                                }
                            }
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.transparency_reasoning_panel)) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(value = reasoningAlpha, onValueChange = { s.setTransparencyReasoningPanel(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                                    Text("${(reasoningAlpha * 100).toInt()}%")
                                }
                            }
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.transparency_drawer_item)) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(value = drawerAlpha, onValueChange = { s.setTransparencyDrawerItem(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                                    Text("${(drawerAlpha * 100).toInt()}%")
                                }
                            }
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.transparency_topbar)) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(value = topBarAlpha, onValueChange = { s.setTransparencyTopBar(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                                    Text("${(topBarAlpha * 100).toInt()}%")
                                }
                            }
                        )
                    },
                )
            )
        }
    }
}
