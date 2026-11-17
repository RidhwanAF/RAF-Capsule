package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing and adjusting system audio stream volumes.
 */
interface VolumeRepository {
    /** Emits volume snapshots when a stream volume changes. */
    fun observeVolumeChanges(): Flow<CapsuleContent.Volume>

    /** Returns an immediate snapshot of all tracked audio streams. */
    fun currentSnapshot(activeStream: AudioStreamType = AudioStreamType.MEDIA): CapsuleContent.Volume

    /** Updates the system volume index for [streamType]. */
    fun setStreamVolume(streamType: AudioStreamType, value: Int)

    /** Cycles ringer mode through Normal -> Vibrate -> Silent (when DND policy granted). */
    fun cycleRingerMode(): com.raf.capsuleos.domain.model.RingerModeState

    /** Explicitly switches the device ringer mode to [mode] (requesting DND policy if Silent isn't permitted). */
    fun setRingerMode(mode: com.raf.capsuleos.domain.model.RingerModeState): com.raf.capsuleos.domain.model.RingerModeState

    /** Returns true when Silent mode toggling via NotificationManager DND policy is permitted. */
    fun canChangeSilentMode(): Boolean
}
