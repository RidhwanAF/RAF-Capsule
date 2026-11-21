package com.raf.capsuleos.presentation.simulation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.simulation.components.CapsulePhoneMockup
import com.raf.capsuleos.presentation.simulation.components.SimulationControlsSection

/**
 * Stateless Navigation 3 Simulation layout pinning [CapsulePhoneMockup] above (portrait) or beside
 * (landscape) the scrollable trigger panels with edge-to-edge non-clipping Scaffold padding.
 */
@Composable
fun SimulationContent(
    modifier: Modifier = Modifier,
    uiState: SimulationUiState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    onMockupWidthMeasured: (Float) -> Unit,
    onResolveExpandTarget: () -> CapsuleContent,
    onResolveCollapseTarget: () -> Pair<CapsuleVisualState, CapsuleContent>,
    onEvent: (CapsuleEvent) -> Unit,
    onToggleMedia: () -> Unit,
    onToggleMultiMedia: () -> Unit = {},
    onPostNotification: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
    onToggleHotspot: () -> Unit = {},
    onToggleLiveActivity: () -> Unit = {},
    onTriggerBluetooth: () -> Unit = {},
    onTriggerCall: () -> Unit = {},
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onCycleRingerMode: () -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
    onSetBrightnessPercent: (Int) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onTriggerAlarm: () -> Unit,
    onToggleCharging: () -> Unit,
    onTriggerUnlock: () -> Unit,
    onResetRing: () -> Unit,
    onSelectPreviewPosition: (CameraAnchorPosition) -> Unit,
    onTogglePlayPause: (String) -> Unit,
    onSkipNext: (String) -> Unit,
    onSkipPrevious: (String) -> Unit,
    onSeekTo: (String, Long) -> Unit,
    onSelectMediaSession: (Int) -> Unit = {},
    onOpenAudioOutput: () -> Unit = {},
    onAnswerCall: (CapsuleContent.CallAlert) -> Unit = {},
    onDeclineCall: (CapsuleContent.CallAlert) -> Unit = {},
    onOpenCallApp: (CapsuleContent.CallAlert) -> Unit = {},
    onOpenLiveActivityApp: (CapsuleContent.LiveActivity) -> Unit = {},
    onTriggerLiveActivityAction: (CapsuleContent.LiveActivity, Int) -> Unit = { _, _ -> },
    onOpenSystemStatusSettings: (SystemStatusKind) -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {},
    onOpenHotspotSettings: () -> Unit = {},
    onLockScreenButton: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    onWakeFromLock: () -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onOpenMediaApp: () -> Unit,
    onOpenClock: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val sharedContainerMod = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "nav_simulation_container"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
            )
        }
    } else {
        Modifier
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_simulation_screen),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.msg_simulation_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.desc_navigate_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier
            .fillMaxSize()
            .then(sharedContainerMod),
    ) { innerPadding ->
        val startPad = innerPadding.calculateStartPadding(layoutDirection) + 16.dp
        val endPad = innerPadding.calculateEndPadding(layoutDirection) + 16.dp
        val topPad = innerPadding.calculateTopPadding() + 8.dp
        val bottomPad = innerPadding.calculateBottomPadding() + 16.dp

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeSplit = maxWidth >= 640.dp
            val mockupHeight = if (isLandscapeSplit) {
                (maxHeight - topPad - bottomPad).coerceIn(220.dp, 368.dp)
            } else {
                364.dp
            }

            if (isLandscapeSplit) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CapsulePhoneMockup(
                        uiState = uiState,
                        viewportHeight = mockupHeight,
                        onMockupWidthMeasured = onMockupWidthMeasured,
                        onResolveExpandTarget = onResolveExpandTarget,
                        onResolveCollapseTarget = onResolveCollapseTarget,
                        onEvent = onEvent,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeekTo = onSeekTo,
                        onSelectMediaSession = onSelectMediaSession,
                        onOpenAudioOutput = onOpenAudioOutput,
                        onToggleTorch = onToggleTorch,
                        onSetTorchStrength = onSetTorchStrength,
                        onSetStreamVolume = onSetStreamVolume,
                        onCycleRingerMode = onCycleRingerMode,
                        onSetRingerMode = onSetRingerMode,
                        onSetBrightnessPercent = onSetBrightnessPercent,
                        onToggleAutoBrightness = onToggleAutoBrightness,
                        onAnswerCall = onAnswerCall,
                        onDeclineCall = onDeclineCall,
                        onOpenCallApp = onOpenCallApp,
                        onOpenLiveActivityApp = onOpenLiveActivityApp,
                        onTriggerLiveActivityAction = onTriggerLiveActivityAction,
                        onOpenSystemStatusSettings = onOpenSystemStatusSettings,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onOpenHotspotSettings = onOpenHotspotSettings,
                        onLockScreenButton = onLockScreenButton,
                        onOpenNotificationShade = onOpenNotificationShade,
                        onWakeFromLock = onWakeFromLock,
                        onOpenNotification = onOpenNotification,
                        onTriggerNotificationAction = onTriggerNotificationAction,
                        onDismissNotification = onDismissNotification,
                        onOpenMediaApp = onOpenMediaApp,
                        onOpenClock = onOpenClock,
                        modifier = Modifier
                            .weight(0.48f)
                            .padding(start = startPad, top = topPad, bottom = bottomPad),
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = topPad, end = endPad, bottom = bottomPad),
                    ) {
                        SimulationControlsSection(
                            uiState = uiState,
                            onToggleMedia = onToggleMedia,
                            onToggleMultiMedia = onToggleMultiMedia,
                            onPostNotification = onPostNotification,
                            onVolumeUp = onVolumeUp,
                            onVolumeDown = onVolumeDown,
                            onToggleTorch = { onToggleTorch(!uiState.isTorchOn) },
                            onToggleHotspot = onToggleHotspot,
                            onToggleLiveActivity = onToggleLiveActivity,
                            onTriggerBluetooth = onTriggerBluetooth,
                            onTriggerCall = onTriggerCall,
                            onTriggerAlarm = onTriggerAlarm,
                            onToggleCharging = onToggleCharging,
                            onTriggerUnlock = onTriggerUnlock,
                            onResetRing = onResetRing,
                            onSelectPreviewPosition = onSelectPreviewPosition,
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CapsulePhoneMockup(
                        uiState = uiState,
                        viewportHeight = mockupHeight,
                        onMockupWidthMeasured = onMockupWidthMeasured,
                        onResolveExpandTarget = onResolveExpandTarget,
                        onResolveCollapseTarget = onResolveCollapseTarget,
                        onEvent = onEvent,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeekTo = onSeekTo,
                        onSelectMediaSession = onSelectMediaSession,
                        onOpenAudioOutput = onOpenAudioOutput,
                        onToggleTorch = onToggleTorch,
                        onSetTorchStrength = onSetTorchStrength,
                        onSetStreamVolume = onSetStreamVolume,
                        onCycleRingerMode = onCycleRingerMode,
                        onSetRingerMode = onSetRingerMode,
                        onSetBrightnessPercent = onSetBrightnessPercent,
                        onToggleAutoBrightness = onToggleAutoBrightness,
                        onAnswerCall = onAnswerCall,
                        onDeclineCall = onDeclineCall,
                        onOpenCallApp = onOpenCallApp,
                        onOpenLiveActivityApp = onOpenLiveActivityApp,
                        onTriggerLiveActivityAction = onTriggerLiveActivityAction,
                        onOpenSystemStatusSettings = onOpenSystemStatusSettings,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onOpenHotspotSettings = onOpenHotspotSettings,
                        onLockScreenButton = onLockScreenButton,
                        onOpenNotificationShade = onOpenNotificationShade,
                        onWakeFromLock = onWakeFromLock,
                        onOpenNotification = onOpenNotification,
                        onTriggerNotificationAction = onTriggerNotificationAction,
                        onDismissNotification = onDismissNotification,
                        onOpenMediaApp = onOpenMediaApp,
                        onOpenClock = onOpenClock,
                        modifier = Modifier
                            .widthIn(max = 1040.dp)
                            .fillMaxWidth()
                            .padding(start = startPad, end = endPad, top = topPad),
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = startPad, end = endPad, top = 12.dp, bottom = bottomPad),
                    ) {
                        SimulationControlsSection(
                            uiState = uiState,
                            onToggleMedia = onToggleMedia,
                            onToggleMultiMedia = onToggleMultiMedia,
                            onPostNotification = onPostNotification,
                            onVolumeUp = onVolumeUp,
                            onVolumeDown = onVolumeDown,
                            onToggleTorch = { onToggleTorch(!uiState.isTorchOn) },
                            onToggleHotspot = onToggleHotspot,
                            onToggleLiveActivity = onToggleLiveActivity,
                            onTriggerBluetooth = onTriggerBluetooth,
                            onTriggerCall = onTriggerCall,
                            onTriggerAlarm = onTriggerAlarm,
                            onToggleCharging = onToggleCharging,
                            onTriggerUnlock = onTriggerUnlock,
                            onResetRing = onResetRing,
                            onSelectPreviewPosition = onSelectPreviewPosition,
                            modifier = Modifier
                                .widthIn(max = 1040.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
