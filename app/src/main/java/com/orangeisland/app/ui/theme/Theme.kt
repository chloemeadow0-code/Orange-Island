package com.orangeisland.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File

enum class ThemeMode { LIGHT, DARK, FOLLOW_DEVICE }

/**
 * Returns the effective [FontFamily] for non-mono typography based on the font preference.
 */
@Composable
private fun effectiveFontFamily(
    fontPreference: String,
    customFontPath: String
): FontFamily = remember(fontPreference, customFontPath) {
    when (fontPreference) {
        "system" -> FontFamily.Default
        "custom" -> {
            val file = File(customFontPath)
            if (file.exists()) {
                try {
                    FontFamily(
                        Font(file, FontWeight.ExtraLight),
                        Font(file, FontWeight.Light),
                        Font(file, FontWeight.Normal),
                        Font(file, FontWeight.Medium),
                        Font(file, FontWeight.Bold),
                    )
                } catch (_: Exception) {
                    OutfitFamily
                }
            } else OutfitFamily
        }
        else -> OutfitFamily
    }
}

/**
 * Builds the [Typography] with the given [FontFamily] replacing all non-mono styles.
 */
private fun typographyWithFont(family: FontFamily, scale: Float): Typography {
    fun TextStyle.withFamilyAndScale(f: FontFamily, s: Float) = copy(
        fontFamily = f,
        fontSize = fontSize * s,
        lineHeight = lineHeight * s
    )
    return Typography.copy(
        displayLarge = Typography.displayLarge.withFamilyAndScale(family, scale),
        displayMedium = Typography.displayMedium.withFamilyAndScale(family, scale),
        displaySmall = Typography.displaySmall.withFamilyAndScale(family, scale),
        headlineLarge = Typography.headlineLarge.withFamilyAndScale(family, scale),
        headlineMedium = Typography.headlineMedium.withFamilyAndScale(family, scale),
        headlineSmall = Typography.headlineSmall.withFamilyAndScale(family, scale),
        titleLarge = Typography.titleLarge.withFamilyAndScale(family, scale),
        titleMedium = Typography.titleMedium.withFamilyAndScale(family, scale),
        titleSmall = Typography.titleSmall.withFamilyAndScale(family, scale),
        bodyLarge = Typography.bodyLarge.withFamilyAndScale(family, scale),
        bodyMedium = Typography.bodyMedium.withFamilyAndScale(family, scale),
        bodySmall = Typography.bodySmall.withFamilyAndScale(family, scale),
        labelLarge = Typography.labelLarge.withFamilyAndScale(family, scale),
        labelMedium = Typography.labelMedium.withFamilyAndScale(family, scale),
        labelSmall = Typography.labelSmall.withFamilyAndScale(family, scale),
    )
}

@Composable
fun OrangeIslandTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_DEVICE,
    colorSchemePreset: ColorSchemePreset = ColorSchemePreset.MIDNIGHT,
    schemeStyle: SchemeStyle = SchemeStyle.TONAL_SPOT,
    dynamicColor: Boolean = true,
    fontPreference: String = "app_default",
    customFontPath: String = "",
    fontScale: Float = 1.0f,
    customGlobalTextColor: Color? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.FOLLOW_DEVICE -> systemDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> remember(colorSchemePreset, schemeStyle, darkTheme) {
            colorSchemeForPreset(colorSchemePreset, schemeStyle, darkTheme)
        }
    }

    val finalColorScheme = if (customGlobalTextColor != null) {
        colorScheme.copy(
            onBackground = customGlobalTextColor,
            onSurface = customGlobalTextColor,
            onSurfaceVariant = customGlobalTextColor,
        )
    } else {
        colorScheme
    }

    val fontFamily = effectiveFontFamily(fontPreference, customFontPath)
    chatFontFamily = fontFamily
    chatFontScale = fontScale
    val typography = remember(fontFamily, fontScale) { typographyWithFont(fontFamily, fontScale) }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = typography,
        content = content
    )
}
