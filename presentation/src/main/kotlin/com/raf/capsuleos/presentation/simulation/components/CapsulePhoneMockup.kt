package com.raf.capsuleos.presentation.simulation.components

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
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.capsule.ui.CapsuleLayoutDpSpec
import com.raf.capsuleos.presentation.capsule.ui.CapsuleScrubPresentation
import com.raf.capsuleos.presentation.capsule.ui.IslandCapsule
import com.raf.capsuleos.presentation.capsule.ui.computeCapsuleLayoutDpSpec
import com.raf.capsuleos.presentation.simulation.SimulationUiState
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
    onMockupWidthMeasured: (Float) -> Unit,
    onResolveExpandTarget: () -> CapsuleContent,
    onResolveCollapseTarget: () -> Pair<CapsuleVisualState, CapsuleContent>,
    onEvent: (CapsuleEvent) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onCycleRingerMode: () -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
    onSetBrightnessPercent: (Int) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onLockScreenButton: () -> Unit,
    onDoubleTapLock: () -> Unit,
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
            .height(344.dp)
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
                if (capsuleState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                    onEvent(CapsuleEvent.CollapseCapsule)
                }
            },
    ) {
        val frameWidthDp = maxWidth.value
        LaunchedEffect(frameWidthDp) {
            onMockupWidthMeasured(frameWidthDp)
        }

        // Compute layout bounds identically to CapsuleOverlayController
        val targetSpec = computeCapsuleLayoutDpSpec(
            state = capsuleState,
            isHoldingCapsule = isHolding,
        )
        val springSpecDp = spring<Dp>(
            dampingRatio = 0.80f,
            stiffness = 430f,
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
            liveWidthDp = lerpFloat(s.compactSpec.widthDp, s.expandedSpec.widthDp, fClamped) + over * 6f
            liveHeightDp = lerpFloat(s.compactSpec.heightDp, s.expandedSpec.heightDp, fClamped) + over * 18f
            liveOffsetXDp = lerpFloat(s.compactSpec.offsetXDp, s.expandedSpec.offsetXDp, fClamped)
            liveTopYDp = lerpFloat(s.compactSpec.topYDp, s.expandedSpec.topYDp, fClamped)
            liveCornerDp = lerpFloat(s.compactSpec.cornerDp, s.expandedSpec.cornerDp, fClamped)
        } else {
            liveWidthDp = animWidthDp.value
            liveHeightDp = animHeightDp.value
            liveOffsetXDp = animOffsetXDp.value + swipeJiggleOffsetDp.value
            liveTopYDp = animTopYDp.value
            liveCornerDp = animCornerDp.value
        }

        // Mock Status Bar behind the capsule overlay (fades icons smoothly when State 3 hides icons)
        val hideIcons = capsuleState.shouldHideStatusBarIcons
        val statusIconAlpha by animateFloatAsState(
            targetValue = if (hideIcons) 0.14f else 0.88f,
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
                uiState = capsuleState,
                cornerRadiusDp = liveCornerDp,
                isBlurSupported = false,
                scrubPresentation = activeScrub,
                onHoldingStateChanged = { holding -> isHolding = holding },
                onHoldPeekStart = { onEvent(CapsuleEvent.HoldPeekStarted) },
                onHoldPeekEnd = { onEvent(CapsuleEvent.HoldPeekReleased) },
                onTap = { onEvent(CapsuleEvent.TapCapsule) },
                onExpand = { onEvent(CapsuleEvent.ExpandToFull) },
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
                    val expandedSpec = computeCapsuleLayoutDpSpec(expandedState, isHoldingCapsule = false)
                    val travelPx = ((expandedSpec.heightDp - compactSpec.heightDp) * density).coerceAtLeast(110f)
                    val initialFraction = if (currentlyExpanded) 1f else 0f
                    activeScrubSession = MockupScrubSession(
                        compactUiState = compactState,
                        compactSpec = compactSpec,
                        expandedUiState = expandedState,
                        expandedSpec = expandedSpec,
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
                    )
                    travelPx to initialFraction
                },
                onVerticalScrubProgress = { rawFraction ->
                    val s = activeScrubSession ?: return@IslandCapsule
                    val f = rawFraction.coerceIn(0f, 1.08f)
                    val fc = f.coerceIn(0f, 1f)
                    scrubPresentation = CapsuleScrubPresentation(
                        fraction = f,
                        compactVisualState = s.compactUiState.visualState,
                        compactContent = s.compactUiState.content,
                        expandedContent = s.expandedUiState.content,
                        expandedWidthDp = s.expandedSpec.widthDp,
                        expandedHeightDp = s.expandedSpec.heightDp,
                        currentWidthDp = lerpFloat(s.compactSpec.widthDp, s.expandedSpec.widthDp, fc),
                        currentHeightDp = lerpFloat(s.compactSpec.heightDp, s.expandedSpec.heightDp, fc),
                    )
                },
                onVerticalScrubEnd = { commitExpanded, velocity ->
                    val fraction = scrubPresentation?.fraction ?: 0f
                    val shouldExpand = when {
                        abs(velocity) > 650f -> velocity > 0f
                        else -> commitExpanded || fraction >= 0.42f
                    }
                    activeScrubSession = null
                    scrubPresentation = null
                    if (shouldExpand) {
                        onEvent(CapsuleEvent.ExpandToFull)
                    } else {
                        onEvent(CapsuleEvent.CollapseCapsule)
                    }
                },
                onOpenNotification = onOpenNotification,
                onTriggerNotificationAction = onTriggerNotificationAction,
                onDismissNotification = onDismissNotification,
                onReplyFocusChanged = { _ -> },
                onOpenMediaApp = { _ -> onOpenMediaApp() },
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
                onLockScreen = onLockScreenButton,
                onDoubleTapLock = onDoubleTapLock,
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
    val expandedUiState: CapsuleUiState,
    val expandedSpec: CapsuleLayoutDpSpec,
)

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
