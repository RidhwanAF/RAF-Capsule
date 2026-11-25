package com.raf.capsuleos.domain.audio

import com.raf.capsuleos.domain.model.AudioBeatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FftBeatAnalyzerTest {

    @Test
    fun `given silent fft frame, when analyzed, then emits live state with zero energy and pulse`() {
        val analyzer = FftBeatAnalyzer()
        val silentFft = ByteArray(1024)

        val state = analyzer.analyze(silentFft, samplingRateMilliHz = 48_000_000, loudnessGain = 0f)

        assertTrue(state.isLive)
        assertEquals(0f, state.bass, 0.001f)
        assertEquals(0f, state.mid, 0.001f)
        assertEquals(0f, state.high, 0.001f)
        assertEquals(0f, state.energy, 0.001f)
        assertEquals(0f, state.beatPulse, 0.001f)
    }

    @Test
    fun `given quiet relaxing music with low loudnessGain, when analyzed, then stays calm and never hits max`() {
        val analyzer = FftBeatAnalyzer()
        // Normalized FFT has moderate bins, but true track loudnessGain is low (0.24 = relaxing track)
        val normalizedFrame = ByteArray(1024).apply {
            this[2] = 45
            this[3] = 20
            this[4] = 50
            this[5] = 15
            this[20] = 40
            this[21] = 18
        }

        var latest = AudioBeatState.Silent
        repeat(6) {
            latest = analyzer.analyze(normalizedFrame, samplingRateMilliHz = 48_000_000, loudnessGain = 0.24f)
        }

        assertTrue("Quiet bass should stay low (< 0.25) but was ${latest.bass}", latest.bass in 0.05f..0.25f)
        assertTrue("Quiet beatPulse should stay calm (< 0.25) but was ${latest.beatPulse}", latest.beatPulse < 0.25f)
        assertTrue("Quiet energy should stay calm (< 0.25) but was ${latest.energy}", latest.energy < 0.25f)
    }

    @Test
    fun `given strong bass impulse at high loudnessGain, when analyzed, then triggers high beat pulse and decays on silence`() {
        val analyzer = FftBeatAnalyzer()
        val silent = ByteArray(1024)
        repeat(5) { analyzer.analyze(silent, samplingRateMilliHz = 48_000_000, loudnessGain = 0f) }

        val kickFrame = ByteArray(1024).apply {
            this[2] = 105
            this[3] = 60
            this[4] = 115
            this[5] = 55
            this[6] = 95
            this[7] = 45
            this[8] = 80
            this[9] = 40
        }
        val hitState = analyzer.analyze(kickFrame, samplingRateMilliHz = 48_000_000, loudnessGain = 0.95f)
        assertTrue("Expected bass > 0.60 but was ${hitState.bass}", hitState.bass > 0.60f)
        assertTrue("Expected beatPulse > 0.70 but was ${hitState.beatPulse}", hitState.beatPulse > 0.70f)

        val decay1 = analyzer.analyze(silent, samplingRateMilliHz = 48_000_000, loudnessGain = 0f)
        val decay2 = analyzer.analyze(silent, samplingRateMilliHz = 48_000_000, loudnessGain = 0f)
        assertTrue(decay1.beatPulse < hitState.beatPulse)
        assertTrue(decay2.beatPulse < decay1.beatPulse)
    }

    @Test
    fun `given active playback when softRecover runs after HAL reattach, then preserves smoothed bands without spike`() {
        val analyzer = FftBeatAnalyzer()
        val grooveFrame = ByteArray(1024).apply {
            this[2] = 60
            this[3] = 25
            this[4] = 65
            this[5] = 30
            this[18] = 50
            this[19] = 20
        }
        var steady = AudioBeatState.Silent
        repeat(6) {
            steady = analyzer.analyze(grooveFrame, samplingRateMilliHz = 48_000_000, loudnessGain = 0.50f)
        }

        analyzer.softRecover()
        val recovered = analyzer.analyze(grooveFrame, samplingRateMilliHz = 48_000_000, loudnessGain = 0.50f)

        assertEquals(steady.bass, recovered.bass, 0.05f)
        assertTrue("Recovered pulse should not synthetic-spike above steady+0.05", recovered.beatPulse <= steady.beatPulse + 0.05f)
    }

    @Test
    fun `given too small fft buffer, when analyzed, then returns silent safely`() {
        val analyzer = FftBeatAnalyzer()
        assertEquals(AudioBeatState.Silent, analyzer.analyze(ByteArray(4), 48_000_000))
    }
}
