package com.orangeisland.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.common.IslandIcon
import com.orangeisland.app.ui.common.IslandIcons
import com.orangeisland.app.viewmodel.ChatViewModel
import java.io.File

private enum class IllustrationSlot { CHAT, INPUT, DRAWER, USER_BUBBLE, TOPBAR, REASONING }

@Composable
fun SettingsIllustrationsPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val s = viewModel.settings
    val context = LocalContext.current
    val chatPath by s.illustrationChatBackgroundPath.collectAsState()
    val inputPath by s.illustrationInputBackgroundPath.collectAsState()
    val drawerPath by s.illustrationDrawerBackgroundPath.collectAsState()
    val userBubblePath by s.illustrationUserBubbleBackgroundPath.collectAsState()
    val topBarPath by s.illustrationTopBarBackgroundPath.collectAsState()
    val reasoningPath by s.illustrationReasoningBackgroundPath.collectAsState()
    val bubbleRadius by s.illustrationUserBubbleCornerRadius.collectAsState()
    val topBarScale by s.topBarCapsuleScale.collectAsState()

    var pendingSlot by remember { mutableStateOf<IllustrationSlot?>(null) }

    // Single launcher; the slot it writes to is captured in `pendingSlot` at the moment the
    // user taps "Choose image". android safety: OpenDocument() grants a transient URI, so we
    // copy the bytes into filesDir immediately in the callback.
    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null || slot == null) return@rememberLauncherForActivityResult
        val dir = File(context.filesDir, "illustration_backgrounds").apply { mkdirs() }
        val prefix = when (slot) {
            IllustrationSlot.CHAT -> "chat"
            IllustrationSlot.INPUT -> "input"
            IllustrationSlot.DRAWER -> "drawer"
            IllustrationSlot.USER_BUBBLE -> "user_bubble"
            IllustrationSlot.TOPBAR -> "topbar"
            IllustrationSlot.REASONING -> "reasoning"
        }
        val oldPath = when (slot) {
            IllustrationSlot.CHAT -> chatPath
            IllustrationSlot.INPUT -> inputPath
            IllustrationSlot.DRAWER -> drawerPath
            IllustrationSlot.USER_BUBBLE -> userBubblePath
            IllustrationSlot.TOPBAR -> topBarPath
            IllustrationSlot.REASONING -> reasoningPath
        }
        // Delete previous illustration for this slot (if any) so filesDir doesn't pile up.
        if (oldPath.isNotBlank()) runCatching { File(oldPath).delete() }

        val target = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (copied) {
            when (slot) {
                IllustrationSlot.CHAT -> s.setIllustrationChatBackgroundPath(target.absolutePath)
                IllustrationSlot.INPUT -> s.setIllustrationInputBackgroundPath(target.absolutePath)
                IllustrationSlot.DRAWER -> s.setIllustrationDrawerBackgroundPath(target.absolutePath)
                IllustrationSlot.USER_BUBBLE -> s.setIllustrationUserBubbleBackgroundPath(target.absolutePath)
                IllustrationSlot.TOPBAR -> s.setIllustrationTopBarBackgroundPath(target.absolutePath)
                IllustrationSlot.REASONING -> s.setIllustrationReasoningBackgroundPath(target.absolutePath)
            }
        }
    }

    fun launchPick(slot: IllustrationSlot) {
        pendingSlot = slot
        pickLauncher.launch(arrayOf("image/*"))
    }

    CollapsingSettingsScaffold(title = stringResource(R.string.illustrations_title), onBack = onBack) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.illustrations_title),
                items = listOf(
                    {
                        IllustrationRow(
                            labelRes = R.string.illustration_chat_background,
                            icon = IslandIcons.IllustrationChatBackground,
                            path = chatPath,
                            onPick = { launchPick(IllustrationSlot.CHAT) },
                            onClear = {
                                if (chatPath.isNotBlank()) runCatching { File(chatPath).delete() }
                                s.setIllustrationChatBackgroundPath("")
                            },
                        )
                    },
                    {
                        IllustrationRow(
                            labelRes = R.string.illustration_input_background,
                            icon = IslandIcons.IllustrationInputBackground,
                            path = inputPath,
                            onPick = { launchPick(IllustrationSlot.INPUT) },
                            onClear = {
                                if (inputPath.isNotBlank()) runCatching { File(inputPath).delete() }
                                s.setIllustrationInputBackgroundPath("")
                            },
                        )
                    },
                    {
                        IllustrationRow(
                            labelRes = R.string.illustration_drawer_background,
                            icon = IslandIcons.IllustrationDrawerBackground,
                            path = drawerPath,
                            onPick = { launchPick(IllustrationSlot.DRAWER) },
                            onClear = {
                                if (drawerPath.isNotBlank()) runCatching { File(drawerPath).delete() }
                                s.setIllustrationDrawerBackgroundPath("")
                            },
                        )
                    },
                    {
                        IllustrationRow(
                            labelRes = R.string.illustration_user_bubble_background,
                            icon = IslandIcons.IllustrationUserBubble,
                            path = userBubblePath,
                            onPick = { launchPick(IllustrationSlot.USER_BUBBLE) },
                            onClear = {
                                if (userBubblePath.isNotBlank()) runCatching { File(userBubblePath).delete() }
                                s.setIllustrationUserBubbleBackgroundPath("")
                            },
                        )
                    },
                    {
                        IllustrationRow(
                            labelRes = R.string.illustration_topbar_background,
                            icon = IslandIcons.IllustrationTopbar,
                            path = topBarPath,
                            onPick = { launchPick(IllustrationSlot.TOPBAR) },
                            onClear = {
                                if (topBarPath.isNotBlank()) runCatching { File(topBarPath).delete() }
                                s.setIllustrationTopBarBackgroundPath("")
                            },
                        )
                    },
                    {
                        IllustrationRow(
                            labelRes = R.string.illustration_reasoning_background,
                            icon = IslandIcons.IllustrationReasoningPanel,
                            path = reasoningPath,
                            onPick = { launchPick(IllustrationSlot.REASONING) },
                            onClear = {
                                if (reasoningPath.isNotBlank()) runCatching { File(reasoningPath).delete() }
                                s.setIllustrationReasoningBackgroundPath("")
                            },
                        )
                    },
                )
            )
            SettingsGroup(
                title = stringResource(R.string.illustrations_title),
                items = listOf(
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.illustration_bubble_corner_radius)) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(
                                        value = bubbleRadius,
                                        onValueChange = { s.setIllustrationUserBubbleCornerRadius(it) },
                                        valueRange = 0f..32f,
                                        modifier = Modifier.fillMaxWidth(1f).padding(end = 8.dp),
                                    )
                                    Text("${bubbleRadius.toInt()}dp")
                                }
                            }
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.topbar_capsule_scale)) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(
                                        value = topBarScale,
                                        onValueChange = { s.setTopBarCapsuleScale(it) },
                                        valueRange = 0.75f..1.5f,
                                        modifier = Modifier.fillMaxWidth(1f).padding(end = 8.dp),
                                    )
                                    Text("${(topBarScale * 100).toInt()}%")
                                }
                            }
                        )
                    },
                )
            )
        }
    }
}

@Composable
private fun IllustrationRow(
    labelRes: Int,
    icon: IslandIcons,
    path: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    SettingsItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { IslandIcon(icon, size = 38.dp) },
        supportingContent = {
            if (path.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        File(path).name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPick) { Text(stringResource(R.string.illustration_pick_image)) }
                if (path.isNotBlank()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.illustration_clear)) }
                }
            }
        },
    )
}
