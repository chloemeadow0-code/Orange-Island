package com.orangeisland.app.ui.voicecall

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orangeisland.app.R
import com.orangeisland.app.model.Participant
import com.orangeisland.app.viewmodel.ChatViewModel
import com.orangeisland.app.viewmodel.VoiceCallManager
import com.orangeisland.app.viewmodel.VoiceCallViewModel

/**
 * Full-screen "AI 语音通话" page. Handles the RECORD_AUDIO runtime permission request up front,
 * shows a "configure STT first" gate when no SiliconFlow key is set, then renders the call UI:
 * a phase status, a live transcript, a speaking/listening avatar with a pulse animation, and a
 * single big hang-up button. Mounting lives in [com.orangeisland.app.MainActivity] (mirrors the
 * Health-page AnimatedVisibility block).
 */
@Composable
fun VoiceCallScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = remember {
        (context.applicationContext as com.orangeisland.app.OrangeIslandApplication).container
    }
    val factory = remember { container.voiceCallViewModelFactory(viewModel) }
    val voiceViewModel: VoiceCallViewModel = viewModel(factory = factory)

    val state by voiceViewModel.state.collectAsState()
    val subtitle by voiceViewModel.subtitle.collectAsState()
    val transcript by voiceViewModel.transcript.collectAsState()
    val amplitude by voiceViewModel.amplitude.collectAsState()
    val error by voiceViewModel.error.collectAsState()
    val sttConfigured by voiceViewModel.sttConfigured.collectAsState()

    // ── RECORD_AUDIO runtime permission ─────────────────────────────────────
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    // Ask once on entry (only if not yet decided); re-check on resume via the state holder.
    LaunchedEffect(Unit) {
        if (!permissionGranted) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Auto-hang-up when leaving the screen so the mic is always released.
    DisposableEffect(Unit) {
        onDispose { voiceViewModel.hangUp() }
    }

    // The user already answered the incoming call to get here, so start the loop automatically as
    // soon as the mic permission is in place (no manual "start" button in this entry path).
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && state == VoiceCallManager.CallState.IDLE) {
            voiceViewModel.startCall()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gate screens: permission or STT config missing.
            if (!permissionGranted) {
                PermissionGate(
                    message = stringResource(R.string.voice_call_perm_denied),
                    onRetry = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onClose = onBack
                )
                return@Column
            }
            if (!sttConfigured && state == VoiceCallManager.CallState.IDLE) {
                ConfigGate(
                    message = stringResource(R.string.voice_call_stt_not_configured),
                    onClose = onBack
                )
                return@Column
            }

            // Status line
            Text(
                text = stateLabel(state, error),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
            )

            // Live transcript (scrolls; auto-follows new turns)
            val listState = rememberLazyListState()
            LaunchedEffect(transcript.size) {
                if (transcript.isNotEmpty()) {
                    listState.animateScrollToItem(transcript.lastIndex)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(transcript, key = { it.speaker.name + "_" + it.text.hashCode() + "_" + transcript.indexOf(it) }) { turn ->
                    TranscriptBubble(turn)
                }
            }

            // Subtitle (current phase detail / what's being said)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // Avatar + pulse
            CallAvatar(state = state, amplitude = amplitude)

            Spacer(modifier = Modifier.height(28.dp))

            // Controls: a single hang-up button. The call starts automatically on entry (the user
            // already answered the incoming call), so there is no start button — only end.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = {
                        voiceViewModel.hangUp()
                        onBack()
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = stringResource(R.string.voice_call_end), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun stateLabel(state: VoiceCallManager.CallState, error: String?): String = when (state) {
    VoiceCallManager.CallState.IDLE -> stringResource(R.string.voice_call_state_idle)
    VoiceCallManager.CallState.SPEAKING -> stringResource(R.string.voice_call_state_speaking)
    VoiceCallManager.CallState.LISTENING -> stringResource(R.string.voice_call_state_listening)
    VoiceCallManager.CallState.RECORDING -> stringResource(R.string.voice_call_state_recording)
    VoiceCallManager.CallState.THINKING -> stringResource(R.string.voice_call_state_thinking)
    VoiceCallManager.CallState.ENDED -> stringResource(R.string.voice_call_state_ended)
    VoiceCallManager.CallState.ERROR -> error ?: stringResource(R.string.voice_call_state_error)
}

/** Pulsing avatar that grows with mic amplitude while recording. */
@Composable
private fun CallAvatar(state: VoiceCallManager.CallState, amplitude: Int) {
    // Continuous pulse while speaking/listening so the UI feels alive.
    val infinite = rememberInfiniteTransition(label = "avatarPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    // Amplitude-driven scale while recording (0..~32767 → +0..0.25).
    val ampScale = 1f + (amplitude.coerceIn(0, 6000) / 6000f) * 0.25f
    val scale = if (state == VoiceCallManager.CallState.RECORDING) ampScale else pulse

    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer halo
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        )
        // Inner disc
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(if (state == VoiceCallManager.CallState.RECORDING) ampScale else 1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state == VoiceCallManager.CallState.SPEAKING) Icons.Default.Call else Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun TranscriptBubble(turn: VoiceCallManager.Turn) {
    val isUser = turn.speaker == Participant.USER
    val align = if (isUser) Alignment.End else Alignment.Start
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                    text = if (isUser) stringResource(R.string.voice_call_you) else stringResource(R.string.app_name),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Mic, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.voice_call_perm_retry)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) { Text(stringResource(R.string.voice_call_close)) }
    }
}

@Composable
private fun ConfigGate(message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Mic, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onClose) { Text(stringResource(R.string.voice_call_close)) }
    }
}
