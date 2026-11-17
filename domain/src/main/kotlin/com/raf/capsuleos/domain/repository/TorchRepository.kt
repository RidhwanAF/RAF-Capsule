package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsuleContent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing and adjusting the device flashlight.
 */
interface TorchRepository {
    /** Emits camera torch state updates whenever torch mode or strength changes. */
    fun observeTorchState(): Flow<CapsuleContent.Torch>

    /** Returns the latest known torch state snapshot. */
    fun currentSnapshot(): CapsuleContent.Torch

    /** Turns the torch on or off using standard mode. */
    fun setTorchEnabled(enabled: Boolean)

    /** Sets variable torch strength level (API 33+). */
    fun setTorchStrength(level: Int)
}
