package com.raf.capsuleos.presentation.capsule.ui.components

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.raf.capsuleos.domain.model.SatelliteSource
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.domain.model.UnlockMethod
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.rememberCachedImageBitmap
import com.raf.capsuleos.presentation.shader.NeuralBorderShader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal const val PILL_SLOT_DP = 52f
private const val MAX_RIGHT_WING_DP = PILL_SLOT_DP * 2f
private const val MIN_RIGHT_WING_DP = 42f
private const val CORNER_ICON_WING_DP = 36f

/**
 * Anchor-aware State 2 horizontal pill geometry locking the physical camera hole inside
 * [cutoutGapDp] across Left, Center, or Right camera placements.
 */
internal data class SidePillGeometry(
    val anchorPosition: CameraAnchorPosition,
    val leftWingDp: Float,
    val cutoutGapDp: Float,
    val rightWingDp: Float,
    val circleDiameterDp: Float,
    val isWideRight: Boolean,
) {
    val totalWidthDp: Float
        get() = leftWingDp + cutoutGapDp + rightWingDp

    val cutoutOffsetXDp: Float
        get() = when (anchorPosition) {
            CameraAnchorPosition.CENTER -> (rightWingDp - leftWingDp) * 0.5f
            CameraAnchorPosition.LEFT -> (totalWidthDp - circleDiameterDp) * 0.5f
            CameraAnchorPosition.RIGHT -> -(totalWidthDp - circleDiameterDp) * 0.5f
        }
}

/** Computes consistent 1-icon pill dimensions with right-wing content-wrapped width capped at [MAX_RIGHT_WING_DP]. */
internal fun computeSidePillGeometry(
    content: CapsuleContent,
    cutout: CutoutGeometry,
    anchorPosition: CameraAnchorPosition = CameraAnchorPosition.CENTER,
    circleDiameterDp: Float = cutout.holeWidthDp + 12f,
    isLandscape: Boolean = false,
    isMinimalIconOnly: Boolean = false,
): SidePillGeometry {
    val effectiveAnchor = if (isLandscape) CameraAnchorPosition.CENTER else anchorPosition
    if (isMinimalIconOnly) {
        return SidePillGeometry(
            anchorPosition = effectiveAnchor,
            leftWingDp = 38f,
            cutoutGapDp = circleDiameterDp,
            rightWingDp = 0f,
            circleDiameterDp = circleDiameterDp,
            isWideRight = false,
        )
    }
    val estimatedRightDp = estimateWrappedRightWingDp(content)
    val cutoutGap = when {
        isLandscape -> 10f
        effectiveAnchor == CameraAnchorPosition.CENTER -> cutout.holeWidthDp + 8f
        else -> circleDiameterDp
    }
    val rawIconWing = if (effectiveAnchor == CameraAnchorPosition.CENTER) PILL_SLOT_DP else CORNER_ICON_WING_DP
    val maxTotal = (cutout.screenWidthDp - 24f).coerceAtLeast(160f)
    val availableWings = (maxTotal - cutoutGap).coerceAtLeast(PILL_SLOT_DP * 1.5f)
    val scale = (availableWings / (rawIconWing + estimatedRightDp)).coerceAtMost(1f)
    val scaledIcon = rawIconWing * scale
    val scaledInfo = estimatedRightDp * scale
    return SidePillGeometry(
        anchorPosition = effectiveAnchor,
        leftWingDp = scaledIcon,
        cutoutGapDp = cutoutGap,
        rightWingDp = scaledInfo,
        circleDiameterDp = circleDiameterDp,
        isWideRight = scaledInfo > PILL_SLOT_DP + 6f,
    )
}

private fun estimateWrappedRightWingDp(content: CapsuleContent): Float {
    val paddingHorizontalDp = 24f
    val charWidthDp = 7.4f
    return when (content) {
        is CapsuleContent.Media -> 46f
        is CapsuleContent.Charging -> "${content.batteryPercent}%".length * charWidthDp + paddingHorizontalDp
        is CapsuleContent.BatteryPeek -> "${content.batteryPercent}%".length * charWidthDp + paddingHorizontalDp
        is CapsuleContent.Torch -> "${content.brightnessPercent}%".length * charWidthDp + paddingHorizontalDp
        is CapsuleContent.Volume -> "100%".length * charWidthDp + paddingHorizontalDp
        is CapsuleContent.NotificationAlert -> {
            val label = content.title.ifBlank { content.appName }
            label.length * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.NotificationSummary -> {
            val approxLabel = if (content.count == 1) "1 notification" else "${content.count} notifications"
            approxLabel.length * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.CallAlert -> {
            content.callerName.length * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.BluetoothDevice -> {
            val text = content.batteryPercent?.let { "$it%" } ?: content.name
            text.length * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.Hotspot -> {
            val text = if (content.isEnabled) "Hotspot" else "Off"
            text.length * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.SystemStatus -> {
            val len = content.detailLabel.ifBlank { content.kind.name }.length.coerceIn(4, 10)
            len * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.LiveActivity -> {
            content.statusText.length * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.Alarm -> {
            val len = if (content.isSet) content.formattedTime.length else 8
            len * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.Unlock -> {
            val len = when (content.unlockMethod) {
                UnlockMethod.FINGERPRINT -> 11
                UnlockMethod.FACE -> 7
                UnlockMethod.GENERIC -> 8
            }
            len * charWidthDp + paddingHorizontalDp
        }
        is CapsuleContent.NowBrief, CapsuleContent.Idle -> MIN_RIGHT_WING_DP
    }.coerceIn(MIN_RIGHT_WING_DP, MAX_RIGHT_WING_DP)
}

/**
 * State 2 single-icon horizontal camera pill supporting directional horizontal swipe switching.
 * Anchors cleanly around Center, Left, or Right camera holes without covering the physical lens.
 */
@Composable
fun State2HorizontalPill(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    cutout: CutoutGeometry,
    anchorPosition: CameraAnchorPosition = CameraAnchorPosition.CENTER,
    circleDiameterDp: Float = cutout.holeWidthDp + 12f,
    isLandscape: Boolean = false,
    isMinimalIconOnly: Boolean = false,
    horizontalMediaGlowStyle: HorizontalMediaGlowStyle = HorizontalMediaGlowStyle.OFF,
    mediaAnimationColorMode: MediaAnimationColorMode = MediaAnimationColorMode.ALBUM_COLOR,
    swipeDirection: Int,
    manualSwipeSeq: Int,
    enableAlbumSharedTransition: Boolean = true,
    showWaterWave: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val targetGeom = computeSidePillGeometry(
        content = content,
        cutout = cutout,
        anchorPosition = anchorPosition,
        circleDiameterDp = circleDiameterDp,
        isLandscape = isLandscape,
        isMinimalIconOnly = isMinimalIconOnly,
    )
    val animatedGapDp by animateFloatAsState(
        targetValue = targetGeom.cutoutGapDp,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f),
        label = "state2CutoutGap",
    )
    val animatedLeftWingDp by animateFloatAsState(
        targetValue = targetGeom.leftWingDp,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f),
        label = "state2LeftWing",
    )
    val animatedRightWingDp by animateFloatAsState(
        targetValue = targetGeom.rightWingDp,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f),
        label = "state2RightWing",
    )
    val geom = targetGeom.copy(
        leftWingDp = animatedLeftWingDp,
        cutoutGapDp = animatedGapDp,
        rightWingDp = animatedRightWingDp,
    )
    val snapshot = State2SwipeSnapshot(
        swipeSeq = manualSwipeSeq,
        content = content,
        geom = geom,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        if (showWaterWave && content is CapsuleContent.Charging) {
            ChargingWaterWaveFill(
                batteryPercent = content.batteryPercent,
                primaryColor = primaryColor,
                geom = geom,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedContent(
            targetState = snapshot,
            contentKey = { it.swipeSeq },
            transitionSpec = {
                val dir = if (swipeDirection < 0) -1 else 1
                (slideInHorizontally(animationSpec = tween(240)) { w -> dir * (w / 3) } +
                    scaleIn(initialScale = 0.90f, animationSpec = tween(240)) +
                    fadeIn(animationSpec = tween(190))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(170)) { w -> -dir * (w / 3) } +
                        scaleOut(targetScale = 0.90f, animationSpec = tween(170)) +
                        fadeOut(animationSpec = tween(140))) using
                    SizeTransform(clip = false)
            },
            label = "state2ManualSwipe",
        ) { active ->
            SymmetricSingleIconPillRow(
                content = active.content,
                geom = active.geom,
                isMinimalIconOnly = isMinimalIconOnly,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor,
                horizontalMediaGlowStyle = horizontalMediaGlowStyle,
                mediaAnimationColorMode = mediaAnimationColorMode,
                enableAlbumSharedTransition = enableAlbumSharedTransition,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

private data class State2SwipeSnapshot(
    val swipeSeq: Int,
    val content: CapsuleContent,
    val geom: SidePillGeometry,
)

@Composable
private fun SymmetricSingleIconPillRow(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    geom: SidePillGeometry,
    isMinimalIconOnly: Boolean = false,
    primaryColor: Color,
    tertiaryColor: Color,
    horizontalMediaGlowStyle: HorizontalMediaGlowStyle,
    mediaAnimationColorMode: MediaAnimationColorMode,
    enableAlbumSharedTransition: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        val isLeftAnchor = geom.anchorPosition == CameraAnchorPosition.LEFT
        val isRightAnchor = geom.anchorPosition == CameraAnchorPosition.RIGHT
        val isNotification = content is CapsuleContent.NotificationAlert

        val iconBox: @Composable () -> Unit = {
            Box(
                contentAlignment = when {
                    isMinimalIconOnly -> Alignment.Center
                    isLeftAnchor -> Alignment.CenterStart
                    isRightAnchor -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
                modifier = Modifier
                    .width(geom.leftWingDp.dp)
                    .fillMaxHeight()
                    .padding(
                        start = when {
                            isMinimalIconOnly -> 6.dp
                            isLeftAnchor -> 2.dp
                            else -> 14.dp
                        },
                        end = when {
                            isMinimalIconOnly -> 2.dp
                            isRightAnchor -> 2.dp
                            else -> 6.dp
                        },
                    ),
            ) {
                AnimatedContent(
                    targetState = content,
                    contentKey = ::contentCarouselKey,
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = spring(0.84f, 380f)) { it / 2 } +
                            fadeIn(animationSpec = tween(210)) +
                            scaleIn(initialScale = 0.78f, animationSpec = spring(0.80f, 380f))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(150)) { it / 2 } +
                                fadeOut(animationSpec = tween(140)) +
                                scaleOut(targetScale = 0.78f, animationSpec = tween(140))) using
                            SizeTransform(clip = false)
                    },
                    label = "leftSingleIcon",
                ) { active ->
                    PrimaryLeftIcon(
                        active = active,
                        primaryColor = primaryColor,
                        tertiaryColor = tertiaryColor,
                        horizontalMediaGlowStyle = horizontalMediaGlowStyle,
                        mediaAnimationColorMode = mediaAnimationColorMode,
                        enableAlbumSharedTransition = enableAlbumSharedTransition,
                        sharedTransitionScope = this@with,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }

        val infoBox: @Composable (Modifier) -> Unit = { boxMod ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = boxMod
                    .fillMaxHeight()
                    .let { base ->
                        if (isNotification) {
                            base
                        } else {
                            base.padding(
                                start = if (isRightAnchor) 10.dp else 4.dp,
                                end = if (isRightAnchor) 4.dp else 10.dp,
                            )
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = content,
                    contentKey = ::contentCarouselKey,
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = spring(0.84f, 380f)) { -it / 2 } +
                            fadeIn(animationSpec = tween(210)) +
                            scaleIn(initialScale = 0.90f, animationSpec = tween(210))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(150)) { -it / 2 } +
                                fadeOut(animationSpec = tween(140)) +
                                scaleOut(targetScale = 0.90f, animationSpec = tween(140)))
                    },
                    label = "rightWingInfo",
                ) { activeRight ->
                    RightWingContent(
                        active = activeRight,
                        primaryColor = primaryColor,
                        mediaAnimationColorMode = mediaAnimationColorMode,
                        sharedTransitionScope = this@with,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }

        val cameraCutoutSlot: @Composable (Float) -> Unit = { slotWidthDp ->
            val playingMedia = (content as? CapsuleContent.Media)?.takeIf {
                it.isPlaying && horizontalMediaGlowStyle == HorizontalMediaGlowStyle.CAMERA_RING
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(slotWidthDp.dp)
                    .fillMaxHeight(),
            ) {
                if (playingMedia != null) {
                    CameraCutoutMediaGlowRing(
                        media = playingMedia,
                        ringDiameterDp = geom.circleDiameterDp.coerceAtMost(slotWidthDp + 4f),
                        primaryColor = primaryColor,
                        tertiaryColor = tertiaryColor,
                        mediaAnimationColorMode = mediaAnimationColorMode,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxSize(),
        ) {
            if (isMinimalIconOnly) {
                when (geom.anchorPosition) {
                    CameraAnchorPosition.LEFT -> {
                        cameraCutoutSlot(geom.cutoutGapDp)
                        iconBox()
                    }
                    CameraAnchorPosition.CENTER,
                    CameraAnchorPosition.RIGHT,
                    -> {
                        iconBox()
                        cameraCutoutSlot(geom.cutoutGapDp)
                    }
                }
            } else {
                when (geom.anchorPosition) {
                    CameraAnchorPosition.LEFT -> {
                        cameraCutoutSlot(geom.cutoutGapDp)
                        iconBox()
                        infoBox(Modifier.weight(1f))
                    }
                    CameraAnchorPosition.CENTER -> {
                        iconBox()
                        val centerGapDp = if (isNotification) {
                            (geom.cutoutGapDp - 6f).coerceAtLeast(0f)
                        } else {
                            geom.cutoutGapDp
                        }
                        cameraCutoutSlot(centerGapDp)
                        infoBox(Modifier.weight(1f))
                    }
                    CameraAnchorPosition.RIGHT -> {
                        infoBox(Modifier.weight(1f))
                        iconBox()
                        cameraCutoutSlot(geom.cutoutGapDp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryLeftIcon(
    modifier: Modifier = Modifier,
    active: CapsuleContent,
    primaryColor: Color,
    tertiaryColor: Color,
    horizontalMediaGlowStyle: HorizontalMediaGlowStyle,
    mediaAnimationColorMode: MediaAnimationColorMode,
    enableAlbumSharedTransition: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        val sharedMod = if (active is CapsuleContent.Media && !enableAlbumSharedTransition) {
            modifier
        } else {
            modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "capsule_icon"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 410f) },
                clipInOverlayDuringTransition = OverlayClip(CircleShape),
            )
        }
        when (active) {
            is CapsuleContent.Media -> {
                val artBitmap = rememberDecodedBytes(active.artworkBytes)
                val showAlbumRing = active.isPlaying && horizontalMediaGlowStyle == HorizontalMediaGlowStyle.ALBUM_ART_RING
                val glowModifier = if (showAlbumRing) {
                    val inf = rememberInfiniteTransition(label = "pillAlbumGlow")
                    val angleState = inf.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 3600, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "pillAlbumAngle",
                    )
                    val beatPhaseState = inf.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 2800, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "pillAlbumBeatPhase",
                    )
                    val artA = if (mediaAnimationColorMode == MediaAnimationColorMode.PRIMARY_COLOR) {
                        primaryColor
                    } else {
                        Color(active.primaryColorArgb)
                    }
                    val artB = if (mediaAnimationColorMode == MediaAnimationColorMode.PRIMARY_COLOR) {
                        tertiaryColor
                    } else {
                        Color(active.secondaryColorArgb)
                    }
                    val rotMatrix = remember { Matrix() }
                    Modifier.drawBehind {
                        val baseRadius = size.minDimension * 0.5f
                        rotMatrix.reset()
                        rotMatrix.setRotate(angleState.value, center.x, center.y)
                        val maxBeatExpandPx = 7.dp.toPx()

                        // Calm breathing-paced outward beat ripple radiating from the album circle border
                        val rawWave = beatPhaseState.value.coerceIn(0f, 1f)
                        val eased = FastOutSlowInEasing.transform(rawWave)
                        val inhale = (rawWave / 0.16f).coerceIn(0f, 1f)
                        val exhale = (1f - eased).let { it * it }
                        val fade = inhale * exhale * 0.72f
                        if (fade > 0.015f) {
                            val beatStrokePx = (1.9f - 1.3f * eased).coerceAtLeast(0.55f).dp.toPx()
                            val beatShader = SweepGradient(
                                center.x,
                                center.y,
                                intArrayOf(
                                    artA.copy(alpha = fade).toArgb(),
                                    artB.copy(alpha = fade).toArgb(),
                                    Color.White.copy(alpha = 0.85f * fade).toArgb(),
                                    artA.copy(alpha = fade).toArgb(),
                                ),
                                floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                            ).apply {
                                setLocalMatrix(rotMatrix)
                            }
                            drawCircle(
                                brush = ShaderBrush(beatShader),
                                radius = baseRadius + eased * maxBeatExpandPx,
                                style = Stroke(width = beatStrokePx),
                            )
                        }

                        val borderShader = SweepGradient(
                            center.x,
                            center.y,
                            intArrayOf(
                                artA.toArgb(),
                                artB.toArgb(),
                                Color.White.copy(alpha = 0.85f).toArgb(),
                                artA.toArgb(),
                            ),
                            floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                        ).apply {
                            setLocalMatrix(rotMatrix)
                        }
                        drawCircle(
                            brush = ShaderBrush(borderShader),
                            radius = baseRadius,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = glowModifier,
                ) {
                    CircleImageOrIcon(
                        bitmap = artBitmap,
                        fallbackIcon = Icons.Rounded.MusicNote,
                        desc = stringResource(R.string.desc_album_art),
                        tint = primaryColor,
                        bgTint = null,
                        sizeDp = 22,
                        modifier = sharedMod,
                    )
                }
            }
            is CapsuleContent.NotificationAlert -> {
                val appBitmap = rememberDecodedBytes(active.iconBytes)
                CircleImageOrIcon(
                    bitmap = appBitmap,
                    fallbackIcon = Icons.Rounded.Notifications,
                    desc = active.appName,
                    tint = primaryColor,
                    bgTint = primaryColor.copy(alpha = 0.2f),
                    sizeDp = 22,
                    modifier = sharedMod,
                )
            }
            is CapsuleContent.NotificationSummary -> NotificationSummaryBellIcon(
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
            is CapsuleContent.Charging -> {
                val chargeTint = capsuleBatteryAccentColor(
                    batteryPercent = active.batteryPercent,
                    isCharging = true,
                    primaryColor = primaryColor,
                )
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = stringResource(R.string.label_charging),
                    tint = chargeTint,
                    modifier = sharedMod.size(18.dp),
                )
            }
            is CapsuleContent.BatteryPeek -> Icon(
                imageVector = batteryIconForLevel(active.batteryPercent),
                contentDescription = stringResource(R.string.desc_battery_icon),
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
            is CapsuleContent.Volume -> {
                val activeInfo = active.streams.firstOrNull { it.streamType == active.activeStream }
                    ?: active.streams.firstOrNull()
                val isMuted = activeInfo == null || activeInfo.current <= 0
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(R.string.desc_volume_icon),
                    tint = primaryColor,
                    modifier = sharedMod.size(18.dp),
                )
            }
            is CapsuleContent.LiveActivity -> {
                val appBitmap = rememberDecodedBytes(active.iconBytes)
                CircleImageOrIcon(
                    bitmap = appBitmap,
                    fallbackIcon = Icons.Rounded.Timer,
                    desc = active.title,
                    tint = primaryColor,
                    bgTint = primaryColor.copy(alpha = 0.2f),
                    sizeDp = 22,
                    modifier = sharedMod,
                )
            }
            is CapsuleContent.BluetoothDevice -> Icon(
                imageVector = bluetoothIconForCategory(active.category),
                contentDescription = active.name,
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
            is CapsuleContent.Hotspot -> Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = stringResource(R.string.desc_hotspot_icon),
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
            is CapsuleContent.SystemStatus -> Icon(
                imageVector = systemStatusSatelliteIcon(active),
                contentDescription = active.kind.name,
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
            is CapsuleContent.CallAlert -> {
                val appBitmap = rememberDecodedBytes(active.appIconBytes)
                CircleImageOrIcon(
                    bitmap = appBitmap,
                    fallbackIcon = Icons.Rounded.Call,
                    desc = active.callerName,
                    tint = Color(0xFF2E9E5B),
                    bgTint = Color(0xFF2E9E5B).copy(alpha = 0.22f),
                    sizeDp = 22,
                    modifier = sharedMod,
                )
            }
            is CapsuleContent.Torch -> Icon(
                imageVector = Icons.Rounded.FlashlightOn,
                contentDescription = stringResource(R.string.desc_torch_icon),
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
            is CapsuleContent.Alarm -> Icon(
                imageVector = Icons.Rounded.Alarm,
                contentDescription = stringResource(R.string.desc_alarm_icon),
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
            is CapsuleContent.Unlock -> {
                val visuals = resolveUnlockVisuals(active.unlockMethod)
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = stringResource(R.string.desc_unlock_icon),
                    tint = primaryColor,
                    modifier = sharedMod.size(18.dp),
                )
            }
            is CapsuleContent.NowBrief, CapsuleContent.Idle -> Spacer(modifier = modifier.size(18.dp))
        }
    }
}

@Composable
private fun RightWingContent(
    modifier: Modifier = Modifier,
    active: CapsuleContent,
    primaryColor: Color,
    mediaAnimationColorMode: MediaAnimationColorMode,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        when (active) {
            is CapsuleContent.Media -> {
                val eqTint = if (mediaAnimationColorMode == MediaAnimationColorMode.PRIMARY_COLOR) {
                    primaryColor
                } else {
                    Color(active.primaryColorArgb)
                }
                MiniEqualizerBars(
                    isPlaying = active.isPlaying,
                    tint = eqTint,
                    modifier = modifier,
                )
            }
            is CapsuleContent.NotificationAlert -> {
                val marqueeText = if (active.title.isNotBlank()) active.title else active.appName
                MarqueeValueText(
                    text = marqueeText,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    useMarquee = true,
                    modifier = modifier
                        .fillMaxWidth()
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "capsule_title"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
                        ),
                )
            }
            is CapsuleContent.Unlock -> {
                val visuals = resolveUnlockVisuals(active.unlockMethod)
                MarqueeValueText(
                    text = visuals.label,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    useMarquee = true,
                    modifier = modifier,
                )
            }
            is CapsuleContent.Charging -> MarqueeValueText(
                text = "${active.batteryPercent}%",
                color = capsuleBatteryAccentColor(active.batteryPercent, isCharging = true, primaryColor = primaryColor),
                fontWeight = FontWeight.Bold,
                useMarquee = false,
                modifier = modifier,
            )
            is CapsuleContent.BatteryPeek -> MarqueeValueText(
                text = "${active.batteryPercent}%",
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                useMarquee = false,
                modifier = modifier,
            )
            is CapsuleContent.Volume -> {
                val activeInfo = active.streams.firstOrNull { it.streamType == active.activeStream }
                    ?: active.streams.firstOrNull()
                val percent = if (activeInfo != null && activeInfo.max > 0) {
                    (activeInfo.current * 100) / activeInfo.max
                } else {
                    0
                }
                MarqueeValueText(
                    text = "$percent%",
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    useMarquee = false,
                    modifier = modifier,
                )
            }
            is CapsuleContent.LiveActivity -> {
                val liveStatus = rememberLiveActivityStatusText(active)
                MarqueeValueText(
                    text = liveStatus,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    useMarquee = liveStatus.length > 7,
                    modifier = modifier,
                )
            }
            is CapsuleContent.Torch -> MarqueeValueText(
                text = "${active.brightnessPercent}%",
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                useMarquee = false,
                modifier = modifier,
            )
            is CapsuleContent.Alarm -> MarqueeValueText(
                text = if (active.isSet) active.formattedTime else stringResource(R.string.label_no_alarm),
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                useMarquee = false,
                modifier = modifier,
            )
            is CapsuleContent.BluetoothDevice -> MarqueeValueText(
                text = active.batteryPercent?.let { "$it%" } ?: active.name,
                color = active.batteryPercent?.let {
                    capsuleBatteryAccentColor(it, isCharging = it >= 60, primaryColor = primaryColor)
                } ?: primaryColor,
                fontWeight = FontWeight.Bold,
                useMarquee = active.batteryPercent == null && active.name.length > 6,
                modifier = modifier,
            )
            is CapsuleContent.Hotspot -> MarqueeValueText(
                text = stringResource(if (active.isEnabled) R.string.label_hotspot_active else R.string.label_hotspot_inactive),
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                useMarquee = true,
                modifier = modifier,
            )
            is CapsuleContent.SystemStatus -> MarqueeValueText(
                text = active.detailLabel.ifBlank { stringResource(R.string.label_status_active) },
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                useMarquee = true,
                modifier = modifier,
            )
            is CapsuleContent.CallAlert -> MarqueeValueText(
                text = active.callerName,
                color = Color(0xFF69F0AE),
                fontWeight = FontWeight.Bold,
                useMarquee = true,
                modifier = modifier,
            )
            is CapsuleContent.NotificationSummary -> MarqueeValueText(
                text = pluralStringResource(
                    R.plurals.label_notification_count_summary,
                    active.count,
                    active.count,
                ),
                color = primaryColor,
                fontWeight = FontWeight.SemiBold,
                useMarquee = true,
                modifier = modifier,
            )
            is CapsuleContent.NowBrief, CapsuleContent.Idle -> Spacer(modifier = modifier.width(1.dp))
        }
    }
}

@Composable
private fun CircleImageOrIcon(
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap?,
    fallbackIcon: ImageVector,
    desc: String,
    tint: Color,
    bgTint: Color?,
    sizeDp: Int,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .let { base -> if (bgTint != null) base.background(bgTint) else base },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = desc,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = desc,
                tint = tint,
                modifier = Modifier.size((sizeDp - 6).coerceAtLeast(12).dp),
            )
        }
    }
}

@Composable
private fun MarqueeValueText(
    modifier: Modifier = Modifier,
    text: String,
    color: Color,
    fontWeight: FontWeight,
    useMarquee: Boolean,
) {
    val applied = if (useMarquee) {
        modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = 350,
            repeatDelayMillis = 900,
            velocity = 34.dp,
        )
    } else {
        modifier
    }
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = applied,
    )
}

@Composable
internal fun ChargingWaterWaveFill(
    modifier: Modifier = Modifier,
    batteryPercent: Int,
    primaryColor: Color,
    geom: SidePillGeometry,
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
        label = "chargingWaterFill",
    )
    val inf = rememberInfiniteTransition(label = "chargingWave")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chargingWavePhase",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        // Vertical water fill rising smoothly from the bottom edge up to [surfaceY]
        val surfaceY = h * (1f - fillFraction)
        val ampScale = if (fillFraction >= 0.97f) 0.25f else 1f
        val amp1 = (h * 0.085f).coerceAtLeast(1.8f) * ampScale
        val amp2 = (h * 0.055f).coerceAtLeast(1.2f) * ampScale
        val steps = 36

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
        drawPath(
            path = backWavePath,
            color = chargeColor.copy(alpha = 0.22f),
        )

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

        // Neuron dot effect bursting out from the camera circle hole perimeter and spreading all around
        // inside the water volume using FastOutSlowInEasing (rapid burst from camera rim -> smooth spread)
        clipPath(frontWavePath) {
            val totalW = geom.totalWidthDp.coerceAtLeast(1f)
            val camCenterFracX = when (geom.anchorPosition) {
                CameraAnchorPosition.LEFT -> (geom.cutoutGapDp * 0.5f) / totalW
                CameraAnchorPosition.CENTER -> (geom.leftWingDp + geom.cutoutGapDp * 0.5f) / totalW
                CameraAnchorPosition.RIGHT -> (totalW - geom.cutoutGapDp * 0.5f) / totalW
            }
            val camCenterX = w * camCenterFracX
            val camCenterY = h * 0.5f
            val camHoleRadiusPx = ((geom.cutoutGapDp - 8f).coerceAtLeast(18f) * 0.46f).dp.toPx()
            val maxHorizontalSpanPx = max(camCenterX, w - camCenterX).coerceAtLeast(camHoleRadiusPx + 12f)
            val maxTravelXPx = (maxHorizontalSpanPx - camHoleRadiusPx * 0.85f).coerceAtLeast(16f)
            val maxTravelYPx = (h * 0.56f).coerceAtLeast(8f)

            val phase01 = (phase / (2.0 * PI).toFloat()).coerceIn(0f, 1f)
            val neuronDotCount = 28
            for (idx in 0 until neuronDotCount) {
                val fIdx = idx.toFloat()
                // Staggered radial burst cycles emerging from the camera circle rim
                val rawProgress = ((phase01 + fIdx * 0.137f) % 1f).coerceIn(0f, 1f)
                val easedBurst = FastOutSlowInEasing.transform(rawProgress)

                // Golden-angle distribution around the camera hole so dots spread all around inside the water
                val baseAngle = fIdx * 2.399963f + (idx % 3) * 0.21f
                val neuralCurl = sin(easedBurst * PI.toFloat() + fIdx * 0.9f) * 0.14f
                val angle = baseAngle + neuralCurl
                val dirX = cos(angle)
                val dirY = sin(angle)

                val reachScale = 0.56f + 0.44f * (((idx * 7 + 3) % 11) / 10f)
                val px = camCenterX + dirX * (camHoleRadiusPx * 0.88f + easedBurst * maxTravelXPx * reachScale)
                val py = camCenterY + dirY * (camHoleRadiusPx * 0.72f + easedBurst * maxTravelYPx * reachScale)

                // Neural stipple twinkle & fast-burst / soft-settle envelope
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
private fun CameraCutoutMediaGlowRing(
    modifier: Modifier = Modifier,
    media: CapsuleContent.Media,
    ringDiameterDp: Float,
    primaryColor: Color,
    tertiaryColor: Color,
    mediaAnimationColorMode: MediaAnimationColorMode,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { 2.4.dp.toPx() }
    val shader = remember { NeuralBorderShader.create() }
    val inf = rememberInfiniteTransition(label = "cameraRingMediaShaderClock")
    val timeSec by inf.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cameraRingMediaTime",
    )
    val artA = if (mediaAnimationColorMode == MediaAnimationColorMode.PRIMARY_COLOR) {
        primaryColor
    } else {
        Color(media.primaryColorArgb)
    }
    val artB = if (mediaAnimationColorMode == MediaAnimationColorMode.PRIMARY_COLOR) {
        tertiaryColor
    } else {
        Color(media.secondaryColorArgb)
    }

    Box(
        modifier = modifier
            .size(ringDiameterDp.dp)
            .drawBehind {
                val cornerPx = size.minDimension * 0.5f
                NeuralBorderShader.updateUniforms(
                    shader = shader,
                    widthPx = size.width,
                    heightPx = size.height,
                    timeSeconds = timeSec,
                    cornerRadiusPx = cornerPx,
                    strokeWidthPx = strokePx,
                    intensity = 0.96f,
                    dotIntensity = 0.72f,
                    neuralMode = 1.0f,
                    ringMix = 0f,
                    ringProgress = 1f,
                    ringGlow = 0f,
                    colorA = artA,
                    colorB = artB,
                )
                drawRect(brush = ShaderBrush(shader))
            },
    )
}

@Composable
private fun rememberDecodedBytes(bytes: ByteArray?): ImageBitmap? = rememberCachedImageBitmap(bytes)

/** Renders the prioritized secondary icon inside the detached left satellite bubble. */
@Composable
fun LeftSatelliteBubbleIcon(
    modifier: Modifier = Modifier,
    satellite: CapsuleContent.LeftSatelliteItem,
    primaryColor: Color,
    swipeSeq: Int = 0,
    swipeDirection: Int = 1,
) {
    AnimatedContent(
        targetState = satellite to swipeSeq,
        contentKey = { (item, seq) -> "$seq:${item.source}:${(item.content as? CapsuleContent.LiveActivity)?.key ?: ""}" },
        transitionSpec = {
            val dir = if (swipeDirection < 0) -1 else 1
            (slideInHorizontally(animationSpec = tween(220)) { w -> dir * (w / 2) } +
                fadeIn(animationSpec = tween(210)) +
                scaleIn(initialScale = 0.68f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f))) togetherWith
                (slideOutHorizontally(animationSpec = tween(160)) { w -> -dir * (w / 2) } +
                    fadeOut(animationSpec = tween(140)) +
                    scaleOut(targetScale = 0.68f, animationSpec = tween(140)))
        },
        label = "leftSatelliteIcon",
        modifier = modifier,
    ) { (active, _) ->
        when (active.source) {
            SatelliteSource.TORCH -> Icon(
                imageVector = Icons.Rounded.FlashlightOn,
                contentDescription = stringResource(R.string.desc_torch_icon),
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            SatelliteSource.HOTSPOT -> Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = stringResource(R.string.desc_hotspot_icon),
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            SatelliteSource.LIVE_ACTIVITY -> {
                val live = active.content as? CapsuleContent.LiveActivity
                val iconBitmap = rememberDecodedBytes(live?.iconBytes)
                CircleImageOrIcon(
                    bitmap = iconBitmap,
                    fallbackIcon = Icons.Rounded.Timer,
                    desc = live?.title ?: stringResource(R.string.desc_battery_icon),
                    tint = primaryColor,
                    bgTint = primaryColor.copy(alpha = 0.2f),
                    sizeDp = 22,
                )
            }
            SatelliteSource.AIRPLANE_MODE -> Icon(
                imageVector = Icons.Rounded.AirplanemodeActive,
                contentDescription = stringResource(R.string.label_status_airplane),
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            SatelliteSource.DO_NOT_DISTURB -> Icon(
                imageVector = Icons.Rounded.DoNotDisturbOn,
                contentDescription = stringResource(R.string.label_status_dnd),
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            SatelliteSource.BATTERY_SAVER -> Icon(
                imageVector = Icons.Rounded.BatterySaver,
                contentDescription = stringResource(R.string.label_status_battery_saver),
                tint = Color(0xFFFFCA28),
                modifier = Modifier.size(18.dp),
            )
            SatelliteSource.VPN -> Icon(
                imageVector = Icons.Rounded.VpnKey,
                contentDescription = stringResource(R.string.label_status_vpn),
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            SatelliteSource.RINGER_MUTED -> {
                val detail = (active.content as? CapsuleContent.SystemStatus)?.detailLabel
                val icon = if (detail.equals("Vibrate", ignoreCase = true)) {
                    Icons.Rounded.Vibration
                } else {
                    Icons.Rounded.NotificationsOff
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.label_status_ringer_muted),
                    tint = primaryColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            SatelliteSource.DATA_SAVER -> Icon(
                imageVector = Icons.Rounded.DataSaverOn,
                contentDescription = stringResource(R.string.label_status_data_saver),
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun systemStatusSatelliteIcon(status: CapsuleContent.SystemStatus): ImageVector = when (status.kind) {
    SystemStatusKind.AIRPLANE_MODE -> Icons.Rounded.AirplanemodeActive
    SystemStatusKind.DO_NOT_DISTURB -> Icons.Rounded.DoNotDisturbOn
    SystemStatusKind.BATTERY_SAVER -> Icons.Rounded.BatterySaver
    SystemStatusKind.VPN -> Icons.Rounded.VpnKey
    SystemStatusKind.RINGER_MUTED -> if (status.detailLabel.equals("Vibrate", ignoreCase = true)) {
        Icons.Rounded.Vibration
    } else {
        Icons.Rounded.NotificationsOff
    }
    SystemStatusKind.DATA_SAVER -> Icons.Rounded.DataSaverOn
}

private fun contentCarouselKey(content: CapsuleContent): String = when (content) {
    is CapsuleContent.Media -> "media"
    is CapsuleContent.Charging -> "charge"
    is CapsuleContent.BatteryPeek -> "battery"
    is CapsuleContent.Volume -> "volume"
    is CapsuleContent.BluetoothDevice -> "bt:${content.address}"
    is CapsuleContent.Hotspot -> "hotspot"
    is CapsuleContent.SystemStatus -> "sys:${content.kind.name}"
    is CapsuleContent.CallAlert -> "call:${content.key}"
    is CapsuleContent.Torch -> "torch"
    is CapsuleContent.Alarm -> "alarm"
    is CapsuleContent.Unlock -> "unlock"
    is CapsuleContent.NotificationAlert -> "notif:${content.key}"
    is CapsuleContent.NotificationSummary -> "notif_summary"
    is CapsuleContent.LiveActivity -> "live:${content.key}"
    is CapsuleContent.NowBrief -> "brief"
    CapsuleContent.Idle -> "idle"
}

@Composable
private fun NotificationSummaryBellIcon(
    modifier: Modifier = Modifier,
    tint: Color,
) {
    val rotationDeg = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        val jiggleSteps = listOf(
            24f to 90,
            -22f to 120,
            18f to 125,
            -14f to 130,
            10f to 135,
            -6f to 140,
            3f to 130,
            0f to 130,
        )
        jiggleSteps.forEach { (angle, durationMs) ->
            rotationDeg.animateTo(
                targetValue = angle,
                animationSpec = tween(durationMillis = durationMs),
            )
        }
    }
    LaunchedEffect(Unit) {
        scale.animateTo(1.14f, tween(durationMillis = 130))
        scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
    }

    Icon(
        imageVector = Icons.Rounded.Notifications,
        contentDescription = stringResource(R.string.desc_notification_count),
        tint = tint,
        modifier = modifier.graphicsLayer {
            rotationZ = rotationDeg.value
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = TransformOrigin(0.5f, 0.14f)
        },
    )
}

private fun batteryIconForLevel(percent: Int): ImageVector = when {
    percent >= 85 -> Icons.Rounded.BatteryFull
    percent >= 60 -> Icons.Rounded.Battery6Bar
    percent >= 35 -> Icons.Rounded.Battery4Bar
    else -> Icons.Rounded.Battery2Bar
}

@Composable
private fun MiniEqualizerBars(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    tint: Color,
) {
    val transition = rememberInfiniteTransition(label = "miniEq")
    val bar1 by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(540, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq3",
    )

    val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.35f, 0.35f, 0.35f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = modifier,
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14f * fraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint),
            )
        }
    }
}
