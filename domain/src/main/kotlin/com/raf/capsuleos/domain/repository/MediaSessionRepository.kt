package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsuleContent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing and controlling active system media sessions.
 */
interface MediaSessionRepository {
    /** Emits the currently active media track, or null when no media session is active. */
    fun observeActiveMedia(): Flow<CapsuleContent.Media?>

    /** Toggles play or pause on the active media session. */
    fun togglePlayPause()

    /** Skips to the next track. */
    fun skipNext()

    /** Skips to the previous track. */
    fun skipPrevious()

    /** Seeks to [positionMs] within the active track. */
    fun seekTo(positionMs: Long)
}
