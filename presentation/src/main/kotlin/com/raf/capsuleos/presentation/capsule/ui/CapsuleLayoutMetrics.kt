package com.raf.capsuleos.presentation.capsule.ui

import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.LandscapeCameraSide
import com.raf.capsuleos.domain.model.SatellitePosition
import com.raf.capsuleos.presentation.capsule.ui.components.computeLandscapeVerticalPillGeometry
import com.raf.capsuleos.presentation.capsule.ui.components.computeSidePillGeometry

/**
 * Resolution-independent window/frame target geometry in dp shared identically between the real
 * [com.raf.capsuleos.presentation.capsule.service.CapsuleOverlayController] and the in-app
 * Navigation 3 Simulation sandbox.
 */
internal const val SATELLITE_GAP_DP = 8f
internal const val SATELLITE_OUTER_PAD_DP = 7f

internal data class CapsuleLayoutDpSpec(
    val widthDp: Float,
    val heightDp: Float,
    val mainWidthDp: Float,
    val mainHeightDp: Float = heightDp,
    val satelliteSizeDp: Float,
    val satelliteGapDp: Float,
    val satelliteOnLeft: Boolean,
    val satelliteVertical: Boolean = false,
    val satelliteOnTop: Boolean = false,
    val minWidthDp: Float,
    val minHeightDp: Float,
    val offsetXDp: Float,
    val topYDp: Float,
    val cornerDp: Float,
)

/** Computes exact target frame bounds in dp for any [CapsuleUiState]. */
internal fun computeCapsuleLayoutDpSpec(
    state: CapsuleUiState,
    isHoldingCapsule: Boolean = false,
): CapsuleLayoutDpSpec {
    val holdBonusDp = if (isHoldingCapsule && state.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED) 4f else 0f
    val circleDp = state.circleDiameterDp
    val anchorCenterXOffsetDp = state.resolveAnchorCenterXOffsetDp()
    val topYDp = state.anchorTopDp
    val content = state.content
    val hasSatellite = state.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
        state.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
        state.leftSatellite != null

    val preferLeft = state.preferences.satellitePosition == SatellitePosition.LEFT

    fun attachHorizontalSatellite(
        mainWidthDp: Float,
        mainHeightDp: Float,
        mainCenterXDp: Float,
        cornerDp: Float,
    ): CapsuleLayoutDpSpec {
        if (!hasSatellite) {
            return CapsuleLayoutDpSpec(
                widthDp = mainWidthDp,
                heightDp = mainHeightDp,
                mainWidthDp = mainWidthDp,
                mainHeightDp = mainHeightDp,
                satelliteSizeDp = 0f,
                satelliteGapDp = 0f,
                satelliteOnLeft = preferLeft,
                satelliteVertical = false,
                satelliteOnTop = false,
                minWidthDp = circleDp,
                minHeightDp = circleDp,
                offsetXDp = mainCenterXDp,
                topYDp = topYDp,
                cornerDp = cornerDp,
            )
        }
        val leadDp = circleDp + SATELLITE_GAP_DP + SATELLITE_OUTER_PAD_DP
        val mainLeftEdgeFromScreenLeft = (state.cutout.screenWidthDp * 0.5f) + mainCenterXDp - (mainWidthDp * 0.5f)
        val mainRightEdgeFromScreenRight = (state.cutout.screenWidthDp * 0.5f) - mainCenterXDp - (mainWidthDp * 0.5f)
        val fitsLeft = mainLeftEdgeFromScreenLeft >= leadDp + 2f
        val fitsRight = mainRightEdgeFromScreenRight >= leadDp + 2f
        val placeOnLeft = if (preferLeft) {
            fitsLeft || !fitsRight
        } else {
            !fitsRight && fitsLeft
        }
        val totalW = mainWidthDp + leadDp
        val combinedCenterX = if (placeOnLeft) {
            mainCenterXDp - (leadDp * 0.5f)
        } else {
            mainCenterXDp + (leadDp * 0.5f)
        }
        val maxHalfSpan = (state.cutout.screenWidthDp * 0.5f) - (totalW * 0.5f) - 4f
        val clampedCombinedX = if (maxHalfSpan > 0f) combinedCenterX.coerceIn(-maxHalfSpan, maxHalfSpan) else 0f
        return CapsuleLayoutDpSpec(
            widthDp = totalW,
            heightDp = mainHeightDp,
            mainWidthDp = mainWidthDp,
            mainHeightDp = mainHeightDp,
            satelliteSizeDp = circleDp,
            satelliteGapDp = SATELLITE_GAP_DP,
            satelliteOnLeft = placeOnLeft,
            satelliteVertical = false,
            satelliteOnTop = false,
            minWidthDp = circleDp,
            minHeightDp = circleDp,
            offsetXDp = clampedCombinedX,
            topYDp = topYDp,
            cornerDp = cornerDp,
        )
    }

    fun attachVerticalSatellite(
        mainWidthDp: Float,
        mainHeightDp: Float,
        mainCenterXDp: Float,
        mainTopYDp: Float,
        cornerDp: Float,
    ): CapsuleLayoutDpSpec {
        // Portrait Start (LEFT) -> Landscape Bottom; Portrait End (RIGHT) -> Landscape Top
        val preferTop = !preferLeft
        if (!hasSatellite) {
            return CapsuleLayoutDpSpec(
                widthDp = mainWidthDp,
                heightDp = mainHeightDp,
                mainWidthDp = mainWidthDp,
                mainHeightDp = mainHeightDp,
                satelliteSizeDp = 0f,
                satelliteGapDp = 0f,
                satelliteOnLeft = preferLeft,
                satelliteVertical = true,
                satelliteOnTop = preferTop,
                minWidthDp = circleDp,
                minHeightDp = circleDp,
                offsetXDp = mainCenterXDp,
                topYDp = mainTopYDp,
                cornerDp = cornerDp,
            )
        }
        val leadDp = circleDp + SATELLITE_GAP_DP + SATELLITE_OUTER_PAD_DP
        val fitsTop = mainTopYDp >= leadDp + 2f
        val fitsBottom = (state.cutout.screenHeightDp - (mainTopYDp + mainHeightDp)) >= leadDp + 2f
        val placeOnTop = if (preferTop) {
            fitsTop || !fitsBottom
        } else {
            !fitsBottom && fitsTop
        }
        val totalH = mainHeightDp + leadDp
        val rawTopY = if (placeOnTop) mainTopYDp - leadDp else mainTopYDp
        val maxTopY = (state.cutout.screenHeightDp - totalH - 4f).coerceAtLeast(4f)
        val clampedTopY = rawTopY.coerceIn(4f, maxTopY)
        return CapsuleLayoutDpSpec(
            widthDp = mainWidthDp,
            heightDp = totalH,
            mainWidthDp = mainWidthDp,
            mainHeightDp = mainHeightDp,
            satelliteSizeDp = circleDp,
            satelliteGapDp = SATELLITE_GAP_DP,
            satelliteOnLeft = preferLeft,
            satelliteVertical = true,
            satelliteOnTop = placeOnTop,
            minWidthDp = circleDp,
            minHeightDp = circleDp,
            offsetXDp = mainCenterXDp,
            topYDp = clampedTopY,
            cornerDp = cornerDp,
        )
    }

    return when (state.visualState) {
        CapsuleVisualState.HIDDEN_SYSTEM -> CapsuleLayoutDpSpec(
            widthDp = 1f,
            heightDp = 1f,
            mainWidthDp = 1f,
            mainHeightDp = 1f,
            satelliteSizeDp = 0f,
            satelliteGapDp = 0f,
            satelliteOnLeft = true,
            satelliteVertical = state.isLandscape,
            satelliteOnTop = !preferLeft,
            minWidthDp = 1f,
            minHeightDp = 1f,
            offsetXDp = anchorCenterXOffsetDp,
            topYDp = topYDp,
            cornerDp = circleDp / 2f,
        )
        CapsuleVisualState.STATE1_CUTOUT_ANCHOR -> {
            val sideDp = circleDp + holdBonusDp
            if (state.isLandscape) {
                attachVerticalSatellite(
                    mainWidthDp = sideDp,
                    mainHeightDp = sideDp,
                    mainCenterXDp = anchorCenterXOffsetDp,
                    mainTopYDp = topYDp,
                    cornerDp = sideDp / 2f,
                )
            } else {
                attachHorizontalSatellite(
                    mainWidthDp = sideDp,
                    mainHeightDp = sideDp,
                    mainCenterXDp = anchorCenterXOffsetDp,
                    cornerDp = sideDp / 2f,
                )
            }
        }
        CapsuleVisualState.STATE2_HORIZONTAL_PILL -> {
            if (state.isLandscape) {
                val vGeom = computeLandscapeVerticalPillGeometry(
                    cutout = state.cutout,
                    anchorPosition = state.preferences.cameraPosition,
                    circleDiameterDp = circleDp,
                    isMinimalIconOnly = state.isFullscreen,
                )
                val wDp = vGeom.pillWidthDp + holdBonusDp
                val hDp = vGeom.totalHeightDp + holdBonusDp
                val maxTopY = (state.cutout.screenHeightDp - hDp - 8f).coerceAtLeast(8f)
                val vTopY = (topYDp + vGeom.topShiftFromAnchorCircleDp).coerceIn(8f, maxTopY)
                attachVerticalSatellite(
                    mainWidthDp = wDp,
                    mainHeightDp = hDp,
                    mainCenterXDp = anchorCenterXOffsetDp,
                    mainTopYDp = vTopY,
                    cornerDp = wDp / 2f,
                )
            } else {
                val geom = computeSidePillGeometry(
                    content = content,
                    cutout = state.cutout,
                    anchorPosition = state.preferences.cameraPosition,
                    circleDiameterDp = circleDp,
                    isLandscape = false,
                    isMinimalIconOnly = state.isFullscreen,
                )
                val hDp = circleDp + holdBonusDp
                val rawW = geom.totalWidthDp + holdBonusDp
                val rawCenterX = anchorCenterXOffsetDp + geom.cutoutOffsetXDp
                val safeHalfW = (state.cutout.screenWidthDp * 0.5f) - (rawW * 0.5f) - 4f
                val clampedCenterX = if (safeHalfW > 0f) rawCenterX.coerceIn(-safeHalfW, safeHalfW) else 0f
                attachHorizontalSatellite(
                    mainWidthDp = rawW,
                    mainHeightDp = hDp,
                    mainCenterXDp = clampedCenterX,
                    cornerDp = hDp / 2f,
                )
            }
        }
        CapsuleVisualState.STATE3_FULL_EXPANDED -> {
            val extraContentPadDp = (state.preferences.expandedContentPaddingDp - 18f).coerceAtLeast(0f)
            val customCornerDp = state.preferences.expandedCornerRadiusDp
            if (state.isLandscape) {
                val maxLandscapeH = (state.cutout.screenHeightDp - 16f).coerceAtLeast(220f)
                val isTorch = content is CapsuleContent.Torch
                val baseW = if (isTorch) {
                    248f + extraContentPadDp * 0.6f
                } else {
                    336f + extraContentPadDp * 0.6f
                }
                val w = baseW.coerceIn(220f, minOf(state.cutout.screenWidthDp - 36f, 372f))
                // Fill full landscape height for all State 3 cards except Flashlight (compact square exception)
                val h = if (isTorch) {
                    (236f + extraContentPadDp).coerceIn(196f, maxLandscapeH)
                } else {
                    maxLandscapeH
                }
                val isRightSide = state.cutout.landscapeCameraSide == LandscapeCameraSide.RIGHT
                val baseEdgeInset = if (state.preferences.belowStatusBar) {
                    state.cutout.landscapeHoleEdgeInsetDp + state.cutout.holeWidthDp + 6f
                } else {
                    state.cutout.landscapeHoleEdgeInsetDp.coerceIn(8f, 24f)
                }
                val effectiveInset = (baseEdgeInset + state.preferences.topOffsetAdjustDp).coerceAtLeast(6f)
                val rawCenterX = if (isRightSide) {
                    (state.cutout.screenWidthDp * 0.5f) - effectiveInset - (w * 0.5f)
                } else {
                    -(state.cutout.screenWidthDp * 0.5f) + effectiveInset + (w * 0.5f)
                }
                val maxHalfX = ((state.cutout.screenWidthDp * 0.5f) - (w * 0.5f) - 6f).coerceAtLeast(0f)
                val panelCenterX = rawCenterX.coerceIn(-maxHalfX, maxHalfX)

                // Portrait LEFT (Start) -> Bottom, CENTER -> Lens Center, RIGHT (End) -> Top
                val maxPanelTop = (state.cutout.screenHeightDp - h - 8f).coerceAtLeast(8f)
                val baseTopY = when (state.preferences.cameraPosition) {
                    CameraAnchorPosition.LEFT -> maxPanelTop
                    CameraAnchorPosition.CENTER -> state.cutout.landscapeHoleCenterYDp - (h * 0.5f)
                    CameraAnchorPosition.RIGHT -> 8f
                }
                val panelTopY = (baseTopY - state.preferences.horizontalOffsetDp).coerceIn(8f, maxPanelTop)
                val landscapeEdgeInset = minOf(effectiveInset, panelTopY).coerceIn(6f, 18f)
                val autoLandscapeCorner = (state.cutout.screenTopCornerRadiusDp - landscapeEdgeInset).coerceIn(24f, 52f)
                val corner = if (customCornerDp > 0.5f) {
                    customCornerDp.coerceIn(8f, 56f)
                } else {
                    autoLandscapeCorner
                }
                CapsuleLayoutDpSpec(
                    widthDp = w,
                    heightDp = h,
                    mainWidthDp = w,
                    mainHeightDp = h,
                    satelliteSizeDp = 0f,
                    satelliteGapDp = 0f,
                    satelliteOnLeft = preferLeft,
                    satelliteVertical = true,
                    satelliteOnTop = !preferLeft,
                    minWidthDp = circleDp,
                    minHeightDp = circleDp,
                    offsetXDp = panelCenterX,
                    topYDp = panelTopY,
                    cornerDp = corner,
                )
            } else if (content is CapsuleContent.Torch) {
                val baseSq = if (content.maxStrengthLevel > 1) 208f else 184f
                val sq = baseSq + extraContentPadDp
                val bounded = sq.coerceAtMost(state.cutout.screenWidthDp - 48f).coerceAtLeast(156f)
                val autoTorchCorner = (state.cutout.screenTopCornerRadiusDp - 14f).coerceIn(26f, 44f)
                val torchCorner = if (customCornerDp > 0.5f) {
                    customCornerDp.coerceIn(8f, 56f)
                } else {
                    autoTorchCorner
                }
                val maxTorchX = ((state.cutout.screenWidthDp * 0.5f) - (bounded * 0.5f) - 16f).coerceAtLeast(0f)
                CapsuleLayoutDpSpec(
                    widthDp = bounded,
                    heightDp = bounded,
                    mainWidthDp = bounded,
                    mainHeightDp = bounded,
                    satelliteSizeDp = 0f,
                    satelliteGapDp = 0f,
                    satelliteOnLeft = true,
                    satelliteVertical = false,
                    satelliteOnTop = false,
                    minWidthDp = circleDp,
                    minHeightDp = circleDp,
                    offsetXDp = anchorCenterXOffsetDp.coerceIn(-maxTorchX, maxTorchX),
                    topYDp = topYDp,
                    cornerDp = torchCorner,
                )
            } else {
                val sidePaddingDp = if (state.preferences.belowStatusBar) {
                    14f
                } else {
                    state.anchorTopDp.coerceIn(10f, 20f)
                }
                val w = (state.cutout.screenWidthDp - sidePaddingDp * 2f).coerceAtLeast(300f)
                val baseH = when (content) {
                    is CapsuleContent.Media -> 256f
                    is CapsuleContent.CallAlert -> 206f
                    is CapsuleContent.BluetoothDevice -> 154f
                    is CapsuleContent.Hotspot -> 154f
                    is CapsuleContent.LiveActivity -> computeLiveActivityExpandedHeightDp(content)
                    is CapsuleContent.SystemStatus -> 176f
                    is CapsuleContent.Volume -> 276f
                    is CapsuleContent.NowBrief -> 298f
                    is CapsuleContent.NotificationAlert -> computeNotificationExpandedHeightDp(content)
                    else -> 154f
                }
                val h = baseH + extraContentPadDp * 1.4f
                val outsidePad = if (state.preferences.belowStatusBar) {
                    sidePaddingDp
                } else {
                    state.anchorTopDp.coerceAtLeast(sidePaddingDp)
                }
                val autoPortraitCorner = (state.cutout.screenTopCornerRadiusDp - outsidePad).coerceIn(24f, 52f)
                val concentricCorner = if (customCornerDp > 0.5f) {
                    customCornerDp.coerceIn(8f, 56f)
                } else {
                    autoPortraitCorner
                }
                val edgeAnchoredX = when (state.preferences.cameraPosition) {
                    CameraAnchorPosition.LEFT,
                    CameraAnchorPosition.CENTER,
                    CameraAnchorPosition.RIGHT,
                    -> 0f
                }
                CapsuleLayoutDpSpec(
                    widthDp = w,
                    heightDp = h,
                    mainWidthDp = w,
                    mainHeightDp = h,
                    satelliteSizeDp = 0f,
                    satelliteGapDp = 0f,
                    satelliteOnLeft = preferLeft,
                    satelliteVertical = false,
                    satelliteOnTop = false,
                    minWidthDp = circleDp,
                    minHeightDp = circleDp,
                    offsetXDp = edgeAnchoredX,
                    topYDp = topYDp,
                    cornerDp = concentricCorner,
                )
            }
        }
    }
}

private fun computeLiveActivityExpandedHeightDp(live: CapsuleContent.LiveActivity): Float {
    val hasTwoLineSubtitle = live.subtitle.length > 34
    val hasProgress = live.progressMax > 0 && live.progress >= 0
    return 186f + (if (hasTwoLineSubtitle) 18f else 0f) + (if (hasProgress) 14f else 0f)
}

private fun computeNotificationExpandedHeightDp(notification: CapsuleContent.NotificationAlert): Float {
    val lines = estimateNotificationLineCount(notification.text)
    val textAreaDp = (lines.coerceIn(2, 10) * 18.5f).coerceIn(48f, 192f)
    val attachmentBonusDp = if (notification.attachmentImageBytes != null) 116f else 0f
    val extraActionRowsDp = (notification.actions.size / 3) * 40f
    return (176f + textAreaDp + attachmentBonusDp + extraActionRowsDp)
        .coerceIn(232f, 396f + extraActionRowsDp.coerceAtMost(80f))
}

private fun estimateNotificationLineCount(text: String): Int {
    if (text.isBlank()) return 0
    val charsPerLine = 38
    return text.split('\n').sumOf { paragraph ->
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) 1 else ((trimmed.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
    }
}
