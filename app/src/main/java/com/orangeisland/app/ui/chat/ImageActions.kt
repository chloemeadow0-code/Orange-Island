package com.orangeisland.app.ui.chat

import com.orangeisland.app.ui.components.DialogWindowEdgeToEdge

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.orangeisland.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Reads the bytes of an image referenced by a file path or content/file Uri string. */
private fun readImageBytes(context: Context, url: String): ByteArray? = try {
    val asFile = File(url)
    if (asFile.exists()) asFile.readBytes()
    else context.contentResolver.openInputStream(Uri.parse(url))?.use { it.readBytes() }
} catch (_: Exception) { null }

/** Save the image into the device gallery (Pictures/OrangeIsland). Returns true on success. */
suspend fun saveImageToGallery(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
    val bytes = readImageBytes(context, url) ?: return@withContext false
    try {
        val name = "orangeisland_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OrangeIsland")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) { false }
}

/** Share the image via a content Uri (copied into the exposed cache dir for FileProvider). */
fun shareImage(context: Context, url: String) {
    try {
        val bytes = readImageBytes(context, url) ?: return
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "orangeisland_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.img_action_share)))
    } catch (_: Exception) { }
}

private data class ImageInfo(val width: Int, val height: Int, val sizeBytes: Long)

private fun readImageInfo(context: Context, url: String): ImageInfo? {
    val bytes = readImageBytes(context, url) ?: return null
    return try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        ImageInfo(opts.outWidth, opts.outHeight, bytes.size.toLong())
    } catch (_: Exception) { null }
}

private fun formatBytes(n: Long): String = when {
    n >= 1024 * 1024 -> String.format("%.1f MB", n / (1024.0 * 1024.0))
    n >= 1024 -> String.format("%.0f KB", n / 1024.0)
    else -> "$n B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageActionsSheet(url: String, onMessage: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Animate the sheet down before running the action, so every option exits with a
    // collapse animation instead of vanishing abruptly. The composable stays in
    // composition during hide(), so actions that need it (the Info dialog, an in-flight
    // save) keep working.
    fun collapseThen(action: () -> Unit) {
        scope.launch {
            try { sheetState.hide() } finally { action() }
        }
    }

    // Routed through the single global snackbar host (a new message dismisses the previous one).
    val savedMsg = stringResource(R.string.img_saved)
    val failMsg = stringResource(R.string.img_save_failed)
    fun doSave() {
        // Keep the sheet in composition until the save finishes — dismissing first would cancel
        // this scope (it's tied to the sheet) and abort both the save and the snackbar.
        scope.launch {
            val ok = saveImageToGallery(context, url)
            onMessage(if (ok) savedMsg else failMsg)
            onDismiss()
        }
    }
    // Pre-Q gallery writes need WRITE_EXTERNAL_STORAGE; request it then save.
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) doSave() else onMessage(failMsg) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        DialogWindowEdgeToEdge()
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            ActionRow(Icons.Default.Download, stringResource(R.string.img_action_save)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) collapseThen { doSave() }
                else permLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            ActionRow(Icons.Default.Share, stringResource(R.string.img_action_share)) {
                collapseThen { shareImage(context, url); onDismiss() }
            }
            ActionRow(Icons.Default.Info, stringResource(R.string.info)) {
                collapseThen { showInfo = true }
            }
        }
    }

    if (showInfo) {
        val info = remember(url) { readImageInfo(context, url) }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showInfo = false; onDismiss() },
            title = { Text(stringResource(R.string.info), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLine(stringResource(R.string.img_info_dimensions), info?.let { "${it.width} × ${it.height}" } ?: "—")
                    InfoLine(stringResource(R.string.img_info_size), info?.let { formatBytes(it.sizeBytes) } ?: "—")
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false; onDismiss() }) { Text(stringResource(R.string.provider_close)) } }
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    // Matches the message info dialog: single "Label: value" line at bodyMedium 14/20.
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
    )
}
