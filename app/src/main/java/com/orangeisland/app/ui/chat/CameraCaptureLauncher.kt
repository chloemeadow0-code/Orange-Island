package com.orangeisland.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.orangeisland.app.tool.CameraToolGate
import com.orangeisland.app.util.DebugLog
import java.io.File
import java.util.UUID

/**
 * Bridges the [CameraToolGate] to the system camera.
 *
 * Observes [CameraToolGate.pending]; whenever a fresh request appears (and we don't already
 * have one in flight), this composable creates an output file under `filesDir/images/`,
 * gets a FileProvider URI for it, and launches [ActivityResultContracts.TakePicture].
 * On success the gate is resolved with the absolute file path; on cancel the request is
 * cancelled.
 *
 * The current implementation processes requests one at a time — the TakePicture contract
 * can only have one in-flight capture, so we serialise them via [currentRequestId].
 */
@Composable
fun CameraCaptureLauncher(gate: CameraToolGate?) {
    if (gate == null) return

    val pending by gate.pending.collectAsState()
    var currentRequestId by remember { mutableStateOf<String?>(null) }
    var currentImagePath by remember { mutableStateOf<String?>(null) }
    var currentImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val reqId = currentRequestId
        val path = currentImagePath
        if (reqId != null && path != null) {
            if (success && File(path).exists()) {
                gate.resolve(reqId, path)
            } else {
                DebugLog.d("CameraCapture", "capture cancelled/failed for req=$reqId")
                gate.cancel(reqId)
                // Clean up the empty placeholder file if the camera didn't write to it.
                runCatching { File(path).takeIf { it.exists() && it.length() == 0L }?.delete() }
            }
        }
        currentRequestId = null
        currentImagePath = null
        currentImageUri = null
    }

    // Pick up the head pending request and start the camera. Only one capture at a time:
    // we ignore additional requests while [currentRequestId] is set.
    LaunchedEffect(pending) {
        if (currentRequestId != null) return@LaunchedEffect
        val head = pending.firstOrNull() ?: return@LaunchedEffect
        val imageDir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(imageDir, "photo_${UUID.randomUUID()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrNull()
        if (uri == null) {
            DebugLog.e("CameraCapture", "Failed to get FileProvider URI")
            gate.cancel(head.id)
            return@LaunchedEffect
        }
        currentRequestId = head.id
        currentImagePath = file.absolutePath
        currentImageUri = uri
        runCatching { launcher.launch(uri) }.onFailure { e ->
            DebugLog.e("CameraCapture", "Failed to launch camera", e)
            gate.cancel(head.id)
            currentRequestId = null
            currentImagePath = null
            currentImageUri = null
        }
    }
}
