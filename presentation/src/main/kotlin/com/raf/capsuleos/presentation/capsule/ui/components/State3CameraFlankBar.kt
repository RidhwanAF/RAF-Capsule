package com.raf.capsuleos.presentation.capsule.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LockOpen
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.SatelliteSource
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.rememberCachedImageBitmap
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

/**
 * Compact header bar placed directly beside the physical camera cutout in Portrait (or cleanly
 * along the top edge in Landscape / Below-Status-Bar mode) so State 3 expanded cards utilize
 * the camera-flanking space without duplicating body information.
 *
 * - **Start flank**: Active app/feature icon + name.
 * - **End flank**: Live ticking clock with seconds + clickable full date with Calendar icon
 *   (launches the default Calendar app or Google Calendar fallback).
 */
@Composable
fun State3CameraFlankBar(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    mediaSessions: List<CapsuleContent.Media>,
    selectedMediaIndex: Int,
    cutout: CutoutGeometry,
    cameraPosition: CameraAnchorPosition,
    belowStatusBar: Boolean,
    isLandscape: Boolean,
    barHeightDp: Float,
    startPaddingDp: Float,
    endPaddingDp: Float,
    interactionsEnabled: Boolean = true,
    onOpenCalendar: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val hasTopCameraHole = !isLandscape && !belowStatusBar
    val cameraGapDp = (cutout.holeWidthDp + 16f).coerceIn(30f, 52f)

    val startSlot: @Composable () -> Unit = {
        FlankStartIdentity(
            content = content,
            mediaSessions = mediaSessions,
            selectedMediaIndex = selectedMediaIndex,
            primary = primary,
        )
    }
    val endSlot: @Composable () -> Unit = {
        FlankLiveClockAndDate(
            primary = primary,
            compactTwoLine = hasTopCameraHole && cameraPosition == CameraAnchorPosition.CENTER,
            interactionsEnabled = interactionsEnabled,
            onOpenCalendar = onOpenCalendar,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(barHeightDp.coerceAtLeast(30f).dp)
            .padding(
                start = startPaddingDp.coerceAtLeast(12f).dp,
                end = endPaddingDp.coerceAtLeast(12f).dp,
            ),
    ) {
        if (!hasTopCameraHole) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f),
            ) {
                startSlot()
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(contentAlignment = Alignment.CenterEnd) {
                endSlot()
            }
        } else {
            when (cameraPosition) {
                CameraAnchorPosition.CENTER -> {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        startSlot()
                    }
                    Spacer(modifier = Modifier.width(cameraGapDp.dp))
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        endSlot()
                    }
                }
                CameraAnchorPosition.LEFT -> {
                    Spacer(modifier = Modifier.width(cameraGapDp.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.weight(1f),
                        ) {
                            startSlot()
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        endSlot()
                    }
                }
                CameraAnchorPosition.RIGHT -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.weight(1f),
                        ) {
                            startSlot()
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        endSlot()
                    }
                    Spacer(modifier = Modifier.width(cameraGapDp.dp))
                }
            }
        }
    }
}

/**
 * Multi-page dot indicator for State 3 cards (multi-session Media or multi-item Satellite).
 * Renders horizontally at bottom-center in portrait, and vertically at end-center in landscape.
 */
@Composable
fun State3PageDotsIndicator(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    mediaSessions: List<CapsuleContent.Media>,
    selectedMediaIndex: Int,
    leftSatellite: CapsuleContent.LeftSatelliteItem?,
    isExpandedFromSatellite: Boolean,
    isLandscape: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    val pageSpec: Triple<Int, Int, Color>? = when {
        content is CapsuleContent.Media && mediaSessions.size > 1 -> Triple(
            mediaSessions.size,
            selectedMediaIndex.coerceIn(0, mediaSessions.lastIndex),
            primary,
        )
        else -> {
            val contentSatSource = flankSatelliteSourceFor(content)
            if ((isExpandedFromSatellite || contentSatSource != null) &&
                leftSatellite != null &&
                (leftSatellite.totalCount > 1 || (contentSatSource != null && contentSatSource != leftSatellite.source))
            ) {
                val isDeactivatedCurrent = contentSatSource != null && contentSatSource != leftSatellite.source
                val totalDots = if (isDeactivatedCurrent) leftSatellite.totalCount + 1 else leftSatellite.totalCount
                val activeDot = if (isDeactivatedCurrent) 0 else leftSatellite.activeIndex.coerceIn(0, totalDots - 1)
                Triple(
                    totalDots,
                    activeDot,
                    if (isDeactivatedCurrent) Color.White.copy(alpha = 0.65f) else primary,
                )
            } else {
                null
            }
        }
    }

    if (pageSpec == null || pageSpec.first <= 1) return
    val (totalCount, activeIndex, accentColor) = pageSpec
    val visibleCount = totalCount.coerceAtMost(6)

    if (isLandscape) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.42f))
                .border(
                    width = 0.7.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 3.5.dp, vertical = 7.dp),
        ) {
            repeat(visibleCount) { index ->
                val isCurrent = index == activeIndex
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = if (isCurrent) 14.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) accentColor else Color.White.copy(alpha = 0.36f),
                        ),
                )
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.42f))
                .border(
                    width = 0.7.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 8.dp, vertical = 3.5.dp),
        ) {
            repeat(visibleCount) { index ->
                val isCurrent = index == activeIndex
                Box(
                    modifier = Modifier
                        .size(width = if (isCurrent) 14.dp else 5.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) accentColor else Color.White.copy(alpha = 0.36f),
                        ),
                )
            }
        }
    }
}

/**
 * Returns true when [State3PageDotsIndicator] will be visible for the given State 3 context so
 * cards can reserve subtle non-overlapping breathing room at the bottom (portrait) or end (landscape).
 */
internal fun hasState3PageDots(
    content: CapsuleContent,
    mediaSessions: List<CapsuleContent.Media>,
    leftSatellite: CapsuleContent.LeftSatelliteItem?,
    isExpandedFromSatellite: Boolean,
): Boolean {
    if (content is CapsuleContent.Media && mediaSessions.size > 1) return true
    val contentSatSource = flankSatelliteSourceFor(content)
    return (isExpandedFromSatellite || contentSatSource != null) &&
        leftSatellite != null &&
        (leftSatellite.totalCount > 1 || (contentSatSource != null && contentSatSource != leftSatellite.source))
}

@Composable
private fun FlankLiveClockAndDate(
    primary: Color,
    compactTwoLine: Boolean,
    interactionsEnabled: Boolean,
    onOpenCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberCapsuleHaptics()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            nowMs = current
            val nextTickMs = (1_000L - (current % 1_000L)).coerceIn(80L, 1_000L)
            delay(nextTickMs.milliseconds)
        }
    }

    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val locale = LocalLocale.current.platformLocale
    val timeFormatter = remember(is24Hour, locale) {
        SimpleDateFormat(if (is24Hour) "HH:mm:ss" else "h:mm:ss", locale)
    }
    val dateFormatter = remember(locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEE, MMM d")
        SimpleDateFormat(pattern, locale)
    }
    val dateObj = Date(nowMs)
    val timeText = timeFormatter.format(dateObj)
    val dateText = dateFormatter.format(dateObj)

    CapsuleExpressiveSurface(
        enabled = interactionsEnabled,
        onClick = {
            haptics.click()
            onOpenCalendar()
        },
        restCornerDp = 10f,
        pressedCornerDp = 6f,
        backgroundColor = Color.Transparent,
    ) {
        if (compactTwoLine) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = timeText,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = dateText,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = dateText,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun FlankStartIdentity(
    content: CapsuleContent,
    mediaSessions: List<CapsuleContent.Media>,
    selectedMediaIndex: Int,
    primary: Color,
) {
    val context = LocalContext.current
    when (content) {
        is CapsuleContent.Media -> {
            val activeMedia = mediaSessions.getOrNull(selectedMediaIndex) ?: content
            val iconBitmap = rememberCachedImageBitmap(activeMedia.appIconBytes)
            val fallbackLabel = stringResource(R.string.label_card_media)
            val appLabel = remember(activeMedia.packageName, context) {
                if (activeMedia.packageName.isBlank()) {
                    null
                } else {
                    runCatching {
                        val info = context.packageManager.getApplicationInfo(activeMedia.packageName, 0)
                        context.packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
                    }.getOrNull()
                }
            } ?: fallbackLabel
            FlankBadgeRow(
                bitmap = iconBitmap,
                fallbackIcon = Icons.Rounded.MusicNote,
                tint = primary,
                label = appLabel,
            )
        }
        is CapsuleContent.NotificationAlert -> {
            val iconBitmap = rememberCachedImageBitmap(content.iconBytes)
            FlankBadgeRow(
                bitmap = iconBitmap,
                fallbackIcon = Icons.Rounded.Notifications,
                tint = primary,
                label = content.appName,
            )
        }
        is CapsuleContent.CallAlert -> {
            val iconBitmap = rememberCachedImageBitmap(content.appIconBytes)
            FlankBadgeRow(
                bitmap = iconBitmap,
                fallbackIcon = Icons.Rounded.Call,
                tint = Color(0xFF4ADE80),
                label = content.appName.ifBlank {
                    stringResource(if (content.isIncoming) R.string.label_incoming_call else R.string.label_ongoing_call)
                },
            )
        }
        is CapsuleContent.NowBrief -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = Icons.Rounded.AutoAwesome,
            tint = primary,
            label = stringResource(R.string.title_now_brief),
            labelColor = primary,
        )
        is CapsuleContent.Torch -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = if (content.isEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            tint = if (content.isEnabled) primary else Color.White.copy(alpha = 0.65f),
            label = stringResource(R.string.label_torch),
        )
        is CapsuleContent.Volume -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = Icons.AutoMirrored.Rounded.VolumeUp,
            tint = primary,
            label = stringResource(R.string.label_volume_mixer),
        )
        is CapsuleContent.Hotspot -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = Icons.Rounded.WifiTethering,
            tint = primary,
            label = stringResource(R.string.label_hotspot),
        )
        is CapsuleContent.BluetoothDevice -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = Icons.Rounded.BluetoothConnected,
            tint = primary,
            label = stringResource(R.string.label_bluetooth_connected),
        )
        is CapsuleContent.Alarm -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = Icons.Rounded.Alarm,
            tint = primary,
            label = stringResource(R.string.label_next_alarm),
        )
        is CapsuleContent.LiveActivity -> {
            val iconBitmap = rememberCachedImageBitmap(content.iconBytes)
            val fallbackLabel = content.title.ifBlank { stringResource(R.string.label_live_activity) }
            val resolvedLabel = remember(content.packageName, context) {
                val pkg = content.packageName
                if (pkg.isNullOrBlank()) {
                    null
                } else {
                    runCatching {
                        val info = context.packageManager.getApplicationInfo(pkg, 0)
                        context.packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
                    }.getOrNull()
                }
            } ?: fallbackLabel
            FlankBadgeRow(
                bitmap = iconBitmap,
                fallbackIcon = Icons.Rounded.Timer,
                tint = primary,
                label = resolvedLabel,
            )
        }
        is CapsuleContent.SystemStatus -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = flankSystemStatusIcon(content),
            tint = primary,
            label = flankSystemStatusLabel(content.kind),
        )
        is CapsuleContent.Unlock -> FlankBadgeRow(
            bitmap = null,
            fallbackIcon = Icons.Rounded.LockOpen,
            tint = primary,
            label = "",
        )
        else -> Unit
    }
}

@Composable
private fun FlankBadgeRow(
    bitmap: ImageBitmap?,
    fallbackIcon: ImageVector,
    tint: Color,
    label: String,
    labelColor: Color = Color.White.copy(alpha = 0.88f),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun flankSatelliteSourceFor(content: CapsuleContent): SatelliteSource? = when (content) {
    is CapsuleContent.Torch -> SatelliteSource.TORCH
    is CapsuleContent.Hotspot -> SatelliteSource.HOTSPOT
    is CapsuleContent.LiveActivity -> SatelliteSource.LIVE_ACTIVITY
    is CapsuleContent.SystemStatus -> when (content.kind) {
        SystemStatusKind.AIRPLANE_MODE -> SatelliteSource.AIRPLANE_MODE
        SystemStatusKind.DO_NOT_DISTURB -> SatelliteSource.DO_NOT_DISTURB
        SystemStatusKind.BATTERY_SAVER -> SatelliteSource.BATTERY_SAVER
        SystemStatusKind.VPN -> SatelliteSource.VPN
        SystemStatusKind.RINGER_MUTED -> SatelliteSource.RINGER_MUTED
        SystemStatusKind.DATA_SAVER -> SatelliteSource.DATA_SAVER
    }
    else -> null
}

private fun flankSystemStatusIcon(status: CapsuleContent.SystemStatus): ImageVector = when (status.kind) {
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

@Composable
private fun flankSystemStatusLabel(kind: SystemStatusKind): String = when (kind) {
    SystemStatusKind.AIRPLANE_MODE -> stringResource(R.string.label_status_airplane)
    SystemStatusKind.DO_NOT_DISTURB -> stringResource(R.string.label_status_dnd)
    SystemStatusKind.BATTERY_SAVER -> stringResource(R.string.label_status_battery_saver)
    SystemStatusKind.VPN -> stringResource(R.string.label_status_vpn)
    SystemStatusKind.RINGER_MUTED -> stringResource(R.string.label_status_ringer_muted)
    SystemStatusKind.DATA_SAVER -> stringResource(R.string.label_status_data_saver)
}
