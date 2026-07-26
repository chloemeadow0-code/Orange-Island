package com.orangeisland.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Orange Island (橘子岛) fixed brand tokens.
 *
 * These are the locked visual values from the UI Playground v0.4 delivery.
 * They are used ONLY for fixed-tone artwork (decorative PNGs and the
 * `IslandIcon` badge background) whose color cannot be derived from the
 * active Material You color scheme. All other chrome continues to source its
 * color from [androidx.compose.material3.MaterialTheme.colorScheme], so the
 * brand only renders in full orange when the user picks the `ORANGE_ISLAND`
 * preset.
 */
object OrangeIslandTokens {

    // ── Brand palette (from styles.css :root) ──────────────────────────────
    val BrandOrange = Color(0xFFF28A34)   // --accent
    val BrandOrangeDeep = Color(0xFFB85825) // --accent-deep
    val BrandOrangeSoft = Color(0xFFFFE6CC) // --accent-soft
    val LeafGreen = Color(0xFF78966C)     // --leaf
    val LeafSoft = Color(0xFFE1EADB)      // --leaf-soft
    val Cream = Color(0xFFFFFBF5)         // --cream (background)
    val Paper = Color(0xFFFFFEFB)         // --paper (surface)
    val Sand = Color(0xFFF6EDE3)          // --sand
    val Ink = Color(0xFF3D2F27)           // --ink (main text)
    val Muted = Color(0xFF917F72)         // --muted (secondary text)
    val Line = Color(0x1A7C563A)          // --line, rgba(124,86,58,0.1)

    // ── Geometry (from styles.css :root + component rules) ─────────────────
    val RadiusLarge: Dp = 24.dp           // --radius (cards, settings rows)
    val RadiusBubble: Dp = 17.dp          // --radius * 0.72 (message bubbles)
    val RadiusComposer: Dp = 20.dp        // --radius * ~0.82
    val IconBadgeDefault: Dp = 42.dp      // .island-icon (38–45dp)
    val IconBadgeDefaultSize: Dp = 38.dp  // smaller variant for dense rows

    // ── Badge background ───────────────────────────────────────────────────
    /** Cream paper gradient for the circular IslandIcon badge (.island-icon). */
    fun badgeBrush() = Brush.linearGradient(
        0.0f to Color(0xFFFCF6EE),
        1.0f to Color(0xFFF7EDE1),
    )
}
