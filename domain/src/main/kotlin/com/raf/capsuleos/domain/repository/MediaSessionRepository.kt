package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsuleContent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing and controlling active system media sessions.
 */
interface MediaSessionRepository {
    /** Emits the prioritized active media track, or null when no media session is active. */
    fun observeActiveMedia(): Flow<CapsuleContent.Media?>

    /** Emits all currently active media sessions ordered by playback state. */
    fun observeMediaSessions(): Flow<List<CapsuleContent.Media>>

    /** Selects a specific active media session by [sessionTokenKey] for transport controls. */
    fun selectMediaSession(sessionTokenKey: String)

    /** Toggles play or pause on the selected or target media session. */
    fun togglePlayPause(sessionTokenKey: String? = null)

    /** Skips to the next track on the selected or target media session. */
    fun skipNext(sessionTokenKey: String? = null)

    /** Skips to the previous track on the selected or target media session. */
    fun skipPrevious(sessionTokenKey: String? = null)

    /** Seeks to [positionMs] within the selected or target track. */
    fun seekTo(positionMs: Long, sessionTokenKey: String? = null)

    /** Opens the modern system audio output switcher dialog for the active or target media session. */
    fun showSystemAudioOutputSwitcher(sessionTokenKey: String? = null): Boolean
}
