package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.BrightnessInfo
import kotlinx.coroutines.flow.Flow

/**
 * Observes and updates device screen brightness and adaptive-brightness mode.
 */
interface BrightnessRepository {
    /** Emits updated [BrightnessInfo] snapshots whenever system brightness or mode changes. */
    fun observeBrightnessChanges(): Flow<BrightnessInfo>

    /** Reads the current synchronous [BrightnessInfo] snapshot from system settings. */
    fun currentSnapshot(): BrightnessInfo

    /**
     * Writes display brightness level clamped to `0..100` percent.
     *
     * @return Updated [BrightnessInfo], or opens permission settings / falls back cleanly when denied.
     */
    fun setBrightnessPercent(percent: Int): BrightnessInfo

    /**
     * Toggles adaptive auto-brightness mode on/off.
     *
     * @return Updated [BrightnessInfo].
     */
    fun toggleAutoBrightness(): BrightnessInfo
}
