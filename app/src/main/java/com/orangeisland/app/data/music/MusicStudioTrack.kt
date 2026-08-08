package com.orangeisland.app.data.music

import kotlinx.serialization.Serializable

/**
 * A generated music track stored in the local music studio library.
 *
 * Voice-replaced (RVC) versions are no longer produced automatically during generation. Instead they
 * are attached here as [voiceVersions], each one a separate RVC run of the original [audioUrl], so a
 * track can carry several voice variants and the original is always preserved.
 */
@Serializable
data class MusicStudioTrack(
    val id: String,
    val title: String,
    val lyrics: String = "",
    val style: String = "",
    val provider: String,
    val audioUrl: String,
    val localPath: String = "",
    val hasVoiceReplacement: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val voiceVersions: List<VoiceVersion> = emptyList()
)

/**
 * A voice-replaced (RVC) variant of a [MusicStudioTrack]. Produced on demand from the track's
 * original [MusicStudioTrack.audioUrl] via Replicate RVC, using the voice model at [modelUrl].
 */
@Serializable
data class VoiceVersion(
    val id: String,
    /** RVC voice model URL used to produce this variant. */
    val modelUrl: String,
    val audioUrl: String,
    val localPath: String,
    val createdAt: Long = System.currentTimeMillis()
)
