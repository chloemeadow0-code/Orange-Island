package com.orangeisland.app.ui.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orangeisland.app.ui.components.HtmlCodeFenceBlock
import com.orangeisland.app.ui.components.LatexImageTransformer
import com.orangeisland.app.ui.theme.ChatType
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

private val PREVIEWABLE_LANGUAGES = setOf("html", "svg")

private val COMPLETE_FENCE_REGEX = Regex("""^`{3,}\s*([\w./+-]*)\s*\n([\s\S]*?)\n`{3,}\s*$""")

/**
 * True while the owning message is still streaming (status SENDING/THINKING/…).
 *
 * Consumed by custom markdown components (e.g. the <details> card) to decide whether to
 * render their fancy form. A <details> block re-parses from plain-text → HTML_BLOCK the
 * instant its closing tag streams in, which rebuilds the whole block list and makes the
 * stick-to-bottom scroll jump. While streaming we therefore render such blocks as plain
 * text (stable height, grows linearly like any other reply); the collapsible card form
 * is only adopted once generation finishes.
 */
internal val LocalMarkdownStreaming = compositionLocalOf { false }

@Stable
internal class ChatMarkdownAssets(
    val renderContext: ChatMarkdownRenderContext,
    val colors: MarkdownColors,
    val thoughtTypography: MarkdownTypography,
    val thoughtPadding: MarkdownPadding,
    val components: MarkdownComponents,
    val flavour: MarkdownFlavourDescriptor,
)

@Composable
internal fun rememberChatMarkdownAssets(textColor: Color, codeBlockWrapEnabled: Boolean = false): ChatMarkdownAssets {
    val customTypography = markdownTypography(
        text = ChatType.body,
        paragraph = ChatType.body,
        ordered = ChatType.body,
        bullet = ChatType.body,
        list = ChatType.body,
        h1 = ChatType.mdH1,
        h2 = ChatType.mdH2,
        h3 = ChatType.mdH3,
        h4 = ChatType.mdH4,
        h5 = ChatType.mdH5,
        h6 = ChatType.mdH6,
        code = ChatType.code,
        inlineCode = ChatType.code,
        table = ChatType.body,
    )

    val thoughtTypography = markdownTypography(
        text = ChatType.thoughtBody,
        paragraph = ChatType.thoughtBody,
        ordered = ChatType.thoughtBody,
        bullet = ChatType.thoughtBody,
        list = ChatType.thoughtBody,
        h1 = ChatType.thH1,
        h2 = ChatType.thH2,
        h3 = ChatType.thH3,
        h4 = ChatType.thH4,
        h5 = ChatType.thH5,
        h6 = ChatType.thH6,
        code = ChatType.thoughtCode,
        inlineCode = ChatType.thoughtCode,
    )

    val fg = MaterialTheme.colorScheme.onBackground
    val bg = MaterialTheme.colorScheme.surface
    val codeBg = remember(fg, bg) {
        Color(
            red   = fg.red   * 0.1f + bg.red   * 0.9f,
            green = fg.green * 0.1f + bg.green * 0.9f,
            blue  = fg.blue  * 0.1f + bg.blue  * 0.9f,
        )
    }
    val customMarkdownColors = markdownColor(
        text = textColor,
        codeBackground = codeBg,
        inlineCodeBackground = Color.Transparent,
    )
    val customMarkdownPadding = markdownPadding(block = 8.dp)
    val thoughtMarkdownPadding = markdownPadding(block = 5.dp)

    val defaultComponents = remember { markdownComponents() }

    val customMarkdownComponents = remember(defaultComponents, codeBlockWrapEnabled) {
        markdownComponents(
            table = { model ->
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    MarkdownTable(
                        content = model.content,
                        node = model.node,
                        style = model.typography.table,
                        headerBlock = { content, header, tableWidth, style ->
                            MarkdownTableHeader(
                                content = content,
                                header = header,
                                tableWidth = tableWidth,
                                style = style,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip,
                            )
                        },
                        rowBlock = { content, row, tableWidth, style ->
                            MarkdownTableRow(
                                content = content,
                                header = row,
                                tableWidth = tableWidth,
                                style = style,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip,
                            )
                        },
                    )
                }
            },
            codeFence = { model ->
                val start = model.node.startOffset.coerceIn(0, model.content.length)
                val end = model.node.endOffset.coerceIn(start, model.content.length)
                val raw = model.content.substring(start, end).trim()
                val match = COMPLETE_FENCE_REGEX.find(raw)
                val language = match?.groupValues?.get(1)?.trim()?.lowercase().orEmpty()
                val body = match?.groupValues?.get(2).orEmpty()

                if (match != null && language in PREVIEWABLE_LANGUAGES) {
                    HtmlCodeFenceBlock(code = body, language = language)
                } else if (codeBlockWrapEnabled) {
                    WrappedCodeFenceBlock(
                        code = body,
                        textColor = textColor,
                        codeBg = codeBg,
                    )
                } else {
                    defaultComponents.codeFence(model)
                }
            },
            // GFM parses <details>…</details> as a single HTML_BLOCK whose raw text
            // is the verbatim tag source. Render it as a collapsible card; for any
            // other HTML/unknown block, replay the dispatcher's default recursion
            // (its children) so legacy raw-HTML display is unchanged.
            //
            // WHILE STREAMING we deliberately skip the card form: a <details> block is
            // re-parsed from plain text → HTML_BLOCK the moment its closing tag arrives,
            // which rebuilds the surrounding block list and makes the stick-to-bottom
            // scroll visibly jump ("keeps jumping back to the <details> render"). Falling
            // back to plain-text children while streaming keeps the height linear and
            // stable; the card is adopted only once generation completes.
            custom = { type, model ->
                val isDetailsBlock = type == MarkdownElementTypes.HTML_BLOCK &&
                    model.node.getUnescapedTextInNode(model.content).contains("<details", ignoreCase = true)
                if (isDetailsBlock && !LocalMarkdownStreaming.current) {
                    MarkdownDetailsBlock(
                        content = model.content,
                        node = model.node,
                    )
                } else {
                    model.node.children.forEach { child ->
                        com.mikepenz.markdown.compose.MarkdownElement(
                            node = child,
                            components = defaultComponents,
                            content = model.content,
                            includeSpacer = false,
                        )
                    }
                }
            }
        )
    }

    val latexImageTransformer = remember(textColor) {
        LatexImageTransformer(
            textSize = 56f,
            color = textColor.toArgb(),
        )
    }
    val markdownFlavour = remember { GFMFlavourDescriptor() }
    val markdownRenderContext = remember(
        customMarkdownColors,
        customTypography,
        customMarkdownPadding,
        customMarkdownComponents,
        latexImageTransformer,
        markdownFlavour,
    ) {
        ChatMarkdownRenderContext(
            colors = customMarkdownColors,
            typography = customTypography,
            padding = customMarkdownPadding,
            components = customMarkdownComponents,
            imageTransformer = latexImageTransformer,
            flavour = markdownFlavour,
        )
    }

    return remember(
        markdownRenderContext,
        customMarkdownColors,
        thoughtTypography,
        thoughtMarkdownPadding,
        customMarkdownComponents,
        markdownFlavour,
    ) {
        ChatMarkdownAssets(
            renderContext = markdownRenderContext,
            colors = customMarkdownColors,
            thoughtTypography = thoughtTypography,
            thoughtPadding = thoughtMarkdownPadding,
            components = customMarkdownComponents,
            flavour = markdownFlavour,
        )
    }
}

/**
 * Auto-wrapping code block used when [codeBlockWrapEnabled] is true.
 * Replaces the library's horizontally-scrolling default with a soft-wrapping
 * Text inside a rounded box that matches the default code block visuals.
 */
@Composable
private fun WrappedCodeFenceBlock(
    code: String,
    textColor: Color,
    codeBg: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(codeBg, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = code,
            style = ChatType.code,
            color = textColor,
            softWrap = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
