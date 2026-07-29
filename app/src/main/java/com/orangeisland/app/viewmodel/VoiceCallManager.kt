package com.orangeisland.app.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.orangeisland.app.api.LlmProvider
import com.orangeisland.app.api.ProviderConfig
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.api.stt.SiliconFlowSttProvider
import com.orangeisland.app.api.stt.SttConfig
import com.orangeisland.app.api.stt.SttProvider
import com.orangeisland.app.api.tts.TtsConfig
import com.orangeisland.app.api.tts.TtsProvider
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Drives the full-duplex "AI phone call" loop:
 *
 *  `speak(aiText)` → LISTEN → VAD detects speech → RECORD → silence → STT → LLM → SPEAK → …
 *
 * Each stage publishes its progress to [state], [subtitle], and [transcript] so the call UI can
 * render a live caption. The loop runs on a dedicated [scope]; [hangUp] cancels it and releases
 * the mic/player. Recording uses [MediaRecorder] (M4A/AAC) with a coarse amplitude-based VAD
 * (no external audio library); playback uses [MediaPlayer]. Designed to be created per call and
 * discarded on hang-up — it is NOT a long-lived singleton.
 *
 * Independent implementation. Built to compose the existing [SttProvider], [TtsProvider], and
 * [LlmProvider] abstractions without touching their internals.
 */
class VoiceCallManager(
    private val app: Application
) {
    /** Coarse phase of the call loop, rendered as the big status in the call UI. */
    enum class CallState { IDLE, SPEAKING, LISTENING, RECORDING, THINKING, ENDED, ERROR }

    /** A single turn in the live transcript shown as captions on the call screen. */
    data class Turn(val speaker: Participant, val text: String)

    private val _state = MutableStateFlow(CallState.IDLE)
    val state: StateFlow<CallState> = _state.asStateFlow()

    /** Short human label for the current phase, e.g. "正在说话…". */
    private val _subtitle = MutableStateFlow("")
    val subtitle: StateFlow<String> = _subtitle.asStateFlow()

    /** Rolling transcript of the call (oldest → newest), capped to avoid unbounded growth. */
    private val _transcript = MutableStateFlow<List<Turn>>(emptyList())
    val transcript: StateFlow<List<Turn>> = _transcript.asStateFlow()

    /** Live mic amplitude (0..~32767) while recording/listening — drives the waveform animation. */
    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    /** Last error message, surfaced when state == ERROR. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val sttProvider: SttProvider = SiliconFlowSttProvider()
    private var ttsProvider: TtsProvider? = null

    private var callScope: CoroutineScope? = null
    private var loopJob: Job? = null
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null

    // ── VAD tuning ──────────────────────────────────────────────────────────
    /** Amplitude (out of 32767) above which we consider the user to be speaking. */
    private val speechThreshold = 800
    /** How long (ms) of continuous sub-threshold audio ends a recording. */
    private val silenceEndMs = 1500L
    /** After the AI finishes speaking, listen this long for the user to start before giving up. */
    private val maxListenMs = 25_000L
    /** Log the sampled amplitude every N ms while listening, for VAD tuning. */
    private var lastAmpLog = 0L

    /**
     * Begin the call. When [generateGreeting] is non-null the AI generates its own opening line
     * (so it sounds natural instead of canned); [greeting] is a fixed fallback used only if the
     * LLM call fails or returns blank. The loop then alternates STT/LLM/TTS until [hangUp]. The
     * provider/credential callbacks are read fresh each turn so a settings change mid-call takes
     * effect on the next exchange.
     */
    fun start(
        greeting: String?,
        generateGreeting: (suspend () -> String?)?,
        ttsProvider: TtsProvider?,
        resolveTtsConfig: () -> TtsConfig,
        resolveTtsApiKey: () -> String,
        resolveVoiceId: () -> String?,
        resolveSttConfig: () -> SttConfig,
        resolveSttApiKey: () -> String,
        generateReply: suspend (userText: String) -> String?
    ) {
        if (loopJob?.isActive == true) return
        this.ttsProvider = ttsProvider
        val scope = CoroutineScope(Dispatchers.Default)
        callScope = scope
        _state.value = CallState.IDLE
        _error.value = null
        _transcript.value = emptyList()
        loopJob = scope.launch {
            try {
                // Opening line: prefer an AI-generated greeting, fall back to the fixed text.
                val opener = generateGreeting?.let {
                    runCatching { it() }.getOrNull()?.takeIf { g -> g.isNotBlank() }
                } ?: greeting
                if (!opener.isNullOrBlank()) {
                    pushTurn(Participant.MODEL, opener)
                    speak(opener, ttsProvider, resolveTtsConfig(), resolveTtsApiKey(), resolveVoiceId())
                }
                while (isActive) {
                    if (!isActive) break
                    val userText = listenAndTranscribe(resolveSttConfig(), resolveSttApiKey())
                    if (!isActive) break
                    if (userText.isNullOrBlank()) {
                        // Nothing said within the listen window — keep the call alive but nudge.
                        _subtitle.value = "（没听到，再说一次？）"
                        delay(800)
                        continue
                    }
                    pushTurn(Participant.USER, userText)
                    _state.value = CallState.THINKING
                    _subtitle.value = "思考中…"
                    val reply = try {
                        generateReply(userText)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        DebugLog.e("VoiceCall", "LLM reply failed", e)
                        _error.value = e.message
                        "抱歉，我没能理解，能再说一遍吗？"
                    }
                    if (!isActive) break
                    val replyText = reply?.takeIf { it.isNotBlank() }
                        ?: "抱歉，我没有回应内容，请再说一遍。"
                    pushTurn(Participant.MODEL, replyText)
                    if (!isActive) break
                    speak(replyText, ttsProvider, resolveTtsConfig(), resolveTtsApiKey(), resolveVoiceId())
                }
            } catch (_: CancellationException) {
                // normal hang-up
            } catch (e: Exception) {
                DebugLog.e("VoiceCall", "call loop crashed", e)
                _state.value = CallState.ERROR
                _error.value = e.message ?: "通话出错"
            } finally {
                _state.value = CallState.ENDED
                withContext(NonCancellable) { releaseAudio() }
            }
        }
    }

    /** End the call immediately and release the mic/player. Safe to call multiple times. */
    fun hangUp() {
        loopJob?.cancel()
        callScope?.cancel()
        loopJob = null
        callScope = null
        releaseAudio()
        if (_state.value != CallState.ERROR) _state.value = CallState.ENDED
        _subtitle.value = "通话已结束"
    }

    // ── Stages ──────────────────────────────────────────────────────────────

    /** Synthesize [text] via TTS and play it back, blocking until playback completes. */
    private suspend fun speak(
        text: String,
        ttsProvider: TtsProvider?,
        config: TtsConfig,
        apiKey: String,
        voiceId: String?
    ) {
        val provider = ttsProvider
        if (provider == null || apiKey.isBlank()) {
            // No TTS configured — skip audio but keep the transcript so the call isn't dead air.
            DebugLog.w("VoiceCall", "TTS not configured; skipping playback")
            _subtitle.value = text
            delay(400)
            return
        }
        _state.value = CallState.SPEAKING
        _subtitle.value = text
        val bytes = try {
            provider.synthesize(text, voiceId, apiKey, config)
        } catch (e: Exception) {
            DebugLog.e("VoiceCall", "TTS synthesis failed", e)
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            DebugLog.w("VoiceCall", "TTS returned no audio")
            delay(400)
            return
        }
        playBytes(bytes)
    }

    /** Play raw audio bytes synchronously (suspends until onCompletion). */
    private suspend fun playBytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val tmp = File(app.cacheDir, "tts_play_${UUID.randomUUID()}.mp3")
        try {
            tmp.outputStream().use { it.write(bytes) }
            val mp = MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                prepare()
            }
            player = mp
            // Gate playback completion so the coroutine resumes right when audio ends.
            suspendCancellableCoroutine<Unit> { cont ->
                mp.setOnCompletionListener {
                    cont.resume(Unit)
                }
                mp.setOnErrorListener { _, _, _ ->
                    DebugLog.e("VoiceCall", "MediaPlayer error")
                    cont.resume(Unit)
                    true
                }
                cont.invokeOnCancellation { runCatching { mp.release() } }
                mp.start()
            }
        } finally {
            runCatching { player?.release() }
            player = null
            tmp.delete()
        }
    }

    /**
     * Listen for the user to start speaking (VAD), record until they pause, then return the STT
     * transcript. Returns null if no speech is detected within [maxListenMs].
     *
     * One recorder session covers the whole listen+record window: the recorder runs continuously
     * while we sample [MediaRecorder.maxAmplitude]; when amplitude crosses [speechThreshold] we
     * mark speech started, and when it stays below threshold for [silenceEndMs] we stop the
     * recorder and hand the file to STT. The recorder is started and stopped exactly once per call
     * to this method (no double-stop), so the file is intact when transcribed.
     */
    private suspend fun listenAndTranscribe(sttConfig: SttConfig, sttApiKey: String): String? {
        _state.value = CallState.LISTENING
        _subtitle.value = "请说…"
        startRecorder()
        try {
            val listenStart = System.currentTimeMillis()
            var speaking = false
            var lastLoud = System.currentTimeMillis()
            while (currentCoroutineContext().isActive) {
                val amp = sampleAmplitude()
                _amplitude.value = amp
                // Low-frequency amplitude log for VAD tuning (every ~1s).
                val now = System.currentTimeMillis()
                if (now - lastAmpLog > 1000) {
                    lastAmpLog = now
                    android.util.Log.e("VoiceCallDebug", "amp=$amp speaking=$speaking threshold=$speechThreshold")
                }
                if (amp > speechThreshold) {
                    if (!speaking) {
                        speaking = true
                        _state.value = CallState.RECORDING
                        _subtitle.value = "正在聆听…"
                    }
                    lastLoud = System.currentTimeMillis()
                } else if (speaking) {
                    // Already speaking; if quiet long enough, finish the recording.
                    if (System.currentTimeMillis() - lastLoud >= silenceEndMs) {
                        android.util.Log.e("VoiceCallDebug", "silence ended recording, stopping")
                        break
                    }
                }
                // No speech yet within the listen window → give up this turn.
                if (!speaking && System.currentTimeMillis() - listenStart > maxListenMs) {
                    android.util.Log.e("VoiceCallDebug", "no speech detected in listen window")
                    return null
                }
                delay(80)
            }
            // Stop the recorder once and capture the file.
            val file = stopRecorder(keepFile = true)
                ?: run {
                    android.util.Log.e("VoiceCallDebug", "recorder produced no file")
                    return null
                }
            val size = file.length()
            android.util.Log.e("VoiceCallDebug", "recorded file size=$size bytes")
            return transcribe(file, sttConfig, sttApiKey)
        } finally {
            // Recorder was already stopped above; this just guarantees cleanup if we returned early.
            releaseRecorder()
            _amplitude.value = 0
        }
    }

    /** Send the recorded file to STT and return the recognized text. */
    private suspend fun transcribe(file: File, sttConfig: SttConfig, sttApiKey: String): String? {
        if (sttApiKey.isBlank()) {
            android.util.Log.e("VoiceCallDebug", "STT: no api key")
            _error.value = "未配置硅基流动 API Key"
            return null
        }
        _state.value = CallState.THINKING
        _subtitle.value = "识别中…"
        return try {
            val bytes = file.readBytes()
            android.util.Log.e("VoiceCallDebug", "STT: sending ${bytes.size} bytes, model=${sttConfig.model}, baseUrl=${sttConfig.baseUrl}")
            val result = sttProvider.transcribe(bytes, file.name, sttApiKey, sttConfig)
            android.util.Log.e("VoiceCallDebug", "STT: result=${result?.take(100)}")
            result?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            DebugLog.e("VoiceCall", "STT failed", e)
            android.util.Log.e("VoiceCallDebug", "STT: exception ${e.message}")
            null
        } finally {
            runCatching { file.delete() }
            recordingFile = null
        }
    }

    // ── Recorder helpers ────────────────────────────────────────────────────

    /** Start a fresh MediaRecorder writing M4A/AAC to a temp file under cacheDir. */
    private fun startRecorder() {
        releaseRecorder()
        val file = File(app.cacheDir, "voice_call_${UUID.randomUUID()}.m4a")
        recordingFile = file
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(app)
        } else {
            MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(16_000)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
                android.util.Log.e("VoiceCallDebug", "recorder started: ${file.absolutePath}")
            } catch (e: Exception) {
                DebugLog.e("VoiceCall", "MediaRecorder prepare/start failed", e)
                android.util.Log.e("VoiceCallDebug", "recorder start FAILED: ${e.message}")
                runCatching { release() }
                throw e
            }
        }
        recorder = rec
    }

    /**
     * Stop the active recorder and return the produced file. [keepFile] is true during the call
     * loop (we still need the bytes for STT); false on teardown.
     */
    private fun stopRecorder(keepFile: Boolean): File? {
        val rec = recorder ?: return recordingFile?.takeIf { keepFile }
        val file = recordingFile
        // stop() can throw if the recorder was started/stopped in quick succession (e.g. no audio
        // frame produced). Treat that as "no recording" rather than crashing the call.
        runCatching {
            rec.stop()
        }.onFailure { DebugLog.w("VoiceCall", "MediaRecorder.stop failed: ${it.message}") }
        runCatching { rec.release() }
        recorder = null
        if (!keepFile) {
            runCatching { file?.delete() }
            recordingFile = null
            return null
        }
        return file
    }

    /** Sample the current mic input level (max amplitude since last sample). */
    private fun sampleAmplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Exception) {
        0
    }

    private fun releaseRecorder() {
        recorder?.let { rec ->
            runCatching { if (Build.VERSION.SDK_INT >= 31) rec.pause() }
            runCatching { rec.release() }
        }
        recorder = null
    }

    /** Release every audio resource held by this manager. */
    private fun releaseAudio() {
        releaseRecorder()
        runCatching { player?.release() }
        player = null
        recordingFile?.let { runCatching { it.delete() } }
        recordingFile = null
    }

    private fun pushTurn(speaker: Participant, text: String) {
        val current = _transcript.value.toMutableList()
        current.add(Turn(speaker, text))
        // Cap the in-memory transcript so a long call doesn't grow unbounded.
        if (current.size > 40) {
            _transcript.value = current.takeLast(30)
        } else {
            _transcript.value = current
        }
    }
}
