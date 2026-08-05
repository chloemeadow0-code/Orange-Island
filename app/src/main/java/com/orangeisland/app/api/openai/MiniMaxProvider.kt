package com.orangeisland.app.api.openai

import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.api.OpenAiVideoUrl
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * MiniMax OpenAI-compatible chat provider.
 *
 * Default endpoint: https://api.minimax.io/v1/chat/completions
 * Supports native `video_url` content parts for video understanding models such as MiniMax-M3.
 */
class MiniMaxProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_MINIMAX_CHAT
    override val defaultBaseUrl: String = "https://api.minimax.io/v1"

    /**
     * Resolve local video paths for MiniMax.
     *
     * - Remote URLs and `mm_file://` references are passed through unchanged.
     * - Small local files (≤ [Constants.MAX_INLINE_VIDEO_BYTES]) are base64-encoded into a data URL.
     * - Large local files are uploaded via MiniMax Files API with `purpose=video_understanding`
     *   and referenced as `mm_file://{file_id}`.
     *
     * This runs inside the provider's request flow, so API credentials are already available.
     */
    override suspend fun resolveVideoUrls(messages: List<ChatMessage>, apiKey: String, baseUrl: String): List<ChatMessage> {
        if (apiKey.isBlank()) return messages
        return messages.map { msg ->
            if (msg.videos.isEmpty()) return@map msg
            val resolved = msg.videos.mapNotNull { videoRef -> resolveVideoUrl(videoRef, apiKey, baseUrl) }
            if (resolved.size == msg.videos.size) {
                msg.copy(videos = resolved)
            } else {
                msg.copy(videos = resolved)
            }
        }
    }

    private fun resolveVideoUrl(videoRef: String, apiKey: String, baseUrl: String): String? {
        // Already a reference MiniMax understands or a remote URL.
        if (videoRef.startsWith("mm_file://", ignoreCase = true) ||
            videoRef.startsWith("http://", ignoreCase = true) ||
            videoRef.startsWith("https://", ignoreCase = true) ||
            videoRef.startsWith("data:", ignoreCase = true)
        ) {
            return videoRef
        }
        val file = File(videoRef.removePrefix("file://"))
        if (!file.exists()) {
            DebugLog.w("MiniMaxProvider", "Video file not found: $videoRef")
            return null
        }
        return if (file.length() <= Constants.MAX_INLINE_VIDEO_BYTES) {
            encodeVideoToDataUrl(file)
        } else {
            uploadVideoForUnderstanding(file, apiKey, baseUrl)
        }
    }

    private fun encodeVideoToDataUrl(file: File): String? {
        return try {
            val bytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mime = guessVideoMimeType(file.name)
            "data:$mime;base64,$base64"
        } catch (e: Exception) {
            DebugLog.e("MiniMaxProvider", "Failed to encode video ${file.name}", e)
            null
        }
    }

    private fun uploadVideoForUnderstanding(file: File, apiKey: String, baseUrl: String): String? {
        return try {
            val mime = guessVideoMimeType(file.name)
            val endpoint = "$baseUrl/files/upload"
            val responseBody = HttpClient.postMultipart(
                url = endpoint,
                fileField = "file",
                fileName = file.name,
                fileMimeType = mime,
                fileBytes = file.readBytes(),
                textFields = mapOf("purpose" to "video_understanding"),
                headers = mapOf("Authorization" to "Bearer $apiKey")
            )
            if (responseBody == null) {
                DebugLog.e("MiniMaxProvider", "Video upload returned empty body for ${file.name}")
                return null
            }
            val result = json.decodeFromString(MiniMaxUploadResponse.serializer(), responseBody)
            val fileId = result.file?.fileId
            if (fileId.isNullOrBlank()) {
                DebugLog.e("MiniMaxProvider", "Video upload response missing file_id: $responseBody")
                null
            } else {
                "mm_file://$fileId"
            }
        } catch (e: Exception) {
            DebugLog.e("MiniMaxProvider", "Failed to upload video ${file.name}", e)
            null
        }
    }

    private fun guessVideoMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            fileName.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
            fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            else -> "video/mp4"
        }
    }

    @Serializable
    private data class MiniMaxUploadResponse(
        @SerialName("file") val file: MiniMaxUploadFile? = null,
        @SerialName("base_resp") val baseResp: MiniMaxBaseResp? = null
    )

    @Serializable
    private data class MiniMaxUploadFile(
        @SerialName("file_id") val fileId: String? = null
    )

    @Serializable
    private data class MiniMaxBaseResp(
        @SerialName("status_code") val statusCode: Int? = null,
        @SerialName("status_msg") val statusMsg: String? = null
    )
}
