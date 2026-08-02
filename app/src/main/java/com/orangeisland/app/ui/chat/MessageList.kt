package com.orangeisland.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.ToolCallDisplayModes
import com.orangeisland.app.ui.chat.message.MessageItem
import com.orangeisland.app.util.Constants

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    allMessages: List<ChatMessage> = emptyList(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    state: LazyListState = rememberLazyListState(),
    userScrollEnabled: Boolean = true,
    isLoading: Boolean = false,
    isSwitching: Boolean = false,
    visualizeContextRollout: Boolean = false,
    showUsageStats: Boolean = false,
    toolCallDisplayMode: String = ToolCallDisplayModes.DEFAULT,
    maxContextWindow: Int = 20,
    modelAliases: Map<String, String> = emptyMap(),
    customUserBubbleColor: Long? = null,
    userBubbleMaskAlpha: Float = 1f,
    userBubbleBackgroundImagePath: String = "",
    userBubbleCornerRadiusOverride: Float? = null,
    customAssistantBubbleColor: Long? = null,
    customReasoningPanelColor: Long? = null,
    reasoningBackgroundImagePath: String = "",
    customChatTextColor: Long? = null,
    customGlobalTextColor: Long? = null,
    messageBubbleAlpha: Float = 1f,
    reasoningPanelAlpha: Float = 1f,
    bottomBarHeight: androidx.compose.ui.unit.Dp = 0.dp,
    viewportHeight: Int = 0,
    messageHeights: SnapshotStateMap<String, Int> = remember { mutableStateMapOf() },
    onEditMessage: (String, String) -> Unit = { _, _ -> },
    onEditAssistantMessage: (String, String) -> Unit = { _, _ -> },
    onSwitchBranch: (String?, String, Int) -> Unit = { _, _, _ -> },
    onRegenerate: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onMediaClick: (List<String>, Int) -> Unit = { _, _ -> },
    onFileContentClick: ((fileName: String, content: String) -> Unit)? = null,
    onPdfPagesClick: ((pages: List<String>, startIndex: Int) -> Unit)? = null,
    thoughtExpandedStates: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() },
    codeBlockWrapEnabled: Boolean = false,
    splitBubbleByLine: Boolean = false,
    // When the host is driving ChatGPT-style stick-to-bottom scrolling, the dynamic
    // spacer below the messages MUST stay at zero. That spacer resizes on every
    // streaming token (it depends on live messageHeights) and fights the programmatic
    // scrollToItem, producing the "jumps around while generating" jitter. With this
    // on, messages stack naturally and the scroll logic owns vertical positioning.
    stickToBottomEnabled: Boolean = false,
) {
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isLoading) { if (isLoading) editingMessageId = null }
    var lastKnownExtraPadding by remember { mutableStateOf(0.dp) }
    LaunchedEffect(isSwitching) { if (isSwitching) lastKnownExtraPadding = 0.dp }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val currentPath = messages.filter { it.participant != Participant.ERROR }
    val contextStartIndex = if (currentPath.size > maxContextWindow) currentPath.size - maxContextWindow else 0
    val inContextIds = currentPath.drop(contextStartIndex).map { it.id }.toSet()

    val lastUserMessageIndex = messages.indexOfLast { it.participant == Participant.USER }

    // Precompute branch siblings grouped by parent once per allMessages change.
    // Previously this filter+sort ran per visible item (O(n²) and re-run on every
    // streaming-token recomposition of the active message).
    val siblingsByParent = remember(allMessages) {
        allMessages
            .filter { !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX) }
            .groupBy { it.parentId }
            .mapValues { (_, v) -> v.sortedBy { it.timestamp } }
    }

    val extraPadding = if (lastUserMessageIndex == -1 || viewportHeight == 0) {
        0.dp
    } else if (stickToBottomEnabled) {
        // In stick-to-bottom mode the host drives vertical position entirely via
        // scrollToItem(lastIndex, 0); the list must NOT add a tail spacer that resizes
        // mid-stream (jitter) NOR a giant fixed spacer (which, combined with the scroll
        // target, produced the "blank screen, scroll up to see messages" bug). Zero it.
        0.dp
    } else {
        val rangeIds = (lastUserMessageIndex until messages.size).map { messages[it].id }
        val allMeasured = rangeIds.all { messageHeights.containsKey(it) }
        if (!allMeasured) {
            // 区间内还有消息没被测量过高度(刚插入、还没渲染完)——沿用上一次算出的
            // 正确值,不要把未测量的当成 0 参与计算,否则会先算出偏大的值、
            // 测量完成后再收缩,造成一次可见的跳动。
            lastKnownExtraPadding
        } else {
            with(density) {
                val vDp = viewportHeight.toDp()
                val targetTopDp = 140.dp
                val availableSpaceDp = vDp - targetTopDp - (bottomBarHeight + 8.dp)
                val contentHeightPx = rangeIds.sumOf { messageHeights[it] ?: 0 }
                val contentHeightDp = contentHeightPx.toDp()
                val computed = (availableSpaceDp - contentHeightDp).coerceAtLeast(0.dp)
                lastKnownExtraPadding = computed
                computed
            }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            reverseLayout = false,
            state = state,
            userScrollEnabled = userScrollEnabled
        ) {
            items(messages, key = { it.id }) { message ->
                val isLastMessage = messages.lastOrNull()?.id == message.id
                val isInContext = inContextIds.contains(message.id)
                val siblings = siblingsByParent[message.parentId].orEmpty()
                val branchIndex = siblings.indexOfFirst { it.id == message.id }
                val totalBranches = siblings.size

                // Fade newly-appended messages in. Placement/fade-out left off so this
                // doesn't fight the manual height/scroll padding management below.
                Box(modifier = Modifier.animateItem(fadeInSpec = tween(400), placementSpec = null, fadeOutSpec = null)) {
                MessageItem(
                    message = message,
                    onEdit = { id, text ->
                        onEditMessage(id, text)
                        editingMessageId = null
                    },
                    onEditAssistantMessage = { id, text ->
                        onEditAssistantMessage(id, text)
                        editingMessageId = null
                    },
                    customUserBubbleColor = customUserBubbleColor,
                    userBubbleMaskAlpha = userBubbleMaskAlpha,
                    userBubbleBackgroundImagePath = userBubbleBackgroundImagePath,
                    userBubbleCornerRadiusOverride = userBubbleCornerRadiusOverride,
                    customAssistantBubbleColor = customAssistantBubbleColor,
                    customReasoningPanelColor = customReasoningPanelColor,
                    reasoningBackgroundImagePath = reasoningBackgroundImagePath,
                    customChatTextColor = customChatTextColor,
                    customGlobalTextColor = customGlobalTextColor,
                    messageBubbleAlpha = messageBubbleAlpha,
                    reasoningPanelAlpha = reasoningPanelAlpha,
                    // isStreaming driven by message status, not isLoading flag
                    isStreaming = isLastMessage && message.participant == Participant.MODEL
                        && message.status in setOf(MessageStatus.SENDING, MessageStatus.THINKING, MessageStatus.TOOL_CALLING, MessageStatus.TRANSCRIBING),
                    isLoading = isLoading,
                    isEditingAllowed = (editingMessageId == null || editingMessageId == message.id) && !isLoading,
                    isEditing = editingMessageId == message.id,
                    isSwitching = isSwitching,
                    isInContext = isInContext,
                    modelAliases = modelAliases,
                    visualizeContextRollout = visualizeContextRollout,
                    showUsageStats = showUsageStats,
                    splitBubbleByLine = splitBubbleByLine,
                    toolCallDisplayMode = toolCallDisplayMode,
                    onStartEdit = { editingMessageId = message.id },
                    onCancelEdit = { editingMessageId = null },
                    branchIndex = branchIndex,
                    totalBranches = totalBranches,
                    onSwitchBranch = { direction -> onSwitchBranch(message.parentId, message.id, direction) },
                    onRegenerate = onRegenerate,
                    onDelete = onDelete,
                    onMediaClick = onMediaClick,
                    onFileContentClick = onFileContentClick,
                    onPdfPagesClick = onPdfPagesClick,
                    onHeightChanged = { height -> messageHeights[message.id] = height },
                    thoughtExpandedStates = thoughtExpandedStates,
                    codeBlockWrapEnabled = codeBlockWrapEnabled,
                )
                }
            }
            item {
                Spacer(modifier = Modifier.height(extraPadding))
            }
        }
    }
}
