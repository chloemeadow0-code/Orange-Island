package com.orangeisland.app.ui.chat.bottombar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.orangeisland.app.R
import com.orangeisland.app.model.SelectedAttachment
import com.orangeisland.app.ui.common.LocalOrangeIslandHaptics

/**
 * The composer's send / stop / pending-send FAB. Owns the "wait for attachment
 * processing then auto-send" handshake and the icon cross-fade between the three
 * states. Extracted from [ChatBottomBar].
 */
@Composable
internal fun ComposerSendButton(
    textFieldState: TextFieldState,
    composer: ChatComposerState,
    isLoading: Boolean,
    isSwitching: Boolean,
    isModelValid: Boolean,
    onSendMessage: (String, List<SelectedAttachment>) -> Boolean,
    onStopGeneration: () -> Unit,
    onCollapse: () -> Unit,
) {
    val haptics = LocalOrangeIslandHaptics.current
    // Pending send: wait for processing to finish, then auto-send
    val anyProcessing = composer.processingStates.isNotEmpty()
    LaunchedEffect(composer.pendingSend, anyProcessing) {
        if (composer.pendingSend && !anyProcessing) {
            if (onSendMessage(textFieldState.text.toString(), composer.selectedAttachments)) {
                composer.clearAttachments()
                textFieldState.edit { replace(0, length, "") }
                onCollapse()
            }
            composer.pendingSend = false
        }
    }
    val canSend = (textFieldState.text.isNotBlank() || composer.selectedAttachments.isNotEmpty()) && !isLoading && isModelValid && !isSwitching
    val isActionable = (isLoading || canSend || composer.pendingSend) && !isSwitching
    FloatingActionButton(
        onClick = {
            if (isSwitching) return@FloatingActionButton
            if (isLoading) onStopGeneration()
            else if (composer.pendingSend) {
                haptics.selection()
                composer.pendingSend = false
            }
            else if (canSend) {
                if (anyProcessing) {
                    haptics.action()
                    composer.pendingSend = true
                } else {
                    if (onSendMessage(textFieldState.text.toString(), composer.selectedAttachments)) {
                        composer.clearAttachments()
                        textFieldState.edit { replace(0, length, "") }
                        onCollapse()
                    }
                }
            }
        },
        containerColor = animateColorAsState(if (isActionable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, tween(400), label = "fabContainer").value,
        contentColor = animateColorAsState(if (isActionable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(400), label = "fabContent").value,
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        val fabIcon = when {
            composer.pendingSend -> "pending"
            isLoading -> "stop"
            else -> "send"
        }
        AnimatedContent(
            targetState = fabIcon,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "fabIcon"
        ) { state ->
            when (state) {
                "pending" -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                "stop" -> Icon(Icons.Default.Stop, stringResource(R.string.action), modifier = Modifier.size(24.dp))
                else -> Icon(Icons.Default.ArrowUpward, stringResource(R.string.action), modifier = Modifier.size(24.dp))
            }
        }
    }
}
