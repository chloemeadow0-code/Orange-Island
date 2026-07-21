package com.orangeisland.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.orangeisland.app.R
import com.orangeisland.app.model.ChatConversation
import com.orangeisland.app.ui.theme.ChatType
import coil.compose.AsyncImage

/**
 * The chat screen's top bar: a title capsule (drawer menu + brand/conversation
 * title with optional token subtitle) and an actions capsule (system prompt +
 * new chat). Extracted from [ChatApp]; all behavior is routed through callbacks.
 */
@Composable
internal fun ChatTopBar(
    isNewChatMode: Boolean,
    activeProjectName: String? = null,
    onExitProject: () -> Unit = {},
    conversations: List<ChatConversation>,
    currentConversationId: String?,
    totalTokens: Int,
    onOpenDrawer: () -> Unit,
    onSystemPromptClick: () -> Unit,
    onNewChat: () -> Unit,
    topBarBackgroundImagePath: String = "",
    topBarAlpha: Float = 1f,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 180.dp)
            .background(
                Brush.verticalGradient(
                    0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                    0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                    1.0f to Color.Transparent
                )
            )
    ) {
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Resolve the active conversation's title; null in new-chat mode OR
                // before the conversation/title has loaded. Both the brand TEXT and the
                // brand font SIZE are gated on this single value, so the title never
                // changes size before the text swaps (no transient "橘子岛 at 17sp").
                val resolvedTitle = if (isNewChatMode) activeProjectName?.takeIf { it.isNotBlank() }
                    else conversations.find { it.id == currentConversationId }?.title?.takeIf { it.isNotBlank() }
                val showBrandTitle = resolvedTitle == null

                // Title capsule: menu + title
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (topBarBackgroundImagePath.isNotBlank()) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = topBarAlpha),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxHeight().widthIn(max = 260.dp)
                ) {
                    Box {
                        if (topBarBackgroundImagePath.isNotBlank()) {
                            AsyncImage(
                                model = topBarBackgroundImagePath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                            // Mandatory scrim -- icons/title text must stay legible over any photo.
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f * topBarAlpha))
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(5.dp))
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu), modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            if (showBrandTitle) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = ChatType.brandTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                            } else {
                                Column(modifier = Modifier.widthIn(max = 180.dp)) {
                                    Text(
                                        text = resolvedTitle,
                                        // Single-line (no token subtitle) uses a slightly-smaller-than-brand
                                        // solo size; with the token subtitle stacked below, the compact size.
                                        style = if (totalTokens > 0) ChatType.conversationTitle else ChatType.conversationTitleSolo,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (totalTokens > 0) {
                                        Text(
                                            text = stringResource(R.string.total_tokens, totalTokens),
                                            style = ChatType.micro,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (isNewChatMode && activeProjectName != null) {
                                    IconButton(onClick = onExitProject, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.project_exit), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Actions capsule: system prompt + new chat
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (topBarBackgroundImagePath.isNotBlank()) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = topBarAlpha),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Box {
                        if (topBarBackgroundImagePath.isNotBlank()) {
                            AsyncImage(
                                model = topBarBackgroundImagePath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                            // Mandatory scrim -- icons must stay legible over any photo.
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f * topBarAlpha))
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(5.dp))
                            IconButton(onClick = onSystemPromptClick, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.Psychology, contentDescription = stringResource(R.string.system_prompt), modifier = Modifier.size(26.dp))
                            }
                            IconButton(onClick = onNewChat, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat), modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                    }
                }
            }
    }
}
