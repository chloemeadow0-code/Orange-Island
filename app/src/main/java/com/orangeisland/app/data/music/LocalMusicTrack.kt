package com.orangeisland.app.data.music

import kotlinx.serialization.Serializable

@Serializable
data class LocalMusicTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val localPath: String,
    val durationMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)
