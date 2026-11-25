package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.AudioBeatState
import kotlinx.coroutines.flow.StateFlow

/**
 * Streams beat-reactive spectrum telemetry from the global audio output mix (`audioSessionId = 0`)
 * while automatically gating capture against phone/VoIP calls and inactive playback states.
 */
interface AudioVisualizerRepository {
    /** Latest smoothed audio spectrum and beat-onset snapshot. */
    val beatState: StateFlow<AudioBeatState>

    /** Returns true when `android.permission.RECORD_AUDIO` is currently granted. */
    fun hasAudioPermission(): Boolean

    /**
     * Updates the visualizer session lifecycle:
     * - [shouldAttach]: keeps the native `Visualizer` attached while a media surface (State 2 pill,
     *   camera ring, or State 3 expanded card) is visible.
     * - [isPlaying]: gates live FFT processing; when false, emits [AudioBeatState.Silent].
     */
    fun setCaptureSession(shouldAttach: Boolean, isPlaying: Boolean)

    /**
     * Hints that a media transport interaction (play/pause, skip, seek, or card scrub) just occurred
     * so the repository can proactively verify and recover the hardware `Visualizer` session if the
     * OEM Audio HAL migrated output mixer handles (`ioHandle`).
     */
    fun notifyMediaTransportHint() = Unit
}
