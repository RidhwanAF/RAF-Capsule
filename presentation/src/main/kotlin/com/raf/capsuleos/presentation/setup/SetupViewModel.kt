package com.raf.capsuleos.presentation.setup

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.repository.BrightnessRepository
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import com.raf.capsuleos.domain.repository.StatusBarIconController
import com.raf.capsuleos.domain.repository.VolumeRepository
import com.raf.capsuleos.presentation.capsule.service.CapsuleOverlayController
import com.raf.capsuleos.presentation.capsule.service.CapsuleTestAction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates accessibility overlay status, notification access, ADB status bar permissions,
 * and camera cutout ring tuning.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: CapsuleSettingsRepository,
    private val statusBarIconController: StatusBarIconController,
    private val brightnessRepository: BrightnessRepository,
    private val volumeRepository: VolumeRepository,
    private val overlayController: CapsuleOverlayController,
) : ViewModel() {

    private val permissionRefreshTrigger = MutableStateFlow(0)

    /** Reactive dashboard state combining permissions, accessibility overlay state, and preferences. */
    val uiState: StateFlow<SetupUiState> = combine(
        permissionRefreshTrigger,
        overlayController.isAccessibilityActive,
        overlayController.isUserPaused,
        settingsRepository.observePreferences(),
    ) { _, a11yActive, paused, prefs ->
        SetupUiState(
            hasAccessibilityOverlay = a11yActive,
            hasNotificationListener = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName),
            hasWriteSecureSettings = statusBarIconController.hasSecureSettingsPermission(),
            hasWriteSettings = brightnessRepository.currentSnapshot().canWrite,
            hasDndPolicyAccess = volumeRepository.canChangeSilentMode(),
            isUserPaused = paused,
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
    }

    /** Updates persisted cutout tuning and auto-collapse behavior. */
    fun updatePreferences(transform: (CapsulePreferences) -> CapsulePreferences) {
        viewModelScope.launch {
            settingsRepository.updatePreferences(transform)
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
