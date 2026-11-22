package com.raf.capsuleos.domain.model

import kotlin.math.abs
import kotlin.math.max

/** Horizontal anchor slot for the physical camera cutout ring and State 2 pill expansion. */
enum class CameraAnchorPosition {
    LEFT,
    CENTER,
    RIGHT,
}

/** Which horizontal bezel holds the physical camera lens while the device is rotated to landscape. */
enum class LandscapeCameraSide {
    NONE,
    LEFT,
    RIGHT,
}

/** Preferred side for the separated circular status bubble relative to the main capsule. */
enum class SatellitePosition {
    LEFT,
    RIGHT,
}

/** App UI theme preference applied to Activity screens. */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Multi-selectable media glow effects for the State 3 fully expanded media player card. */
enum class ExpandedMediaGlowEffect {
    CARD_BORDER_NEURON,
    OUTER_SCREEN_GLOW,
    ALBUM_ART_RING,
}

/** Single-choice ambient media glow mode for Horizontal Expand & Camera Ring compact surfaces. */
enum class HorizontalMediaGlowStyle {
    OFF,
    HORIZONTAL_PILL,
    CAMERA_RING,
    ALBUM_ART_RING,
}

/** Color source applied to media player animations (wave, outer aura, inner card border, album ring). */
enum class MediaAnimationColorMode {
    ALBUM_COLOR,
    PRIMARY_COLOR,
}

/** Customizable action executed on double-tap or long-hold gestures. */
enum class CapsuleGestureAction {
    NONE,
    EXPAND_CARD,
    COLLAPSE_CARD,
    LOCK_SCREEN,
    TOGGLE_FLASHLIGHT,
    TOGGLE_MEDIA_PLAY_PAUSE,
    PEEK_BATTERY,
    LAUNCH_CUSTOM_APP,
}

/** Capsule card categories supporting independent double-tap / long-hold gesture overrides. */
enum class GestureCardTarget {
    IDLE_RING,
    MEDIA,
    FLASHLIGHT,
    NOTIFICATION,
    VOLUME,
}

/**
 * Bound gesture action and optional custom launch package.
 * A null [action] in card-specific overrides means "Inherit Global".
 */
data class GestureActionBinding(
    val action: CapsuleGestureAction? = null,
    val customAppPackage: String? = null,
)

/** Per-card override configuration for double-tap and long-hold gestures. */
data class CardGestureConfig(
    val doubleTap: GestureActionBinding = GestureActionBinding(),
    val longHold: GestureActionBinding = GestureActionBinding(),
)

/**
 * Physical camera cutout and status bar dimensions measured in dp from system display insets.
 *
 * @property holeWidthDp Physical cutout circle diameter in dp.
 * @property holeHeightDp Physical cutout height in dp.
 * @property topOffsetDp Distance from screen top edge to cutout top edge in dp.
 * @property statusBarBottomDp Bottom edge of the system status bar in dp.
 * @property screenWidthDp Full logical screen width in dp.
 * @property screenHeightDp Full logical screen height in dp.
 * @property holeCenterOffsetXDp Horizontal cutout offset from display center in dp (0 on centered cutouts).
 * @property screenTopCornerRadiusDp Measured hardware display top corner radius in dp for concentric expanded card rounding.
 */
data class CutoutGeometry(
    val holeWidthDp: Float = 38.4f,
    val holeHeightDp: Float = 38.4f,
    val topOffsetDp: Float = 20.8f,
    val statusBarBottomDp: Float = 80f,
    val screenWidthDp: Float = 412f,
    val screenHeightDp: Float = 915f,
    val holeCenterOffsetXDp: Float = 0f,
    val screenTopCornerRadiusDp: Float = 42f,
    val landscapeCameraSide: LandscapeCameraSide = LandscapeCameraSide.NONE,
    val landscapeHoleCenterXDp: Float = 0f,
    val landscapeHoleCenterYDp: Float = 206f,
    val landscapeHoleEdgeInsetDp: Float = 20.8f,
) {
    /** Vertical center of the physical front camera hole in dp from the top of the screen. */
    val holeCenterYDp: Float
        get() = topOffsetDp + (holeHeightDp / 2f)
}

/**
 * Configurable capsule positioning and behavior preferences.
 *
 * @property belowStatusBar Places the capsule below the status bar when enabled.
 * @property cameraPosition Horizontal anchor region (Left / Center / Right) around which the capsule originates.
 * @property paddingWidthDp Extra diameter padding around the camera hole in State 1 (dp).
 * @property topOffsetAdjustDp Fine-tune vertical adjustment in dp (-16..+16).
 * @property horizontalOffsetDp Fine-tune horizontal offset from the selected [cameraPosition] anchor in dp.
 * @property expandedContentPaddingDp Inner content padding for expanded State 3 cards in dp.
 * @property hasAutoDetectedCamera Tracks whether initial hardware cutout auto-detection already initialized positioning.
 * @property autoCollapseDelayMs Auto-collapse timeout for transient pills and State 3 card.
 * @property hideOnQsOpen When enabled, smoothly collapses expanded pills/cards/satellites down to the battery ring only and disables gestures while Quick Settings is open.
 * @property suppressSystemHeadsUp Suppresses floating system heads-up notification banners so controls remain in Quick Settings shade only (requires `WRITE_SECURE_SETTINGS`).
 */
data class CapsulePreferences(
    val belowStatusBar: Boolean = false,
    val cameraPosition: CameraAnchorPosition = CameraAnchorPosition.CENTER,
    val paddingWidthDp: Float = 12f,
    val topOffsetAdjustDp: Float = 0f,
    val horizontalOffsetDp: Float = 0f,
    val expandedContentPaddingDp: Float = 18f,
    val expandedCornerRadiusDp: Float = 0f,
    val hasAutoDetectedCamera: Boolean = false,
    val horizontalAutoCollapseDelayMs: Long = 6000L,
    val autoCollapseDelayMs: Long = 4000L,
    val mediaFollowGlobalAutoCollapse: Boolean = true,
    val mediaAutoCollapseDelayMs: Long = 15000L,
    val showOnLockScreen: Boolean = true,
    val enableLandscapeMode: Boolean = true,
    val hideOnQsOpen: Boolean = true,
    val suppressSystemHeadsUp: Boolean = true,
    val enableNeuronGlow: Boolean = true,
    val enableOuterNeuronGlow: Boolean = true,
    val expandedMediaGlowEffects: Set<ExpandedMediaGlowEffect> = setOf(
        ExpandedMediaGlowEffect.CARD_BORDER_NEURON,
        ExpandedMediaGlowEffect.OUTER_SCREEN_GLOW,
        ExpandedMediaGlowEffect.ALBUM_ART_RING,
    ),
    val horizontalMediaGlowStyle: HorizontalMediaGlowStyle = HorizontalMediaGlowStyle.OFF,
    val mediaAnimationColorMode: MediaAnimationColorMode = MediaAnimationColorMode.ALBUM_COLOR,
    val keepExpandedMediaGlowAlways: Boolean = true,
    val keepOuterMediaGlowAlways: Boolean = true,
    val showBatteryOnCameraRing: Boolean = true,
    val enableLeftSatelliteBubble: Boolean = true,
    val satellitePosition: SatellitePosition = SatellitePosition.LEFT,
    val showTorchInSatellite: Boolean = true,
    val showHotspotInSatellite: Boolean = true,
    val showLiveActivityInSatellite: Boolean = true,
    val showAirplaneInSatellite: Boolean = true,
    val showDndInSatellite: Boolean = true,
    val showBatterySaverInSatellite: Boolean = true,
    val showVpnInSatellite: Boolean = true,
    val showRingerMutedInSatellite: Boolean = true,
    val showDataSaverInSatellite: Boolean = true,
    val enableBluetoothAlerts: Boolean = true,
    val enableCallAutoExpand: Boolean = true,
    val globalDoubleTapAction: CapsuleGestureAction = CapsuleGestureAction.LOCK_SCREEN,
    val globalDoubleTapAppPackage: String? = null,
    val globalLongHoldAction: CapsuleGestureAction = CapsuleGestureAction.EXPAND_CARD,
    val globalLongHoldAppPackage: String? = null,
    val cardGestures: Map<GestureCardTarget, CardGestureConfig> = emptyMap(),
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
)

/**
 * Complete reactive UI state emitted by [com.raf.capsuleos.domain.coordinator.IslandStateCoordinator].
 */
data class CapsuleUiState(
    val visualState: CapsuleVisualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
    val content: CapsuleContent = CapsuleContent.Idle,
    val leftSatellite: CapsuleContent.LeftSatelliteItem? = null,
    val satelliteSwipeSeq: Int = 0,
    val satelliteSwipeDirection: Int = 1,
    val satelliteJiggleSeq: Int = 0,
    val mediaSessions: List<CapsuleContent.Media> = emptyList(),
    val selectedMediaIndex: Int = 0,
    val manualSwipeSeq: Int = 0,
    val swipeJiggleSeq: Int = 0,
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val isNeuralPulseActive: Boolean = false,
    val isCollapseWaveActive: Boolean = false,
    val isRingGlowActive: Boolean = false,
    val isExpandedFromSatellite: Boolean = false,
    val isSatelliteGlowActive: Boolean = false,
    val isQsOpen: Boolean = false,
    val isKeyguardLocked: Boolean = false,
    val isLandscape: Boolean = false,
    val isFullscreen: Boolean = false,
    val swipeDirection: Int = 1,
    val cutout: CutoutGeometry = CutoutGeometry(),
    val preferences: CapsulePreferences = CapsulePreferences(),
) {
    /**
     * Exact 1:1 diameter in dp for State 1 so it forms a perfect circle slightly larger
     * than the physical camera cutout for a comfortable touch/hold target and battery ring.
     */
    val circleDiameterDp: Float
        get() = max(cutout.holeWidthDp, cutout.holeHeightDp) + preferences.paddingWidthDp

    /** Computed State 1 width in dp (1:1 circle). */
    val anchorWidthDp: Float
        get() = circleDiameterDp

    /** Computed State 1 height in dp (1:1 circle). */
    val anchorHeightDp: Float
        get() = circleDiameterDp

    /**
     * Horizontal offset from display center (dp) combining [CapsulePreferences.cameraPosition],
     * measured [CutoutGeometry.holeCenterOffsetXDp], and [CapsulePreferences.horizontalOffsetDp].
     */
    fun resolveAnchorCenterXOffsetDp(): Float {
        val maxReach = ((cutout.screenWidthDp * 0.5f) - (circleDiameterDp * 0.5f)).coerceAtLeast(0f)
        if (isLandscape) {
            // Portrait topOffsetAdjustDp (+ = inward from top edge) & belowStatusBar map to inward X from the side camera bezel
            val isRightSide = cutout.landscapeCameraSide == LandscapeCameraSide.RIGHT
            val inwardSign = if (isRightSide) -1f else 1f
            val baseX = when {
                preferences.belowStatusBar -> {
                    val pastLensInset = cutout.landscapeHoleEdgeInsetDp + cutout.holeWidthDp + 4f + (circleDiameterDp * 0.5f)
                    if (isRightSide) (cutout.screenWidthDp * 0.5f) - pastLensInset else -(cutout.screenWidthDp * 0.5f) + pastLensInset
                }
                cutout.landscapeCameraSide != LandscapeCameraSide.NONE -> cutout.landscapeHoleCenterXDp
                else -> -maxReach + 16f
            }
            return (baseX + preferences.topOffsetAdjustDp * inwardSign).coerceIn(-maxReach, maxReach)
        }
        val cornerMarginDp = 16f
        val cornerMag = ((cutout.screenWidthDp * 0.5f) - (circleDiameterDp * 0.5f) - cornerMarginDp)
            .coerceAtLeast(0f)
        val edgeThreshold = cutout.screenWidthDp * 0.12f
        val baseX = when (preferences.cameraPosition) {
            CameraAnchorPosition.LEFT -> if (cutout.holeCenterOffsetXDp < -edgeThreshold) {
                cutout.holeCenterOffsetXDp
            } else {
                -cornerMag
            }
            CameraAnchorPosition.CENTER -> if (abs(cutout.holeCenterOffsetXDp) <= edgeThreshold) {
                cutout.holeCenterOffsetXDp
            } else {
                0f
            }
            CameraAnchorPosition.RIGHT -> if (cutout.holeCenterOffsetXDp > edgeThreshold) {
                cutout.holeCenterOffsetXDp
            } else {
                cornerMag
            }
        }
        return (baseX + preferences.horizontalOffsetDp).coerceIn(-maxReach, maxReach)
    }

    /**
     * Top Y coordinate in dp so the 1:1 State 1 circle and State 2 pill are centered
     * directly over the physical camera lens center in portrait, and map portrait Start/Center/End
     * (`LEFT -> BOTTOM`, `CENTER -> CENTER`, `RIGHT -> TOP`) + `horizontalOffsetDp` along the
     * side camera bezel in landscape.
     */
    val anchorTopDp: Float
        get() = when {
            isLandscape -> {
                val edgeMarginDp = 16f
                val maxTop = (cutout.screenHeightDp - circleDiameterDp - 4f).coerceAtLeast(4f)
                val baseCenterY = when (preferences.cameraPosition) {
                    CameraAnchorPosition.LEFT -> cutout.screenHeightDp - edgeMarginDp - (circleDiameterDp * 0.5f)
                    CameraAnchorPosition.CENTER -> cutout.landscapeHoleCenterYDp
                    CameraAnchorPosition.RIGHT -> edgeMarginDp + (circleDiameterDp * 0.5f)
                }
                // Portrait +horizontalOffsetDp moves toward End (Top in landscape); - moves toward Start (Bottom)
                (baseCenterY - (circleDiameterDp * 0.5f) - preferences.horizontalOffsetDp)
                    .coerceIn(4f, maxTop)
            }
            preferences.belowStatusBar -> {
                (cutout.statusBarBottomDp + 2f + preferences.topOffsetAdjustDp).coerceAtLeast(2f)
            }
            else -> {
                (cutout.holeCenterYDp - (circleDiameterDp / 2f) + preferences.topOffsetAdjustDp)
                    .coerceAtLeast(0f)
            }
        }

    /**
     * True when Quick Settings is open with [CapsulePreferences.hideOnQsOpen], OR the device is on
     * the lock screen with [CapsulePreferences.showOnLockScreen] disabled, restricting the capsule
     * to the battery camera ring only and disabling gestures.
     */
    val isQsRingOnlyActive: Boolean
        get() = (preferences.hideOnQsOpen && isQsOpen) || (!preferences.showOnLockScreen && isKeyguardLocked)

    /** True when active media, torch, live activity, or charging data is present. */
    val hasExpandableContent: Boolean
        get() = content !is CapsuleContent.Idle

    /** True when the separated left circular satellite bubble has an active indicator. */
    val hasLeftSatellite: Boolean
        get() = leftSatellite != null

    /** Resolves the effective double-tap binding for [target], falling back to the global binding when Inherit Global is selected. */
    fun resolveEffectiveDoubleTap(target: GestureCardTarget = gestureTargetForContent()): GestureActionBinding {
        val override = preferences.cardGestures[target]?.doubleTap
        return if (override?.action != null) {
            override
        } else {
            GestureActionBinding(
                action = preferences.globalDoubleTapAction,
                customAppPackage = preferences.globalDoubleTapAppPackage,
            )
        }
    }

    /** Resolves the effective long-hold binding for [target], falling back to the global binding when Inherit Global is selected. */
    fun resolveEffectiveLongHold(target: GestureCardTarget = gestureTargetForContent()): GestureActionBinding {
        val override = preferences.cardGestures[target]?.longHold
        return if (override?.action != null) {
            override
        } else {
            GestureActionBinding(
                action = preferences.globalLongHoldAction,
                customAppPackage = preferences.globalLongHoldAppPackage,
            )
        }
    }

    /** Maps [targetContent] to the corresponding [GestureCardTarget] for customizable gestures. */
    fun gestureTargetForContent(targetContent: CapsuleContent = content): GestureCardTarget = when (targetContent) {
        is CapsuleContent.Media -> GestureCardTarget.MEDIA
        is CapsuleContent.Torch -> GestureCardTarget.FLASHLIGHT
        is CapsuleContent.NotificationAlert -> GestureCardTarget.NOTIFICATION
        is CapsuleContent.Volume -> GestureCardTarget.VOLUME
        else -> GestureCardTarget.IDLE_RING
    }
}
