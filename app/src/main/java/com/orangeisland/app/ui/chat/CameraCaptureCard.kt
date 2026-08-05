package com.orangeisland.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.orangeisland.app.tool.CameraToolGate
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * A chat tool card that shows the camera preview and automatically captures a photo.
 *
 * Triggered by [CameraToolGate.pending]. When mounted, it binds a CameraX preview + image capture
 * to the current lifecycle, waits a short moment for auto-exposure to settle, then takes a
 * picture and resolves the gate request with the saved file path.
 */
@Composable
fun CameraCaptureCard(
    gate: CameraToolGate,
    requestId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf<CaptureState>(CaptureState.Starting) }

    LaunchedEffect(requestId) {
        // Only gate on permission. The actual capture lifecycle is driven by AndroidView below.
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                state = CaptureState.Starting
            }
            else -> {
                state = CaptureState.NoPermission
                gate.cancel(requestId)
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI 正在拍照…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (state != CaptureState.NoPermission) {
                AndroidView(
                        factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) { previewView ->
                    // Bind the camera once when the view is ready and permission is granted.
                    if (state == CaptureState.Starting) {
                        bindCameraAndCapture(
                            context = context,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            requestId = requestId,
                            onStateChange = { state = it }
                        ) { path ->
                            if (path != null) gate.resolve(requestId, path) else gate.cancel(requestId)
                        }
                    }
                }
            }

            when (state) {
                CaptureState.Starting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is CaptureState.Done -> {
                    Text(
                        text = "照片已捕获",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                CaptureState.Error -> {
                    Text(
                        text = "拍照失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                CaptureState.NoPermission -> {
                    Text(
                        text = "需要相机权限",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

private sealed class CaptureState {
    object Starting : CaptureState()
    data class Done(val path: String) : CaptureState()
    object Error : CaptureState()
    object NoPermission : CaptureState()
}

private data class BoundCamera(
    val provider: ProcessCameraProvider,
    val imageCapture: ImageCapture
)

private var activeBinding: BoundCamera? = null

private fun bindCameraAndCapture(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    requestId: String,
    onStateChange: (CaptureState) -> Unit,
    onComplete: (String?) -> Unit
) {
    if (activeBinding != null) return // one binding at a time

    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = try {
            providerFuture.get()
        } catch (e: Exception) {
            DebugLog.e("CameraCapture", "Camera provider failed: ${e.message}")
            onStateChange(CaptureState.Error)
            onComplete(null)
            return@addListener
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            activeBinding = BoundCamera(provider, imageCapture)

            // Give auto-exposure/white-balance a moment to settle, then capture.
            previewView.postDelayed({
                takePhoto(context, provider, imageCapture, onStateChange, onComplete)
            }, 900)
        } catch (e: Exception) {
            DebugLog.e("CameraCapture", "Bind failed: ${e.message}")
            onStateChange(CaptureState.Error)
            onComplete(null)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun takePhoto(
    context: android.content.Context,
    provider: ProcessCameraProvider,
    imageCapture: ImageCapture,
    onStateChange: (CaptureState) -> Unit,
    onComplete: (String?) -> Unit
) {
    val imageDir = File(context.filesDir, "images").apply { mkdirs() }
    val file = File(imageDir, "photo_${UUID.randomUUID()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                DebugLog.e("CameraCapture", "Photo capture failed: ${exc.message}")
                provider.unbindAll()
                activeBinding = null
                onStateChange(CaptureState.Error)
                onComplete(null)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                provider.unbindAll()
                activeBinding = null
                onStateChange(CaptureState.Done(file.absolutePath))
                onComplete(file.absolutePath)
            }
        }
    )
}

