package com.raf.capsuleos.presentation.simulation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    viewModel: SimulationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SimulationContent(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onMockupWidthMeasured = viewModel::syncMockupWidthDp,
        onResolveExpandTarget = viewModel::resolveExpandTarget,
        onResolveCollapseTarget = viewModel::resolveCollapseTarget,
        onEvent = viewModel::dispatchCoordinatorEvent,
        onToggleMedia = { viewModel.toggleMediaPlaying() },
        onToggleMultiMedia = viewModel::toggleMultiMediaSimulation,
        onPostNotification = viewModel::triggerMockNotification,
        onVolumeUp = { viewModel.stepVolumeEvent(1) },
        onVolumeDown = { viewModel.stepVolumeEvent(-1) },
        onToggleTorch = viewModel::setTorchEnabled,
        onSetTorchStrength = viewModel::setTorchStrength,
        onToggleHotspot = viewModel::toggleMockHotspot,
        onToggleLiveActivity = viewModel::toggleMockLiveActivity,
        onTriggerBluetooth = viewModel::triggerMockBluetooth,
        onTriggerCall = viewModel::triggerMockCall,
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
        onSkipNext = { key -> viewModel.skipTrack(1, key) },
        onSkipPrevious = { key -> viewModel.skipTrack(-1, key) },
        onSeekTo = { key, ms -> viewModel.seekMediaTo(ms, key) },
        onSelectMediaSession = viewModel::selectMediaSession,
        onOpenAudioOutput = {
            viewModel.recordSandboxAction("Sandbox Audio Output Switcher opened")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onAnswerCall = { viewModel.answerMockCall() },
        onDeclineCall = { viewModel.declineMockCall() },
        onOpenCallApp = { call ->
            viewModel.recordSandboxAction("Sandbox Open Call App (${call.appName})")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onOpenLiveActivityApp = { live ->
            viewModel.recordSandboxAction("Sandbox Open Live Activity (${live.title})")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onTriggerLiveActivityAction = viewModel::handleSandboxLiveActivityAction,
        onOpenSystemStatusSettings = { kind ->
            viewModel.recordSandboxAction("Sandbox Open Settings (${kind.name})")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onOpenBluetoothSettings = {
            viewModel.recordSandboxAction("Sandbox Open Bluetooth Settings")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onOpenHotspotSettings = {
            viewModel.recordSandboxAction("Sandbox Open Hotspot Settings")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
        onLockScreenButton = { viewModel.triggerSandboxLockScreen("Now Brief Lock button") },
        onOpenNotificationShade = {
            viewModel.recordSandboxAction("Sandbox Open Notification Shade")
            viewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
        },
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
