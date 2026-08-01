package com.orangeisland.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun ZoomableImageItem(
    url: String,
    onTap: () -> Unit,
    onScaleChanged: (Float) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    consumeConditionally: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current

    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offsetX by remember(url) { mutableFloatStateOf(0f) }
    var offsetY by remember(url) { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember(url) { mutableStateOf(Size.Zero) }
    var animationJob by remember(url) { mutableStateOf<Job?>(null) }
    var lastCentroid by remember(url) { mutableStateOf(Offset.Unspecified) }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Scale factor that maps "fit between system bars" to user scale = 1f
    val baseScale = remember(containerSize, imageSize, statusBarHeight, navBarHeight) {
        if (containerSize == Size.Zero || imageSize == Size.Zero) 1f
        else {
            val imageAspect = imageSize.width / imageSize.height
            val containerAspect = containerSize.width / containerSize.height
            val fittedWidth = if (imageAspect > containerAspect) containerSize.width else containerSize.height * imageAspect
            val fittedHeight = if (imageAspect > containerAspect) containerSize.width / imageAspect else containerSize.height
            val barsPx = with(density) { statusBarHeight.toPx() + navBarHeight.toPx() }
            val sHeight = if (fittedHeight > 0f) (containerSize.height - barsPx) / fittedHeight else 1f
            val sWidth = if (fittedWidth > 0f) containerSize.width / fittedWidth else 1f
            minOf(sHeight, sWidth).coerceIn(0.1f, 1f)
        }
    }

    fun rubberBandValue(fullDelta: Float, dimension: Float): Float {
        if (dimension <= 0f) return 0f
        val c = 0.4f
        return (fullDelta * c * dimension) / (dimension + c * fullDelta)
    }

    fun visualScale(raw: Float): Float = when {
        raw < 1f -> 1f - rubberBandValue(1f - raw, 1f)
        raw > 10f -> 10f + rubberBandValue(raw - 10f, 5f)
        else -> raw
    }

    fun getMaxOffsets(currentRaw: Float): Pair<Float, Float> {
        if (imageSize == Size.Zero || containerSize == Size.Zero) return 0f to 0f
        val effectiveScale = visualScale(currentRaw) * baseScale
        val imageAspectRatio = imageSize.width / imageSize.height
        val containerAspectRatio = containerSize.width / containerSize.height
        val contentWidth = if (imageAspectRatio > containerAspectRatio) containerSize.width else containerSize.height * imageAspectRatio
        val contentHeight = if (imageAspectRatio > containerAspectRatio) containerSize.width / imageAspectRatio else containerSize.height
        val maxX = (contentWidth * effectiveScale - containerSize.width).coerceAtLeast(0f) / 2f
        val maxY = (contentHeight * effectiveScale - containerSize.height).coerceAtLeast(0f) / 2f
        return maxX to maxY
    }

    LaunchedEffect(Unit) {
        snapshotFlow { visualScale(scale) }.collect { onScaleChanged(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = onLongPress?.let { cb -> { _ -> cb() } },
                    onDoubleTap = { tapOffset ->
                        animationJob?.cancel()
                        animationJob = scope.launch {
                            val s0 = scale
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val tapRelX = tapOffset.x - centerX
                            val tapRelY = tapOffset.y - centerY
                            val isZoomIn = s0 <= 1.05f
                            val targetScale = if (isZoomIn) 3f else 1f
                            var prevS = s0
                            var prevOX = offsetX
                            var prevOY = offsetY
                            AnimationState(s0).animateTo(targetScale, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, 0.001f)) {
                                scale = value
                                val r = visualScale(value) / visualScale(prevS)
                                val (maxX, maxY) = getMaxOffsets(value)
                                if (isZoomIn) {
                                    val isLandscape = imageSize.width / imageSize.height > containerSize.width / containerSize.height
                                    val longO = if (isLandscape) maxX > 0f else maxY > 0f
                                    val shortO = if (isLandscape) maxY > 0f else maxX > 0f
                                    val pivX = if (shortO) tapRelX else if (longO && isLandscape) tapRelX else 0f
                                    val pivY = if (shortO) tapRelY else if (longO && !isLandscape) tapRelY else 0f
                                    offsetX = prevOX * r + pivX * (1f - r)
                                    offsetY = prevOY * r + pivY * (1f - r)
                                } else {
                                    // zoom-OUT: keep content at tap, clamp to bounds
                                    offsetX = (prevOX * r + tapRelX * (1f - r)).coerceIn(-maxX, maxX)
                                    offsetY = (prevOY * r + tapRelY * (1f - r)).coerceIn(-maxY, maxY)
                                }
                                prevS = value
                                prevOX = offsetX
                                prevOY = offsetY
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        coil.compose.AsyncImage(
            model = url,
            contentDescription = null,
            onSuccess = { state -> imageSize = state.painter.intrinsicSize },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(url) {
                    val velocityTracker = VelocityTracker()
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        animationJob?.cancel()
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        lastCentroid = Offset.Unspecified
                        var rawScale = scale
                        var logicalOffsetX = offsetX
                        var logicalOffsetY = offsetY
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (!pastTouchSlop) {
                                val panAmount = panChange.getDistance()
                                if (zoomChange != 1f || panAmount > touchSlop) pastTouchSlop = true
                            }
                            if (pastTouchSlop) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (zoomChange != 1f && centroid != Offset.Unspecified) lastCentroid = centroid
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val oldVis = visualScale(scale)
                                    rawScale = (rawScale * zoomChange).coerceIn(0.1f, 30f)
                                    val newVis = visualScale(rawScale)
                                    val r = if (oldVis != 0f) newVis / oldVis else 1f
                                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val isZooming = abs(1f - r) > 0.0001f
                                    if (isZooming) {
                                        logicalOffsetX = logicalOffsetX * r + (centroid.x - center.x) * (1f - r)
                                        logicalOffsetY = logicalOffsetY * r + (centroid.y - center.y) * (1f - r)
                                    } else {
                                        logicalOffsetX += panChange.x
                                        logicalOffsetY += panChange.y
                                    }
                                    val (maxX, maxY) = getMaxOffsets(rawScale)
                                    scale = rawScale
                                    offsetX = logicalOffsetX.coerceIn(-maxX, maxX)
                                    offsetY = logicalOffsetY.coerceIn(-maxY, maxY)
                                    logicalOffsetX = offsetX
                                    logicalOffsetY = offsetY
                                    if (consumeConditionally) {
                                        if (newVis > 1.05f || zoomChange != 1f || abs(panChange.y) > abs(panChange.x)) {
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } else {
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            }
                            if (event.changes.size == 1) {
                                velocityTracker.addPosition(event.changes.first().uptimeMillis, event.changes.first().position)
                            } else {
                                velocityTracker.resetTracking()
                            }
                        } while (event.changes.any { it.pressed })
                        val rawVelocity = velocityTracker.calculateVelocity()
                        val maxV = with(density) { 2500.dp.toPx() }
                        val velocity = Velocity(
                            x = if (rawVelocity.x.isNaN()) 0f else rawVelocity.x.coerceIn(-maxV, maxV),
                            y = if (rawVelocity.y.isNaN()) 0f else rawVelocity.y.coerceIn(-maxV, maxV)
                        )
                        animationJob = scope.launch {
                            if (scale < 0.95f || scale > 10.05f) {
                                val sS = scale; val sX = offsetX; val sY = offsetY
                                val targetS = scale.coerceIn(1f, 10f)
                                val (targetMaxX, targetMaxY) = getMaxOffsets(targetS)
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val pivot = center
                                val targetR = if (sS != 0f) targetS / sS else 1f
                                val finalPivotX = sX * targetR + (pivot.x - center.x) * (1f - targetR)
                                val finalPivotY = sY * targetR + (pivot.y - center.y) * (1f - targetR)
                                val targetX = finalPivotX.coerceIn(-targetMaxX, targetMaxX)
                                val targetY = finalPivotY.coerceIn(-targetMaxY, targetMaxY)
                                AnimationState(0f).animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)) {
                                    val currentS = sS + (targetS - sS) * value
                                    scale = currentS
                                    val r = if (sS != 0f) visualScale(currentS) / visualScale(sS) else 1f
                                    val pivotOX = sX * r + (pivot.x - center.x) * (1f - r)
                                    val pivotOY = sY * r + (pivot.y - center.y) * (1f - r)
                                    offsetX = pivotOX + (targetX - finalPivotX) * value
                                    offsetY = pivotOY + (targetY - finalPivotY) * value
                                }
                            } else {
                                launch {
                                    val (maxX, _) = getMaxOffsets(scale)
                                    if (offsetX > maxX || offsetX < -maxX) {
                                        offsetX = offsetX.coerceIn(-maxX, maxX)
                                    } else if (velocity.x != 0f) {
                                        val decay = splineBasedDecay<Float>(density)
                                        AnimationState(offsetX, velocity.x).animateDecay(decay) {
                                            val (curMaxX, _) = getMaxOffsets(scale)
                                            if (value > curMaxX || value < -curMaxX) {
                                                offsetX = value.coerceIn(-curMaxX, curMaxX)
                                                cancelAnimation()
                                            } else offsetX = value
                                        }
                                    }
                                }
                                launch {
                                    val (_, maxY) = getMaxOffsets(scale)
                                    if (offsetY > maxY || offsetY < -maxY) {
                                        offsetY = offsetY.coerceIn(-maxY, maxY)
                                    } else if (velocity.y != 0f) {
                                        val decay = splineBasedDecay<Float>(density)
                                        AnimationState(offsetY, velocity.y).animateDecay(decay) {
                                            val (_, curMaxY) = getMaxOffsets(scale)
                                            if (value > curMaxY || value < -curMaxY) {
                                                offsetY = value.coerceIn(-curMaxY, curMaxY)
                                                cancelAnimation()
                                            } else offsetY = value
                                        }
                                    }
                                }
                            }
                        }
                        velocityTracker.resetTracking()
                    }
                }
                .graphicsLayer(
                    scaleX = visualScale(scale) * baseScale,
                    scaleY = visualScale(scale) * baseScale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
        if (imageSize == Size.Zero) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
        }
    }
}
