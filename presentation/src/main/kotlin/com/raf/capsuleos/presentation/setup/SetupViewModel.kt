package com.raf.capsuleos.presentation.setup

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.repository.AudioVisualizerRepository
import com.raf.capsuleos.domain.repository.BluetoothRepository
import com.raf.capsuleos.domain.repository.BrightnessRepository
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import com.raf.capsuleos.domain.repository.StatusBarIconController
import com.raf.capsuleos.domain.repository.SystemEventsRepository
import com.raf.capsuleos.domain.repository.VolumeRepository
import com.raf.capsuleos.presentation.capsule.service.CapsuleOverlayController
import com.raf.capsuleos.presentation.capsule.service.CapsuleTestAction
import com.raf.capsuleos.presentation.capsule.service.CutoutGeometryResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** One-shot UI feedback emitted from [SetupViewModel]. */
sealed interface SetupFeedback {
    data class CutoutDetected(
        val position: CameraAnchorPosition,
        val holeWidthDp: Int,
        val hasHardwareCutout: Boolean,
    ) : SetupFeedback
}

/**
 * Coordinates accessibility overlay status, permissions, initial/manual camera cutout detection,
 * tuning preferences, and live quick-test triggers.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: CapsuleSettingsRepository,
    private val statusBarIconController: StatusBarIconController,
    private val brightnessRepository: BrightnessRepository,
    private val volumeRepository: VolumeRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val systemEventsRepository: SystemEventsRepository,
    private val audioVisualizerRepository: AudioVisualizerRepository,
    private val overlayController: CapsuleOverlayController,
) : ViewModel() {

    private val permissionRefreshTrigger = MutableStateFlow(0)
    private val detectedState = MutableStateFlow(CutoutGeometryResolver.detect(context))
    private val feedbackChannel = Channel<SetupFeedback>(Channel.BUFFERED)

    /** One-shot user feedback events (e.g. hardware camera auto-detect results). */
    val feedback: Flow<SetupFeedback> = feedbackChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            overlayController.ensureInitialCameraDetected(context)
            detectedState.value = CutoutGeometryResolver.detect(context)
        }
    }

    /** Reactive dashboard state combining permissions, overlay state, cutout geometry, and preferences. */
    val uiState: StateFlow<SetupUiState> = combine(
        combine(
            permissionRefreshTrigger,
            overlayController.isAccessibilityActive,
            overlayController.isUserPaused,
        ) { _, a11yActive, paused -> a11yActive to paused },
        settingsRepository.observePreferences(),
        overlayController.coordinator.uiState.map { it.cutout }.distinctUntilChanged(),
        detectedState,
    ) { (a11yActive, paused), prefs, liveCutout, initialDetect ->
        val effectiveCutout = if (initialDetect.hasHardwareCutout) liveCutout else initialDetect.geometry
        SetupUiState(
            hasAccessibilityOverlay = a11yActive,
            hasNotificationListener = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName),
            hasWriteSecureSettings = statusBarIconController.hasSecureSettingsPermission(),
            hasWriteSettings = brightnessRepository.currentSnapshot().canWrite,
            hasDndPolicyAccess = volumeRepository.canChangeSilentMode(),
            hasBluetoothPermission = bluetoothRepository.hasBluetoothPermission(),
            hasLocationPermission = systemEventsRepository.hasLocationPermission(),
            hasAudioVisualizerPermission = audioVisualizerRepository.hasAudioPermission(),
            isUserPaused = paused,
            cutoutGeometry = effectiveCutout,
            detectedPosition = initialDetect.detectedPosition,
            hasHardwareCutout = initialDetect.hasHardwareCutout,
            preferences = prefs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SetupUiState(),
    )

    /** Re-evaluates all system permission checks after returning from Settings. */
    fun refreshPermissions() {
        permissionRefreshTrigger.value += 1
        if (systemEventsRepository.hasLocationPermission()) {
            systemEventsRepository.refreshWeatherNow()
        }
    }

    /** Updates persisted cutout tuning and marks manual calibration active so initial detect stays overridden. */
    fun updatePreferences(transform: (CapsulePreferences) -> CapsulePreferences) {
        viewModelScope.launch {
            settingsRepository.updatePreferences { current ->
                transform(current).copy(hasAutoDetectedCamera = true)
            }
        }
    }

    /** Explicitly detects physical camera cutout bounds from the host window/display and aligns the capsule. */
    fun autoDetectCameraPosition(hostContext: Context = context) {
        viewModelScope.launch {
            val measurement = overlayController.autoDetectCameraPosition(hostContext)
            detectedState.value = measurement
            feedbackChannel.trySend(
                SetupFeedback.CutoutDetected(
                    position = measurement.detectedPosition,
                    holeWidthDp = measurement.geometry.holeWidthDp.roundToInt(),
                    hasHardwareCutout = measurement.hasHardwareCutout,
                ),
            )
        }
    }

    /** Pauses or resumes the active Accessibility overlay capsule. */
    fun toggleUserPause() {
        overlayController.toggleUserPause()
    }

    /** Triggers a synthetic test action directly on the overlay controller. */
    fun triggerTestAction(action: CapsuleTestAction) {
        overlayController.dispatchTestAction(action)
    }
}
