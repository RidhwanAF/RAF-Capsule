package com.raf.capsuleos.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation 3 destination keys for Raf Capsule.
 */
@Serializable
sealed interface CapsuleRoute : NavKey {

    /** Categorized setup, permission onboarding, cutout calibration, and behavior dashboard. */
    @Serializable
    data object Setup : CapsuleRoute

    /** Isolated interactive sandbox screen for testing all capsule gestures and cards safely. */
    @Serializable
    data object Simulation : CapsuleRoute
}
