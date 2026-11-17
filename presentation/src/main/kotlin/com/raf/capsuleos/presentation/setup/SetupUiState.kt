package com.raf.capsuleos.presentation.setup

import androidx.compose.runtime.Immutable
import com.raf.capsuleos.domain.model.CapsulePreferences

/**
 * Immutable UI state for the Raf Capsule setup, permission, and tuning dashboard.
 */
@Immutable
data class SetupUiState(
    val hasAccessibilityOverlay: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val hasWriteSecureSettings: Boolean = false,
    val hasWriteSettings: Boolean = false,
    val hasDndPolicyAccess: Boolean = false,
    val isUserPaused: Boolean = false,
    val preferences: CapsulePreferences = CapsulePreferences(),
) {
    /** True when the Accessibility overlay host is actively running and unpaused. */
    val isCapsuleRunning: Boolean
        get() = hasAccessibilityOverlay && !isUserPaused
}
