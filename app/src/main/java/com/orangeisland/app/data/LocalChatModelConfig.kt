package com.orangeisland.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LocalChatModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val modelId: String,
    val alias: String,
    val localFilePath: String = "",
    val mmprojPath: String = "",
    val nCtx: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 4096
)
