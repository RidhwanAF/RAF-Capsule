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

    private var isSlideWaveActive = false

    /** True when the device hardware motor supports fine-grained continuous amplitude waveforms. */
    val supportsAmplitudeWave: Boolean
        get() = vibrator.hasVibrator() && vibrator.hasAmplitudeControl()

    /** Crisp micro-tick for taps and discrete single-step actions. */
    fun tick() {
        vibratePredefined(VibrationEffect.EFFECT_TICK)
    }

    /** Soft confirmation click for transport buttons, toggles, and completed holds. */
    fun click() {
        vibratePredefined(VibrationEffect.EFFECT_CLICK)
    }

    /**
     * Starts or sustains a silky, low-amplitude continuous wave while sliding a slider or
     * scrubbing a gesture — modeled after Android's system volume slider texture rather than
     * discrete step ticks. Does not restart if already active so motion stays completely seamless.
     */
    fun onSmoothSlideMove() {
        if (!supportsAmplitudeWave || isSlideWaveActive) return
        isSlideWaveActive = true
        runCatching {
            val timings = longArrayOf(24L, 48L, 52L, 52L, 48L)
            val amplitudes = intArrayOf(2, 4, 6, 5, 4)
            // Repeat from index 1 so the whisper-light low-amplitude wave flows seamlessly without seams.
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 1))
        }
    }

    /** Immediately stops the continuous smooth slide wave when movement pauses or drag ends. */
    fun stopSmoothSlideWave() {
        if (!isSlideWaveActive) return
        isSlideWaveActive = false
        runCatching { vibrator.cancel() }
    }

    /** Whisper-soft low-frequency endpoint thud played once when a slider reaches its exact min/max bound. */
    fun slideBoundaryThud() {
        if (!vibrator.hasVibrator()) return
        stopSmoothSlideWave()
        runCatching {
            val effect = if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.18f)
                    .compose()
            } else if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createOneShot(10L, 14)
            } else {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            vibrator.vibrate(effect)
        }
    }

    /**
     * Plays a soft, buttery rising sinusoidal haptic wave while holding the capsule,
     * inspired by Pixel Circle-to-Search without harsh maximum amplitude slams.
     */
    fun startSoftCircleToSearchWave() {
        if (!vibrator.hasVibrator()) return
        isSlideWaveActive = false
        runCatching {
            val timings = longArrayOf(40L, 45L, 45L, 50L, 50L, 55L, 55L, 60L)
            val amplitudes = intArrayOf(8, 14, 21, 30, 40, 50, 62, 74)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    /** Cancels an in-flight hold or slide wave if the user releases early. */
    fun cancelWave() {
        isSlideWaveActive = false
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
