package com.orangeisland.app.ui.chat.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

/**
 * Renders an HTML `<details>` block produced by GFM's parser as a collapsible card,
 * matching the visual idiom of [CompactedHistoryCard]: a `surfaceVariant` rounded
 * card with an icon + `<summary>` title row and a chevron that rotates on toggle.
 *
 * GFM parses `<details>…</details>` (a whole HTML block) into a single HTML_BLOCK
 * node whose raw text is the verbatim tag source. We therefore regex-parse the
 * node's own text rather than walking its AST children. The inner body is fed back
 * through the full markdown pipeline ([MarkdownTextContent]) so that **markdown
 * inside the block** (bold, lists, code, …) renders normally instead of as raw text.
 *
 * Default state is collapsed. If the model omits `<summary>`, a neutral fallback
 * title is shown. This is a generic renderer — it is not tied to any persona.
 */
@Composable
internal fun MarkdownDetailsBlock(
    content: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
) {
    val raw = remember(content, node) { node.getUnescapedTextInNode(content) }
    val parsed = remember(raw) { parseDetails(raw) }
    // Rebuild a render context from the current markdown CompositionLocals so the
    // body inherits the surrounding colors/typography/components. We can't capture
    // the caller's ChatMarkdownRenderContext (it transitively holds the components
    // table we're being registered into → a construction cycle).
    val renderContext = ChatMarkdownRenderContext(
        colors = LocalMarkdownColors.current,
        typography = LocalMarkdownTypography.current,
        padding = LocalMarkdownPadding.current,
        components = LocalMarkdownComponents.current,
        imageTransformer = LocalImageTransformer.current,
        flavour = DetailsBodyFlavour,
    )
    // Stable per-block collapsed state keyed on the title text.
    var expanded by rememberSaveable(parsed.title) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "detailsChevron"
    )

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }
        // NOTE: no animateContentSize() here. While streaming, the outer message height
        // changes on every token; an animated size transition lags behind those changes
        // (it keeps resizing AFTER text.length has settled), so the chat's stick-to-bottom
        // scroll — which keys on text.length — would stop early while this block was still
        // animating, producing the visible "jumps to the <details> render" jitter. The
        // expand/collapse animation is handled by the inner AnimatedVisibility instead.
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Subject,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = parsed.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    MarkdownTextContent(
                        text = parsed.body,
                        renderContext = renderContext,
                        immediate = true,
                        includeFirstSpacer = false
                    )
                }
            }
        }
    }
}

private data class ParsedDetails(val title: String, val body: String)

// Shared GFM flavour for re-parsing the inner body of a <details> block.
private val DetailsBodyFlavour = GFMFlavourDescriptor()

/**
 * Extract the `<summary>` title and the body between `<details>`/`</details>`
 * from a raw HTML_BLOCK string. Whitespace, optional self-closing, and case
 * variants of the tags are all tolerated. HTML entities in the summary are
 * decoded so titles read naturally.
 */
private fun parseDetails(raw: String): ParsedDetails {
    val titleMatch = SUMMARY_REGEX.find(raw)
    val title = titleMatch?.groupValues?.getOrNull(1)
        ?.replace("\n", " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "展开详情"

    val body = raw
        .replace(OPEN_DETAILS_REGEX, "")
        .replace(CLOSE_DETAILS_REGEX, "")
        .replace(SUMMARY_BLOCK_REGEX, "")
        .trim()
    return ParsedDetails(title, body)
}

// <details ...> opening tag (tolerate attributes like <details open>)
private val OPEN_DETAILS_REGEX = Regex("""(?is)\s*<details\b[^>]*>\s*""")
// </details> closing tag
private val CLOSE_DETAILS_REGEX = Regex("""(?is)\s*</details\s*>\s*""")
// The first <summary>...</summary> (for the title) — case-insensitive
private val SUMMARY_REGEX = Regex("""(?is)<summary\b[^>]*>(.*?)</summary\s*>""")
// Whole <summary>…</summary> block, for stripping from the body
private val SUMMARY_BLOCK_REGEX = Regex("""(?is)<summary\b[^>]*>.*?</summary\s*>""")
