package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Battery0Bar
import androidx.compose.material.icons.rounded.Battery1Bar
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery3Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.WeatherConditionKind
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.CapsuleSlider
import kotlin.math.roundToInt

/**
 * State 3 "Now Brief" control center showing At-a-Glance weather/notification counts,
 * Flashlight shared-morph trigger, end-aligned alarm status, Volume + Ringer rotation row,
 * and Brightness + Auto-Brightness rotation row.
 */
@Composable
fun QuickHubExpandedContent(
    modifier: Modifier = Modifier,
    brief: CapsuleContent.NowBrief,
    topClearanceDp: Float,
    startClearanceDp: Float = 0f,
    endClearanceDp: Float = 0f,
    isLandscape: Boolean = false,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onUserInteraction: () -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onCycleRingerMode: () -> Unit,
    onSetBrightnessPercent: (Int) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onLockScreen: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    onOpenClock: () -> Unit,
) {
    val localHapticFeedback = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val activeStream = brief.volume.streams.firstOrNull { it.streamType == brief.volume.activeStream }
        ?: brief.volume.streams.firstOrNull { it.streamType == AudioStreamType.MEDIA }
        ?: brief.volume.streams.firstOrNull()
    val torchTileBg by animateColorAsState(
        targetValue = if (brief.torch.isEnabled) primary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 220),
        label = "hubTorchTileBg",
    )
    val torchCircleBg by animateColorAsState(
        targetValue = if (brief.torch.isEnabled) primary else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(durationMillis = 220),
        label = "hubTorchCircleBg",
    )
    val torchCircleTint by animateColorAsState(
        targetValue = if (brief.torch.isEnabled) Color.Black else Color.White,
        animationSpec = tween(durationMillis = 220),
        label = "hubTorchTint",
    )

    val autoActive = brief.brightness.isAuto
    var autoSpinCycles by remember { mutableIntStateOf(0) }
    val autoBtnBg by animateColorAsState(
        targetValue = if (autoActive) primary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 220),
        label = "autoBrightnessBtnBg",
    )
    val autoBtnTint by animateColorAsState(
        targetValue = if (autoActive) primary else Color.White.copy(alpha = 0.88f),
        animationSpec = tween(durationMillis = 220),
        label = "autoBrightnessBtnTint",
    )
    val autoIconRotation by animateFloatAsState(
        targetValue = autoSpinCycles * 360f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "autoBrightnessSpin",
    )

    val volumeRowContent: @Composable () -> Unit = {
        if (activeStream != null) {
            val max = activeStream.max.coerceAtLeast(1)
            val isMuted = activeStream.current <= 0 || brief.volume.ringerMode == RingerModeState.SILENT
            val ringerActive = brief.volume.ringerMode != RingerModeState.NORMAL
            val ringerBtnBg by animateColorAsState(
                targetValue = if (ringerActive) primary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.12f),
                animationSpec = tween(durationMillis = 220),
                label = "ringerBtnBg",
            )
            val ringerBtnTint by animateColorAsState(
                targetValue = if (ringerActive) primary else Color.White.copy(alpha = 0.90f),
                animationSpec = tween(durationMillis = 220),
                label = "ringerBtnTint",
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(R.string.desc_volume_icon),
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                CapsuleSlider(
                    value = activeStream.current.toFloat(),
                    onValueChange = { next ->
                        val target = next.roundToInt().coerceIn(0, max)
                        if (target != activeStream.current) {
                            onSetStreamVolume(activeStream.streamType, target)
                        }
                    },
                    valueRange = 0f..max.toFloat(),
                    enabled = interactionsEnabled,
                    onInteraction = onUserInteraction,
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                CapsuleExpressiveSurface(
                    enabled = interactionsEnabled,
                    onClick = {
                        localHapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onUserInteraction()
                        onCycleRingerMode()
                    },
                    restCornerDp = 17f,
                    pressedCornerDp = 8f,
                    backgroundColor = ringerBtnBg,
                    modifier = Modifier
                        .width(42.dp)
                        .height(34.dp),
                ) {
                    AnimatedContent(
                        targetState = brief.volume.ringerMode,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.72f, animationSpec = spring(0.75f, 450f))) togetherWith
                                (fadeOut(tween(120)) + scaleOut(targetScale = 0.72f, animationSpec = tween(120)))
                        },
                        label = "ringerModeIconAnim",
                    ) { mode ->
                        Icon(
                            imageVector = ringerIconForMode(mode),
                            contentDescription = stringResource(R.string.desc_ringer_mode),
                            tint = ringerBtnTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    val brightnessRowContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = brightnessIconForLevel(brief.brightness.levelPercent),
                contentDescription = stringResource(R.string.desc_brightness_icon),
                tint = primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            CapsuleSlider(
                value = brief.brightness.levelPercent.toFloat(),
                onValueChange = { next ->
                    val target = next.roundToInt().coerceIn(0, 100)
                    if (target != brief.brightness.levelPercent) {
                        onSetBrightnessPercent(target)
                    }
                },
                onValueChangeFinished = {
                    if (!brief.brightness.canWrite) {
                        onToggleAutoBrightness()
                    }
                },
                valueRange = 0f..100f,
                enabled = interactionsEnabled,
                onInteraction = onUserInteraction,
                colors = SliderDefaults.colors(
                    thumbColor = primary,
                    activeTrackColor = primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            CapsuleExpressiveSurface(
                enabled = interactionsEnabled,
                onClick = {
                    localHapticFeedback.performHapticFeedback(
                        if (autoActive) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    autoSpinCycles += 1
                    onUserInteraction()
                    onToggleAutoBrightness()
                },
                restCornerDp = 17f,
                pressedCornerDp = 8f,
                backgroundColor = autoBtnBg,
                modifier = Modifier
                    .width(42.dp)
                    .height(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrightnessAuto,
                    contentDescription = stringResource(R.string.desc_auto_brightness),
                    tint = autoBtnTint,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = autoIconRotation },
                )
            }
        }
    }

    if (isLandscape) {
        val batteryColor = capsuleBatteryAccentColor(
            batteryPercent = brief.batteryPercent,
            isCharging = brief.isCharging,
            primaryColor = primary,
        )
        val torchStatusLabel = when {
            !brief.torch.isEnabled -> "OFF"
            brief.torch.maxStrengthLevel > 1 -> "${brief.torch.brightnessPercent}%"
            else -> "ON"
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = (contentPaddingDp + startClearanceDp).dp,
                    end = (contentPaddingDp + endClearanceDp).dp,
                    top = topClearanceDp.dp,
                    bottom = (contentPaddingDp + 2f).coerceAtLeast(12f).dp,
                ),
        ) {
            // 1. Unified At-a-Glance Bento Card (Battery + Weather + Notifications + Next Alarm)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = segmentedBatteryIconForLevel(brief.batteryPercent),
                            contentDescription = stringResource(R.string.desc_battery_icon),
                            tint = batteryColor,
                            modifier = Modifier.size(16.dp),
                        )
                        if (brief.isCharging) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = stringResource(R.string.label_charging),
                                tint = batteryColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "${brief.batteryPercent}%",
                            color = if (brief.isCharging) batteryColor else Color.White.copy(alpha = 0.90f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }

                    brief.weather?.let { weather ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = weatherIconForKind(weather.conditionKind),
                                contentDescription = stringResource(R.string.desc_weather_icon),
                                tint = primary.copy(alpha = 0.92f),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = weather.formattedBadge,
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (brief.activeNotificationCount > 0) {
                        CapsuleExpressiveSurface(
                            enabled = interactionsEnabled,
                            onClick = {
                                localHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onOpenNotificationShade()
                            },
                            restCornerDp = 12f,
                            pressedCornerDp = 7f,
                            backgroundColor = primary.copy(alpha = 0.18f),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = stringResource(R.string.desc_notification_count),
                                    tint = primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.label_notification_count_summary,
                                        brief.activeNotificationCount,
                                        brief.activeNotificationCount,
                                    ),
                                    color = primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    CapsuleExpressiveSurface(
                        enabled = interactionsEnabled,
                        onClick = {
                            localHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onOpenClock()
                        },
                        restCornerDp = 12f,
                        pressedCornerDp = 7f,
                        backgroundColor = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Alarm,
                                contentDescription = stringResource(R.string.desc_alarm_icon),
                                tint = if (brief.nextAlarmLabel != null) primary else Color.White.copy(alpha = 0.52f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = brief.nextAlarmLabel ?: stringResource(R.string.label_no_alarm),
                                color = if (brief.nextAlarmLabel != null) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.56f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 2. Single-Column Full-Width Quick Actions (Flashlight + Lock Screen)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CapsuleExpressiveSurface(
                    onClick = {
                        localHapticFeedback.performHapticFeedback(
                            if (brief.torch.isEnabled) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                        )
                        onUserInteraction()
                        onToggleTorch(!brief.torch.isEnabled)
                    },
                    restCornerDp = 22f,
                    pressedCornerDp = 10f,
                    backgroundColor = torchTileBg,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    with(sharedTransitionScope) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "torch_icon_box"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(torchCircleBg),
                            ) {
                                AnimatedContent(
                                    targetState = brief.torch.isEnabled,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.72f, animationSpec = tween(180))) togetherWith
                                            (fadeOut(tween(130)) + scaleOut(targetScale = 0.72f, animationSpec = tween(130)))
                                    },
                                    label = "hubTorchIcon",
                                ) { enabled ->
                                    Icon(
                                        imageVector = if (enabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                        contentDescription = stringResource(R.string.desc_torch_icon),
                                        tint = torchCircleTint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.label_torch),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "torch_label"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    ),
                            )
                            Text(
                                text = torchStatusLabel,
                                color = if (brief.torch.isEnabled) primary else Color.White.copy(alpha = 0.56f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                CapsuleExpressiveSurface(
                    enabled = interactionsEnabled,
                    onClick = {
                        localHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onLockScreen()
                    },
                    restCornerDp = 22f,
                    pressedCornerDp = 10f,
                    backgroundColor = Color.White.copy(alpha = 0.10f),
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f)),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.desc_lock_screen),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_lock_screen),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 3. Grouped Sliders Control Card (Volume + Brightness)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                volumeRowContent()
                brightnessRowContent()
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = (contentPaddingDp + startClearanceDp).dp,
                    end = (contentPaddingDp + endClearanceDp).dp,
                    top = topClearanceDp.dp,
                    bottom = (contentPaddingDp + 2f).coerceAtLeast(12f).dp,
                ),
        ) {
            // Compact At-a-Glance weather/notification summary + clickable Alarm chip
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    AtAGlanceSummaryRow(
                        brief = brief,
                        primary = primary,
                        interactionsEnabled = interactionsEnabled,
                        onOpenNotificationShade = {
                            localHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onOpenNotificationShade()
                        },
                    )
                }
                CapsuleExpressiveSurface(
                    enabled = interactionsEnabled,
                    onClick = {
                        localHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onOpenClock()
                    },
                    restCornerDp = 12f,
                    pressedCornerDp = 7f,
                    backgroundColor = Color.White.copy(alpha = 0.08f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Alarm,
                            contentDescription = stringResource(R.string.desc_alarm_icon),
                            tint = if (brief.nextAlarmLabel != null) primary else Color.White.copy(alpha = 0.52f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = brief.nextAlarmLabel ?: stringResource(R.string.label_no_alarm),
                            color = if (brief.nextAlarmLabel != null) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.56f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Quick Action Row: Compact Expressive Flashlight Left + Expressive Lock Screen Right
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CapsuleExpressiveSurface(
                    onClick = {
                        localHapticFeedback.performHapticFeedback(
                            if (brief.torch.isEnabled) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                        )
                        onUserInteraction()
                        onToggleTorch(!brief.torch.isEnabled)
                    },
                    restCornerDp = 22f,
                    pressedCornerDp = 10f,
                    backgroundColor = torchTileBg,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 1.dp),
                ) {
                    with(sharedTransitionScope) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "torch_icon_box"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(torchCircleBg),
                            ) {
                                AnimatedContent(
                                    targetState = brief.torch.isEnabled,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.72f, animationSpec = tween(180))) togetherWith
                                            (fadeOut(tween(130)) + scaleOut(targetScale = 0.72f, animationSpec = tween(130)))
                                    },
                                    label = "hubTorchIcon",
                                ) { enabled ->
                                    Icon(
                                        imageVector = if (enabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                        contentDescription = stringResource(R.string.desc_torch_icon),
                                        tint = torchCircleTint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.label_torch),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "torch_label"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                            )
                        }
                    }
                }

                CapsuleExpressiveSurface(
                    enabled = interactionsEnabled,
                    onClick = {
                        localHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onLockScreen()
                    },
                    restCornerDp = 22f,
                    pressedCornerDp = 10f,
                    backgroundColor = Color.White.copy(alpha = 0.10f),
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 1.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f)),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.desc_lock_screen),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_lock_screen),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            volumeRowContent()
            brightnessRowContent()
        }
    }
}

@Composable
private fun AtAGlanceSummaryRow(
    modifier: Modifier = Modifier,
    brief: CapsuleContent.NowBrief,
    primary: Color,
    interactionsEnabled: Boolean = true,
    onOpenNotificationShade: () -> Unit = {},
) {
    val batteryColor = capsuleBatteryAccentColor(
        batteryPercent = brief.batteryPercent,
        isCharging = brief.isCharging,
        primaryColor = primary,
    )
    val batteryLabel = "${brief.batteryPercent}%"
    val liveTitle = brief.liveActivity?.title?.takeIf { it.isNotBlank() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = segmentedBatteryIconForLevel(brief.batteryPercent),
                contentDescription = stringResource(R.string.desc_battery_icon),
                tint = batteryColor,
                modifier = Modifier.size(15.dp),
            )
            if (brief.isCharging) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = stringResource(R.string.label_charging),
                    tint = batteryColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = batteryLabel,
                color = if (brief.isCharging) batteryColor else Color.White.copy(alpha = 0.84f),
                fontSize = 12.sp,
                fontWeight = if (brief.isCharging) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }

        brief.weather?.let { weather ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = weatherIconForKind(weather.conditionKind),
                    contentDescription = stringResource(R.string.desc_weather_icon),
                    tint = primary.copy(alpha = 0.92f),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = weather.formattedBadge,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }

        if (liveTitle != null) {
            Text(
                text = "• $liveTitle",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }

        if (brief.activeNotificationCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary.copy(alpha = 0.18f))
                    .clickable(enabled = interactionsEnabled, onClick = onOpenNotificationShade)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = stringResource(R.string.desc_notification_count),
                    tint = primary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = brief.activeNotificationCount.toString(),
                    color = primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun segmentedBatteryIconForLevel(percent: Int): ImageVector = when {
    percent >= 92 -> Icons.Rounded.BatteryFull
    percent >= 78 -> Icons.Rounded.Battery6Bar
    percent >= 64 -> Icons.Rounded.Battery5Bar
    percent >= 50 -> Icons.Rounded.Battery4Bar
    percent >= 36 -> Icons.Rounded.Battery3Bar
    percent >= 22 -> Icons.Rounded.Battery2Bar
    percent >= 10 -> Icons.Rounded.Battery1Bar
    else -> Icons.Rounded.Battery0Bar
}

private fun ringerIconForMode(mode: RingerModeState): ImageVector = when (mode) {
    RingerModeState.NORMAL -> Icons.Rounded.NotificationsActive
    RingerModeState.VIBRATE -> Icons.Rounded.Vibration
    RingerModeState.SILENT -> Icons.Rounded.NotificationsOff
}

private fun brightnessIconForLevel(percent: Int): ImageVector = when {
    percent >= 68 -> Icons.Rounded.BrightnessHigh
    percent >= 32 -> Icons.Rounded.BrightnessMedium
    else -> Icons.Rounded.BrightnessLow
}

private fun weatherIconForKind(kind: WeatherConditionKind): ImageVector = when (kind) {
    WeatherConditionKind.CLEAR -> Icons.Rounded.WbSunny
    WeatherConditionKind.CLOUDY -> Icons.Rounded.Cloud
    WeatherConditionKind.RAIN -> Icons.Rounded.WaterDrop
    WeatherConditionKind.SNOW -> Icons.Rounded.AcUnit
    WeatherConditionKind.STORM -> Icons.Rounded.Thunderstorm
    WeatherConditionKind.FOG -> Icons.Rounded.Cloud
}
