package com.raf.capsuleos.presentation.customization

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.navigation.CustomizationSection
import com.raf.capsuleos.presentation.setup.SetupFeedback
import com.raf.capsuleos.presentation.setup.SetupViewModel
import com.raf.capsuleos.presentation.simulation.SimulationViewModel
import com.raf.capsuleos.presentation.simulation.components.CapsulePhoneMockup

/**
 * Stateful Navigation 3 destination rendering a dedicated screen for a single [CustomizationSection]
 * along with a live real-time interactive [CapsulePhoneMockup] preview that automatically morphs
 * to the expanded or compact state as settings are adjusted.
 */
@Composable
fun CustomizationScreen(
    modifier: Modifier = Modifier,
    section: CustomizationSection,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
    simulationViewModel: SimulationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val simUiState by simulationViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var previousPreferences by remember { mutableStateOf<CapsulePreferences?>(null) }

    LaunchedEffect(section, uiState.preferences) {
        simulationViewModel.syncCustomizationSectionPreview(
            section = section,
            preferences = uiState.preferences,
            previousPreferences = previousPreferences,
        )
        previousPreferences = uiState.preferences
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.feedback.collect { event ->
            when (event) {
                is SetupFeedback.CutoutDetected -> {
                    val msg = if (event.hasHardwareCutout) {
                        val posLabel = resources.getString(
                            when (event.position) {
                                CameraAnchorPosition.LEFT -> R.string.label_anchor_left
                                CameraAnchorPosition.CENTER -> R.string.label_anchor_center
                                CameraAnchorPosition.RIGHT -> R.string.label_anchor_right
                            },
                        )
                        resources.getString(R.string.msg_cutout_detected, posLabel, event.holeWidthDp)
                    } else {
                        resources.getString(R.string.msg_no_cutout_detected)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val isExpandedPreview =
        simUiState.capsuleUiState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
    val previewHeight by animateDpAsState(
        targetValue = if (isExpandedPreview) 326.dp else 184.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f),
        label = "customizationPreviewHeight",
    )

    CustomizationContent(
        section = section,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onAutoDetectCamera = { viewModel.autoDetectCameraPosition(context) },
        onUpdatePreferences = viewModel::updatePreferences,
        livePreviewContent = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val effectivePreviewHeight = if (constraints.hasBoundedHeight) {
                    maxHeight.coerceIn(184.dp, 340.dp)
                } else {
                    previewHeight
                }
                CapsulePhoneMockup(
                    uiState = simUiState,
                    viewportHeight = effectivePreviewHeight,
                    onMockupWidthMeasured = simulationViewModel::syncMockupWidthDp,
                    onResolveExpandTarget = simulationViewModel::resolveExpandTarget,
                    onResolveCollapseTarget = simulationViewModel::resolveCollapseTarget,
                    onEvent = simulationViewModel::dispatchCoordinatorEvent,
                    onTogglePlayPause = simulationViewModel::toggleMediaPlaying,
                    onSkipNext = { key -> simulationViewModel.skipTrack(1, key) },
                    onSkipPrevious = { key -> simulationViewModel.skipTrack(-1, key) },
                    onSeekTo = { key, ms -> simulationViewModel.seekMediaTo(ms, key) },
                    onSelectMediaSession = simulationViewModel::selectMediaSession,
                    onOpenAudioOutput = {
                        simulationViewModel.recordSandboxAction("Sandbox Audio Output Switcher opened")
                    },
                    onToggleTorch = simulationViewModel::setTorchEnabled,
                    onSetTorchStrength = simulationViewModel::setTorchStrength,
                    onSetStreamVolume = simulationViewModel::setStreamVolume,
                    onCycleRingerMode = simulationViewModel::cycleRingerMode,
                    onSetRingerMode = simulationViewModel::setRingerMode,
                    onSetBrightnessPercent = simulationViewModel::setBrightnessPercent,
                    onToggleAutoBrightness = simulationViewModel::toggleAutoBrightness,
                    onAnswerCall = { simulationViewModel.answerMockCall() },
                    onDeclineCall = { simulationViewModel.declineMockCall() },
                    onOpenCallApp = { call ->
                        simulationViewModel.recordSandboxAction("Sandbox Open Call App (${call.callerName})")
                    },
                    onOpenLiveActivityApp = { live ->
                        simulationViewModel.recordSandboxAction("Sandbox Open Live Activity (${live.title})")
                        simulationViewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
                    },
                    onOpenSystemStatusSettings = { kind ->
                        simulationViewModel.recordSandboxAction("Sandbox Open Settings (${kind.name})")
                        simulationViewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
                    },
                    onOpenBluetoothSettings = {
                        simulationViewModel.recordSandboxAction("Sandbox Open Bluetooth Settings")
                    },
                    onOpenHotspotSettings = {
                        simulationViewModel.recordSandboxAction("Sandbox Open Hotspot Settings")
                    },
                    onLockScreenButton = { simulationViewModel.triggerSandboxLockScreen("Now Brief Lock") },
                    onOpenNotificationShade = {
                        simulationViewModel.recordSandboxAction("Sandbox Open Notification Shade")
                        simulationViewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
                    },
                    onWakeFromLock = simulationViewModel::wakeFromSandboxLock,
                    onOpenNotification = { alert ->
                        simulationViewModel.recordSandboxAction("Sandbox Open Notification (${alert.appName})")
                        simulationViewModel.dispatchCoordinatorEvent(CapsuleEvent.CollapseCapsule)
                    },
                    onTriggerNotificationAction = simulationViewModel::handleSandboxNotificationAction,
                    onDismissNotification = simulationViewModel::dismissSandboxNotification,
                    onOpenMediaApp = { simulationViewModel.recordSandboxAction("Sandbox Open Media App") },
                    onOpenClock = { simulationViewModel.recordSandboxAction("Sandbox Open Clock App") },
                )
            }
        },
        modifier = modifier,
    )
}
