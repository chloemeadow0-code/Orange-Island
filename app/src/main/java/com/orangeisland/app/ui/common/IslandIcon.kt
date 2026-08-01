package com.orangeisland.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.theme.OrangeIslandTokens

/**
 * Circular "island" badge that hosts the 38 Orange Island watercolor icons.
 *
 * Mirrors the UI Playground `.island-icon` rule: a soft cream-paper disc with a
 * hairline border and a gentle shadow, with the transparent PNG inset so it is
 * never clipped. The badge itself only carries artwork + container styling; it
 * has no click handling of its own, so it never steals taps from the parent row.
 *
 * @param res the `R.drawable.island_*` watercolor icon.
 * @param size disc diameter (the design uses 38–45dp; 42dp is the row default).
 */
@Composable
fun IslandIcon(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    size: Dp = OrangeIslandTokens.IconBadgeDefault,
    contentDescription: String? = null,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OrangeIslandTokens.badgeBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(res),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.06f),
            )
        }
    }
}

/**
 * Convenience overload keyed by the icon's stable logical id, so call sites read
 * `IslandIcon(IslandIcons.WebSearch)` instead of the raw drawable resource.
 */
@Composable
fun IslandIcon(
    icon: IslandIcons,
    modifier: Modifier = Modifier,
    size: Dp = OrangeIslandTokens.IconBadgeDefault,
    contentDescription: String? = null,
) {
    IslandIcon(icon.res, modifier, size, contentDescription)
}

/** Stable registry mapping each Orange Island icon to its drawable resource. */
enum class IslandIcons(@DrawableRes val res: Int) {
    WebSearch(R.drawable.island_web_search),
    ConversationSearch(R.drawable.island_conversation_search),
    Terminal(R.drawable.island_terminal),
    McpServer(R.drawable.island_mcp_server),
    Plugin(R.drawable.island_plugin),
    DeviceAccess(R.drawable.island_device_access),
    Proxy(R.drawable.island_proxy),
    Settings(R.drawable.island_settings),
    Provider(R.drawable.island_provider),
    Model(R.drawable.island_model),
    Memory(R.drawable.island_memory),
    Appearance(R.drawable.island_appearance),
    Generation(R.drawable.island_generation),
    TitleGeneration(R.drawable.island_title_generation),
    StreamingResponse(R.drawable.island_streaming_response),
    Transcription(R.drawable.island_transcription),
    ImageGeneration(R.drawable.island_image_generation),
    Prompts(R.drawable.island_prompts),
    SystemPrompt(R.drawable.island_system_prompt),
    Embedding(R.drawable.island_embedding),
    DataControl(R.drawable.island_data_control),
    AutoBackup(R.drawable.island_auto_backup),
    ClaudeImport(R.drawable.island_claude_import),
    Sandbox(R.drawable.island_sandbox),
    Workflow(R.drawable.island_workflow),
    WorkflowCanvas(R.drawable.island_workflow_canvas),
    WorkflowEditor(R.drawable.island_workflow_editor),
    WorkflowLog(R.drawable.island_workflow_log),
    CustomColors(R.drawable.island_custom_colors),
    Illustrations(R.drawable.island_illustrations),
    Language(R.drawable.island_language),
    Logs(R.drawable.island_logs),
    About(R.drawable.island_about),
    SettingsSearch(R.drawable.island_settings_search),
    Documentation(R.drawable.island_documentation),
    Health(R.drawable.island_health),
    Thinking(R.drawable.island_thinking),
    VoiceSynthesis(R.drawable.island_voice_synthesis),
}
