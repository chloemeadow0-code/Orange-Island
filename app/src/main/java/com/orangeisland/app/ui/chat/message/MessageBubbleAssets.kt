package com.orangeisland.app.ui.chat.message

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
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
                } else if (match != null) {
                    PlainCodeFenceBlock(
                        code = body,
                        language = language,
                        wrapEnabled = codeBlockWrapEnabled,
                    )
                } else {
                    // Regex couldn't cleanly parse the fence text (edge case); fall back to
                    // the library's default renderer so content still shows.
                    defaultComponents.codeFence(model)
                }
            },
            // GFM parses <details>…</details> as a single HTML_BLOCK whose raw text
            // is the verbatim tag source. Render it as a collapsible card; for any
            // other HTML/unknown block, replay the dispatcher's default recursion
            // (its children) so legacy raw-HTML display is unchanged.
            custom = { type, model ->
                if (false) {
                    // TODO: MarkdownDetailsBlock 尚未实现，<details> 标签暂时走默认 HTML 渲染。
                    // 原调用：MarkdownDetailsBlock(content = model.content, node = model.node)
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

/** Maps a fenced code block's language tag to a (extension, mimeType) pair for file export. */
private fun codeFileSpecFor(language: String): Pair<String, String> = when (language.lowercase()) {
    "python", "py" -> "py" to "text/x-python"
    "kotlin", "kt" -> "kt" to "text/x-kotlin"
    "java" -> "java" to "text/x-java"
    "javascript", "js" -> "js" to "text/javascript"
    "typescript", "ts" -> "ts" to "text/typescript"
    "html" -> "html" to "text/html"
    "css" -> "css" to "text/css"
    "json" -> "json" to "application/json"
    "xml" -> "xml" to "text/xml"
    "yaml", "yml" -> "yaml" to "text/yaml"
    "markdown", "md" -> "md" to "text/markdown"
    "sql" -> "sql" to "text/x-sql"
    "sh", "bash", "shell" -> "sh" to "text/x-sh"
    "c" -> "c" to "text/x-c"
    "cpp", "c++" -> "cpp" to "text/x-c++"
    "csharp", "cs" -> "cs" to "text/x-csharp"
    "go" -> "go" to "text/x-go"
    "rust", "rs" -> "rs" to "text/rust"
    "swift" -> "swift" to "text/x-swift"
    "php" -> "php" to "text/x-php"
    "ruby", "rb" -> "rb" to "text/x-ruby"
    else -> "txt" to "text/plain"
}

@Composable
private fun PlainCodeFenceBlock(
    code: String,
    language: String,
    wrapEnabled: Boolean,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val headerColor = MaterialTheme.colorScheme.secondaryContainer
    val bodyColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f)
        .compositeOver(MaterialTheme.colorScheme.surface)
    val onHeaderColor = MaterialTheme.colorScheme.onSecondaryContainer

    val (ext, mimeType) = remember(language) { codeFileSpecFor(language) }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        OutputStreamWriter(output).use { it.write(code) }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(bodyColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.ifBlank { "text" },
                fontSize = 12.sp,
                color = onHeaderColor.copy(alpha = 0.7f),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = onHeaderColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = { fileLauncher.launch("code.$ext") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = onHeaderColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Box(modifier = Modifier.padding(12.dp)) {
            SelectionContainer {
                Text(
                    text = code,
                    style = ChatType.code,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = wrapEnabled,
                    modifier = if (wrapEnabled) Modifier.fillMaxWidth()
                               else Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}
