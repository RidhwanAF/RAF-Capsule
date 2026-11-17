package com.raf.capsuleos.presentation.capsule.service

import android.app.Dialog
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
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.RingerModeState
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
import com.raf.capsuleos.presentation.capsule.ui.components.computeSidePillGeometry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
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
    private val systemEventsRepository: SystemEventsRepository,
    private val statusBarIconController: StatusBarIconController,
    private val settingsRepository: CapsuleSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
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
    private var activeContainer: FrameLayout? = null
    private var activeLifecycleOwner: ComposeOverlayLifecycleOwner? = null
    private var activeHostContext: Context? = null
    private var lockScreenAction: (() -> Boolean)? = null
    private var cardBlurDrawable: CapsuleBlurDrawable? = null
    private var windowObserverJob: Job? = null
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
    private var isHoldingCapsule = false
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
    }

    /** Attaches the capsule using `TYPE_ACCESSIBILITY_OVERLAY` directly over the camera & status bar. */
    fun attachAccessibilityHost(
        serviceContext: Context,
        onLockScreen: () -> Boolean = { false },
    ) {
        _isAccessibilityActive.value = true
        lockScreenAction = onLockScreen
        attachOverlay(hostContext = serviceContext)
    }

    /** Detaches the `TYPE_ACCESSIBILITY_OVERLAY` host when the accessibility service unbinds. */
    fun detachAccessibilityHost() {
        _isAccessibilityActive.value = false
        lockScreenAction = null
        tearDownCurrentOverlay()
    }

    private fun triggerLockScreen() {
        coordinator.onEvent(CapsuleEvent.CollapseCapsule)
        lockScreenAction?.invoke()
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
            CapsuleTestAction.NOW_BRIEF -> coordinator.onEvent(CapsuleEvent.ExpandToFull)
            CapsuleTestAction.NOTIFICATION -> coordinator.onEvent(
                CapsuleEvent.NotificationPosted(
                    CapsuleContent.NotificationAlert(
                        key = "test|com.raf.capsuleos|${System.currentTimeMillis()}",
                        packageName = appContext.packageName,
                        appName = "Raf Capsule",
                        title = "New Message — Tap to expand & open",
                        text = "Sample notification preview with frosted vertical blur.",
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
                IslandCapsule(
                    uiState = state,
                    cornerRadiusDp = animatedCornerDpState.floatValue,
                    isBlurSupported = blurSupported,
                    scrubPresentation = scrubPresentationState.value,
                    onHoldingStateChanged = { holding ->
                        if (isHoldingCapsule != holding) {
                            isHoldingCapsule = holding
                            updateSpringTargets()
                        }
                    },
                    onHoldPeekStart = { coordinator.onEvent(CapsuleEvent.HoldPeekStarted) },
                    onHoldPeekEnd = { coordinator.onEvent(CapsuleEvent.HoldPeekReleased) },
                    onTap = { coordinator.onEvent(CapsuleEvent.TapCapsule) },
                    onExpand = { coordinator.onEvent(CapsuleEvent.ExpandToFull) },
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
                    onTogglePlayPause = {
                        coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                        mediaSessionRepository.togglePlayPause()
                    },
                    onSkipNext = {
                        coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                        mediaSessionRepository.skipNext()
                    },
                    onSkipPrevious = {
                        coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                        mediaSessionRepository.skipPrevious()
                    },
                    onSeekTo = { positionMs ->
                        coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
                        mediaSessionRepository.seekTo(positionMs)
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
                    onLockScreen = ::triggerLockScreen,
                    onDoubleTapLock = ::triggerLockScreen,
                    onOpenClock = {
                        coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                        val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { appContext.startActivity(clockIntent) }
                    },
                )
            }
        }

        val container = object : FrameLayout(hostContext) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                val isState3 = coordinator.uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
                if (ev.action == MotionEvent.ACTION_OUTSIDE && isState3 && !isReplyInputFocused) {
                    val sinceExpand = System.currentTimeMillis() - state3EnteredAtMs
                    if (sinceExpand > 360L) {
                        coordinator.onEvent(CapsuleEvent.CollapseCapsule)
                    }
                    return true
                }
                if (isState3 && (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE)) {
                    coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
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

        val state = coordinator.uiState.value
        val spec = computeTargetWindowSpec(hostContext, state)
        snapSpringsTo(spec)
        blurDrawable.setCornerRadiusPx(spec.cornerDp * hostContext.resources.displayMetrics.density.coerceAtLeast(1f))

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
            dialog.show()
            activeDialog = dialog
            startObservingWindowState()
        }
    }

    private fun tearDownCurrentOverlay() {
        stopSpringLoop()
        clearScrubState()
        isReplyInputFocused = false
        windowObserverJob?.cancel()
        windowObserverJob = null
        statusBarIconController.restoreIcons()
        statusBarIconController.setSystemHeadsUpSuppressed(false)
        runCatching { activeDialog?.dismiss() }
        activeDialog = null
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
                }
            }
        }
    }

    private fun startObservingWindowState() {
        windowObserverJob?.cancel()
        windowObserverJob = scope.launch {
            var lastVisualState: CapsuleVisualState? = null
            var lastJiggleSeq = coordinator.uiState.value.swipeJiggleSeq

            coordinator.uiState.collectLatest { state ->
                val triggeredJiggle = state.swipeJiggleSeq != lastJiggleSeq
                lastJiggleSeq = state.swipeJiggleSeq
                val shouldSuppressHeadsUp = _isAccessibilityActive.value &&
                    !_isUserPaused.value &&
                    state.preferences.suppressSystemHeadsUp
                statusBarIconController.setSystemHeadsUpSuppressed(shouldSuppressHeadsUp)

                if (state.shouldHideStatusBarIcons) {
                    statusBarIconController.hideIconsForExpansion()
                } else {
                    statusBarIconController.restoreIcons()
                }

                val container = activeContainer ?: return@collectLatest
                val dialog = activeDialog ?: return@collectLatest
                val win = dialog.window ?: return@collectLatest

                val isVisible = state.visualState != CapsuleVisualState.HIDDEN_SYSTEM
                val targetViewVis = if (isVisible) View.VISIBLE else View.GONE
                if (win.decorView.visibility != targetViewVis) {
                    win.decorView.visibility = targetViewVis
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
                lastVisualState = state.visualState

                val activeScrub = activeScrubSession
                if (activeScrub != null) {
                    val isExpectedTransition = !activeScrub.isFingerDown &&
                        state.visualState == activeScrub.expectedCoastVisualState
                    if (state.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
                        (activeScrub.isFingerDown || isExpectedTransition)
                    ) {
                        return@collectLatest
                    }
                    clearScrubState()
                }

                if (isVisible && wasHidden) {
                    activeHostContext?.let { host ->
                        snapSpringsTo(computeTargetWindowSpec(host, state))
                        applySpringsToWindow(host, win)
                    }
                } else {
                    if (triggeredJiggle && state.visualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL) {
                        val density = activeHostContext?.resources?.displayMetrics?.density ?: 3f
                        offsetXSpring.velocity = state.swipeDirection * 290f * density
                        jiggleUntilMs = SystemClock.uptimeMillis() + 420L
                    }
                    updateSpringTargets()
                }
            }
        }
    }

    private fun updateSpringTargets() {
        val host = activeHostContext ?: return
        val state = coordinator.uiState.value
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

        // Sub-step twice per frame for unconditional harmonic spring accuracy across 60Hz–120Hz displays
        val halfDt = dtSec * 0.5f
        val isJiggling = SystemClock.uptimeMillis() < jiggleUntilMs
        val xStiffness = if (isJiggling) 520f else 420f
        val xDamping = if (isJiggling) 0.40f else 0.9f
        var active = false
        repeat(2) {
            val wMoving = widthSpring.step(halfDt, stiffness = 390f, dampingRatio = 0.86f)
            val hMoving = heightSpring.step(halfDt, stiffness = 340f, dampingRatio = 0.78f)
            val xMoving = offsetXSpring.step(halfDt, stiffness = xStiffness, dampingRatio = xDamping)
            val yMoving = topYSpring.step(halfDt, stiffness = 420f, dampingRatio = 0.9f)
            val cMoving = cornerDpSpring.step(halfDt, stiffness = 360f, dampingRatio = 0.86f)
            val bMoving = blurRadiusSpring.step(halfDt, stiffness = 440f, dampingRatio = 0.96f)
            active = wMoving || hMoving || xMoving || yMoving || cMoving || bMoving
        }

        applySpringsToWindow(host, win)

        val session = activeScrubSession
        if (session != null && !session.isFingerDown) {
            val density = host.resources.displayMetrics.density.coerceAtLeast(1f)
            val coastFraction = ((heightSpring.value - session.compactSpec.heightPx) / session.travelPx)
                .coerceIn(-0.04f, 1.14f)
            val liveWidthDp = (widthSpring.value.coerceAtLeast(minClampWPx) / density).coerceAtLeast(1f)
            val liveHeightDp = (heightSpring.value.coerceAtLeast(minClampHPx) / density).coerceAtLeast(1f)
            scrubPresentationState.value = scrubPresentationState.value?.copy(
                fraction = coastFraction,
                currentWidthDp = liveWidthDp,
                currentHeightDp = liveHeightDp,
            )
        }

        if (active) {
            Choreographer.getInstance().postFrameCallback(vsyncCallback)
        } else {
            isSpringLoopRunning = false
            if (activeScrubSession?.isFingerDown == false) {
                clearScrubState()
            }
        }
    }

    private fun startVerticalScrub(): Pair<Float, Float> {
        val host = activeHostContext ?: return 420f to 0f
        val win = activeDialog?.window
        stopSpringLoop()
        isHoldingCapsule = false

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
        val expandedSpec = computeTargetWindowSpec(host, expandedState)
        val density = host.resources.displayMetrics.density.coerceAtLeast(1f)
        val travelPx = (expandedSpec.heightPx - compactSpec.heightPx).coerceAtLeast(160f)
        val initialFraction = ((heightSpring.value - compactSpec.heightPx) / travelPx)
            .coerceIn(-0.04f, 1.14f)

        val session = ScrubSession(
            compactUiState = compactState,
            compactSpec = compactSpec,
            expandedUiState = expandedState,
            expandedSpec = expandedSpec,
            expandedWidthDp = expandedSpec.widthPx / density,
            expandedHeightDp = expandedSpec.heightPx / density,
            travelPx = travelPx,
            isFingerDown = true,
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
            if (coordinator.uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            } else {
                coordinator.onEvent(CapsuleEvent.HoldPeekReleased)
            }
        }

        val targetSpec = if (commitExpanded) session.expandedSpec else session.compactSpec
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
        val density = host.resources.displayMetrics.density.coerceAtLeast(1f)

        val baseW = lerp(session.compactSpec.widthPx, session.expandedSpec.widthPx, widthT) + overFraction * 24f
        val baseH = lerp(session.compactSpec.heightPx, session.expandedSpec.heightPx, t) + overFraction * 140f
        val targetX = lerp(session.compactSpec.offsetXPx, session.expandedSpec.offsetXPx, widthT)
        val targetY = lerp(session.compactSpec.topYPx, session.expandedSpec.topYPx, t)
        val circleCornerNowDp = (min(baseW, baseH) / density) * 0.5f
        val compactCornerTarget = if (session.compactUiState.visualState == CapsuleVisualState.STATE1_CUTOUT_ANCHOR) {
            circleCornerNowDp
        } else {
            session.compactSpec.cornerDp
        }
        val cornerDp = lerp(compactCornerTarget, session.expandedSpec.cornerDp, t)
        val blurPx = lerp(session.compactSpec.blurRadiusPx, session.expandedSpec.blurRadiusPx, blurT)

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
        )
    }

    private fun clearScrubState() {
        activeScrubSession = null
        scrubPresentationState.value = null
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private data class ScrubSession(
        val compactUiState: CapsuleUiState,
        val compactSpec: WindowSpec,
        val expandedUiState: CapsuleUiState,
        val expandedSpec: WindowSpec,
        val expandedWidthDp: Float,
        val expandedHeightDp: Float,
        val travelPx: Float,
        var isFingerDown: Boolean,
        var expectedCoastVisualState: CapsuleVisualState,
    )

    private fun setOverlayKeyboardFocus(focused: Boolean) {
        isReplyInputFocused = focused
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
        val holdBonusDp = if (isHoldingCapsule && state.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED) 4f else 0f
        val circleDp = state.circleDiameterDp
        val minCirclePx = circleDp * density
        val prefsOffsetPx = state.preferences.horizontalOffsetDp * density
        val cutoutCenterXOffsetPx = state.cutout.holeCenterOffsetXDp * density
        val topYPx = state.anchorTopDp * density
        val content = state.content

        return when (state.visualState) {
            CapsuleVisualState.HIDDEN_SYSTEM -> WindowSpec(
                widthPx = 1f,
                heightPx = 1f,
                minWidthPx = 1f,
                minHeightPx = 1f,
                offsetXPx = cutoutCenterXOffsetPx + prefsOffsetPx,
                topYPx = topYPx,
                cornerDp = circleDp / 2f,
                blurRadiusPx = 0f,
            )
            CapsuleVisualState.STATE1_CUTOUT_ANCHOR -> {
                val sidePx = (circleDp + holdBonusDp) * density
                WindowSpec(
                    widthPx = sidePx,
                    heightPx = sidePx,
                    minWidthPx = minCirclePx,
                    minHeightPx = minCirclePx,
                    offsetXPx = cutoutCenterXOffsetPx + prefsOffsetPx,
                    topYPx = topYPx,
                    cornerDp = (circleDp + holdBonusDp) / 2f,
                    blurRadiusPx = 0f,
                )
            }
            CapsuleVisualState.STATE2_HORIZONTAL_PILL -> {
                val geom = computeSidePillGeometry(content, state.cutout)
                val hDp = circleDp + holdBonusDp
                WindowSpec(
                    widthPx = (geom.totalWidthDp + holdBonusDp) * density,
                    heightPx = hDp * density,
                    minWidthPx = minCirclePx,
                    minHeightPx = minCirclePx,
                    offsetXPx = cutoutCenterXOffsetPx + geom.cutoutOffsetXDp * density + prefsOffsetPx,
                    topYPx = topYPx,
                    cornerDp = hDp / 2f,
                    blurRadiusPx = 0f,
                )
            }
            CapsuleVisualState.STATE3_FULL_EXPANDED -> {
                val (wDp, hDp, expandedCornerDp) = if (content is CapsuleContent.Torch) {
                    val sq = if (content.maxStrengthLevel > 1) 208f else 184f
                    val bounded = sq.coerceAtMost(state.cutout.screenWidthDp - 48f)
                    val torchCorner = (state.cutout.screenTopCornerRadiusDp - 14f).coerceIn(26f, 36f)
                    Triple(bounded, bounded, torchCorner)
                } else {
                    val sidePaddingDp = if (state.preferences.belowStatusBar) {
                        14f
                    } else {
                        state.anchorTopDp.coerceIn(10f, 20f)
                    }
                    val w = (state.cutout.screenWidthDp - sidePaddingDp * 2f).coerceAtLeast(300f)
                    val h = when (content) {
                        is CapsuleContent.Media -> 240f
                        is CapsuleContent.Volume -> 262f
                        is CapsuleContent.NowBrief -> 288f
                        is CapsuleContent.NotificationAlert -> 214f
                        else -> 148f
                    }
                    val outsidePad = if (state.preferences.belowStatusBar) {
                        sidePaddingDp
                    } else {
                        state.anchorTopDp.coerceAtLeast(sidePaddingDp)
                    }
                    val concentricCorner = (state.cutout.screenTopCornerRadiusDp - outsidePad).coerceIn(24f, 48f)
                    Triple(w, h, concentricCorner)
                }
                WindowSpec(
                    widthPx = wDp * density,
                    heightPx = hDp * density,
                    minWidthPx = minCirclePx,
                    minHeightPx = minCirclePx,
                    offsetXPx = prefsOffsetPx,
                    topYPx = topYPx,
                    cornerDp = expandedCornerDp,
                    blurRadiusPx = if (_isBlurEnabled.value) 64f else 0f,
                )
            }
        }
    }

    private fun measureDeviceCutout(context: Context) {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val metrics = windowManager.currentWindowMetrics
        val displayWidthPx = metrics.bounds.width().toFloat()
        val screenWidthDp = (displayWidthPx / density).coerceAtLeast(320f)
        val insets = metrics.windowInsets
        val statusBarBottomPx = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val statusBarBottomDp = (statusBarBottomPx / density).coerceAtLeast(24f)
        val tlCornerPx = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        val trCornerPx = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
        val maxCornerPx = maxOf(tlCornerPx, trCornerPx)
        val screenCornerDp = if (maxCornerPx > 0) (maxCornerPx.toFloat() / density) else 42f

        val cutout = insets.displayCutout
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.cutout

        val path = cutout?.cutoutPath
        if (path != null) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            if (!bounds.isEmpty && bounds.width() > 0f && bounds.height() > 0f) {
                val centerXOffsetDp = (bounds.centerX() - (displayWidthPx / 2f)) / density
                coordinator.updateCutoutGeometry(
                    CutoutGeometry(
                        holeWidthDp = bounds.width() / density,
                        holeHeightDp = bounds.height() / density,
                        topOffsetDp = bounds.top / density,
                        statusBarBottomDp = statusBarBottomDp,
                        screenWidthDp = screenWidthDp,
                        holeCenterOffsetXDp = centerXOffsetDp,
                        screenTopCornerRadiusDp = screenCornerDp,
                    ),
                )
                return
            }
        }
        val topRect = cutout?.boundingRectTop
        if (topRect != null && !topRect.isEmpty) {
            val estimatedHoleSizeDp = (topRect.width().toFloat() / density).coerceIn(20f, 64f)
            val bottomDp = topRect.bottom.toFloat() / density
            val estimatedTopDp = ((bottomDp - estimatedHoleSizeDp) * 0.5f).coerceAtLeast(4f)
            val centerXOffsetDp = (topRect.exactCenterX() - (displayWidthPx / 2f)) / density
            coordinator.updateCutoutGeometry(
                CutoutGeometry(
                    holeWidthDp = estimatedHoleSizeDp,
                    holeHeightDp = estimatedHoleSizeDp,
                    topOffsetDp = estimatedTopDp,
                    statusBarBottomDp = statusBarBottomDp,
                    screenWidthDp = screenWidthDp,
                    holeCenterOffsetXDp = centerXOffsetDp,
                    screenTopCornerRadiusDp = screenCornerDp,
                ),
            )
            return
        }
        coordinator.updateCutoutGeometry(
            CutoutGeometry(
                statusBarBottomDp = statusBarBottomDp,
                screenWidthDp = screenWidthDp,
                screenTopCornerRadiusDp = screenCornerDp,
            ),
        )
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
