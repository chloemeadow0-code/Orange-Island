package com.orangeisland.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.orangeisland.app.tool.CameraToolGate
import com.orangeisland.app.util.DebugLog
import java.io.File
import java.util.UUID

/**
 * A small camera preview used inside the chat overlay.
 *
 * Lifecycle:
 *  - Composes CameraX preview + image capture, auto-captures after a short delay.
 *  - On capture, resolves the gate request and caches the photo path locally so the
 *    image keeps rendering even after the request leaves the gate's pending list.
 *  - On dispose, forcibly unbinds the camera provider to free the camera hardware.
 */
@Composable
fun CameraMiniCapture(
    gate: CameraToolGate?,
    request: CameraToolGate.CaptureRequest,
    modifier: Modifier = Modifier
) {
    if (gate == null) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember(request.id) { mutableStateOf<CaptureMiniState>(CaptureMiniState.Starting) }
    // Cache the captured path locally so it survives the request leaving gate.pending.
    var capturedPath by remember(request.id) { mutableStateOf<String?>(null) }
    val gatePhotoPath by request.photoPath.collectAsState()

    // Mirror gate's photo path into local cache as soon as it lands.
    LaunchedEffect(gatePhotoPath) {
        if (gatePhotoPath != null && capturedPath == null) {
            capturedPath = gatePhotoPath
        }
    }

    LaunchedEffect(request.id) {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> state = CaptureMiniState.Starting
            else -> {
                state = CaptureMiniState.NoPermission
                gate.cancel(request.id)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val displayPath = capturedPath ?: gatePhotoPath
        when {
            displayPath != null -> {
                coil.compose.AsyncImage(
                    model = displayPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state == CaptureMiniState.NoPermission -> {
                Text(
                    text = "需要相机权限",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                CameraPreviewContent(
                    gate = gate,
                    request = request,
                    lifecycleOwner = lifecycleOwner,
                    onCaptured = { path -> capturedPath = path }
                )
            }
        }
    }
}

private enum class CaptureMiniState {
    Starting, NoPermission
}

@Composable
private fun CameraPreviewContent(
    gate: CameraToolGate,
    request: CameraToolGate.CaptureRequest,
    lifecycleOwner: LifecycleOwner,
    onCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    var isBound by remember(request.id) { mutableStateOf(false) }
    val providerRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { previewView ->
        if (!isBound) {
            isBound = true
            bindMiniCamera(context, lifecycleOwner, previewView, request, gate, onCaptured) { provider ->
                providerRef.value = provider
            }
        }
    }

    // Forcibly release the camera when this composable leaves the composition.
    DisposableEffect(request.id) {
        onDispose {
            DebugLog.d("CameraMini", "onDispose: unbinding camera for ${request.id}")
            providerRef.value?.unbindAll()
            providerRef.value = null
        }
    }
}

private fun bindMiniCamera(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    request: CameraToolGate.CaptureRequest,
    gate: CameraToolGate,
    onCaptured: (String) -> Unit,
    onProviderReady: (ProcessCameraProvider) -> Unit
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = try {
            providerFuture.get()
        } catch (e: Exception) {
            DebugLog.e("CameraMini", "Camera provider failed: ${e.message}")
            gate.cancel(request.id)
            return@addListener
        }
        onProviderReady(provider)

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .build()

        try {
            provider.unbindAll()
            val selector = if (request.facing == "front")
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)

            previewView.postDelayed({
                takeMiniPhoto(context, provider, imageCapture, request, gate, onCaptured)
            }, 900)
        } catch (e: Exception) {
            DebugLog.e("CameraMini", "bindToLifecycle failed: ${e.message}")
            provider.unbindAll()
            gate.cancel(request.id)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun takeMiniPhoto(
    context: android.content.Context,
    provider: ProcessCameraProvider,
    imageCapture: ImageCapture,
    request: CameraToolGate.CaptureRequest,
    gate: CameraToolGate,
    onCaptured: (String) -> Unit
) {
    val imageDir = File(context.filesDir, "images").apply { mkdirs() }
    val file = File(imageDir, "photo_${UUID.randomUUID()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    runCatching {
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    DebugLog.e("CameraMini", "takePicture onError: ${exc.message}")
                    provider.unbindAll()
                    gate.cancel(request.id)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    DebugLog.d("CameraMini", "takePicture onImageSaved: ${file.absolutePath}")
                    provider.unbindAll()
                    onCaptured(file.absolutePath)
                    gate.resolve(request.id, file.absolutePath)
                }
            }
        )
    }.onFailure { e ->
        DebugLog.e("CameraMini", "takePicture threw: ${e.message}")
        provider.unbindAll()
        gate.cancel(request.id)
    }
}
