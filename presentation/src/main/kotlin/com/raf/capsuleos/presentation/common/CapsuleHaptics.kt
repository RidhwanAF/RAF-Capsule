package com.raf.capsuleos.presentation.common

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Overlay-safe haptic feedback helper backed directly by [Vibrator], including a gentle
 * Circle-to-Search rising wave pattern for hold-to-expand gestures.
 */
class CapsuleHaptics(context: Context) {

    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    /** Crisp micro-tick for slider steps, taps, and swipe collapses. */
    fun tick() {
        vibratePredefined(VibrationEffect.EFFECT_TICK)
    }

    /** Soft confirmation click for transport buttons, toggles, and completed holds. */
    fun click() {
        vibratePredefined(VibrationEffect.EFFECT_CLICK)
    }

    /**
     * Plays a soft, buttery rising sinusoidal haptic wave while holding the capsule,
     * inspired by Pixel Circle-to-Search without harsh maximum amplitude slams.
     */
    fun startSoftCircleToSearchWave() {
        if (!vibrator.hasVibrator()) return
        runCatching {
            val timings = longArrayOf(40L, 45L, 45L, 50L, 50L, 55L, 55L, 60L)
            val amplitudes = intArrayOf(8, 14, 21, 30, 40, 50, 62, 74)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    /** Cancels an in-flight hold wave if the user releases early. */
    fun cancelWave() {
        runCatching { vibrator.cancel() }
    }

    private fun vibratePredefined(effectId: Int) {
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
        }
    }
}

/**
 * Remembers a [CapsuleHaptics] instance bound to the current [LocalContext].
 */
@Composable
fun rememberCapsuleHaptics(): CapsuleHaptics {
    val context = LocalContext.current
    return remember(context) { CapsuleHaptics(context) }
}
