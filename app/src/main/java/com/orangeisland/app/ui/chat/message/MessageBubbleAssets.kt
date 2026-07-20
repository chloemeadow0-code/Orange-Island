package com.orangeisland.app.ui.chat.message

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

/**
 * The memoized markdown rendering assets shared by a single [MessageItem]: the main
 * chat-body [ChatMarkdownRenderContext] plus the subordinate thought-block typography,
 * colors, padding and components reused by the [SegmentDetailSheet].
 *
 * Extracted from MessageItem so the ~110 lines of typography/color/component wiring
 * live in one place and the message composable reads as layout, not configuration.
 */
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
internal fun rememberChatMarkdownAssets(textColor: Color): ChatMarkdownAssets {
    // Chat-specific markdown scale — optimized for immersive reading.
    // Outfit's large x-height means 15sp reads like ~16sp Roboto.
    // Heading steps of 3sp (h1→h2→h3) and 2sp (h3→h4) create
    // a visible but not jarring hierarchy during long-form reading.
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

    // Compact typography for thought blocks — subordinate to main chat body.
    // One tier below main markdown: body at 13sp (vs 15sp), headings similarly
    // stepped down. Readable for paragraph-level content but clearly secondary.
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
    // Composite fg at 0.1 alpha over bg to produce the exact opaque equivalent
    val codeBg = remember(fg, bg) {
        Color(
            red   = fg.red   * 0.1f + bg.red   * 0.9f,
            green = fg.green * 0.1f + bg.green * 0.9f,
            blue  = fg.blue  * 0.1f + bg.blue  * 0.9f,
        )
    }
    val customMarkdownColors = markdownColor(
        codeBackground = codeBg,
        inlineCodeBackground = Color.Transparent,
    )
    val customMarkdownPadding = markdownPadding(block = 8.dp)
    val thoughtMarkdownPadding = markdownPadding(block = 5.dp)

    val customMarkdownComponents = remember {
        markdownComponents(
            table = { model ->
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
