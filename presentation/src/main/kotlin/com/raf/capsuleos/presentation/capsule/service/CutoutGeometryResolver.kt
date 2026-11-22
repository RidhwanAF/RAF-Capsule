package com.raf.capsuleos.presentation.capsule.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.DisplayCutout
import android.view.RoundedCorner
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.LandscapeCameraSide

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
 * Automatically transforms rotated landscape cutout coordinates back into portrait orientation so
 * in-app previews and auto-detection never inherit sideways landscape offsets.
 */
internal object CutoutGeometryResolver {

    fun detect(context: Context): DetectedCutoutMeasurement {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val windowManager = context.getSystemService(WindowManager::class.java)
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = defaultDisplay?.rotation ?: Surface.ROTATION_0

        val metrics = windowManager?.currentWindowMetrics
        val displayWidthPx = (metrics?.bounds?.width() ?: context.resources.displayMetrics.widthPixels)
            .toFloat()
            .coerceAtLeast(1f)
        val displayHeightPx = (metrics?.bounds?.height() ?: context.resources.displayMetrics.heightPixels)
            .toFloat()
            .coerceAtLeast(1f)
        val isLandscape = rotation == Surface.ROTATION_90 ||
            rotation == Surface.ROTATION_270 ||
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            displayWidthPx > displayHeightPx
        val portraitWidthPx = if (isLandscape) minOf(displayWidthPx, displayHeightPx) else displayWidthPx
        val screenWidthDp = (portraitWidthPx / density).coerceAtLeast(320f)
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

        val cutout = insets?.displayCutout ?: defaultDisplay?.cutout

        val path = cutout?.cutoutPath
        if (path != null) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            if (!bounds.isEmpty && bounds.width() > 0f && bounds.height() > 0f) {
                return buildPortraitMeasurementFromBounds(
                    bounds = bounds,
                    isLandscape = isLandscape,
                    rotation = rotation,
                    displayWidthPx = displayWidthPx,
                    displayHeightPx = displayHeightPx,
                    portraitWidthPx = portraitWidthPx,
                    screenWidthDp = screenWidthDp,
                    statusBarBottomDp = statusBarBottomDp,
                    screenCornerDp = screenCornerDp,
                    density = density,
                )
            }
        }

        val boundingRect = cutout?.boundingRectTop?.takeIf { !it.isEmpty }
            ?: cutout?.boundingRects?.firstOrNull { !it.isEmpty }
        if (boundingRect != null) {
            return buildPortraitMeasurementFromBounds(
                bounds = RectF(boundingRect),
                isLandscape = isLandscape,
                rotation = rotation,
                displayWidthPx = displayWidthPx,
                displayHeightPx = displayHeightPx,
                portraitWidthPx = portraitWidthPx,
                screenWidthDp = screenWidthDp,
                statusBarBottomDp = statusBarBottomDp,
                screenCornerDp = screenCornerDp,
                density = density,
            )
        }

        return DetectedCutoutMeasurement(
            geometry = CutoutGeometry(
                statusBarBottomDp = statusBarBottomDp.coerceIn(32f, 64f),
                screenWidthDp = screenWidthDp,
                screenTopCornerRadiusDp = screenCornerDp,
            ),
            detectedPosition = CameraAnchorPosition.CENTER,
            hasHardwareCutout = false,
        )
    }

    /**
     * Returns a portrait-normalized [CutoutGeometry] guaranteed to keep the capsule visible at the
     * top of the in-app phone mockup preview regardless of current device orientation.
     */
    fun detectForPortraitPreview(context: Context): CutoutGeometry {
        val detected = detect(context).geometry
        return detected.copy(
            holeWidthDp = detected.holeWidthDp.coerceIn(22f, 42f),
            holeHeightDp = detected.holeHeightDp.coerceIn(22f, 42f),
            topOffsetDp = detected.topOffsetDp.coerceIn(6f, 34f),
            statusBarBottomDp = detected.statusBarBottomDp.coerceIn(36f, 58f),
            holeCenterOffsetXDp = detected.holeCenterOffsetXDp.coerceIn(-120f, 120f),
            screenTopCornerRadiusDp = detected.screenTopCornerRadiusDp.coerceIn(24f, 44f),
        )
    }

    /**
     * Returns live overlay [CutoutGeometry] using the current logical display width & height
     * and exact physical camera bezel coordinates (`LEFT` / `RIGHT`) in landscape mode.
     */
    fun detectLiveOverlayGeometry(context: Context): CutoutGeometry {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val windowManager = context.getSystemService(WindowManager::class.java)
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = defaultDisplay?.rotation ?: Surface.ROTATION_0
        val metrics = windowManager?.currentWindowMetrics
        val liveWidthPx = (metrics?.bounds?.width() ?: context.resources.displayMetrics.widthPixels)
            .toFloat()
            .coerceAtLeast(1f)
        val liveHeightPx = (metrics?.bounds?.height() ?: context.resources.displayMetrics.heightPixels)
            .toFloat()
            .coerceAtLeast(1f)
        val liveWidthDp = (liveWidthPx / density).coerceAtLeast(320f)
        val liveHeightDp = (liveHeightPx / density).coerceAtLeast(320f)
        val isLandscape = rotation == Surface.ROTATION_90 ||
            rotation == Surface.ROTATION_270 ||
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            liveWidthPx > liveHeightPx

        val base = detect(context).geometry
        if (!isLandscape) {
            return base.copy(
                screenWidthDp = liveWidthDp,
                screenHeightDp = liveHeightDp,
                landscapeCameraSide = LandscapeCameraSide.NONE,
            )
        }

        val cutout = metrics?.windowInsets?.displayCutout ?: defaultDisplay?.cutout
        val rawBounds = extractLiveCutoutBounds(cutout)
        val side: LandscapeCameraSide
        val centerXDp: Float
        val centerYDp: Float
        val edgeInsetDp: Float

        if (rawBounds != null) {
            side = if (rawBounds.centerX() < liveWidthPx * 0.5f) {
                LandscapeCameraSide.LEFT
            } else {
                LandscapeCameraSide.RIGHT
            }
            centerXDp = (rawBounds.centerX() - (liveWidthPx * 0.5f)) / density
            centerYDp = (rawBounds.centerY() / density).coerceIn(24f, liveHeightDp - 24f)
            edgeInsetDp = if (side == LandscapeCameraSide.LEFT) {
                (rawBounds.left / density).coerceIn(4f, 44f)
            } else {
                ((liveWidthPx - rawBounds.right) / density).coerceIn(4f, 44f)
            }
        } else {
            side = if (rotation == Surface.ROTATION_270) LandscapeCameraSide.RIGHT else LandscapeCameraSide.LEFT
            edgeInsetDp = base.topOffsetDp.coerceIn(8f, 32f)
            val halfLens = base.holeHeightDp * 0.5f
            centerXDp = if (side == LandscapeCameraSide.LEFT) {
                -(liveWidthDp * 0.5f) + edgeInsetDp + halfLens
            } else {
                (liveWidthDp * 0.5f) - edgeInsetDp - halfLens
            }
            centerYDp = liveHeightDp * 0.5f
        }

        return base.copy(
            screenWidthDp = liveWidthDp,
            screenHeightDp = liveHeightDp,
            landscapeCameraSide = side,
            landscapeHoleCenterXDp = centerXDp,
            landscapeHoleCenterYDp = centerYDp,
            landscapeHoleEdgeInsetDp = edgeInsetDp,
        )
    }

    private fun extractLiveCutoutBounds(cutout: DisplayCutout?): RectF? {
        if (cutout == null) return null
        val path = cutout.cutoutPath
        if (path != null) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            if (!bounds.isEmpty && bounds.width() > 0f && bounds.height() > 0f) return bounds
        }
        val rect = cutout.boundingRectLeft.takeIf { !it.isEmpty }
            ?: cutout.boundingRectRight.takeIf { !it.isEmpty }
            ?: cutout.boundingRects.firstOrNull { !it.isEmpty }
        return rect?.let { RectF(it) }
    }

    private fun buildPortraitMeasurementFromBounds(
        bounds: RectF,
        isLandscape: Boolean,
        rotation: Int,
        displayWidthPx: Float,
        displayHeightPx: Float,
        portraitWidthPx: Float,
        screenWidthDp: Float,
        statusBarBottomDp: Float,
        screenCornerDp: Float,
        density: Float,
    ): DetectedCutoutMeasurement {
        val holeWidthDp: Float
        val holeHeightDp: Float
        val topOffsetDp: Float
        val centerXRatio: Float
        val centerXOffsetDp: Float
        val resolvedStatusBarBottomDp: Float

        if (isLandscape) {
            holeWidthDp = (bounds.height() / density).coerceIn(20f, 64f)
            holeHeightDp = (bounds.width() / density).coerceIn(20f, 64f)
            val portraitTopPx = when (rotation) {
                Surface.ROTATION_90 -> bounds.left
                Surface.ROTATION_270 -> (displayWidthPx - bounds.right)
                else -> minOf(bounds.left, displayWidthPx - bounds.right)
            }.coerceAtLeast(0f)
            val portraitCenterXPx = when (rotation) {
                Surface.ROTATION_90 -> (displayHeightPx - bounds.centerY())
                Surface.ROTATION_270 -> bounds.centerY()
                else -> bounds.centerY()
            }.coerceIn(0f, portraitWidthPx)
            topOffsetDp = (portraitTopPx / density).coerceIn(4f, 44f)
            centerXRatio = portraitCenterXPx / portraitWidthPx
            centerXOffsetDp = (portraitCenterXPx - (portraitWidthPx / 2f)) / density
            resolvedStatusBarBottomDp = maxOf(statusBarBottomDp, topOffsetDp + holeHeightDp + 8f)
                .coerceIn(36f, 64f)
        } else {
            holeWidthDp = (bounds.width() / density).coerceIn(20f, 64f)
            holeHeightDp = (bounds.height() / density).coerceIn(20f, 64f)
            topOffsetDp = (bounds.top / density).coerceAtLeast(0f)
            centerXRatio = bounds.centerX() / displayWidthPx
            centerXOffsetDp = (bounds.centerX() - (displayWidthPx / 2f)) / density
            resolvedStatusBarBottomDp = statusBarBottomDp
        }

        return DetectedCutoutMeasurement(
            geometry = CutoutGeometry(
                holeWidthDp = holeWidthDp,
                holeHeightDp = holeHeightDp,
                topOffsetDp = topOffsetDp,
                statusBarBottomDp = resolvedStatusBarBottomDp,
                screenWidthDp = screenWidthDp,
                holeCenterOffsetXDp = centerXOffsetDp,
                screenTopCornerRadiusDp = screenCornerDp,
            ),
            detectedPosition = resolvePositionForRatio(centerXRatio),
            hasHardwareCutout = true,
        )
    }

    private fun resolvePositionForRatio(ratioX: Float): CameraAnchorPosition = when {
        ratioX < 0.36f -> CameraAnchorPosition.LEFT
        ratioX > 0.64f -> CameraAnchorPosition.RIGHT
        else -> CameraAnchorPosition.CENTER
    }
}
