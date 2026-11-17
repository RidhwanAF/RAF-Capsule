package com.raf.capsuleos.presentation.simulation

import androidx.compose.runtime.Immutable
import com.raf.capsuleos.domain.model.CapsuleUiState

/**
 * Immutable UI state for the isolated Navigation 3 interactive Capsule Simulation screen.
 */
@Immutable
data class SimulationUiState(
    val capsuleUiState: CapsuleUiState = CapsuleUiState(),
    val isMediaPlaying: Boolean = false,
    val isTorchOn: Boolean = false,
    val isCharging: Boolean = false,
    val isScreenLocked: Boolean = false,
    val simulatedBrightnessPercent: Int = 72,
    val isAutoBrightness: Boolean = true,
    val latestActionLog: String? = null,
)
