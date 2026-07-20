package com.orangeisland.app.ui.components

import android.graphics.Color
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Restores edge-to-edge / transparent navigation bar inside a Compose dialog window
 * ([ModalBottomSheet], [androidx.compose.ui.window.Dialog], popups).
 *
 * Such composables render in their **own** Window, which does not inherit the Activity window's
 * edge-to-edge config. On API 29+ the new window defaults `isNavigationBarContrastEnforced = true`,
 * so the system paints a translucent scrim behind the nav bar — making it look opaque even though
 * the Activity set it transparent. Call this as the first line inside the dialog/sheet content.
 */
@Composable
fun DialogWindowEdgeToEdge() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}
