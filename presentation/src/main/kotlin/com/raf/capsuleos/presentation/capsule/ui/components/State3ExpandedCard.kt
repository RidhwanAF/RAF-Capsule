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
import com.raf.capsuleos.domain.model.RingerModeState

/**
 * Dispatches State 3 full-expanded content to the matching interactive control card with pure
 * crossfade + [SharedTransitionScope] morphing (e.g. Now Brief's Flashlight icon & label flying
 * seamlessly into the dedicated Flashlight square box without fighting scale transforms).
 */
@Composable
fun State3ExpandedCard(
    modifier: Modifier = Modifier,
    content: CapsuleContent,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    timeSeconds: Float,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onUserInteraction: () -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onReplyFocusChanged: (Boolean) -> Unit,
    onOpenMediaApp: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onCycleRingerMode: () -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
    onSetBrightnessPercent: (Int) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onLockScreen: () -> Unit,
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
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    timeSeconds = timeSeconds,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onOpenMediaApp = { onOpenMediaApp(active.packageName) },
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
                    onOpenNotification = { onOpenNotification(active) },
                    onTriggerAction = { actionId, replyText ->
                        onTriggerNotificationAction(active, actionId, replyText)
                    },
                    onDismissNotification = { onDismissNotification(active) },
                    onReplyFocusChanged = onReplyFocusChanged,
                )
                is CapsuleContent.Torch -> TorchExpandedContent(
                    torch = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
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
                    onUserInteraction = onUserInteraction,
                    onSetStreamVolume = onSetStreamVolume,
                    onSetRingerMode = onSetRingerMode,
                )
                is CapsuleContent.Alarm -> AlarmExpandedContent(
                    alarm = active,
                    topClearanceDp = topClearanceDp,
                    contentPaddingDp = contentPaddingDp,
                    onOpenClock = onOpenClock,
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
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@AnimatedContent,
                    onUserInteraction = onUserInteraction,
                    onToggleTorch = onToggleTorch,
                    onSetStreamVolume = onSetStreamVolume,
                    onCycleRingerMode = onCycleRingerMode,
                    onSetBrightnessPercent = onSetBrightnessPercent,
                    onToggleAutoBrightness = onToggleAutoBrightness,
                    onLockScreen = onLockScreen,
                    onOpenClock = onOpenClock,
                )
                is CapsuleContent.Charging,
                is CapsuleContent.BatteryPeek,
                is CapsuleContent.LiveActivity,
                CapsuleContent.Idle,
                -> Unit
            }
        }
    }
}

private fun state3CategoryKey(content: CapsuleContent): String = when (content) {
    is CapsuleContent.Media -> "media"
    is CapsuleContent.NotificationAlert -> "notif:${content.key}"
    is CapsuleContent.Torch -> "torch"
    is CapsuleContent.Volume -> "volume"
    is CapsuleContent.Alarm -> "alarm"
    is CapsuleContent.Unlock -> "unlock"
    is CapsuleContent.NowBrief -> "nowbrief"
    else -> "other"
}
