package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.CapsuleSlider
import kotlin.math.roundToInt

/**
 * State 3 expanded multi-stream volume mixer for Media, Ring, and Alarm streams plus centered
 * Ring / Vibrate / Silent mode switcher buttons at the bottom.
 *
 * @param modifier Applied to the mixer container.
 * @param volume Current snapshot of audio stream levels and active ringer mode.
 * @param topClearanceDp Camera-cutout top clearance in dp.
 * @param onUserInteraction Resets inactivity auto-collapse timers.
 * @param onSetStreamVolume Updates the system volume index for a stream.
 * @param onSetRingerMode Applies an explicit [RingerModeState] selection.
 */
@Composable
fun VolumeExpandedContent(
    modifier: Modifier = Modifier,
    volume: CapsuleContent.Volume,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    onUserInteraction: () -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = contentPaddingDp.dp,
                end = contentPaddingDp.dp,
                top = topClearanceDp.dp,
                bottom = (contentPaddingDp * 0.8f).coerceAtLeast(10f).dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.desc_volume_icon),
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_volume_mixer),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

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

        displayedStreams.forEach { stream ->
            val icon = when (stream.streamType) {
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
            val max = stream.max.coerceAtLeast(1)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.width(46.dp),
                )
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
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            RingerModeOptionButton(
                mode = RingerModeState.NORMAL,
                selected = volume.ringerMode == RingerModeState.NORMAL,
                icon = Icons.Rounded.NotificationsActive,
                label = stringResource(R.string.label_ringer_normal),
                accent = accent,
                enabled = interactionsEnabled,
                onClick = {
                    onUserInteraction()
                    onSetRingerMode(RingerModeState.NORMAL)
                },
            )
            RingerModeOptionButton(
                mode = RingerModeState.VIBRATE,
                selected = volume.ringerMode == RingerModeState.VIBRATE,
                icon = Icons.Rounded.Vibration,
                label = stringResource(R.string.label_ringer_vibrate),
                accent = accent,
                enabled = interactionsEnabled,
                onClick = {
                    onUserInteraction()
                    onSetRingerMode(RingerModeState.VIBRATE)
                },
            )
            RingerModeOptionButton(
                mode = RingerModeState.SILENT,
                selected = volume.ringerMode == RingerModeState.SILENT,
                icon = Icons.Rounded.NotificationsOff,
                label = stringResource(R.string.label_ringer_silent),
                accent = accent,
                enabled = interactionsEnabled,
                onClick = {
                    onUserInteraction()
                    onSetRingerMode(RingerModeState.SILENT)
                },
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 200),
        label = "ringerOptionBg_${mode.name}",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
        animationSpec = tween(durationMillis = 200),
        label = "ringerOptionContent_${mode.name}",
    )

    CapsuleExpressiveSurface(
        enabled = enabled,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onClick()
        },
        restCornerDp = 18f,
        pressedCornerDp = 8f,
        pressedScale = 0.94f,
        backgroundColor = bgColor,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.72f)) else null,
        modifier = modifier.height(34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accent else contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}
