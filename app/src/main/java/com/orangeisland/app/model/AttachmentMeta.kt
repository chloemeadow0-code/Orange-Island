package com.orangeisland.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class AttachmentMeta(val items: List<AttachmentItem> = emptyList()) {
    companion object {
        // Lenient decoder: ignores unknown keys and tolerates minor formatting quirks so
        // schema drift across versions doesn't silently drop every attachment.
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Parse persisted attachment metadata, tolerating two historical storage formats:
         *
         *  1. Canonical: `{"items":[{"type":"video","original_uri":"..."}, ...]}`
         *  2. Legacy array: `["file://.../vid_xxx.mp4", ...]` — an older write path stored a
         *     bare list of video URIs in the attachmentMeta column. Each entry is treated as
         *     a video AttachmentItem so video narration/stripping still works on old data.
         *
         * Returns null only when [raw] is blank or genuinely unparseable.
         */
        /**
         * Guess attachment type from a URI by extension. Used only for legacy bare-URI arrays
         * that carry no explicit type field.
         */
        private fun guessTypeFromUri(uri: String): String {
            val lower = uri.substringBefore('?').lowercase()
            return when {
                lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") ||
                    lower.endsWith(".avi") || lower.endsWith(".webm") || lower.endsWith(".3gp") -> "video"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                    lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") -> "image"
                else -> "video" // conservative default — legacy arrays originated from the videos column
            }
        }

        fun parse(raw: String?): AttachmentMeta? {
            if (raw.isNullOrBlank()) return null
            return try {
                val element = json.parseToJsonElement(raw)
                when (element) {
                    is JsonArray -> {
                        // Legacy bare-URI arrays: the attachmentMeta column was reused from
                        // MessageEntity.videos/images, so entries can be either media type.
                        // Guess the type from the extension and assign imageIndex = position,
                        // so image transcription (which requires imageIndex) can still pick
                        // these up. pageCount defaults to 1 per item.
                        val items = element.mapIndexedNotNull { idx, e ->
                            val uri = (e as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                            uri?.let {
                                AttachmentItem(
                                    originalUri = it,
                                    type = guessTypeFromUri(it),
                                    imageIndex = idx,
                                    pageCount = 1
                                )
                            }
                        }
                        AttachmentMeta(items = items)
                    }
                    is JsonObject -> json.decodeFromString<AttachmentMeta>(raw)
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

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
    @SerialName("transcription") val transcription: String? = null,
    @SerialName("video_transcription") val videoTranscription: String? = null
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
