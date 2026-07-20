package com.orangeisland.app.ui.common

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import com.orangeisland.app.service.AppForegroundTracker

@Stable
interface OrangeIslandHaptics {
    fun action()
    fun selection()
    fun longPress()
    fun success()
    fun reject()
    fun generationStart()
    fun generationTick()
    fun generationEnd()
    fun generationStopped()
    fun startAnsweringTexture()
    fun stopAnsweringTexture()
}

object NoOpOrangeIslandHaptics : OrangeIslandHaptics {
    override fun action() = Unit
    override fun selection() = Unit
    override fun longPress() = Unit
    override fun success() = Unit
    override fun reject() = Unit
    override fun generationStart() = Unit
    override fun generationTick() = Unit
    override fun generationEnd() = Unit
    override fun generationStopped() = Unit
    override fun startAnsweringTexture() = Unit
    override fun stopAnsweringTexture() = Unit
}

val LocalOrangeIslandHaptics = compositionLocalOf<OrangeIslandHaptics> { NoOpOrangeIslandHaptics }

@Composable
fun rememberOrangeIslandHaptics(enabled: Boolean): OrangeIslandHaptics {
    val view = LocalView.current
    val enabledState = rememberUpdatedState(enabled)
    val haptics = remember(view) {
        PlatformOrangeIslandHaptics(view) { enabledState.value }
    }
    DisposableEffect(haptics) {
        val listener: (Boolean) -> Unit = { inForeground ->
            if (!inForeground) haptics.stopAnsweringTexture()
        }
        AppForegroundTracker.addListener(listener)
        onDispose {
            AppForegroundTracker.removeListener(listener)
            haptics.stopAnsweringTexture()
        }
    }
    return haptics
}

private class PlatformOrangeIslandHaptics(
    private val view: View,
    private val enabled: () -> Boolean
) : OrangeIslandHaptics {
    private val vibrator: Vibrator? = view.context.applicationContext.findVibrator()
    private var answeringTextureActive = false

    override fun action() = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    override fun selection() = perform(HapticFeedbackConstants.CLOCK_TICK)

    override fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    override fun success() = perform(confirmFeedback())

    override fun reject() = perform(rejectFeedback())

    override fun generationStart() = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    override fun generationTick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    override fun generationEnd() = success()

    override fun generationStopped() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    override fun startAnsweringTexture() {
        if (!isAllowed() || answeringTextureActive) return
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        answeringTextureActive = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(16L, 32L),
                    intArrayOf(12, 0),
                    0
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 16L, 32L), 0)
        }
    }

    override fun stopAnsweringTexture() {
        if (!answeringTextureActive) return
        answeringTextureActive = false
        vibrator?.cancel()
    }

    private fun perform(type: Int) {
        if (isAllowed()) {
            view.performHapticFeedback(type)
        }
    }

    private fun isAllowed(): Boolean = enabled() && AppForegroundTracker.isInForeground

    private fun confirmFeedback(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }

    private fun rejectFeedback(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
}

private fun Context.findVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
