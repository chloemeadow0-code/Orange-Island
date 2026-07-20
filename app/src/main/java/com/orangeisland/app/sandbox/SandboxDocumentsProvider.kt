package com.orangeisland.app.sandbox

import com.orangeisland.app.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/**
 * Exposes the sandbox home directory via the Storage Access Framework so
 * system file managers can browse and edit files directly — same pattern
 * that Termux uses for its $HOME.
 */
class SandboxDocumentsProvider : DocumentsProvider() {

    companion object {
        const val ROOT_ID = "orangeisland_sandbox"
        const val ROOT_DOCUMENT_ID = "home"
        const val DEFAULT_AUTHORITY_SUFFIX = "documents"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

    }

    private lateinit var homeDirectory: File
    private lateinit var canonicalHome: File

    override fun onCreate(): Boolean {
        val appContext = context ?: return false
        homeDirectory = File(appContext.filesDir, "sandbox-home").apply { mkdirs() }
        canonicalHome = homeDirectory.canonicalFile
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection?.toList()?.toTypedArray() ?: DEFAULT_ROOT_PROJECTION
        return MatrixCursor(columns).apply {
            newRow().apply {
                add(Root.COLUMN_ROOT_ID, ROOT_ID)
                add(Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
                add(Root.COLUMN_TITLE, "Orange Island")
                add(Root.COLUMN_SUMMARY, "Sandbox home")
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_LOCAL_ONLY)
                add(Root.COLUMN_MIME_TYPES, "*/*")
                add(Root.COLUMN_AVAILABLE_BYTES, canonicalHome.usableSpace)
            }
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection?.toList()?.toTypedArray() ?: DEFAULT_DOCUMENT_PROJECTION
        return MatrixCursor(columns).apply {
            includeFile(this, documentId, fileForDocumentId(documentId))
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection?.toList()?.toTypedArray() ?: DEFAULT_DOCUMENT_PROJECTION
        val parent = fileForDocumentId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a directory: $parentDocumentId")

        return MatrixCursor(columns).apply {
            parent.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
                ?.forEach { child -> includeFile(this, documentIdForFile(child), child) }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = fileForDocumentId(documentId)
        if (file.isDirectory) throw FileNotFoundException("Cannot open directory as file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        validateDisplayName(displayName)
        val parent = fileForDocumentId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Parent is not a directory")

        val child = checkedFile(File(parent, displayName))
        if (child.exists()) throw FileNotFoundException("File already exists: $displayName")

        val created = if (mimeType == Document.MIME_TYPE_DIR) child.mkdir() else child.createNewFile()
        if (!created) throw FileNotFoundException("Unable to create: $displayName")
        return documentIdForFile(child)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileForDocumentId(documentId)
        if (file == canonicalHome) throw FileNotFoundException("Cannot delete sandbox home")
        if (!file.deleteRecursively()) throw FileNotFoundException("Unable to delete: $documentId")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        validateDisplayName(displayName)
        val source = fileForDocumentId(documentId)
        if (source == canonicalHome) throw FileNotFoundException("Cannot rename sandbox home")

        val parent = source.parentFile ?: throw FileNotFoundException("Document has no parent")
        val target = checkedFile(File(parent, displayName))
        if (target.exists() || !source.renameTo(target)) throw FileNotFoundException("Unable to rename document")
        return documentIdForFile(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = fileForDocumentId(parentDocumentId)
        val child = fileForDocumentId(documentId)
        return child != parent && child.path.startsWith(parent.path + File.separator)
    }

    // ── helpers ────────────────────────────────────────────────

    private fun includeFile(cursor: MatrixCursor, documentId: String, file: File) {
        var flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        flags = if (file.isDirectory) flags or Document.FLAG_DIR_SUPPORTS_CREATE
                else flags or Document.FLAG_SUPPORTS_WRITE

        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, if (file == canonicalHome) "Home" else file.name)
            add(Document.COLUMN_MIME_TYPE, mimeTypeFor(file))
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, if (file.isFile) file.length() else null)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        }
    }

    private fun fileForDocumentId(documentId: String): File {
        if (documentId == ROOT_DOCUMENT_ID) return canonicalHome
        val prefix = "$ROOT_DOCUMENT_ID:"
        if (!documentId.startsWith(prefix)) throw FileNotFoundException("Unknown document ID: $documentId")
        return checkedFile(File(canonicalHome, documentId.removePrefix(prefix)))
    }

    private fun documentIdForFile(file: File): String {
        val checked = checkedFile(file)
        if (checked == canonicalHome) return ROOT_DOCUMENT_ID
        val relative = checked.relativeTo(canonicalHome).invariantSeparatorsPath
        return "$ROOT_DOCUMENT_ID:$relative"
    }

    /** Prevent path-traversal escapes outside the sandbox home. */
    private fun checkedFile(file: File): File {
        val canonical = file.canonicalFile
        val inside = canonical == canonicalHome ||
            canonical.path.startsWith(canonicalHome.path + File.separator)
        if (!inside) throw FileNotFoundException("Path escapes sandbox home")
        return canonical
    }

    private fun validateDisplayName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') ||
            name.contains('\\') || name.indexOf(' ') >= 0
        ) throw FileNotFoundException("Invalid file name: $name")
    }

    private fun mimeTypeFor(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
            ?: "application/octet-stream"
    }
}

/**
 * Open the sandbox home in the system file manager.
 * Uses the SAF root URI (ACTION_VIEW + buildRootUri) so DocumentsUI navigates
 * straight into the provider; falls back to ACTION_OPEN_DOCUMENT_TREE with a
 * tree URI as EXTRA_INITIAL_URI when the OEM file manager doesn't support
 * direct root browsing.
 */
fun Context.openSandboxHome(authority: String = "$packageName.${SandboxDocumentsProvider.DEFAULT_AUTHORITY_SUFFIX}") {
    val rootUri = DocumentsContract.buildRootUri(authority, SandboxDocumentsProvider.ROOT_ID)
    val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(rootUri, DocumentsContract.Root.MIME_TYPE_ITEM)
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(grantFlags)
        if (this@openSandboxHome !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    try {
        startActivity(viewIntent)
    } catch (_: Exception) {
        openSandboxHomePicker(authority)
    }
}

private fun Context.openSandboxHomePicker(authority: String) {
    val treeUri = DocumentsContract.buildTreeDocumentUri(authority, SandboxDocumentsProvider.ROOT_DOCUMENT_ID)
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
        }
        addFlags(flags)
        if (this@openSandboxHomePicker !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    startActivity(intent)
}
