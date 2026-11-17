package com.raf.capsuleos.presentation.capsule.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.presentation.R

internal const val PILL_SLOT_DP = 52f

/**
 * Symmetric slot-based State 2 horizontal pill geometry locking the physical camera hole inside
 * [cutoutGapDp]. Left holds one clean icon slot ([PILL_SLOT_DP]); Right scales in discrete
 * multiples (`1x` for compact readouts/EQ, `2x` for long marquee text), keeping 1-vs-1 states
 * 100% symmetric around the camera cutout.
 */
internal data class SidePillGeometry(
    val leftWingDp: Float,
    val cutoutGapDp: Float,
    val rightWingDp: Float,
    val isWideRight: Boolean,
) {
    val totalWidthDp: Float get() = leftWingDp + cutoutGapDp + rightWingDp
    val cutoutOffsetXDp: Float get() = (rightWingDp - leftWingDp) * 0.5f
}

/** Computes consistent 1-icon pill wing dimensions across Controller and Compose UI. */
internal fun computeSidePillGeometry(
    content: CapsuleContent,
    cutout: CutoutGeometry,
): SidePillGeometry {
    val cutoutGap = cutout.holeWidthDp + 8f
    val rightSlots = when (content) {
        is CapsuleContent.NotificationAlert -> 2
        is CapsuleContent.Unlock -> 2
        is CapsuleContent.LiveActivity -> if (content.statusText.length > 5) 2 else 1
        is CapsuleContent.Alarm -> if (!content.isSet) 2 else 1
        else -> 1
    }
    val rawLeft = PILL_SLOT_DP
    val rawRight = rightSlots * PILL_SLOT_DP
    val maxTotal = (cutout.screenWidthDp - 24f).coerceAtLeast(160f)
    val availableWings = (maxTotal - cutoutGap).coerceAtLeast(PILL_SLOT_DP * 2f)
    val scale = (availableWings / (rawLeft + rawRight)).coerceAtMost(1f)
    return SidePillGeometry(
        leftWingDp = rawLeft * scale,
        cutoutGapDp = cutoutGap,
        rightWingDp = rawRight * scale,
        isWideRight = rightSlots > 1,
    )
}

/**
 * State 2 single-icon horizontal camera pill supporting directional horizontal swipe switching.
 * Padding around the central physical cutout stays symmetric between Left icon & Right wing.
 */
@Composable
fun State2HorizontalPill(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    cutout: CutoutGeometry,
    swipeDirection: Int,
    manualSwipeSeq: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val geom = computeSidePillGeometry(content, cutout)
    val snapshot = State2SwipeSnapshot(
        swipeSeq = manualSwipeSeq,
        content = content,
        geom = geom,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
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
                primaryColor = primaryColor,
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
    primaryColor: Color,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxSize(),
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .width(geom.leftWingDp.dp)
                    .fillMaxHeight()
                    .padding(start = 14.dp, end = 6.dp),
            ) {
                AnimatedContent(
                    targetState = content,
                    contentKey = ::contentCarouselKey,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(210)) +
                            scaleIn(initialScale = 0.76f, animationSpec = spring(0.80f, 380f))) togetherWith
                            (fadeOut(animationSpec = tween(140)) +
                                scaleOut(targetScale = 0.76f, animationSpec = tween(140)))
                    },
                    label = "leftSingleIcon",
                ) { active ->
                    PrimaryLeftIcon(
                        active = active,
                        primaryColor = primaryColor,
                        sharedTransitionScope = this@with,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }

            Spacer(modifier = Modifier.width(geom.cutoutGapDp.dp))

            Box(
                contentAlignment = if (geom.isWideRight) Alignment.CenterStart else Alignment.CenterEnd,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 6.dp, end = 14.dp),
            ) {
                AnimatedContent(
                    targetState = content,
                    contentKey = ::contentCarouselKey,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(210)) +
                            scaleIn(initialScale = 0.90f, animationSpec = tween(210))) togetherWith
                            (fadeOut(animationSpec = tween(140)) +
                                scaleOut(targetScale = 0.90f, animationSpec = tween(140)))
                    },
                    label = "rightWingInfo",
                ) { activeRight ->
                    RightWingContent(
                        active = activeRight,
                        primaryColor = primaryColor,
                        sharedTransitionScope = this@with,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        val sharedMod = modifier.sharedElement(
            sharedContentState = rememberSharedContentState(key = "capsule_icon"),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
        )
        when (active) {
            is CapsuleContent.Media -> {
                val artBitmap = rememberDecodedBytes(active.artworkBytes)
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
            is CapsuleContent.LiveActivity -> Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = active.title,
                tint = primaryColor,
                modifier = sharedMod.size(18.dp),
            )
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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        when (active) {
            is CapsuleContent.Media -> MiniEqualizerBars(
                isPlaying = active.isPlaying,
                tint = primaryColor,
                modifier = modifier,
            )
            is CapsuleContent.NotificationAlert -> {
                val marqueeText = if (active.title.isNotBlank()) active.title else active.appName
                MarqueeValueText(
                    text = marqueeText,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    useMarquee = true,
                    modifier = modifier.sharedElement(
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
            is CapsuleContent.LiveActivity -> MarqueeValueText(
                text = active.statusText,
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                useMarquee = active.statusText.length > 7,
                modifier = modifier,
            )
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
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = applied,
    )
}

@Composable
private fun rememberDecodedBytes(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.let { raw ->
        BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
    }
}

private fun contentCarouselKey(content: CapsuleContent): String = when (content) {
    is CapsuleContent.Media -> "media"
    is CapsuleContent.Charging -> "charge"
    is CapsuleContent.BatteryPeek -> "battery"
    is CapsuleContent.Volume -> "volume"
    is CapsuleContent.Torch -> "torch"
    is CapsuleContent.Alarm -> "alarm"
    is CapsuleContent.Unlock -> "unlock"
    is CapsuleContent.NotificationAlert -> "notif:${content.key}"
    is CapsuleContent.LiveActivity -> "live:${content.key}"
    is CapsuleContent.NowBrief -> "brief"
    CapsuleContent.Idle -> "idle"
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
