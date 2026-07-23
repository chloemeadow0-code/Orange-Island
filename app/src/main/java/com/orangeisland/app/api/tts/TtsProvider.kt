package com.orangeisland.app.api.tts

/**
 * Per-request configuration forwarded to a [TtsProvider].
 *
 * Not all fields are honoured by every provider:
 * - ElevenLabs: [model], [speed], [outputFormat], [stability], [style]
 * - MiniMax:    [model], [speed], [outputFormat], [volume], [pitch]
 */
data class TtsConfig(
    val model: String = "",
    val speed: Float = 1.0f,
    val outputFormat: String = "",
    val stability: Float = 0.5f,
    val similarityBoost: Float = 0.75f,
    val style: Float = 0.0f,
    val volume: Float = 1.0f,
    val pitch: Float = 0.0f
)

/**
 * Abstracts a third-party text-to-speech backend. Implementations are responsible
 * for their own HTTP wiring, retry logic, and error handling.
 */
interface TtsProvider {
    /** Synthesize [text] into audio bytes using [config]. Returns null on failure. */
    suspend fun synthesize(
        text: String,
        voiceId: String?,
        apiKey: String,
        config: TtsConfig = TtsConfig()
    ): ByteArray?
}
