package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.ExpandedMediaGlowEffect
import com.raf.capsuleos.domain.model.MediaAnimationColorMode
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SystemStatusKind

/**
 * Dispatches State 3 full-expanded content to the matching interactive control card with pure
 * crossfade + [SharedTransitionScope] morphing (e.g. Now Brief's Flashlight icon & label flying
 * seamlessly into the dedicated Flashlight square box without fighting scale transforms).
 */
@Composable
fun State3ExpandedCard(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    mediaSessions: List<CapsuleContent.Media> = emptyList(),
    selectedMediaIndex: Int = 0,
    expandedMediaGlowEffects: Set<ExpandedMediaGlowEffect> = setOf(
        ExpandedMediaGlowEffect.CARD_BORDER_NEURON,
        ExpandedMediaGlowEffect.OUTER_SCREEN_GLOW,
        ExpandedMediaGlowEffect.ALBUM_ART_RING,
    ),
    mediaAnimationColorMode: MediaAnimationColorMode = MediaAnimationColorMode.ALBUM_COLOR,
    topClearanceDp: Float,
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
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = content,
            contentKey = ::state3CategoryKey,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith
                    fadeOut(animationSpec = tween(160))
            },
            label = "state3CardMorph",
        ) { active ->
            when (active) {
                is CapsuleContent.Media -> MediaExpandedContent(
                    media = active,
                    mediaSessions = mediaSessions,
                    selectedMediaIndex = selectedMediaIndex,
                    expandedMediaGlowEffects = expandedMediaGlowEffects,
                    mediaAnimationColorMode = mediaAnimationColorMode,
                    topClearanceDp = topClearanceDp,
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
                    topClearanceDp = topClearanceDp,
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
                )
                is CapsuleContent.CallAlert -> CallExpandedContent(
                    call = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onOpenCallApp = { onOpenCallApp(active) },
                    onAnswerCall = { onAnswerCall(active) },
                    onDeclineCall = { onDeclineCall(active) },
                )
                is CapsuleContent.BluetoothDevice -> BluetoothExpandedContent(
                    device = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                )
                is CapsuleContent.Hotspot -> HotspotExpandedContent(
                    hotspot = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onOpenHotspotSettings = onOpenHotspotSettings,
                )
                is CapsuleContent.Torch -> TorchExpandedContent(
                    torch = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                    onUserInteraction = onUserInteraction,
                    onToggleTorch = onToggleTorch,
                    onSetTorchStrength = onSetTorchStrength,
                )
                is CapsuleContent.Volume -> VolumeExpandedContent(
                    volume = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    onUserInteraction = onUserInteraction,
                    onSetStreamVolume = onSetStreamVolume,
                    onSetRingerMode = onSetRingerMode,
                )
                is CapsuleContent.Alarm -> AlarmExpandedContent(
                    alarm = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    onOpenClock = { if (interactionsEnabled) onOpenClock() },
                )
                is CapsuleContent.Unlock -> UnlockExpandedContent(
                    unlock = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                is CapsuleContent.NowBrief -> QuickHubExpandedContent(
                    brief = active,
                    topClearanceDp = topClearanceDp,
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
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onUserInteraction = onUserInteraction,
                    onOpenLiveActivityApp = { if (interactionsEnabled) onOpenLiveActivityApp(active) },
                    onTriggerLiveActivityAction = { actionId ->
                        if (interactionsEnabled) onTriggerLiveActivityAction(active, actionId)
                    },
                )
                is CapsuleContent.SystemStatus -> SystemStatusExpandedContent(
                    status = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    interactionsEnabled = interactionsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onUserInteraction = onUserInteraction,
                    onOpenSystemStatusSettings = onOpenSystemStatusSettings,
                    onSetRingerMode = onSetRingerMode,
                )
                is CapsuleContent.Charging,
                is CapsuleContent.BatteryPeek,
                is CapsuleContent.NotificationSummary,
                CapsuleContent.Idle,
                -> Unit
            }
        }
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
