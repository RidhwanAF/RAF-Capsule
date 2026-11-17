package com.raf.capsuleos.presentation.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.simulation.components.CapsulePhoneMockup
import com.raf.capsuleos.presentation.simulation.components.SimulationControlsSection

/**
 * Stateless Navigation 3 Simulation layout pinning [CapsulePhoneMockup] at the top so vertical
 * drag-to-expand gestures never conflict with scrolling the trigger panels beneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationContent(
    modifier: Modifier = Modifier,
    uiState: SimulationUiState,
    onBack: () -> Unit,
    onMockupWidthMeasured: (Float) -> Unit,
    onResolveExpandTarget: () -> CapsuleContent,
    onResolveCollapseTarget: () -> Pair<CapsuleVisualState, CapsuleContent>,
    onEvent: (CapsuleEvent) -> Unit,
    onToggleMedia: () -> Unit,
    onPostNotification: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
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
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onLockScreenButton: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onWakeFromLock: () -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onOpenMediaApp: () -> Unit,
    onOpenClock: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

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
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Pinned interactive sandbox viewport so vertical capsule scrub never competes with scrolling
            CapsulePhoneMockup(
                uiState = uiState,
                onMockupWidthMeasured = onMockupWidthMeasured,
                onResolveExpandTarget = onResolveExpandTarget,
                onResolveCollapseTarget = onResolveCollapseTarget,
                onEvent = onEvent,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeekTo = onSeekTo,
                onToggleTorch = onToggleTorch,
                onSetTorchStrength = onSetTorchStrength,
                onSetStreamVolume = onSetStreamVolume,
                onCycleRingerMode = onCycleRingerMode,
                onSetRingerMode = onSetRingerMode,
                onSetBrightnessPercent = onSetBrightnessPercent,
                onToggleAutoBrightness = onToggleAutoBrightness,
                onLockScreenButton = onLockScreenButton,
                onDoubleTapLock = onDoubleTapLock,
                onWakeFromLock = onWakeFromLock,
                onOpenNotification = onOpenNotification,
                onTriggerNotificationAction = onTriggerNotificationAction,
                onDismissNotification = onDismissNotification,
                onOpenMediaApp = onOpenMediaApp,
                onOpenClock = onOpenClock,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SimulationControlsSection(
                    uiState = uiState,
                    onToggleMedia = onToggleMedia,
                    onPostNotification = onPostNotification,
                    onVolumeUp = onVolumeUp,
                    onVolumeDown = onVolumeDown,
                    onToggleTorch = { onToggleTorch(!uiState.isTorchOn) },
                    onTriggerAlarm = onTriggerAlarm,
                    onToggleCharging = onToggleCharging,
                    onTriggerUnlock = onTriggerUnlock,
                    onResetRing = onResetRing,
                    onSelectPreviewPosition = onSelectPreviewPosition,
                )
            }
        }
    }
}
