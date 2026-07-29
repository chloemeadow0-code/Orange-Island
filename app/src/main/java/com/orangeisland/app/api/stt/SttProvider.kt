package com.orangeisland.app.api.stt

/**
 * Per-request configuration forwarded to an [SttProvider].
 *
 * Not every field is honoured by every backend:
 * - SiliconFlow: [model], [baseUrl]
 */
data class SttConfig(
    val model: String = "FunAudioLLM/SenseVoiceSmall",
    val baseUrl: String = ""
)

/**
 * Abstracts a third-party speech-to-text backend. Implementations are responsible
 * for their own HTTP wiring and error handling. [transcribe] returns the recognized
 * text, or null on failure.
 */
interface SttProvider {
    /**
     * Transcribe [audioBytes] into text using [config].
     *
     * @param audioBytes raw audio file bytes (e.g. M4A/AAC, MP3, WAV)
     * @param fileName   the file name (incl. extension) to send to the API
     * @param apiKey     provider API key
     * @param config     per-request options (model, baseUrl, …)
     * @return recognized text, or null on failure
     */
    suspend fun transcribe(
        audioBytes: ByteArray,
        fileName: String,
        apiKey: String,
        config: SttConfig = SttConfig()
    ): String?
}
