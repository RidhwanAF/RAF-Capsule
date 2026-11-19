package com.raf.capsuleos.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Distinct customization domains that can be expanded inline or opened in their own separate screen.
 */
@Serializable
enum class CustomizationSection {
    CAMERA_POSITION,
    BEHAVIOR_LAYOUT,
    INDICATORS_MEDIA,
    GESTURES_ACTIONS,
}

/**
 * Type-safe Navigation 3 destination keys for RAF Capsule OS.
 */
@Serializable
sealed interface CapsuleRoute : NavKey {

    /** Home dashboard with Setup card, separated expandable Capsule Configuration categories, and quick tests. */
    @Serializable
    data object Setup : CapsuleRoute

    /** Dedicated Camera Position & Cutout calibration screen. */
    @Serializable
    data object CameraPosition : CapsuleRoute

    /** Dedicated Capsule Behavior & Layout screen. */
    @Serializable
    data object BehaviorLayout : CapsuleRoute

    /** Dedicated Indicators, Left Status Bubble & Media Glow screen. */
    @Serializable
    data object IndicatorsMedia : CapsuleRoute

    /** Dedicated Global & Per-Card Gestures and Actions screen. */
    @Serializable
    data object GesturesActions : CapsuleRoute

    /** Isolated interactive sandbox screen for testing all capsule gestures and cards safely. */
    @Serializable
    data object Simulation : CapsuleRoute

    /** App-level Theme (System/Light/Dark, Dynamic Color / M3 Expressive Monochrome) and System-Synced Language screen. */
    @Serializable
    data object AppSettings : CapsuleRoute
}
