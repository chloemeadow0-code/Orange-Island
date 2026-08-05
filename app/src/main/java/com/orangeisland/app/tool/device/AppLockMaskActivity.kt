package com.orangeisland.app.tool.device

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeisland.app.R
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.ui.theme.ColorSchemePreset
import com.orangeisland.app.ui.theme.OrangeIslandTheme
import com.orangeisland.app.ui.theme.SchemeStyle
import com.orangeisland.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first

/**
 * Full-screen mask shown over a locked app. Uses [OrangeIslandTheme] so the lock screen
 * follows the user's color scheme / dark-mode / font preferences — it should look like
 * a first-party part of Orange Island, not a foreign overlay.
 *
 * Only the AI can unlock apps (via unlock_app). There is no PIN escape hatch: the user
 * must return to Orange Island and ask the assistant to lift the lock.
 */
class AppLockMaskActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake the screen / show over lock screen when needed.
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val label = intent.getStringExtra(EXTRA_LABEL) ?: packageName
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        // Load the user's theme settings once (synchronously on create; this is a tiny read).
        val settings = SettingsManager(applicationContext)
        data class ThemeSettings(
            val themeMode: String,
            val colorScheme: String,
            val schemeStyle: String,
            val dynamicColor: Boolean,
            val fontPreference: String,
            val customFontPath: String
        )
        val ts = kotlinx.coroutines.runBlocking {
            ThemeSettings(
                themeMode = settings.themeMode.first(),
                colorScheme = settings.colorScheme.first(),
                schemeStyle = settings.schemeStyle.first(),
                dynamicColor = settings.dynamicColor.first(),
                fontPreference = settings.fontPreference.first(),
                customFontPath = settings.customFontPath.first()
            )
        }

        setContent {
            val themeModeEnum = runCatching { ThemeMode.valueOf(ts.themeMode) }
                .getOrDefault(ThemeMode.FOLLOW_DEVICE)
            val preset = runCatching { ColorSchemePreset.valueOf(ts.colorScheme) }
                .getOrDefault(ColorSchemePreset.ORANGE_ISLAND)
            val style = runCatching { SchemeStyle.valueOf(ts.schemeStyle) }
                .getOrDefault(SchemeStyle.TONAL_SPOT)

            OrangeIslandTheme(
                themeMode = themeModeEnum,
                colorSchemePreset = preset,
                schemeStyle = style,
                dynamicColor = ts.dynamicColor,
                fontPreference = ts.fontPreference,
                customFontPath = ts.customFontPath
            ) {
                MaskScreen(label = label, message = message)
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_LABEL = "label"
        const val EXTRA_MESSAGE = "message"
    }
}

@Composable
private fun MaskScreen(label: String, message: String) {
    // Swallow the system back gesture/button: there is no PIN escape hatch, so letting the user
    // back out of the mask would defeat the lock. (The app can still be left via Recents / Home,
    // which AppLockAccessibilityService handles by re-showing the mask.)
    BackHandler(enabled = true) { /* intentionally empty: consume back press */ }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = label,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_lock_mask_subtitle),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = message,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.app_lock_mask_hint),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
