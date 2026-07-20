package com.orangeisland.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Clears text-field focus (and dismisses the IME) when the user taps empty
 * space anywhere inside this container.
 *
 * Apply to the **root container** of any screen, dialog, or bottom sheet that
 * hosts a [androidx.compose.material3.TextField] / `OutlinedTextField`. The tap
 * is detected via [detectTapGestures], so taps consumed by children — buttons,
 * the field itself, scroll gestures — are left untouched; only taps that land on
 * otherwise-empty area clear focus. Unlike `clickable`, this adds no button
 * semantics to the accessibility tree.
 *
 * For dialogs, attach this to the outermost content column (the one that also
 * holds the title / padding) rather than a tight wrapper around the field, so
 * there is real empty area available to absorb the tap.
 */
fun Modifier.clearFocusOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
}
