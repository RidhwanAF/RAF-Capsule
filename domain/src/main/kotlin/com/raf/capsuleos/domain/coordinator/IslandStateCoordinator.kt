package com.raf.capsuleos.domain.coordinator

import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.BrightnessInfo
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEffect
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.WeatherSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pure Kotlin state machine orchestrating the 3-state camera cutout capsule:
 * - **State 1 (1:1 Camera Ring)**: Resting 1:1 circle showing live battery progress arc
 *   (Theme primary on battery; percentage-tiered Red/Orange/Yellow/Green while charging).
 * - **State 2 (Horizontal Side Pill)**: View-stacked horizontal expansion supporting slide rotation
 *   across active items, 2-second minimum dwell lock, and automatic 10-second return to canonical
 *   priority order (Media -> Notification/LiveActivity -> Charging -> Torch).
 * - **State 3 (Big Container)**: Expanded card opened via hold or expandable pill tap,
 *   auto-collapsing per settings and resetting timer while interacted with.
 *
 * @property scope Coroutine scope tied to the accessibility overlay service lifecycle.
 * @property nowBriefProvider Supplies live system snapshot data when expanding Now Brief.
 * @property clockMillis Monotonic or test-injectable millisecond clock for deterministic timers.
 */
class IslandStateCoordinator(
    private val scope: CoroutineScope,
    private val nowBriefProvider: () -> CapsuleContent.NowBrief = {
        CapsuleContent.NowBrief(
            batteryPercent = 85,
            isCharging = false,
            torch = CapsuleContent.Torch(isEnabled = false, strengthLevel = 1, maxStrengthLevel = 1),
            volume = CapsuleContent.Volume(activeStream = AudioStreamType.MEDIA, streams = emptyList()),
            nextAlarmLabel = null,
            liveActivity = null,
        )
    },
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val _uiState = MutableStateFlow(CapsuleUiState())

    /** Reactive stream of the current capsule visual state and payload. */
    val uiState: StateFlow<CapsuleUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CapsuleEffect>(extraBufferCapacity = 8)

    /** One-shot navigation effects emitted when tapping app-backed capsules or cards. */
    val effects: SharedFlow<CapsuleEffect> = _effects.asSharedFlow()

    private var activeMedia: CapsuleContent.Media? = null
    private var activeLiveActivity: CapsuleContent.LiveActivity? = null
    private var activeTorch: CapsuleContent.Torch? = null
    private var activeBrightness: BrightnessInfo? = null
    private var activeWeather: WeatherSummary? = null
    private var activeNotificationCount: Int? = null
    private var batteryPercent: Int = 85
    private var isCharging: Boolean = false
    private var isLandscape: Boolean = false
    private var isScreenInactive: Boolean = false

    private data class StackEntry(
        val stackId: String,
        val content: CapsuleContent,
        val isTransient: Boolean,
        val expiresAtMs: Long,
        val priority: Int,
        val updatedAtMs: Long,
        val burstUntilMs: Long = 0L,
    )

    private val viewStack = mutableListOf<StackEntry>()
    private var currentStackId: String? = null
    private var currentStackShownAtMs: Long = 0L
    private var manualSwipeSeq: Int = 0
    private var swipeJiggleSeq: Int = 0

    private var manualOverrideStackId: String? = null
    private var manualOverrideExpiresAtMs: Long = 0L

    private var stackScheduleJob: Job? = null
    private var manualOverrideJob: Job? = null
    private var state3AutoCollapseJob: Job? = null
    private var neuralPulseJob: Job? = null
    private var collapseWaveJob: Job? = null
    private var ringGlowJob: Job? = null
    private var isHoldFromRingActive: Boolean = false
    private var hasInteractedInCurrentState3: Boolean = false
    private var hasInteractedNotificationInCard: Boolean = false

    /** Updates measured device camera cutout and screen geometry when changed. */
    fun updateCutoutGeometry(cutout: CutoutGeometry) {
        if (_uiState.value.cutout != cutout) {
            _uiState.update { current -> current.copy(cutout = cutout) }
        }
    }

    /** Updates user preferences for cutout alignment and auto-collapse timing when changed. */
    fun updatePreferences(preferences: CapsulePreferences) {
        if (_uiState.value.preferences != preferences) {
            _uiState.update { current -> current.copy(preferences = preferences) }
            if (_uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                scheduleState3AutoCollapse()
            }
        }
    }

    /** Processes a system, media, notification, or user gesture event. */
    fun onEvent(event: CapsuleEvent) {
        when (event) {
            is CapsuleEvent.OrientationChanged -> {
                if (isLandscape != event.isLandscape) {
                    isLandscape = event.isLandscape
                    syncHiddenOrActive()
                }
            }
            is CapsuleEvent.ScreenInactiveChanged -> {
                val nextInactive = event.isScreenInactive
                if (isScreenInactive != nextInactive) {
                    isScreenInactive = nextInactive
                    syncHiddenOrActive()
                }
            }
            is CapsuleEvent.BatteryUpdated -> handleBatteryUpdated(event.percent, event.isCharging)
            is CapsuleEvent.LiveActivityUpdated -> handleLiveActivityUpdated(event.liveActivity)
            is CapsuleEvent.NotificationPosted -> handleNotificationPosted(event.notification)
            is CapsuleEvent.NotificationRemoved -> handleNotificationRemoved(event.key)
            is CapsuleEvent.MediaUpdated -> handleMediaUpdated(event.media)
            is CapsuleEvent.TorchUpdated -> handleTorchUpdated(event.torch)
            is CapsuleEvent.VolumeChanged -> handleVolumeChanged(event.volume)
            is CapsuleEvent.AlarmUpdated -> enqueueTransient(
                stackId = STACK_ALARM,
                content = event.alarm,
                priority = PRIORITY_ALARM,
                durationMs = effectiveAutoCollapseMs(),
            )
            is CapsuleEvent.DeviceUnlocked -> enqueueTransient(
                stackId = STACK_UNLOCK,
                content = event.unlock,
                priority = PRIORITY_UNLOCK,
                durationMs = effectiveAutoCollapseMs(),
            )
            CapsuleEvent.HoldPeekStarted -> handleHoldPeekStarted()
            CapsuleEvent.HoldPeekReleased -> handleHoldPeekReleased()
            CapsuleEvent.ExpandToFull -> handleExpandToFull()
            CapsuleEvent.UserInteractedInCard -> handleCardInteraction()
            is CapsuleEvent.CycleContent -> handleCycleContent(event.direction)
            CapsuleEvent.TapCapsule -> handleTapInteraction()
            CapsuleEvent.CollapseCapsule -> collapseToBaseState()
            is CapsuleEvent.BrightnessChanged -> handleBrightnessChanged(event.brightness)
            is CapsuleEvent.WeatherUpdated -> handleWeatherUpdated(event.weather)
            is CapsuleEvent.ActiveNotificationCountUpdated -> handleActiveNotificationCountUpdated(event.count)
        }
    }

    private fun syncHiddenOrActive() {
        if (isLandscape || isScreenInactive) {
            isHoldFromRingActive = false
            clearManualOverride()
            viewStack.removeAll { it.stackId == STACK_CHARGING_PEEK || it.stackId == STACK_BATTERY_PEEK }
            cancelAllTimers()
            currentStackId = null
            manualOverrideStackId = null
            _uiState.update { current ->
                current.copy(
                    visualState = CapsuleVisualState.HIDDEN_SYSTEM,
                    isNeuralPulseActive = false,
                    isCollapseWaveActive = false,
                    isRingGlowActive = false,
                )
            }
        } else {
            collapseToBaseState(dismissShownTransient = false)
        }
    }

    private val isHiddenByEnvironment: Boolean
        get() = isLandscape || isScreenInactive

    private fun handleBatteryUpdated(percent: Int, charging: Boolean) {
        val wasCharging = isCharging
        batteryPercent = percent.coerceIn(0, 100)
        isCharging = charging

        _uiState.update { current ->
            val updatedContent = when (val c = current.content) {
                is CapsuleContent.Charging -> c.copy(batteryPercent = batteryPercent)
                is CapsuleContent.BatteryPeek -> c.copy(batteryPercent = batteryPercent)
                is CapsuleContent.NowBrief -> c.copy(
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                )
                else -> c
            }
            current.copy(
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                content = updatedContent,
            )
        }

        upsertInPlaceIfPresent(STACK_CHARGING_PEEK) {
            CapsuleContent.Charging(batteryPercent = batteryPercent)
        }
        upsertInPlaceIfPresent(STACK_BATTERY_PEEK) {
            CapsuleContent.BatteryPeek(batteryPercent = batteryPercent)
        }

        if (isHiddenByEnvironment) return
        if (!wasCharging && charging) {
            enqueueTransient(
                stackId = STACK_CHARGING_PEEK,
                content = CapsuleContent.Charging(batteryPercent = batteryPercent),
                priority = PRIORITY_CHARGING,
                durationMs = effectiveAutoCollapseMs(),
                allowBurst = false,
            )
        } else if (wasCharging && !charging) {
            if (manualOverrideStackId == STACK_CHARGING_PEEK) {
                clearManualOverride()
            }
            removeStackItem(STACK_CHARGING_PEEK, allowImmediateReplace = true)
        }
    }

    private fun handleLiveActivityUpdated(liveActivity: CapsuleContent.LiveActivity?) {
        activeLiveActivity = liveActivity

        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            val content = currentState.content
            if (content is CapsuleContent.NowBrief) {
                _uiState.update { it.copy(content = content.copy(liveActivity = liveActivity)) }
            }
        }

        if (liveActivity != null) {
            upsertPersistent(
                stackId = "live:${liveActivity.key}",
                content = liveActivity,
                priority = PRIORITY_LIVE_ACTIVITY,
            )
        } else {
            removeMatchingStackItems(allowImmediateReplace = false) { it.stackId.startsWith("live:") }
        }
    }

    private fun handleNotificationPosted(notification: CapsuleContent.NotificationAlert) {
        val stackId = "notif:${notification.key}"
        val existing = viewStack.firstOrNull { it.stackId == stackId }
        val existingAlert = existing?.content as? CapsuleContent.NotificationAlert
        if (existingAlert != null &&
            existingAlert.title == notification.title &&
            existingAlert.text == notification.text
        ) {
            return
        }

        val now = clockMillis()
        val durationMs = min(effectiveAutoCollapseMs(), MAX_NOTIFICATION_AUTO_DISMISS_MS)
        val entry = StackEntry(
            stackId = stackId,
            content = notification,
            isTransient = true,
            expiresAtMs = now + durationMs,
            priority = PRIORITY_NOTIFICATION,
            updatedAtMs = now,
            burstUntilMs = now + durationMs,
        )
        val existingIdx = viewStack.indexOfFirst { it.stackId == stackId }
        if (existingIdx >= 0) {
            viewStack[existingIdx] = entry
        } else {
            viewStack.add(entry)
        }

        if (isHiddenByEnvironment || isHoldFromRingActive) return

        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            val shown = currentState.content
            if (shown is CapsuleContent.NotificationAlert) {
                val userPinnedThisNotification = hasInteractedNotificationInCard ||
                    manualOverrideStackId == "notif:${shown.key}"
                if (!userPinnedThisNotification) {
                    enterState3Expanded(notification)
                }
            }
            return
        }

        // System-like notification behavior: immediately show newest notification unless user manually pinned one
        val userPinnedNotification = manualOverrideStackId?.startsWith("notif:") == true
        if (!userPinnedNotification) {
            clearManualOverride()
            forceShowStackEntry(stackId, notification, withPulse = true)
        }
    }

    private fun handleNotificationRemoved(key: String) {
        if (manualOverrideStackId == "notif:$key") {
            clearManualOverride()
        }
        removeStackItem("notif:$key", allowImmediateReplace = false)
        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            val shown = currentState.content
            if (shown is CapsuleContent.NotificationAlert && shown.key == key) {
                collapseToBaseState()
            }
        }
    }

    private fun handleMediaUpdated(media: CapsuleContent.Media?) {
        activeMedia = media

        val isPlaying = media != null && media.isPlaying
        if (!isPlaying &&
            _uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED &&
            _uiState.value.content is CapsuleContent.Media &&
            media == null
        ) {
            viewStack.removeAll { it.stackId == STACK_MEDIA }
            collapseToBaseState()
            return
        }

        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            if (media != null && currentState.content is CapsuleContent.Media) {
                _uiState.update { it.copy(content = media) }
            }
        }

        if (isPlaying) {
            upsertPersistent(
                stackId = STACK_MEDIA,
                content = media,
                priority = PRIORITY_MEDIA,
            )
        } else {
            removeStackItem(STACK_MEDIA, allowImmediateReplace = false)
        }
    }

    private fun handleTorchUpdated(torch: CapsuleContent.Torch) {
        activeTorch = torch.takeIf { it.isEnabled }
        if (isHiddenByEnvironment) return

        val currentState = _uiState.value
        val currentContent = currentState.content
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            if (torch.isEnabled) {
                if (currentContent is CapsuleContent.NowBrief || currentContent is CapsuleContent.Torch) {
                    // Morph cleanly into dedicated square Flashlight box card!
                    _uiState.update { it.copy(content = torch) }
                    scheduleState3AutoCollapse()
                }
            } else if (currentContent is CapsuleContent.Torch) {
                viewStack.removeAll { it.stackId == STACK_TORCH }
                // Keep State 3 open after Turn Off so user can toggle back ON or let auto-collapse expire
                _uiState.update { it.copy(content = torch) }
                scheduleState3AutoCollapse()
                return
            } else if (currentContent is CapsuleContent.NowBrief) {
                _uiState.update { it.copy(content = currentContent.copy(torch = torch)) }
            }
        }

        if (torch.isEnabled) {
            upsertPersistent(
                stackId = STACK_TORCH,
                content = torch,
                priority = PRIORITY_TORCH,
            )
        } else {
            if (manualOverrideStackId == STACK_TORCH) {
                clearManualOverride()
            }
            removeStackItem(STACK_TORCH, allowImmediateReplace = false)
        }
    }

    private fun handleVolumeChanged(volume: CapsuleContent.Volume) {
        if (isHiddenByEnvironment) return

        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            when (val content = currentState.content) {
                is CapsuleContent.NowBrief -> {
                    _uiState.update { it.copy(content = content.copy(volume = volume)) }
                    return
                }
                is CapsuleContent.Volume -> {
                    _uiState.update { it.copy(content = volume) }
                    scheduleState3AutoCollapse()
                    return
                }
                else -> Unit
            }
        }

        enqueueTransient(
            stackId = STACK_VOLUME,
            content = volume,
            priority = PRIORITY_VOLUME,
            durationMs = fastLowPriorityMs(),
            minDurationMs = MIN_LOW_PRIORITY_COLLAPSE_MS,
        )
    }

    private fun handleBrightnessChanged(brightness: BrightnessInfo) {
        activeBrightness = brightness
        if (isHiddenByEnvironment) return
        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            val content = currentState.content
            if (content is CapsuleContent.NowBrief) {
                _uiState.update { it.copy(content = content.copy(brightness = brightness)) }
            }
        }
    }

    private fun handleWeatherUpdated(weather: WeatherSummary?) {
        activeWeather = weather
        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            val content = currentState.content
            if (content is CapsuleContent.NowBrief) {
                _uiState.update { it.copy(content = content.copy(weather = weather)) }
            }
        }
    }

    private fun handleActiveNotificationCountUpdated(count: Int) {
        val safeCount = count.coerceAtLeast(0)
        activeNotificationCount = safeCount
        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            val content = currentState.content
            if (content is CapsuleContent.NowBrief) {
                _uiState.update { it.copy(content = content.copy(activeNotificationCount = safeCount)) }
            }
        }
    }

    private fun buildNowBrief(
        liveOverride: CapsuleContent.LiveActivity? = activeLiveActivity,
    ): CapsuleContent.NowBrief {
        val base = nowBriefProvider()
        return base.copy(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            liveActivity = liveOverride ?: base.liveActivity,
            brightness = activeBrightness ?: base.brightness,
            weather = activeWeather ?: base.weather,
            activeNotificationCount = activeNotificationCount ?: base.activeNotificationCount,
        )
    }

    private fun handleHoldPeekStarted() {
        if (isHiddenByEnvironment) return
        if (_uiState.value.visualState != CapsuleVisualState.STATE1_CUTOUT_ANCHOR) return

        isHoldFromRingActive = true
        val (stackId, peekContent) = if (isCharging) {
            STACK_CHARGING_PEEK to CapsuleContent.Charging(batteryPercent = batteryPercent)
        } else {
            STACK_BATTERY_PEEK to CapsuleContent.BatteryPeek(batteryPercent = batteryPercent)
        }
        val now = clockMillis()
        viewStack.removeAll { it.stackId == STACK_CHARGING_PEEK || it.stackId == STACK_BATTERY_PEEK }
        viewStack.add(
            StackEntry(
                stackId = stackId,
                content = peekContent,
                isTransient = true,
                expiresAtMs = now + max(MIN_VIEW_DWELL_MS, effectiveAutoCollapseMs()),
                priority = PRIORITY_MANUAL_PEEK,
                updatedAtMs = now,
            ),
        )
        forceShowStackEntry(stackId, peekContent, withPulse = true)
    }

    private fun handleHoldPeekReleased() {
        isHoldFromRingActive = false
        if (isHiddenByEnvironment) return
        if (_uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) return
        val activeId = currentStackId ?: return
        if (activeId == STACK_CHARGING_PEEK || activeId == STACK_BATTERY_PEEK) {
            val now = clockMillis()
            val idx = viewStack.indexOfFirst { it.stackId == activeId }
            if (idx >= 0) {
                viewStack[idx] = viewStack[idx].copy(
                    expiresAtMs = now + max(MIN_VIEW_DWELL_MS, effectiveAutoCollapseMs()),
                )
            }
            scheduleNextStackTick()
        }
    }

    /**
     * Computes the exact State 3 expanded payload matching current stack priority without mutating
     * coordinator state, used by interactive vertical scrub previews before full expansion commits.
     */
    fun resolveExpandTargetContent(): CapsuleContent {
        if (isHoldFromRingActive) return buildNowBrief()
        return when (val content = _uiState.value.content) {
            is CapsuleContent.Media,
            is CapsuleContent.NotificationAlert,
            is CapsuleContent.Torch,
            is CapsuleContent.Volume,
            is CapsuleContent.Alarm,
            is CapsuleContent.Unlock,
            -> content
            else -> buildNowBrief()
        }
    }

    /**
     * Resolves the canonical compact stage `(visualState, content)` that State 3 would collapse back
     * into cleanly, keeping interactive drag-to-collapse synced with live stack priority or latest
     * active manual selection.
     */
    fun resolveCollapseTarget(): Pair<CapsuleVisualState, CapsuleContent> {
        val now = clockMillis()
        val dismissedId = if (_uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            transientDismissStackId(_uiState.value.content)
        } else {
            null
        }
        val overrideId = manualOverrideStackId
        val overrideCandidate = if (overrideId != null && overrideId != dismissedId && now < manualOverrideExpiresAtMs) {
            viewStack.firstOrNull { it.stackId == overrideId }
                ?: buildCarouselEntries(now).firstOrNull { it.stackId == overrideId }
        } else {
            null
        }
        val candidate = overrideCandidate ?: viewStack
            .asSequence()
            .filterNot { entry ->
                entry.stackId == STACK_CHARGING_PEEK ||
                    entry.stackId == STACK_BATTERY_PEEK ||
                    entry.stackId == dismissedId ||
                    (entry.isTransient && entry.expiresAtMs <= now)
            }
            .maxWithOrNull(stackPriorityComparator(now))
        return if (candidate != null) {
            CapsuleVisualState.STATE2_HORIZONTAL_PILL to candidate.content
        } else {
            CapsuleVisualState.STATE1_CUTOUT_ANCHOR to CapsuleContent.Idle
        }
    }

    private fun handleExpandToFull() {
        if (isHiddenByEnvironment) return
        val currentState = _uiState.value
        if (currentState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            collapseToBaseState()
            return
        }

        val expandedContent = resolveExpandTargetContent()
        isHoldFromRingActive = false
        val overrideId = manualOverrideStackId
        if (overrideId != null) {
            val now = clockMillis()
            manualOverrideExpiresAtMs = now + MANUAL_CYCLE_TIMEOUT_MS
            scheduleManualOverrideReset()
        }
        viewStack.removeAll { entry ->
            (entry.stackId == STACK_CHARGING_PEEK || entry.stackId == STACK_BATTERY_PEEK) &&
                entry.stackId != overrideId
        }
        enterState3Expanded(expandedContent)
    }

    private fun handleCycleContent(direction: Int) {
        if (isHiddenByEnvironment) return
        val dir = if (direction < 0) -1 else 1
        val now = clockMillis()
        pruneExpired(now)

        val carousel = buildCarouselEntries(now)
        if (carousel.isEmpty()) return

        val activeId = currentStackId ?: findMatchingStackId(_uiState.value.content)
        val currentIndex = carousel.indexOfFirst { it.stackId == activeId }
        val targetIndex = if (currentIndex < 0) {
            if (dir > 0) 0 else carousel.lastIndex
        } else {
            (currentIndex + dir).mod(carousel.size)
        }
        val targetEntry = carousel[targetIndex]

        if (carousel.size <= 1 || targetEntry.stackId == activeId) {
            swipeJiggleSeq++
            _uiState.update { it.copy(swipeDirection = dir, swipeJiggleSeq = swipeJiggleSeq) }
            return
        }

        // Start 10-second user interaction hold on the manually selected view before returning to canonical priority
        manualOverrideStackId = targetEntry.stackId
        manualOverrideExpiresAtMs = now + MANUAL_CYCLE_TIMEOUT_MS
        scheduleManualOverrideReset()

        // Ensure chosen slot stays present in viewStack during the 10-second manual hold if needed
        val existingIdx = viewStack.indexOfFirst { it.stackId == targetEntry.stackId }
        if (existingIdx < 0 && targetEntry.isTransient) {
            viewStack.add(targetEntry.copy(expiresAtMs = manualOverrideExpiresAtMs))
        }

        manualSwipeSeq++
        _uiState.update { it.copy(swipeDirection = dir, manualSwipeSeq = manualSwipeSeq) }
        if (_uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            val resolvedCard = when (targetEntry.content) {
                is CapsuleContent.Charging, is CapsuleContent.BatteryPeek -> buildNowBrief()
                else -> targetEntry.content
            }
            enterState3Expanded(resolvedCard)
        } else {
            forceShowStackEntry(targetEntry.stackId, targetEntry.content, withPulse = true)
        }
    }

    private fun handleTapInteraction() {
        if (isHiddenByEnvironment) return
        val currentState = _uiState.value
        when (currentState.visualState) {
            CapsuleVisualState.HIDDEN_SYSTEM -> Unit
            CapsuleVisualState.STATE1_CUTOUT_ANCHOR -> {
                val (stackId, peekContent) = if (isCharging) {
                    STACK_CHARGING_PEEK to CapsuleContent.Charging(batteryPercent = batteryPercent)
                } else {
                    STACK_BATTERY_PEEK to CapsuleContent.BatteryPeek(batteryPercent = batteryPercent)
                }
                val now = clockMillis()
                viewStack.removeAll { it.stackId == STACK_CHARGING_PEEK || it.stackId == STACK_BATTERY_PEEK }
                viewStack.add(
                    StackEntry(
                        stackId = stackId,
                        content = peekContent,
                        isTransient = true,
                        expiresAtMs = now + max(MIN_VIEW_DWELL_MS, effectiveAutoCollapseMs()),
                        priority = PRIORITY_MANUAL_PEEK,
                        updatedAtMs = now,
                    ),
                )
                forceShowStackEntry(stackId, peekContent, withPulse = true)
            }
            CapsuleVisualState.STATE2_HORIZONTAL_PILL -> {
                when (val content = currentState.content) {
                    is CapsuleContent.Media,
                    is CapsuleContent.NotificationAlert,
                    is CapsuleContent.Volume,
                    is CapsuleContent.Torch,
                    is CapsuleContent.Alarm,
                    is CapsuleContent.Unlock,
                    -> enterState3Expanded(content)
                    is CapsuleContent.BatteryPeek,
                    is CapsuleContent.Charging,
                    -> enterState3Expanded(buildNowBrief())
                    is CapsuleContent.LiveActivity -> {
                        val pkg = content.packageName
                        if (!pkg.isNullOrBlank()) {
                            _effects.tryEmit(CapsuleEffect.LaunchApp(packageName = pkg, notificationKey = content.key))
                            collapseToBaseState()
                        } else {
                            enterState3Expanded(buildNowBrief(liveOverride = content))
                        }
                    }
                    is CapsuleContent.NowBrief, CapsuleContent.Idle -> Unit
                }
            }
            CapsuleVisualState.STATE3_FULL_EXPANDED -> {
                when (val content = currentState.content) {
                    // Notification actions (Reply / Open / Dismiss) live cleanly inside the expanded card
                    is CapsuleContent.NotificationAlert -> handleCardInteraction()
                    is CapsuleContent.Media -> {
                        _effects.tryEmit(CapsuleEffect.LaunchApp(packageName = content.packageName))
                        collapseToBaseState()
                    }
                    is CapsuleContent.LiveActivity -> {
                        content.packageName?.let { pkg ->
                            _effects.tryEmit(CapsuleEffect.LaunchApp(packageName = pkg, notificationKey = content.key))
                        }
                        collapseToBaseState()
                    }
                    else -> handleCardInteraction()
                }
            }
        }
    }

    private fun handleCardInteraction() {
        val now = clockMillis()
        hasInteractedInCurrentState3 = true
        if (_uiState.value.content is CapsuleContent.NotificationAlert) {
            hasInteractedNotificationInCard = true
        }
        if (manualOverrideStackId != null) {
            manualOverrideExpiresAtMs = now + MANUAL_CYCLE_TIMEOUT_MS
            scheduleManualOverrideReset()
        }
        if (_uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            scheduleState3AutoCollapse()
        }
    }

    private fun enterState3Expanded(content: CapsuleContent) {
        stackScheduleJob?.cancel()
        stackScheduleJob = null
        collapseWaveJob?.cancel()
        collapseWaveJob = null
        ringGlowJob?.cancel()
        ringGlowJob = null
        currentStackId = null
        hasInteractedInCurrentState3 = false
        hasInteractedNotificationInCard = false
        _uiState.update { current ->
            current.copy(
                visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
                content = content,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                isNeuralPulseActive = true,
                isCollapseWaveActive = false,
                isRingGlowActive = false,
            )
        }
        triggerNeuralPulseCooldown()
        scheduleState3AutoCollapse()
    }

    private fun collapseToBaseState(dismissShownTransient: Boolean = true) {
        isHoldFromRingActive = false
        hasInteractedInCurrentState3 = false
        hasInteractedNotificationInCard = false
        state3AutoCollapseJob?.cancel()
        state3AutoCollapseJob = null
        neuralPulseJob?.cancel()
        neuralPulseJob = null

        val previousVisualState = _uiState.value.visualState
        val wasState3 = previousVisualState == CapsuleVisualState.STATE3_FULL_EXPANDED
        val wasExpandedOrPill = wasState3 || previousVisualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL

        val dismissedId = if (dismissShownTransient && wasState3) {
            transientDismissStackId(_uiState.value.content)
        } else {
            null
        }
        if (dismissedId != null) {
            viewStack.removeAll { it.stackId == dismissedId }
            if (manualOverrideStackId == dismissedId) {
                clearManualOverride()
            }
        }

        if (isHiddenByEnvironment) {
            clearManualOverride()
            viewStack.removeAll { it.stackId == STACK_CHARGING_PEEK || it.stackId == STACK_BATTERY_PEEK }
            currentStackId = null
            _uiState.update {
                it.copy(
                    visualState = CapsuleVisualState.HIDDEN_SYSTEM,
                    isNeuralPulseActive = false,
                    isCollapseWaveActive = false,
                    isRingGlowActive = false,
                )
            }
            return
        }

        val now = clockMillis()
        val activeOverrideId = manualOverrideStackId
        if (activeOverrideId != null) {
            val refreshedExpiry = now + MANUAL_CYCLE_TIMEOUT_MS
            manualOverrideExpiresAtMs = refreshedExpiry
            val overrideIdx = viewStack.indexOfFirst { it.stackId == activeOverrideId }
            if (overrideIdx >= 0 && viewStack[overrideIdx].isTransient) {
                viewStack[overrideIdx] = viewStack[overrideIdx].copy(expiresAtMs = refreshedExpiry)
            }
            scheduleManualOverrideReset()
        }
        viewStack.removeAll { entry ->
            (entry.stackId == STACK_CHARGING_PEEK || entry.stackId == STACK_BATTERY_PEEK) &&
                entry.stackId != activeOverrideId
        }

        pruneExpired(now)
        val pinnedOverride = activeOverrideId?.let { id ->
            viewStack.firstOrNull { it.stackId == id }
                ?: buildCarouselEntries(now).firstOrNull { it.stackId == id }
        }
        val top = pinnedOverride ?: selectTopCandidate(now)
        if (top != null) {
            forceShowStackEntry(top.stackId, top.content, withPulse = false)
            if (wasState3) {
                triggerCollapseWavePulse()
            }
        } else {
            clearManualOverride()
            currentStackId = null
            _uiState.update { current ->
                current.copy(
                    visualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
                    content = CapsuleContent.Idle,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    isNeuralPulseActive = false,
                )
            }
            if (wasState3) {
                triggerCollapseWavePulse()
            }
            if (wasExpandedOrPill) {
                triggerRingGlowPulse()
            }
        }
    }

    private fun enqueueTransient(
        stackId: String,
        content: CapsuleContent,
        priority: Int,
        durationMs: Long,
        allowBurst: Boolean = true,
        minDurationMs: Long = MIN_VIEW_DWELL_MS,
    ) {
        val now = clockMillis()
        val effectiveDuration = max(minDurationMs, durationMs)
        val newEntry = StackEntry(
            stackId = stackId,
            content = content,
            isTransient = true,
            expiresAtMs = now + effectiveDuration,
            priority = priority,
            updatedAtMs = now,
            burstUntilMs = if (allowBurst) now + effectiveDuration else 0L,
        )
        val existingIdx = viewStack.indexOfFirst { it.stackId == stackId }
        if (existingIdx >= 0) {
            viewStack[existingIdx] = newEntry
        } else {
            viewStack.add(newEntry)
        }
        evaluateViewStack(allowPulse = true)
    }

    private fun upsertPersistent(
        stackId: String,
        content: CapsuleContent,
        priority: Int,
    ) {
        val now = clockMillis()
        val existingIdx = viewStack.indexOfFirst { it.stackId == stackId }
        val entry = StackEntry(
            stackId = stackId,
            content = content,
            isTransient = false,
            expiresAtMs = Long.MAX_VALUE,
            priority = priority,
            updatedAtMs = now,
        )
        if (existingIdx >= 0) {
            viewStack[existingIdx] = entry
        } else {
            viewStack.add(entry)
        }
        evaluateViewStack(allowPulse = existingIdx < 0)
    }

    private inline fun upsertInPlaceIfPresent(
        stackId: String,
        crossinline transform: (StackEntry) -> CapsuleContent,
    ) {
        val idx = viewStack.indexOfFirst { it.stackId == stackId }
        if (idx >= 0) {
            val current = viewStack[idx]
            viewStack[idx] = current.copy(content = transform(current))
        }
    }

    private fun removeStackItem(stackId: String, allowImmediateReplace: Boolean) {
        removeMatchingStackItems(allowImmediateReplace) { it.stackId == stackId }
    }

    private inline fun removeMatchingStackItems(
        allowImmediateReplace: Boolean,
        crossinline predicate: (StackEntry) -> Boolean,
    ) {
        val removedCurrent = currentStackId?.let { active ->
            viewStack.any { it.stackId == active && predicate(it) }
        } ?: false
        val changed = viewStack.removeAll { predicate(it) }
        if (changed) {
            if (removedCurrent && allowImmediateReplace) {
                currentStackId = null
            }
            evaluateViewStack(allowPulse = false)
        }
    }

    private fun evaluateViewStack(allowPulse: Boolean) {
        if (isHiddenByEnvironment) return
        val state = _uiState.value
        if (state.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) return
        if (isHoldFromRingActive) {
            val heldPeek = viewStack.firstOrNull {
                it.stackId == STACK_CHARGING_PEEK || it.stackId == STACK_BATTERY_PEEK
            }
            if (heldPeek != null) {
                currentStackId = heldPeek.stackId
                applyState2Update(heldPeek.content, withPulse = null)
                return
            }
        }

        val now = clockMillis()
        pruneExpired(now)

        val overrideId = manualOverrideStackId
        if (overrideId != null && now < manualOverrideExpiresAtMs) {
            val pinned = viewStack.firstOrNull { it.stackId == overrideId }
                ?: buildCarouselEntries(now).firstOrNull { it.stackId == overrideId }
            if (pinned != null) {
                applyState2Update(pinned.content, withPulse = null)
                scheduleNextStackTick()
                return
            }
        }

        val activeId = currentStackId
        if (activeId != null) {
            val activeInStack = viewStack.firstOrNull { it.stackId == activeId }
            val elapsed = now - currentStackShownAtMs

            if (activeInStack != null) {
                applyState2Update(activeInStack.content, withPulse = null)
                if (elapsed < MIN_VIEW_DWELL_MS) {
                    scheduleNextStackTick()
                    return
                }
            } else {
                // Expired or removed item clears dwell lock immediately so ring size returns promptly
                currentStackId = null
            }
        }

        val top = selectTopCandidate(now)
        if (top == null) {
            val wasPill = state.visualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL
            currentStackId = null
            _uiState.update { current ->
                current.copy(
                    visualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
                    content = CapsuleContent.Idle,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    isNeuralPulseActive = false,
                )
            }
            if (wasPill) {
                triggerRingGlowPulse()
            }
            return
        }

        if (top.stackId != currentStackId || state.visualState != CapsuleVisualState.STATE2_HORIZONTAL_PILL) {
            forceShowStackEntry(top.stackId, top.content, withPulse = allowPulse)
        } else {
            applyState2Update(top.content, withPulse = null)
            scheduleNextStackTick()
        }
    }

    private fun forceShowStackEntry(stackId: String, content: CapsuleContent, withPulse: Boolean) {
        val now = clockMillis()
        currentStackId = stackId
        currentStackShownAtMs = now
        applyState2Update(content, withPulse = withPulse)
        if (withPulse) {
            triggerNeuralPulseCooldown()
        }
        scheduleNextStackTick()
    }

    private fun applyState2Update(
        content: CapsuleContent,
        withPulse: Boolean?,
    ) {
        _uiState.update { current ->
            current.copy(
                visualState = CapsuleVisualState.STATE2_HORIZONTAL_PILL,
                content = content,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                isNeuralPulseActive = withPulse ?: current.isNeuralPulseActive,
            )
        }
    }

    private fun buildCarouselEntries(now: Long): List<StackEntry> {
        val items = LinkedHashMap<String, StackEntry>()
        viewStack.forEach { items[it.stackId] = it }

        activeMedia?.let { media ->
            items.putIfAbsent(
                STACK_MEDIA,
                StackEntry(
                    stackId = STACK_MEDIA,
                    content = media,
                    isTransient = !media.isPlaying,
                    expiresAtMs = if (media.isPlaying) Long.MAX_VALUE else now + MANUAL_CYCLE_TIMEOUT_MS,
                    priority = if (media.isPlaying) PRIORITY_MEDIA else PRIORITY_PAUSED_MEDIA,
                    updatedAtMs = now,
                ),
            )
        }

        if (isCharging) {
            items.putIfAbsent(
                STACK_CHARGING_PEEK,
                StackEntry(
                    stackId = STACK_CHARGING_PEEK,
                    content = CapsuleContent.Charging(batteryPercent = batteryPercent),
                    isTransient = true,
                    expiresAtMs = now + MANUAL_CYCLE_TIMEOUT_MS,
                    priority = PRIORITY_CHARGING,
                    updatedAtMs = now,
                ),
            )
        } else {
            items.putIfAbsent(
                STACK_BATTERY_PEEK,
                StackEntry(
                    stackId = STACK_BATTERY_PEEK,
                    content = CapsuleContent.BatteryPeek(batteryPercent = batteryPercent),
                    isTransient = true,
                    expiresAtMs = now + MANUAL_CYCLE_TIMEOUT_MS,
                    priority = PRIORITY_BATTERY_IDLE,
                    updatedAtMs = now,
                ),
            )
        }

        return items.values.sortedWith(
            compareByDescending<StackEntry> { it.priority }.thenByDescending { it.updatedAtMs },
        )
    }

    private fun findMatchingStackId(content: CapsuleContent): String? = when (content) {
        is CapsuleContent.Media -> STACK_MEDIA
        is CapsuleContent.Charging -> STACK_CHARGING_PEEK
        is CapsuleContent.BatteryPeek -> STACK_BATTERY_PEEK
        is CapsuleContent.Torch -> STACK_TORCH
        is CapsuleContent.Volume -> STACK_VOLUME
        is CapsuleContent.Alarm -> STACK_ALARM
        is CapsuleContent.Unlock -> STACK_UNLOCK
        is CapsuleContent.NotificationAlert -> "notif:${content.key}"
        is CapsuleContent.LiveActivity -> "live:${content.key}"
        is CapsuleContent.NowBrief, CapsuleContent.Idle -> null
    }

    private fun scheduleManualOverrideReset() {
        manualOverrideJob?.cancel()
        val now = clockMillis()
        val waitMs = (manualOverrideExpiresAtMs - now).coerceAtLeast(1L)
        manualOverrideJob = scope.launch {
            delay(waitMs.milliseconds)
            if (_uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                return@launch
            }
            manualOverrideStackId = null
            manualOverrideExpiresAtMs = 0L
            // Clear transient burst boosts & manual peeks so resting canonical priority wins immediately:
            // Media > LiveActivity > Notification > Other
            viewStack.removeAll { it.stackId == STACK_CHARGING_PEEK || it.stackId == STACK_BATTERY_PEEK }
            viewStack.indices.forEach { idx ->
                if (viewStack[idx].burstUntilMs > 0L) {
                    viewStack[idx] = viewStack[idx].copy(burstUntilMs = 0L)
                }
            }
            evaluateViewStack(allowPulse = true)
        }
    }

    private fun clearManualOverride() {
        manualOverrideJob?.cancel()
        manualOverrideJob = null
        manualOverrideStackId = null
        manualOverrideExpiresAtMs = 0L
    }

    private fun scheduleNextStackTick() {
        stackScheduleJob?.cancel()
        val now = clockMillis()
        val activeId = currentStackId
        val nextWakeMs = buildList {
            if (activeId != null) {
                val dwellRemaining = (currentStackShownAtMs + MIN_VIEW_DWELL_MS) - now
                if (dwellRemaining > 0L) add(dwellRemaining)
            }
            viewStack.forEach { entry ->
                if (entry.isTransient && entry.expiresAtMs < Long.MAX_VALUE) {
                    val rem = entry.expiresAtMs - now
                    if (rem > 0L) add(rem)
                }
                if (entry.burstUntilMs > now && entry.burstUntilMs < entry.expiresAtMs) {
                    add(entry.burstUntilMs - now)
                }
            }
        }.minOrNull() ?: return

        stackScheduleJob = scope.launch {
            delay(nextWakeMs.milliseconds)
            evaluateViewStack(allowPulse = true)
        }
    }

    private fun pruneExpired(now: Long) {
        viewStack.removeAll { it.isTransient && it.expiresAtMs <= now }
    }

    private fun transientDismissStackId(content: CapsuleContent): String? = when (content) {
        is CapsuleContent.NotificationAlert -> "notif:${content.key}"
        is CapsuleContent.Volume -> STACK_VOLUME
        is CapsuleContent.Alarm -> STACK_ALARM
        is CapsuleContent.Unlock -> STACK_UNLOCK
        else -> null
    }

    private fun selectTopCandidate(now: Long): StackEntry? =
        viewStack.maxWithOrNull(stackPriorityComparator(now))

    private fun stackPriorityComparator(now: Long): Comparator<StackEntry> =
        compareBy<StackEntry> { effectivePriority(it, now) }.thenBy { it.updatedAtMs }

    private fun effectivePriority(entry: StackEntry, now: Long): Int =
        if (entry.burstUntilMs > now) PRIORITY_FRESH_BURST_BASE + entry.priority else entry.priority

    private fun effectiveAutoCollapseMs(): Long =
        _uiState.value.preferences.autoCollapseDelayMs.coerceAtLeast(MIN_VIEW_DWELL_MS)

    private fun fastLowPriorityMs(): Long {
        val configured = _uiState.value.preferences.autoCollapseDelayMs.coerceAtLeast(MIN_LOW_PRIORITY_COLLAPSE_MS)
        return (configured / 2L).coerceAtLeast(MIN_LOW_PRIORITY_COLLAPSE_MS)
    }

    private fun scheduleState3AutoCollapse() {
        state3AutoCollapseJob?.cancel()
        val rawDelayMs = _uiState.value.preferences.autoCollapseDelayMs.coerceAtLeast(1500L)
        val delayMs = when {
            _uiState.value.content is CapsuleContent.NotificationAlert ->
                min(rawDelayMs, MAX_NOTIFICATION_AUTO_DISMISS_MS)
            _uiState.value.content is CapsuleContent.Volume && !hasInteractedInCurrentState3 ->
                (rawDelayMs / 2L).coerceAtLeast(MIN_LOW_PRIORITY_COLLAPSE_MS)
            else -> rawDelayMs
        }
        state3AutoCollapseJob = scope.launch {
            delay(delayMs.milliseconds)
            if (_uiState.value.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                collapseToBaseState()
            }
        }
    }

    private fun triggerNeuralPulseCooldown() {
        neuralPulseJob?.cancel()
        neuralPulseJob = scope.launch {
            // 1450ms active hold + 550ms UI fade-out = 2.0s smooth dot-dot-dot neural disappear
            delay(NEURAL_DOT_BURST_HOLD_MS.milliseconds)
            _uiState.update { current -> current.copy(isNeuralPulseActive = false) }
        }
    }

    private fun triggerCollapseWavePulse() {
        collapseWaveJob?.cancel()
        _uiState.update { current -> current.copy(isCollapseWaveActive = true) }
        collapseWaveJob = scope.launch {
            // 760ms active hold + 260ms UI fade-out = ~1.0s collapse wave & neuron animation
            delay(COLLAPSE_WAVE_HOLD_MS.milliseconds)
            _uiState.update { current -> current.copy(isCollapseWaveActive = false) }
        }
    }

    private fun triggerRingGlowPulse() {
        ringGlowJob?.cancel()
        _uiState.update { current -> current.copy(isRingGlowActive = true) }
        ringGlowJob = scope.launch {
            // 760ms radiant hold + 280ms UI settle = ~1.0s battery ring glow back to normal
            delay(RING_GLOW_HOLD_MS.milliseconds)
            _uiState.update { current -> current.copy(isRingGlowActive = false) }
        }
    }

    private fun cancelAllTimers() {
        stackScheduleJob?.cancel()
        stackScheduleJob = null
        manualOverrideJob?.cancel()
        manualOverrideJob = null
        state3AutoCollapseJob?.cancel()
        state3AutoCollapseJob = null
        neuralPulseJob?.cancel()
        neuralPulseJob = null
        collapseWaveJob?.cancel()
        collapseWaveJob = null
        ringGlowJob?.cancel()
        ringGlowJob = null
    }

    companion object {
        const val MIN_VIEW_DWELL_MS = 2_000L
        const val MIN_LOW_PRIORITY_COLLAPSE_MS = 1_000L
        const val MAX_NOTIFICATION_AUTO_DISMISS_MS = 4_500L
        const val NEURAL_DOT_BURST_HOLD_MS = 1_900L
        const val COLLAPSE_WAVE_HOLD_MS = 760L
        const val RING_GLOW_HOLD_MS = 760L
        const val MANUAL_CYCLE_TIMEOUT_MS = 10_000L

        private const val STACK_MEDIA = "media"
        private const val STACK_TORCH = "torch"
        private const val STACK_VOLUME = "volume"
        private const val STACK_ALARM = "alarm"
        private const val STACK_UNLOCK = "unlock"
        private const val STACK_CHARGING_PEEK = "charging_peek"
        private const val STACK_BATTERY_PEEK = "battery_peek"

        // Resting canonical priority order: Notification > Media > LiveActivity > Other > Volume
        private const val PRIORITY_BATTERY_IDLE = 10
        private const val PRIORITY_PAUSED_MEDIA = 20
        private const val PRIORITY_VOLUME = 30
        private const val PRIORITY_TORCH = 40
        private const val PRIORITY_CHARGING = 50
        private const val PRIORITY_ALARM = 60
        private const val PRIORITY_UNLOCK = 70
        private const val PRIORITY_LIVE_ACTIVITY = 100
        private const val PRIORITY_MEDIA = 110
        private const val PRIORITY_NOTIFICATION = 130
        private const val PRIORITY_FRESH_BURST_BASE = 200
        private const val PRIORITY_MANUAL_PEEK = 300
    }
}
