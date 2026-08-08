package com.orangeisland.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.orangeisland.app.R

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

val OutfitFamily = FontFamily(
    Font(R.font.mioutfit_extralight, FontWeight.ExtraLight),
    Font(R.font.mioutfit_light, FontWeight.Light),
    Font(R.font.mioutfit_regular, FontWeight.Normal),
    Font(R.font.mioutfit_medium, FontWeight.Medium),
    Font(R.font.mioutfit_bold, FontWeight.Bold),
)

// Geometric (modular) type scale: every distinct size is a term of a geometric
// sequence anchored at body = 16sp with common ratio r = 1.2 (minor third).
// Sizes: 11, 13, 16, 19, 23, 28, 33, 40, 48, 57. Line heights scale per tier
// (display 1.15× · headline 1.25× · title 1.3× · body 1.45× · label 1.4×).
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 66.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 55.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 33.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 35.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// ChatType — single source of truth for the chat surface's typographic scale.
//
// The chat page is an information-dense, immersive-reading surface and so uses a
// TIGHTER scale than the 1.2 (minor-third) geometric scale that drives Settings'
// big collapsing titles. Here the ratio is ~1.15 (major-second), anchored at the
// reading body (15sp). Outfit's tall x-height makes 15sp read like ~16sp Roboto.
//
// Five semantic tiers — never reach past them on a chat Text:
//   · Title   — brand 20 · sheet 19 · conversation 17   (the only sizes ≥17)
//   · Input   — 16 (slightly above body for a comfortable touch target)
//   · Body    — 15 (user + assistant message text; the anchor)
//   · Sub     — 13 (thought body; clearly subordinate to body)
//   · Meta    — 12 labels/status · 11 micro (token counts, badges)
//
// Hierarchy is carried by SIZE + WEIGHT + COLOR together: e.g. the collapsed
// "thought for Ns" eyebrow is meta(12) but Bold + primary, so it out-ranks the
// 13sp thought body it introduces despite being smaller. Call sites supply color.
//
// Font family / scale are provided by OrangeIslandTheme via these CompositionLocals
// (compositionLocalOf, NOT staticCompositionLocalOf: a font-size-tier change must
// invalidate every reading composable). They replace the old module-level mutable
// vars, which had no snapshot tracking — a recomposition that read ChatType without
// re-running OrangeIslandTheme could observe a stale or mid-write value (the
// "font size is suddenly wrong after returning from background" bug).
val LocalChatFontFamily = compositionLocalOf<FontFamily> { OutfitFamily }
val LocalChatFontScale = compositionLocalOf { 1.0f }

object FontSizeTiers {
    const val SMALL = "small"
    const val DEFAULT = "default"
    const val LARGE = "large"
    const val XLARGE = "xlarge"
    const val XXLARGE = "xxlarge"

    fun scaleFor(tier: String): Float = when (tier) {
        SMALL -> 0.85f
        LARGE -> 1.15f
        XLARGE -> 1.3f
        XXLARGE -> 1.45f
        else -> 1.0f // DEFAULT + any unknown fallback
    }
}

object ChatType {

    // Every style is a @Composable getter reading LocalChatFontFamily /
    // LocalChatFontScale, so reads are tracked by the snapshot system and any
    // reader recomposes when the theme's font choice changes — the same pattern
    // as MaterialTheme.colorScheme / MaterialTheme.typography. All call sites
    // already sit in @Composable context, so `ChatType.xxx` syntax is unchanged.

    @Composable
    private fun chatStyle(
        fontWeight: FontWeight,
        sizeSp: Int,
        lineSp: Int,
        letterSpacing: TextUnit = TextUnit.Unspecified
    ): TextStyle {
        val s = LocalChatFontScale.current
        return TextStyle(
            fontFamily = LocalChatFontFamily.current,
            fontWeight = fontWeight,
            fontSize = (sizeSp * s).sp,
            lineHeight = (lineSp * s).sp,
            letterSpacing = letterSpacing
        )
    }

    @Composable
    private fun monoStyle(fontWeight: FontWeight, sizeSp: Int, lineSp: Int): TextStyle {
        val s = LocalChatFontScale.current
        return TextStyle(
            fontFamily = MonoFamily,
            fontWeight = fontWeight,
            fontSize = (sizeSp * s).sp,
            lineHeight = (lineSp * s).sp
        )
    }

    // Title tier
    // Brand wordmark in the new-chat capsule: prominent in the empty state, one
    // clean step above the active-conversation title (20 → 15).
    val brandTitle: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 20, 26)
    val sheetTitle: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 19, 25)
    // Active-conversation title: one step below the brand wordmark (16 → 15),
    // Bold so it still reads as a title against the 15sp Normal body.
    val conversationTitle: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 15, 20)

    // Active-conversation title when it stands alone (no token subtitle): a touch
    // smaller than the 20sp brand wordmark so a lone title doesn't read as loud.
    val conversationTitleSolo: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 17, 22)

    // Input tier
    val input: TextStyle @Composable get() = chatStyle(FontWeight.Normal, 16, 23, letterSpacing = 0.5.sp)

    // Body tier
    val body: TextStyle @Composable get() = chatStyle(FontWeight.Normal, 16, 24)
    val userBody: TextStyle @Composable get() = chatStyle(FontWeight.Normal, 14, 22)
    val thoughtBody: TextStyle @Composable get() = chatStyle(FontWeight.Normal, 13, 19)
    val thoughtTitle: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 13, 19)
    val errorBody: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 13, 18)

    // Meta tier
    val meta: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 12, 17)
    val metaNormal: TextStyle @Composable get() = chatStyle(FontWeight.Normal, 12, 17)
    val micro: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 11, 15)

    // Code / mono
    val code: TextStyle @Composable get() = monoStyle(FontWeight.Normal, 14, 20)
    val thoughtCode: TextStyle @Composable get() = monoStyle(FontWeight.Normal, 12, 17)
    val thoughtCodeLarge: TextStyle @Composable get() = monoStyle(FontWeight.Normal, 13, 19)

    // Sheet
    val detailTitle: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 22, 28)

    // Rating
    val ratingTitle: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 28, 35)

    // Drawer
    val conversationsTitle: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 25, 32)
    val drawerButton: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 14, 20)
    val drawerSearch: TextStyle @Composable get() = chatStyle(FontWeight.Normal, 16, 23)

    // Assistant markdown headings — even ~1.15 steps; h1 reined in (22, not 24)
    // so the jump from h2 stays proportional and h1 doesn't shout over 15sp body.
    val mdH1: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 22, 28)
    val mdH2: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 19, 25)
    val mdH3: TextStyle @Composable get() = chatStyle(FontWeight.SemiBold, 17, 23)
    val mdH4: TextStyle @Composable get() = chatStyle(FontWeight.SemiBold, 16, 22)
    val mdH5: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 15, 22)
    val mdH6: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 15, 22)

    // Thought-block headings — one tier below assistant markdown.
    val thH1: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 18, 23)
    val thH2: TextStyle @Composable get() = chatStyle(FontWeight.Bold, 16, 21)
    val thH3: TextStyle @Composable get() = chatStyle(FontWeight.SemiBold, 15, 20)
    val thH4: TextStyle @Composable get() = chatStyle(FontWeight.SemiBold, 14, 19)
    val thH5: TextStyle @Composable get() = chatStyle(FontWeight.Medium, 13, 19)
    val thH6: TextStyle @Composable get() = chatStyle(FontWeight.Normal, 13, 19)
}
