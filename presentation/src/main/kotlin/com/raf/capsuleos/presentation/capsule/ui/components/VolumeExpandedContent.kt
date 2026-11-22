package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.StreamVolumeInfo
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.CapsuleSlider
import kotlin.math.roundToInt

/**
 * State 3 expanded multi-stream volume mixer featuring frosted M3 Expressive stream tiles with
 * tap-to-mute badges, live percentage readouts, smooth sliders, and a full-width segmented
 * Ring / Vibrate / Silent control bar adapted cleanly for both portrait and landscape layouts.
 *
 * @param modifier Applied to the mixer container.
 * @param volume Current snapshot of audio stream levels and active ringer mode.
 * @param topClearanceDp Camera-cutout top clearance in dp.
 * @param isLandscape True when rendered in landscape full-height card mode.
 * @param contentPaddingDp Dynamic outer content padding in dp.
 * @param interactionsEnabled True when interactive touches are enabled.
 * @param onUserInteraction Resets inactivity auto-collapse timers.
 * @param onSetStreamVolume Updates the system volume index for a stream.
 * @param onSetRingerMode Applies an explicit [RingerModeState] selection.
 */
@Composable
fun VolumeExpandedContent(
    modifier: Modifier = Modifier,
    volume: CapsuleContent.Volume,
    topClearanceDp: Float,
    isLandscape: Boolean = false,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    onUserInteraction: () -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val displayedStreams = if (volume.activeStream == AudioStreamType.VOICE ||
        volume.activeStream == AudioStreamType.NOTIFICATION
    ) {
        volume.streams.filter {
            it.streamType == volume.activeStream ||
                it.streamType == AudioStreamType.MEDIA ||
                it.streamType == AudioStreamType.RING
        }
    } else {
        volume.streams.filter {
            it.streamType == AudioStreamType.MEDIA ||
                it.streamType == AudioStreamType.RING ||
                it.streamType == AudioStreamType.ALARM
        }
    }

    Column(
        verticalArrangement = if (isLandscape) {
            Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        } else {
            Arrangement.SpaceBetween
        },
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = contentPaddingDp.dp,
                end = contentPaddingDp.dp,
                top = topClearanceDp.dp,
                bottom = (contentPaddingDp * 0.72f).coerceAtLeast(10f).dp,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            displayedStreams.forEach { stream ->
                VolumeStreamCard(
                    stream = stream,
                    isActiveStream = stream.streamType == volume.activeStream,
                    isLandscape = isLandscape,
                    accent = accent,
                    interactionsEnabled = interactionsEnabled,
                    onUserInteraction = onUserInteraction,
                    onSetStreamVolume = onSetStreamVolume,
                )
            }
        }

        if (!isLandscape) {
            Spacer(modifier = Modifier.height(6.dp))
        }

        RingerSegmentedBar(
            selectedMode = volume.ringerMode,
            accent = accent,
            isLandscape = isLandscape,
            interactionsEnabled = interactionsEnabled,
            onSelectMode = { mode ->
                onUserInteraction()
                onSetRingerMode(mode)
            },
        )
    }
}

@Composable
private fun VolumeStreamCard(
    modifier: Modifier = Modifier,
    stream: StreamVolumeInfo,
    isActiveStream: Boolean,
    isLandscape: Boolean,
    accent: Color,
    interactionsEnabled: Boolean,
    onUserInteraction: () -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val max = stream.max.coerceAtLeast(1)
    val percent = ((stream.current.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    val isMuted = stream.current <= 0

    val baseIcon = when (stream.streamType) {
        AudioStreamType.MEDIA -> Icons.Rounded.MusicNote
        AudioStreamType.RING -> Icons.Rounded.Notifications
        AudioStreamType.ALARM -> Icons.Rounded.Alarm
        AudioStreamType.NOTIFICATION -> Icons.Rounded.Notifications
        AudioStreamType.VOICE -> Icons.AutoMirrored.Rounded.VolumeUp
    }
    val label = when (stream.streamType) {
        AudioStreamType.MEDIA -> stringResource(R.string.label_stream_media)
        AudioStreamType.RING -> stringResource(R.string.label_stream_ring)
        AudioStreamType.ALARM -> stringResource(R.string.label_stream_alarm)
        AudioStreamType.NOTIFICATION -> stringResource(R.string.label_stream_notif)
        AudioStreamType.VOICE -> stringResource(R.string.label_stream_voice)
    }

    val containerColor by animateColorAsState(
        targetValue = if (isActiveStream) {
            Color.White.copy(alpha = 0.13f)
        } else {
            Color.White.copy(alpha = 0.07f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "volumeStreamBg_${stream.streamType.name}",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActiveStream) {
            accent.copy(alpha = 0.48f)
        } else {
            Color.White.copy(alpha = 0.10f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "volumeStreamBorder_${stream.streamType.name}",
    )
    val iconBadgeBg by animateColorAsState(
        targetValue = when {
            isMuted -> Color.White.copy(alpha = 0.08f)
            isActiveStream -> accent.copy(alpha = 0.24f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "volumeIconBg_${stream.streamType.name}",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            isMuted -> Color.White.copy(alpha = 0.45f)
            isActiveStream -> accent
            else -> Color.White.copy(alpha = 0.88f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "volumeIconTint_${stream.streamType.name}",
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = if (isLandscape) 9.dp else 6.dp,
                ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isLandscape) 34.dp else 30.dp)
                    .clip(CircleShape)
                    .background(iconBadgeBg)
                    .clickable(enabled = interactionsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onUserInteraction()
                        val toggledTarget = if (isMuted) {
                            (max * 0.5f).roundToInt().coerceAtLeast(1)
                        } else {
                            0
                        }
                        onSetStreamVolume(stream.streamType, toggledTarget)
                    },
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else baseIcon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(if (isLandscape) 18.dp else 16.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = label,
                        color = if (isActiveStream) Color.White else Color.White.copy(alpha = 0.86f),
                        fontSize = if (isLandscape) 12.sp else 11.sp,
                        fontWeight = if (isActiveStream) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$percent%",
                        color = if (isActiveStream) accent else Color.White.copy(alpha = 0.65f),
                        fontSize = if (isLandscape) 12.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }

                CapsuleSlider(
                    value = stream.current.toFloat(),
                    onValueChange = { next ->
                        val target = next.roundToInt().coerceIn(0, max)
                        if (target != stream.current) {
                            onSetStreamVolume(stream.streamType, target)
                        }
                    },
                    valueRange = 0f..max.toFloat(),
                    enabled = interactionsEnabled,
                    onInteraction = onUserInteraction,
                    colors = SliderDefaults.colors(
                        thumbColor = if (isActiveStream) accent else Color.White,
                        activeTrackColor = if (isActiveStream) accent else accent.copy(alpha = 0.78f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 22.dp else 18.dp),
                )
            }
        }
    }
}

@Composable
private fun RingerSegmentedBar(
    modifier: Modifier = Modifier,
    selectedMode: RingerModeState,
    accent: Color,
    isLandscape: Boolean,
    interactionsEnabled: Boolean,
    onSelectMode: (RingerModeState) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            RingerModeOptionButton(
                mode = RingerModeState.NORMAL,
                selected = selectedMode == RingerModeState.NORMAL,
                icon = Icons.Rounded.NotificationsActive,
                label = stringResource(R.string.label_ringer_normal),
                accent = accent,
                isLandscape = isLandscape,
                enabled = interactionsEnabled,
                onClick = { onSelectMode(RingerModeState.NORMAL) },
                modifier = Modifier.weight(1f),
            )
            RingerModeOptionButton(
                mode = RingerModeState.VIBRATE,
                selected = selectedMode == RingerModeState.VIBRATE,
                icon = Icons.Rounded.Vibration,
                label = stringResource(R.string.label_ringer_vibrate),
                accent = accent,
                isLandscape = isLandscape,
                enabled = interactionsEnabled,
                onClick = { onSelectMode(RingerModeState.VIBRATE) },
                modifier = Modifier.weight(1f),
            )
            RingerModeOptionButton(
                mode = RingerModeState.SILENT,
                selected = selectedMode == RingerModeState.SILENT,
                icon = Icons.Rounded.NotificationsOff,
                label = stringResource(R.string.label_ringer_silent),
                accent = accent,
                isLandscape = isLandscape,
                enabled = interactionsEnabled,
                onClick = { onSelectMode(RingerModeState.SILENT) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RingerModeOptionButton(
    modifier: Modifier = Modifier,
    mode: RingerModeState,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    accent: Color,
    isLandscape: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.28f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "ringerOptionBg_${mode.name}",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 200),
        label = "ringerOptionContent_${mode.name}",
    )

    CapsuleExpressiveSurface(
        enabled = enabled,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onClick()
        },
        restCornerDp = 16f,
        pressedCornerDp = 8f,
        pressedScale = 0.95f,
        backgroundColor = bgColor,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.68f)) else null,
        modifier = modifier.height(if (isLandscape) 38.dp else 34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accent else contentColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
