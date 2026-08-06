package com.orangeisland.app.api.music

import java.io.File

/**
 * Abstraction for a cloud music-generation backend.
 *
 * Implementations mirror the [com.orangeisland.app.api.tts.TtsProvider] pattern:
 * each provider owns its own HTTP wiring, retry logic, and provider-specific parsing.
 */
interface MusicGenerationProvider {

    /** Unique provider id used for settings and routing, e.g. "suno", "replicate". */
    val id: String

    /** Human-readable label shown in settings. */
    val displayName: String

    /**
     * Submit a generation request to the backend. Must not block for the full duration;
     * it should return a [MusicJob] that the caller can poll.
     */
    suspend fun submit(config: MusicProviderConfig, request: MusicGenerationRequest): MusicJob

    /**
     * Poll the backend for an update to [job]. Implementations should return a new
     * [MusicJob] with the latest status and audio URL if available.
     */
    suspend fun poll(config: MusicProviderConfig, job: MusicJob): MusicJob

    /**
     * Download the audio file at [url] into [destination]. Returns the destination file.
     */
    suspend fun download(url: String, destination: File): File

    /**
     * Cancel any in-flight request for [job]. Optional; return false if cancellation
     * is not supported by the provider.
     */
    suspend fun cancel(job: MusicJob): Boolean = false
}
