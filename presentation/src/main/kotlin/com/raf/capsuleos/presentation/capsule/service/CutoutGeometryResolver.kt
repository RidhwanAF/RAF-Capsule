package com.raf.capsuleos.presentation.capsule.service

import android.content.Context
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CutoutGeometry

/**
 * Hardware cutout measurement pairing physical [CutoutGeometry] metrics with the detected
 * horizontal [CameraAnchorPosition] region (`LEFT`, `CENTER`, or `RIGHT`).
 */
internal data class DetectedCutoutMeasurement(
    val geometry: CutoutGeometry,
    val detectedPosition: CameraAnchorPosition,
    val hasHardwareCutout: Boolean,
)

/**
 * Inspects [WindowManager.getCurrentWindowMetrics] and [DisplayManager] display cutouts to measure
 * physical camera hole bounds, status bar height, corner radii, and alignment region.
 */
internal object CutoutGeometryResolver {

    fun detect(context: Context): DetectedCutoutMeasurement {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val windowManager = context.getSystemService(WindowManager::class.java)
        val displayManager = context.getSystemService(DisplayManager::class.java)

        val metrics = windowManager?.currentWindowMetrics
        val displayWidthPx = (metrics?.bounds?.width() ?: context.resources.displayMetrics.widthPixels)
            .toFloat()
            .coerceAtLeast(1f)
        val screenWidthDp = (displayWidthPx / density).coerceAtLeast(320f)
        val insets = metrics?.windowInsets

        val statusBarBottomPx = insets
            ?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            ?.top
            ?: 0
        val statusBarBottomDp = (statusBarBottomPx / density).coerceAtLeast(24f)

        val tlCornerPx = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        val trCornerPx = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
        val maxCornerPx = maxOf(tlCornerPx, trCornerPx)
        val screenCornerDp = if (maxCornerPx > 0) (maxCornerPx.toFloat() / density) else 42f

        val cutout = insets?.displayCutout
            ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.cutout

        val path = cutout?.cutoutPath
        if (path != null) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            if (!bounds.isEmpty && bounds.width() > 0f && bounds.height() > 0f) {
                val centerXOffsetDp = (bounds.centerX() - (displayWidthPx / 2f)) / density
                return DetectedCutoutMeasurement(
                    geometry = CutoutGeometry(
                        holeWidthDp = bounds.width() / density,
                        holeHeightDp = bounds.height() / density,
                        topOffsetDp = bounds.top / density,
                        statusBarBottomDp = statusBarBottomDp,
                        screenWidthDp = screenWidthDp,
                        holeCenterOffsetXDp = centerXOffsetDp,
                        screenTopCornerRadiusDp = screenCornerDp,
                    ),
                    detectedPosition = resolvePositionForRatio(bounds.centerX() / displayWidthPx),
                    hasHardwareCutout = true,
                )
            }
        }

        val boundingRect = cutout?.boundingRectTop?.takeIf { !it.isEmpty }
            ?: cutout?.boundingRects?.firstOrNull { !it.isEmpty }
        if (boundingRect != null) {
            val estimatedHoleSizeDp = (boundingRect.width().toFloat() / density).coerceIn(20f, 64f)
            val bottomDp = boundingRect.bottom.toFloat() / density
            val estimatedTopDp = ((bottomDp - estimatedHoleSizeDp) * 0.5f).coerceAtLeast(4f)
            val centerX = boundingRect.exactCenterX()
            val centerXOffsetDp = (centerX - (displayWidthPx / 2f)) / density
            return DetectedCutoutMeasurement(
                geometry = CutoutGeometry(
                    holeWidthDp = estimatedHoleSizeDp,
                    holeHeightDp = estimatedHoleSizeDp,
                    topOffsetDp = estimatedTopDp,
                    statusBarBottomDp = statusBarBottomDp,
                    screenWidthDp = screenWidthDp,
                    holeCenterOffsetXDp = centerXOffsetDp,
                    screenTopCornerRadiusDp = screenCornerDp,
                ),
                detectedPosition = resolvePositionForRatio(centerX / displayWidthPx),
                hasHardwareCutout = true,
            )
        }

        return DetectedCutoutMeasurement(
            geometry = CutoutGeometry(
                statusBarBottomDp = statusBarBottomDp,
                screenWidthDp = screenWidthDp,
                screenTopCornerRadiusDp = screenCornerDp,
            ),
            detectedPosition = CameraAnchorPosition.CENTER,
            hasHardwareCutout = false,
        )
    }

    private fun resolvePositionForRatio(ratioX: Float): CameraAnchorPosition = when {
        ratioX < 0.36f -> CameraAnchorPosition.LEFT
        ratioX > 0.64f -> CameraAnchorPosition.RIGHT
        else -> CameraAnchorPosition.CENTER
    }
}
