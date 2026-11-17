package com.raf.capsuleos.presentation.simulation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raf.capsuleos.domain.coordinator.IslandStateCoordinator
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.BrightnessInfo
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.StreamVolumeInfo
import com.raf.capsuleos.domain.model.UnlockMethod
import com.raf.capsuleos.domain.model.WeatherConditionKind
import com.raf.capsuleos.domain.model.WeatherSummary
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import com.raf.capsuleos.presentation.capsule.service.CutoutGeometryResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns a dedicated, completely isolated [IslandStateCoordinator] sandbox so every gesture,
 * expand/collapse animation, slider, notification reply, ringer toggle, and double-tap lock
 * runs identically to the live overlay without modifying device hardware or system state.
 */
@HiltViewModel
class SimulationViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: CapsuleSettingsRepository,
) : ViewModel() {

    private var sandboxBatteryPercent = 86
    private var sandboxCharging = false
    private var sandboxTorch = CapsuleContent.Torch(
        isEnabled = false,
        strengthLevel = 3,
        maxStrengthLevel = 5,
    )
    private var sandboxVolume = CapsuleContent.Volume(
        activeStream = AudioStreamType.MEDIA,
        streams = listOf(
            StreamVolumeInfo(AudioStreamType.MEDIA, 9, 15),
            StreamVolumeInfo(AudioStreamType.RING, 5, 7),
            StreamVolumeInfo(AudioStreamType.ALARM, 6, 7),
        ),
        ringerMode = RingerModeState.NORMAL,
    )
    private var sandboxBrightness = BrightnessInfo(
        levelPercent = 72,
        isAuto = true,
        canWrite = true,
    )
    private var sandboxAlarmLabel: String? = "07:00"
    private var sandboxNotificationCount = 0
    private var sandboxMedia: CapsuleContent.Media? = CapsuleContent.Media(
        packageName = "com.raf.sandbox.music",
        title = "Midnight City (Sandbox)",
        artist = "M83",
        isPlaying = true,
        positionMs = 42_000L,
        durationMs = 243_000L,
        artworkBytes = null,
        primaryColorArgb = 0xFF6C5CE7.toInt(),
        secondaryColorArgb = 0xFF00CEC9.toInt(),
    )
    private var notifCounter = 0

    private val simMetaState = MutableStateFlow(SandboxMeta())

    private val sandboxCoordinator = IslandStateCoordinator(
        scope = viewModelScope,
        nowBriefProvider = {
            CapsuleContent.NowBrief(
                batteryPercent = sandboxBatteryPercent,
                isCharging = sandboxCharging,
                torch = sandboxTorch,
                volume = sandboxVolume,
                nextAlarmLabel = sandboxAlarmLabel,
                liveActivity = null,
                brightness = sandboxBrightness,
                weather = WeatherSummary(
                    temperatureCelsius = 21,
                    conditionLabel = "Clear",
                    conditionKind = WeatherConditionKind.CLEAR,
                ),
                activeNotificationCount = sandboxNotificationCount,
            )
        },
        clockMillis = SystemClock::uptimeMillis,
    )

    /** Combined reactive state backing the Navigation 3 Simulation screen. */
    val uiState: StateFlow<SimulationUiState> = combine(
        sandboxCoordinator.uiState,
        simMetaState,
    ) { capsuleState, meta ->
        SimulationUiState(
            capsuleUiState = capsuleState,
            isMediaPlaying = meta.isMediaPlaying,
            isTorchOn = meta.isTorchOn,
            isCharging = meta.isCharging,
            isScreenLocked = meta.isScreenLocked,
            simulatedBrightnessPercent = meta.brightnessPercent,
            isAutoBrightness = meta.isAutoBrightness,
            latestActionLog = meta.latestLog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SimulationUiState(),
    )

    init {
        val detected = CutoutGeometryResolver.detect(context)
        sandboxCoordinator.updateCutoutGeometry(detected.geometry)

        viewModelScope.launch {
            settingsRepository.observePreferences().collect { prefs ->
                sandboxCoordinator.updatePreferences(prefs)
            }
        }

        // Seed sandbox with battery, brightness, volume, and media track
        sandboxCoordinator.onEvent(CapsuleEvent.BatteryUpdated(sandboxBatteryPercent, sandboxCharging))
        sandboxCoordinator.onEvent(CapsuleEvent.BrightnessChanged(sandboxBrightness))
        sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(sandboxMedia))
        simMetaState.value = simMetaState.value.copy(isMediaPlaying = true)

        viewModelScope.launch {
            while (true) {
                delay(500.milliseconds)
                val active = sandboxMedia
                if (active != null && active.isPlaying) {
                    val nextPos = if (active.positionMs + 500L >= active.durationMs) 0L else active.positionMs + 500L
                    val updated = active.copy(positionMs = nextPos)
                    sandboxMedia = updated
                    sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(updated))
                }
            }
        }
    }

    /** Updates sandbox preview cutout width dynamically to fit the phone mockup frame cleanly. */
    fun syncMockupWidthDp(previewWidthDp: Float) {
        val current = sandboxCoordinator.uiState.value.cutout
        val targetWidth = previewWidthDp.coerceAtLeast(300f)
        if (current.screenWidthDp != targetWidth) {
            sandboxCoordinator.updateCutoutGeometry(current.copy(screenWidthDp = targetWidth))
        }
    }

    fun updateSandboxPreferences(transform: (CapsulePreferences) -> CapsulePreferences) {
        val current = sandboxCoordinator.uiState.value.preferences
        sandboxCoordinator.updatePreferences(transform(current))
    }

    fun dispatchCoordinatorEvent(event: CapsuleEvent) {
        sandboxCoordinator.onEvent(event)
    }

    fun resolveExpandTarget(): CapsuleContent = sandboxCoordinator.resolveExpandTargetContent()

    fun resolveCollapseTarget() = sandboxCoordinator.resolveCollapseTarget()

    fun setTorchEnabled(enabled: Boolean) {
        sandboxTorch = sandboxTorch.copy(isEnabled = enabled)
        simMetaState.value = simMetaState.value.copy(
            isTorchOn = enabled,
            latestLog = "Sandbox Flashlight -> ${if (enabled) "ON (${sandboxTorch.brightnessPercent}%)" else "OFF"} (hardware untouched)",
        )
        sandboxCoordinator.onEvent(CapsuleEvent.TorchUpdated(sandboxTorch))
    }

    fun setTorchStrength(level: Int) {
        val clamped = level.coerceIn(1, sandboxTorch.maxStrengthLevel)
        sandboxTorch = sandboxTorch.copy(isEnabled = true, strengthLevel = clamped)
        simMetaState.value = simMetaState.value.copy(
            isTorchOn = true,
            latestLog = "Sandbox Flashlight strength -> ${sandboxTorch.brightnessPercent}% (hardware untouched)",
        )
        sandboxCoordinator.onEvent(CapsuleEvent.TorchUpdated(sandboxTorch))
    }

    fun setStreamVolume(stream: AudioStreamType, value: Int) {
        val updatedStreams = sandboxVolume.streams.map { info ->
            if (info.streamType == stream) info.copy(current = value.coerceIn(0, info.max)) else info
        }
        sandboxVolume = sandboxVolume.copy(activeStream = stream, streams = updatedStreams)
        simMetaState.value = simMetaState.value.copy(
            latestLog = "Sandbox ${stream.name} volume -> $value (system audio untouched)",
        )
        sandboxCoordinator.onEvent(CapsuleEvent.VolumeChanged(sandboxVolume))
    }

    fun stepVolumeEvent(delta: Int) {
        val media = sandboxVolume.streams.first { it.streamType == AudioStreamType.MEDIA }
        setStreamVolume(AudioStreamType.MEDIA, (media.current + delta).coerceIn(0, media.max))
    }

    fun cycleRingerMode() {
        val next = when (sandboxVolume.ringerMode) {
            RingerModeState.NORMAL -> RingerModeState.VIBRATE
            RingerModeState.VIBRATE -> RingerModeState.SILENT
            RingerModeState.SILENT -> RingerModeState.NORMAL
        }
        setRingerMode(next)
    }

    fun setRingerMode(mode: RingerModeState) {
        sandboxVolume = sandboxVolume.copy(ringerMode = mode)
        simMetaState.value = simMetaState.value.copy(
            latestLog = "Sandbox Ringer -> ${mode.name} (phone ringer untouched)",
        )
        sandboxCoordinator.onEvent(CapsuleEvent.VolumeChanged(sandboxVolume))
    }

    fun setBrightnessPercent(percent: Int) {
        val clamped = percent.coerceIn(1, 100)
        sandboxBrightness = sandboxBrightness.copy(levelPercent = clamped, isAuto = false)
        simMetaState.value = simMetaState.value.copy(
            brightnessPercent = clamped,
            isAutoBrightness = false,
            latestLog = "Sandbox Brightness -> $clamped% (display brightness untouched)",
        )
        sandboxCoordinator.onEvent(CapsuleEvent.BrightnessChanged(sandboxBrightness))
    }

    fun toggleAutoBrightness() {
        val nextAuto = !sandboxBrightness.isAuto
        sandboxBrightness = sandboxBrightness.copy(isAuto = nextAuto)
        simMetaState.value = simMetaState.value.copy(
            isAutoBrightness = nextAuto,
            latestLog = "Sandbox Auto-Brightness -> ${if (nextAuto) "ON" else "OFF"} (system untouched)",
        )
        sandboxCoordinator.onEvent(CapsuleEvent.BrightnessChanged(sandboxBrightness))
    }

    fun toggleMediaPlaying() {
        val existing = sandboxMedia
        if (existing == null) {
            val fresh = CapsuleContent.Media(
                packageName = "com.raf.sandbox.music",
                title = "Midnight City (Sandbox)",
                artist = "M83",
                isPlaying = true,
                positionMs = 15_000L,
                durationMs = 243_000L,
                artworkBytes = null,
                primaryColorArgb = 0xFF6C5CE7.toInt(),
                secondaryColorArgb = 0xFF00CEC9.toInt(),
            )
            sandboxMedia = fresh
            sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(fresh))
            simMetaState.value = simMetaState.value.copy(
                isMediaPlaying = true,
                latestLog = "Sandbox Media started (real audio untouched)",
            )
        } else {
            val toggled = existing.copy(isPlaying = !existing.isPlaying)
            sandboxMedia = toggled
            sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(toggled))
            simMetaState.value = simMetaState.value.copy(
                isMediaPlaying = toggled.isPlaying,
                latestLog = "Sandbox Media -> ${if (toggled.isPlaying) "Playing" else "Paused"}",
            )
        }
    }

    fun skipTrack(delta: Int) {
        val tracks = listOf(
            "Midnight City (Sandbox)" to "M83",
            "Resonance (Sandbox)" to "HOME",
            "Starlight (Sandbox)" to "Muse",
        )
        val current = sandboxMedia ?: return
        val idx = tracks.indexOfFirst { it.first == current.title }.coerceAtLeast(0)
        val nextIdx = Math.floorMod(idx + delta, tracks.size)
        val (title, artist) = tracks[nextIdx]
        val updated = current.copy(title = title, artist = artist, positionMs = 0L, isPlaying = true)
        sandboxMedia = updated
        sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(updated))
        simMetaState.value = simMetaState.value.copy(
            isMediaPlaying = true,
            latestLog = "Sandbox Skipped to $title — $artist",
        )
    }

    fun seekMediaTo(positionMs: Long) {
        val current = sandboxMedia ?: return
        val updated = current.copy(positionMs = positionMs.coerceIn(0L, current.durationMs))
        sandboxMedia = updated
        sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(updated))
    }

    fun triggerMockNotification() {
        notifCounter += 1
        sandboxNotificationCount += 1
        val samples = listOf(
            Triple("WhatsApp", "Alex", "Meeting moved to 14:30 — join when ready!"),
            Triple("Messages", "Sam", "Dinner tonight at 7?"),
            Triple("Slack", "Release CI", "Tag v1.0.0 release artifact verified"),
        )
        val (app, sender, body) = samples[(notifCounter - 1) % samples.size]
        val key = "sandbox|notif|$notifCounter"
        val alert = CapsuleContent.NotificationAlert(
            key = key,
            packageName = "com.raf.sandbox.$app",
            appName = app,
            title = "$sender #$notifCounter",
            text = body,
            iconBytes = null,
            timestampMs = SystemClock.uptimeMillis(),
            actions = listOf(
                CapsuleContent.NotificationActionItem(
                    actionId = 0,
                    label = "Reply",
                    supportsInlineReply = true,
                    replyHint = "Reply in sandbox...",
                ),
                CapsuleContent.NotificationActionItem(
                    actionId = 1,
                    label = "Mark read",
                    supportsInlineReply = false,
                ),
            ),
        )
        sandboxCoordinator.onEvent(CapsuleEvent.NotificationPosted(alert))
        simMetaState.value = simMetaState.value.copy(
            latestLog = "Sandbox Notification #$notifCounter ($app) posted",
        )
    }

    fun handleSandboxNotificationAction(
        alert: CapsuleContent.NotificationAlert,
        actionId: Int,
        replyText: String?,
    ) {
        sandboxCoordinator.onEvent(CapsuleEvent.UserInteractedInCard)
        val label = alert.actions.firstOrNull { it.actionId == actionId }?.label ?: "Action"
        val detail = if (!replyText.isNullOrBlank()) "$label: \"$replyText\"" else label
        simMetaState.value = simMetaState.value.copy(
            latestLog = "Sandbox Notification [$detail] (nothing sent externally)",
        )
        if (replyText != null) {
            sandboxCoordinator.onEvent(CapsuleEvent.CollapseCapsule)
        }
    }

    fun dismissSandboxNotification(alert: CapsuleContent.NotificationAlert) {
        sandboxNotificationCount = (sandboxNotificationCount - 1).coerceAtLeast(0)
        sandboxCoordinator.onEvent(CapsuleEvent.NotificationRemoved(alert.key))
        simMetaState.value = simMetaState.value.copy(
            latestLog = "Sandbox Notification dismissed (${alert.appName})",
        )
    }

    fun triggerMockAlarm() {
        sandboxCoordinator.onEvent(
            CapsuleEvent.AlarmUpdated(
                CapsuleContent.Alarm(
                    triggerTimeMillis = System.currentTimeMillis() + 3_600_000L,
                    formattedTime = sandboxAlarmLabel ?: "07:00",
                    isSet = true,
                ),
            ),
        )
        simMetaState.value = simMetaState.value.copy(latestLog = "Sandbox Alarm pill triggered")
    }

    fun toggleChargingSimulation() {
        sandboxCharging = !sandboxCharging
        sandboxCoordinator.onEvent(CapsuleEvent.BatteryUpdated(sandboxBatteryPercent, sandboxCharging))
        simMetaState.value = simMetaState.value.copy(
            isCharging = sandboxCharging,
            latestLog = "Sandbox Battery -> $sandboxBatteryPercent% (${if (sandboxCharging) "Charging" else "Discharging"})",
        )
    }

    fun triggerMockUnlock() {
        sandboxCoordinator.onEvent(
            CapsuleEvent.DeviceUnlocked(
                CapsuleContent.Unlock(
                    unlockMethod = UnlockMethod.FINGERPRINT,
                    nextAlarmLabel = sandboxAlarmLabel,
                ),
            ),
        )
        simMetaState.value = simMetaState.value.copy(latestLog = "Sandbox Unlock pill triggered")
    }

    fun triggerSandboxLockScreen(reason: String) {
        sandboxCoordinator.onEvent(CapsuleEvent.CollapseCapsule)
        simMetaState.value = simMetaState.value.copy(
            isScreenLocked = true,
            latestLog = "$reason -> Screen Locked in sandbox only",
        )
    }

    fun wakeFromSandboxLock() {
        simMetaState.value = simMetaState.value.copy(
            isScreenLocked = false,
            latestLog = "Sandbox screen woken",
        )
    }

    fun resetToIdleRing() {
        sandboxMedia = null
        sandboxTorch = sandboxTorch.copy(isEnabled = false)
        sandboxCharging = false
        sandboxCoordinator.onEvent(CapsuleEvent.TorchUpdated(sandboxTorch))
        sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(null))
        sandboxCoordinator.onEvent(CapsuleEvent.BatteryUpdated(sandboxBatteryPercent, false))
        sandboxCoordinator.onEvent(CapsuleEvent.CollapseCapsule)
        simMetaState.value = simMetaState.value.copy(
            isMediaPlaying = false,
            isTorchOn = false,
            isCharging = false,
            latestLog = "Sandbox reset to State 1 Battery Ring",
        )
    }

    fun recordSandboxAction(message: String) {
        simMetaState.value = simMetaState.value.copy(latestLog = message)
    }

    private data class SandboxMeta(
        val isMediaPlaying: Boolean = false,
        val isTorchOn: Boolean = false,
        val isCharging: Boolean = false,
        val isScreenLocked: Boolean = false,
        val brightnessPercent: Int = 72,
        val isAutoBrightness: Boolean = true,
        val latestLog: String? = null,
    )
}
