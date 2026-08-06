package com.orangeisland.app.api.music

import java.io.File

/**
 * Configuration for a voice-conversion backend (e.g. Replicate RVC).
 */
data class VoiceConversionConfig(
    val provider: String,
    val apiKey: String,
    val modelVersion: String? = null,
    val voiceModelUrl: String? = null,
    val custom: Map<String, String> = emptyMap()
)

/**
 * Abstraction for a voice-conversion backend that replaces the voice in an existing
 * audio track (e.g. RVC over a Suno-generated song).
 */
interface VoiceConversionProvider {

    /** Unique provider id, e.g. "replicate_rvc". */
    val id: String

    /** Human-readable label. */
    val displayName: String

    /**
     * Convert the audio at [sourceUrl] and return the URL of the converted audio.
     * May involve submission and polling, so implementations must handle their own HTTP.
     */
    suspend fun convert(config: VoiceConversionConfig, sourceUrl: String): String

    /**
     * Download the converted audio at [url] into [destination].
     */
    suspend fun download(url: String, destination: File): File

    /**
     * Cancel any in-flight conversion. Optional.
     */
    suspend fun cancel(): Boolean = false
}
