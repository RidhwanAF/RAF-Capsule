package com.raf.capsuleos.presentation.simulation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.capsuleos.domain.model.CapsuleEvent

/**
 * Stateful Navigation 3 Simulation destination binding [SimulationViewModel].
 */
@Composable
fun SimulationScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: SimulationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SimulationContent(
        uiState = uiState,
        onBack = onBack,
        onMockupWidthMeasured = viewModel::syncMockupWidthDp,
        onResolveExpandTarget = viewModel::resolveExpandTarget,
        onResolveCollapseTarget = viewModel::resolveCollapseTarget,
        onEvent = viewModel::dispatchCoordinatorEvent,
        onToggleMedia = viewModel::toggleMediaPlaying,
        onPostNotification = viewModel::triggerMockNotification,
        onVolumeUp = { viewModel.stepVolumeEvent(1) },
        onVolumeDown = { viewModel.stepVolumeEvent(-1) },
        onToggleTorch = viewModel::setTorchEnabled,
        onSetTorchStrength = viewModel::setTorchStrength,
        onSetStreamVolume = viewModel::setStreamVolume,
        onCycleRingerMode = viewModel::cycleRingerMode,
        onSetRingerMode = viewModel::setRingerMode,
        onSetBrightnessPercent = viewModel::setBrightnessPercent,
        onToggleAutoBrightness = viewModel::toggleAutoBrightness,
        onTriggerAlarm = viewModel::triggerMockAlarm,
        onToggleCharging = viewModel::toggleChargingSimulation,
        onTriggerUnlock = viewModel::triggerMockUnlock,
        onResetRing = viewModel::resetToIdleRing,
        onSelectPreviewPosition = { pos ->
            viewModel.updateSandboxPreferences { it.copy(cameraPosition = pos) }
        },
        onTogglePlayPause = viewModel::toggleMediaPlaying,
        onSkipNext = { viewModel.skipTrack(1) },
        onSkipPrevious = { viewModel.skipTrack(-1) },
        onSeekTo = viewModel::seekMediaTo,
        onLockScreenButton = { viewModel.triggerSandboxLockScreen("Now Brief Lock button") },
        onDoubleTapLock = { viewModel.triggerSandboxLockScreen("Double-tap gesture") },
        onWakeFromLock = viewModel::wakeFromSandboxLock,
        onOpenNotification = { alert ->
            viewModel.recordSandboxAction("Sandbox Open Notification (${alert.appName})")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onTriggerNotificationAction = viewModel::handleSandboxNotificationAction,
        onDismissNotification = viewModel::dismissSandboxNotification,
        onOpenMediaApp = {
            viewModel.recordSandboxAction("Sandbox Open Media App")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onOpenClock = {
            viewModel.recordSandboxAction("Sandbox Open Clock")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        modifier = modifier,
    )
}
