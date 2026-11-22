package com.raf.capsuleos.presentation.simulation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raf.capsuleos.domain.coordinator.IslandStateCoordinator
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.BluetoothDeviceCategory
import com.raf.capsuleos.domain.model.BrightnessInfo
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEffect
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.HorizontalMediaGlowStyle
import com.raf.capsuleos.domain.model.LrcLyricsParser
import com.raf.capsuleos.domain.model.MediaOutputKind
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.StreamVolumeInfo
import com.raf.capsuleos.domain.model.UnlockMethod
import com.raf.capsuleos.domain.model.WeatherConditionKind
import com.raf.capsuleos.domain.model.WeatherSummary
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import com.raf.capsuleos.presentation.capsule.service.CutoutGeometryResolver
import com.raf.capsuleos.presentation.navigation.CustomizationSection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
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
    private val sandboxLyricsMidnight = LrcLyricsParser.parse(
        """
        [00:00.00]Waiting in a car
        [00:15.00]Waiting for a ride in the dark
        [00:38.00]The night city grows
        [00:40.00]Look at the horizon glow
        [00:42.00]Waiting in a car
        [00:44.00]Waiting for a ride in the dark
        [00:46.00]Drinking in the lounge
        [00:48.00]Following the neon signs
        [00:50.00]Waiting for a word
        [00:52.00]Looking at the milky skyline
        [00:54.00]The city is my church
        [00:56.00]It wraps me in the sparkling twilight
        [01:00.00]Shining through the midnight haze
        """.trimIndent(),
    )

    private var sandboxMedia: CapsuleContent.Media? = CapsuleContent.Media(
        packageName = "com.raf.sandbox.music",
        title = "Midnight City (Sandbox)",
        artist = "M83",
        isPlaying = true,
        positionMs = 40_000L,
        durationMs = 243_000L,
        artworkBytes = null,
        primaryColorArgb = 0xFF6C5CE7.toInt(),
        secondaryColorArgb = 0xFF00CEC9.toInt(),
        sessionTokenKey = "sandbox:music:1",
        audioOutputLabel = "Pixel Buds Pro",
        audioOutputKind = MediaOutputKind.BLUETOOTH,
        lyrics = sandboxLyricsMidnight,
    )
    private var sandboxMediaList: List<CapsuleContent.Media> = listOfNotNull(sandboxMedia)
    private var sandboxHotspot = CapsuleContent.Hotspot(isEnabled = false)
    private var sandboxLiveActivity: CapsuleContent.LiveActivity? = null
    private var sandboxBluetooth: CapsuleContent.BluetoothDevice? = null
    private var sandboxCall: CapsuleContent.CallAlert? = null
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
        val previewGeometry = CutoutGeometryResolver.detectForPortraitPreview(context)
        sandboxCoordinator.updateCutoutGeometry(previewGeometry)

        viewModelScope.launch {
            settingsRepository.observePreferences().collect { prefs ->
                sandboxCoordinator.updatePreferences(prefs)
            }
        }

        // Seed sandbox with battery, brightness, volume, and media track
        sandboxCoordinator.onEvent(CapsuleEvent.BatteryUpdated(sandboxBatteryPercent, sandboxCharging))
        sandboxCoordinator.onEvent(CapsuleEvent.BrightnessChanged(sandboxBrightness))
        sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sandboxMediaList))
        sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(sandboxMedia))
        simMetaState.value = simMetaState.value.copy(isMediaPlaying = true)

        viewModelScope.launch {
            sandboxCoordinator.effects.collect { effect ->
                when (effect) {
                    CapsuleEffect.LockScreen -> triggerSandboxLockScreen("Configured gesture")
                    CapsuleEffect.OpenNotificationShade -> recordSandboxAction("Sandbox Open Notification Shade")
                    CapsuleEffect.ToggleTorch -> setTorchEnabled(!sandboxTorch.isEnabled)
                    CapsuleEffect.ToggleMediaPlayPause -> toggleMediaPlaying()
                    is CapsuleEffect.SwitchMediaSession -> {
                        val matched = sandboxMediaList.firstOrNull { it.sessionTokenKey == effect.sessionTokenKey }
                        if (matched != null) {
                            sandboxMedia = matched
                            recordSandboxAction("Sandbox Switched Media -> ${matched.title}")
                        }
                    }
                    is CapsuleEffect.LaunchApp -> {
                        recordSandboxAction("Sandbox Launch App (${effect.packageName})")
                    }
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                delay(500.milliseconds)
                val updatedList = sandboxMediaList.map { session ->
                    if (session.isPlaying) {
                        val nextPos = if (session.positionMs + 500L >= session.durationMs) 0L else session.positionMs + 500L
                        session.copy(positionMs = nextPos)
                    } else {
                        session
                    }
                }
                if (updatedList != sandboxMediaList) {
                    sandboxMediaList = updatedList
                    val active = updatedList.firstOrNull { it.sessionTokenKey == sandboxMedia?.sessionTokenKey }
                        ?: updatedList.firstOrNull { it.isPlaying }
                        ?: updatedList.firstOrNull()
                    sandboxMedia = active
                    sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(updatedList))
                    sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(active))
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

    fun toggleMediaPlaying(tokenKey: String? = null) {
        val targetKey = tokenKey?.takeIf { it.isNotBlank() }
        val existing = targetKey?.let { key -> sandboxMediaList.firstOrNull { it.sessionTokenKey == key } }
            ?: sandboxMedia
        if (existing == null) {
            val fresh = CapsuleContent.Media(
                packageName = "com.raf.sandbox.music",
                title = "Midnight City (Sandbox)",
                artist = "M83",
                isPlaying = true,
                positionMs = 40_000L,
                durationMs = 243_000L,
                artworkBytes = null,
                primaryColorArgb = 0xFF6C5CE7.toInt(),
                secondaryColorArgb = 0xFF00CEC9.toInt(),
                sessionTokenKey = "sandbox:music:1",
                audioOutputLabel = "Pixel Buds Pro",
                audioOutputKind = MediaOutputKind.BLUETOOTH,
                lyrics = sandboxLyricsMidnight,
            )
            sandboxMedia = fresh
            sandboxMediaList = listOf(fresh)
            sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sandboxMediaList))
            sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(fresh))
            simMetaState.value = simMetaState.value.copy(
                isMediaPlaying = true,
                latestLog = "Sandbox Media started (real audio untouched)",
            )
        } else {
            val toggled = existing.copy(isPlaying = !existing.isPlaying)
            sandboxMedia = toggled
            sandboxMediaList = sandboxMediaList.map { if (it.sessionTokenKey == toggled.sessionTokenKey) toggled else it }
            sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sandboxMediaList))
            sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(toggled))
            simMetaState.value = simMetaState.value.copy(
                isMediaPlaying = toggled.isPlaying,
                latestLog = "Sandbox Media -> ${if (toggled.isPlaying) "Playing" else "Paused"} (${toggled.title})",
            )
        }
    }

    fun toggleMultiMediaSimulation() {
        val second = CapsuleContent.Media(
            packageName = "com.raf.sandbox.youtube",
            title = "Levitating (Live Studio)",
            artist = "Dua Lipa",
            isPlaying = false,
            positionMs = 64_000L,
            durationMs = 203_000L,
            artworkBytes = null,
            primaryColorArgb = 0xFFE84393.toInt(),
            secondaryColorArgb = 0xFFFD79A8.toInt(),
            sessionTokenKey = "sandbox:video:2",
            audioOutputLabel = "Speaker",
            audioOutputKind = MediaOutputKind.SPEAKER,
        )
        if (sandboxMediaList.size > 1) {
            sandboxMediaList = listOfNotNull(sandboxMediaList.firstOrNull())
            sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sandboxMediaList))
            recordSandboxAction("Sandbox Multi-Media -> 1 player active")
        } else {
            val first = sandboxMedia ?: CapsuleContent.Media(
                packageName = "com.raf.sandbox.music",
                title = "Midnight City (Sandbox)",
                artist = "M83",
                isPlaying = true,
                positionMs = 42_000L,
                durationMs = 243_000L,
                artworkBytes = null,
                primaryColorArgb = 0xFF6C5CE7.toInt(),
                secondaryColorArgb = 0xFF00CEC9.toInt(),
                sessionTokenKey = "sandbox:music:1",
                audioOutputLabel = "Pixel Buds Pro",
                audioOutputKind = MediaOutputKind.BLUETOOTH,
                lyrics = sandboxLyricsMidnight,
            )
            sandboxMedia = first
            sandboxMediaList = listOf(first, second)
            sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sandboxMediaList))
            sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(first))
            recordSandboxAction("Sandbox Multi-Media -> 2 players active (swipe horizontally on expanded card)")
        }
    }

    fun selectMediaSession(index: Int) {
        sandboxCoordinator.onEvent(CapsuleEvent.SelectMediaSession(index))
    }

    fun skipTrack(delta: Int, tokenKey: String? = null) {
        val tracks = listOf(
            "Midnight City (Sandbox)" to "M83",
            "Resonance (Sandbox)" to "HOME",
            "Starlight (Sandbox)" to "Muse",
        )
        val current = tokenKey?.takeIf { it.isNotBlank() }?.let { key ->
            sandboxMediaList.firstOrNull { it.sessionTokenKey == key }
        } ?: sandboxMedia ?: return
        val idx = tracks.indexOfFirst { it.first == current.title }.coerceAtLeast(0)
        val nextIdx = Math.floorMod(idx + delta, tracks.size)
        val (title, artist) = tracks[nextIdx]
        val updated = current.copy(title = title, artist = artist, positionMs = 0L, isPlaying = true)
        sandboxMedia = updated
        sandboxMediaList = sandboxMediaList.map { if (it.sessionTokenKey == updated.sessionTokenKey) updated else it }
        sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sandboxMediaList))
        sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(updated))
        simMetaState.value = simMetaState.value.copy(
            isMediaPlaying = true,
            latestLog = "Sandbox Skipped to $title — $artist",
        )
    }

    fun seekMediaTo(positionMs: Long, tokenKey: String? = null) {
        val current = tokenKey?.takeIf { it.isNotBlank() }?.let { key ->
            sandboxMediaList.firstOrNull { it.sessionTokenKey == key }
        } ?: sandboxMedia ?: return
        val updated = current.copy(positionMs = positionMs.coerceIn(0L, current.durationMs))
        sandboxMedia = updated
        sandboxMediaList = sandboxMediaList.map { if (it.sessionTokenKey == updated.sessionTokenKey) updated else it }
        sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sandboxMediaList))
        sandboxCoordinator.onEvent(CapsuleEvent.MediaUpdated(updated))
    }

    fun triggerMockNotification() {
        notifCounter += 1
        sandboxNotificationCount += 1
        val samples = listOf(
            Triple(
                "WhatsApp",
                "Alex",
                "Meeting moved to 14:30 — join when ready!\n\nAgenda items:\n1. Review capsule spring choreography & gesture polish\n2. Verify synced lyrics and scrollable notification body\n3. Check inline notification image, GIF, and sticker rendering\n4. Test lock screen battery ring behavior on unlock\n5. Confirm right/left separated status bubble placement\n6. Validate scrollable top and bottom gradient indicators\n7. Final sign-off before release deployment tonight.",
            ),
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
        sandboxCoordinator.onEvent(CapsuleEvent.ActiveNotificationCountUpdated(sandboxNotificationCount))
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
        sandboxCoordinator.onEvent(CapsuleEvent.ActiveNotificationCountUpdated(sandboxNotificationCount))
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
                    unlockMethod = UnlockMethod.GENERIC,
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
        sandboxMediaList = emptyList()
        sandboxTorch = sandboxTorch.copy(isEnabled = false)
        sandboxHotspot = CapsuleContent.Hotspot(isEnabled = false)
        sandboxLiveActivity = null
        sandboxBluetooth = null
        sandboxCall = null
        sandboxCharging = false
        sandboxNotificationCount = 0
        sandboxCoordinator.onEvent(CapsuleEvent.ActiveNotificationCountUpdated(0))
        sandboxCoordinator.onEvent(CapsuleEvent.CallUpdated(null))
        sandboxCoordinator.onEvent(CapsuleEvent.BluetoothDeviceUpdated(null))
        sandboxCoordinator.onEvent(CapsuleEvent.LiveActivityUpdated(null))
        sandboxCoordinator.onEvent(CapsuleEvent.HotspotUpdated(sandboxHotspot))
        sandboxCoordinator.onEvent(CapsuleEvent.TorchUpdated(sandboxTorch))
        sandboxCoordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(emptyList()))
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

    fun triggerMockBluetooth() {
        val next = if (sandboxBluetooth == null) {
            CapsuleContent.BluetoothDevice(
                name = "Pixel Buds Pro",
                address = "00:11:22:33:44:55",
                batteryPercent = 84,
                category = BluetoothDeviceCategory.HEADPHONES,
                isConnected = true,
            )
        } else {
            null
        }
        sandboxBluetooth = next
        sandboxCoordinator.onEvent(CapsuleEvent.BluetoothDeviceUpdated(next))
        recordSandboxAction(
            if (next != null) "Sandbox Bluetooth connected: ${next.name} (${next.batteryPercent}%)"
            else "Sandbox Bluetooth disconnected",
        )
    }

    fun toggleMockHotspot() {
        val next = !sandboxHotspot.isEnabled
        sandboxHotspot = CapsuleContent.Hotspot(
            isEnabled = next,
            connectedClients = if (next) 2 else 0,
            ssid = "Raf Pixel Hotspot",
        )
        sandboxCoordinator.onEvent(CapsuleEvent.HotspotUpdated(sandboxHotspot))
        recordSandboxAction("Sandbox Hotspot -> ${if (next) "ON (Left Satellite)" else "OFF"}")
    }

    fun toggleMockLiveActivity() {
        val next = if (sandboxLiveActivity == null) {
            CapsuleContent.LiveActivity(
                key = "sandbox:live:timer",
                title = "Kitchen Timer",
                statusText = "04:12",
                subtitle = "Pasta timer • Running in background with live action controls",
                packageName = context.packageName,
                showChronometer = true,
                chronometerBaseEpochMs = System.currentTimeMillis() + 252_000L,
                isCountdown = true,
                actions = listOf(
                    CapsuleContent.NotificationActionItem(actionId = 0, label = "Pause"),
                    CapsuleContent.NotificationActionItem(actionId = 1, label = "Stop"),
                ),
                progress = 68,
                progressMax = 100,
            )
        } else {
            null
        }
        sandboxLiveActivity = next
        sandboxCoordinator.onEvent(CapsuleEvent.LiveActivityUpdated(next))
        recordSandboxAction(
            if (next != null) "Sandbox Live Activity -> ${next.title} (${next.statusText})"
            else "Sandbox Live Activity cleared",
        )
    }

    fun handleSandboxLiveActivityAction(live: CapsuleContent.LiveActivity, actionId: Int) {
        val label = live.actions.firstOrNull { it.actionId == actionId }?.label ?: "Action"
        if (actionId == 1) {
            sandboxLiveActivity = null
            sandboxCoordinator.onEvent(CapsuleEvent.LiveActivityUpdated(null))
        }
        sandboxCoordinator.onEvent(CapsuleEvent.CollapseCapsule)
        recordSandboxAction("Sandbox Live Activity [$label] triggered")
    }

    fun triggerMockCall() {
        val next = if (sandboxCall == null) {
            CapsuleContent.CallAlert(
                key = "sandbox:call:1",
                callerName = "Alex Mercer",
                callStatus = "Incoming call • HD Voice",
                packageName = "com.google.android.dialer",
                appName = "Phone",
                isIncoming = true,
                hasAnswerAction = true,
                hasDeclineAction = true,
                isOngoing = false,
            )
        } else {
            null
        }
        sandboxCall = next
        sandboxCoordinator.onEvent(CapsuleEvent.CallUpdated(next))
        recordSandboxAction(if (next != null) "Sandbox Incoming Call -> ${next.callerName}" else "Sandbox Call ended")
    }

    fun answerMockCall() {
        val current = sandboxCall ?: return
        val ongoing = current.copy(
            callStatus = "00:08 • Ongoing",
            isIncoming = false,
            hasAnswerAction = false,
            isOngoing = true,
        )
        sandboxCall = ongoing
        sandboxCoordinator.onEvent(CapsuleEvent.CallUpdated(ongoing))
        recordSandboxAction("Sandbox Call Answered (${ongoing.callerName})")
    }

    fun declineMockCall() {
        sandboxCall = null
        sandboxCoordinator.onEvent(CapsuleEvent.CallUpdated(null))
        recordSandboxAction("Sandbox Call Declined / Ended")
    }

    fun recordSandboxAction(message: String) {
        simMetaState.value = simMetaState.value.copy(latestLog = message)
    }

    private var customizationQsPreviewJob: Job? = null

    /**
     * Synchronizes the live preview in [com.raf.capsuleos.presentation.customization.CustomizationScreen]
     * with adaptive initial states (ring-only for Camera Position/Gestures, expanded for Behavior &
     * expanded cards) and smooth live transitions matching the specific edited item.
     */
    fun syncCustomizationSectionPreview(
        section: CustomizationSection,
        preferences: CapsulePreferences,
        previousPreferences: CapsulePreferences?,
    ) {
        sandboxCoordinator.updatePreferences(preferences)
        if (previousPreferences == null) {
            when (section) {
                CustomizationSection.CAMERA_POSITION -> {
                    // Camera padding & anchor screen starts cleanly on the physical Camera Ring only
                    resetToIdleRing()
                }
                CustomizationSection.BEHAVIOR_LAYOUT -> {
                    // Expanded card preview showcases expanded content padding & collapse behavior upfront
                    sandboxCoordinator.onEvent(CapsuleEvent.ExpandToFull)
                }
                CustomizationSection.INDICATORS_MEDIA -> {
                    setTorchEnabled(true)
                    if (!sandboxHotspot.isEnabled) {
                        toggleMockHotspot()
                    }
                    if (!simMetaState.value.isMediaPlaying) {
                        toggleMediaPlaying()
                    }
                    if (preferences.expandedMediaGlowEffects.isNotEmpty() ||
                        preferences.keepExpandedMediaGlowAlways
                    ) {
                        sandboxCoordinator.onEvent(CapsuleEvent.ExpandToFull)
                    }
                }
                CustomizationSection.GESTURES_ACTIONS -> {
                    // Start on Camera Ring so double-tap / long-hold global gestures can be tested immediately
                    resetToIdleRing()
                }
            }
            return
        }

        val ringOnlySettingChanged =
            preferences.cameraPosition != previousPreferences.cameraPosition ||
                preferences.horizontalOffsetDp != previousPreferences.horizontalOffsetDp ||
                preferences.topOffsetAdjustDp != previousPreferences.topOffsetAdjustDp ||
                preferences.paddingWidthDp != previousPreferences.paddingWidthDp ||
                preferences.showBatteryOnCameraRing != previousPreferences.showBatteryOnCameraRing

        val hideOnQsChanged = preferences.hideOnQsOpen != previousPreferences.hideOnQsOpen
        val bluetoothToggledOn = preferences.enableBluetoothAlerts && !previousPreferences.enableBluetoothAlerts
        val callAutoExpandToggledOn = preferences.enableCallAutoExpand && !previousPreferences.enableCallAutoExpand
        val satelliteChanged =
            preferences.enableLeftSatelliteBubble != previousPreferences.enableLeftSatelliteBubble ||
                preferences.satellitePosition != previousPreferences.satellitePosition ||
                preferences.showTorchInSatellite != previousPreferences.showTorchInSatellite ||
                preferences.showHotspotInSatellite != previousPreferences.showHotspotInSatellite ||
                preferences.showLiveActivityInSatellite != previousPreferences.showLiveActivityInSatellite ||
                preferences.showAirplaneInSatellite != previousPreferences.showAirplaneInSatellite ||
                preferences.showDndInSatellite != previousPreferences.showDndInSatellite ||
                preferences.showBatterySaverInSatellite != previousPreferences.showBatterySaverInSatellite ||
                preferences.showVpnInSatellite != previousPreferences.showVpnInSatellite ||
                preferences.showRingerMutedInSatellite != previousPreferences.showRingerMutedInSatellite ||
                preferences.showDataSaverInSatellite != previousPreferences.showDataSaverInSatellite

        val expandedMediaStyle =
            preferences.expandedMediaGlowEffects != previousPreferences.expandedMediaGlowEffects ||
                preferences.mediaAnimationColorMode != previousPreferences.mediaAnimationColorMode ||
                preferences.keepOuterMediaGlowAlways != previousPreferences.keepOuterMediaGlowAlways ||
                preferences.mediaFollowGlobalAutoCollapse != previousPreferences.mediaFollowGlobalAutoCollapse ||
                preferences.mediaAutoCollapseDelayMs != previousPreferences.mediaAutoCollapseDelayMs
        val compactMediaStyle =
            preferences.horizontalMediaGlowStyle != previousPreferences.horizontalMediaGlowStyle &&
                preferences.horizontalMediaGlowStyle != HorizontalMediaGlowStyle.OFF

        val expandedSettingChanged =
            preferences.expandedContentPaddingDp != previousPreferences.expandedContentPaddingDp ||
                preferences.expandedCornerRadiusDp != previousPreferences.expandedCornerRadiusDp ||
                preferences.autoCollapseDelayMs != previousPreferences.autoCollapseDelayMs ||
                preferences.enableNeuronGlow != previousPreferences.enableNeuronGlow ||
                preferences.enableOuterNeuronGlow != previousPreferences.enableOuterNeuronGlow ||
                preferences.keepExpandedMediaGlowAlways != previousPreferences.keepExpandedMediaGlowAlways ||
                preferences.keepOuterMediaGlowAlways != previousPreferences.keepOuterMediaGlowAlways ||
                expandedMediaStyle ||
                callAutoExpandToggledOn

        val compactPillSettingChanged =
            preferences.horizontalAutoCollapseDelayMs != previousPreferences.horizontalAutoCollapseDelayMs ||
                compactMediaStyle ||
                bluetoothToggledOn ||
                satelliteChanged

        val isCurrentlyExpanded =
            sandboxCoordinator.uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED

        when {
            ringOnlySettingChanged -> {
                customizationQsPreviewJob?.cancel()
                sandboxCoordinator.onEvent(CapsuleEvent.QuickSettingsChanged(false))
                resetToIdleRing()
            }
            hideOnQsChanged -> {
                customizationQsPreviewJob?.cancel()
                if (preferences.hideOnQsOpen) {
                    if (!simMetaState.value.isMediaPlaying) {
                        toggleMediaPlaying()
                    }
                    customizationQsPreviewJob = viewModelScope.launch {
                        sandboxCoordinator.onEvent(CapsuleEvent.QuickSettingsChanged(true))
                        delay(1800.milliseconds)
                        sandboxCoordinator.onEvent(CapsuleEvent.QuickSettingsChanged(false))
                    }
                } else {
                    sandboxCoordinator.onEvent(CapsuleEvent.QuickSettingsChanged(false))
                }
            }
            callAutoExpandToggledOn -> {
                if (sandboxCall == null) {
                    triggerMockCall()
                } else {
                    sandboxCoordinator.onEvent(CapsuleEvent.ExpandToFull)
                }
            }
            bluetoothToggledOn -> {
                if (sandboxBluetooth == null) {
                    triggerMockBluetooth()
                } else {
                    sandboxCoordinator.onEvent(
                        CapsuleEvent.BluetoothDeviceUpdated(sandboxBluetooth),
                    )
                }
                if (isCurrentlyExpanded) {
                    sandboxCoordinator.onEvent(CapsuleEvent.CollapseCapsule)
                }
            }
            satelliteChanged -> {
                if (!simMetaState.value.isTorchOn) {
                    setTorchEnabled(true)
                }
                if (!sandboxHotspot.isEnabled) {
                    toggleMockHotspot()
                }
                if (isCurrentlyExpanded) {
                    sandboxCoordinator.onEvent(CapsuleEvent.CollapseCapsule)
                }
            }
            expandedSettingChanged -> {
                if ((preferences.keepExpandedMediaGlowAlways != previousPreferences.keepExpandedMediaGlowAlways ||
                        expandedMediaStyle) &&
                    !simMetaState.value.isMediaPlaying
                ) {
                    toggleMediaPlaying()
                }
                if (!isCurrentlyExpanded) {
                    sandboxCoordinator.onEvent(CapsuleEvent.ExpandToFull)
                } else {
                    sandboxCoordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                }
            }
            compactPillSettingChanged -> {
                if (!simMetaState.value.isMediaPlaying && !bluetoothToggledOn) {
                    toggleMediaPlaying()
                }
                if (isCurrentlyExpanded) {
                    sandboxCoordinator.onEvent(CapsuleEvent.CollapseCapsule)
                }
            }
        }
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
