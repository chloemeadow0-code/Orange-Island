package com.orangeisland.app.ui.chat

import androidx.compose.runtime.compositionLocalOf
import com.orangeisland.app.tool.CameraToolGate

/**
 * CompositionLocal that provides the shared [CameraToolGate] down to the message-list
 * composables (e.g. [CompactSegmentBlock]) so a `take_photo` tool card can render its
 * own CameraX preview without threading the gate through 6 layers of parameters.
 */
val LocalCameraToolGate = compositionLocalOf<CameraToolGate?> { null }
