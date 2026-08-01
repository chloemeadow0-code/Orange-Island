package com.orangeisland.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color

/**
 * 轻量行内 markdown 渲染，专给分气泡场景用——只处理 **加粗**、*斜体*、`行内代码`
 * 三种最常见的短句内格式，不解析表格/代码块/列表这类块级结构（分气泡本来
 * 就是拆过的单行短句，几乎不会包含这些块级内容）。目的是彻底避开完整
 * markdown 渲染器内部段落组件强制 fillMaxWidth 的问题，让 Text 能按内容
 * 自然宽度包裹。
 */
private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
private val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
private val CODE_REGEX = Regex("`([^`]+?)`")

/**
 * True when [text] contains block-level markdown that the lightweight inline
 * renderer ([buildInlineMarkdownAnnotatedString]) cannot represent, so the caller
 * should fall back to the full markdown pipeline instead. Covers images,
 * fenced code blocks (``` or ~~~), indented code blocks, tables (| ... |),
 * GFM alerts ("> ![note]"), and block HTML tags (e.g. `<details>`).
 *
 * Intentionally match-anywhere (no `^`/MULTILINE): a block can land in any
 * split-bubble segment, and we'd rather over-trigger the full renderer than
 * silently mangle a block as inline text.
 */
private val BLOCK_MARKDOWN_REGEX = Regex(
    """!\[|```|~~~|^\s{4,}\S|\|[ \t]-+|[ \t]\|[ \t]|^>\s*!\[|<[a-zA-Z][^>]*>""",
    RegexOption.MULTILINE
)

fun needsFullMarkdownRenderer(text: String): Boolean = BLOCK_MARKDOWN_REGEX.containsMatchIn(text)

fun buildInlineMarkdownAnnotatedString(
    text: String,
    codeBackground: Color,
    codeColor: Color
): AnnotatedString {
    data class Token(val range: IntRange, val style: SpanStyle, val content: String)

    val tokens = mutableListOf<Token>()
    fun collect(regex: Regex, style: (MatchResult) -> SpanStyle) {
        regex.findAll(text).forEach { m ->
            tokens.add(Token(m.range, style(m), m.groupValues[1]))
        }
    }
    collect(CODE_REGEX) { SpanStyle(background = codeBackground, color = codeColor) }
    collect(BOLD_REGEX) { SpanStyle(fontWeight = FontWeight.Bold) }
    collect(ITALIC_REGEX) { SpanStyle(fontStyle = FontStyle.Italic) }

    // 按起始位置排序，重叠的（比如代码块内部误匹配到星号）跳过后面的
    val sorted = tokens.sortedBy { it.range.first }
    val accepted = mutableListOf<Token>()
    var lastEnd = -1
    for (t in sorted) {
        if (t.range.first > lastEnd) {
            accepted.add(t)
            lastEnd = t.range.last
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        for (t in accepted) {
            if (t.range.first > cursor) append(text.substring(cursor, t.range.first))
            withStyle(t.style) { append(t.content) }
            cursor = t.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
