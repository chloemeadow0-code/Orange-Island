package com.orangeisland.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.util.gradientBlur
import com.orangeisland.app.model.Participant
import com.orangeisland.app.ui.chat.bottombar.ChatBottomBar
import com.orangeisland.app.ui.chat.message.hasActiveAnswerSegment
import com.orangeisland.app.ui.components.AnimatedBlobBackground
import com.orangeisland.app.ui.components.clearFocusOnTap
import com.orangeisland.app.ui.components.TypewriterText
import com.orangeisland.app.ui.common.DecorativeImage
import com.orangeisland.app.ui.common.LocalOrangeIslandHaptics
import com.orangeisland.app.ui.common.rememberOrangeIslandHaptics
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val SCROLL_EASING = CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.0f)

// isVisibleAnswerSegment() / hasActiveAnswerSegment() are shared (internal) from
// MessageItemSegments.kt.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatApp(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenMiniApp: () -> Unit = {},
    onOpenMusicStudio: () -> Unit = {},
    onMediaClick: (List<String>, Int) -> Unit,
    onFileContentClick: ((String, String) -> Unit)? = null,
    onPdfPagesClick: ((List<String>, Int) -> Unit)? = null,
    onPdfPreviewSelect: ((List<String>, Int) -> Unit)? = null,
    pdfViewerSelection: Set<Int> = emptySet(),
    onTogglePdfSelection: ((Int) -> Unit)? = null,
    onInitPdfSelection: ((Set<Int>) -> Unit)? = null,
    fullScreenViewerUrls: List<String>? = null,
    onSnackbarOffsetChanged: (androidx.compose.ui.unit.Dp) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed,
        confirmStateChange = { newValue ->
            if (newValue != DrawerValue.Closed) {
                focusManager.clearFocus()
            }
            true
        }
    )

    val uiState by viewModel.chatUiState.collectAsState()
    val thoughtExpandedStates = remember(uiState.currentConversationId) { mutableStateMapOf<String, Boolean>() }
    val haptics = rememberOrangeIslandHaptics(uiState.hapticsEnabled)


    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var conversationToRename by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var showMcpSheet by remember { mutableStateOf(false) }
    // ── Project dialogs (parallel to rename/delete above) ──
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var projectToSettings by remember { mutableStateOf<String?>(null) }
    var projectToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var outerSpacerStartNanos by remember { mutableLongStateOf(0L) }
    var outerSpacerTickNanos by remember { mutableLongStateOf(0L) }
    val spacerDurationMs = 400f
    val spacerEasing = remember { CubicBezierEasing(0.15f, 0.5f, 0.25f, 1.0f) }

    // Start timing synchronously on the first expand frame; never reset
    if (isExpanded && outerSpacerStartNanos == 0L) {
        outerSpacerStartNanos = System.nanoTime()
    }
    if (!isExpanded) {
        outerSpacerStartNanos = 0L
        outerSpacerTickNanos = 0L
    }

    val spacerElapsedMs = if (outerSpacerStartNanos > 0L) {
        val tick = if (outerSpacerTickNanos > 0L) outerSpacerTickNanos else outerSpacerStartNanos
        ((tick - outerSpacerStartNanos) / 1_000_000f).coerceIn(0f, spacerDurationMs)
    } else 0f

    val isExpandAnimating = outerSpacerStartNanos > 0L && spacerElapsedMs < spacerDurationMs

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            while (true) {
                outerSpacerTickNanos = System.nanoTime()
                if ((outerSpacerTickNanos - outerSpacerStartNanos) / 1_000_000f >= spacerDurationMs) break
                delay(16L)
            }
        }
    }

    val outerSpacerHeightPx: Float = if (outerSpacerStartNanos > 0L) {
        val easedFraction = spacerEasing.transform(spacerElapsedMs / spacerDurationMs)
        with(density) { 44.dp.toPx() } * (1f - easedFraction)
    } else 0f

    val configuration = LocalConfiguration.current
    val drawerWidth = configuration.screenWidthDp.dp * 0.8f
    var bottomBarHeightPx by rememberSaveable { mutableFloatStateOf(0f) }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    var drawerProgress by remember { mutableFloatStateOf(0f) }
    // Bottom offset to clear the Settings button in the drawer.
    var settingsButtonTopDp by remember { mutableFloatStateOf(80f) }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // When expanded, the Surface fills the screen and the model-selector capsule sits
    // at the very bottom. Snackbar must clear: nav bar + IME + Surface outer padding + Box
    // bottom padding + Row height/margin + a small gap.
    val bottomInset = maxOf(navBarBottom, imeBottom)
    val expandedCapsuleOffset = bottomInset + 74.dp
    val targetSnackbarOffset = if (drawerProgress <= 0.5f) {
        if (isExpanded) expandedCapsuleOffset else (bottomBarHeight - 4.dp).coerceAtLeast(0.dp)
    } else {
        val t = ((drawerProgress - 0.5f) * 2f).coerceIn(0f, 1f)
        (bottomBarHeight.value + (settingsButtonTopDp - bottomBarHeight.value) * t).dp.coerceAtLeast(0.dp)
    }
    LaunchedEffect(targetSnackbarOffset) { onSnackbarOffsetChanged(targetSnackbarOffset) }
    val listState = rememberLazyListState()
    val textFieldState = rememberSaveable(saver = androidx.compose.foundation.text.input.TextFieldState.Saver) { androidx.compose.foundation.text.input.TextFieldState() }
    val inputFocusRequester = remember { FocusRequester() }

    // -- Scroll state (ChatGPT-style stick-to-bottom) --
    // The list auto-follows new content ONLY while the user is parked at the
    // bottom. The moment they scroll up to read history, follow is suspended so
    // programmatic scrolls don't fight their fingers; it resumes on send or when
    // they tap the "scroll to bottom" FAB.
    //
    // IMPORTANT: we must NOT key the "user scrolled" signal on isScrollInProgress,
    // because our OWN programmatic scrollToItem/animateScrollToItem also flips
    // isScrollInProgress true -- doing so created a feedback loop (scroll ->
    // "user scrolled" -> unpin -> stop following -> content overflows -> scroll
    // again -> ...) that produced the visible up/down jitter. Instead we watch the
    // FINAL settled scroll position via firstVisibleItemIndex: if the viewport
    // ends up more than a couple items above the bottom after a scroll session,
    // only then treat it as the user having intentionally scrolled up.
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= total - 2
        }
    }

    // Pinned = auto-follow is active. Mutated only by (a) the settle detector
    // below, (b) explicit re-pins from send / FAB / open-conversation. NEVER by
    // the streaming-follower itself, so there's no self-triggered toggling.
    val stickToBottom = remember { mutableStateOf(true) }
    // Guards the settle detector so it ignores scroll sessions WE started.
    val programmaticScroll = remember { mutableStateOf(false) }

    // When a scroll session ends and we did NOT start it, look at where it landed:
    // off-bottom -> the user scrolled up -> suspend follow. This is the only place
    // that ever clears the pin, and it can't react to our own scrolls.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }                                   // scroll just came to rest
            .collect {
                if (!programmaticScroll.value && !isAtBottom) {
                    stickToBottom.value = false
                }
            }
    }

    // Consume a one-shot prefilled input (set by an outside caller like the workflow detail page's
    // "Edit in chat" button) into the chat input field. Clears the pending value afterwards.
    val pendingPrefill = uiState.pendingPrefillInput
    LaunchedEffect(pendingPrefill) {
        val text = pendingPrefill ?: return@LaunchedEffect
        if (text.isNotBlank()) {
            textFieldState.edit { replace(0, length, text) }
        }
        viewModel.consumePendingPrefillInput()
    }

    val messageHeights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    var showLaunchContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        showLaunchContent = true
    }


    // ���� Programmatic scroll helpers ����������������������������������������������������������������������������������
    // All of these rely on LazyListState's own layout-driven positioning
    // (animateScrollToItem / scrollToItem) instead of summing per-message pixel
    // heights. The old approach manually accumulated messageHeights to compute an
    // absolute offset, which raced against layout (unmeasured items read as 0 and
    // made the target land too high) and produced the "I scroll down, it jumps
    // back up" jerks. LazyListState already knows the true positions once laid
    // out, so we defer to it.

    /** Scroll to the bottom of the conversation. ChatGPT-style "stick to bottom":
     *  we land on the last item and expose its BOTTOM so a streaming reply that
     *  keeps growing stays anchored at the lower edge of the viewport. Wraps the
     *  actual scroll in the programmaticScroll guard so the settle detector above
     *  knows this scroll came from us and doesn't mistake it for the user scrolling
     *  (which would wrongly suspend follow). */
    suspend fun scrollToBottom(animate: Boolean = true) {
        if (uiState.messages.isEmpty()) return
        // scrollToItem(index, offset) aligns the item's TOP to the contentPadding.top line,
        // then scrolls DOWN by `offset` px. A huge offset (Int.MAX_VALUE) was pushing the
        // last message UP and out of the viewport, leaving the tail spacer as a blank
        // screen �� that's the "blank screen, scroll up to see uiState.messages" bug. offset 0 just
        // parks the last message at the top padding line, which for any conversation taller
        // than the viewport is exactly the bottom; for short ones the list can't scroll past
        // its content anyway, so it lands at max scroll (uiState.messages visible, no blank).
        val lastIndex = uiState.messages.lastIndex
        try {
            withTimeout(2000) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .filter { it > lastIndex }
                    .first()
            }
        } catch (e: Exception) {
            // Layout never caught up in time; fall through and try anyway.
        }
        programmaticScroll.value = true
        try {
            if (animate) listState.animateScrollToItem(lastIndex, 0)
            else listState.scrollToItem(lastIndex, 0)
        } finally {
            programmaticScroll.value = false
        }
    }

    /** Bring a specific message to the top of the viewport. Used by the
     *  edit / regenerate / branch-switch flows where we want to keep a chosen
     *  message in view rather than jumping to the very bottom. For a MODEL reply
     *  we target its parent USER message, matching the previous behaviour. */
    suspend fun scrollToMessage(targetMessageId: String, animate: Boolean = true) {
        val msg = uiState.messages.find { it.id == targetMessageId } ?: return
        val targetIndex = if (msg.participant == Participant.MODEL && msg.parentId != null) {
            val parentIndex = uiState.messages.indexOfFirst { it.id == msg.parentId }
            if (parentIndex != -1) parentIndex else uiState.messages.indexOfFirst { it.id == targetMessageId }
        } else {
            uiState.messages.indexOfFirst { it.id == targetMessageId }
        }
        if (targetIndex == -1) return
        if (animate) listState.animateScrollToItem(targetIndex, 0)
        else listState.scrollToItem(targetIndex, 0)
    }

    val branchSwitchTrigger = uiState.branchSwitchTrigger

    LaunchedEffect(branchSwitchTrigger) {
        val targetMessageId = branchSwitchTrigger ?: return@LaunchedEffect
        if (uiState.currentConversationId == null) {
            viewModel.clearBranchSwitchTrigger()
            viewModel.clearBranchSwitching()
            return@LaunchedEffect
        }

        try {
            val currentMsgs = withTimeout(4000) {
                snapshotFlow { uiState.messages }
                    .filter { currentMsgs -> currentMsgs.any { it.id == targetMessageId } }
                    .first()
            }
            com.orangeisland.app.util.DebugLog.d("MsgRender", "branchSwitch resolved: msgs=${currentMsgs.size} " +
                "ids=${currentMsgs.joinToString(",") { it.id.take(12) }}")

            val msg = currentMsgs.find { it.id == targetMessageId }
            val currentTargetIndex = if (msg?.participant == Participant.MODEL && msg.parentId != null) {
                val parentIndex = currentMsgs.indexOfFirst { it.id == msg.parentId }
                if (parentIndex != -1) parentIndex else currentMsgs.indexOfFirst { it.id == targetMessageId }
            } else {
                currentMsgs.indexOfFirst { it.id == targetMessageId }
            }

            if (currentTargetIndex != -1) {
                com.orangeisland.app.util.DebugLog.d("MsgRender", "scrollToItem index=$currentTargetIndex " +
                    "layoutInfo.totalItemsCount=${listState.layoutInfo.totalItemsCount}")
                listState.scrollToItem(currentTargetIndex, 0)
            }
        } catch (e: Exception) {
            // Timeout or intended cancellation
        }
        viewModel.clearBranchSwitchTrigger()
        viewModel.clearBranchSwitching()
    }

    LaunchedEffect(uiState.currentConversationId) {
        // New chat's first send creates the conversation; its own scroll-to-message handles
        // scrolling, so skip this conversation-open auto-scroll once to avoid a double scroll.
        if (viewModel.suppressNextOpenScroll) {
            viewModel.suppressNextOpenScroll = false
            viewModel.setSwitching(false)
            return@LaunchedEffect
        }
        if (uiState.currentConversationId != null) {
            // IMPORTANT timing: while the switching overlay is up, MessageList is fed an
            // EMPTY list (see the switchingToExisting guard at the MessageList call site),
            // so the LazyColumn hasn't laid out any real items yet. We must NOT scroll
            // before the overlay drops �� scrolling against an empty/just-inflating list is
            // exactly the "freezes for a beat, then jumps to the last message" symptom.
            // So: wait for the overlay to clear (isSwitching == false), which means the real
            // message list has been switched in, THEN give the LazyColumn a frame to measure,
            // THEN jump to the bottom. scrollToBottom also waits for totalItemsCount itself.
            try {
                withTimeout(4000) {
                    snapshotFlow { uiState.isSwitching }.filter { !it }.first()
                }
            } catch (e: Exception) {
                // Timeout
            }
            // One frame of grace so the LazyColumn measures the real items before we jump.
            kotlinx.coroutines.delay(16)
            try {
                scrollToBottom(animate = false)
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    // -- Streaming auto-follow (ChatGPT-style) --
    // While pinned, keep the newest content visible as a streaming reply grows.
    //  - Trigger = message content fingerprint (id + text length + status). NEVER
    //    layoutInfo: reading the visible-items state creates a feedback loop with
    //    our own scrolls. NEVER stickToBottom inside the snapshot either: that
    //    would re-fire on every pin flip. We read it plain (outside snapshotFlow)
    //    in the collector instead, so a pin change can't itself trigger a scroll.
    //  - The actual scrollToItem is wrapped in the programmaticScroll guard so the
    //    settle detector treats it as ours, not the user's.
    //  - conflate() caps work to one scroll per frame; per-token micro-jumps are
    //    smoothed out instead of queueing into visible jitter.
    LaunchedEffect(uiState.currentConversationId) {
        snapshotFlow {
            val last = uiState.messages.lastOrNull()
            last?.let { "${it.id}|${it.text.length}|${it.thoughts?.length ?: 0}|${it.status}" }
        }.filter { uiState.messages.isNotEmpty() }
            .conflate()
            .collect {
                if (stickToBottom.value && uiState.messages.isNotEmpty()) {
                    val last = uiState.messages.lastOrNull()
                    com.orangeisland.app.util.DebugLog.d(
                        "ChatScroll",
                        "stream-follow: lastId=${last?.id?.take(8)} len=${last?.text?.length} hasDetails=${last?.text?.contains("<details", ignoreCase = true) == true} firstIdx=${listState.firstVisibleItemIndex} firstOff=${listState.firstVisibleItemScrollOffset} total=${listState.layoutInfo.totalItemsCount}"
                    )
                    programmaticScroll.value = true
                    try {
                        // offset 0 parks the last message at the top padding line (= the
                        // bottom of a tall list). See scrollToBottom for why Int.MAX_VALUE
                        // was wrong (blank screen).
                        listState.scrollToItem(uiState.messages.lastIndex, 0)
                    } finally {
                        programmaticScroll.value = false
                    }
                }
            }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToMessage.collect { messageId ->
            // The only producer of this flow today is MessageGenerationController,
            // which fires onScrollToMessage(userMessageId) right after a send. The
            // old behaviour scrolled TO that user message, leaving the just-created
            // assistant reply below the fold �� i.e. "it jumps to my message". For
            // ChatGPT-style we want the send to pin to the BOTTOM (the live reply),
            // so both null and non-null ids route through scrollToBottom here.
            // scrollToMessage(targetId) is kept for explicit "bring this message to
            // the top" callers (branch switch via branchSwitchTrigger, etc.).
            stickToBottom.value = true
            // Wait a tick for the new MODEL placeholder to be inserted + measured
            // before we jump, otherwise scrollToItem lands on the pre-send layout.
            delay(50)
            scrollToBottom(animate = true)
        }
    }

    BackHandler(enabled = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed) {
        focusManager.clearFocus()
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue != DrawerValue.Closed) {
            isExpanded = false
            focusManager.clearFocus()
        }
    }

    var observedGeneration by remember { mutableStateOf(uiState.isLoading) }
    var previousIsLoading by remember { mutableStateOf(uiState.isLoading) }
    LaunchedEffect(uiState.isLoading) {
        when {
            uiState.isLoading && !previousIsLoading -> {
                observedGeneration = true
            }
            !uiState.isLoading && previousIsLoading && observedGeneration -> {
                val terminalStatus = uiState.messages.lastOrNull { it.participant == Participant.MODEL }?.status
                when (terminalStatus) {
                    MessageStatus.ERROR -> haptics.reject()
                    MessageStatus.STOPPED -> haptics.generationStopped()
                    else -> haptics.generationEnd()
                }
                observedGeneration = false
            }
        }
        previousIsLoading = uiState.isLoading
    }

    val answeringHapticActive = uiState.isLoading &&
        uiState.generatingInConversationId == uiState.currentConversationId &&
        uiState.messages.lastOrNull { it.participant == Participant.MODEL }?.let { message ->
            message.status == MessageStatus.SENDING && message.hasActiveAnswerSegment()
        } == true
    DisposableEffect(answeringHapticActive, uiState.hapticsEnabled) {
        if (answeringHapticActive && uiState.hapticsEnabled) {
            haptics.startAnsweringTexture()
        }
        onDispose {
            haptics.stopAnsweringTexture()
        }
    }

    var pendingDrawerConversationHaptic by remember { mutableStateOf<String?>(null) }
    var previousIsSwitching by remember { mutableStateOf(uiState.isSwitching) }
    LaunchedEffect(uiState.isSwitching, uiState.currentConversationId) {
        if (
            previousIsSwitching &&
            !uiState.isSwitching &&
            pendingDrawerConversationHaptic != null &&
            pendingDrawerConversationHaptic == uiState.currentConversationId
        ) {
            haptics.success()
            pendingDrawerConversationHaptic = null
        }
        previousIsSwitching = uiState.isSwitching
    }

    CompositionLocalProvider(
        LocalOrangeIslandHaptics provides haptics,
        LocalCameraToolGate provides viewModel.cameraToolGate
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = DrawerDefaults.scrimColor,
        drawerContent = {
            ChatDrawerContent(
                viewModel = viewModel,
                drawerWidth = drawerWidth,
                drawerState = drawerState,
                scope = scope,
                inputFocusRequester = inputFocusRequester,
                onDrawerProgress = { drawerProgress = it },
                onSettingsButtonTop = { settingsButtonTopDp = it },
                onOpenSettings = onOpenSettings,
                onOpenMiniApp = onOpenMiniApp,
                onOpenMusicStudio = onOpenMusicStudio,
                onRequestRename = { id, title -> showRenameDialog = id; conversationToRename = title },
                onRequestDelete = { id -> showDeleteConfirmDialog = id },
                onPendingDrawerHaptic = { pendingDrawerConversationHaptic = it },
                onRequestCreateProject = { showCreateProjectDialog = true },
                onRequestRenameProject = { id, name -> projectToRename = id to name },
                onRequestProjectSettings = { id -> projectToSettings = id },
                onRequestDeleteProject = { id, name -> projectToDelete = id to name },
                onRequestMoveConversation = { convId, targetPid -> viewModel.moveConversation(convId, targetPid) }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (uiState.chatBackgroundImagePath.isBlank()) {
                        uiState.customChatBackground?.let { argb ->
                            Modifier.background(com.orangeisland.app.ui.components.ColorMath.argbToColor(argb))
                        } ?: Modifier
                    } else Modifier
                )
                .clearFocusOnTap()
                .onSizeChanged { viewportHeightPx = it.height }
        ) {
            if (uiState.chatBackgroundImagePath.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = uiState.chatBackgroundImagePath,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val (targetCa, targetQa) = if (!dark) {
                0.00f to 0.00f
            } else if (uiState.isNewChatMode) {
                0.20f to 0.10f
            } else {
                0.02f to 0.01f
            }
            val ca by animateFloatAsState(targetCa, tween(800))
            val qa by animateFloatAsState(targetQa, tween(800))
            AnimatedBlobBackground(centerAlpha = ca, quarterAlpha = qa, blurRadius = 40f, dark = dark)

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    ChatTopBar(
                        isNewChatMode = uiState.isNewChatMode,
                        activeProjectName = uiState.activeProjectName,
                        onExitProject = {
                            haptics.action()
                            viewModel.setActiveProject(null)
                            viewModel.createNewChat()
                        },
                        conversations = uiState.conversations,
                        currentConversationId = uiState.currentConversationId,
                        totalTokens = uiState.totalTokens,
                        onOpenDrawer = { haptics.action(); focusManager.clearFocus(); scope.launch { drawerState.open() } },
                        onSystemPromptClick = { haptics.action(); showPromptDialog = true },
                        onNewChat = {
                            haptics.action()
                            isExpanded = false
                            viewModel.createNewChat()
                            inputFocusRequester.requestFocus()
                        },
                        topBarBackgroundImagePath = uiState.topBarBackgroundImagePath,
                        topBarAlpha = uiState.topBarAlpha,
                        topBarCapsuleScale = uiState.topBarCapsuleScale,
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val topBarH = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
                    val pivotY = ((LocalConfiguration.current.screenHeightDp + topBarH.value / 2f - bottomBarHeight.value) / 2f).coerceAtLeast(0f) / LocalConfiguration.current.screenHeightDp
                    AnimatedContent(
                        targetState = Pair(uiState.isNewChatMode, showLaunchContent),
                        transitionSpec = {
                            val targetNewChat = targetState.first
                            val targetShowLaunch = targetState.second
                            val initialNewChat = initialState.first
                            val initialShowLaunch = initialState.second

                            if (targetNewChat && (targetShowLaunch != initialShowLaunch || targetNewChat != initialNewChat)) {
                                // Entering new-chat mode: scale+fade animation
                                val enterSpec = tween<Float>(700, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f))
                                val fadeInSpec = tween<Float>(500)
                                (fadeIn(animationSpec = fadeInSpec) + scaleIn(initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, pivotY), animationSpec = enterSpec))
                                    .togetherWith(fadeOut(animationSpec = tween(300)))
                            } else if (!targetNewChat && !initialNewChat) {
                                // Switching between existing uiState.conversations: no animation
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                // Returning from new-chat to an existing conversation
                                fadeIn(animationSpec = tween(300))
                                    .togetherWith(fadeOut(animationSpec = tween(300)))
                            }
                        },
                        label = "MainContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { (targetNewChat, targetShowLaunch) ->
                        if (!targetNewChat) {
                            val messageListModifier = if (uiState.blurEffectsEnabled) {
                                Modifier.fillMaxSize().gradientBlur(blurAtTopDp = 8f, blurAtBottomDp = 0f)
                            } else {
                                Modifier.fillMaxSize()
                            }
                            // While the switching overlay is up, hand MessageList an EMPTY list.
                            // The visible "freeze with no spinner" on chat open was because the
                            // heavy LazyColumn first-pass layout (lots of items + per-message
                            // height/branch bookkeeping) ran on the SAME frame as the spinner's
                            // fadeIn �� so the main thread was blocked and the spinner never got
                            // drawn, exactly when we needed it. Emptying the list during the
                            // switch makes that first frame cheap (only the spinner paints);
                            // the real list renders on the frame after uiState.isSwitching flips false.
                            val switchingToExisting = uiState.isSwitching && !uiState.isTransitioningToNewChat && !uiState.isBranchSwitching
                            if (switchingToExisting) com.orangeisland.app.util.DebugLog.d("MsgRender", "MessageList fed EMPTY (switchingToExisting): isSwitching=${uiState.isSwitching} isBranchSwitching=${uiState.isBranchSwitching} isTransitioning=${uiState.isTransitioningToNewChat}")
                            MessageList(
                                messages = if (switchingToExisting) emptyList() else uiState.messages,
                                allMessages = if (switchingToExisting) emptyList() else uiState.allMessages,
                                modifier = messageListModifier,
                                state = listState,
                                // Global generation gate: while ANY generation is in
                                // progress, all per-message actions (edit / delete /
                                // regenerate / branch-switch) are disabled. Bound to the
                                // global uiState.isLoading so there is no per-conversation timing
                                // window (uiState.generatingInConversationId is set asynchronously).
                                isLoading = uiState.isLoading,
                                isSwitching = uiState.isSwitching,
                                visualizeContextRollout = uiState.visualizeContextRollout,
                                showUsageStats = uiState.showUsageStats,
                                toolCallDisplayMode = uiState.toolCallDisplayMode,
                                maxContextWindow = uiState.contextWindow,
                                modelAliases = uiState.modelAliases,
                                customUserBubbleColor = uiState.customUserBubbleColor,
                                userBubbleBackgroundImagePath = uiState.userBubbleBackgroundImagePath,
                                userBubbleCornerRadiusOverride = uiState.userBubbleCornerRadius,
                                customAssistantBubbleColor = uiState.customAssistantBubbleColor,
                                customReasoningPanelColor = uiState.customReasoningPanelColor,
                                reasoningBackgroundImagePath = uiState.reasoningBackgroundImagePath,
                                customChatTextColor = uiState.customChatTextColor,
                                customGlobalTextColor = uiState.customGlobalTextColor,
                                messageBubbleAlpha = uiState.messageBubbleAlpha,
                                userBubbleMaskAlpha = uiState.userBubbleMaskAlpha,
                                reasoningPanelAlpha = uiState.reasoningPanelAlpha,
                                bottomBarHeight = bottomBarHeight,
                                viewportHeight = viewportHeightPx,
                                messageHeights = messageHeights,
                                onEditMessage = { id, text ->
                                    val isFirstMessage = uiState.messages.isEmpty()
                                    viewModel.editMessage(id, text)
                                    scope.launch {
                                        if (!isFirstMessage) {
                                            // An edit kicks off a fresh reply �� follow it to the bottom.
                                            stickToBottom.value = true
                                            delay(50)
                                            scrollToBottom(animate = true)
                                        }
                                    }
                                },
                                onEditAssistantMessage = { id, text -> viewModel.editAssistantMessage(id, text) },
                                onSwitchBranch = { parentId, currentMessageId, direction ->
                                    haptics.selection()
                                    viewModel.switchBranch(parentId, currentMessageId, direction)
                                },
                                onRegenerate = { id ->
                                    haptics.action()
                                    viewModel.regenerate(id)
                                    scope.launch {
                                        // Regeneration replaces the trailing reply; pin to bottom so the
                                        // new streaming reply stays in view.
                                        stickToBottom.value = true
                                        delay(50)
                                        scrollToBottom(animate = true)
                                    }
                                },
                                onDelete = { id -> viewModel.deleteMessage(id) },
                                onMediaClick = onMediaClick,
                                onFileContentClick = onFileContentClick,
                                onPdfPagesClick = { pages, idx -> haptics.action(); onPdfPagesClick?.invoke(pages, idx) },
                                onLoadOlderMessages = { viewModel.loadOlderMessages() },
                                thoughtExpandedStates = thoughtExpandedStates,
                                codeBlockWrapEnabled = uiState.codeBlockWrapEnabled,
                                splitBubbleByLine = uiState.splitBubbleByLine,
                                stickToBottomEnabled = true,
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = 140.dp,
                                    bottom = bottomBarHeight + 8.dp
                                )
                            )
                        } else if (targetShowLaunch) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = bottomBarHeight),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Watercolor orange boat + welcome text, grouped so they sit
                                    // together regardless of screen height (UI Playground v0.4 empty
                                    // state). The boat is decorative and non-interactive.
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(top = 40.dp, start = 24.dp, end = 24.dp)
                                    ) {
                                        DecorativeImage(
                                            res = com.orangeisland.app.R.drawable.island_deco_boat,
                                            width = 220.dp,
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        TypewriterText(
                                            text = uiState.activeProjectName?.let { stringResource(R.string.welcome_to_project, it) }
                                                ?: stringResource(R.string.welcome_to_orange_island),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }

                    val showButton by remember {
                        derivedStateOf {
                            if (uiState.isNewChatMode) false
                            else {
                                val info = listState.layoutInfo
                                val total = info.totalItemsCount
                                total > 1 && info.visibleItemsInfo.none { it.index == total - 2 }
                            }
                        }
                    }

                    val fabElevation by animateDpAsState(
                        targetValue = if (showButton) 4.dp else 0.dp,
                        animationSpec = tween(400)
                    )

                    AnimatedVisibility(
                        visible = showButton,
                        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.6f, animationSpec = tween(400)),
                        exit = fadeOut(tween(400)) + scaleOut(targetScale = 0.6f, animationSpec = tween(400)),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBarHeight + 8.dp)
                    ) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            FloatingActionButton(onClick = { scope.launch { stickToBottom.value = true; scrollToBottom(animate = true) } }, containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp), contentColor = MaterialTheme.colorScheme.onSurface, shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(fabElevation), modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.scroll_to_bottom), modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // AI camera capture overlay: floats above the input bar while take_photo is
                    // pending. Independent of message-list rendering so it always shows up.
                    val cameraGate = viewModel.cameraToolGate
                    if (cameraGate != null) {
                        val cameraPending by cameraGate.pending.collectAsState()
                        val cameraRequest = cameraPending.firstOrNull()
                        AnimatedVisibility(
                            visible = cameraRequest != null,
                            enter = fadeIn(tween(200)) + slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(250)
                            ),
                            exit = fadeOut(tween(200)) + slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(200)
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomBarHeight + 12.dp, start = 16.dp, end = 16.dp)
                        ) {
                            if (cameraRequest != null) {
                                com.orangeisland.app.ui.chat.CameraMiniCapture(
                                    gate = cameraGate,
                                    request = cameraRequest,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.isSwitching && !uiState.isTransitioningToNewChat,
                        enter = fadeIn(animationSpec = tween(60)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            val gradientTopPaddingPx = with(density) { 20.dp.toPx() }
            val gradientWidthPx = with(density) { 40.dp.toPx() }
            val bgColor = MaterialTheme.colorScheme.background
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(if (isExpanded) Modifier.fillMaxHeight().statusBarsPadding() else Modifier)
                    .drawBehind {
                        val totalH = size.height
                        if (totalH > 0f) {
                            val (transparentEnd, fadeEnd) = if (isExpanded) {
                                // In expanded mode, keep the gradient compact at the top
                                val h = gradientTopPaddingPx.coerceAtMost(totalH * 0.12f)
                                val w = gradientWidthPx.coerceAtMost(totalH * 0.24f)
                                (h / totalH) to ((h + w) / totalH)
                            } else {
                                val te = (gradientTopPaddingPx / totalH).coerceIn(0f, 1f)
                                val fe = ((gradientTopPaddingPx + gradientWidthPx) / totalH).coerceIn(0f, 1f)
                                te to fe
                            }
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        transparentEnd to Color.Transparent,
                                        fadeEnd to bgColor,
                                    ),
                                    startY = 0f,
                                    endY = totalH
                                )
                            )
                        }
                    },
                color = Color.Transparent
            ) {
                Column {
                    if (outerSpacerHeightPx > 0f) {
                        Spacer(modifier = Modifier.height(with(density) { outerSpacerHeightPx.toDp() }))
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isExpanded) Modifier.fillMaxHeight() else Modifier)
                            .onSizeChanged {
                            if (!isExpanded) bottomBarHeightPx = it.height.toFloat()
                        }
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(8.dp),
                    color = if (uiState.inputBackgroundImagePath.isNotBlank()) {
                        Color.Transparent
                    } else {
                        uiState.customInputFieldColor?.let { com.orangeisland.app.ui.components.ColorMath.argbToColor(it) } ?: MaterialTheme.colorScheme.surface
                    },
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (uiState.inputBackgroundImagePath.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = uiState.inputBackgroundImagePath,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                            // Mandatory scrim -- input text must stay legible over any photo.
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                            )
                        }
                        ChatBottomBar(
                        onSendMessage = { text, attachments ->
                            viewModel.sendMessage(text, attachments = attachments).also { sent ->
                                if (sent) {
                                    haptics.action()
                                    // Scrolling is handled by the viewModel.scrollToMessage flow collector
                                    // above (triggered internally by sendMessage() via onScrollToMessage),
                                    // which waits for the target message to actually render before
                                    // animating. A second manual scroll call here used to race against it
                                    // and cause a visible double-jump.
                                }
                            }
                        },
                        onStopGeneration = {
                            haptics.generationStopped()
                            viewModel.stopGeneration()
                        },
                        isLoading = uiState.isLoading,
                        isSwitching = uiState.isSwitching,
                        enabledModels = uiState.enabledModels,
                        selectedModel = uiState.selectedModel,
                        modelAliases = uiState.modelAliases,
                        codeExecutionEnabled = uiState.codeExecutionEnabled,
                        googleSearchEnabled = uiState.googleSearchEnabled,
                        thinkingEnabled = uiState.thinkingEnabled,
                        thinkingLevel = uiState.thinkingLevel,
                        thinkingBudgetEnabled = uiState.thinkingBudgetEnabled,
                        thinkingBudgetTokens = uiState.thinkingBudgetTokens,
                        onCodeExecutionToggle = { enabled -> haptics.selection(); viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(codeExecutionEnabled = enabled) } },
                        onGoogleSearchToggle = { enabled -> haptics.selection(); viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(googleSearchEnabled = enabled) } },
                        onThinkingToggle = { enabled -> haptics.selection(); viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(thinkingEnabled = enabled) } },
                        onThinkingLevelChange = { level -> viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(thinkingLevel = level) } },
                        onThinkingBudgetEnabledChange = { enabled -> viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(thinkingBudgetEnabled = enabled) } },
                        onThinkingBudgetTokensChange = { tokens -> viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(thinkingBudgetTokens = tokens) } },
                        webSearchEnabled = uiState.webSearchEnabled,
                        onWebSearchToggle = { enabled -> haptics.selection(); viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(webSearchEnabled = enabled) } },
                        shellEnabled = uiState.shellEnabled,
                        onShellToggle = { enabled -> haptics.selection(); viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(shellEnabled = enabled) } },
                        videoNarrationEnabled = uiState.videoNarrationEnabled,
                        onVideoNarrationToggle = { enabled -> haptics.selection(); viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(videoNarrationEnabled = enabled) } },
                        onModelSelect = { haptics.selection(); viewModel.setActiveModel(it) },
                        onImageClick = { url -> haptics.action(); onMediaClick(listOf(url), 0) },
                        onAllMediaClick = { urls, idx -> haptics.action(); onMediaClick(urls, idx) },
                        onFileContentClick = { name, content -> haptics.action(); viewModel.showFilePreview(name, content) },
                        modifier = Modifier,
                        textFieldState = textFieldState,
                        focusRequester = inputFocusRequester,
                        isExpanded = isExpanded,
                        isExpandAnimating = isExpandAnimating,
                        onCollapse = { haptics.action(); isExpanded = false },
                        onExpand = { haptics.action(); isExpanded = true },
                        showWebSearch = uiState.globalWebSearch,
                        showShell = uiState.shellDevices.isNotEmpty() && uiState.globalShell,
                        onPdfPagesClick = { pages, idx -> haptics.action(); onPdfPagesClick?.invoke(pages, idx) },
                        onPdfPreviewSelect = { pages, idx -> haptics.action(); onPdfPreviewSelect?.invoke(pages, idx) },
                        pdfViewerSelection = pdfViewerSelection,
                        onTogglePdfSelection = onTogglePdfSelection,
                        onInitPdfSelection = onInitPdfSelection,
                        fullScreenViewerUrls = fullScreenViewerUrls,
                        onAdvancedClick = { showAdvancedDialog = true },
                        showMcpEntry = uiState.mcpServers.any { it.enabled },
                        mcpConversationActive = uiState.mcpServerIds?.isNotEmpty() == true,
                        onMcpClick = { haptics.action(); showMcpSheet = true },
                        onInputFocusChanged = { focused ->
                            if (focused && isAtBottom && !uiState.isNewChatMode) {
                                scope.launch {
                                    if (uiState.messages.isNotEmpty()) {
                                        listState.animateScrollToItem(uiState.messages.lastIndex, 0)
                                    }
                                }
                            }
                        }
                    )
                }
            }
            }
        }
        }
    }
    }

    showRenameDialog?.let { id ->
        ChatRenameDialog(
            initialName = conversationToRename,
            onSave = { newName ->
                viewModel.renameConversation(id, newName)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    showDeleteConfirmDialog?.let { id ->
        ChatDeleteConfirmDialog(
            onConfirm = {
                haptics.reject()
                viewModel.deleteConversation(id)
                showDeleteConfirmDialog = null
            },
            onDismiss = { showDeleteConfirmDialog = null }
        )
    }

    // ── Project dialogs ──────────────────────────────────────
    // The model/prompt option lists are built from the same settings flows used by the
    // per-conversation dialogs, so "project default" and "chat override" see the same set.
        // Project pickers use the global settings defaults from the single UI state.
val projectModelOptions = remember(uiState.enabledModels, uiState.modelAliases) {
        uiState.enabledModels.toList().map { id ->
            id to (uiState.modelAliases[id]?.ifBlank { null } ?: id.substringAfter(":", id))
        }
    }
    val projectPromptOptions = remember(uiState.systemPrompts) {
        uiState.systemPrompts.map { it.id to it.title }
    }
    val globalDefaultPromptTitle = uiState.systemPrompts.find { it.id == uiState.activeSystemPromptId }?.title
        ?: stringResource(R.string.no_system_prompt)
    val globalDefaultModelTitle = remember(uiState.globalSelectedModel, uiState.modelAliases) {
        uiState.modelAliases[uiState.globalSelectedModel]?.ifBlank { null } ?: uiState.globalSelectedModel.substringAfter(":", uiState.globalSelectedModel)
    }

    if (showCreateProjectDialog) {
        ProjectCreateDialog(
            availableModels = projectModelOptions,
            promptList = projectPromptOptions,
            globalDefaultPromptTitle = globalDefaultPromptTitle,
            globalDefaultModelTitle = globalDefaultModelTitle,
            onConfirm = { name, modelId, promptId ->
                viewModel.createProject(name, modelId, promptId)
                showCreateProjectDialog = false
            },
            onDismiss = { showCreateProjectDialog = false }
        )
    }

    projectToRename?.let { (id, name) ->
        ProjectRenameDialog(
            initialName = name,
            onSave = { newName ->
                viewModel.renameProject(id, newName)
                projectToRename = null
            },
            onDismiss = { projectToRename = null }
        )
    }

    // ── Project settings: full-screen sub-page (slide-in overlay) ──
    // Replaces the old single AlertDialog. Renders as an overlay on top of chat so the
    // look & feel matches the main Settings page (CollapsingSettingsScaffold + SettingsGroup).
    val projectSettingsVisible = projectToSettings != null
    androidx.compose.animation.AnimatedVisibility(
        visible = projectSettingsVisible,
        enter = androidx.compose.animation.slideInHorizontally(
            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            initialOffsetX = { it }
        ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.slideOutHorizontally(
            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            targetOffsetX = { it }
        ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
    ) {
        val id = projectToSettings
        val project = id?.let { uiState.projects.find { p -> p.id == it } }
        if (id != null && project != null) {
            // Refresh project memory files whenever the dialog is open for this project.
            // Simplest correct hook: re-fetch on (re)composition boundary via remember +
            // LaunchedEffect keyed on the project id, so create/edit/delete callbacks can refresh
            // by bumping a local counter.
            var memoryRefreshKey by remember { mutableStateOf(0) }
            var projectMemoryFiles by remember(id) {
                mutableStateOf<List<com.orangeisland.app.data.MemoryManager.MemoryFileInfo>>(emptyList())
            }
            LaunchedEffect(id, memoryRefreshKey) {
                projectMemoryFiles = viewModel.listProjectMemoryFiles(id)
            }
            val conversationsInProject = remember(uiState.conversations, id) {
                uiState.conversations.filter { it.projectId == id }
            }
            ProjectSettingsScreen(
                projectId = id,
                projectName = project.name,
                initialModelId = project.modelId,
                initialPromptId = project.systemPromptId,
                availableModels = projectModelOptions,
                promptList = projectPromptOptions,
                globalDefaultPromptTitle = globalDefaultPromptTitle,
                globalDefaultModelTitle = globalDefaultModelTitle,
                memoryFiles = projectMemoryFiles,
                conversationsInProject = conversationsInProject,
                onCreateMemoryFile = { name, content, desc ->
                    viewModel.createProjectMemoryFile(id, name, content, desc)
                    memoryRefreshKey++  // trigger reload
                },
                onDeleteMemoryFile = { name ->
                    viewModel.deleteProjectMemoryFile(id, name)
                    memoryRefreshKey++  // trigger reload
                },
                onEditMemoryFile = { name, newContent, newDesc ->
                    viewModel.editProjectMemoryFile(id, name, newContent, newDesc)
                    memoryRefreshKey++  // trigger reload
                },
                onSave = { name, modelId, promptId ->
                    if (name != project.name) viewModel.renameProject(id, name)
                    viewModel.setProjectDefaults(id, promptId, modelId)
                },
                onOpenConversation = { convId ->
                    viewModel.selectConversation(convId)
                    projectToSettings = null
                },
                onCreateChatInProject = {
                    viewModel.setActiveProject(id)
                    viewModel.createNewChat()
                    projectToSettings = null
                },
                onDeleteProject = {
                    projectToDelete = id to project.name
                    projectToSettings = null
                },
                onBack = { projectToSettings = null }
            )
        }
    }

    projectToDelete?.let { (id, name) ->
        ProjectDeleteConfirmDialog(
            projectName = name,
            onConfirm = {
                haptics.reject()
                viewModel.deleteProject(id)
                projectToDelete = null
            },
            onDismiss = { projectToDelete = null }
        )
    }

    if (showPromptDialog) {
        ChatSystemPromptDialog(viewModel = viewModel, onDismiss = { showPromptDialog = false })
    }

    if (showAdvancedDialog) {
        ChatAdvancedSettingsDialog(viewModel = viewModel, onDismiss = { showAdvancedDialog = false })
    }

    if (showMcpSheet) {
        McpConversationSheet(
            servers = uiState.mcpServers.filter { it.enabled },
            selectedIds = uiState.mcpServerIds,
            onInherit = {
                viewModel.updateConversationSetting(uiState.currentConversationId) { it.copy(mcpServerIds = null) }
            },
            onToggle = { id, on ->
                viewModel.updateConversationSetting(uiState.currentConversationId) { settings ->
                    // First toggle in inherit-mode seeds the explicit list with every enabled
                    // server (so unchecking one doesn't silently drop all the others); subsequent
                    // toggles just add/remove the one id.
                    val seed = settings.mcpServerIds
                        ?: uiState.mcpServers.filter { it.enabled }.map { s -> s.id }
                    val next = if (on) (seed + id).distinct() else seed - id
                    settings.copy(mcpServerIds = next)
                }
            },
            onDismiss = { showMcpSheet = false }
        )
    }
}

