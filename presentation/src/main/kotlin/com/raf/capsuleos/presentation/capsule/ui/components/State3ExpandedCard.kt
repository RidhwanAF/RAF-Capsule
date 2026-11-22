package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.ExpandedMediaGlowEffect
import com.raf.capsuleos.domain.model.LandscapeCameraSide
import com.raf.capsuleos.domain.model.MediaAnimationColorMode
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SystemStatusKind

/**
 * Dispatches State 3 full-expanded content to the matching interactive control card with pure
 * crossfade + [SharedTransitionScope] morphing (e.g. Now Brief's Flashlight icon & label flying
 * seamlessly into the dedicated Flashlight square box without fighting scale transforms) and a
 * unified [State3CameraFlankBar] utilizing the space beside the camera in both portrait & landscape.
 */
@Composable
fun State3ExpandedCard(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    mediaSessions: List<CapsuleContent.Media> = emptyList(),
    selectedMediaIndex: Int = 0,
    leftSatellite: CapsuleContent.LeftSatelliteItem? = null,
    isExpandedFromSatellite: Boolean = false,
    swipeDirection: Int = 1,
    cutout: CutoutGeometry = CutoutGeometry(),
    cameraPosition: CameraAnchorPosition = CameraAnchorPosition.CENTER,
    belowStatusBar: Boolean = false,
    expandedMediaGlowEffects: Set<ExpandedMediaGlowEffect> = setOf(
        ExpandedMediaGlowEffect.CARD_BORDER_NEURON,
        ExpandedMediaGlowEffect.OUTER_SCREEN_GLOW,
        ExpandedMediaGlowEffect.ALBUM_ART_RING,
    ),
    mediaAnimationColorMode: MediaAnimationColorMode = MediaAnimationColorMode.ALBUM_COLOR,
    topClearanceDp: Float,
    isLandscape: Boolean = false,
    contentPaddingDp: Float = 18f,
    timeSeconds: Float,
    interactionsEnabled: Boolean = true,
    showMediaBackdrop: Boolean = true,
    enableAlbumSharedTransition: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onUserInteraction: () -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onReplyFocusChanged: (Boolean) -> Unit,
    onOpenMediaApp: (String) -> Unit,
    onSelectMediaSession: (Int) -> Unit = {},
    onOpenAudioOutput: () -> Unit = {},
    onLyricsActiveChanged: (Boolean) -> Unit = {},
    onTogglePlayPause: (String) -> Unit,
    onSkipNext: (String) -> Unit,
    onSkipPrevious: (String) -> Unit,
    onSeekTo: (String, Long) -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onCycleRingerMode: () -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
    onSetBrightnessPercent: (Int) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onAnswerCall: (CapsuleContent.CallAlert) -> Unit = {},
    onDeclineCall: (CapsuleContent.CallAlert) -> Unit = {},
    onOpenCallApp: (CapsuleContent.CallAlert) -> Unit = {},
    onOpenLiveActivityApp: (CapsuleContent.LiveActivity) -> Unit = {},
    onTriggerLiveActivityAction: (CapsuleContent.LiveActivity, Int) -> Unit = { _, _ -> },
    onOpenSystemStatusSettings: (SystemStatusKind) -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {},
    onOpenHotspotSettings: () -> Unit = {},
    onLockScreen: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    onOpenClock: () -> Unit,
    onOpenCalendar: () -> Unit = {},
) {
    val flankBarHeightDp = if (!isLandscape && !belowStatusBar) {
        topClearanceDp.coerceAtLeast(38f)
    } else {
        34f
    }
    val effectiveTopClearanceDp = if (!isLandscape && !belowStatusBar) {
        topClearanceDp
    } else {
        maxOf(topClearanceDp, flankBarHeightDp + 6f)
    }
    val isRightCamera = cutout.landscapeCameraSide == LandscapeCameraSide.RIGHT
    val hasPageDots = hasState3PageDots(
        content = content,
        mediaSessions = mediaSessions,
        leftSatellite = leftSatellite,
        isExpandedFromSatellite = isExpandedFromSatellite,
    )
    // Landscape content clears the physical side camera hole while pager/scroll stays edge-to-edge.
    val landscapeCameraClearDp = if (isLandscape && !belowStatusBar) {
        (cutout.holeWidthDp + 6f).coerceIn(28f, 38f)
    } else {
        0f
    }
    val dotsEndReserveDp = if (isLandscape && hasPageDots) 14f else 0f
    val cardStartClearDp = if (isLandscape) {
        (if (!isRightCamera) landscapeCameraClearDp else dotsEndReserveDp)
    } else {
        0f
    }
    val cardEndClearDp = if (isLandscape) {
        (if (isRightCamera) landscapeCameraClearDp else dotsEndReserveDp)
    } else {
        0f
    }
    val portraitBottomDotsDp = if (!isLandscape && hasPageDots) 18f else 0f

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = content,
            contentKey = ::state3CategoryKey,
            transitionSpec = {
                if (isExpandedFromSatellite) {
                    val dir = if (swipeDirection < 0) -1 else 1
                    if (isLandscape) {
                        (slideInVertically(tween(220)) { h -> dir * (h / 5) } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically(tween(160)) { h -> -dir * (h / 5) } + fadeOut(tween(140)))
                    } else {
                        (slideInHorizontally(tween(220)) { w -> dir * (w / 5) } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(160)) { w -> -dir * (w / 5) } + fadeOut(tween(140)))
                    }
                } else {
                    fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(160))
                }
            },
            label = "state3CardMorph",
        ) { active ->
            val sideInsetModifier = Modifier
                .fillMaxSize()
                .padding(
                    start = cardStartClearDp.dp,
                    end = cardEndClearDp.dp,
                    bottom = portraitBottomDotsDp.dp,
                )
            when (active) {
                is CapsuleContent.Media -> MediaExpandedContent(
                    media = active,
                    mediaSessions = mediaSessions,
                    selectedMediaIndex = selectedMediaIndex,
                    expandedMediaGlowEffects = expandedMediaGlowEffects,
                    mediaAnimationColorMode = mediaAnimationColorMode,
                    topClearanceDp = effectiveTopClearanceDp,
                    startClearanceDp = cardStartClearDp,
                    endClearanceDp = cardEndClearDp,
                    bottomDotsClearanceDp = portraitBottomDotsDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    timeSeconds = timeSeconds,
                    interactionsEnabled = interactionsEnabled,
                    showBackdrop = showMediaBackdrop,
                    enableAlbumSharedTransition = enableAlbumSharedTransition,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onOpenMediaApp = onOpenMediaApp,
                    onSelectMediaSession = onSelectMediaSession,
                    onOpenAudioOutput = onOpenAudioOutput,
                    onLyricsActiveChanged = onLyricsActiveChanged,
                    onUserInteraction = onUserInteraction,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onSeekTo = onSeekTo,
                )
                is CapsuleContent.NotificationAlert -> NotificationExpandedContent(
                    notification = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onUserInteraction = onUserInteraction,
                    onOpenNotification = { if (interactionsEnabled) onOpenNotification(active) },
                    onTriggerAction = { actionId, replyText ->
                        if (interactionsEnabled) onTriggerNotificationAction(active, actionId, replyText)
                    },
                    onDismissNotification = { if (interactionsEnabled) onDismissNotification(active) },
                    onReplyFocusChanged = onReplyFocusChanged,
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.CallAlert -> CallExpandedContent(
                    call = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onOpenCallApp = { onOpenCallApp(active) },
                    onAnswerCall = { onAnswerCall(active) },
                    onDeclineCall = { onDeclineCall(active) },
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.BluetoothDevice -> BluetoothExpandedContent(
                    device = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.Hotspot -> HotspotExpandedContent(
                    hotspot = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onOpenHotspotSettings = onOpenHotspotSettings,
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.Torch -> TorchExpandedContent(
                    torch = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                    onUserInteraction = onUserInteraction,
                    onToggleTorch = onToggleTorch,
                    onSetTorchStrength = onSetTorchStrength,
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.Volume -> VolumeExpandedContent(
                    volume = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onUserInteraction = onUserInteraction,
                    onSetStreamVolume = onSetStreamVolume,
                    onSetRingerMode = onSetRingerMode,
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.Alarm -> AlarmExpandedContent(
                    alarm = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    onOpenClock = { if (interactionsEnabled) onOpenClock() },
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.Unlock -> UnlockExpandedContent(
                    unlock = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.NowBrief -> QuickHubExpandedContent(
                    brief = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    startClearanceDp = cardStartClearDp,
                    endClearanceDp = cardEndClearDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                    onUserInteraction = onUserInteraction,
                    onToggleTorch = onToggleTorch,
                    onSetStreamVolume = onSetStreamVolume,
                    onCycleRingerMode = onCycleRingerMode,
                    onSetBrightnessPercent = onSetBrightnessPercent,
                    onToggleAutoBrightness = onToggleAutoBrightness,
                    onLockScreen = onLockScreen,
                    onOpenNotificationShade = onOpenNotificationShade,
                    onOpenClock = onOpenClock,
                )
                is CapsuleContent.LiveActivity -> LiveActivityExpandedContent(
                    liveActivity = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onUserInteraction = onUserInteraction,
                    onOpenLiveActivityApp = { if (interactionsEnabled) onOpenLiveActivityApp(active) },
                    onTriggerLiveActivityAction = { actionId ->
                        if (interactionsEnabled) onTriggerLiveActivityAction(active, actionId)
                    },
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.SystemStatus -> SystemStatusExpandedContent(
                    status = active,
                    topClearanceDp = effectiveTopClearanceDp,
                    isLandscape = isLandscape,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onUserInteraction = onUserInteraction,
                    onOpenSystemStatusSettings = onOpenSystemStatusSettings,
                    onSetRingerMode = onSetRingerMode,
                    modifier = sideInsetModifier,
                )
                is CapsuleContent.Charging,
                is CapsuleContent.BatteryPeek,
                is CapsuleContent.NotificationSummary,
                CapsuleContent.Idle,
                -> Unit
            }
        }

        // Landscape top flank bar ignores camera clearance for centered cameras (and bottom-rotated corner cameras),
        // dynamically padding only when a corner camera physically rotates into the top-left or top-right corner.
        val topBarNeedsLandscapeCameraPad = isLandscape && !belowStatusBar && when (cameraPosition) {
            CameraAnchorPosition.CENTER -> false
            CameraAnchorPosition.LEFT -> isRightCamera
            CameraAnchorPosition.RIGHT -> !isRightCamera
        }
        val flankStartPaddingDp = contentPaddingDp +
            (if (topBarNeedsLandscapeCameraPad && !isRightCamera) landscapeCameraClearDp else 0f)
        val flankEndPaddingDp = contentPaddingDp +
            (if (topBarNeedsLandscapeCameraPad && isRightCamera) landscapeCameraClearDp else 0f)

        // Camera-flanking top bar: app/feature identity on start + ticking clock & clickable date on end
        State3CameraFlankBar(
            content = content,
            mediaSessions = mediaSessions,
            selectedMediaIndex = selectedMediaIndex,
            cutout = cutout,
            cameraPosition = cameraPosition,
            belowStatusBar = belowStatusBar,
            isLandscape = isLandscape,
            barHeightDp = flankBarHeightDp,
            startPaddingDp = flankStartPaddingDp,
            endPaddingDp = flankEndPaddingDp,
            interactionsEnabled = interactionsEnabled,
            onOpenCalendar = onOpenCalendar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape || belowStatusBar) 4.dp else 0.dp),
        )

        // Page indicator dots: bottom-center horizontal in portrait, end-center vertical in landscape
        State3PageDotsIndicator(
            content = content,
            mediaSessions = mediaSessions,
            selectedMediaIndex = selectedMediaIndex,
            leftSatellite = leftSatellite,
            isExpandedFromSatellite = isExpandedFromSatellite,
            isLandscape = isLandscape,
            modifier = Modifier
                .align(
                    when {
                        !isLandscape -> Alignment.BottomCenter
                        isRightCamera -> Alignment.CenterStart
                        else -> Alignment.CenterEnd
                    },
                )
                .padding(
                    bottom = if (!isLandscape) 6.dp else 0.dp,
                    start = if (isLandscape && isRightCamera) 5.dp else 0.dp,
                    end = if (isLandscape && !isRightCamera) 5.dp else 0.dp,
                ),
        )
    }
}

private fun state3CategoryKey(content: CapsuleContent): String = when (content) {
    is CapsuleContent.Media -> "media"
    is CapsuleContent.NotificationAlert -> "notif:${content.key}"
    is CapsuleContent.CallAlert -> "call:${content.key}"
    is CapsuleContent.BluetoothDevice -> "bt:${content.address}"
    is CapsuleContent.Hotspot -> "hotspot"
    is CapsuleContent.LiveActivity -> "live:${content.key}"
    is CapsuleContent.SystemStatus -> "sys:${content.kind.name}"
    is CapsuleContent.Torch -> "torch"
    is CapsuleContent.Volume -> "volume"
    is CapsuleContent.Alarm -> "alarm"
    is CapsuleContent.Unlock -> "unlock"
    is CapsuleContent.NowBrief -> "nowbrief"
    else -> "other"
}
