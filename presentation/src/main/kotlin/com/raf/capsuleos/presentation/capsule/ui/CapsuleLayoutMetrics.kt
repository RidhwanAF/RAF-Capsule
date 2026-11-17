package com.raf.capsuleos.presentation.capsule.ui

import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.presentation.capsule.ui.components.computeSidePillGeometry

/**
 * Resolution-independent window/frame target geometry in dp shared identically between the real
 * [com.raf.capsuleos.presentation.capsule.service.CapsuleOverlayController] and the in-app
 * Navigation 3 Simulation sandbox.
 */
internal data class CapsuleLayoutDpSpec(
    val widthDp: Float,
    val heightDp: Float,
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

    return when (state.visualState) {
        CapsuleVisualState.HIDDEN_SYSTEM -> CapsuleLayoutDpSpec(
            widthDp = 1f,
            heightDp = 1f,
            minWidthDp = 1f,
            minHeightDp = 1f,
            offsetXDp = anchorCenterXOffsetDp,
            topYDp = topYDp,
            cornerDp = circleDp / 2f,
        )
        CapsuleVisualState.STATE1_CUTOUT_ANCHOR -> {
            val sideDp = circleDp + holdBonusDp
            CapsuleLayoutDpSpec(
                widthDp = sideDp,
                heightDp = sideDp,
                minWidthDp = circleDp,
                minHeightDp = circleDp,
                offsetXDp = anchorCenterXOffsetDp,
                topYDp = topYDp,
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
            CapsuleLayoutDpSpec(
                widthDp = rawW,
                heightDp = hDp,
                minWidthDp = circleDp,
                minHeightDp = circleDp,
                offsetXDp = clampedCenterX,
                topYDp = topYDp,
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
                    is CapsuleContent.Media -> 240f
                    is CapsuleContent.Volume -> 262f
                    is CapsuleContent.NowBrief -> 288f
                    is CapsuleContent.NotificationAlert -> 214f
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
