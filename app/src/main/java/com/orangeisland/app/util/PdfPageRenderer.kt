package com.orangeisland.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

object PdfPageRenderer {
    private const val MAX_PAGES = 5
    private const val TARGET_LONG_EDGE = 1536

    /**
     * Renders only the requested [pages] (first [MAX_PAGES] if omitted) to internal storage
     * at full quality. Cancellation-aware: partial files are deleted on cancel.
     */
    suspend fun renderAsImages(context: Context, uri: Uri, pages: Set<Int>? = null): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return emptyList()

        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        val renderer = PdfRenderer(ParcelFileDescriptor(fd))
        val paths = mutableListOf<String>()

        try {
            val totalPages = renderer.pageCount
            val selectedPages = pages?.filter { it in 0 until totalPages }?.toSet()
                ?: (0 until minOf(totalPages, MAX_PAGES)).toSet()
            for (i in selectedPages.sorted()) {
                coroutineContext.ensureActive()
                val page = renderer.openPage(i)
                val scale = TARGET_LONG_EDGE.toFloat() / maxOf(page.width, page.height)
                val scaledWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val scaledHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(
                    scaledWidth, scaledHeight,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val file = File(context.filesDir, "pdf_${UUID.randomUUID()}_$i.jpg")
                file.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
                }
                bitmap.recycle()
                paths.add(file.absolutePath)
            }
        } catch (c: CancellationException) {
            paths.forEach { runCatching { File(it).delete() } }
            throw c
        } finally {
            renderer.close()
        }
        return paths
    }

    /**
     * Renders every page (up to [maxPages]) to internal storage. Cancellation-aware: if the
     * calling coroutine is cancelled mid-render (e.g. the page-select dialog is dismissed), the
     * partially-written page files are deleted before the [CancellationException] propagates, so
     * no orphaned JPEGs are left behind in filesDir.
     */
    suspend fun renderAllPages(context: Context, uri: Uri, maxPages: Int = 200, onProgress: ((current: Int, total: Int) -> Unit)? = null): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return emptyList()

        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        val renderer = PdfRenderer(ParcelFileDescriptor(fd))
        val paths = mutableListOf<String>()
        try {
            val effectiveTotal = minOf(renderer.pageCount, maxPages)

            for (i in 0 until effectiveTotal) {
                coroutineContext.ensureActive()
                val page = renderer.openPage(i)
                val scale = TARGET_LONG_EDGE.toFloat() / maxOf(page.width, page.height)
                val scaledWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val scaledHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val file = File(context.filesDir, "pdf_preview_${UUID.randomUUID()}_$i.jpg")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                bitmap.recycle()
                paths.add(file.absolutePath)
                onProgress?.invoke(i + 1, effectiveTotal)
            }
        } catch (c: CancellationException) {
            paths.forEach { runCatching { File(it).delete() } }
            throw c
        } finally {
            renderer.close()
        }
        return paths
    }

    fun getPageCount(context: Context, uri: Uri): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return 0
        return try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            val renderer = PdfRenderer(ParcelFileDescriptor(fd))
            val count = renderer.pageCount
            renderer.close()
            count
        } catch (_: Exception) { 0 }
    }
}
