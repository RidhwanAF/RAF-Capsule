package com.raf.capsuleos.presentation.simulation.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.capsule.ui.CapsuleLayoutDpSpec
import com.raf.capsuleos.presentation.capsule.ui.CapsuleScrubPresentation
import com.raf.capsuleos.presentation.capsule.ui.IslandCapsule
import com.raf.capsuleos.presentation.capsule.ui.computeCapsuleLayoutDpSpec
import com.raf.capsuleos.presentation.shader.OuterScreenExpansionAura
import com.raf.capsuleos.presentation.shader.resolveOuterExpansionAuraColors
import com.raf.capsuleos.presentation.shader.shouldShowOuterExpansionAura
import com.raf.capsuleos.presentation.simulation.SimulationUiState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive top-of-phone sandbox viewport hosting the real [IslandCapsule] composable over a
 * simulated camera punch-hole, status bar, and dynamic wallpaper. Supports all touch gestures and
 * 1:1 vertical finger scrub without touching real device hardware.
 */
@Composable
fun CapsulePhoneMockup(
    modifier: Modifier = Modifier,
    uiState: SimulationUiState,
    viewportHeight: Dp = 344.dp,
    onMockupWidthMeasured: (Float) -> Unit,
    onResolveExpandTarget: () -> CapsuleContent,
    onResolveCollapseTarget: () -> Pair<CapsuleVisualState, CapsuleContent>,
    onEvent: (CapsuleEvent) -> Unit,
    onTogglePlayPause: (String) -> Unit,
    onSkipNext: (String) -> Unit,
    onSkipPrevious: (String) -> Unit,
    onSeekTo: (String, Long) -> Unit,
    onSelectMediaSession: (Int) -> Unit = {},
    onOpenAudioOutput: () -> Unit = {},
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onCycleRingerMode: () -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
    onSetBrightnessPercent: (Int) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onAnswerCall: (CapsuleContent.CallAlert) -> Unit = {},
    onDeclineCall: (CapsuleContent.CallAlert) -> Unit = {},
    onOpenCallApp: (CapsuleContent.CallAlert) -> Unit = {},
    onOpenLiveActivityApp: (CapsuleContent.LiveActivity) -> Unit = {},
    onOpenSystemStatusSettings: (SystemStatusKind) -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {},
    onOpenHotspotSettings: () -> Unit = {},
    onLockScreenButton: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    onWakeFromLock: () -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onOpenMediaApp: () -> Unit,
    onOpenClock: () -> Unit,
) {
    val capsuleState = uiState.capsuleUiState
    val density = LocalDensity.current.density.coerceAtLeast(1f)
    val prefs = capsuleState.preferences

    var isHolding by remember { mutableStateOf(false) }
    var forceCameraCircleStage by remember { mutableStateOf(false) }
    var suppressSatelliteStage by remember { mutableStateOf(false) }
    var holdPillBeforeRingState by remember { mutableStateOf<CapsuleUiState?>(null) }
    var skipNextCollapseStaging by remember { mutableStateOf(false) }
    var state3EnteredAtMs by remember { mutableLongStateOf(0L) }
    var prevStateSnapshot by remember { mutableStateOf(capsuleState) }
    LaunchedEffect(capsuleState.visualState, capsuleState.isQsRingOnlyActive) {
        val prevSnapshot = prevStateSnapshot
        val prevExpanded = prevSnapshot.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
        val nowExpanded = capsuleState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
        skipNextCollapseStaging = false
        prevStateSnapshot = capsuleState

        if (nowExpanded && !prevExpanded) {
            state3EnteredAtMs = SystemClock.uptimeMillis()
        }

        if (capsuleState.isQsRingOnlyActive) {
            holdPillBeforeRingState = null
            forceCameraCircleStage = false
            suppressSatelliteStage = false
        } else if (prevExpanded && !nowExpanded && capsuleState.leftSatellite != null) {
            // Collapse fully into the main capsule FIRST with left circle completely gone;
            // only after the collapse has completely settled, reveal the left circle.
            holdPillBeforeRingState = null
            forceCameraCircleStage = false
            suppressSatelliteStage = true
            delay(500L)
            suppressSatelliteStage = false
        } else if (nowExpanded) {
            holdPillBeforeRingState = null
            forceCameraCircleStage = false
            suppressSatelliteStage = false
        }
    }
    var activeScrubSession by remember { mutableStateOf<MockupScrubSession?>(null) }
    var scrubPresentation by remember { mutableStateOf<CapsuleScrubPresentation?>(null) }
    val swipeJiggleOffsetDp = remember { Animatable(0f) }

    LaunchedEffect(capsuleState.swipeJiggleSeq) {
        if (capsuleState.swipeJiggleSeq > 0) {
            swipeJiggleOffsetDp.animateTo(-7f, tween(durationMillis = 70))
            swipeJiggleOffsetDp.animateTo(7f, tween(durationMillis = 85))
            swipeJiggleOffsetDp.animateTo(-3f, tween(durationMillis = 70))
            swipeJiggleOffsetDp.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 620f))
        }
    }

    val brightnessGain = 0.35f + 0.65f * (uiState.simulatedBrightnessPercent.coerceIn(1, 100) / 100f)
    val wallpaperBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1B2336).copy(alpha = brightnessGain),
            Color(0xFF281D3B).copy(alpha = brightnessGain),
            Color(0xFF10131C).copy(alpha = brightnessGain),
        ),
    )

    val outerBezelRadius = (capsuleState.cutout.screenTopCornerRadiusDp).coerceIn(24f, 44f).dp
    val bezelShape = RoundedCornerShape(outerBezelRadius)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(viewportHeight)
            .clip(bezelShape)
            .background(wallpaperBrush)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = bezelShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (capsuleState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED &&
                    SystemClock.uptimeMillis() - state3EnteredAtMs > 420L
                ) {
                    onEvent(CapsuleEvent.CollapseCapsule)
                }
            },
    ) {
        val frameWidthDp = maxWidth.value
        LaunchedEffect(frameWidthDp) {
            onMockupWidthMeasured(frameWidthDp)
        }

        // Compute layout bounds identically to CapsuleOverlayController
        val displayCapsuleState = holdPillBeforeRingState ?: capsuleState
        val effectiveLayoutState = when {
            forceCameraCircleStage && displayCapsuleState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED ->
                displayCapsuleState.copy(
                    visualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
                    content = CapsuleContent.Idle,
                    leftSatellite = null,
                )
            suppressSatelliteStage && displayCapsuleState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED ->
                displayCapsuleState.copy(leftSatellite = null)
            else -> displayCapsuleState
        }
        val targetSpec = computeCapsuleLayoutDpSpec(
            state = effectiveLayoutState,
            isHoldingCapsule = isHolding,
        )
        val springSpecDp = spring<Dp>(
            dampingRatio = 0.82f,
            stiffness = 410f,
        )
        val animWidthDp by animateDpAsState(targetSpec.widthDp.dp, springSpecDp, label = "simW")
        val animHeightDp by animateDpAsState(targetSpec.heightDp.dp, springSpecDp, label = "simH")
        val animOffsetXDp by animateDpAsState(targetSpec.offsetXDp.dp, springSpecDp, label = "simX")
        val animTopYDp by animateDpAsState(targetSpec.topYDp.dp, springSpecDp, label = "simY")
        val animCornerDp by animateDpAsState(targetSpec.cornerDp.dp, springSpecDp, label = "simCorner")

        val activeScrub = scrubPresentation
        val liveWidthDp: Float
        val liveHeightDp: Float
        val liveOffsetXDp: Float
        val liveTopYDp: Float
        val liveCornerDp: Float
        if (activeScrub != null && activeScrubSession != null) {
            val f = activeScrub.fraction
            val s = activeScrubSession!!
            val fClamped = f.coerceIn(0f, 1f)
            val over = (f - 1f).coerceAtLeast(0f)
            val satT = activeScrub.satelliteFraction
            val extraW = s.compactSpec.widthDp - s.compactBaseSpec.widthDp
            val extraX = s.compactSpec.offsetXDp - s.compactBaseSpec.offsetXDp
            liveWidthDp = lerpFloat(s.compactBaseSpec.widthDp, s.expandedSpec.widthDp, fClamped) + over * 6f + satT * extraW
            liveHeightDp = lerpFloat(s.compactBaseSpec.heightDp, s.expandedSpec.heightDp, fClamped) + over * 18f
            liveOffsetXDp = lerpFloat(s.compactBaseSpec.offsetXDp, s.expandedSpec.offsetXDp, fClamped) + satT * extraX
            liveTopYDp = lerpFloat(s.compactBaseSpec.topYDp, s.expandedSpec.topYDp, fClamped)
            liveCornerDp = lerpFloat(s.compactBaseSpec.cornerDp, s.expandedSpec.cornerDp, fClamped)
        } else {
            liveWidthDp = animWidthDp.value
            liveHeightDp = animHeightDp.value
            liveOffsetXDp = animOffsetXDp.value + swipeJiggleOffsetDp.value
            liveTopYDp = animTopYDp.value
            liveCornerDp = animCornerDp.value
        }

        // Mock Status Bar behind the capsule overlay
        val isQsSimulated = capsuleState.isQsRingOnlyActive
        val statusIconAlpha by animateFloatAsState(
            targetValue = if (isQsSimulated) 0.45f else 0.88f,
            animationSpec = tween(durationMillis = 200),
            label = "simStatusAlpha",
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .alpha(statusIconAlpha),
        ) {
            Text(
                text = "11:52",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "${capsuleState.batteryPercent}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Physical camera cutout marker anchored at Left / Center / Right base position
        val physicalHoleXOffsetDp = capsuleState
            .copy(preferences = prefs.copy(horizontalOffsetDp = 0f))
            .resolveAnchorCenterXOffsetDp()
        val cutoutWidthDp = capsuleState.cutout.holeWidthDp
        val cutoutHeightDp = capsuleState.cutout.holeHeightDp
        val cutoutTopDp = capsuleState.cutout.topOffsetDp
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        x = (physicalHoleXOffsetDp * density).roundToInt(),
                        y = (cutoutTopDp * density).roundToInt(),
                    )
                }
                .size(width = cutoutWidthDp.dp, height = cutoutHeightDp.dp)
                .clip(CircleShape)
                .background(Color(0xFF0A0C12))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        )

        // Subtle wallpaper helper caption near bottom of preview
        if (capsuleState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED) {
            Text(
                text = stringResource(R.string.msg_sandbox_ready),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            )
        }

        // Outer screen-frame expansion aura radiating inward from phone frame edges -> center transparent
        val (outerAuraColorA, outerAuraColorB) = resolveOuterExpansionAuraColors(
            state = capsuleState,
            primaryColor = MaterialTheme.colorScheme.primary,
            tertiaryColor = MaterialTheme.colorScheme.tertiary,
        )
        val outerAuraHeightDp = (targetSpec.topYDp + targetSpec.heightDp + 36f)
            .coerceAtLeast(180f)
            .dp
        OuterScreenExpansionAura(
            visible = shouldShowOuterExpansionAura(capsuleState),
            colorA = outerAuraColorA,
            colorB = outerAuraColorB,
            screenCornerRadiusDp = outerBezelRadius.value,
            spreadDp = 68f,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(outerAuraHeightDp),
        )

        // Actual IslandCapsule composable running sandbox coordinator state
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        x = (liveOffsetXDp * density).roundToInt(),
                        y = (liveTopYDp * density).roundToInt(),
                    )
                }
                .size(width = liveWidthDp.coerceAtLeast(24f).dp, height = liveHeightDp.coerceAtLeast(24f).dp),
        ) {
            IslandCapsule(
                uiState = displayCapsuleState,
                cornerRadiusDp = liveCornerDp,
                isBlurSupported = false,
                scrubPresentation = activeScrub,
                isForceCameraCircleStage = forceCameraCircleStage,
                isSatelliteDelayedStage = suppressSatelliteStage,
                onHoldingStateChanged = { holding -> isHolding = holding },
                onHoldPeekStart = { onEvent(CapsuleEvent.HoldPeekStarted) },
                onHoldPeekEnd = { onEvent(CapsuleEvent.HoldPeekReleased) },
                onTap = { onEvent(CapsuleEvent.TapCapsule) },
                onExpand = { onEvent(CapsuleEvent.ExpandToFull) },
                onLongHoldGesture = { onEvent(CapsuleEvent.LongHoldCapsule) },
                onDoubleTapGesture = { onEvent(CapsuleEvent.DoubleTapCapsule) },
                onExpandSatellite = {
                    holdPillBeforeRingState = null
                    forceCameraCircleStage = false
                    suppressSatelliteStage = false
                    onEvent(CapsuleEvent.ExpandLeftSatellite)
                },
                onMergeSatelliteIntoMainBeforeCamera = {
                    holdPillBeforeRingState = null
                    forceCameraCircleStage = false
                    suppressSatelliteStage = true
                },
                onMergeToCameraBeforeExpand = {
                    holdPillBeforeRingState = null
                    suppressSatelliteStage = false
                    forceCameraCircleStage = true
                },
                onCycleSatellite = { dir -> onEvent(CapsuleEvent.CycleLeftSatellite(dir)) },
                onUserInteraction = { onEvent(CapsuleEvent.UserInteractedInCard) },
                onCycleContent = { dir -> onEvent(CapsuleEvent.CycleContent(dir)) },
                onVerticalScrubStart = {
                    val currentlyExpanded = capsuleState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
                    val compactState: CapsuleUiState
                    val expandedState: CapsuleUiState
                    if (currentlyExpanded) {
                        val (targetVisual, targetContent) = onResolveCollapseTarget()
                        compactState = capsuleState.copy(visualState = targetVisual, content = targetContent)
                        expandedState = capsuleState
                    } else {
                        val targetContent = onResolveExpandTarget()
                        compactState = capsuleState
                        expandedState = capsuleState.copy(
                            visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
                            content = targetContent,
                        )
                    }
                    val compactSpec = computeCapsuleLayoutDpSpec(compactState, isHoldingCapsule = false)
                    val compactBaseSpec = computeCapsuleLayoutDpSpec(
                        compactState.copy(leftSatellite = null),
                        isHoldingCapsule = false,
                    )
                    val expandedSpec = computeCapsuleLayoutDpSpec(expandedState, isHoldingCapsule = false)
                    val travelPx = ((expandedSpec.heightDp - compactBaseSpec.heightDp) * density).coerceAtLeast(110f)
                    val initialFraction = if (currentlyExpanded) 1f else 0f
                    val satThreshold = 0.24f
                    val initSatT = if (!currentlyExpanded && compactState.leftSatellite != null) {
                        ((satThreshold - initialFraction) / satThreshold).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    activeScrubSession = MockupScrubSession(
                        compactUiState = compactState,
                        compactSpec = compactSpec,
                        compactBaseSpec = compactBaseSpec,
                        expandedUiState = expandedState,
                        expandedSpec = expandedSpec,
                        isCollapsing = currentlyExpanded,
                    )
                    scrubPresentation = CapsuleScrubPresentation(
                        fraction = initialFraction,
                        compactVisualState = compactState.visualState,
                        compactContent = compactState.content,
                        expandedContent = expandedState.content,
                        expandedWidthDp = expandedSpec.widthDp,
                        expandedHeightDp = expandedSpec.heightDp,
                        currentWidthDp = if (currentlyExpanded) expandedSpec.widthDp else compactSpec.widthDp,
                        currentHeightDp = if (currentlyExpanded) expandedSpec.heightDp else compactSpec.heightDp,
                        isCollapsing = currentlyExpanded,
                        satelliteFraction = initSatT,
                        fromSatellite = expandedState.isExpandedFromSatellite,
                    )
                    travelPx to initialFraction
                },
                onVerticalScrubProgress = { rawFraction ->
                    val s = activeScrubSession ?: return@IslandCapsule
                    val f = rawFraction.coerceIn(0f, 1.08f)
                    val fc = f.coerceIn(0f, 1f)
                    val satThreshold = 0.24f
                    val satT = if (!s.isCollapsing && s.compactUiState.leftSatellite != null) {
                        ((satThreshold - fc) / satThreshold).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val extraW = s.compactSpec.widthDp - s.compactBaseSpec.widthDp
                    scrubPresentation = CapsuleScrubPresentation(
                        fraction = f,
                        compactVisualState = s.compactUiState.visualState,
                        compactContent = s.compactUiState.content,
                        expandedContent = s.expandedUiState.content,
                        expandedWidthDp = s.expandedSpec.widthDp,
                        expandedHeightDp = s.expandedSpec.heightDp,
                        currentWidthDp = lerpFloat(s.compactBaseSpec.widthDp, s.expandedSpec.widthDp, fc) + satT * extraW,
                        currentHeightDp = lerpFloat(s.compactBaseSpec.heightDp, s.expandedSpec.heightDp, fc),
                        isCollapsing = s.isCollapsing,
                        satelliteFraction = satT,
                        fromSatellite = s.expandedUiState.isExpandedFromSatellite,
                    )
                },
                onVerticalScrubEnd = { commitExpanded, velocity ->
                    val s = activeScrubSession
                    val fraction = scrubPresentation?.fraction ?: 0f
                    val threshold = if (s?.isCollapsing == true) 0.75f else 0.25f
                    val shouldExpand = when {
                        abs(velocity) > 650f -> velocity > 0f
                        else -> commitExpanded || fraction >= threshold
                    }
                    activeScrubSession = null
                    scrubPresentation = null
                    if (shouldExpand) {
                        onEvent(CapsuleEvent.ExpandToFull)
                    } else {
                        skipNextCollapseStaging = true
                        onEvent(CapsuleEvent.CollapseCapsule)
                    }
                },
                onOpenNotification = onOpenNotification,
                onTriggerNotificationAction = onTriggerNotificationAction,
                onDismissNotification = onDismissNotification,
                onReplyFocusChanged = { focused -> onEvent(CapsuleEvent.ReplyFocusChanged(focused)) },
                onOpenMediaApp = { _ -> onOpenMediaApp() },
                onSelectMediaSession = onSelectMediaSession,
                onOpenAudioOutput = onOpenAudioOutput,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeekTo = onSeekTo,
                onToggleTorch = onToggleTorch,
                onSetTorchStrength = onSetTorchStrength,
                onSetStreamVolume = onSetStreamVolume,
                onCycleRingerMode = onCycleRingerMode,
                onSetRingerMode = onSetRingerMode,
                onSetBrightnessPercent = onSetBrightnessPercent,
                onToggleAutoBrightness = onToggleAutoBrightness,
                onAnswerCall = onAnswerCall,
                onDeclineCall = onDeclineCall,
                onOpenCallApp = onOpenCallApp,
                onOpenLiveActivityApp = onOpenLiveActivityApp,
                onOpenSystemStatusSettings = onOpenSystemStatusSettings,
                onOpenBluetoothSettings = onOpenBluetoothSettings,
                onOpenHotspotSettings = onOpenHotspotSettings,
                onLockScreen = onLockScreenButton,
                onOpenNotificationShade = onOpenNotificationShade,
                onOpenClock = onOpenClock,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Simulated Screen Lock overlay (never locks actual hardware screen)
        AnimatedVisibility(
            visible = uiState.isScreenLocked,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .pointerInput(Unit) { }
                    .clickable(onClick = onWakeFromLock),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = stringResource(R.string.label_sandbox_locked),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.action_tap_to_wake),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private data class MockupScrubSession(
    val compactUiState: CapsuleUiState,
    val compactSpec: CapsuleLayoutDpSpec,
    val compactBaseSpec: CapsuleLayoutDpSpec,
    val expandedUiState: CapsuleUiState,
    val expandedSpec: CapsuleLayoutDpSpec,
    val isCollapsing: Boolean,
)

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
