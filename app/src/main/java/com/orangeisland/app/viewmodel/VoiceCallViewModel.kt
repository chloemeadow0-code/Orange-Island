package com.orangeisland.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orangeisland.app.api.tts.ElevenLabsTtsProvider
import com.orangeisland.app.api.tts.MinimaxTtsProvider
import com.orangeisland.app.api.tts.TtsConfig
import com.orangeisland.app.api.tts.TtsProvider
import com.orangeisland.app.api.stt.SttConfig
import com.orangeisland.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel backing the full-screen "AI 语音通话" page. Owns one [VoiceCallManager] per call and
 * bridges it to the app's settings (STT key/model from the new SiliconFlow page; TTS provider/key
 * from the existing TTS page) and to [ChatViewModel.generateVoiceReply] for the LLM leg.
 *
 * TTS/STT credentials are read fresh on every turn via the `resolve*` closures handed to the
 * manager, so a settings change mid-call is picked up on the next exchange without restarting.
 */
class VoiceCallViewModel(
    private val app: Application,
    private val settings: SettingsRepository,
    private val chatViewModel: ChatViewModel
) : ViewModel() {

    private val manager = VoiceCallManager(app)
    /** Guards against saving the transcript twice (the hang-up button + onDispose both call hangUp). */
    private var transcriptSaved = false

    val state: StateFlow<VoiceCallManager.CallState> = manager.state
    val subtitle: StateFlow<String> = manager.subtitle
    val transcript: StateFlow<List<VoiceCallManager.Turn>> = manager.transcript
    val amplitude: StateFlow<Int> = manager.amplitude
    val error: StateFlow<String?> = manager.error

    /** Whether the call can even start: STT must be configured (key present). Used by the UI to
     *  show a "configure STT first" hint instead of a dead call. */
    val sttConfigured: StateFlow<Boolean> = settings.sttApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val elevenLabs: TtsProvider = ElevenLabsTtsProvider()
    private val minimax: TtsProvider = MinimaxTtsProvider()

    /** Begin the call. No-op if already running. */
    fun startCall() {
        transcriptSaved = false
        manager.start(
            greeting = null,
            generateGreeting = { chatViewModel.generateVoiceGreeting() },
            ttsProvider = resolveTtsProvider(),
            resolveTtsConfig = { resolveTtsConfig() },
            resolveTtsApiKey = { settings.ttsApiKey.value },
            resolveVoiceId = { settings.ttsVoiceId.value.ifBlank { null } },
            resolveSttConfig = { resolveSttConfig() },
            resolveSttApiKey = { settings.sttApiKey.value },
            generateReply = { chatViewModel.generateVoiceReply(it) }
        )
    }

    fun hangUp() {
        // Snapshot the transcript before hanging up (the manager clears it), then persist it so the
        // call's content is remembered in chat history. Guarded so a double hangUp (button + leave)
        // never saves twice.
        val snapshot = if (transcriptSaved) emptyList() else manager.transcript.value.map { turn ->
            // Label as "用户" for the human and the app name for the AI, so when the LLM reads this
            // transcript back as context it can tell the two speakers apart unambiguously (it would
            // misread a bare "我" as itself).
            (if (turn.speaker == com.orangeisland.app.model.Participant.USER) "用户" else app.getString(com.orangeisland.app.R.string.app_name)) to turn.text
        }
        transcriptSaved = true
        manager.hangUp()
        android.util.Log.e("VoiceCallDebug", "hangUp: snapshot size=${snapshot.size}")
        if (snapshot.isNotEmpty()) {
            viewModelScope.launch {
                runCatching { chatViewModel.saveCallTranscript(snapshot) }
            }
        }
    }

    private fun resolveTtsProvider(): TtsProvider? = when (settings.ttsProvider.value.lowercase()) {
        "minimax" -> minimax
        "elevenlabs" -> elevenLabs
        else -> elevenLabs
    }

    private fun resolveTtsConfig(): TtsConfig = TtsConfig(
        model = settings.ttsModel.value,
        speed = settings.ttsSpeed.value,
        outputFormat = settings.ttsOutputFormat.value,
        stability = settings.ttsStability.value,
        similarityBoost = settings.ttsSimilarityBoost.value,
        style = settings.ttsStyle.value,
        volume = settings.ttsVolume.value,
        pitch = settings.ttsPitch.value
    )

    private fun resolveSttConfig(): SttConfig = SttConfig(
        model = settings.sttModel.value.ifBlank { "FunAudioLLM/SenseVoiceSmall" },
        baseUrl = settings.sttBaseUrl.value
    )

    override fun onCleared() {
        super.onCleared()
        manager.hangUp()
    }
}
