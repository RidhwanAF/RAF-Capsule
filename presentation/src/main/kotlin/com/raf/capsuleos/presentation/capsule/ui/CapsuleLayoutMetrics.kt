package com.raf.capsuleos.presentation.capsule.ui

import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.SatellitePosition
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
    val satelliteSizeDp: Float,
    val satelliteGapDp: Float,
    val satelliteOnLeft: Boolean,
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

    fun attachSatellite(
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
                satelliteSizeDp = 0f,
                satelliteGapDp = 0f,
                satelliteOnLeft = preferLeft,
                minWidthDp = circleDp,
                minHeightDp = circleDp,
                offsetXDp = mainCenterXDp,
                topYDp = topYDp,
                cornerDp = cornerDp,
            )
        }
        val satSizeDp = circleDp
        val leadDp = satSizeDp + SATELLITE_GAP_DP + SATELLITE_OUTER_PAD_DP
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
            satelliteSizeDp = satSizeDp,
            satelliteGapDp = SATELLITE_GAP_DP,
            satelliteOnLeft = placeOnLeft,
            minWidthDp = circleDp,
            minHeightDp = circleDp,
            offsetXDp = clampedCombinedX,
            topYDp = topYDp,
            cornerDp = cornerDp,
        )
    }

    return when (state.visualState) {
        CapsuleVisualState.HIDDEN_SYSTEM -> CapsuleLayoutDpSpec(
            widthDp = 1f,
            heightDp = 1f,
            mainWidthDp = 1f,
            satelliteSizeDp = 0f,
            satelliteGapDp = 0f,
            satelliteOnLeft = true,
            minWidthDp = 1f,
            minHeightDp = 1f,
            offsetXDp = anchorCenterXOffsetDp,
            topYDp = topYDp,
            cornerDp = circleDp / 2f,
        )
        CapsuleVisualState.STATE1_CUTOUT_ANCHOR -> {
            val sideDp = circleDp + holdBonusDp
            attachSatellite(
                mainWidthDp = sideDp,
                mainHeightDp = sideDp,
                mainCenterXDp = anchorCenterXOffsetDp,
                cornerDp = sideDp / 2f,
            )
        }
        CapsuleVisualState.STATE2_HORIZONTAL_PILL -> {
            val geom = computeSidePillGeometry(
                content = content,
                cutout = state.cutout,
                anchorPosition = state.preferences.cameraPosition,
                circleDiameterDp = circleDp,
            )
            val hDp = circleDp + holdBonusDp
            val rawW = geom.totalWidthDp + holdBonusDp
            val rawCenterX = anchorCenterXOffsetDp + geom.cutoutOffsetXDp
            val safeHalfW = (state.cutout.screenWidthDp * 0.5f) - (rawW * 0.5f) - 4f
            val clampedCenterX = if (safeHalfW > 0f) rawCenterX.coerceIn(-safeHalfW, safeHalfW) else 0f
            attachSatellite(
                mainWidthDp = rawW,
                mainHeightDp = hDp,
                mainCenterXDp = clampedCenterX,
                cornerDp = hDp / 2f,
            )
        }
        CapsuleVisualState.STATE3_FULL_EXPANDED -> {
            val extraContentPadDp = (state.preferences.expandedContentPaddingDp - 18f).coerceAtLeast(0f)
            if (content is CapsuleContent.Torch) {
                val baseSq = if (content.maxStrengthLevel > 1) 208f else 184f
                val sq = baseSq + extraContentPadDp
                val bounded = sq.coerceAtMost(state.cutout.screenWidthDp - 48f)
                val torchCorner = (state.cutout.screenTopCornerRadiusDp - 14f).coerceIn(26f, 36f)
                val maxTorchX = ((state.cutout.screenWidthDp * 0.5f) - (bounded * 0.5f) - 16f).coerceAtLeast(0f)
                CapsuleLayoutDpSpec(
                    widthDp = bounded,
                    heightDp = bounded,
                    mainWidthDp = bounded,
                    satelliteSizeDp = 0f,
                    satelliteGapDp = 0f,
                    satelliteOnLeft = true,
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
                    is CapsuleContent.Media -> 252f
                    is CapsuleContent.CallAlert -> 196f
                    is CapsuleContent.BluetoothDevice -> 142f
                    is CapsuleContent.Hotspot -> 142f
                    is CapsuleContent.LiveActivity -> 158f
                    is CapsuleContent.SystemStatus -> 150f
                    is CapsuleContent.Volume -> 262f
                    is CapsuleContent.NowBrief -> 288f
                    is CapsuleContent.NotificationAlert -> computeNotificationExpandedHeightDp(content)
                    else -> 148f
                }
                val h = baseH + extraContentPadDp * 1.4f
                val outsidePad = if (state.preferences.belowStatusBar) {
                    sidePaddingDp
                } else {
                    state.anchorTopDp.coerceAtLeast(sidePaddingDp)
                }
                val concentricCorner = (state.cutout.screenTopCornerRadiusDp - outsidePad).coerceIn(24f, 48f)
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
                    satelliteSizeDp = 0f,
                    satelliteGapDp = 0f,
                    satelliteOnLeft = preferLeft,
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

private fun computeNotificationExpandedHeightDp(notification: CapsuleContent.NotificationAlert): Float {
    val lines = estimateNotificationLineCount(notification.text)
    val textAreaDp = (lines.coerceIn(2, 10) * 18.5f).coerceIn(44f, 188f)
    val attachmentBonusDp = if (notification.attachmentImageBytes != null) 116f else 0f
    return (152f + textAreaDp + attachmentBonusDp).coerceIn(220f, 384f)
}

private fun estimateNotificationLineCount(text: String): Int {
    if (text.isBlank()) return 0
    val charsPerLine = 38
    return text.split('\n').sumOf { paragraph ->
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) 1 else ((trimmed.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
    }
}
