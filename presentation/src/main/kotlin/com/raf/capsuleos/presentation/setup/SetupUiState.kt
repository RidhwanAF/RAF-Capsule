package com.raf.capsuleos.presentation.setup

import androidx.compose.runtime.Immutable
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CutoutGeometry

/**
 * Immutable UI state for the categorized Raf Capsule setup, cutout calibration, and behavior dashboard.
 */
@Immutable
data class SetupUiState(
    val hasAccessibilityOverlay: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val hasWriteSecureSettings: Boolean = false,
    val hasWriteSettings: Boolean = false,
    val hasDndPolicyAccess: Boolean = false,
    val hasBluetoothPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isUserPaused: Boolean = false,
    val cutoutGeometry: CutoutGeometry = CutoutGeometry(),
    val detectedPosition: CameraAnchorPosition = CameraAnchorPosition.CENTER,
    val hasHardwareCutout: Boolean = false,
    val preferences: CapsulePreferences = CapsulePreferences(),
) {
    /** True when the Accessibility overlay host is actively running and unpaused. */
    val isCapsuleRunning: Boolean
        get() = hasAccessibilityOverlay && !isUserPaused

    /** True when every setup and optional integration permission is currently granted. */
    val areAllPermissionsGranted: Boolean
        get() = hasAccessibilityOverlay &&
            hasNotificationListener &&
            hasWriteSettings &&
            hasDndPolicyAccess &&
            hasBluetoothPermission &&
            hasLocationPermission &&
            hasWriteSecureSettings
}
