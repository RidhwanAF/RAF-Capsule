package com.raf.capsuleos.presentation.capsule.service

import android.app.Dialog
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.raf.capsuleos.domain.coordinator.IslandStateCoordinator
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEffect
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.domain.repository.BluetoothRepository
import com.raf.capsuleos.domain.repository.BrightnessRepository
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import com.raf.capsuleos.domain.repository.MediaSessionRepository
import com.raf.capsuleos.domain.repository.NotificationRepository
import com.raf.capsuleos.domain.repository.StatusBarIconController
import com.raf.capsuleos.domain.repository.SystemEventsRepository
import com.raf.capsuleos.domain.repository.TorchRepository
import com.raf.capsuleos.domain.repository.VolumeRepository
import com.raf.capsuleos.presentation.capsule.ui.CapsuleScrubPresentation
import com.raf.capsuleos.presentation.capsule.ui.IslandCapsule
import com.raf.capsuleos.presentation.capsule.ui.computeCapsuleLayoutDpSpec
import com.raf.capsuleos.presentation.shader.OuterScreenExpansionAura
import com.raf.capsuleos.presentation.shader.resolveOuterExpansionAuraColors
import com.raf.capsuleos.presentation.shader.shouldShowOuterExpansionAura
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Supported quick-test actions triggered from the setup calibration dashboard. */
enum class CapsuleTestAction {
    VOLUME,
    ALARM,
    NOW_BRIEF,
    NOTIFICATION,
}

/**
 * Shared singleton controller managing the Accessibility-native capsule overlay window.
 * Drives exact `1:1` Window + Card + Blur sizing on vsync using a single damped spring solver
 * anchored directly around the measured device camera cutout across Pixel 6/7/8/9+ devices.
 */
@Singleton
class CapsuleOverlayController @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val mediaSessionRepository: MediaSessionRepository,
    private val notificationRepository: NotificationRepository,
    private val torchRepository: TorchRepository,
    private val volumeRepository: VolumeRepository,
    private val brightnessRepository: BrightnessRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val systemEventsRepository: SystemEventsRepository,
    private val statusBarIconController: StatusBarIconController,
    private val settingsRepository: CapsuleSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val coordinator = IslandStateCoordinator(
        scope = scope,
        nowBriefProvider = {
            val (percent, charging) = systemEventsRepository.currentBatteryState()
            CapsuleContent.NowBrief(
                batteryPercent = percent,
                isCharging = charging,
                torch = torchRepository.currentSnapshot(),
                volume = volumeRepository.currentSnapshot(AudioStreamType.MEDIA),
                nextAlarmLabel = systemEventsRepository.currentNextAlarmLabel(),
                liveActivity = notificationRepository.currentLiveActivity(),
                brightness = brightnessRepository.currentSnapshot(),
                weather = systemEventsRepository.currentWeatherSummary(),
                activeNotificationCount = notificationRepository.currentActiveNotificationCount(),
            )
        },
        clockMillis = SystemClock::uptimeMillis,
    )

    private var activeDialog: Dialog? = null
    private var activeAuraDialog: Dialog? = null
    private var activeContainer: FrameLayout? = null
    private var activeLifecycleOwner: ComposeOverlayLifecycleOwner? = null
    private var activeHostContext: Context? = null
    private var lockScreenAction: (() -> Boolean)? = null
    private var openNotificationShadeAction: (() -> Boolean)? = null
    private var cardBlurDrawable: CapsuleBlurDrawable? = null
    private var windowObserverJob: Job? = null
    private var collapseStageJob: Job? = null
    private var state3EnteredAtMs: Long = 0L
    private var isReplyInputFocused = false

    private var minClampWPx = 100f
    private var minClampHPx = 100f
    private val animatedCornerDpState = mutableFloatStateOf(25f)

    private val widthSpring = SpringAxis()
    private val heightSpring = SpringAxis()
    private val offsetXSpring = SpringAxis()
    private val topYSpring = SpringAxis()
    private val cornerDpSpring = SpringAxis()
    private val blurRadiusSpring = SpringAxis()
    private var lastFrameNanos = 0L
    private var isSpringLoopRunning = false
    private val isHoldingCapsuleState = mutableStateOf(false)
    private val forceCameraCircleStageState = mutableStateOf(false)
    private val suppressSatelliteStageState = mutableStateOf(false)
    private val holdPillBeforeRingState = mutableStateOf<CapsuleUiState?>(null)
    private var skipNextCollapseStaging = false
    private var jiggleUntilMs = 0L
    private val scrubPresentationState = mutableStateOf<CapsuleScrubPresentation?>(null)
    private var activeScrubSession: ScrubSession? = null

    private val _isAccessibilityActive = MutableStateFlow(false)

    /** True when [CapsuleAccessibilityService] is bound and hosting `TYPE_ACCESSIBILITY_OVERLAY`. */
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    private val _isUserPaused = MutableStateFlow(false)

    /** True when the user temporarily paused the capsule overlay from Setup. */
    val isUserPaused: StateFlow<Boolean> = _isUserPaused.asStateFlow()

    private val _isBlurEnabled = MutableStateFlow(windowManager.isCrossWindowBlurEnabled)

    private val blurEnabledListener = Consumer<Boolean> { enabled ->
        _isBlurEnabled.value = enabled
        updateSpringTargets()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                activeHostContext?.let { measureDeviceCutout(it) }
                dispatchEnvironmentVisibility()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            dispatchEnvironmentVisibility()
        }
    }

    private val vsyncCallback = Choreographer.FrameCallback { frameTimeNanos ->
        stepWindowSprings(frameTimeNanos)
    }

    init {
        displayManager.registerDisplayListener(displayListener, null)
        windowManager.addCrossWindowBlurEnabledListener(appContext.mainExecutor, blurEnabledListener)
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        ContextCompat.registerReceiver(appContext, screenReceiver, screenFilter, ContextCompat.RECEIVER_EXPORTED)
        collectDomainStreams()
        collectEffects()
        scope.launch { ensureInitialCameraDetected(appContext) }
    }

    /** Runs hardware cutout detection once on initial setup without overwriting subsequent manual edits. */
    suspend fun ensureInitialCameraDetected(hostContext: Context = appContext) {
        val currentPrefs = settingsRepository.observePreferences().first()
        val measurement = CutoutGeometryResolver.detect(hostContext)
        coordinator.updateCutoutGeometry(measurement.geometry)
        if (!currentPrefs.hasAutoDetectedCamera) {
            settingsRepository.updatePreferences { prefs ->
                if (prefs.hasAutoDetectedCamera) {
                    prefs
                } else {
                    prefs.copy(
                        cameraPosition = measurement.detectedPosition,
                        horizontalOffsetDp = 0f,
                        topOffsetAdjustDp = 0f,
                        hasAutoDetectedCamera = true,
                    )
                }
            }
        }
    }

    /** Explicitly auto-detects physical camera cutout alignment and resets fine-tune offsets. */
    internal suspend fun autoDetectCameraPosition(hostContext: Context = appContext): DetectedCutoutMeasurement {
        val measurement = CutoutGeometryResolver.detect(hostContext)
        coordinator.updateCutoutGeometry(measurement.geometry)
        settingsRepository.updatePreferences { prefs ->
            prefs.copy(
                cameraPosition = measurement.detectedPosition,
                horizontalOffsetDp = 0f,
                topOffsetAdjustDp = 0f,
                hasAutoDetectedCamera = true,
            )
        }
        return measurement
    }

    /** Attaches the capsule using `TYPE_ACCESSIBILITY_OVERLAY` directly over the camera & status bar. */
    fun attachAccessibilityHost(
        serviceContext: Context,
        onLockScreen: () -> Boolean = { false },
        onOpenNotificationShade: () -> Boolean = { false },
    ) {
        _isAccessibilityActive.value = true
        lockScreenAction = onLockScreen
        openNotificationShadeAction = onOpenNotificationShade
        attachOverlay(hostContext = serviceContext)
    }

    /** Detaches the `TYPE_ACCESSIBILITY_OVERLAY` host when the accessibility service unbinds. */
    fun detachAccessibilityHost() {
        _isAccessibilityActive.value = false
        lockScreenAction = null
        openNotificationShadeAction = null
        tearDownCurrentOverlay()
    }

    private fun triggerLockScreen() {
        coordinator.onEvent(CapsuleEvent.CollapseCapsule)
        lockScreenAction?.invoke()
    }

    private fun triggerOpenNotificationShade() {
        coordinator.onEvent(CapsuleEvent.CollapseCapsule)
        openNotificationShadeAction?.invoke()
    }

    /** Dispatches device keyguard lockscreen state from the accessibility host. */
    fun setKeyguardLocked(isLocked: Boolean) {
        coordinator.onEvent(CapsuleEvent.KeyguardLockedChanged(isLocked))
    }

    private fun launchSystemIntent(intent: Intent, onFailure: (() -> Unit)? = null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { onFailure?.invoke() }
    }

    /** Dispatches Quick Settings / notification shade open state from the accessibility service. */
    fun setQuickSettingsOpen(isOpen: Boolean) {
        coordinator.onEvent(CapsuleEvent.QuickSettingsChanged(isOpen))
    }

    /** Toggles temporary pause/resume of the active capsule without unbinding Accessibility. */
    fun toggleUserPause() {
        _isUserPaused.value = !_isUserPaused.value
        dispatchEnvironmentVisibility()
    }

    /** Dispatches a configuration change from the active accessibility host service. */
    fun onConfigurationChanged(hostContext: Context, newConfig: Configuration) {
        measureDeviceCutout(hostContext)
        dispatchEnvironmentVisibility(newConfig)
    }

    /** Dispatches a synthetic test event from the setup dashboard. */
    fun dispatchTestAction(action: CapsuleTestAction) {
        coordinator.onEvent(CapsuleEvent.QuickSettingsChanged(false))
        when (action) {
            CapsuleTestAction.VOLUME -> coordinator.onEvent(
                CapsuleEvent.VolumeChanged(volumeRepository.currentSnapshot(AudioStreamType.MEDIA)),
            )
            CapsuleTestAction.ALARM -> coordinator.onEvent(
                CapsuleEvent.AlarmUpdated(
                    CapsuleContent.Alarm(
                        triggerTimeMillis = System.currentTimeMillis() + 3_600_000L,
                        formattedTime = "07:00",
                        isSet = true,
                    ),
                ),
            )
            CapsuleTestAction.NOW_BRIEF -> coordinator.onEvent(CapsuleEvent.ExpandNowBrief)
            CapsuleTestAction.NOTIFICATION -> coordinator.onEvent(
                CapsuleEvent.NotificationPosted(
                    CapsuleContent.NotificationAlert(
                        key = "test|com.raf.capsuleos|${System.currentTimeMillis()}",
                        packageName = appContext.packageName,
                        appName = "Raf Capsule",
                        title = "New Message — Tap to expand & open",
                        text = "Sample notification preview with frosted vertical blur.\n" +
                            "Long messaging threads automatically expand taller for comfortable reading.\n" +
                            "Scroll down to see the directional primary top & bottom edge indicators appear only when more content is available in that direction.\n" +
                            "Inline photos, GIFs, and stickers from messaging apps also render directly below the message text.\n" +
                            "Tap Open to jump straight into the app or Dismiss to clear.",
                        iconBytes = null,
                        timestampMs = System.currentTimeMillis(),
                    ),
                ),
            )
        }
    }

    private fun attachOverlay(hostContext: Context) {
        tearDownCurrentOverlay()
        activeHostContext = hostContext

        measureDeviceCutout(hostContext)
        dispatchEnvironmentVisibility(hostContext.resources.configuration)

        val lifecycleOwner = ComposeOverlayLifecycleOwner().apply { onCreateAndResume() }
        activeLifecycleOwner = lifecycleOwner

        val blurDrawable = CapsuleBlurDrawable()
        cardBlurDrawable = blurDrawable

        val dialog = Dialog(hostContext, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val composeView = ComposeView(hostContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by coordinator.uiState.collectAsStateWithLifecycle()
                val blurSupported by _isBlurEnabled.collectAsStateWithLifecycle()
                val forceCamera by forceCameraCircleStageState
                val suppressSatellite by suppressSatelliteStageState
                val heldPill by holdPillBeforeRingState
                val effectiveUiState = heldPill ?: state
                MaterialTheme(colorScheme = dynamicDarkColorScheme(hostContext)) {
                    IslandCapsule(
                        uiState = effectiveUiState,
                        cornerRadiusDp = animatedCornerDpState.floatValue,
                        isBlurSupported = blurSupported,
                        scrubPresentation = scrubPresentationState.value,
                        isForceCameraCircleStage = forceCamera,
                        isSatelliteDelayedStage = suppressSatellite,
                        onHoldingStateChanged = { holding ->
                            if (isHoldingCapsuleState.value != holding) {
                                isHoldingCapsuleState.value = holding
                                updateSpringTargets()
                            }
                        },
                        onHoldPeekStart = { coordinator.onEvent(CapsuleEvent.HoldPeekStarted) },
                        onHoldPeekEnd = { coordinator.onEvent(CapsuleEvent.HoldPeekReleased) },
                        onTap = { coordinator.onEvent(CapsuleEvent.TapCapsule) },
                        onExpand = { coordinator.onEvent(CapsuleEvent.ExpandToFull) },
                        onLongHoldGesture = { coordinator.onEvent(CapsuleEvent.LongHoldCapsule) },
                        onDoubleTapGesture = { coordinator.onEvent(CapsuleEvent.DoubleTapCapsule) },
                        onExpandSatellite = {
                            holdPillBeforeRingState.value = null
                            forceCameraCircleStageState.value = false
                            suppressSatelliteStageState.value = false
                            coordinator.onEvent(CapsuleEvent.ExpandLeftSatellite)
                        },
                        onMergeSatelliteIntoMainBeforeCamera = {
                            collapseStageJob?.cancel()
                            collapseStageJob = null
                            holdPillBeforeRingState.value = null
                            forceCameraCircleStageState.value = false
                            suppressSatelliteStageState.value = true
                            updateSpringTargets()
                        },
                        onMergeToCameraBeforeExpand = {
                            collapseStageJob?.cancel()
                            collapseStageJob = null
                            holdPillBeforeRingState.value = null
                            suppressSatelliteStageState.value = false
                            forceCameraCircleStageState.value = true
                            updateSpringTargets()
                        },
                        onCycleSatellite = { direction -> coordinator.onEvent(CapsuleEvent.CycleLeftSatellite(direction)) },
                        onUserInteraction = { coordinator.onEvent(CapsuleEvent.UserInteractedInCard) },
                        onCycleContent = { direction -> coordinator.onEvent(CapsuleEvent.CycleContent(direction)) },
                        onVerticalScrubStart = ::startVerticalScrub,
                        onVerticalScrubProgress = ::updateVerticalScrub,
                        onVerticalScrubEnd = ::endVerticalScrub,
                        onOpenNotification = { alert ->
                            setOverlayKeyboardFocus(false)
                            notificationRepository.openNotificationOrApp(alert.key, alert.packageName)
                            coordinator.onEvent(CapsuleEvent.NotificationRemoved(alert.key))
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                        },
                        onTriggerNotificationAction = { alert, actionId, replyText ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            notificationRepository.executeNotificationAction(alert.key, actionId, replyText)
                            if (replyText != null) {
                                setOverlayKeyboardFocus(false)
                                coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                            }
                        },
                        onDismissNotification = { alert ->
                            setOverlayKeyboardFocus(false)
                            notificationRepository.dismissNotification(alert.key)
                            coordinator.onEvent(CapsuleEvent.NotificationRemoved(alert.key))
                        },
                        onReplyFocusChanged = ::setOverlayKeyboardFocus,
                        onOpenMediaApp = { pkg ->
                            notificationRepository.openNotificationOrApp(null, pkg)
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                        },
                        onSelectMediaSession = { index ->
                            coordinator.onEvent(CapsuleEvent.SelectMediaSession(index))
                        },
                        onOpenAudioOutput = {
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                            val activeMedia = coordinator.uiState.value.content as? CapsuleContent.Media
                            val shown = mediaSessionRepository.showSystemAudioOutputSwitcher(
                                activeMedia?.sessionTokenKey?.ifBlank { null },
                            )
                            if (!shown) {
                                launchSystemIntent(Intent(Settings.Panel.ACTION_VOLUME))
                            }
                        },
                        onTogglePlayPause = { tokenKey ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            mediaSessionRepository.togglePlayPause(tokenKey.ifBlank { null })
                        },
                        onSkipNext = { tokenKey ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            mediaSessionRepository.skipNext(tokenKey.ifBlank { null })
                        },
                        onSkipPrevious = { tokenKey ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            mediaSessionRepository.skipPrevious(tokenKey.ifBlank { null })
                        },
                        onSeekTo = { tokenKey, positionMs ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            mediaSessionRepository.seekTo(positionMs, tokenKey.ifBlank { null })
                        },
                        onToggleTorch = { enabled ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            torchRepository.setTorchEnabled(enabled)
                        },
                        onSetTorchStrength = { level ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            torchRepository.setTorchStrength(level)
                        },
                        onSetStreamVolume = { stream, value ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            volumeRepository.setStreamVolume(stream, value)
                            coordinator.onEvent(
                                CapsuleEvent.VolumeChanged(volumeRepository.currentSnapshot(stream)),
                            )
                        },
                        onCycleRingerMode = {
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            val updatedRinger = volumeRepository.cycleRingerMode()
                            val activeStream = (coordinator.uiState.value.content as? CapsuleContent.Volume)
                                ?.activeStream
                                ?: (coordinator.uiState.value.content as? CapsuleContent.NowBrief)
                                    ?.volume?.activeStream
                                ?: AudioStreamType.MEDIA
                            coordinator.onEvent(
                                CapsuleEvent.VolumeChanged(
                                    volumeRepository.currentSnapshot(activeStream).copy(ringerMode = updatedRinger),
                                ),
                            )
                        },
                        onSetRingerMode = { mode ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            val updatedRinger = volumeRepository.setRingerMode(mode)
                            if (mode == RingerModeState.SILENT && updatedRinger != RingerModeState.SILENT) {
                                coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                            } else {
                                val activeStream = (coordinator.uiState.value.content as? CapsuleContent.Volume)
                                    ?.activeStream
                                    ?: (coordinator.uiState.value.content as? CapsuleContent.NowBrief)
                                        ?.volume?.activeStream
                                    ?: AudioStreamType.RING
                                coordinator.onEvent(
                                    CapsuleEvent.VolumeChanged(
                                        volumeRepository.currentSnapshot(activeStream).copy(ringerMode = updatedRinger),
                                    ),
                                )
                            }
                        },
                        onSetBrightnessPercent = { percent ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            val updated = brightnessRepository.setBrightnessPercent(percent)
                            coordinator.onEvent(CapsuleEvent.BrightnessChanged(updated))
                        },
                        onToggleAutoBrightness = {
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            val updated = brightnessRepository.toggleAutoBrightness()
                            coordinator.onEvent(CapsuleEvent.BrightnessChanged(updated))
                        },
                        onAnswerCall = { call ->
                            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                            notificationRepository.answerCall(call.key)
                        },
                        onDeclineCall = { call ->
                            notificationRepository.declineCall(call.key)
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                        },
                        onOpenCallApp = { call ->
                            notificationRepository.openNotificationOrApp(call.key, call.packageName)
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                        },
                        onOpenLiveActivityApp = { live ->
                            val pkg = live.packageName
                            if (!pkg.isNullOrBlank()) {
                                notificationRepository.openNotificationOrApp(live.key, pkg)
                            }
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                        },
                        onOpenSystemStatusSettings = { kind ->
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                            val primaryIntent = when (kind) {
                                SystemStatusKind.AIRPLANE_MODE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                                SystemStatusKind.DO_NOT_DISTURB -> Intent("android.settings.ZEN_MODE_SETTINGS")
                                SystemStatusKind.BATTERY_SAVER -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                SystemStatusKind.VPN -> Intent(Settings.ACTION_VPN_SETTINGS)
                                SystemStatusKind.RINGER_MUTED -> Intent(Settings.ACTION_SOUND_SETTINGS)
                                SystemStatusKind.DATA_SAVER -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
                            }
                            launchSystemIntent(primaryIntent) {
                                launchSystemIntent(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        onOpenBluetoothSettings = {
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                            launchSystemIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        onOpenHotspotSettings = {
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                            launchSystemIntent(Intent("android.settings.TETHER_SETTINGS")) {
                                launchSystemIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        },
                        onLockScreen = ::triggerLockScreen,
                        onOpenNotificationShade = ::triggerOpenNotificationShade,
                        onOpenClock = {
                            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                            val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { appContext.startActivity(clockIntent) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val container = object : FrameLayout(hostContext) {
            private val gestureExclusionRect = Rect()
            private val gestureExclusionRects = listOf(gestureExclusionRect)

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0) {
                    gestureExclusionRect.set(0, 0, w, h)
                    systemGestureExclusionRects = gestureExclusionRects
                }
            }

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                val isState3 = coordinator.uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
                if (ev.action == MotionEvent.ACTION_OUTSIDE && isState3 && !isReplyInputFocused) {
                    val sinceExpand = System.currentTimeMillis() - state3EnteredAtMs
                    if (sinceExpand > 360L) {
                        coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                    }
                    return true
                }
                if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (isState3) {
                        coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        activeContainer = container

        val auraDialog = Dialog(hostContext, android.R.style.Theme_Translucent_NoTitleBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            val auraComposeView = ComposeView(hostContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val auraState by coordinator.uiState.collectAsStateWithLifecycle()
                    MaterialTheme(colorScheme = dynamicDarkColorScheme(hostContext)) {
                        val (auraColorA, auraColorB) = resolveOuterExpansionAuraColors(
                            state = auraState,
                            primaryColor = MaterialTheme.colorScheme.primary,
                            tertiaryColor = MaterialTheme.colorScheme.tertiary,
                        )
                        val layoutSpec = computeCapsuleLayoutDpSpec(
                            state = auraState,
                            isHoldingCapsule = false,
                        )
                        val outerAuraHeightDp = (layoutSpec.topYDp + layoutSpec.heightDp + 36f)
                            .coerceAtLeast(180f)
                            .dp
                        OuterScreenExpansionAura(
                            visible = shouldShowOuterExpansionAura(auraState),
                            colorA = auraColorA,
                            colorB = auraColorB,
                            screenCornerRadiusDp = auraState.cutout.screenTopCornerRadiusDp.coerceIn(28f, 52f),
                            spreadDp = 72f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(outerAuraHeightDp),
                        )
                    }
                }
            }
            val auraContainer = FrameLayout(hostContext).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                addView(
                    auraComposeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            setContentView(auraContainer)
            window?.let { auraWin ->
                auraWin.decorView.setPadding(0, 0, 0, 0)
                auraWin.setDimAmount(0f)
                auraWin.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val auraHeightPx = (hostContext.resources.displayMetrics.heightPixels * 0.52f)
                    .roundToInt()
                    .coerceAtLeast(1)
                val auraParams = WindowManager.LayoutParams().apply {
                    copyFrom(auraWin.attributes)
                    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = auraHeightPx
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = 0
                    y = 0
                    format = PixelFormat.TRANSLUCENT
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                auraWin.attributes = auraParams
            }
        }

        val state = coordinator.uiState.value
        val spec = computeTargetWindowSpec(hostContext, state)
        val initialDensity = hostContext.resources.displayMetrics.density.coerceAtLeast(1f)
        forceCameraCircleStageState.value = false
        suppressSatelliteStageState.value = false
        holdPillBeforeRingState.value = null
        skipNextCollapseStaging = false
        snapSpringsTo(spec)
        blurDrawable.setCornerRadiusPx(spec.cornerDp * initialDensity)

        dialog.setContentView(container)
        dialog.window?.let { win ->
            win.decorView.setPadding(0, 0, 0, 0)
            win.setBackgroundDrawable(blurDrawable)
            win.setDimAmount(0f)
            win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val initParams = WindowManager.LayoutParams().apply {
                copyFrom(win.attributes)
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                width = spec.widthPx.roundToInt().coerceAtLeast(1)
                height = spec.heightPx.roundToInt().coerceAtLeast(1)
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = spec.offsetXPx.roundToInt()
                y = spec.topYPx.roundToInt()
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            win.attributes = initParams
        }

        runCatching {
            auraDialog.show()
            activeAuraDialog = auraDialog
            dialog.show()
            activeDialog = dialog
            startObservingWindowState()
        }
    }

    private fun tearDownCurrentOverlay() {
        stopSpringLoop()
        clearScrubState()
        collapseStageJob?.cancel()
        collapseStageJob = null
        forceCameraCircleStageState.value = false
        suppressSatelliteStageState.value = false
        holdPillBeforeRingState.value = null
        isHoldingCapsuleState.value = false
        isReplyInputFocused = false
        windowObserverJob?.cancel()
        windowObserverJob = null
        statusBarIconController.restoreIcons()
        statusBarIconController.setSystemHeadsUpSuppressed(false)
        runCatching { activeDialog?.dismiss() }
        activeDialog = null
        runCatching { activeAuraDialog?.dismiss() }
        activeAuraDialog = null
        activeContainer = null
        activeLifecycleOwner?.onDestroy()
        activeLifecycleOwner = null
        activeHostContext = null
        cardBlurDrawable = null
    }

    private fun collectDomainStreams() {
        scope.launch {
            settingsRepository.observePreferences().collectLatest { prefs ->
                coordinator.updatePreferences(prefs)
            }
        }
        scope.launch {
            mediaSessionRepository.observeActiveMedia().collectLatest { media ->
                coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            }
        }
        scope.launch {
            mediaSessionRepository.observeMediaSessions().collectLatest { sessions ->
                coordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(sessions))
            }
        }
        scope.launch {
            notificationRepository.observeNotificationEvents().collectLatest { event ->
                coordinator.onEvent(event)
            }
        }
        scope.launch {
            torchRepository.observeTorchState().collectLatest { torch ->
                coordinator.onEvent(CapsuleEvent.TorchUpdated(torch))
            }
        }
        scope.launch {
            volumeRepository.observeVolumeChanges().collectLatest { volume ->
                coordinator.onEvent(CapsuleEvent.VolumeChanged(volume))
            }
        }
        scope.launch {
            bluetoothRepository.observeBluetoothEvents().collectLatest { event ->
                coordinator.onEvent(event)
            }
        }
        scope.launch {
            systemEventsRepository.observeSystemEvents().collectLatest { event ->
                coordinator.onEvent(event)
            }
        }
        scope.launch {
            brightnessRepository.observeBrightnessChanges().collectLatest { brightness ->
                coordinator.onEvent(CapsuleEvent.BrightnessChanged(brightness))
            }
        }
        scope.launch {
            systemEventsRepository.observeWeatherSummary().collectLatest { weather ->
                coordinator.onEvent(CapsuleEvent.WeatherUpdated(weather))
            }
        }
    }

    private fun collectEffects() {
        scope.launch {
            coordinator.effects.collectLatest { effect ->
                when (effect) {
                    is CapsuleEffect.LaunchApp -> {
                        notificationRepository.openNotificationOrApp(effect.notificationKey, effect.packageName)
                    }
                    CapsuleEffect.LockScreen -> triggerLockScreen()
                    CapsuleEffect.OpenNotificationShade -> triggerOpenNotificationShade()
                    CapsuleEffect.ToggleTorch -> {
                        val nextEnabled = !torchRepository.currentSnapshot().isEnabled
                        torchRepository.setTorchEnabled(nextEnabled)
                    }
                    CapsuleEffect.ToggleMediaPlayPause -> {
                        mediaSessionRepository.togglePlayPause()
                    }
                    is CapsuleEffect.SwitchMediaSession -> {
                        mediaSessionRepository.selectMediaSession(effect.sessionTokenKey)
                    }
                }
            }
        }
    }

    private fun startObservingWindowState() {
        windowObserverJob?.cancel()
        windowObserverJob = scope.launch {
            var lastVisualState: CapsuleVisualState? = null
            var lastQsRingOnly = false
            var lastSwipeJiggleSeq = coordinator.uiState.value.swipeJiggleSeq

            coordinator.uiState.collect { state ->
                val shouldSuppressHeadsUp = _isAccessibilityActive.value &&
                    !_isUserPaused.value &&
                    state.preferences.suppressSystemHeadsUp &&
                    !state.isQsRingOnlyActive
                statusBarIconController.setSystemHeadsUpSuppressed(shouldSuppressHeadsUp)

                val container = activeContainer ?: return@collect
                val dialog = activeDialog ?: return@collect
                val win = dialog.window ?: return@collect
                val host = activeHostContext ?: return@collect

                setOverlayNotTouchableForQs(win, state.isQsRingOnlyActive)

                val isVisible = state.visualState != CapsuleVisualState.HIDDEN_SYSTEM
                val targetViewVis = if (isVisible) View.VISIBLE else View.GONE
                if (win.decorView.visibility != targetViewVis) {
                    win.decorView.visibility = targetViewVis
                }
                activeAuraDialog?.window?.decorView?.let { auraDecor ->
                    if (auraDecor.visibility != targetViewVis) {
                        auraDecor.visibility = targetViewVis
                    }
                }
                container.visibility = targetViewVis
                if (state.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED) {
                    setOverlayKeyboardFocus(false)
                }

                if (state.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED &&
                    lastVisualState != CapsuleVisualState.STATE3_FULL_EXPANDED
                ) {
                    state3EnteredAtMs = System.currentTimeMillis()
                }
                val wasHidden = lastVisualState == CapsuleVisualState.HIDDEN_SYSTEM
                val wasState3 = lastVisualState == CapsuleVisualState.STATE3_FULL_EXPANDED
                val wasQsRingOnly = lastQsRingOnly
                lastVisualState = state.visualState
                lastQsRingOnly = state.isQsRingOnlyActive

                val activeScrub = activeScrubSession
                if (activeScrub != null) {
                    val isExpectedTransition = !activeScrub.isFingerDown &&
                        state.visualState == activeScrub.expectedCoastVisualState
                    if (state.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
                        (activeScrub.isFingerDown || isExpectedTransition)
                    ) {
                        return@collect
                    }
                    clearScrubState()
                }

                if (state.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                    collapseStageJob?.cancel()
                    collapseStageJob = null
                    holdPillBeforeRingState.value = null
                    forceCameraCircleStageState.value = false
                    suppressSatelliteStageState.value = false
                    updateSpringTargets()
                    return@collect
                }

                if (!isVisible) {
                    collapseStageJob?.cancel()
                    collapseStageJob = null
                    holdPillBeforeRingState.value = null
                    forceCameraCircleStageState.value = false
                    suppressSatelliteStageState.value = false
                    skipNextCollapseStaging = false
                    return@collect
                }

                if (wasHidden) {
                    collapseStageJob?.cancel()
                    collapseStageJob = null
                    holdPillBeforeRingState.value = null
                    forceCameraCircleStageState.value = false
                    suppressSatelliteStageState.value = false
                    skipNextCollapseStaging = false
                    val spec = computeTargetWindowSpec(host, state)
                    snapSpringsTo(spec)
                    applySpringsToWindow(host, win)
                    return@collect
                }

                // When collapsing from Horizontal Expand -> Camera Ring while the separated circle
                // remains active, DO NOT merge/hide/re-separate the circle — let the horizontal
                // capsule smoothly shrink to the camera ring while the separated circle glides beside it.
                if (state.isQsRingOnlyActive) {
                    collapseStageJob?.cancel()
                    collapseStageJob = null
                    holdPillBeforeRingState.value = null
                    forceCameraCircleStageState.value = false
                    suppressSatelliteStageState.value = false
                    updateSpringTargets()
                    return@collect
                }

                val suppressStaging = skipNextCollapseStaging
                skipNextCollapseStaging = false
                if (wasState3) {
                    collapseStageJob?.cancel()
                    val hasSatellite = state.leftSatellite != null
                    if (hasSatellite) {
                        // Collapse fully into the main capsule FIRST with left circle completely gone;
                        // only after the main capsule has completely settled, separate the left circle.
                        forceCameraCircleStageState.value = false
                        suppressSatelliteStageState.value = true
                        updateSpringTargets()
                        val mainOnlySpec = computeTargetWindowSpec(host, state.copy(leftSatellite = null))
                        collapseStageJob = scope.launch {
                            val startMs = SystemClock.uptimeMillis()
                            while (SystemClock.uptimeMillis() - startMs < 520L) {
                                delay(16L)
                                val closeToMain = abs(widthSpring.value - mainOnlySpec.widthPx) <= 1.5f &&
                                    abs(heightSpring.value - mainOnlySpec.heightPx) <= 1.5f &&
                                    abs(offsetXSpring.value - mainOnlySpec.offsetXPx) <= 1.5f &&
                                    abs(widthSpring.velocity) <= 12f &&
                                    abs(heightSpring.velocity) <= 12f
                                if (closeToMain) break
                            }
                            delay(150L)

                            val finalState = coordinator.uiState.value
                            if (finalState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                                finalState.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
                                !finalState.isQsRingOnlyActive
                            ) {
                                suppressSatelliteStageState.value = false
                                updateSpringTargets()
                            }
                        }
                    } else if (!suppressStaging && state.visualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL) {
                        forceCameraCircleStageState.value = false
                        suppressSatelliteStageState.value = false
                        updateSpringTargets()
                    } else {
                        forceCameraCircleStageState.value = false
                        suppressSatelliteStageState.value = false
                        updateSpringTargets()
                    }
                    return@collect
                }

                // Returning from QS-only ring when both horizontal pill and left satellite are active:
                // restore main horizontal pill first, then slightly delay left circle separation.
                if (wasQsRingOnly &&
                    !state.isQsRingOnlyActive &&
                    state.visualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL &&
                    state.leftSatellite != null
                ) {
                    collapseStageJob?.cancel()
                    forceCameraCircleStageState.value = false
                    suppressSatelliteStageState.value = true
                    updateSpringTargets()
                    val mainOnlySpec = computeTargetWindowSpec(host, state.copy(leftSatellite = null))
                    collapseStageJob = scope.launch {
                        val startMs = SystemClock.uptimeMillis()
                        while (SystemClock.uptimeMillis() - startMs < 250L) {
                            delay(16L)
                            val closeToMain = abs(widthSpring.value - mainOnlySpec.widthPx) <= 3.5f &&
                                abs(offsetXSpring.value - mainOnlySpec.offsetXPx) <= 3.5f
                            if (closeToMain) break
                        }
                        delay(90L)
                        val latestState = coordinator.uiState.value
                        if (!latestState.isQsRingOnlyActive &&
                            latestState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                            latestState.visualState != CapsuleVisualState.HIDDEN_SYSTEM
                        ) {
                            suppressSatelliteStageState.value = false
                            updateSpringTargets()
                        }
                    }
                    return@collect
                }

                if (state.swipeJiggleSeq != lastSwipeJiggleSeq) {
                    lastSwipeJiggleSeq = state.swipeJiggleSeq
                    val density = host.resources.displayMetrics.density.coerceAtLeast(1f)
                    val dir = if (state.swipeDirection < 0) -1f else 1f
                    jiggleUntilMs = SystemClock.uptimeMillis() + 340L
                    offsetXSpring.velocity = dir * 240f * density
                    ensureSpringLoopRunning()
                }

                if (!forceCameraCircleStageState.value) {
                    updateSpringTargets()
                }
            }
        }
    }

    private fun updateSpringTargets() {
        val host = activeHostContext ?: return
        val rawState = holdPillBeforeRingState.value ?: coordinator.uiState.value
        val state = when {
            forceCameraCircleStageState.value && rawState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED ->
                rawState.copy(
                    visualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
                    content = CapsuleContent.Idle,
                    leftSatellite = null,
                )
            suppressSatelliteStageState.value && rawState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED ->
                rawState.copy(leftSatellite = null)
            else -> rawState
        }
        val spec = computeTargetWindowSpec(host, state)
        minClampWPx = spec.minWidthPx
        minClampHPx = spec.minHeightPx

        widthSpring.target = spec.widthPx
        heightSpring.target = spec.heightPx
        offsetXSpring.target = spec.offsetXPx
        topYSpring.target = spec.topYPx
        cornerDpSpring.target = spec.cornerDp
        blurRadiusSpring.target = spec.blurRadiusPx

        ensureSpringLoopRunning()
    }

    private fun snapSpringsTo(spec: WindowSpec) {
        minClampWPx = spec.minWidthPx
        minClampHPx = spec.minHeightPx
        widthSpring.snapTo(spec.widthPx)
        heightSpring.snapTo(spec.heightPx)
        offsetXSpring.snapTo(spec.offsetXPx)
        topYSpring.snapTo(spec.topYPx)
        cornerDpSpring.snapTo(spec.cornerDp)
        blurRadiusSpring.snapTo(spec.blurRadiusPx)
        animatedCornerDpState.floatValue = spec.cornerDp
    }

    private fun ensureSpringLoopRunning() {
        if (isSpringLoopRunning) return
        isSpringLoopRunning = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(vsyncCallback)
    }

    private fun stopSpringLoop() {
        isSpringLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(vsyncCallback)
    }

    private fun stepWindowSprings(frameTimeNanos: Long) {
        if (!isSpringLoopRunning) return
        val host = activeHostContext
        val win = activeDialog?.window
        if (host == null || win == null) {
            isSpringLoopRunning = false
            return
        }

        val dtSec = if (lastFrameNanos == 0L) {
            0.016f
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.002f, 0.032f)
        }
        lastFrameNanos = frameTimeNanos

        // Sub-step twice per frame with matched geometric spring constants so camera cutout stays 1:1 anchored
        val halfDt = dtSec * 0.5f
        val isJiggling = SystemClock.uptimeMillis() < jiggleUntilMs
        val xStiffness = if (isJiggling) 520f else 410f
        val xDamping = if (isJiggling) 0.40f else 0.82f
        var active = false
        repeat(2) {
            val wMoving = widthSpring.step(halfDt, stiffness = 410f, dampingRatio = 0.82f)
            val hMoving = heightSpring.step(halfDt, stiffness = 410f, dampingRatio = 0.82f)
            val xMoving = offsetXSpring.step(halfDt, stiffness = xStiffness, dampingRatio = xDamping)
            val yMoving = topYSpring.step(halfDt, stiffness = 410f, dampingRatio = 0.82f)
            val cMoving = cornerDpSpring.step(halfDt, stiffness = 410f, dampingRatio = 0.82f)
            val bMoving = blurRadiusSpring.step(halfDt, stiffness = 460f, dampingRatio = 0.96f)
            active = wMoving || hMoving || xMoving || yMoving || cMoving || bMoving
        }

        applySpringsToWindow(host, win)

        val session = activeScrubSession
        if (session != null && !session.isFingerDown) {
            val density = host.resources.displayMetrics.density.coerceAtLeast(1f)
            val coastFraction = ((heightSpring.value - session.compactBaseSpec.heightPx) / session.travelPx)
                .coerceIn(-0.04f, 1.14f)
            val t = coastFraction.coerceIn(0f, 1f)
            val satThreshold = 0.24f
            val satLeadT = if (!session.isCollapsing && session.compactUiState.leftSatellite != null) {
                ((satThreshold - t) / satThreshold).coerceIn(0f, 1f)
            } else {
                0f
            }
            val liveWidthDp = (widthSpring.value.coerceAtLeast(minClampWPx) / density).coerceAtLeast(1f)
            val liveHeightDp = (heightSpring.value.coerceAtLeast(minClampHPx) / density).coerceAtLeast(1f)
            scrubPresentationState.value = scrubPresentationState.value?.copy(
                fraction = coastFraction,
                currentWidthDp = liveWidthDp,
                currentHeightDp = liveHeightDp,
                satelliteFraction = satLeadT,
            )
        }

        if (active) {
            Choreographer.getInstance().postFrameCallback(vsyncCallback)
        } else {
            isSpringLoopRunning = false
            val scrubFinished = activeScrubSession?.isFingerDown == false
            if (scrubFinished) {
                clearScrubState()
            }
        }
    }

    private fun startVerticalScrub(): Pair<Float, Float> {
        val host = activeHostContext ?: return 420f to 0f
        val win = activeDialog?.window
        collapseStageJob?.cancel()
        collapseStageJob = null
        holdPillBeforeRingState.value = null
        forceCameraCircleStageState.value = false
        suppressSatelliteStageState.value = false
        stopSpringLoop()
        isHoldingCapsuleState.value = false

        val current = coordinator.uiState.value
        val isFromExpanded = current.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
        val compactState: CapsuleUiState
        val expandedState: CapsuleUiState

        if (isFromExpanded) {
            expandedState = current
            val (collapseVis, collapseContent) = coordinator.resolveCollapseTarget()
            compactState = current.copy(visualState = collapseVis, content = collapseContent)
        } else {
            compactState = if (current.content is CapsuleContent.BatteryPeek || current.content is CapsuleContent.Charging) {
                val (collapseVis, collapseContent) = coordinator.resolveCollapseTarget()
                current.copy(visualState = collapseVis, content = collapseContent)
            } else {
                current
            }
            expandedState = current.copy(
                visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
                content = coordinator.resolveExpandTargetContent(),
            )
        }

        val compactSpec = computeTargetWindowSpec(host, compactState)
        val compactBaseSpec = computeTargetWindowSpec(host, compactState.copy(leftSatellite = null))
        val expandedSpec = computeTargetWindowSpec(host, expandedState)
        val density = host.resources.displayMetrics.density.coerceAtLeast(1f)
        val travelPx = (expandedSpec.heightPx - compactBaseSpec.heightPx).coerceAtLeast(160f)
        val initialFraction = ((heightSpring.value - compactBaseSpec.heightPx) / travelPx)
            .coerceIn(-0.04f, 1.14f)

        val session = ScrubSession(
            compactUiState = compactState,
            compactSpec = compactSpec,
            compactBaseSpec = compactBaseSpec,
            expandedUiState = expandedState,
            expandedSpec = expandedSpec,
            expandedWidthDp = expandedSpec.widthPx / density,
            expandedHeightDp = expandedSpec.heightPx / density,
            travelPx = travelPx,
            isFingerDown = true,
            isCollapsing = isFromExpanded,
            expectedCoastVisualState = expandedState.visualState,
        )
        activeScrubSession = session
        minClampWPx = compactSpec.minWidthPx
        minClampHPx = compactSpec.minHeightPx

        applyScrubFrame(session, initialFraction, host, win)
        return travelPx to initialFraction
    }

    private fun updateVerticalScrub(fraction: Float) {
        val session = activeScrubSession ?: return
        val host = activeHostContext ?: return
        applyScrubFrame(session, fraction, host, activeDialog?.window)
    }

    private fun endVerticalScrub(commitExpanded: Boolean, velocityYPxPerSec: Float) {
        val session = activeScrubSession ?: return
        session.isFingerDown = false
        session.expectedCoastVisualState = if (commitExpanded) {
            CapsuleVisualState.STATE3_FULL_EXPANDED
        } else {
            session.compactUiState.visualState
        }

        if (commitExpanded) {
            if (coordinator.uiState.value.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED) {
                coordinator.onEvent(CapsuleEvent.ExpandToFull)
            } else {
                coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
            }
        } else {
            skipNextCollapseStaging = true
            if (coordinator.uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            } else {
                coordinator.onEvent(CapsuleEvent.HoldPeekReleased)
            }
        }

        val hideSatUntilSettled = !commitExpanded && session.isCollapsing && session.compactUiState.leftSatellite != null
        val targetSpec = when {
            commitExpanded -> session.expandedSpec
            hideSatUntilSettled -> session.compactBaseSpec
            else -> session.compactSpec
        }
        if (hideSatUntilSettled) {
            suppressSatelliteStageState.value = true
        }
        minClampWPx = targetSpec.minWidthPx
        minClampHPx = targetSpec.minHeightPx

        widthSpring.target = targetSpec.widthPx
        heightSpring.target = targetSpec.heightPx
        offsetXSpring.target = targetSpec.offsetXPx
        topYSpring.target = targetSpec.topYPx
        cornerDpSpring.target = targetSpec.cornerDp
        blurRadiusSpring.target = targetSpec.blurRadiusPx

        heightSpring.velocity = velocityYPxPerSec.coerceIn(-4800f, 4800f)
        ensureSpringLoopRunning()
    }

    private fun applyScrubFrame(
        session: ScrubSession,
        fraction: Float,
        host: Context,
        window: Window?,
    ) {
        val t = fraction.coerceIn(0f, 1f)
        val overFraction = (fraction - 1f).coerceAtLeast(0f)
        val widthT = t
        val blurT = ((t - 0.08f) / 0.65f).coerceIn(0f, 1f)
        val satThreshold = 0.24f
        val satLeadT = if (!session.isCollapsing && session.compactUiState.leftSatellite != null) {
            ((satThreshold - t) / satThreshold).coerceIn(0f, 1f)
        } else {
            0f
        }
        val density = host.resources.displayMetrics.density.coerceAtLeast(1f)

        val extraLeadW = session.compactSpec.widthPx - session.compactBaseSpec.widthPx
        val extraLeadX = session.compactSpec.offsetXPx - session.compactBaseSpec.offsetXPx
        val mainW = lerp(session.compactBaseSpec.widthPx, session.expandedSpec.widthPx, widthT) + overFraction * 24f
        val baseW = mainW + satLeadT * extraLeadW
        val baseH = lerp(session.compactBaseSpec.heightPx, session.expandedSpec.heightPx, t) + overFraction * 140f
        val targetX = lerp(session.compactBaseSpec.offsetXPx, session.expandedSpec.offsetXPx, widthT) + satLeadT * extraLeadX
        val targetY = lerp(session.compactBaseSpec.topYPx, session.expandedSpec.topYPx, t)
        val circleCornerNowDp = (min(mainW, baseH) / density) * 0.5f
        val compactCornerTarget = if (session.compactUiState.visualState == CapsuleVisualState.STATE1_CUTOUT_ANCHOR) {
            circleCornerNowDp
        } else {
            session.compactBaseSpec.cornerDp
        }
        val cornerDp = lerp(compactCornerTarget, session.expandedSpec.cornerDp, t)
        val blurPx = lerp(session.compactBaseSpec.blurRadiusPx, session.expandedSpec.blurRadiusPx, blurT)

        widthSpring.snapTo(baseW.coerceAtLeast(minClampWPx))
        heightSpring.snapTo(baseH.coerceAtLeast(minClampHPx))
        offsetXSpring.snapTo(targetX)
        topYSpring.snapTo(targetY)
        cornerDpSpring.snapTo(cornerDp)
        blurRadiusSpring.snapTo(blurPx)

        window?.let { applySpringsToWindow(host, it) }

        scrubPresentationState.value = CapsuleScrubPresentation(
            fraction = fraction,
            compactVisualState = session.compactUiState.visualState,
            compactContent = session.compactUiState.content,
            expandedContent = session.expandedUiState.content,
            expandedWidthDp = session.expandedWidthDp,
            expandedHeightDp = session.expandedHeightDp,
            currentWidthDp = (widthSpring.value.coerceAtLeast(minClampWPx) / density).coerceAtLeast(1f),
            currentHeightDp = (heightSpring.value.coerceAtLeast(minClampHPx) / density).coerceAtLeast(1f),
            isCollapsing = session.isCollapsing,
            satelliteFraction = satLeadT,
            fromSatellite = session.expandedUiState.isExpandedFromSatellite,
        )
    }

    private fun clearScrubState() {
        val wasCollapsingWithSat = activeScrubSession?.let {
            it.isCollapsing && it.expectedCoastVisualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                it.compactUiState.leftSatellite != null
        } == true
        activeScrubSession = null
        scrubPresentationState.value = null
        if (wasCollapsingWithSat && suppressSatelliteStageState.value) {
            collapseStageJob?.cancel()
            collapseStageJob = scope.launch {
                delay(150L)
                val finalState = coordinator.uiState.value
                if (finalState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                    finalState.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
                    !finalState.isQsRingOnlyActive
                ) {
                    suppressSatelliteStageState.value = false
                    updateSpringTargets()
                }
            }
        }
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private data class ScrubSession(
        val compactUiState: CapsuleUiState,
        val compactSpec: WindowSpec,
        val compactBaseSpec: WindowSpec,
        val expandedUiState: CapsuleUiState,
        val expandedSpec: WindowSpec,
        val expandedWidthDp: Float,
        val expandedHeightDp: Float,
        val travelPx: Float,
        var isFingerDown: Boolean,
        val isCollapsing: Boolean,
        var expectedCoastVisualState: CapsuleVisualState,
    )

    private fun setOverlayNotTouchableForQs(window: Window, notTouchable: Boolean) {
        val currentFlags = window.attributes.flags
        val isCurrentlyNotTouchable = (currentFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        if (isCurrentlyNotTouchable == notTouchable) return
        if (notTouchable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    private fun setOverlayKeyboardFocus(focused: Boolean) {
        isReplyInputFocused = focused
        coordinator.onEvent(CapsuleEvent.ReplyFocusChanged(focused))
        val win = activeDialog?.window ?: return
        if (focused) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        } else {
            win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    private fun applySpringsToWindow(context: Context, window: Window) {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val clampedW = widthSpring.value.coerceAtLeast(minClampWPx).roundToInt().coerceAtLeast(1)
        val clampedH = heightSpring.value.coerceAtLeast(minClampHPx).roundToInt().coerceAtLeast(1)
        val targetX = offsetXSpring.value.roundToInt()
        val targetY = topYSpring.value.roundToInt()
        val cornerDp = cornerDpSpring.value.coerceAtLeast(8f)
        val cornerPx = cornerDp * density
        val blurPx = blurRadiusSpring.value.roundToInt().coerceIn(0, 80)

        animatedCornerDpState.floatValue = cornerDp
        cardBlurDrawable?.setCornerRadiusPx(cornerPx)
        runCatching { window.setBackgroundBlurRadius(blurPx) }

        val current = window.attributes
        if (current.width != clampedW || current.height != clampedH ||
            current.x != targetX || current.y != targetY
        ) {
            val freshParams = WindowManager.LayoutParams().apply {
                copyFrom(current)
                x = targetX
                y = targetY
                width = clampedW
                height = clampedH
            }
            window.attributes = freshParams
        }
    }

    private data class WindowSpec(
        val widthPx: Float,
        val heightPx: Float,
        val minWidthPx: Float,
        val minHeightPx: Float,
        val offsetXPx: Float,
        val topYPx: Float,
        val cornerDp: Float,
        val blurRadiusPx: Float,
    )

    private fun computeTargetWindowSpec(context: Context, state: CapsuleUiState): WindowSpec {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val dpSpec = computeCapsuleLayoutDpSpec(state = state, isHoldingCapsule = isHoldingCapsuleState.value)
        val blurPx = if (state.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED && _isBlurEnabled.value) {
            64f
        } else {
            0f
        }
        return WindowSpec(
            widthPx = dpSpec.widthDp * density,
            heightPx = dpSpec.heightDp * density,
            minWidthPx = dpSpec.minWidthDp * density,
            minHeightPx = dpSpec.minHeightDp * density,
            offsetXPx = dpSpec.offsetXDp * density,
            topYPx = dpSpec.topYDp * density,
            cornerDp = dpSpec.cornerDp,
            blurRadiusPx = blurPx,
        )
    }

    private fun measureDeviceCutout(context: Context) {
        val detected = CutoutGeometryResolver.detect(context)
        coordinator.updateCutoutGeometry(detected.geometry)
    }

    private fun dispatchEnvironmentVisibility(config: Configuration = appContext.resources.configuration) {
        val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = defaultDisplay?.rotation ?: Surface.ROTATION_0
        val isRotatedLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        val isConfigLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

        val displayState = defaultDisplay?.state ?: Display.STATE_OFF
        val isInteractive = powerManager.isInteractive
        val isScreenOffOrAod = !isInteractive || displayState != Display.STATE_ON || _isUserPaused.value

        coordinator.onEvent(CapsuleEvent.OrientationChanged(isConfigLandscape || isRotatedLandscape))
        coordinator.onEvent(CapsuleEvent.ScreenInactiveChanged(isScreenOffOrAod))
        coordinator.onEvent(CapsuleEvent.KeyguardLockedChanged(keyguardManager.isKeyguardLocked))
    }

    private class SpringAxis {
        var value = 0f
        var velocity = 0f
        var target = 0f

        fun snapTo(v: Float) {
            value = v
            velocity = 0f
            target = v
        }

        fun step(dtSec: Float, stiffness: Float, dampingRatio: Float): Boolean {
            val displacement = value - target
            if (abs(displacement) < 0.35f && abs(velocity) < 1.5f) {
                value = target
                velocity = 0f
                return false
            }
            val damping = 2f * dampingRatio * sqrt(stiffness)
            val force = -stiffness * displacement - damping * velocity
            velocity += force * dtSec
            value += velocity * dtSec
            return true
        }
    }

    private class CapsuleBlurDrawable : Drawable() {
        private val inner = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.TRANSPARENT)
        }

        fun setCornerRadiusPx(radiusPx: Float) {
            inner.cornerRadius = radiusPx
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            inner.bounds = bounds
        }

        override fun draw(canvas: Canvas) = inner.draw(canvas)

        override fun setAlpha(alpha: Int) {
            inner.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            inner.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getOutline(outline: Outline) {
            inner.getOutline(outline)
        }
    }
}
