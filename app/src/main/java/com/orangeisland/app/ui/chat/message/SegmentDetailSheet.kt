package com.orangeisland.app.ui.chat.message

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.orangeisland.app.ui.components.DialogWindowEdgeToEdge
import com.orangeisland.app.ui.components.ColorMath
import com.orangeisland.app.R
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.ui.theme.ChatType
import com.orangeisland.app.util.noOpBringIntoView
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponents
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Segment detail bottom sheet (custom implementation).
//
// A self-contained draggable bottom sheet with its own finite-state machine
// (Collapsed / Half / Full) driving an Animatable fraction; the whole gesture +
// snap + dim subsystem lives here. The host (MessageItem) only decides WHICH
// segment(s) to show and toggles visibility via [onDismiss].
@Composable
internal fun SegmentDetailSheet(
    message: ChatMessage,
    selectedSegmentIndex: Int,
    selectedSegmentIndices: List<Int>,
    isStreaming: Boolean,
    markdownColors: MarkdownColors,
    thoughtTypography: MarkdownTypography,
    thoughtMarkdownPadding: MarkdownPadding,
    markdownComponents: MarkdownComponents,
    markdownFlavour: MarkdownFlavourDescriptor,
    imageTransformer: ImageTransformer,
    customReasoningPanelColor: Long? = null,
    reasoningPanelAlpha: Float = 1f,
    reasoningBackgroundImagePath: String = "",
    onDismiss: () -> Unit
) {
    val liveSegs = remember(message.segments) {
        mergeAdjacentSegments(message.segments.orEmpty()).filter { it.type != "answer" }
    }
    val selectedSegs = remember(liveSegs, selectedSegmentIndices, selectedSegmentIndex) {
        selectedSegmentIndices.mapNotNull { liveSegs.getOrNull(it) }
            .ifEmpty { liveSegs.getOrNull(selectedSegmentIndex)?.let { listOf(it) }.orEmpty() }
    }
    val seg = selectedSegs.firstOrNull()
    if (seg == null) {
        onDismiss()
    } else {
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        val PARTIAL = 0.45f
        val FULL = 0.94f

        // ── Finite state machine ──
        // Collapsed = 0, Half = PARTIAL, Full = FULL
        // Full is only entered when animateTo(FULL) completes naturally.
        val PHASE_COLLAPSED = 0; val PHASE_HALF = 1; val PHASE_FULL = 2
        var phase by remember { mutableIntStateOf(PHASE_HALF) }

        var rawFraction by remember { mutableFloatStateOf(0f) }
        val visualFraction = remember { Animatable(0f) }
        var snapJob by remember { mutableStateOf<Job?>(null) }
        var dismissing by remember { mutableStateOf(false) }

        val snapSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 350f, visibilityThreshold = 0.001f)

        // ── Snap target: midline (0.5) × velocity direction ──
        // velSign > 0 = upward (expanding), velSign < 0 = downward (collapsing)
        fun snapTarget(pos: Float, velSign: Float): Float {
            val goingUp = velSign >= 0f
            return when {
                pos > 0.5f && goingUp -> FULL      // upper half + up → full
                pos > 0.5f && !goingUp -> PARTIAL  // upper half + down → half
                pos <= 0.5f && goingUp -> PARTIAL  // lower half + up → half
                else -> 0f                          // lower half + down → collapsed
            }
        }

        // ── Single animation entry point. Sets phase after animation completes. ──
        fun animateTo(target: Float) {
            snapJob?.cancel()
            snapJob = coroutineScope.launch {
                visualFraction.animateTo(target, snapSpring)
                rawFraction = visualFraction.value
                phase = when (target) {
                    FULL -> PHASE_FULL
                    PARTIAL -> PHASE_HALF
                    else -> PHASE_COLLAPSED
                }
                if (target == 0f) onDismiss()
            }
        }

        fun dismiss() { dismissing = true; animateTo(0f) }

        // ── Grab: interrupt animation, sync raw to current visual position ──
        fun grabSheet() {
            if (dismissing) return
            if (snapJob?.isActive == true) {
                snapJob?.cancel()
                rawFraction = visualFraction.value
            }
        }

        // ── Initial appearance ──
        LaunchedEffect(Unit) {
            animateTo(PARTIAL)
            snapJob?.join()
            rawFraction = PARTIAL
        }

        // ── Safety-net snap: if drag ends without fling (velocity ≈ 0) ──
        LaunchedEffect(rawFraction) {
            if (dismissing || snapJob?.isActive == true) return@LaunchedEffect
            val pos = rawFraction
            delay(80)
            if (dismissing || pos != rawFraction || snapJob?.isActive == true) return@LaunchedEffect
            val target = snapTarget(pos, 0f)
            if (abs(target - pos) > 0.01f) animateTo(target)
        }

        // ── Dim: per-frame poll of visualFraction → native Window.dimAmount ──
        val dialogWindowRef = remember { mutableStateOf<android.view.Window?>(null) }

        LaunchedEffect(dialogWindowRef.value) {
            val window = dialogWindowRef.value ?: return@LaunchedEffect
            while (isActive) {
                window.attributes = window.attributes.also {
                    it.dimAmount = (0.32f * visualFraction.value).coerceIn(0f, 1f)
                }
                withFrameNanos { }
            }
        }

        // ── NestedScrollConnection ──
        // Half: content does NOT scroll — all delta goes to sheet expansion.
        // Full: content scrolls normally. Exit Full ONLY when content at top
        //       and finger still dragging down (source == Drag).
        val sheetScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!dismissing && phase != PHASE_FULL) {
                        grabSheet()
                        val delta = -available.y / screenHeightPx
                        rawFraction = (rawFraction + delta).coerceIn(0f, FULL)
                        coroutineScope.launch { visualFraction.snapTo(rawFraction) }
                        if (rawFraction >= FULL && available.y < 0f) phase = PHASE_FULL
                        return available.copy(x = 0f)
                    }
                    return Offset.Zero // Full: let content scroll
                }

                override fun onPostScroll(
                    consumed: Offset, available: Offset, source: NestedScrollSource
                ): Offset {
                    if (dismissing) return Offset.Zero
                    // Exit Full → Half: content at top + finger dragging down
                    if (phase == PHASE_FULL
                        && available.y > 0f
                        && scrollState.value == 0
                        && source == NestedScrollSource.UserInput
                    ) {
                        phase = PHASE_HALF
                        val delta = -available.y / screenHeightPx
                        rawFraction = (FULL + delta).coerceIn(0f, FULL)
                        coroutineScope.launch { visualFraction.snapTo(rawFraction) }
                        return available.copy(x = 0f)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (phase != PHASE_FULL && available.y != 0f) {
                        val velSign = if (available.y < 0f) 1f else -1f
                        animateTo(snapTarget(rawFraction, velSign))
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Dialog(
            onDismissRequest = { dismiss() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false
            )
        ) {
            DialogWindowEdgeToEdge()
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { dialogWindowRef.value = dialogWindow }

            Box(modifier = Modifier.fillMaxSize()) {
                // Transparent click-catcher — dim is handled by native Window.dimAmount.
                // Uses pointerInput to avoid reading visualFraction in composition.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (visualFraction.value > 0.02f) dismiss()
                                }
                            )
                        }
                )

                // Sheet height via Modifier.layout (layout phase) to avoid
                // recomposition on every spring animation frame.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val h = (screenHeightPx * visualFraction.value).roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = h, maxHeight = h)
                            )
                            layout(placeable.width, h) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                ) {
                val reasoningBaseColor = customReasoningPanelColor?.let { ColorMath.argbToColor(it) }
                    ?: MaterialTheme.colorScheme.surfaceContainer
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    shadowElevation = 8.dp,
                    color = reasoningBaseColor
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (reasoningBackgroundImagePath.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = reasoningBackgroundImagePath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(reasoningBaseColor.copy(alpha = reasoningPanelAlpha))
                            )
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                        // Draggable header: drag handle + title + divider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    var velEma = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            if (dismissing) return@detectVerticalDragGestures
                                            velEma = 0f
                                            grabSheet()
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (dismissing) return@detectVerticalDragGestures
                                            change.consume()
                                            velEma = velEma * 0.5f + (-dragAmount).coerceIn(-1f, 1f) * 0.5f
                                            rawFraction = (rawFraction - dragAmount / screenHeightPx)
                                                .coerceIn(0f, FULL)
                                            coroutineScope.launch { visualFraction.snapTo(rawFraction) }
                                            if (rawFraction >= FULL && dragAmount < 0f) phase = PHASE_FULL
                                        },
                                        onDragEnd = {
                                            if (dismissing) return@detectVerticalDragGestures
                                            animateTo(snapTarget(rawFraction, velEma))
                                        }
                                    )
                                }
                        ) {
                            // Drag handle
                            Box(
                                modifier = Modifier.fillMaxWidth().height(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp).height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                )
                            }

                            // Fixed title
                            Text(
                                text = if (selectedSegs.size > 1) compactSegmentTitle(selectedSegs, message, useLiveStatus = false)
                                    else if (seg.type == "tool") toolDisplayName(seg.toolName)
                                    else if (seg.type == "transcription") transcriptionLabel(liveSegs, selectedSegmentIndex)
                                    else stringResource(R.string.tool_thinking),
                                style = ChatType.detailTitle,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }

                        // Scrollable detail content
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(sheetScrollConnection)
                                .verticalScroll(scrollState)
                                .noOpBringIntoView()
                                .padding(horizontal = 24.dp)
                                // Markdown content (thinking, transcription) hugs its title with a
                                // unified 4dp; tool detail leads with an Arguments/Result label, so it
                                // gets a slightly larger 6dp.
                                .padding(top = if (seg.type == "tool") 6.dp else 4.dp)
                                .navigationBarsPadding()
                                .padding(bottom = 32.dp)
                        ) {
                            if (selectedSegs.size > 1) {
                                selectedSegs.forEachIndexed { index, detailSeg ->
                                    val detailIndex = selectedSegmentIndices.getOrNull(index)
                                        ?: liveSegs.indexOf(detailSeg).coerceAtLeast(0)
                                    Text(
                                        segmentDetailTitle(detailSeg, liveSegs, detailIndex),
                                        style = ChatType.detailTitle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 18.dp, bottom = 8.dp)
                                    )
                                    if (detailSeg.type == "tool") {
                                        ToolDetailContent(detailSeg)
                                    } else if (detailSeg.type == "transcription" && detailSeg.content.isBlank()) {
                                        Text(
                                            text = "Image transcription is empty.",
                                            style = ChatType.body,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    } else {
                                        SelectionContainer {
                                            RecomposeSafeMarkdown(
                                                content = detailSeg.content,
                                                isStreaming = isStreaming && index == selectedSegs.lastIndex
                                            ) { text ->
                                                val markdownParser = remember(text) { MarkdownParser(markdownFlavour) }
                                                val referenceLinkHandler = remember(text) { ReferenceLinkHandlerImpl() }
                                                Markdown(
                                                    content = text.escapeForMarkdown(),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = markdownColors,
                                                    typography = thoughtTypography,
                                                    padding = thoughtMarkdownPadding,
                                                    components = markdownComponents,
                                                    imageTransformer = imageTransformer,
                                                    flavour = markdownFlavour,
                                                    parser = markdownParser,
                                                    referenceLinkHandler = referenceLinkHandler,
                                                    animations = markdownAnimations { this }
                                                )
                                            }
                                        }
                                    }
                                    if (index < selectedSegs.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 18.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            } else if (seg.type == "tool") {
                                ToolDetailContent(seg)
                            } else if (seg.type == "transcription" && seg.content.isBlank()) {
                                Text(
                                    text = "Image transcription is empty.",
                                    style = ChatType.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            } else {
                                var debouncedThoughtContent by remember { mutableStateOf(seg.content) }
                                if (!isStreaming) {
                                    debouncedThoughtContent = seg.content
                                } else {
                                    var lastUpdateMs by remember { mutableLongStateOf(0L) }
                                    var flushJob by remember { mutableStateOf<Job?>(null) }
                                    LaunchedEffect(seg.content) {
                                        val now = System.currentTimeMillis()
                                        val elapsed = now - lastUpdateMs
                                        if (elapsed >= 500) {
                                            flushJob?.cancel()
                                            debouncedThoughtContent = seg.content
                                            lastUpdateMs = now
                                        } else {
                                            flushJob?.cancel()
                                            flushJob = launch {
                                                delay(500 - elapsed)
                                                debouncedThoughtContent = seg.content
                                                lastUpdateMs = System.currentTimeMillis()
                                            }
                                        }
                                    }
                                }
                                if (!isStreaming) {
                                    SelectionContainer {
                                        RecomposeSafeMarkdown(
                                            content = debouncedThoughtContent,
                                            isStreaming = isStreaming
                                        ) { text ->
                                            val markdownParser = remember(text) { MarkdownParser(markdownFlavour) }
                                            val referenceLinkHandler = remember(text) { ReferenceLinkHandlerImpl() }
                                            Markdown(
                                                content = text.escapeForMarkdown(),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = markdownColors,
                                                typography = thoughtTypography,
                                                padding = thoughtMarkdownPadding,
                                                components = markdownComponents,
                                                imageTransformer = imageTransformer,
                                                flavour = markdownFlavour,
                                                parser = markdownParser,
                                                referenceLinkHandler = referenceLinkHandler,
                                                animations = markdownAnimations { this }
                                            )
                                        }
                                    }
                                } else {
                                    RecomposeSafeMarkdown(
                                        content = debouncedThoughtContent,
                                        isStreaming = isStreaming
                                    ) { text ->
                                        val markdownParser = remember(text) { MarkdownParser(markdownFlavour) }
                                        val referenceLinkHandler = remember(text) { ReferenceLinkHandlerImpl() }
                                        Markdown(
                                            content = text.escapeForMarkdown(),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = markdownColors,
                                            typography = thoughtTypography,
                                            padding = thoughtMarkdownPadding,
                                            components = markdownComponents,
                                            imageTransformer = imageTransformer,
                                            flavour = markdownFlavour,
                                            parser = markdownParser,
                                            referenceLinkHandler = referenceLinkHandler,
                                            animations = markdownAnimations { this }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                }
            }
        }
    }
}
