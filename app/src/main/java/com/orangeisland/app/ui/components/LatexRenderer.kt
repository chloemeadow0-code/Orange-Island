package com.orangeisland.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig
import androidx.compose.ui.text.PlaceholderVerticalAlign
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.noties.jlatexmath.JLatexMathDrawable
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt

data class LatexSpan(
    val isLatex: Boolean,
    val content: String,
    val display: Boolean = false,
)

// ── Patterns ──────────────────────────────────────────────────────────

/** Greek letters (U+0370–U+03FF) and math symbols (U+2200–U+2AFF). */
private val MATH_UNICODE = Regex("""[Ͱ-Ͽ∀-⫿]""")

/** LaTeX command — backslash + letters, e.g. \frac \alpha \text \sum. */
private val LATEX_COMMAND = Regex("""\\[a-zA-Z]+""")

/** Escaped dollar sign — strong LaTeX signal (not used in prose). */
private val ESCAPED_DOLLAR = Regex("""\\\$""")

/** Math structural markers — sub/superscript braces, begin/end. */
private val MATH_STRUCTURE = Regex("""[\^_]\{|\\begin|\\end""")

/** Pure number or percentage explicitly wrapped in $...$ */
private val PURE_NUM = Regex("""^-?\d+(\.\d+)?\\?%?$""")

/** Math operators — presence strongly suggests LaTeX, not prose. */
private val MATH_OPS = Regex("""[+\-*/=<>|^_]""")

/** English prose stop-words — signals natural language, not math. */
private val PROSE_WORD = Regex(
    """\b(and|or|the|a|an|in|on|to|for|of|is|are|was|were|be|it|we|you|he|she|they|""" +
    """costs?|price|dollar|another|other|this|that|each|per|total|only|about|""" +
    """more|less|than|with|from|by|can|will|would|should|could|may|not|but|""" +
    """one|two|three|four|five|ten|new|old|some|all|any|just|also|has|have|had|what)\b""",
    RegexOption.IGNORE_CASE
)

/** Sentence-ending punctuation followed by space — strong prose signal. */
private val SENTENCE_PUNCT = Regex("""[.!?;:]["») \t]""")

private const val LATEX_URL_PREFIX = "latex://"
private const val LATEX_URL_INLINE = "inline/"
private const val LATEX_URL_DISPLAY = "display/"
private val LATEX_BASE64 = Base64.UrlSafe

private data class LatexImageRequest(
    val latex: String,
    val display: Boolean,
)

private data class ProtectedRange(
    val start: Int,
    val endExclusive: Int,
)

private fun findMarkdownProtectedRanges(src: String): List<ProtectedRange> {
    val ranges = mutableListOf<ProtectedRange>()
    var i = 0
    while (i < src.length) {
        if (src.startsWith("```", i)) {
            val end = src.indexOf("```", startIndex = i + 3)
            val endExclusive = if (end >= 0) end + 3 else src.length
            ranges.add(ProtectedRange(i, endExclusive))
            i = endExclusive
            continue
        }

        if (src[i] == '`') {
            val lineEnd = src.indexOf('\n', startIndex = i + 1)
                .let { if (it >= 0) it else src.length }
            val end = src.indexOf('`', startIndex = i + 1)
            val endExclusive = if (end >= 0 && end < lineEnd) end + 1 else lineEnd
            ranges.add(ProtectedRange(i, endExclusive))
            i = endExclusive
            continue
        }

        i++
    }
    return ranges
}

// ── Unicode gate ──────────────────────────────────────────────────────

/**
 * Returns true if every non-ASCII character in [s] is safely inside `{…}`,
 * OR is a Greek / math-symbol Unicode codepoint.
 * This lets `x_{中文}` and `\alpha` through while rejecting bare prose.
 */
private fun nonAsciiInsideBraces(s: String): Boolean {
    var depth = 0
    var esc = false
    for (ch in s) {
        if (esc) { esc = false; continue }
        if (ch == '\\') { esc = true; continue }
        if (ch == '{') { depth++; continue }
        if (ch == '}' && depth > 0) { depth--; continue }
        if (ch.code > 127 && depth == 0 && !MATH_UNICODE.matches(ch.toString()) && ch.isLetter()) {
            return false
        }
    }
    return true
}

// ── Content discriminator for $...$ ────────────────────────────────────

/**
 * Heuristic: given the trimmed content between a pair of `$` delimiters,
 * decide whether it is more likely LaTeX or natural-language prose.
 */
private fun isLikelyLatex(content: String): Boolean {
    val t = content.trim()
    if (t.isEmpty()) return false

    // Non-ASCII outside braces → prose (check BEFORE positive signals —
    // a rogue Chinese char must veto even if \mathrm or = is present)
    if (!nonAsciiInsideBraces(t)) return false

    // Strong positive signals — these are almost certainly LaTeX
    if (LATEX_COMMAND.containsMatchIn(t)) return true
    if (ESCAPED_DOLLAR.containsMatchIn(t)) return true
    if (MATH_STRUCTURE.containsMatchIn(t)) return true
    if (PURE_NUM.matches(t)) return true
    // Single character — math variable (not the article "a" / "A")
    if (t.length == 1 && t[0].isLetterOrDigit()) return true
    val hasMathOps = MATH_OPS.containsMatchIn(t)

    // Strong negative signals — these are almost certainly prose
    if (SENTENCE_PUNCT.containsMatchIn(t)) return false

    // Prose stop-words: fatal unless math operators are also present.
    // Single-letter matches are excluded — they are math variables.
    val words = t.split(Regex("""\s+"""))
    val hasProseWord = words.any { w -> w.length > 1 && PROSE_WORD.matches(w) }
    if (hasProseWord && !hasMathOps) return false

    // Many whitespace-delimited tokens → prose (unless math ops dominate)
    if (words.size > 5 && !hasMathOps) return false

    // No letters at all, no math ops, not pure number → not LaTeX
    // (catches "800–" where – is non-ASCII punctuation)
    if (t.none { it.isLetter() } && !hasMathOps) return false

    // Short, no prose signals — default to LaTeX
    return true
}

// ── Markdown escape ────────────────────────────────────────────────────

fun String.escapeDollarForMarkdown(): String = buildString {
    val src = this@escapeDollarForMarkdown
    val protectedRanges = findMarkdownProtectedRanges(src)
    var protectedIndex = 0
    var i = 0
    while (i < src.length) {
        while (protectedIndex < protectedRanges.size && protectedRanges[protectedIndex].endExclusive <= i) {
            protectedIndex++
        }
        val protected = protectedRanges.getOrNull(protectedIndex)
        if (protected != null && i >= protected.start) {
            append(src.substring(i, protected.endExclusive))
            i = protected.endExclusive
            protectedIndex++
            continue
        }

        val ch = src[i]
        val remaining = src.substring(i)

        // ``` fenced code block — pass through
        if (remaining.startsWith("```")) {
            val end = remaining.indexOf("```", 3)
            if (end >= 0) {
                append(remaining.substring(0, end + 3))
                i += end + 3; continue
            }
        }
        // ` inline code — pass through
        if (ch == '`') {
            val end = remaining.indexOf('`', 1)
            if (end >= 0) {
                append(remaining.substring(0, end + 1))
                i += end + 1; continue
            }
        }
        // Bare $ → \$ (but not already-escaped \$)
        if (ch == '$' && (i == 0 || src[i - 1] != '\\')) {
            append('\\')
        }
        append(ch)
        i++
    }
}

// ── Span parser ────────────────────────────────────────────────────────

fun parseLatexSpans(text: String): List<LatexSpan> {
    val spans = mutableListOf<LatexSpan>()
    val buf = StringBuilder()
    val protectedRanges = findMarkdownProtectedRanges(text)
    var protectedIndex = 0
    var i = 0
    while (i < text.length) {
        while (protectedIndex < protectedRanges.size && protectedRanges[protectedIndex].endExclusive <= i) {
            protectedIndex++
        }
        val protected = protectedRanges.getOrNull(protectedIndex)
        if (protected != null && i >= protected.start) {
            buf.append(text.substring(i, protected.endExclusive))
            i = protected.endExclusive
            protectedIndex++
            continue
        }

        val remaining = text.substring(i)

        // ``` fenced code block — skip until closing ```
        if (remaining.startsWith("```")) {
            val end = remaining.indexOf("```", 3)
            if (end >= 0) {
                if (buf.isNotEmpty()) { spans.add(LatexSpan(false, buf.toString())); buf.clear() }
                spans.add(LatexSpan(false, remaining.substring(0, end + 3)))
                i += end + 3
                continue
            }
        }

        // ` inline code — skip until closing `
        if (remaining[0] == '`' && !remaining.startsWith("```")) {
            val end = remaining.indexOf('`', 1)
            if (end >= 0) {
                buf.append(remaining.substring(0, end + 1))
                i += end + 1
                continue
            }
        }

        // $$ display math
        if (remaining.startsWith("$$")) {
            val end = remaining.indexOf("$$", 2)
            if (end >= 0) {
                val latex = remaining.substring(2, end).trim()
                if (latex.isNotBlank() && nonAsciiInsideBraces(latex)) {
                    if (buf.isNotEmpty()) { spans.add(LatexSpan(false, buf.toString())); buf.clear() }
                    spans.add(LatexSpan(true, latex, true))
                    i += end + 2
                    continue
                } else if (latex.isNotBlank()) {
                    // Invalid display candidate. Keep recovery local so a stray
                    // "$$" cannot consume a later valid display opener/closer.
                    buf.append("$$")
                    i += 2
                    continue
                }
                buf.append("$$")
                i += 2
                continue
            }
        }

        // \[ display math
        if (remaining.startsWith("\\[")) {
            val end = remaining.indexOf("\\]", 2)
            if (end >= 0) {
                val latex = remaining.substring(2, end).trim()
                if (latex.isNotBlank() && nonAsciiInsideBraces(latex)) {
                    if (buf.isNotEmpty()) { spans.add(LatexSpan(false, buf.toString())); buf.clear() }
                    spans.add(LatexSpan(true, latex, true))
                    i += end + 2
                    continue
                } else if (latex.isNotBlank()) {
                    buf.append("\\[")
                    i += 2
                    continue
                }
                buf.append("\\[")
                i += 2
                continue
            }
        }

        // \( inline math
        if (remaining.startsWith("\\(")) {
            val end = remaining.indexOf("\\)", 2)
            if (end >= 0) {
                val latex = remaining.substring(2, end).trim()
                if (latex.isNotBlank() && nonAsciiInsideBraces(latex)) {
                    if (buf.isNotEmpty()) { spans.add(LatexSpan(false, buf.toString())); buf.clear() }
                    spans.add(LatexSpan(true, latex, false))
                    i += end + 2
                    continue
                } else if (latex.isNotBlank()) {
                    buf.append("\\(")
                    i += 2
                    continue
                }
                buf.append("\\(")
                i += 2
                continue
            }
        }

        // $ inline math — skip if preceded by \ (escaped)
        if (remaining[0] == '$' && !remaining.startsWith("$$")) {
            val prevChar = if (i > 0) text[i - 1] else ' '
            if (prevChar != '\\') {
                // Find real closing $ on the same line (skip escaped \$)
                val lineEnd = remaining.indexOf('\n').let { if (it < 0) remaining.length else it }
                var end = remaining.indexOf('$', 1)
                while (end in 1..<lineEnd && remaining[end - 1] == '\\') {
                    end = remaining.indexOf('$', end + 1)
                }
                if (end in 1..<lineEnd) {
                    val latex = remaining.substring(1, end).trim()
                    if (latex.isNotEmpty() && isLikelyLatex(latex)) {
                        if (buf.isNotEmpty()) { spans.add(LatexSpan(false, buf.toString())); buf.clear() }
                        spans.add(LatexSpan(true, latex, false))
                        i += end + 1; continue
                    }
                    // Content looks like prose → only treat the opening $ as literal.
                    // Don't consume the closing $ — it may belong to a valid LaTeX pair later.
                    buf.append('$')
                    i++
                    continue
                }

                // No real same-line closing $ → treat $ as literal (could be a dollar amount)
                buf.append('$')
                i++
                continue
            }
        }

        // Escaped \$ → literal $
        if (remaining.startsWith("\\$")) {
            buf.append('$')
            i += 2
            continue
        }

        buf.append(remaining[0])
        i++
    }
    if (buf.isNotEmpty()) spans.add(LatexSpan(false, buf.toString()))

    // Escape $ in non-LaTeX spans for markdown compatibility
    for (idx in spans.indices) {
        val span = spans[idx]
        if (!span.isLatex) {
            spans[idx] = span.copy(content = span.content.escapeDollarForMarkdown())
        }
    }

    // Trim whitespace around inline LaTeX spans
    for (idx in spans.indices) {
        val span = spans[idx]
        if (span.isLatex && !span.display) {
            if (idx > 0) {
                val prev = spans[idx - 1]
                if (!prev.isLatex) spans[idx - 1] = prev.copy(content = prev.content.trimEnd())
            }
            if (idx + 1 < spans.size) {
                val next = spans[idx + 1]
                if (!next.isLatex) spans[idx + 1] = next.copy(content = next.content.trimStart())
            }
        }
    }

    return spans
}

// ── Rendering ──────────────────────────────────────────────────────────

fun renderLatexToBitmap(
    latex: String,
    textSize: Float = 48f,
    color: Int = 0xFF000000.toInt(),
    fallbackW: Int = 800,
    fallbackH: Int = 200,
    minW: Int = 0,
): Bitmap? {
    return try {
        val drawable = JLatexMathDrawable.builder(latex)
            .textSize(textSize)
            .color(color)
            .build()
        val iw = drawable.intrinsicWidth
        val ih = drawable.intrinsicHeight
        val w = maxOf(iw.takeIf { it > 0 } ?: fallbackW, minW)
        val h = ih.takeIf { it > 0 } ?: fallbackH
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bmp
    } catch (e: Exception) {
        null
    }
}

fun canRenderLatex(latex: String): Boolean {
    return try {
        JLatexMathDrawable.builder(latex).textSize(48f).color(0).build()
        true
    } catch (_: Exception) { false }
}

private fun encodeLatexUrl(latex: String, display: Boolean = false): String {
    val mode = if (display) LATEX_URL_DISPLAY else LATEX_URL_INLINE
    val encoded = LATEX_BASE64.encode(latex.toByteArray(Charsets.UTF_8))
    return "$mode$encoded"
}

private fun decodeLatexUrl(encoded: String): String {
    return LATEX_BASE64.decode(encoded).decodeToString()
}

private fun decodeLatexLink(link: String): LatexImageRequest? {
    if (!link.startsWith(LATEX_URL_PREFIX)) return null
    val payload = link.removePrefix(LATEX_URL_PREFIX)
    val (display, encoded) = when {
        payload.startsWith(LATEX_URL_DISPLAY) -> true to payload.removePrefix(LATEX_URL_DISPLAY)
        payload.startsWith(LATEX_URL_INLINE) -> false to payload.removePrefix(LATEX_URL_INLINE)
        else -> false to payload
    }
    return try {
        LatexImageRequest(decodeLatexUrl(encoded), display)
    } catch (_: Exception) {
        null
    }
}

fun inlineLatexToMarkdown(latexContent: String): String {
    return latexToMarkdown(latexContent, display = false)
}

fun latexToMarkdown(latexContent: String, display: Boolean): String {
    val image = "![latex]($LATEX_URL_PREFIX${encodeLatexUrl(latexContent, display)})"
    return if (display) "\n\n$image\n\n" else image
}

private fun renderTextToBitmap(text: String, textSize: Float, color: Int): Bitmap {
    val paint = android.graphics.Paint().apply {
        this.textSize = textSize * 0.6f
        this.color = color
        isAntiAlias = true
    }
    val fm = paint.fontMetrics
    val w = (paint.measureText(text) + 8f).toInt().coerceAtLeast(1)
    val h = ((fm.descent - fm.ascent) + 8f).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(0x00000000)
    val canvas = Canvas(bmp)
    canvas.drawText(text, 4f, -fm.ascent + 4f, paint)
    return bmp
}

private data class LatexRenderKey(
    val latex: String,
    val textSize: Float,
    val color: Int,
)

private object LatexBitmapCache {
    private const val MAX_ENTRIES = 128

    private val lock = Any()
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = mutableMapOf<LatexRenderKey, Deferred<Bitmap>>()
    private val bitmaps = object : LinkedHashMap<LatexRenderKey, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LatexRenderKey, Bitmap>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(key: LatexRenderKey): Bitmap? = synchronized(lock) {
        bitmaps[key]
    }

    fun renderAsync(key: LatexRenderKey): Deferred<Bitmap> = synchronized(lock) {
        bitmaps[key]?.let { return CompletableDeferred(it) }
        inFlight[key]?.let { return it }

        val deferred = CompletableDeferred<Bitmap>()
        inFlight[key] = deferred
        renderScope.launch {
            try {
                val rendered = renderBitmap(key)
                synchronized(lock) {
                    bitmaps[key] = rendered
                    if (inFlight[key] === deferred) {
                        inFlight.remove(key)
                    }
                }
                deferred.complete(rendered)
            } catch (e: CancellationException) {
                synchronized(lock) {
                    if (inFlight[key] === deferred) {
                        inFlight.remove(key)
                    }
                }
                deferred.completeExceptionally(e)
                throw e
            } catch (t: Throwable) {
                synchronized(lock) {
                    if (inFlight[key] === deferred) {
                        inFlight.remove(key)
                    }
                }
                deferred.completeExceptionally(t)
            }
        }
        deferred
    }

    suspend fun awaitRendered(key: LatexRenderKey): Bitmap {
        get(key)?.let { return it }
        return renderAsync(key).await()
    }

    private fun renderBitmap(key: LatexRenderKey): Bitmap {
        val fw = (key.textSize * 10).toInt()
        val fh = (key.textSize * 2).toInt()
        return renderLatexToBitmap(
            key.latex,
            key.textSize,
            key.color,
            fallbackW = fw,
            fallbackH = fh,
            minW = 0,
        ) ?: renderTextToBitmap("$${key.latex}$", key.textSize, key.color)
    }
}

private fun estimateLatexPlaceholderSize(latex: String, textSize: Float, display: Boolean): Size {
    val charCount = latex.length.coerceAtLeast(1)
    val minWidth = textSize * if (display) 4f else 1.4f
    val maxWidth = textSize * if (display) 18f else 8f
    val width = (charCount * textSize * if (display) 0.46f else 0.38f)
        .coerceIn(minWidth, maxWidth)
    val height = textSize * if (display) 1.45f else 1.05f
    return Size(width, height)
}

// ── Image transformer ──────────────────────────────────────────────────

class LatexImageTransformer(
    private val textSize: Float = 40f,
    private val color: Int = 0xFF000000.toInt(),
) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        val request = decodeLatexLink(link)
        if (request == null) {
            // Not a latex:// link — treat as a normal network/local image (e.g. stickers,
            // emoji images sent by the model) and load it via Coil.
            //
            // The markwon library renders this painter with a bare `Image(painter)` and
            // `ContentScale.Fit`, sizing it from `painter.intrinsicSize`. Coil's async
            // painter reports `Size.Unspecified` until the fetch resolves, so the image
            // measures 0x0 and never appears. We wrap it so `intrinsicSize` returns a
            // sensible non-zero placeholder derived from the container width; once the
            // real bitmap lands, intrinsicSize updates and `Image()` re-measures.
            return networkImageData(link)
        }
        val key = LatexRenderKey(request.latex, textSize, color)
        var bitmap by remember(key) { mutableStateOf(LatexBitmapCache.get(key)) }
        val fade = remember(key) { Animatable(if (bitmap != null) 1f else 0f) }
        LaunchedEffect(key) {
            val cached = LatexBitmapCache.get(key)
            if (cached != null) {
                bitmap = cached
                fade.snapTo(1f)
                return@LaunchedEffect
            }

            bitmap = null
            fade.snapTo(0f)
            val rendered = try {
                LatexBitmapCache.awaitRendered(key)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (rendered != null) {
                bitmap = rendered
                fade.snapTo(0f)
                fade.animateTo(1f, tween(durationMillis = 180))
            }
        }
        val bmp: Bitmap? = bitmap
        val density = LocalDensity.current
        val placeholderModifier = if (bmp == null) {
            val estimated = estimateLatexPlaceholderSize(request.latex, textSize, request.display)
            with(density) {
                Modifier.size(estimated.width.toDp(), estimated.height.toDp())
            }
        } else {
            Modifier
        }
        return ImageData(
            painter = bmp?.let { FadingBitmapPainter(it.asImageBitmap(), fade.value) }
                ?: ColorPainter(Color.Transparent),
            contentDescription = request.latex,
            modifier = placeholderModifier,
            alignment = Alignment.CenterStart,
            contentScale = ContentScale.Fit,
        )
    }

    override fun placeholderConfig(
        link: String,
        density: Density,
        containerSize: Size,
        imageWidth: ImageWidth,
        imageSize: Size,
        imageSizeChanged: ((link: String, Size) -> Unit)?,
    ): PlaceholderConfig {
        val request = decodeLatexLink(link) ?: return super.placeholderConfig(
            link, density, containerSize, imageWidth, imageSize, imageSizeChanged
        )
        val key = LatexRenderKey(request.latex, textSize, color)
        val resolvedSize = if (imageSize.isUnspecified) {
            LatexBitmapCache.get(key)?.let { bmp ->
                Size(bmp.width.toFloat(), bmp.height.toFloat())
            } ?: estimateLatexPlaceholderSize(request.latex, textSize, request.display)
        } else {
            imageSize
        }
        val sizeDp = with(density) {
            Size(
                width = resolvedSize.width.toDp().value,
                height = resolvedSize.height.toDp().value,
            )
        }
        return PlaceholderConfig(size = sizeDp, verticalAlign = PlaceholderVerticalAlign.TextCenter)
    }

    /**
     * Builds an [ImageData] for a non-latex (network/local) image link.
     *
     * The markdown library renders this with a bare `Image(painter)` and
     * `ContentScale.Fit`, sizing it from `painter.intrinsicSize`. Coil's
     * `rememberAsyncImagePainter` reports `Size.Unspecified` while loading, which
     * would measure 0x0 and collapse the layout. We therefore apply a non-zero
     * placeholder modifier (max 120.dp, 16:9 fallback) during loading, and switch
     * to the real aspect ratio from the painter's intrinsicSize once the fetch
     * succeeds. GIF and animated images are supported natively because we return
     * the Coil painter directly instead of manually extracting a bitmap.
     */
    @Composable
    private fun networkImageData(link: String): ImageData {
        val painter = rememberAsyncImagePainter(model = link)
        val modifier = when (val state = painter.state) {
            is AsyncImagePainter.State.Success -> {
                val intrinsicSize = state.painter.intrinsicSize
                val ratio = if (intrinsicSize.isUnspecified || intrinsicSize.width <= 0 || intrinsicSize.height <= 0) {
                    16f / 9f
                } else {
                    intrinsicSize.width / intrinsicSize.height
                }
                Modifier
                    .widthIn(max = 120.dp)
                    .aspectRatio(ratio)
            }
            else -> {
                // Loading / Empty / Error — keep a non-zero placeholder so the layout does not collapse.
                Modifier
                    .widthIn(max = 120.dp)
                    .aspectRatio(16f / 9f)
            }
        }
        return ImageData(
            painter = painter,
            contentDescription = null,
            // Cap the image at ~half the bubble width so stickers/emoji images don't
            // fill the whole row. widthIn keeps small images at their natural size and
            // only constrains the upper bound; aspectRatio preserves the proportions.
            modifier = modifier,
            alignment = Alignment.Center,
            contentScale = ContentScale.Fit,
        )
    }
}

private class FadingBitmapPainter(
    private val image: ImageBitmap,
    private val alpha: Float,
) : Painter() {
    override val intrinsicSize: Size
        get() = Size(image.width.toFloat(), image.height.toFloat())

    override fun DrawScope.onDraw() {
        drawImage(
            image = image,
            dstSize = IntSize(
                width = size.width.roundToInt().coerceAtLeast(1),
                height = size.height.roundToInt().coerceAtLeast(1),
            ),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}
