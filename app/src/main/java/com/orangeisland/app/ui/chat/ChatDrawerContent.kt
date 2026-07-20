package com.orangeisland.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.ui.chat.search.DrawerSearchBar
import com.orangeisland.app.ui.chat.search.SearchResultItem
import com.orangeisland.app.ui.chat.search.rememberDrawerSearchState
import com.orangeisland.app.ui.components.clearFocusOnTap
import com.orangeisland.app.ui.common.LocalOrangeIslandHaptics
import com.orangeisland.app.ui.theme.ChatType
import com.orangeisland.app.util.verticalEdgeFade
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The conversation navigation drawer: search box, new-chat button, conversation list with
 * per-item context menu, and the settings button. Reads its own flows from [viewModel];
 * shared host state (drawer slide progress, settings-button position, dialog requests) is
 * written back through callbacks so [ChatApp] keeps owning it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatDrawerContent(
    viewModel: ChatViewModel,
    drawerWidth: Dp,
    drawerState: DrawerState,
    scope: CoroutineScope,
    inputFocusRequester: FocusRequester,
    onDrawerProgress: (Float) -> Unit,
    onSettingsButtonTop: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestRename: (String, String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onPendingDrawerHaptic: (String?) -> Unit
) {
    val haptics = LocalOrangeIslandHaptics.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { drawerWidth.toPx() }

    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val isNewChatMode by viewModel.isNewChatMode.collectAsState()
    val isSwitching by viewModel.isSwitching.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 1.dp,
        modifier = Modifier
            .width(drawerWidth)
            .onGloballyPositioned { coords ->
                val x = coords.positionInWindow().x
                if (!x.isNaN() && drawerWidthPx > 0f) {
                    onDrawerProgress((1f + x / drawerWidthPx).coerceIn(0f, 1f))
                }
            }
            .graphicsLayer {
                clip = true
            }
    ) {
        val drawerListState = rememberLazyListState()
        val atTop = drawerListState.firstVisibleItemIndex == 0 && drawerListState.firstVisibleItemScrollOffset == 0
        val atBottom by remember {
            derivedStateOf {
                val layoutInfo = drawerListState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems == 0) {
                    true
                } else {
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.maxByOrNull { it.index }
                    lastVisibleItem != null &&
                        lastVisibleItem.index == totalItems - 1 &&
                        lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
                }
            }
        }
        val stw by animateFloatAsState(if (atTop) 0f else 1f, tween(200))
        val sbw by animateFloatAsState(if (atBottom) 0f else 1f, tween(200))
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clearFocusOnTap()
        ) {
            Text(stringResource(R.string.conversations), style = ChatType.conversationsTitle)
            Spacer(modifier = Modifier.height(12.dp))

            val search = rememberDrawerSearchState(viewModel)

            DrawerSearchBar(query = search.query, onQueryChange = { search.query = it })
            Spacer(modifier = Modifier.height(12.dp))

            if (!search.isActive) {
                val newChatDisabled = isSwitching
                val newChatContainer by animateColorAsState(
                    if (newChatDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primary,
                    tween(300), label = "newChatContainer"
                )
                val newChatContent by animateColorAsState(
                    if (newChatDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.onPrimary,
                    tween(300), label = "newChatContent"
                )
                Button(
                    onClick = {
                        if (!newChatDisabled) {
                            haptics.action()
                            viewModel.createNewChat()
                            scope.launch {
                                drawerState.close()
                                inputFocusRequester.requestFocus()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    enabled = true,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = newChatContainer,
                        contentColor = newChatContent
                    )
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.new_chat), style = ChatType.drawerButton)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (search.isActive && search.results.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }

            LazyColumn(state = drawerListState, modifier = Modifier.weight(1f).verticalEdgeFade(edgeFadeDp = 40f, topWeight = stw, bottomWeight = sbw)) {
                if (search.isActive) {
                    val grouped = search.results.groupBy { it.first.conversationId }
                    val titleMap = conversations.associate { it.id to it.title }
                    items(grouped.entries.toList()) { (convId, entries) ->
                        val bestScore = entries.maxOfOrNull { it.second } ?: 0f
                        SearchResultItem(
                            title = titleMap[convId] ?: stringResource(R.string.unknown),
                            messages = entries.map { it.first },
                            score = bestScore,
                            query = search.query,
                            onClick = {
                                haptics.selection()
                                if (convId != currentConversationId || isNewChatMode) {
                                    onPendingDrawerHaptic(convId)
                                }
                                viewModel.selectConversation(convId)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                } else {
                    items(conversations) { conversation ->
                        val isSelected = conversation.id == currentConversationId
                        var showMenu by remember { mutableStateOf(false) }
                        var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
                        var lastPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        val density = LocalDensity.current

                        Box {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(vertical = 2.dp)
                                    .clip(CircleShape)
                                    .pointerInput(showMenu) {
                                        if (!showMenu) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    lastPosition = event.changes.first().position
                                                }
                                            }
                                        }
                                    }
                                    .combinedClickable(
                                        enabled = !isSwitching,
                                        onClick = {
                                            haptics.selection()
                                            if (conversation.id != currentConversationId || isNewChatMode) {
                                                onPendingDrawerHaptic(conversation.id)
                                            }
                                            viewModel.selectConversation(conversation.id)
                                            scope.launch { drawerState.close() }
                                        },
                                        onLongClick = {
                                            haptics.longPress()
                                            pressOffset = with(density) {
                                                val x = lastPosition.x.toDp().coerceIn(16.dp, 200.dp)
                                                DpOffset(x, lastPosition.y.toDp() - 28.dp)
                                            }
                                            showMenu = true
                                        }
                                    ),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = conversation.title,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 16.dp,
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                offset = pressOffset,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.generate_title)) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    enabled = !isSwitching && !isLoading,
                                    onClick = {
                                        haptics.action()
                                        showMenu = false
                                        viewModel.generateTitle(conversation.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                enabled = !isSwitching && !isLoading,
                                onClick = {
                                    haptics.action()
                                    showMenu = false
                                    onRequestRename(conversation.id, conversation.title)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = if (!isSwitching && !isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = if (!isSwitching && !isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) },
                                enabled = !isSwitching && !isLoading,
                                onClick = {
                                    showMenu = false
                                    onRequestDelete(conversation.id)
                                }
                            )
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = {
                    haptics.action()
                    focusManager.clearFocus()
                    onOpenSettings()
                    scope.launch { drawerState.close() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .onGloballyPositioned { coords ->
                        val screenHeightPx = configuration.screenHeightDp * density.density
                        val buttonTopPx = coords.positionInWindow().y
                        onSettingsButtonTop((screenHeightPx - buttonTopPx) / density.density)
                    },
                shape = CircleShape
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings), style = ChatType.drawerButton)
            }
        }
    }
}
