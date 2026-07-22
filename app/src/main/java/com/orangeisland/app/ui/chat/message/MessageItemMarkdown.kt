package com.orangeisland.app.ui.chat.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert

import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.orangeisland.app.R
import com.orangeisland.app.util.noOpBringIntoView
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.ToolCallDisplayModes
import com.orangeisland.app.ui.common.LocalOrangeIslandHaptics
import com.orangeisland.app.ui.theme.MonoFamily
import com.orangeisland.app.ui.theme.ChatType
import com.orangeisland.app.ui.components.*
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

// ── Streaming Markdown rendering (extracted from MessageItem.kt) ──────────────
// Pure code-motion: these were `private` members of MessageItem.kt; entry points
// used by MessageItem.kt / the timeline section are `internal`. Behavior unchanged.

private const val STABLE_MARKDOWN_PROMOTION_BATCH_SIZE = 8

@Stable
internal class ChatMarkdownRenderContext(
    val colors: MarkdownColors,
    val typography: MarkdownTypography,
    val padding: MarkdownPadding,
    val components: MarkdownComponents,
    val imageTransformer: ImageTransformer,
    val flavour: MarkdownFlavourDescriptor,
)

@Stable
internal class StableMarkdownBlock(
    val startOffset: Int,
    val endOffset: Int,
    val contentHash: Int,
    val node: ASTNode,
    val sourceContent: String,
) {
    fun hasSameIdentity(other: StableMarkdownBlock): Boolean =
        startOffset == other.startOffset &&
            endOffset == other.endOffset &&
            contentHash == other.contentHash &&
            node.type == other.node.type
}

@Stable
internal data class StreamingMarkdownNode(
    val startOffset: Int,
    val endOffset: Int,
    val contentHash: Int,
    val node: ASTNode,
    val sourceContent: String,
)

@Stable
internal class StreamingMarkdownBlocksState(initialTail: String) {
    val stableBlocks: SnapshotStateList<StableMarkdownBlock> = mutableStateListOf()
    var tailNode by mutableStateOf<StreamingMarkdownNode?>(null)
    var fallbackTail by mutableStateOf(initialTail)
}

@Immutable
private data class ParsedStreamingMarkdownBlocks(
    val stableBlocks: List<StableMarkdownBlock>,
    val tailNode: StreamingMarkdownNode?,
    val fallbackTail: String,
)

@Composable
internal fun rememberStreamingMarkdownBlocks(
    content: String,
    flavour: MarkdownFlavourDescriptor,
    active: Boolean
): StreamingMarkdownBlocksState {
    val state = remember { StreamingMarkdownBlocksState("") }

    LaunchedEffect(content, flavour, active) {
        if (!active) return@LaunchedEffect
        val parsed = withContext(Dispatchers.Default) {
            val prepared = content.toRenderableMarkdownText()
            splitStreamingMarkdownBlocks(prepared, flavour)
        }
        state.applyParsed(parsed)
    }

    return state
}

private suspend fun StreamingMarkdownBlocksState.applyParsed(parsed: ParsedStreamingMarkdownBlocks) {
    val existing = stableBlocks
    val parsedBlocks = parsed.stableBlocks
    val canAppend = existing.size <= parsedBlocks.size &&
        existing.indices.all { index -> existing[index].hasSameIdentity(parsedBlocks[index]) }

    if (!canAppend) {
        existing.clear()
        appendStableBlocksInBatches(parsedBlocks, startIndex = 0)
    } else if (parsedBlocks.size > existing.size) {
        appendStableBlocksInBatches(parsedBlocks, startIndex = existing.size)
    }
    tailNode = parsed.tailNode
    fallbackTail = parsed.fallbackTail
}

private suspend fun StreamingMarkdownBlocksState.appendStableBlocksInBatches(
    parsedBlocks: List<StableMarkdownBlock>,
    startIndex: Int
) {
    var index = startIndex
    while (index < parsedBlocks.size) {
        val end = minOf(index + STABLE_MARKDOWN_PROMOTION_BATCH_SIZE, parsedBlocks.size)
        stableBlocks.addAll(parsedBlocks.subList(index, end))
        index = end
        if (index < parsedBlocks.size) {
            withFrameNanos { }
        }
    }
}

private fun splitStreamingMarkdownBlocks(
    content: String,
    flavour: MarkdownFlavourDescriptor
): ParsedStreamingMarkdownBlocks {
    if (content.isBlank()) return ParsedStreamingMarkdownBlocks(emptyList(), null, content)

    return runCatching {
        val root = MarkdownParser(flavour).buildMarkdownTreeFromString(content)
        val children = root.children
        val nonBlankChildren = children.withIndex().filter { indexed ->
            val node = indexed.value
            val start = node.startOffset.coerceIn(0, content.length)
            val end = node.endOffset.coerceIn(start, content.length)
            content.substring(start, end).isNotBlank()
        }
        if (nonBlankChildren.isEmpty()) {
            ParsedStreamingMarkdownBlocks(emptyList(), null, content)
        } else {
            val tailIndex = nonBlankChildren.last().index
            val stableNodes = children.take(tailIndex)
            val blocks = stableNodes.map { node ->
                node.toStableMarkdownBlock(content)
            }
            val tailNode = children.getOrNull(tailIndex)?.toStreamingMarkdownNode(content)
            val tailStart = tailNode?.startOffset?.coerceIn(0, content.length) ?: 0
            ParsedStreamingMarkdownBlocks(blocks, tailNode, content.substring(tailStart))
        }
    }.getOrElse {
        ParsedStreamingMarkdownBlocks(emptyList(), null, content)
    }
}

private fun ASTNode.toStableMarkdownBlock(sourceContent: String): StableMarkdownBlock {
    val start = startOffset.coerceIn(0, sourceContent.length)
    val end = endOffset.coerceIn(start, sourceContent.length)
    return StableMarkdownBlock(
        startOffset = start,
        endOffset = end,
        contentHash = sourceContent.substring(start, end).hashCode(),
        node = this,
        sourceContent = sourceContent
    )
}

private fun ASTNode.toStreamingMarkdownNode(sourceContent: String): StreamingMarkdownNode {
    val start = startOffset.coerceIn(0, sourceContent.length)
    val end = endOffset.coerceIn(start, sourceContent.length)
    return StreamingMarkdownNode(
        startOffset = start,
        endOffset = end,
        contentHash = sourceContent.substring(start, end).hashCode(),
        node = this,
        sourceContent = sourceContent
    )
}

@Composable
internal fun StreamingMarkdownBlockContent(
    blocks: StreamingMarkdownBlocksState,
    renderContext: ChatMarkdownRenderContext,
    modifier: Modifier = Modifier,
    tailIsStreaming: Boolean = false
) {
    Column(modifier = modifier) {
        StableMarkdownBlockList(
            blocks = blocks.stableBlocks,
            renderContext = renderContext
        )
        val tailNode = blocks.tailNode
        if (tailNode != null) {
            RecomposeSafeMarkdownNode(
                content = tailNode,
                isStreaming = tailIsStreaming
            ) { node ->
                MarkdownNodeContent(node, renderContext)
            }
        } else if (blocks.fallbackTail.isNotBlank()) {
            RecomposeSafeMarkdown(
                content = blocks.fallbackTail,
                isStreaming = tailIsStreaming
            ) { text ->
                MarkdownPreparedTextContent(text, renderContext)
            }
        }
    }
}

@Composable
private fun StableMarkdownBlockList(
    blocks: SnapshotStateList<StableMarkdownBlock>,
    renderContext: ChatMarkdownRenderContext
) {
    blocks.forEach { block ->
        key(block.startOffset) {
            StableMarkdownBlockItem(block, renderContext)
        }
    }
}

@Composable
private fun StableMarkdownBlockItem(
    block: StableMarkdownBlock,
    renderContext: ChatMarkdownRenderContext
) {
    MarkdownBlockContent(block, renderContext)
}

@Composable
private fun MarkdownBlockContent(
    block: StableMarkdownBlock,
    renderContext: ChatMarkdownRenderContext
) {
    val state = remember(block) {
        State.Success(
            node = block.node,
            content = block.sourceContent,
            linksLookedUp = false,
            referenceLinkHandler = ReferenceLinkHandlerImpl()
        )
    }
    MarkdownNodeStateContent(state, renderContext)
}

@Composable
private fun MarkdownNodeContent(
    node: StreamingMarkdownNode,
    renderContext: ChatMarkdownRenderContext
) {
    val state = remember(node) {
        State.Success(
            node = node.node,
            content = node.sourceContent,
            linksLookedUp = false,
            referenceLinkHandler = ReferenceLinkHandlerImpl()
        )
    }
    MarkdownNodeStateContent(state, renderContext)
}

@Composable
private fun MarkdownNodeStateContent(
    state: State.Success,
    renderContext: ChatMarkdownRenderContext
) {
    com.mikepenz.markdown.compose.Markdown(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        colors = renderContext.colors,
        typography = renderContext.typography,
        padding = renderContext.padding,
        components = renderContext.components,
        imageTransformer = renderContext.imageTransformer,
        animations = markdownAnimations { this },
        success = { successState, components, modifier ->
            Column(modifier) {
                MarkdownElement(
                    node = successState.node,
                    components = components,
                    content = successState.content,
                    includeSpacer = true
                )
            }
        }
    )
}

@Composable
internal fun MarkdownTextContent(
    text: String,
    renderContext: ChatMarkdownRenderContext,
    immediate: Boolean = false,
    includeFirstSpacer: Boolean = true,
    onReady: () -> Unit = {}
) {
    val markdownText = remember(text) { text.toRenderableMarkdownText() }
    MarkdownPreparedTextContent(
        text = markdownText,
        renderContext = renderContext,
        immediate = immediate,
        includeFirstSpacer = includeFirstSpacer,
        onReady = onReady
    )
}

@Composable
private fun MarkdownPreparedTextContent(
    text: String,
    renderContext: ChatMarkdownRenderContext,
    immediate: Boolean = false,
    includeFirstSpacer: Boolean = true,
    onReady: () -> Unit = {}
) {
    val markdownText = text
    val markdownParser = remember(markdownText, renderContext.flavour) {
        MarkdownParser(renderContext.flavour)
    }
    val referenceLinkHandler = remember(markdownText) { ReferenceLinkHandlerImpl() }
    val markdownState = rememberMarkdownState(
        content = markdownText,
        flavour = renderContext.flavour,
        parser = markdownParser,
        referenceLinkHandler = referenceLinkHandler,
        immediate = immediate
    )
    val state by markdownState.state.collectAsState()
    val currentOnReady by rememberUpdatedState(onReady)

    LaunchedEffect(state) {
        if (state is State.Success) currentOnReady()
    }

    com.mikepenz.markdown.compose.Markdown(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        colors = renderContext.colors,
        typography = renderContext.typography,
        padding = renderContext.padding,
        components = renderContext.components,
        imageTransformer = renderContext.imageTransformer,
        animations = markdownAnimations { this },
        success = { successState, components, modifier ->
            MarkdownSuccessWithSpacing(
                state = successState,
                components = components,
                modifier = modifier,
                includeFirstSpacer = includeFirstSpacer
            )
        }
    )
}

@Composable
private fun MarkdownSuccessWithSpacing(
    state: State.Success,
    components: MarkdownComponents,
    modifier: Modifier = Modifier,
    includeFirstSpacer: Boolean = true
) {
    Column(modifier) {
        state.node.children.forEachIndexed { index, node ->
            MarkdownElement(
                node = node,
                components = components,
                content = state.content,
                includeSpacer = includeFirstSpacer || index > 0
            )
        }
    }
}

@Composable
private fun RecomposeSafeMarkdownNode(
    content: StreamingMarkdownNode,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    render: @Composable (node: StreamingMarkdownNode) -> Unit
) {
    var buf0 by remember { mutableStateOf<StreamingMarkdownNode?>(null) }
    var buf1 by remember { mutableStateOf<StreamingMarkdownNode?>(null) }
    var front by remember { mutableIntStateOf(0) }
    var fadeAlpha by remember { mutableFloatStateOf(0f) }

    val currentContent by rememberUpdatedState(content)
    val currentIsStreaming by rememberUpdatedState(isStreaming)
    val fadeChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    fun sameContent(a: StreamingMarkdownNode?, b: StreamingMarkdownNode): Boolean =
        a?.startOffset == b.startOffset &&
            a.endOffset == b.endOffset &&
            a.contentHash == b.contentHash &&
            a.node.type == b.node.type

    // Detect content changes and enqueue a fade.  Non-streaming content is
    // written straight into the front buffer so settled messages render
    // immediately without any cross-fade overhead.
    LaunchedEffect(content, isStreaming) {
        if (!currentIsStreaming) {
            // Non-streaming: always render from buf0, clear buf1, reset fade.
            // Also drain any pending fade signal so the fade effect does not
            // flip front after we have already settled on buf0.
            if (!sameContent(buf0, currentContent)) buf0 = currentContent
            buf1 = null
            front = 0
            fadeAlpha = 0f
            fadeChannel.tryReceive()
        } else {
            val cur = if (front == 0) buf0 else buf1
            if (cur == null || !sameContent(cur, currentContent)) {
                fadeChannel.trySend(Unit)
            }
        }
    }

    // Consume the fade queue.  Because the channel is CONFLATED, multiple
    // rapid updates collapse to a single pending signal; the loop simply
    // catches up with the latest content once the current fade ends.
    LaunchedEffect(Unit) {
        for (_signal in fadeChannel) {
            // If we have already switched to non-streaming, abort immediately.
            if (!currentIsStreaming) {
                fadeAlpha = 0f
                continue
            }

            val incoming = 1 - front
            if (front == 0) buf1 = currentContent else buf0 = currentContent

            val startNs = withFrameNanos { it }
            val durationNs = 180_000_000L
            while (true) {
                val nowNs = withFrameNanos { it }
                val p = ((nowNs - startNs).toFloat() / durationNs).coerceAtMost(1f)
                fadeAlpha = p
                if (p >= 1f) break
                // Abort early if streaming ended mid-fade.
                if (!currentIsStreaming) {
                    fadeAlpha = 0f
                    break
                }
            }
            if (currentIsStreaming) {
                front = incoming
            }
            fadeAlpha = 0f
        }
    }

    val isFading = fadeAlpha > 0f
    val incoming = 1 - front
    val z0 = when {
        isFading && incoming == 0 -> 2f
        isFading && front == 0 -> 0f
        front == 0 -> 2f
        else -> 0f
    }
    val a0 = when {
        isFading && incoming == 0 -> fadeAlpha
        isFading && front == 0 -> 1f
        front == 0 -> 1f
        else -> 0f
    }
    val z1 = when {
        isFading && incoming == 1 -> 2f
        isFading && front == 1 -> 0f
        front == 1 -> 2f
        else -> 0f
    }
    val a1 = when {
        isFading && incoming == 1 -> fadeAlpha
        isFading && front == 1 -> 1f
        front == 1 -> 1f
        else -> 0f
    }

    Box(modifier = modifier) {
        buf0?.let { node ->
            Box(modifier = Modifier.fillMaxWidth().zIndex(z0).alpha(a0)) { render(node) }
        }
        buf1?.let { node ->
            Box(modifier = Modifier.fillMaxWidth().zIndex(z1).alpha(a1)) { render(node) }
        }
    }
}

private fun String.toRenderableMarkdownText(): String {
    val spans = parseLatexSpans(this)
    val markdown = if (spans.all { !it.isLatex }) {
        this
    } else {
        spans.joinToString("") { span ->
            if (span.isLatex) latexToMarkdown(span.content, span.display)
            else span.content
        }
    }
    return markdown.escapeForMarkdown()
}

internal fun String.escapeForMarkdown(): String =
    replace("<think>", "<​think>").replace("</think>", "</​think>").escapeDollarForMarkdown()
