package com.raf.capsuleos.domain.audio

import com.raf.capsuleos.domain.model.AudioBeatState
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * Pure Kotlin FFT spectrum and beat-reactive analyzer using [hypot], [ln], and [pow].
 *
 * Combines normalized FFT spectral shape (`SCALING_MODE_NORMALIZED`, preserving crisp bass/mid/high
 * resolution at any playback volume) with true track loudness (`loudnessGain` derived from
 * `Visualizer.MeasurementPeakRms` compensated for system volume) so:
 * - Relaxing / quiet music stays genuinely gentle (`0.08..0.30`), never hitting max scale/speed.
 * - Heavy kicks and loud drops proportionally drive high energy and punchy [AudioBeatState.beatPulse].
 */
class FftBeatAnalyzer {

    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedHigh = 0f
    private var smoothedEnergy = 0f
    private var bassBaseline = 0f
    private var hasBaseline = false
    private var currentBeatPulse = 0f

    /** Resets all running averages and envelope state back to silence. */
    fun reset() {
        smoothedBass = 0f
        smoothedMid = 0f
        smoothedHigh = 0f
        smoothedEnergy = 0f
        bassBaseline = 0f
        hasBaseline = false
        currentBeatPulse = 0f
    }

    /**
     * Preserves live smoothed spectrum bands across seamless HAL re-attachments while resetting only
     * the flux baseline so the first post-recovery frame never produces a synthetic spike or dip.
     */
    fun softRecover() {
        hasBaseline = false
    }

    /**
     * Processes one FFT capture frame fused with hardware Peak/RMS amplitude loudness & crest factor.
     *
     * @param fft Packed FFT bytes returned by `Visualizer.OnDataCaptureListener.onFftDataCapture`.
     * @param samplingRateMilliHz Sampling rate reported in milliHertz (e.g., `48_000_000` for 48 kHz).
     * @param loudnessGain True track loudness factor (`0f..1f`) from `MeasurementPeakRms` + volume dB.
     * @param transientCrest Time-domain peak-over-RMS crest factor (`0f..1f`) capturing sharp drum hits.
     */
    fun analyze(
        fft: ByteArray,
        samplingRateMilliHz: Int,
        loudnessGain: Float = 1f,
        transientCrest: Float = 0f,
    ): AudioBeatState {
        val binCount = fft.size / 2
        if (binCount < 4) return AudioBeatState.Silent

        val clampedGain = loudnessGain.coerceIn(0f, 1f)
        val clampedCrest = transientCrest.coerceIn(0f, 1f)
        val sampleRateHz = (samplingRateMilliHz / 1000.0).coerceAtLeast(8_000.0)
        val hzPerBin = sampleRateHz / fft.size.toDouble()

        var bassSum = 0.0
        var bassMax = 0.0
        var bassBins = 0
        var midSum = 0.0
        var midMax = 0.0
        var midBins = 0
        var highSum = 0.0
        var highMax = 0.0
        var highBins = 0

        (1 until binCount).forEach { k ->
            val re = fft[2 * k].toDouble()
            val im = fft[2 * k + 1].toDouble()
            val mag = hypot(re, im)
            val freqHz = k * hzPerBin

            when {
                freqHz < 20.0 -> Unit
                freqHz <= 220.0 -> {
                    bassSum += mag
                    if (mag > bassMax) bassMax = mag
                    bassBins++
                }
                freqHz <= 2_000.0 -> {
                    midSum += mag
                    if (mag > midMax) midMax = mag
                    midBins++
                }
                freqHz <= 10_000.0 -> {
                    highSum += mag
                    if (mag > highMax) highMax = mag
                    highBins++
                }
            }
        }

        val rawBass = mapMagnitudeToLevel(blendMeanAndPeak(bassSum, bassBins, bassMax), exponent = 1.45) * clampedGain
        val rawMid = mapMagnitudeToLevel(blendMeanAndPeak(midSum, midBins, midMax), exponent = 1.50) * clampedGain
        val rawHigh = mapMagnitudeToLevel(blendMeanAndPeak(highSum, highBins, highMax), exponent = 1.55) * clampedGain
        val rawEnergy = (rawBass * 0.55f + rawMid * 0.30f + rawHigh * 0.15f).coerceIn(0f, 1f)

        // Responsive equalizer-style tracking: fast attack on hits, smooth decay between beats
        smoothedBass = smoothAsymmetric(smoothedBass, rawBass, attack = 0.74f, release = 0.30f)
        smoothedMid = smoothAsymmetric(smoothedMid, rawMid, attack = 0.65f, release = 0.28f)
        smoothedHigh = smoothAsymmetric(smoothedHigh, rawHigh, attack = 0.65f, release = 0.30f)
        smoothedEnergy = smoothAsymmetric(smoothedEnergy, rawEnergy, attack = 0.68f, release = 0.28f)

        // Fuse FFT positive bass spectral flux with time-domain amplitude crest scaled by loudness
        val flux = if (hasBaseline) (rawBass - bassBaseline).coerceAtLeast(0f) else 0f
        val crestBoost = if (rawEnergy > 0.005f) clampedCrest * clampedGain * 0.34f else 0f
        val proportionalPunch = (rawBass * 0.54f + flux * 1.55f + crestBoost).coerceIn(0f, 1f)
        currentBeatPulse = if (proportionalPunch > currentBeatPulse) {
            proportionalPunch
        } else {
            (currentBeatPulse * 0.70f + proportionalPunch * 0.30f).let { if (it < 0.008f) 0f else it }
        }

        bassBaseline = if (!hasBaseline) rawBass else bassBaseline * 0.85f + rawBass * 0.15f
        hasBaseline = true

        return AudioBeatState(
            isLive = true,
            bass = smoothedBass,
            mid = smoothedMid,
            high = smoothedHigh,
            energy = smoothedEnergy,
            beatPulse = currentBeatPulse,
        )
    }

    private fun blendMeanAndPeak(sum: Double, count: Int, peak: Double): Double {
        if (count <= 0) return 0.0
        val mean = sum / count
        return mean * 0.40 + peak * 0.60
    }

    private fun mapMagnitudeToLevel(magnitude: Double, exponent: Double): Float {
        if (magnitude <= NOISE_FLOOR_MAG) return 0f
        val effectiveMag = (magnitude - NOISE_FLOOR_MAG).coerceAtLeast(0.0)
        val logNorm = (ln(1.0 + effectiveMag * LOG_SCALE) / LOG_DENOM).coerceIn(0.0, 1.0)
        return logNorm.pow(exponent).toFloat().coerceIn(0f, 1f)
    }

    private fun smoothAsymmetric(previous: Float, target: Float, attack: Float, release: Float): Float {
        val alpha = if (target >= previous) attack else release
        return (previous + (target - previous) * alpha).coerceIn(0f, 1f)
    }

    private companion object {
        const val NOISE_FLOOR_MAG = 1.0
        const val LOG_SCALE = 0.55
        val LOG_DENOM = ln(1.0 + 110.0 * LOG_SCALE)
    }
}
