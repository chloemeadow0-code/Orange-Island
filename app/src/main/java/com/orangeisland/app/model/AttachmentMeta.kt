package com.orangeisland.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class AttachmentMeta(val items: List<AttachmentItem> = emptyList())

@Serializable
data class AttachmentItem(
    val originalUri: String? = null,
    val type: String,               // "image", "video", "file", "pdf"
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("image_index") val imageIndex: Int? = null,
    @SerialName("page_count") val pageCount: Int? = null,
    val warning: String? = null,
    @SerialName("text_content") val textContent: String? = null,
    @SerialName("transcription") val transcription: String? = null
)

/** Used for passing attachment metadata from ChatBottomBar to ViewModel. */
data class SelectedAttachment(
    val uri: String,
    val type: String,               // "image", "video", "file", "pdf"
    val frameCount: Int? = null,
    val sliceIntervalMs: Long? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val processedFrames: List<String>? = null,
    val selectedPages: Set<Int>? = null,
    val preRenderedPaths: List<String>? = null
)
