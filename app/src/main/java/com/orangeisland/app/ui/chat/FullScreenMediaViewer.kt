package com.orangeisland.app.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangeisland.app.R
import com.orangeisland.app.ui.common.OrangeIslandHaptics
import com.orangeisland.app.ui.common.NoOpOrangeIslandHaptics
import com.orangeisland.app.ui.common.rememberOrangeIslandHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun FullScreenMediaViewer(
    urls: List<String>,
    initialIndex: Int = 0,
    pdfPages: List<String>,
    pdfSelectedPages: Set<Int>? = null,
    onTogglePdfPage: ((Int) -> Unit)? = null,
    onClose: () -> Unit,
    onNavigate: (Int) -> Unit,
    onMessage: (String) -> Unit = {},
    hapticsEnabled: Boolean = true
) {
    val url = urls.getOrNull(initialIndex) ?: return
    val haptics = rememberOrangeIslandHaptics(hapticsEnabled)
    val isPdf = pdfPages.isNotEmpty()
    val context = LocalContext.current
    val mimeType = remember(url) {
        try { context.contentResolver.getType(Uri.parse(url)) } catch (_: Exception) { null }
    }
    val isSingleVideo = mimeType?.startsWith("video/") == true ||
        url.endsWith(".mp4", true) || url.endsWith(".webm", true) ||
        url.endsWith(".mov", true) || url.endsWith(".avi", true) ||
        url.contains("vid_original_")

    if (isSingleVideo && urls.size == 1) {
        var showOverlay by remember { mutableStateOf(true) }
        var closing by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayer(uri = url, onClose = onClose, closing = closing)
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(24.dp)
            ) {
                Surface(
                    onClick = { closing = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.shadow(8.dp, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.provider_close), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(48.dp).padding(12.dp))
                }
            }
        }
        return
    }

    if (isPdf) {
        PdfPager(pdfPages, url, pdfSelectedPages, onTogglePdfPage, onClose, onNavigate)
        return
    }

    if (urls.size > 1) {
        MediaPager(urls, initialIndex, onClose, onNavigate, onMessage, haptics = haptics)
        return
    }

    // Single image — full zoom/pan experience
    SingleImage(url = url, onClose = onClose, onMessage = onMessage, haptics = haptics)
}

// --- PDF pager (existing logic) ---

@Composable
private fun PdfPager(
    pdfPages: List<String>,
    url: String,
    pdfSelectedPages: Set<Int>? = null,
    onTogglePdfPage: ((Int) -> Unit)? = null,
    onClose: () -> Unit,
    onNavigate: (Int) -> Unit
) {
    val pdfInitialPage = pdfPages.indexOf(url).coerceIn(0, pdfPages.size - 1)
    var currentScale by remember { mutableFloatStateOf(1f) }
    var showOverlay by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(initialPage = pdfInitialPage) { pdfPages.size }
    LaunchedEffect(pagerState.currentPage) {
        val idx = pagerState.currentPage
        if (idx in pdfPages.indices) onNavigate(idx)
    }
    BackHandler { onClose() }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = currentScale <= 1.05f
        ) { page ->
            ZoomableImageItem(
                url = pdfPages[page],
                onTap = { showOverlay = !showOverlay },
                onScaleChanged = { if (page == pagerState.currentPage) currentScale = it },
                consumeConditionally = true
            )
        }
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.shadow(8.dp, RoundedCornerShape(50))
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${pdfPages.size}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = { onClose() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.shadow(8.dp, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.provider_close), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(48.dp).padding(12.dp))
                }
            }
        }

        // Bottom-left selection capsule
        if (pdfSelectedPages != null && onTogglePdfPage != null) {
            val currentPage = pagerState.currentPage
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = currentPage in pdfSelectedPages,
                        onCheckedChange = { onTogglePdfPage(currentPage) },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${pdfSelectedPages.size} / ${pdfPages.size} ${stringResource(R.string.pdf_selected)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
        }
    }
}

// --- Multi-image/video pager ---

@Composable
private fun MediaPager(
    urls: List<String>,
    initialIndex: Int,
    onClose: () -> Unit,
    onNavigate: (Int) -> Unit,
    onMessage: (String) -> Unit = {},
    haptics: OrangeIslandHaptics = NoOpOrangeIslandHaptics
) {
    val context = LocalContext.current
    var currentScale by remember { mutableFloatStateOf(1f) }
    var showOverlay by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    var actionsForUrl by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, urls.size - 1)) { urls.size }
    LaunchedEffect(pagerState.currentPage) { onNavigate(pagerState.currentPage) }
    LaunchedEffect(closing) { if (closing) { kotlinx.coroutines.delay(400); onClose() } }
    BackHandler { closing = true }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = currentScale <= 1.05f
        ) { page ->
            val mediaUrl = urls[page]
            val isVideo = remember(mediaUrl) {
                val mt = try { context.contentResolver.getType(Uri.parse(mediaUrl)) } catch (_: Exception) { null }
                mt?.startsWith("video/") == true || mediaUrl.endsWith(".mp4", true) ||
                    mediaUrl.endsWith(".webm", true) || mediaUrl.endsWith(".mov", true) ||
                    mediaUrl.endsWith(".avi", true) || mediaUrl.contains("vid_original_")
            }
            if (isVideo) {
                if (page == pagerState.currentPage) {
                    VideoPlayer(uri = mediaUrl, onClose = onClose, closing = closing)
                }
            } else {
                ZoomableImageItem(
                    url = mediaUrl,
                    onTap = { showOverlay = !showOverlay },
                    onScaleChanged = { if (page == pagerState.currentPage) currentScale = it },
                    onLongPress = { haptics.longPress(); actionsForUrl = mediaUrl },
                    consumeConditionally = true
                )
            }
        }
        actionsForUrl?.let { ImageActionsSheet(url = it, onMessage = onMessage, onDismiss = { actionsForUrl = null }) }
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (urls.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(50))
                    ) {
                        Text(
                            "${pagerState.currentPage + 1} / ${urls.size}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = { closing = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.shadow(8.dp, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.provider_close), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(48.dp).padding(12.dp))
                }
            }
        }
    }
}

// --- Single image (delegates to ZoomableImageItem) ---

@Composable
private fun SingleImage(
    url: String,
    onClose: () -> Unit,
    onMessage: (String) -> Unit = {},
    haptics: OrangeIslandHaptics = NoOpOrangeIslandHaptics
) {
    var showOverlay by remember { mutableStateOf(true) }
    var showActions by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f))) {
        ZoomableImageItem(
            url = url,
            onTap = { showOverlay = !showOverlay },
            onLongPress = { haptics.longPress(); showActions = true },
            consumeConditionally = true
        )
        if (showActions) ImageActionsSheet(url = url, onMessage = onMessage, onDismiss = { showActions = false })
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(24.dp)
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.shadow(8.dp, CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.provider_close), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(48.dp).padding(12.dp))
            }
        }
    }

    BackHandler { onClose() }
}
