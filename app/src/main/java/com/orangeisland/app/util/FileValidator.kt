package com.orangeisland.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileValidator {
    enum class Error {
        UNKNOWN_TYPE,
        UNSUPPORTED_TYPE,
        TOO_LARGE
    }

    private val MIME_WHITELIST = setOf(
        "text/",
        "application/json",
        "application/xml",
        "application/yaml",
        "application/pdf"
    )
    private const val MAX_SIZE = 20L * 1024 * 1024

    data class Result(val valid: Boolean, val error: Error? = null, val mimeType: String? = null)

    fun validate(context: Context, uri: Uri): Result {
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) { null }

        if (mimeType == null)
            return Result(false, Error.UNKNOWN_TYPE, null)

        val allowed = MIME_WHITELIST.any { mimeType.startsWith(it) } ||
                      mimeType in MIME_WHITELIST
        if (!allowed)
            return Result(false, Error.UNSUPPORTED_TYPE, mimeType)

        val fileSize = try {
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) it.getLong(idx) else null
                } else null
            }
        } catch (_: Exception) { null }

        if (fileSize != null && fileSize > MAX_SIZE && mimeType != "application/pdf")
            return Result(false, Error.TOO_LARGE, mimeType)

        return Result(true, mimeType = mimeType)
    }

    fun resolveMimeType(context: Context, uriString: String): String? {
        return try {
            context.contentResolver.getType(Uri.parse(uriString))
        } catch (_: Exception) { null }
    }

    fun resolveFileName(context: Context, uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    fun resolveFileSize(context: Context, uri: Uri): Long? {
        return try {
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) it.getLong(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    fun errorMessage(context: Context, error: Error, mimeType: String? = null): String {
        return when (error) {
            Error.UNKNOWN_TYPE -> context.getString(com.orangeisland.app.R.string.file_unknown_type)
            Error.UNSUPPORTED_TYPE -> context.getString(com.orangeisland.app.R.string.file_unsupported_type, mimeType ?: "?")
            Error.TOO_LARGE -> context.getString(com.orangeisland.app.R.string.file_too_large)
        }
    }
}
