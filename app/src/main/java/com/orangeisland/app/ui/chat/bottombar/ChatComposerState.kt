package com.orangeisland.app.ui.chat.bottombar

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.orangeisland.app.model.SelectedAttachment
import com.orangeisland.app.ui.chat.VideoSliceDialog
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.ui.common.OrangeIslandHaptics
import com.orangeisland.app.ui.common.LocalOrangeIslandHaptics
import com.orangeisland.app.util.FileValidator
import com.orangeisland.app.util.PdfPageRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder for the chat composer's attachment subsystem (images / videos / PDFs /
 * generic files): the picked-attachment list, per-attachment processing progress, and
 * the PDF page-select + video-slice dialog state, plus the logic for picking, frame
 * extraction, page rendering, and removal.
 *
 * Hoisted out of the `ChatBottomBar` composable body (Phase E6) so the composable holds
 * UI and this holder owns attachment state/behaviour — the Compose "separate state from
 * UI" best practice. Obtain via [rememberChatComposerState]; the composable reads/writes
 * `composer.xxx` and wires the launchers/dialogs to these methods.
 */
class ChatComposerState(
    private val context: Context,
    private val haptics: OrangeIslandHaptics,
    private val scope: CoroutineScope,
) {
    var selectedAttachments by mutableStateOf<List<SelectedAttachment>>(emptyList())
    var processingStates by mutableStateOf<Map<String, Float>>(emptyMap())
    var pendingSend by mutableStateOf(false)

    // PDF page selection dialog state
    var showPdfPageDialog by mutableStateOf(false)
    var pendingPdfUri by mutableStateOf<String?>(null)
    var pendingPdfPages by mutableIntStateOf(0)
    var pendingPdfFileName by mutableStateOf<String?>(null)
    var pendingPdfMimeType by mutableStateOf<String?>(null)
    var pendingPdfRenderedPaths by mutableStateOf<List<String>>(emptyList())
    var pendingPdfIsRendering by mutableStateOf(false)
    var pendingPdfRenderProgress by mutableStateOf(0 to 0)
    var pdfDialogHiddenForPreview by mutableStateOf(false)
    // Background render job for the page-select dialog, so a dismiss can cancel it and
    // let renderAllPages clean up its partially-written page files.
    var pdfRenderJob by mutableStateOf<Job?>(null)
    // In-flight video frame-extraction jobs, keyed by video uri, so removing a video while
    // it is still extracting can cancel the job (which deletes its partial frame files).
    val videoExtractionJobs = mutableMapOf<String, Job>()

    // Video slicing dialog state
    var showVideoSliceDialog by mutableStateOf(false)
    var pendingVideoUri by mutableStateOf<String?>(null)
    var pendingVideoDurationMs by mutableLongStateOf(0L)
    var pendingVideoQueue by mutableStateOf<List<String>>(emptyList())

    // File validation rejection dialog
    var rejectedMessage by mutableStateOf<String?>(null)

    /** Clear the attachment list after a successful send. The extracted-frame / rendered-page
     *  files are now owned by the stored message (via images field in MessageEntity) — they
     *  must NOT be deleted here; message deletion handles that. */
    fun clearAttachments() {
        selectedAttachments = emptyList()
    }

    /** Remove the attachment at [index], cancelling any in-flight extraction and deleting its
     *  pre-extracted frame / rendered-page files. */
    fun removeAttachmentAt(index: Int) {
        haptics.selection()
        val removed = selectedAttachments.getOrNull(index)
        // Cancel in-flight video extraction + delete partial frames
        if (removed != null && videoExtractionJobs.containsKey(removed.uri)) {
            videoExtractionJobs[removed.uri]?.cancel()
            videoExtractionJobs.remove(removed.uri)
        }
        // Clean up pre-extracted video frame files
        if (removed?.processedFrames != null) {
            for (path in removed.processedFrames) {
                try { java.io.File(path).delete() } catch (_: Exception) {}
            }
        }
        // Clean up PDF page preview files
        if (removed?.preRenderedPaths != null) {
            for (path in removed.preRenderedPaths) {
                try { java.io.File(path).delete() } catch (_: Exception) {}
            }
        }
        val uriStr = removed?.uri
        selectedAttachments = selectedAttachments.toMutableList().also { it.removeAt(index) }
        if (uriStr != null) processingStates = processingStates - uriStr
    }

    // Helper: process next video in queue, showing slice dialog
    fun processNextVideo() {
        if (pendingVideoQueue.isNotEmpty()) {
            val uri = pendingVideoQueue.first()
            pendingVideoQueue = pendingVideoQueue.drop(1).toMutableList()
            val durationMs = try {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                retriever.setDataSource(context, android.net.Uri.parse(uri))
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally { retriever.release() }
            } catch (_: Exception) { 0L }
            pendingVideoUri = uri
            pendingVideoDurationMs = durationMs
            showVideoSliceDialog = true
        }
    }

    // Start frame extraction for a video, return list of frame paths
    suspend fun extractVideoFrames(videoUri: String, frameCount: Int, intervalMs: Long): List<String> {
        return withContext(Dispatchers.IO) {
            val paths = mutableListOf<String>()
            try {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                retriever.setDataSource(context, android.net.Uri.parse(videoUri))
                var timeUs = 0L
                val intervalUs = intervalMs * 1000L
                for (i in 0 until frameCount) {
                    ensureActive()
                    val bitmap = retriever.getFrameAtTime(
                        timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    if (bitmap != null) {
                        val file = java.io.File(context.filesDir, "vid_${java.util.UUID.randomUUID()}_$i.jpg")
                        file.outputStream().use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        bitmap.recycle()
                        paths.add(file.absolutePath)
                    }
                    timeUs += intervalUs
                    processingStates = processingStates + (videoUri to (i + 1).toFloat() / frameCount)
                }
                } finally { retriever.release() }
            } catch (c: CancellationException) {
                // Removed mid-extraction: drop the partial frame files instead of orphaning them.
                paths.forEach { runCatching { java.io.File(it).delete() } }
                throw c
            } catch (e: Exception) { DebugLog.e("ChatComposer", "Video frame extraction failed", e) }
            processingStates = processingStates - videoUri
            paths
        }
    }

    /** Handle images picked from the photo picker. */
    fun onPickImages(uris: List<Uri>) {
        if (uris.isNotEmpty()) haptics.selection()
        selectedAttachments = selectedAttachments + uris.map {
            SelectedAttachment(
                uri = it.toString(), type = "image",
                mimeType = try { context.contentResolver.getType(it) } catch (_: Exception) { null }
            )
        }
    }

    /** Handle videos picked from the video picker; queues them and kicks off the slice dialog. */
    fun onPickVideos(uris: List<Uri>) {
        if (uris.isNotEmpty()) haptics.selection()
        val urisToQueue = uris.map { it.toString() }
        pendingVideoQueue = pendingVideoQueue + urisToQueue
        if (!showVideoSliceDialog) processNextVideo()
    }

    /** Handle generic files picked from the document picker (validates, queues first PDF for
     *  page rendering, adds the rest as attachments). */
    fun onPickFiles(uris: List<Uri>, onInitPdfSelection: ((Set<Int>) -> Unit)?) {
        val validAttachments = mutableListOf<SelectedAttachment>()
        val rejectedMessages = mutableListOf<String>()
        for (uri in uris) {
            val validation = FileValidator.validate(context, uri)
            if (!validation.valid) {
                rejectedMessages.add(FileValidator.errorMessage(context, validation.error!!, validation.mimeType))
                continue
            }
            val mimeType = validation.mimeType
            val type = when {
                mimeType == "application/pdf" -> "pdf"
                mimeType != null -> "file"
                else -> "file"
            }
            val fileName = try {
                val cursor = context.contentResolver.query(
                    uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) it.getString(idx) else null
                    } else null
                }
            } catch (_: Exception) { null }
            if (type == "pdf" && !showPdfPageDialog) {
                // Queue first PDF — render all pages in background
                val pageCount = PdfPageRenderer.getPageCount(context, uri)
                if (pageCount > 0) {
                    pendingPdfUri = uri.toString()
                    pendingPdfPages = pageCount
                    pendingPdfFileName = fileName
                    pendingPdfMimeType = mimeType
                    pendingPdfRenderedPaths = emptyList()
                    pendingPdfIsRendering = true
                    pendingPdfRenderProgress = 0 to pageCount
                    showPdfPageDialog = true
                    // Initialize selection to first 5 pages
                    onInitPdfSelection?.invoke((0 until minOf(pageCount, 5)).toSet())
                    pdfRenderJob = scope.launch(Dispatchers.IO) {
                        val paths = PdfPageRenderer.renderAllPages(
                            context, uri, maxPages = pageCount,
                            onProgress = { cur, total -> pendingPdfRenderProgress = cur to total }
                        )
                        pendingPdfRenderedPaths = paths
                        pendingPdfIsRendering = false
                    }
                    continue
                }
            }
            validAttachments.add(SelectedAttachment(
                uri = uri.toString(), type = type,
                mimeType = mimeType, fileName = fileName
            ))
        }
        if (rejectedMessages.isNotEmpty()) {
            haptics.reject()
            rejectedMessage = rejectedMessages.joinToString("\n")
        }
        if (validAttachments.isNotEmpty()) haptics.selection()
        selectedAttachments = selectedAttachments + validAttachments
    }

    /** Add a sliced video as an attachment and start background frame extraction. */
    fun addSlicedVideo(vidUri: String, frameCount: Int, intervalMs: Long) {
        val fileName = try {
            val cursor = context.contentResolver.query(
                Uri.parse(vidUri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
        val attachment = SelectedAttachment(
            uri = vidUri, type = "video",
            frameCount = frameCount,
            sliceIntervalMs = intervalMs,
            fileName = fileName,
            mimeType = "video/*"
        )
        selectedAttachments = selectedAttachments + attachment
        processingStates = processingStates + (vidUri to 0f)

        // Start frame extraction and store result paths; track job so an X-delete while
        // extracting can cancel it (extractVideoFrames cleans up partial files on cancel).
        val job = scope.launch(Dispatchers.IO) {
            val framePaths = extractVideoFrames(vidUri, frameCount, intervalMs)
            selectedAttachments = selectedAttachments.map { a ->
                if (a.uri == vidUri) a.copy(processedFrames = framePaths) else a
            }
            videoExtractionJobs.remove(vidUri)
        }
        videoExtractionJobs[vidUri] = job
    }
}

@Composable
fun rememberChatComposerState(): ChatComposerState {
    val context = LocalContext.current
    val haptics = LocalOrangeIslandHaptics.current
    val scope = rememberCoroutineScope()
    return remember(context, haptics, scope) { ChatComposerState(context, haptics, scope) }
}
