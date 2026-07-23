package com.orangeisland.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MiniAppEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
)
