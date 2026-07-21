package com.orangeisland.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.orangeisland.app.data.local.ProjectEntity
import com.orangeisland.app.model.ChatConversation
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
    onOpenWorkflows: () -> Unit = {},
    onRequestRename: (String, String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onPendingDrawerHaptic: (String?) -> Unit,
    // ── Projects: routed to the host so the dialogs live next to rename/delete ──
    onRequestCreateProject: () -> Unit,
    onRequestRenameProject: (String, String) -> Unit,
    onRequestProjectSettings: (String) -> Unit,
    onRequestDeleteProject: (String, String) -> Unit,
    onRequestMoveConversation: (String, String?) -> Unit
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
    val drawerItemAlpha by viewModel.settings.transparencyDrawerItem.collectAsState()
    val drawerBackgroundImagePath by viewModel.settings.illustrationDrawerBackgroundPath.collectAsState()

    val projects by viewModel.projects.collectAsState()
    val activeProjectId by viewModel.activeProjectId.collectAsState()

    // Per-project expand/collapse. Persisted across drawer open/close; new projects
    // default to expanded, "ungrouped" (null) starts expanded too.
    val expandedProjects = rememberSaveable(saver = ProjectExpandSaver) { mutableStateMapOf<String?, Boolean>() }
    fun isExpanded(pid: String?): Boolean = expandedProjects[pid] ?: true
    fun toggleExpanded(pid: String?) { expandedProjects[pid] = !isExpanded(pid) }

    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = if (drawerBackgroundImagePath.isNotBlank()) Color.Transparent else MaterialTheme.colorScheme.surface,
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
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (drawerBackgroundImagePath.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = drawerBackgroundImagePath,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Mandatory scrim -- conversation titles must stay legible.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                )
            }
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
                val newProjectDisabled = isSwitching
                val newProjectContainer by animateColorAsState(
                    if (newProjectDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primary,
                    tween(300), label = "newProjectContainer"
                )
                val newProjectContent by animateColorAsState(
                    if (newProjectDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.onPrimary,
                    tween(300), label = "newProjectContent"
                )
                Button(
                    onClick = {
                        if (!newProjectDisabled) {
                            haptics.action()
                            onRequestCreateProject()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    enabled = true,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = newProjectContainer,
                        contentColor = newProjectContent
                    )
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.new_project), style = ChatType.drawerButton)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (search.isActive && search.results.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ── Grouped conversation list (precomputed) ────────────
            // Computed outside LazyColumn because LazyListScope is NOT a @Composable scope;
            // remember() must be called from the surrounding Column. Cheap to rebuild.
            val conversationsByProject = remember(conversations) {
                conversations.groupBy { it.projectId }
            }
            val ungroupedConvs = conversationsByProject[null].orEmpty()
            val projectOrder = remember(projects, conversationsByProject) {
                // Render projects that exist OR contain conversations (covers the race where
                // a chat was filed under a project just deleted on another device).
                val seen = mutableSetOf<String?>()
                val ordered = mutableListOf<ProjectEntity?>()
                projects.forEach { p ->
                    if (p.id !in seen) { ordered.add(p); seen.add(p.id) }
                }
                if (null !in seen && ungroupedConvs.isNotEmpty()) { ordered.add(null); seen.add(null) }
                ordered
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
                    // Real projects first (in DB sort order), then "ungrouped" (projectId == null)
                    // always last. Each project renders a header row (tap to expand/collapse,
                    // long-press / ⋮ for project menu) followed by its conversations. A
                    // conversation's long-press menu gains a "move to project" submenu.
                    projectOrder.forEach { project ->
                        val pid = project?.id
                        val convs = if (pid == null) ungroupedConvs else conversationsByProject[pid].orEmpty()
                        // Skip empty real projects only when nothing to show AND collapsed — but we
                        // always show headers so the user can drop chats into them. Ungrouped hides
                        // entirely when empty (no point rendering an empty bucket).
                        if (pid == null && convs.isEmpty()) return@forEach

                        item(key = "header_${pid ?: "ungrouped"}") {
                            ProjectHeaderRow(
                                project = project,
                                ungroupedCount = if (pid == null) convs.size else null,
                                expanded = isExpanded(pid),
                                isActive = pid != null && pid == activeProjectId,
                                drawerItemAlpha = drawerItemAlpha,
                                onToggle = { haptics.selection(); toggleExpanded(pid) },
                                onOpenSettings = {
                                    haptics.action()
                                    viewModel.setActiveProject(pid)
                                    if (project != null) {
                                        onRequestProjectSettings(project.id)
                                        scope.launch { drawerState.close() }
                                    }
                                }
                            )
                        }

                        if (isExpanded(pid)) {
                            if (convs.isEmpty()) {
                                item(key = "empty_${pid ?: "ungrouped"}") {
                                    Text(
                                        text = stringResource(R.string.project_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 6.dp)
                                    )
                                }
                            } else {
                                items(convs, key = { "conv_${it.id}" }) { conversation ->
                                    ConversationRow(
                                        conversation = conversation,
                                        isSelected = conversation.id == currentConversationId,
                                        isSwitching = isSwitching,
                                        isLoading = isLoading,
                                        drawerItemAlpha = drawerItemAlpha,
                                        projects = projects,
                                        onOpen = {
                                            haptics.selection()
                                            if (conversation.id != currentConversationId || isNewChatMode) {
                                                onPendingDrawerHaptic(conversation.id)
                                            }
                                            viewModel.selectConversation(conversation.id)
                                            scope.launch { drawerState.close() }
                                        },
                                        onGenerateTitle = {
                                            haptics.action()
                                            viewModel.generateTitle(conversation.id)
                                        },
                                        onRename = { onRequestRename(conversation.id, conversation.title) },
                                        onDelete = { onRequestDelete(conversation.id) },
                                        onMove = { targetPid -> onRequestMoveConversation(conversation.id, targetPid) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = {
                    haptics.action()
                    focusManager.clearFocus()
                    onOpenWorkflows()
                    scope.launch { drawerState.close() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.workflows_title), style = ChatType.drawerButton)
            }

            Spacer(modifier = Modifier.height(8.dp))

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
}

// ════════════════════════════════════════════════════════════════════════
// Project-grouped drawer sub-components
// ════════════════════════════════════════════════════════════════════════

/**
 * A snapshot list serializer for the expand-state map. Keys are project ids or the
 * sentinel [UNGROUPED_KEY] for null; rememberSaveable can't hold a nullable-keyed Map
 * directly, so we round-trip through a List<Pair<String,Boolean>>.
 */
private const val UNGROUPED_KEY = "__ungrouped__"
private val ProjectExpandSaver = androidx.compose.runtime.saveable.Saver<
    androidx.compose.runtime.snapshots.SnapshotStateMap<String?, Boolean>,
    ArrayList<String>
>(
    save = { state ->
        // Encode each entry as "key|1" or "key|0"; collapsed entries are saved so the
        // collapsed state survives too. Default-expanded entries are omitted to keep it small.
        ArrayList(state.map { (k, v) -> "${k ?: UNGROUPED_KEY}|${if (v) 1 else 0}" })
    },
    restore = { saved ->
        androidx.compose.runtime.mutableStateMapOf<String?, Boolean>().apply {
            saved.forEach { entry ->
                val idx = entry.lastIndexOf('|')
                if (idx > 0) {
                    val key = entry.substring(0, idx)
                    val value = entry.substring(idx + 1) == "1"
                    this[if (key == UNGROUPED_KEY) null else key] = value
                }
            }
        }
    }
)

/**
 * Collapsible project header. Tapping the chevron/title toggles expand; tapping the
 * folder icon area activates the project (sets the context for the next new chat from
 * the top-bar "+"). The trailing ⋮ opens project settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectHeaderRow(
    project: ProjectEntity?,
    ungroupedCount: Int?,
    expanded: Boolean,
    isActive: Boolean,
    drawerItemAlpha: Float,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val title = project?.name ?: stringResource(R.string.ungrouped)
    val count = project?.let {
        // Real project count is rendered lazily via the host state; for compactness we
        // only show count for the ungrouped bucket here (matches ChatGPT behavior).
        null
    } ?: ungroupedCount
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(vertical = 2.dp)
            .clip(CircleShape)
            .clickable {
                if (project != null) {
                    onOpenSettings()
                } else {
                    onToggle()   // "未分组"没有设置页可进，点击整行退化成跟点箭头一样的展开/折叠
                }
            }
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = drawerItemAlpha.coerceAtLeast(0.4f))
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = drawerItemAlpha.coerceAtLeast(0.4f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = ChatType.drawerButton,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = drawerItemAlpha.coerceAtLeast(0.4f)),
            modifier = Modifier.weight(1f)
        )
        if (count != null && count > 0) {
            Text(
                text = stringResource(R.string.project_count_format, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = drawerItemAlpha.coerceAtLeast(0.4f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
    }
}

/**
 * One conversation row inside a project group. Same surface/click/long-press behavior as
 * the original flat list, plus a "move to project" submenu in the context menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ChatConversation,
    isSelected: Boolean,
    isSwitching: Boolean,
    isLoading: Boolean,
    drawerItemAlpha: Float,
    projects: List<ProjectEntity>,
    onOpen: () -> Unit,
    onGenerateTitle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (String?) -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalOrangeIslandHaptics.current
    var showMenu by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
    var lastPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
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
                    onClick = onOpen,
                    onLongClick = {
                        haptics.longPress()
                        pressOffset = with(density) {
                            val x = lastPosition.x.toDp().coerceIn(16.dp, 200.dp)
                            DpOffset(x, lastPosition.y.toDp() - 28.dp)
                        }
                        showMenu = true
                    }
                ),
            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = drawerItemAlpha) else Color.Transparent,
            shape = CircleShape
        ) {
            Text(
                text = conversation.title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge,
                color = (if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface).copy(alpha = drawerItemAlpha.coerceAtLeast(0.4f))
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
                    onGenerateTitle()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                enabled = !isSwitching && !isLoading,
                onClick = {
                    haptics.action()
                    showMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_to_project)) },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                enabled = !isSwitching && !isLoading,
                onClick = {
                    haptics.action()
                    showMenu = false
                    showMoveMenu = true
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.delete),
                        color = if (!isSwitching && !isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = if (!isSwitching && !isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                },
                enabled = !isSwitching && !isLoading,
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }

        // Move-to-project submenu. Anchored under the row; offers ungrouped + every project.
        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 16.dp,
            expanded = showMoveMenu,
            onDismissRequest = { showMoveMenu = false },
            offset = pressOffset,
            shape = RoundedCornerShape(12.dp)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ungrouped)) },
                enabled = conversation.projectId != null,
                onClick = {
                    showMoveMenu = false
                    onMove(null)
                }
            )
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
                    enabled = p.id != conversation.projectId,
                    onClick = {
                        showMoveMenu = false
                        onMove(p.id)
                    }
                )
            }
        }
    }
}
