package com.raf.capsuleos.domain.model

import kotlin.math.max

/**
 * Physical camera cutout and status bar dimensions measured in dp from system display insets.
 *
 * @property holeWidthDp Physical cutout circle diameter in dp.
 * @property holeHeightDp Physical cutout height in dp.
 * @property topOffsetDp Distance from screen top edge to cutout top edge in dp.
 * @property statusBarBottomDp Bottom edge of the system status bar in dp.
 * @property screenWidthDp Full logical screen width in dp.
 * @property holeCenterOffsetXDp Horizontal cutout offset from display center in dp (0 on centered cutouts).
 * @property screenTopCornerRadiusDp Measured hardware display top corner radius in dp for concentric expanded card rounding.
 */
data class CutoutGeometry(
    val holeWidthDp: Float = 38.4f,
    val holeHeightDp: Float = 38.4f,
    val topOffsetDp: Float = 20.8f,
    val statusBarBottomDp: Float = 80f,
    val screenWidthDp: Float = 412f,
    val holeCenterOffsetXDp: Float = 0f,
    val screenTopCornerRadiusDp: Float = 42f,
) {
    /** Vertical center of the physical front camera hole in dp from the top of the screen. */
    val holeCenterYDp: Float
        get() = topOffsetDp + (holeHeightDp / 2f)
}

/**
 * Configurable capsule positioning and behavior preferences.
 *
 * @property belowStatusBar Places the capsule below the status bar when enabled.
 * @property paddingWidthDp Extra diameter padding around the camera hole in State 1 (dp).
 * @property paddingHeightDp Extra vertical padding in State 2 (dp).
 * @property topOffsetAdjustDp Fine-tune vertical adjustment in dp (-16..+16).
 * @property horizontalOffsetDp Fine-tune horizontal offset from center in dp.
 * @property autoCollapseDelayMs Auto-collapse timeout for transient pills and State 3 card.
 * @property autoHideStatusBarIcons Whether State 3 expanded card hides status bar icons via `icon_blacklist`.
 * @property suppressSystemHeadsUp Suppresses floating system heads-up notification banners so controls remain in Quick Settings shade only (requires `WRITE_SECURE_SETTINGS`).
 */
data class CapsulePreferences(
    val belowStatusBar: Boolean = false,
    val paddingWidthDp: Float = 12f,
    val paddingHeightDp: Float = 8f,
    val topOffsetAdjustDp: Float = 0f,
    val horizontalOffsetDp: Float = 0f,
    val autoCollapseDelayMs: Long = 4000L,
    val autoHideStatusBarIcons: Boolean = true,
    val suppressSystemHeadsUp: Boolean = true,
)

/**
 * Complete reactive UI state emitted by [com.raf.capsuleos.domain.coordinator.IslandStateCoordinator].
 *
 * @property visualState Current visual stage of the 3-state capsule.
 * @property content Active domain content rendered in the capsule.
 * @property manualSwipeSeq Monotonic counter incremented only on explicit horizontal swipe gestures.
 * @property batteryPercent Live battery percentage (0..100) rendered on the 1:1 camera ring.
 * @property isCharging True while the device is charging.
 * @property isNeuralPulseActive Whether the Gemini-style neural dot/border pulse is boosted.
 * @property swipeDirection Horizontal slide animation direction (`-1` or `1`) for content carousel.
 * @property cutout Physical camera cutout and status bar geometry measured on the device.
 * @property preferences Active capsule tuning and behavior settings.
 */
data class CapsuleUiState(
    val visualState: CapsuleVisualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
    val content: CapsuleContent = CapsuleContent.Idle,
    val manualSwipeSeq: Int = 0,
    val swipeJiggleSeq: Int = 0,
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val isNeuralPulseActive: Boolean = false,
    val isCollapseWaveActive: Boolean = false,
    val isRingGlowActive: Boolean = false,
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
     * Top Y coordinate in dp so the 1:1 State 1 circle and State 2 pill are centered
     * directly over the physical camera lens center (`cutout.holeCenterYDp`).
     */
    val anchorTopDp: Float
        get() = if (preferences.belowStatusBar) {
            (cutout.statusBarBottomDp + 2f + preferences.topOffsetAdjustDp).coerceAtLeast(2f)
        } else {
            (cutout.holeCenterYDp - (circleDiameterDp / 2f) + preferences.topOffsetAdjustDp)
                .coerceAtLeast(0f)
        }

    /** True when status bar icons should be hidden (only while State 3 expanded card overlaps top bar). */
    val shouldHideStatusBarIcons: Boolean
        get() = !preferences.belowStatusBar &&
            preferences.autoHideStatusBarIcons &&
            visualState == CapsuleVisualState.STATE3_FULL_EXPANDED

    /** True when active media, torch, live activity, or charging data is present. */
    val hasExpandableContent: Boolean
        get() = content !is CapsuleContent.Idle
}
