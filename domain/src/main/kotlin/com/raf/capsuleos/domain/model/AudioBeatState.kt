package com.raf.capsuleos.domain.model

/**
 * Real-time audio spectrum and beat-reactive telemetry sampled from the global output mix
 * while the expanded media player card is actively playing.
 *
 * @property isLive True while live FFT capture is actively streaming audio frames.
 * @property bass Normalized sub-bass / kick-drum energy (`0f..1f`).
 * @property mid Normalized vocal / snare mid-range energy (`0f..1f`).
 * @property high Normalized hi-hat / treble energy (`0f..1f`).
 * @property energy Perceptual overall loudness energy (`0f..1f`).
 * @property beatPulse Transient beat-onset pulse (`0f..1f`) decaying smoothly after each kick.
 */
data class AudioBeatState(
    val isLive: Boolean = false,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
    val energy: Float = 0f,
    val beatPulse: Float = 0f,
) {
    companion object {
        val Silent = AudioBeatState()
    }
}
