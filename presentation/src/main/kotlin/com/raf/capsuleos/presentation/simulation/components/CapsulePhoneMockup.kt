package com.raf.capsuleos.presentation.simulation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
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
import com.raf.capsuleos.presentation.capsule.ui.IslandCapsule
import com.raf.capsuleos.presentation.capsule.ui.computeCapsuleLayoutDpSpec
import com.raf.capsuleos.presentation.shader.OuterScreenExpansionAura
import com.raf.capsuleos.presentation.shader.resolveOuterExpansionAuraColors
import com.raf.capsuleos.presentation.shader.shouldShowOuterExpansionAura
import com.raf.capsuleos.presentation.simulation.SimulationUiState
import kotlin.math.roundToInt

/**
 * Interactive top-of-phone sandbox viewport hosting the real [IslandCapsule] composable over a
 * simulated camera punch-hole, status bar, and dynamic wallpaper. Driven by [CapsuleMockupSpringHost]
 * on display VSYNC with deferred layout-phase sizing so animations match the live Accessibility
 * overlay 1:1 with zero parent recompositions per frame.
 */
@Composable
fun CapsulePhoneMockup(
    modifier: Modifier = Modifier,
    uiState: SimulationUiState,
    viewportHeight: Dp = 364.dp,
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
    val coroutineScope = rememberCoroutineScope()

    val springHost = remember {
        CapsuleMockupSpringHost(
            initialState = capsuleState,
            initialDensity = density,
            scope = coroutineScope,
            onResolveExpandTarget = onResolveExpandTarget,
            onResolveCollapseTarget = onResolveCollapseTarget,
            onEvent = onEvent,
        )
    }

    SideEffect {
        springHost.onResolveExpandTarget = onResolveExpandTarget
        springHost.onResolveCollapseTarget = onResolveCollapseTarget
        springHost.onEvent = onEvent
        springHost.syncState(capsuleState, density)
    }

    LaunchedEffect(springHost) {
        springHost.runVsyncLoop()
    }

    DisposableEffect(springHost) {
        onDispose { springHost.dispose() }
    }

    val brightnessGain = 0.35f + 0.65f * (uiState.simulatedBrightnessPercent.coerceIn(1, 100) / 100f)
    val wallpaperBrush = remember(brightnessGain) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1B2336).copy(alpha = brightnessGain),
                Color(0xFF281D3B).copy(alpha = brightnessGain),
                Color(0xFF10131C).copy(alpha = brightnessGain),
            ),
        )
    }

    val outerBezelRadius = (capsuleState.cutout.screenTopCornerRadiusDp).coerceIn(24f, 44f).dp
    val bezelShape = remember(outerBezelRadius) { RoundedCornerShape(outerBezelRadius) }

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
                springHost.onBackdropTap()
            },
    ) {
        val frameWidthDp = maxWidth.value
        LaunchedEffect(frameWidthDp) {
            onMockupWidthMeasured(frameWidthDp)
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
        val staticTargetSpec = computeCapsuleLayoutDpSpec(state = capsuleState, isHoldingCapsule = false)
        val outerAuraHeightDp = (staticTargetSpec.topYDp + staticTargetSpec.heightDp + 36f)
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

        // Isolated animated capsule host — reads spring px exclusively in layout phase (0 parent recompositions)
        MockupAnimatedCapsuleLayer(
            springHost = springHost,
            capsuleState = capsuleState,
            onEvent = onEvent,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onSeekTo = onSeekTo,
            onSelectMediaSession = onSelectMediaSession,
            onOpenAudioOutput = onOpenAudioOutput,
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
            onLockScreenButton = onLockScreenButton,
            onOpenNotificationShade = onOpenNotificationShade,
            onOpenNotification = onOpenNotification,
            onTriggerNotificationAction = onTriggerNotificationAction,
            onDismissNotification = onDismissNotification,
            onOpenMediaApp = onOpenMediaApp,
            onOpenClock = onOpenClock,
            modifier = Modifier.fillMaxSize(),
        )

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

@Composable
private fun MockupAnimatedCapsuleLayer(
    modifier: Modifier = Modifier,
    springHost: CapsuleMockupSpringHost,
    capsuleState: CapsuleUiState,
    onEvent: (CapsuleEvent) -> Unit,
    onTogglePlayPause: (String) -> Unit,
    onSkipNext: (String) -> Unit,
    onSkipPrevious: (String) -> Unit,
    onSeekTo: (String, Long) -> Unit,
    onSelectMediaSession: (Int) -> Unit,
    onOpenAudioOutput: () -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
    onSetStreamVolume: (AudioStreamType, Int) -> Unit,
    onCycleRingerMode: () -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
    onSetBrightnessPercent: (Int) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onAnswerCall: (CapsuleContent.CallAlert) -> Unit,
    onDeclineCall: (CapsuleContent.CallAlert) -> Unit,
    onOpenCallApp: (CapsuleContent.CallAlert) -> Unit,
    onOpenLiveActivityApp: (CapsuleContent.LiveActivity) -> Unit,
    onOpenSystemStatusSettings: (SystemStatusKind) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onLockScreenButton: () -> Unit,
    onOpenNotificationShade: () -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onOpenMediaApp: () -> Unit,
    onOpenClock: () -> Unit,
) {
    Layout(
        content = {
            IslandCapsule(
                uiState = capsuleState,
                cornerRadiusDp = springHost.liveCornerDp,
                isBlurSupported = false,
                scrubPresentation = springHost.scrubPresentation,
                isForceCameraCircleStage = springHost.forceCameraCircleStage,
                isSatelliteDelayedStage = springHost.suppressSatelliteStage,
                onHoldingStateChanged = springHost::onHoldingChanged,
                onHoldPeekStart = { onEvent(CapsuleEvent.HoldPeekStarted) },
                onHoldPeekEnd = { onEvent(CapsuleEvent.HoldPeekReleased) },
                onTap = { onEvent(CapsuleEvent.TapCapsule) },
                onExpand = { onEvent(CapsuleEvent.ExpandToFull) },
                onLongHoldGesture = { onEvent(CapsuleEvent.LongHoldCapsule) },
                onDoubleTapGesture = { onEvent(CapsuleEvent.DoubleTapCapsule) },
                onExpandSatellite = springHost::onExpandSatellite,
                onMergeSatelliteIntoMainBeforeCamera = springHost::onMergeSatelliteIntoMainBeforeCamera,
                onMergeToCameraBeforeExpand = springHost::onMergeToCameraBeforeExpand,
                onCycleSatellite = { dir -> onEvent(CapsuleEvent.CycleLeftSatellite(dir)) },
                onUserInteraction = { onEvent(CapsuleEvent.UserInteractedInCard) },
                onCycleContent = { dir -> onEvent(CapsuleEvent.CycleContent(dir)) },
                onVerticalScrubStart = springHost::startVerticalScrub,
                onVerticalScrubProgress = springHost::updateVerticalScrub,
                onVerticalScrubEnd = springHost::endVerticalScrub,
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
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val wPx = springHost.liveWidthPx.roundToInt().coerceAtLeast(1)
        val hPx = springHost.liveHeightPx.roundToInt().coerceAtLeast(1)
        val placeable = measurables.first().measure(Constraints.fixed(wPx, hPx))
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        layout(maxW, maxH) {
            val x = ((maxW - wPx) * 0.5f + springHost.liveOffsetXPx).roundToInt()
            val y = springHost.liveTopYPx.roundToInt()
            placeable.place(x = x, y = y)
        }
    }
}
