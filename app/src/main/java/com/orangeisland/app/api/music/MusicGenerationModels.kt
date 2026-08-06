package com.orangeisland.app.api.music

import kotlinx.serialization.Serializable

/**
 * A single request to generate a music track from lyrics / prompt / style.
 */
@Serializable
data class MusicGenerationRequest(
    val title: String,
    val lyrics: String,
    val style: String? = null,
    val makeInstrumental: Boolean = false,
    val model: String? = null,
    val custom: Map<String, String> = emptyMap()
)

/**
 * Status of a music generation job returned by a provider.
 */
enum class MusicJobStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILURE
}

/**
 * A job handle produced when a provider accepts a generation request.
 */
@Serializable
data class MusicJob(
    val id: String,
    val provider: String,
    val status: MusicJobStatus = MusicJobStatus.PENDING,
    val audioUrl: String? = null,
    val errorMessage: String? = null,
    val rawResponse: String? = null
)

/**
 * Result returned by [MusicGenerationProvider.poll] after a job reaches a terminal state.
 */
@Serializable
data class MusicGenerationResult(
    val job: MusicJob,
    val audioUrl: String? = null
)

/**
 * Configuration common to every provider. Provider-specific values live in [custom].
 */
@Serializable
data class MusicProviderConfig(
    val provider: String,
    val apiKey: String,
    val baseUrl: String? = null,
    val model: String? = null,
    val custom: Map<String, String> = emptyMap()
)
