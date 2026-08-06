package com.orangeisland.app.data.music

import kotlinx.serialization.Serializable

/**
 * A generated music track stored in the local music studio library.
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
    val createdAt: Long = System.currentTimeMillis()
)
