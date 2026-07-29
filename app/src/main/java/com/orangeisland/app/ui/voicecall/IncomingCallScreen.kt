package com.orangeisland.app.ui.voicecall

import android.media.RingtoneManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeisland.app.R
import com.orangeisland.app.viewmodel.VoiceCallGate

/**
 * Full-screen incoming-call UI, shown when the AI calls the user via `make_voice_call`. Renders a
 * phone-style ringing screen (pulsing avatar, caller = the AI, the reason, and big Accept/Decline
 * buttons) and plays the system ringtone while it's up. The screen is driven by [gate]'s pending
 * queue head; answering resolves the gate `true` (the call loop then starts in [VoiceCallScreen]),
 * declining resolves it `false` (the tool returns "declined" to the model).
 *
 * The ringtone is started/stopped in a [DisposableEffect] so it always stops when the call is
 * answered, declined, or the composition leaves. We use the system ringtone via the
 * [AudioManager] stream so the user's volume settings apply.
 */
@Composable
fun IncomingCallScreen(
    gate: VoiceCallGate,
    onAnswer: () -> Unit
) {
    val context = LocalContext.current
    val pending by gate.pending.collectAsState()
    val call = pending.firstOrNull() ?: return

    // ── Ringtone ─────────────────────────────────────────────────────────────
    DisposableEffect(call.id) {
        val ringtone = RingtoneManager.getRingtone(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        ringtone?.let {
            try { it.audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
            } catch (_: Exception) {}
            try { it.play() } catch (_: Exception) {}
        }
        onDispose {
            runCatching { ringtone?.stop() }
        }
    }

    // Pulse animation for the avatar while ringing.
    val infinite = rememberInfiniteTransition(label = "ringPulse")
    val pulse1 by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "p1"
    )
    val pulse2 by infinite.animateFloat(
        initialValue = 0.95f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    1f to MaterialTheme.colorScheme.background
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: caller identity
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = call.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.voice_call_incoming_ringing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Center: pulsing avatar
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulse2)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulse1)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Call, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Bottom: Accept / Decline
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = { gate.resolve(call.id, answered = false) },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = stringResource(R.string.voice_call_decline), modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.voice_call_decline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = {
                            // Resolve the gate first so the tool returns "answered", THEN start the
                            // call loop. Order matters: if we started the loop first the tool would
                            // still be suspended.
                            gate.resolve(call.id, answered = true)
                            onAnswer()
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = stringResource(R.string.voice_call_accept), modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.voice_call_accept), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
