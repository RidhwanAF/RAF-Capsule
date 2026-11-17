package com.raf.capsuleos.presentation.capsule.ui

import android.os.SystemClock
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.presentation.capsule.ui.components.State2HorizontalPill
import com.raf.capsuleos.presentation.capsule.ui.components.State3ExpandedCard
import com.raf.capsuleos.presentation.capsule.ui.components.capsuleBatteryAccentColor
import com.raf.capsuleos.presentation.capsule.ui.components.computeSidePillGeometry
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics
import com.raf.capsuleos.presentation.shader.NeuralBorderShader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

private const val DOUBLE_TAP_LOCK_WINDOW_MS = 260L

private enum class CapsuleStageKey {
    STATE1_RING,
    STATE2_SIDE_PILL,
    STATE3_BIG_CARD,
}

/**
 * Interactive vertical scrub snapshot driving continuous 1:1 card morphing and crossfades.
 *
 * @property fraction Continuous expansion progress (`0f` = compact, `1f` = expanded card, `> 1f` rubberband).
 * @property compactVisualState Canonical compact base visual stage (`STATE1` or `STATE2`).
 * @property compactContent Compact content shown cleanly at low fractions.
 * @property expandedContent Expanded State 3 content revealed as `fraction` grows toward `1f`.
 * @property expandedWidthDp Resolved State 3 target width in dp to avoid mid-drag text reflow.
 * @property expandedHeightDp Resolved State 3 target height in dp for top-anchored clipping.
 */
data class CapsuleScrubPresentation(
    val fraction: Float,
    val compactVisualState: CapsuleVisualState,
    val compactContent: CapsuleContent,
    val expandedContent: CapsuleContent,
    val expandedWidthDp: Float,
    val expandedHeightDp: Float,
    val currentWidthDp: Float = expandedWidthDp,
    val currentHeightDp: Float = expandedHeightDp,
)

/**
 * Root 3-state dynamic camera capsule filling 100% of its VSYNC-animated accessibility overlay
 * window so card bounds, rounded outline, and behind-window blur match pixel-for-pixel.
 *
 * - **State 1**: Unified border shader configured as a clockwise battery ring around the cutout.
 * - **State 2**: Dynamic asymmetric side pill supporting 1–2 icons left and marquee info right.
 * - **State 3**: Vertical top-black to bottom-25%-translucent frosted blur card with unified
 *   SharedTransitionLayout + SeekableTransitionState 1:1 finger-tracked component morphing.
 */
@Composable
fun IslandCapsule(
    modifier: Modifier = Modifier,
    uiState: CapsuleUiState,
    cornerRadiusDp: Float,
    isBlurSupported: Boolean,
    scrubPresentation: CapsuleScrubPresentation?,
    onHoldingStateChanged: (Boolean) -> Unit,
    onHoldPeekStart: () -> Unit,
    onHoldPeekEnd: () -> Unit,
    onTap: () -> Unit,
    onExpand: () -> Unit,
    onUserInteraction: () -> Unit,
    onCycleContent: (Int) -> Unit,
    onVerticalScrubStart: () -> Pair<Float, Float>,
    onVerticalScrubProgress: (Float) -> Unit,
    onVerticalScrubEnd: (commitExpanded: Boolean, velocityYPxPerSec: Float) -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onReplyFocusChanged: (Boolean) -> Unit,
    onOpenMediaApp: (String) -> Unit,
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
    onLockScreen: () -> Unit,
    onDoubleTapLock: () -> Unit,
    onOpenClock: () -> Unit,
) {
    if (uiState.visualState == CapsuleVisualState.HIDDEN_SYSTEM && scrubPresentation == null) {
        return
    }

    val context = LocalContext.current
    val dynamicScheme = remember(context) { dynamicDarkColorScheme(context) }

    MaterialTheme(colorScheme = dynamicScheme) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val haptics = rememberCapsuleHaptics()
        val composeHaptics = LocalHapticFeedback.current
        val density = LocalDensity.current
        val rootView = LocalView.current
        val screenLoc = remember { IntArray(2) }
        val coroutineScope = rememberCoroutineScope()
        val prefs = uiState.preferences

        val stageKey = when (uiState.visualState) {
            CapsuleVisualState.STATE1_CUTOUT_ANCHOR -> CapsuleStageKey.STATE1_RING
            CapsuleVisualState.STATE2_HORIZONTAL_PILL -> CapsuleStageKey.STATE2_SIDE_PILL
            CapsuleVisualState.STATE3_FULL_EXPANDED -> CapsuleStageKey.STATE3_BIG_CARD
            CapsuleVisualState.HIDDEN_SYSTEM -> CapsuleStageKey.STATE1_RING
        }

        val isExpandedCard = stageKey == CapsuleStageKey.STATE3_BIG_CARD
        val isScrubbing = scrubPresentation != null
        val scrubFractionClamped = scrubPresentation?.fraction?.coerceIn(0f, 1f)
        val isCollapseWave = uiState.isCollapseWaveActive && !isExpandedCard
        var isHolding by remember { mutableStateOf(false) }

        val targetGlowIntensity = when {
            (isExpandedCard && uiState.isNeuralPulseActive) || isCollapseWave -> 1.0f
            isExpandedCard -> 0f
            isScrubbing -> 0.42f * (1f - (scrubFractionClamped ?: 0f))
            isHolding -> 0.58f
            stageKey == CapsuleStageKey.STATE2_SIDE_PILL -> 0f
            else -> 0f
        }
        val targetDotIntensity = if ((isExpandedCard && uiState.isNeuralPulseActive) || isCollapseWave) 1.0f else 0f
        val targetNeuralMode = when {
            isExpandedCard || isCollapseWave -> 1.0f
            isScrubbing -> (scrubFractionClamped ?: 0f)
            else -> 0f
        }
        val targetRingMix = when {
            isScrubbing -> if (scrubPresentation.compactVisualState == CapsuleVisualState.STATE1_CUTOUT_ANCHOR) {
                (1f - (scrubFractionClamped ?: 0f) / 0.50f).coerceIn(0f, 1f)
            } else {
                0f
            }
            stageKey == CapsuleStageKey.STATE1_RING -> 1f
            else -> 0f
        }
        val targetRingGlow = if (uiState.isRingGlowActive && stageKey == CapsuleStageKey.STATE1_RING && !isScrubbing) {
            1f
        } else {
            0f
        }
        val animatedIntensity by animateFloatAsState(
            targetValue = targetGlowIntensity,
            animationSpec = tween(durationMillis = if (isCollapseWave) 160 else 280),
            label = "neuralIntensity",
        )
        val animatedDotIntensity by animateFloatAsState(
            targetValue = targetDotIntensity,
            animationSpec = tween(
                durationMillis = when {
                    targetDotIntensity > 0f -> 160
                    uiState.isCollapseWaveActive || !isExpandedCard -> 260
                    else -> 550
                },
            ),
            label = "neuralDotIntensity",
        )
        val animatedNeuralMode by animateFloatAsState(
            targetValue = targetNeuralMode,
            animationSpec = tween(durationMillis = if (isCollapseWave) 160 else 260),
            label = "neuralMode",
        )
        val animatedRingMix by animateFloatAsState(
            targetValue = targetRingMix,
            animationSpec = tween(durationMillis = 360),
            label = "unifiedRingMix",
        )
        val animatedRingGlow by animateFloatAsState(
            targetValue = targetRingGlow,
            animationSpec = tween(durationMillis = if (targetRingGlow > 0f) 160 else 280),
            label = "unifiedRingGlow",
        )
        val effectiveRingMix = if (isScrubbing) targetRingMix else animatedRingMix
        val animatedRingProgress by animateFloatAsState(
            targetValue = uiState.batteryPercent.coerceIn(0, 100) / 100f,
            animationSpec = tween(durationMillis = 500),
            label = "unifiedRingProgress",
        )
        val targetRingColor = capsuleBatteryAccentColor(
            batteryPercent = uiState.batteryPercent,
            isCharging = uiState.isCharging,
            primaryColor = primaryColor,
        )
        val animatedRingColor by animateColorAsState(
            targetValue = targetRingColor,
            animationSpec = tween(durationMillis = 420),
            label = "unifiedRingColor",
        )
        val activeShaderColorA = lerp(primaryColor, animatedRingColor, effectiveRingMix)

        val isShaderClockActive = animatedIntensity > 0.01f ||
            animatedDotIntensity > 0.01f ||
            animatedRingGlow > 0.01f ||
            ((isExpandedCard || (scrubFractionClamped ?: 0f) > 0.1f) &&
                (uiState.content is CapsuleContent.Media || scrubPresentation?.expandedContent is CapsuleContent.Media))
        val shaderTime by rememberShaderTimeSeconds(isAnimating = isShaderClockActive)
        val neuralShader = remember { NeuralBorderShader.create() }

        val capsuleShape = RoundedCornerShape(cornerRadiusDp.dp)
        val cornerPx = with(density) { cornerRadiusDp.dp.toPx() }
        val strokePx = with(density) { (2.2f + 0.4f * effectiveRingMix).dp.toPx() }

        val topClearanceDp = if (prefs.belowStatusBar) {
            16f
        } else {
            val anchorYDp = uiState.anchorTopDp
            val holeBottomInWindowDp = (uiState.cutout.topOffsetDp + uiState.cutout.holeHeightDp) - anchorYDp
            (holeBottomInWindowDp + 10f).coerceAtLeast(40f)
        }

        val bgProgress = scrubFractionClamped ?: if (isExpandedCard) 1f else 0f
        val expandedBottomAlpha = if (isBlurSupported) 0.25f else 0.72f
        val midAlpha = 1f - 0.30f * bgProgress
        val bottomAlpha = 1f - (1f - expandedBottomAlpha) * bgProgress

        val cardBackgroundModifier = if (bgProgress > 0.01f) {
            Modifier.background(
                brush = Brush.verticalGradient(
                    0.0f to Color.Black,
                    0.28f to Color.Black.copy(alpha = midAlpha),
                    1.0f to Color.Black.copy(alpha = bottomAlpha),
                ),
            )
        } else {
            Modifier.background(Color.Black)
        }

        val currentVisualState by rememberUpdatedState(uiState.visualState)
        val latestScrubStart by rememberUpdatedState(onVerticalScrubStart)
        val latestScrubProgress by rememberUpdatedState(onVerticalScrubProgress)
        val latestScrubEnd by rememberUpdatedState(onVerticalScrubEnd)
        val latestUserInteraction by rememberUpdatedState(onUserInteraction)
        val latestTap by rememberUpdatedState(onTap)
        val latestDoubleTapLock by rememberUpdatedState(onDoubleTapLock)
        var lastCompactTapUpMs by remember { mutableLongStateOf(0L) }
        var pendingCompactTapJob by remember { mutableStateOf<Job?>(null) }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = modifier
                .fillMaxSize()
                .clip(capsuleShape)
                .then(cardBackgroundModifier)
                .drawWithContent {
                    drawContent()
                    if (animatedIntensity > 0.01f || animatedDotIntensity > 0.01f || effectiveRingMix > 0.01f || animatedRingGlow > 0.01f) {
                        NeuralBorderShader.updateUniforms(
                            shader = neuralShader,
                            widthPx = size.width,
                            heightPx = size.height,
                            timeSeconds = shaderTime,
                            cornerRadiusPx = cornerPx,
                            strokeWidthPx = strokePx,
                            intensity = animatedIntensity,
                            dotIntensity = animatedDotIntensity,
                            neuralMode = animatedNeuralMode,
                            ringMix = effectiveRingMix,
                            ringProgress = animatedRingProgress,
                            ringGlow = animatedRingGlow,
                            colorA = activeShaderColorA,
                            colorB = tertiaryColor,
                        )
                        drawRect(brush = ShaderBrush(neuralShader))
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downVisualState = currentVisualState
                        rootView.getLocationOnScreen(screenLoc)
                        val startScreenX = screenLoc[0] + down.position.x
                        val startScreenY = screenLoc[1] + down.position.y

                        if (downVisualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                            lastCompactTapUpMs = 0L
                            pendingCompactTapJob?.cancel()
                            pendingCompactTapJob = null
                            latestUserInteraction()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    latestUserInteraction()
                                    if (!change.isConsumed) {
                                        rootView.getLocationOnScreen(screenLoc)
                                        val currentScreenX = screenLoc[0] + change.position.x
                                        val currentScreenY = screenLoc[1] + change.position.y
                                        val dx = currentScreenX - startScreenX
                                        val dy = currentScreenY - startScreenY
                                        if (dy < -22f && abs(dy) > abs(dx) * 1.35f) {
                                            composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            val (travelPx, initialFraction) = latestScrubStart()
                                            trackVerticalScrub(
                                                startFraction = initialFraction,
                                                travelPx = travelPx,
                                                engageScreenY = currentScreenY,
                                                initiallyFromExpanded = true,
                                                rootView = rootView,
                                                screenLoc = screenLoc,
                                                onProgress = latestScrubProgress,
                                                onInteractionPulse = latestUserInteraction,
                                                onComplete = latestScrubEnd,
                                            )
                                            return@awaitEachGesture
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }

                        val downAtMs = SystemClock.uptimeMillis()
                        val supportsDoubleTapLock = downVisualState != CapsuleVisualState.STATE3_FULL_EXPANDED
                        val isDoubleTapLockCandidate = supportsDoubleTapLock &&
                            lastCompactTapUpMs > 0L &&
                            (downAtMs - lastCompactTapUpMs <= DOUBLE_TAP_LOCK_WINDOW_MS)
                        if (isDoubleTapLockCandidate) {
                            pendingCompactTapJob?.cancel()
                            pendingCompactTapJob = null
                        }

                        var movedTooFar = false
                        var holdPeekTriggered = false
                        var fullExpandTriggered = false
                        var cycledThisGesture = false

                        val holdJob = if (!isDoubleTapLockCandidate) {
                            coroutineScope.launch {
                                delay(95.milliseconds)
                                if (!movedTooFar) {
                                    lastCompactTapUpMs = 0L
                                    holdPeekTriggered = true
                                    isHolding = true
                                    onHoldingStateChanged(true)
                                    onHoldPeekStart()
                                    haptics.startSoftCircleToSearchWave()
                                    delay(360.milliseconds)
                                    if (!movedTooFar) {
                                        fullExpandTriggered = true
                                        isHolding = false
                                        onHoldingStateChanged(false)
                                        composeHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onExpand()
                                    }
                                }
                            }
                        } else {
                            null
                        }

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                rootView.getLocationOnScreen(screenLoc)
                                val currentScreenX = screenLoc[0] + change.position.x
                                val currentScreenY = screenLoc[1] + change.position.y
                                val dx = currentScreenX - startScreenX
                                val dy = currentScreenY - startScreenY
                                if (dy > 14f && abs(dy) > abs(dx) * 1.15f) {
                                    lastCompactTapUpMs = 0L
                                    holdJob?.cancel()
                                    if (isHolding) {
                                        isHolding = false
                                        onHoldingStateChanged(false)
                                        haptics.cancelWave()
                                    }
                                    composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    val (travelPx, initialFraction) = latestScrubStart()
                                    trackVerticalScrub(
                                        startFraction = initialFraction,
                                        travelPx = travelPx,
                                        engageScreenY = currentScreenY,
                                        initiallyFromExpanded = false,
                                        rootView = rootView,
                                        screenLoc = screenLoc,
                                        onProgress = latestScrubProgress,
                                        onInteractionPulse = latestUserInteraction,
                                        onComplete = latestScrubEnd,
                                    )
                                    return@awaitEachGesture
                                } else if (!cycledThisGesture && abs(dx) > 46f && abs(dx) > abs(dy) * 1.3f) {
                                    lastCompactTapUpMs = 0L
                                    cycledThisGesture = true
                                    movedTooFar = true
                                    holdJob?.cancel()
                                    composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onCycleContent(if (dx < 0f) 1 else -1)
                                } else if (hypot(dx, dy) > 36f) {
                                    lastCompactTapUpMs = 0L
                                    movedTooFar = true
                                    holdJob?.cancel()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        holdJob?.cancel()
                        if (isHolding) {
                            isHolding = false
                            onHoldingStateChanged(false)
                            if (!fullExpandTriggered) {
                                haptics.cancelWave()
                            }
                        }

                        if (!fullExpandTriggered && !cycledThisGesture) {
                            if (holdPeekTriggered) {
                                lastCompactTapUpMs = 0L
                                onHoldPeekEnd()
                            } else if (!movedTooFar) {
                                if (isDoubleTapLockCandidate) {
                                    lastCompactTapUpMs = 0L
                                    pendingCompactTapJob?.cancel()
                                    pendingCompactTapJob = null
                                    composeHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    latestDoubleTapLock()
                                } else if (supportsDoubleTapLock) {
                                    composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    lastCompactTapUpMs = SystemClock.uptimeMillis()
                                    pendingCompactTapJob?.cancel()
                                    pendingCompactTapJob = coroutineScope.launch {
                                        delay(DOUBLE_TAP_LOCK_WINDOW_MS.milliseconds)
                                        lastCompactTapUpMs = 0L
                                        latestTap()
                                    }
                                } else {
                                    lastCompactTapUpMs = 0L
                                    composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    latestTap()
                                }
                            }
                        }
                    }
                },
        ) {
            val f = scrubPresentation?.fraction?.coerceIn(0f, 1f)
            val compactAlpha = if (f != null) ((0.50f - f) / 0.50f).coerceIn(0f, 1f) else 1f
            val expandedAlpha = if (f != null) ((f - 0.50f) / 0.50f).coerceIn(0f, 1f) else 1f
            val expandedScale = if (scrubPresentation != null) {
                val widthRatio = (scrubPresentation.currentWidthDp / scrubPresentation.expandedWidthDp.coerceAtLeast(1f))
                    .coerceIn(0.32f, 1.08f)
                val heightRatio = (scrubPresentation.currentHeightDp / scrubPresentation.expandedHeightDp.coerceAtLeast(1f))
                    .coerceIn(0.32f, 1.08f)
                minOf(widthRatio, heightRatio)
            } else {
                1f
            }

            fun Modifier.scrubContainer(
                targetW: Float,
                targetH: Float,
                alphaVal: Float,
                scaleVal: Float,
            ): Modifier = this
                .fillMaxSize()
                .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                .size(width = targetW.dp, height = targetH.dp)
                .graphicsLayer {
                    alpha = alphaVal
                    scaleX = scaleVal
                    scaleY = scaleVal
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }

            fun resolvePillScrubScale(geomWidthDp: Float): Float = if (scrubPresentation != null) {
                val pillWidthScale = (scrubPresentation.currentWidthDp / geomWidthDp.coerceAtLeast(1f))
                    .coerceIn(0.65f, 1.05f)
                (0.82f + 0.18f * compactAlpha).coerceAtMost(pillWidthScale)
            } else {
                1f
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Single unified stage tree ensures State 3 (e.g. MediaPlayer) never unmounts/blinks on vertical drag
                SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = stageKey,
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = {
                            if (targetState == CapsuleStageKey.STATE1_RING || initialState == CapsuleStageKey.STATE1_RING) {
                                (fadeIn(animationSpec = tween(200, delayMillis = 20)) +
                                    scaleIn(
                                        initialScale = 0.72f,
                                        transformOrigin = TransformOrigin(0.5f, 0f),
                                        animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f),
                                    )) togetherWith
                                    (fadeOut(animationSpec = tween(140)) +
                                        scaleOut(
                                            targetScale = 0.72f,
                                            transformOrigin = TransformOrigin(0.5f, 0f),
                                            animationSpec = tween(170),
                                        )) using SizeTransform(clip = false)
                            } else {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(220)) using
                                    SizeTransform(clip = false)
                            }
                        },
                        label = "capsuleStageSwitch",
                        modifier = Modifier.fillMaxSize(),
                    ) { activeStage ->
                        when (activeStage) {
                            CapsuleStageKey.STATE1_RING -> Box(modifier = Modifier.fillMaxSize())
                            CapsuleStageKey.STATE2_SIDE_PILL -> {
                                val pillContent = if (scrubPresentation != null &&
                                    scrubPresentation.compactVisualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL
                                ) {
                                    scrubPresentation.compactContent
                                } else {
                                    uiState.content
                                }
                                val pillGeom = computeSidePillGeometry(pillContent, uiState.cutout)
                                val pillModifier = if (scrubPresentation != null) {
                                    Modifier.scrubContainer(
                                        targetW = pillGeom.totalWidthDp,
                                        targetH = uiState.circleDiameterDp,
                                        alphaVal = compactAlpha,
                                        scaleVal = resolvePillScrubScale(pillGeom.totalWidthDp),
                                    )
                                } else {
                                    Modifier
                                        .fillMaxSize()
                                        .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                                        .size(
                                            width = pillGeom.totalWidthDp.dp,
                                            height = uiState.circleDiameterDp.dp,
                                        )
                                }
                                Box(modifier = pillModifier) {
                                    State2HorizontalPill(
                                        content = pillContent,
                                        cutout = uiState.cutout,
                                        swipeDirection = uiState.swipeDirection,
                                        manualSwipeSeq = uiState.manualSwipeSeq,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            CapsuleStageKey.STATE3_BIG_CARD -> {
                                val cardWrapperModifier = if (scrubPresentation != null) {
                                    Modifier.scrubContainer(
                                        targetW = scrubPresentation.expandedWidthDp,
                                        targetH = scrubPresentation.expandedHeightDp,
                                        alphaVal = expandedAlpha,
                                        scaleVal = expandedScale,
                                    )
                                } else {
                                    Modifier.fillMaxSize()
                                }
                                Box(modifier = cardWrapperModifier) {
                                    State3ExpandedCard(
                                        content = uiState.content,
                                        topClearanceDp = topClearanceDp,
                                        timeSeconds = shaderTime,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onUserInteraction = onUserInteraction,
                                        onOpenNotification = onOpenNotification,
                                        onTriggerNotificationAction = onTriggerNotificationAction,
                                        onDismissNotification = onDismissNotification,
                                        onReplyFocusChanged = onReplyFocusChanged,
                                        onOpenMediaApp = onOpenMediaApp,
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
                                        onLockScreen = onLockScreen,
                                        onOpenClock = onOpenClock,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }

                // Cross-stage scrub preview when dragging past 50% toward the opposite stage
                if (scrubPresentation != null &&
                    stageKey == CapsuleStageKey.STATE3_BIG_CARD &&
                    scrubPresentation.compactVisualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL &&
                    compactAlpha > 0.01f
                ) {
                    val pillGeom = computeSidePillGeometry(scrubPresentation.compactContent, uiState.cutout)
                    Box(
                        modifier = Modifier.scrubContainer(
                            targetW = pillGeom.totalWidthDp,
                            targetH = uiState.circleDiameterDp,
                            alphaVal = compactAlpha,
                            scaleVal = resolvePillScrubScale(pillGeom.totalWidthDp),
                        ),
                    ) {
                        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(visible = true, modifier = Modifier.fillMaxSize()) {
                                State2HorizontalPill(
                                    content = scrubPresentation.compactContent,
                                    cutout = uiState.cutout,
                                    swipeDirection = uiState.swipeDirection,
                                    manualSwipeSeq = uiState.manualSwipeSeq,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                } else if (scrubPresentation != null &&
                    stageKey != CapsuleStageKey.STATE3_BIG_CARD &&
                    expandedAlpha > 0.01f
                ) {
                    Box(
                        modifier = Modifier.scrubContainer(
                            targetW = scrubPresentation.expandedWidthDp,
                            targetH = scrubPresentation.expandedHeightDp,
                            alphaVal = expandedAlpha,
                            scaleVal = expandedScale,
                        ),
                    ) {
                        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(visible = true, modifier = Modifier.fillMaxSize()) {
                                State3ExpandedCard(
                                    content = scrubPresentation.expandedContent,
                                    topClearanceDp = topClearanceDp,
                                    timeSeconds = shaderTime,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    onUserInteraction = onUserInteraction,
                                    onOpenNotification = onOpenNotification,
                                    onTriggerNotificationAction = onTriggerNotificationAction,
                                    onDismissNotification = onDismissNotification,
                                    onReplyFocusChanged = onReplyFocusChanged,
                                    onOpenMediaApp = onOpenMediaApp,
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
                                    onLockScreen = onLockScreen,
                                    onOpenClock = onOpenClock,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.trackVerticalScrub(
    startFraction: Float,
    travelPx: Float,
    engageScreenY: Float,
    initiallyFromExpanded: Boolean,
    rootView: View,
    screenLoc: IntArray,
    onProgress: (Float) -> Unit,
    onInteractionPulse: () -> Unit,
    onComplete: (commitExpanded: Boolean, velocityYPxPerSec: Float) -> Unit,
) {
    var currentFraction = startFraction
    var lastSampleY = engageScreenY
    var lastSampleTimeMs = SystemClock.uptimeMillis()
    var smoothedVy = 0f
    var lastPulseTimeMs = lastSampleTimeMs

    do {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull()
        if (change != null) {
            change.consume()
            rootView.getLocationOnScreen(screenLoc)
            val currentScreenY = screenLoc[1] + change.position.y
            val effectiveDy = currentScreenY - engageScreenY
            val raw = startFraction + effectiveDy / travelPx
            currentFraction = applyScrubRubberBand(raw)
            onProgress(currentFraction)

            val dtMs = change.uptimeMillis - lastSampleTimeMs
            if (dtMs in 4L..120L) {
                val instantVy = (currentScreenY - lastSampleY) / (dtMs / 1000f)
                smoothedVy = if (smoothedVy == 0f) instantVy else smoothedVy * 0.55f + instantVy * 0.45f
                lastSampleY = currentScreenY
                lastSampleTimeMs = change.uptimeMillis
            } else if (dtMs > 120L) {
                smoothedVy = 0f
                lastSampleY = currentScreenY
                lastSampleTimeMs = change.uptimeMillis
            }

            if (change.uptimeMillis - lastPulseTimeMs >= 350L) {
                lastPulseTimeMs = change.uptimeMillis
                onInteractionPulse()
            }
        }
    } while (event.changes.any { it.pressed })

    val commitExpanded = if (initiallyFromExpanded) {
        (currentFraction >= 0.68f && smoothedVy > -600f) || smoothedVy > 380f
    } else {
        (currentFraction >= 0.36f && smoothedVy >= -350f) || smoothedVy > 620f
    }
    onComplete(commitExpanded, smoothedVy)
}

private fun applyScrubRubberBand(raw: Float): Float = when {
    raw > 1f -> {
        val over = raw - 1f
        1f + (over / (1f + over * 3.2f)) * 0.14f
    }
    raw < 0f -> {
        val under = -raw
        -(under / (1f + under * 4f)) * 0.04f
    }
    else -> raw
}
