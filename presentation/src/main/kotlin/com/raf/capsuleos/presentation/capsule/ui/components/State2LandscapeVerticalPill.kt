package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.HorizontalMediaGlowStyle
import com.raf.capsuleos.domain.model.MediaAnimationColorMode
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.presentation.common.rememberCachedImageBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val LANDSCAPE_PILL_WING_DP = 48f

/**
 * Vertical camera-anchored pill geometry used in Landscape mode so the pill expands
 * vertically along the Left or Right camera bezel (taller, not wider) while honoring portrait
 * Start (`LEFT -> BOTTOM`), Center (`CENTER`), and End (`RIGHT -> TOP`) camera settings.
 */
internal data class LandscapeVerticalPillGeometry(
    val anchorPosition: CameraAnchorPosition,
    val topWingDp: Float,
    val cutoutGapDp: Float,
    val bottomWingDp: Float,
    val pillWidthDp: Float,
) {
    val totalHeightDp: Float
        get() = topWingDp + cutoutGapDp + bottomWingDp

    /**
     * Vertical offset from the camera circle's top edge to the pill's top edge so the camera
     * circle stays locked at its Start (Bottom), Center, or End (Top) anchor.
     */
    val topShiftFromAnchorCircleDp: Float
        get() = when (anchorPosition) {
            CameraAnchorPosition.CENTER -> -(topWingDp + (cutoutGapDp - pillWidthDp) * 0.5f)
            CameraAnchorPosition.LEFT -> -(totalHeightDp - pillWidthDp)
            CameraAnchorPosition.RIGHT -> 0f
        }
}

/** Computes camera-anchored vertical pill dimensions in dp for Landscape State 2. */
internal fun computeLandscapeVerticalPillGeometry(
    cutout: CutoutGeometry,
    anchorPosition: CameraAnchorPosition = CameraAnchorPosition.CENTER,
    circleDiameterDp: Float,
    isMinimalIconOnly: Boolean = false,
): LandscapeVerticalPillGeometry {
    if (isMinimalIconOnly) {
        val iconWing = 38f
        return when (anchorPosition) {
            CameraAnchorPosition.RIGHT -> LandscapeVerticalPillGeometry(
                anchorPosition = anchorPosition,
                topWingDp = 0f,
                cutoutGapDp = circleDiameterDp,
                bottomWingDp = iconWing,
                pillWidthDp = circleDiameterDp,
            )
            CameraAnchorPosition.CENTER,
            CameraAnchorPosition.LEFT,
            -> LandscapeVerticalPillGeometry(
                anchorPosition = anchorPosition,
                topWingDp = iconWing,
                cutoutGapDp = circleDiameterDp,
                bottomWingDp = 0f,
                pillWidthDp = circleDiameterDp,
            )
        }
    }
    val gap = if (anchorPosition == CameraAnchorPosition.CENTER) {
        (cutout.holeHeightDp + 8f).coerceIn(32f, 60f)
    } else {
        circleDiameterDp
    }
    val maxTotalH = (cutout.screenHeightDp - 32f).coerceAtLeast(136f)
    val wing = ((maxTotalH - gap) * 0.5f).coerceIn(36f, LANDSCAPE_PILL_WING_DP)
    return LandscapeVerticalPillGeometry(
        anchorPosition = anchorPosition,
        topWingDp = wing,
        cutoutGapDp = gap,
        bottomWingDp = wing,
        pillWidthDp = circleDiameterDp,
    )
}

/**
 * Dedicated Landscape State 2 vertical pill UI matching portrait [State2HorizontalPill]'s exact
 * design language, colors, charging water-wave fill, and media ambient glow styles, positioned
 * vertically along the physical side camera bezel.
 *
 * @param modifier Applied to the root vertical pill container.
 * @param content Active [CapsuleContent] displayed in the vertical pill.
 * @param cutout Live landscape [CutoutGeometry] for camera gap alignment.
 * @param anchorPosition Portrait camera anchor (`LEFT -> BOTTOM`, `CENTER -> CENTER`, `RIGHT -> TOP`).
 * @param circleDiameterDp Base camera ring diameter used as the slim vertical pill width.
 * @param horizontalMediaGlowStyle Media ambient glow style mirroring portrait settings.
 * @param mediaAnimationColorMode Accent color mode for media glow & equalizer animation.
 * @param swipeDirection Direction sign (`-1` or `+1`) for vertical content transition animation.
 * @param manualSwipeSeq Monotonic swipe sequence counter triggering animated swaps.
 */
@Composable
fun State2LandscapeVerticalPill(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    cutout: CutoutGeometry,
    anchorPosition: CameraAnchorPosition = CameraAnchorPosition.CENTER,
    circleDiameterDp: Float,
    isMinimalIconOnly: Boolean = false,
    horizontalMediaGlowStyle: HorizontalMediaGlowStyle = HorizontalMediaGlowStyle.OFF,
    mediaAnimationColorMode: MediaAnimationColorMode = MediaAnimationColorMode.ALBUM_COLOR,
    swipeDirection: Int,
    manualSwipeSeq: Int,
    showWaterWave: Boolean = true,
) {
    val geom = computeLandscapeVerticalPillGeometry(
        cutout = cutout,
        anchorPosition = anchorPosition,
        circleDiameterDp = circleDiameterDp,
        isMinimalIconOnly = isMinimalIconOnly,
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        if (showWaterWave && content is CapsuleContent.Charging) {
            LandscapeChargingWaterWaveFill(
                batteryPercent = content.batteryPercent,
                primaryColor = primaryColor,
                geom = geom,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedContent(
            targetState = manualSwipeSeq to content,
            contentKey = { it.first },
            contentAlignment = Alignment.Center,
            transitionSpec = {
                val dir = if (swipeDirection < 0) -1 else 1
                (slideInVertically(animationSpec = tween(220)) { h -> dir * (h / 4) } +
                    scaleIn(initialScale = 0.88f, animationSpec = tween(220)) +
                    fadeIn(animationSpec = tween(180))) togetherWith
                    (slideOutVertically(animationSpec = tween(160)) { h -> -dir * (h / 4) } +
                        scaleOut(targetScale = 0.88f, animationSpec = tween(160)) +
                        fadeOut(animationSpec = tween(130))) using
                    SizeTransform(clip = false)
            },
            label = "landscapeVerticalPillMorph",
        ) { (_, active) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isMinimalIconOnly) {
                    when (geom.anchorPosition) {
                        CameraAnchorPosition.RIGHT -> {
                            Spacer(modifier = Modifier.height(geom.cutoutGapDp.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.bottomWingDp.dp),
                            ) {
                                LandscapeTopWingIcon(
                                    content = active,
                                    primaryColor = primaryColor,
                                    tertiaryColor = tertiaryColor,
                                    horizontalMediaGlowStyle = horizontalMediaGlowStyle,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                        }
                        CameraAnchorPosition.CENTER,
                        CameraAnchorPosition.LEFT,
                        -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.topWingDp.dp),
                            ) {
                                LandscapeTopWingIcon(
                                    content = active,
                                    primaryColor = primaryColor,
                                    tertiaryColor = tertiaryColor,
                                    horizontalMediaGlowStyle = horizontalMediaGlowStyle,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                            Spacer(modifier = Modifier.height(geom.cutoutGapDp.dp))
                        }
                    }
                } else {
                    when (geom.anchorPosition) {
                        CameraAnchorPosition.CENTER -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.topWingDp.dp),
                            ) {
                                LandscapeTopWingIcon(
                                    content = active,
                                    primaryColor = primaryColor,
                                    tertiaryColor = tertiaryColor,
                                    horizontalMediaGlowStyle = horizontalMediaGlowStyle,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                            Spacer(modifier = Modifier.height(geom.cutoutGapDp.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.bottomWingDp.dp)
                                    .padding(horizontal = 3.dp),
                            ) {
                                LandscapeBottomWingTelemetry(
                                    content = active,
                                    primaryColor = primaryColor,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                        }
                        // Portrait LEFT (Start) -> Landscape BOTTOM anchor; wings expand upward above the lens
                        CameraAnchorPosition.LEFT -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.topWingDp.dp),
                            ) {
                                LandscapeTopWingIcon(
                                    content = active,
                                    primaryColor = primaryColor,
                                    tertiaryColor = tertiaryColor,
                                    horizontalMediaGlowStyle = horizontalMediaGlowStyle,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.bottomWingDp.dp)
                                    .padding(horizontal = 3.dp),
                            ) {
                                LandscapeBottomWingTelemetry(
                                    content = active,
                                    primaryColor = primaryColor,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                            Spacer(modifier = Modifier.height(geom.cutoutGapDp.dp))
                        }
                        // Portrait RIGHT (End) -> Landscape TOP anchor; wings expand downward below the lens
                        CameraAnchorPosition.RIGHT -> {
                            Spacer(modifier = Modifier.height(geom.cutoutGapDp.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.topWingDp.dp),
                            ) {
                                LandscapeTopWingIcon(
                                    content = active,
                                    primaryColor = primaryColor,
                                    tertiaryColor = tertiaryColor,
                                    horizontalMediaGlowStyle = horizontalMediaGlowStyle,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(geom.bottomWingDp.dp)
                                    .padding(horizontal = 3.dp),
                            ) {
                                LandscapeBottomWingTelemetry(
                                    content = active,
                                    primaryColor = primaryColor,
                                    mediaAnimationColorMode = mediaAnimationColorMode,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LandscapeChargingWaterWaveFill(
    modifier: Modifier = Modifier,
    batteryPercent: Int,
    primaryColor: Color,
    geom: LandscapeVerticalPillGeometry,
) {
    val chargeColor = capsuleBatteryAccentColor(
        batteryPercent = batteryPercent,
        isCharging = true,
        primaryColor = primaryColor,
    )
    val neuralCoreColor = lerp(chargeColor, Color(0xFFF5FCFF), 0.74f)
    val fillFraction by animateFloatAsState(
        targetValue = (batteryPercent.coerceIn(0, 100) / 100f).coerceIn(0.10f, 1f),
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "landscapeChargingWaterFill",
    )
    val inf = rememberInfiniteTransition(label = "landscapeChargingWave")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "landscapeChargingWavePhase",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val surfaceY = h * (1f - fillFraction)
        val ampScale = if (fillFraction >= 0.97f) 0.25f else 1f
        val amp1 = (h * 0.045f).coerceAtLeast(1.8f) * ampScale
        val amp2 = (h * 0.030f).coerceAtLeast(1.2f) * ampScale
        val steps = 28

        val backWavePath = Path().apply {
            moveTo(0f, h)
            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val x = w * u
                val y = (surfaceY + cos(u * 4.2f * PI.toFloat() - phase * 1.1f) * amp2)
                    .coerceIn(1f, h - 1f)
                lineTo(x, y)
            }
            lineTo(w, h)
            close()
        }
        drawPath(path = backWavePath, color = chargeColor.copy(alpha = 0.22f))

        val frontWavePath = Path().apply {
            moveTo(0f, h)
            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val x = w * u
                val y = (surfaceY + sin(u * 3.6f * PI.toFloat() + phase) * amp1)
                    .coerceIn(1f, h - 1f)
                lineTo(x, y)
            }
            lineTo(w, h)
            close()
        }
        drawPath(
            path = frontWavePath,
            brush = Brush.verticalGradient(
                0.0f to chargeColor.copy(alpha = 0.56f),
                0.45f to chargeColor.copy(alpha = 0.42f),
                1.0f to chargeColor.copy(alpha = 0.28f),
                startY = surfaceY.coerceAtLeast(0f),
                endY = h,
            ),
        )

        // Glowing top meniscus ridge across the rising vertical wave
        val crestPath = Path().apply {
            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val x = w * u
                val y = (surfaceY + sin(u * 3.6f * PI.toFloat() + phase) * amp1)
                    .coerceIn(1f, h - 1f)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = crestPath,
            color = Color.White.copy(alpha = 0.52f),
            style = Stroke(width = 1.2.dp.toPx()),
        )

        // Neuron bubble dots bursting out radially from the side camera hole inside the water volume
        clipPath(frontWavePath) {
            val totalH = geom.totalHeightDp.coerceAtLeast(1f)
            val camCenterFracY = when (geom.anchorPosition) {
                CameraAnchorPosition.LEFT -> (totalH - geom.cutoutGapDp * 0.5f) / totalH
                CameraAnchorPosition.CENTER -> (geom.topWingDp + geom.cutoutGapDp * 0.5f) / totalH
                CameraAnchorPosition.RIGHT -> (geom.cutoutGapDp * 0.5f) / totalH
            }
            val camCenterX = w * 0.5f
            val camCenterY = h * camCenterFracY
            val camHoleRadiusPx = ((geom.cutoutGapDp - 8f).coerceAtLeast(18f) * 0.46f).dp.toPx()
            val maxVerticalSpanPx = max(camCenterY, h - camCenterY).coerceAtLeast(camHoleRadiusPx + 12f)
            val maxTravelYPx = (maxVerticalSpanPx - camHoleRadiusPx * 0.85f).coerceAtLeast(16f)
            val maxTravelXPx = (w * 0.56f).coerceAtLeast(8f)

            val phase01 = (phase / (2.0 * PI).toFloat()).coerceIn(0f, 1f)
            val neuronDotCount = 28
            for (idx in 0 until neuronDotCount) {
                val fIdx = idx.toFloat()
                val rawProgress = ((phase01 + fIdx * 0.137f) % 1f).coerceIn(0f, 1f)
                val easedBurst = FastOutSlowInEasing.transform(rawProgress)

                val baseAngle = fIdx * 2.399963f + (idx % 3) * 0.21f
                val neuralCurl = sin(easedBurst * PI.toFloat() + fIdx * 0.9f) * 0.14f
                val angle = baseAngle + neuralCurl
                val dirX = cos(angle)
                val dirY = sin(angle)

                val reachScale = 0.56f + 0.44f * (((idx * 7 + 3) % 11) / 10f)
                val px = camCenterX + dirX * (camHoleRadiusPx * 0.72f + easedBurst * maxTravelXPx * reachScale)
                val py = camCenterY + dirY * (camHoleRadiusPx * 0.88f + easedBurst * maxTravelYPx * reachScale)

                val emergeFade = (rawProgress / 0.12f).coerceIn(0f, 1f)
                val settleFade = ((1f - rawProgress) / 0.28f).coerceIn(0f, 1f)
                val sinWave = 0.5f + 0.5f * sin(phase * 3.0f + fIdx * 2.17f)
                val twinkle = 0.36f + 0.64f * (sinWave * sinWave)
                val alphaEnvelope = emergeFade * settleFade * twinkle

                if (alphaEnvelope > 0.03f) {
                    val dotScale = 0.78f + 0.34f * (1f - easedBurst * 0.45f)
                    val dotRadiusPx = ((1.25f + (idx % 3) * 0.32f) * dotScale).dp.toPx()

                    drawCircle(
                        color = chargeColor.copy(alpha = 0.52f * alphaEnvelope),
                        radius = dotRadiusPx * 2.15f,
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = neuralCoreColor.copy(alpha = 0.94f * alphaEnvelope),
                        radius = dotRadiusPx,
                        center = Offset(px, py),
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeTopWingIcon(
    content: CapsuleContent,
    primaryColor: Color,
    tertiaryColor: Color,
    horizontalMediaGlowStyle: HorizontalMediaGlowStyle,
    mediaAnimationColorMode: MediaAnimationColorMode,
) {
    when (content) {
        is CapsuleContent.Media -> {
            val bitmap = rememberCachedImageBitmap(content.artworkBytes)
            val accent = if (mediaAnimationColorMode == MediaAnimationColorMode.ALBUM_COLOR && content.primaryColorArgb != 0) {
                Color(content.primaryColorArgb)
            } else {
                primaryColor
            }
            val showAlbumRing = horizontalMediaGlowStyle == HorizontalMediaGlowStyle.ALBUM_ART_RING && content.isPlaying
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp),
            ) {
                if (showAlbumRing) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                0f to accent.copy(alpha = 0.95f),
                                0.5f to tertiaryColor.copy(alpha = 0.85f),
                                1f to accent.copy(alpha = 0.95f),
                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                            ),
                            radius = (size.minDimension * 0.5f) - 1.dp.toPx(),
                            style = Stroke(width = 1.8.dp.toPx()),
                        )
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        is CapsuleContent.NotificationAlert -> {
            val iconBitmap = rememberCachedImageBitmap(content.iconBytes)
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        is CapsuleContent.NotificationSummary -> Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.Charging -> Icon(
            imageVector = Icons.Rounded.BatteryChargingFull,
            contentDescription = null,
            tint = capsuleBatteryAccentColor(content.batteryPercent, isCharging = true, primaryColor = primaryColor),
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.BatteryPeek -> Icon(
            imageVector = Icons.Rounded.BatteryFull,
            contentDescription = null,
            tint = capsuleBatteryAccentColor(content.batteryPercent, isCharging = false, primaryColor = primaryColor),
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.Volume -> Icon(
            imageVector = if (resolveVolumePercent(content) == 0) {
                Icons.AutoMirrored.Rounded.VolumeOff
            } else {
                Icons.AutoMirrored.Rounded.VolumeUp
            },
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.Torch -> Icon(
            imageVector = Icons.Rounded.FlashlightOn,
            contentDescription = null,
            tint = Color(0xFFFFD54F),
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.CallAlert -> Icon(
            imageVector = Icons.Rounded.Call,
            contentDescription = null,
            tint = Color(0xFF4ADE80),
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.BluetoothDevice -> Icon(
            imageVector = Icons.Rounded.BluetoothConnected,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.Hotspot -> Icon(
            imageVector = Icons.Rounded.WifiTethering,
            contentDescription = null,
            tint = tertiaryColor,
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.LiveActivity -> Icon(
            imageVector = Icons.Rounded.Timer,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.Alarm -> Icon(
            imageVector = Icons.Rounded.Alarm,
            contentDescription = null,
            tint = tertiaryColor,
            modifier = Modifier.size(20.dp),
        )
        is CapsuleContent.Unlock -> Icon(
            imageVector = Icons.Rounded.LockOpen,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(19.dp),
        )
        is CapsuleContent.SystemStatus -> {
            val icon = when (content.kind) {
                SystemStatusKind.AIRPLANE_MODE -> Icons.Rounded.AirplanemodeActive
                SystemStatusKind.DO_NOT_DISTURB -> Icons.Rounded.DoNotDisturbOn
                SystemStatusKind.BATTERY_SAVER -> Icons.Rounded.BatterySaver
                SystemStatusKind.VPN -> Icons.Rounded.VpnKey
                SystemStatusKind.RINGER_MUTED -> Icons.Rounded.NotificationsOff
                SystemStatusKind.DATA_SAVER -> Icons.Rounded.DataSaverOn
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(19.dp),
            )
        }
        else -> Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(primaryColor),
        )
    }
}

@Composable
private fun LandscapeBottomWingTelemetry(
    content: CapsuleContent,
    primaryColor: Color,
    mediaAnimationColorMode: MediaAnimationColorMode,
) {
    when (content) {
        is CapsuleContent.Media -> {
            val accent = if (mediaAnimationColorMode == MediaAnimationColorMode.ALBUM_COLOR && content.primaryColorArgb != 0) {
                Color(content.primaryColorArgb)
            } else {
                primaryColor
            }
            LandscapeCompactEqualizer(isPlaying = content.isPlaying, accent = accent)
        }
        is CapsuleContent.Charging -> CompactVerticalBadgeText(
            text = "${content.batteryPercent}%",
            color = capsuleBatteryAccentColor(content.batteryPercent, isCharging = true, primaryColor = primaryColor),
        )
        is CapsuleContent.BatteryPeek -> CompactVerticalBadgeText(
            text = "${content.batteryPercent}%",
            color = capsuleBatteryAccentColor(content.batteryPercent, isCharging = false, primaryColor = primaryColor),
        )
        is CapsuleContent.Volume -> CompactVerticalBadgeText(
            text = "${resolveVolumePercent(content)}%",
            color = Color.White,
        )
        is CapsuleContent.Torch -> CompactVerticalBadgeText(
            text = "${content.brightnessPercent}%",
            color = Color(0xFFFFD54F),
        )
        is CapsuleContent.NotificationSummary -> CompactVerticalBadgeText(
            text = "${content.count}",
            color = primaryColor,
        )
        is CapsuleContent.NotificationAlert -> Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(primaryColor),
        )
        is CapsuleContent.CallAlert -> Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF4ADE80)),
        )
        is CapsuleContent.BluetoothDevice -> CompactVerticalBadgeText(
            text = content.batteryPercent?.let { "$it%" } ?: "BT",
            color = Color.White,
        )
        is CapsuleContent.Alarm -> CompactVerticalBadgeText(
            text = if (content.isSet) content.formattedTime else "OFF",
            color = Color.White,
        )
        is CapsuleContent.LiveActivity -> CompactVerticalBadgeText(
            text = content.statusText.take(5),
            color = primaryColor,
        )
        else -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.8f)),
        )
    }
}

@Composable
private fun CompactVerticalBadgeText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LandscapeCompactEqualizer(
    isPlaying: Boolean,
    accent: Color,
) {
    val transition = rememberInfiniteTransition(label = "landscapeEq")
    val barA by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 1f else 0.35f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eqA",
    )
    val barB by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isPlaying) 0.30f else 0.45f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eqB",
    )
    val barC by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = if (isPlaying) 0.95f else 0.35f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eqC",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(16.dp),
    ) {
        listOf(barA, barB, barC).forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14f * level).coerceAtLeast(4f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
        }
    }
}

internal fun resolveVolumePercent(volume: CapsuleContent.Volume): Int {
    val active = volume.streams.firstOrNull { it.streamType == volume.activeStream }
        ?: volume.streams.firstOrNull()
        ?: return 0
    return ((active.current.toFloat() / active.max.coerceAtLeast(1).toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
}
