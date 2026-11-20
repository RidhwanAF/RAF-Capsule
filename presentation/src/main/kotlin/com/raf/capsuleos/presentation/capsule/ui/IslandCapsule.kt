package com.raf.capsuleos.presentation.capsule.ui

import android.os.SystemClock
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleGestureAction
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.ExpandedMediaGlowEffect
import com.raf.capsuleos.domain.model.HorizontalMediaGlowStyle
import com.raf.capsuleos.domain.model.MediaAnimationColorMode
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.presentation.capsule.ui.components.LeftSatelliteBubbleIcon
import com.raf.capsuleos.presentation.capsule.ui.components.MediaBackdropLayer
import com.raf.capsuleos.presentation.capsule.ui.components.State2HorizontalPill
import com.raf.capsuleos.presentation.capsule.ui.components.State3ExpandedCard
import com.raf.capsuleos.presentation.capsule.ui.components.capsuleBatteryAccentColor
import com.raf.capsuleos.presentation.capsule.ui.components.computeSidePillGeometry
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics
import com.raf.capsuleos.presentation.shader.NeuralBorderShader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
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
    val isCollapsing: Boolean = false,
    val satelliteFraction: Float = 0f,
    val fromSatellite: Boolean = false,
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
    isForceCameraCircleStage: Boolean = false,
    isSatelliteDelayedStage: Boolean = false,
    onHoldingStateChanged: (Boolean) -> Unit,
    onHoldPeekStart: () -> Unit,
    onHoldPeekEnd: () -> Unit,
    onTap: () -> Unit,
    onExpand: () -> Unit,
    onLongHoldGesture: () -> Unit = onExpand,
    onDoubleTapGesture: () -> Unit = {},
    onExpandSatellite: () -> Unit = {},
    onMergeSatelliteIntoMainBeforeCamera: () -> Unit = {},
    onMergeToCameraBeforeExpand: () -> Unit = {},
    onCycleSatellite: (Int) -> Unit = {},
    onUserInteraction: () -> Unit,
    onCycleContent: (Int) -> Unit,
    onVerticalScrubStart: () -> Pair<Float, Float>,
    onVerticalScrubProgress: (Float) -> Unit,
    onVerticalScrubEnd: (commitExpanded: Boolean, velocityYPxPerSec: Float, isFastDirectSwipe: Boolean) -> Unit,
    onOpenNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onTriggerNotificationAction: (CapsuleContent.NotificationAlert, Int, String?) -> Unit,
    onDismissNotification: (CapsuleContent.NotificationAlert) -> Unit,
    onNotificationSwipeChanged: (offsetXDp: Float, pullFraction: Float) -> Unit = { _, _ -> },
    onReplyFocusChanged: (Boolean) -> Unit,
    onOpenMediaApp: (String) -> Unit,
    onSelectMediaSession: (Int) -> Unit = {},
    onOpenAudioOutput: () -> Unit = {},
    onLyricsActiveChanged: (Boolean) -> Unit = {},
    onTogglePlayPause: (String) -> Unit,
    onSkipNext: (String) -> Unit,
    onSkipPrevious: (String) -> Unit,
    onSeekTo: (String, Long) -> Unit,
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
    onTriggerLiveActivityAction: (CapsuleContent.LiveActivity, Int) -> Unit = { _, _ -> },
    onOpenSystemStatusSettings: (SystemStatusKind) -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {},
    onOpenHotspotSettings: () -> Unit = {},
    onLockScreen: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
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

        var isMergingSatelliteIntoMain by remember { mutableStateOf(false) }
        var isMergingToCameraBeforeExpand by remember { mutableStateOf(false) }
        val rawIsExpandedCard = uiState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
        var localCollapseSatLock by remember { mutableStateOf(false) }
        var prevRawIsExpanded by remember { mutableStateOf(rawIsExpandedCard) }
        if (prevRawIsExpanded && !rawIsExpandedCard && uiState.leftSatellite != null) {
            localCollapseSatLock = true
        } else if (rawIsExpandedCard) {
            localCollapseSatLock = false
        }
        prevRawIsExpanded = rawIsExpandedCard
        LaunchedEffect(localCollapseSatLock) {
            if (localCollapseSatLock) {
                delay(160.milliseconds)
                localCollapseSatLock = false
            }
        }
        LaunchedEffect(rawIsExpandedCard) {
            if (rawIsExpandedCard) {
                isMergingSatelliteIntoMain = false
                isMergingToCameraBeforeExpand = false
            }
        }

        val stageKey = when {
            isMergingToCameraBeforeExpand -> CapsuleStageKey.STATE1_RING
            isForceCameraCircleStage && !rawIsExpandedCard && scrubPresentation == null -> CapsuleStageKey.STATE1_RING
            uiState.visualState == CapsuleVisualState.STATE1_CUTOUT_ANCHOR -> CapsuleStageKey.STATE1_RING
            uiState.visualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL -> CapsuleStageKey.STATE2_SIDE_PILL
            uiState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED -> CapsuleStageKey.STATE3_BIG_CARD
            else -> CapsuleStageKey.STATE1_RING
        }

        val isExpandedCard = stageKey == CapsuleStageKey.STATE3_BIG_CARD
        val isScrubbing = scrubPresentation != null
        val scrubFractionClamped = scrubPresentation?.fraction?.coerceIn(0f, 1f)
        val allowNeuronGlow = prefs.enableNeuronGlow
        // Remove collapse wave glow on Horizontal Expand; keep it for Camera Ring collapse per user settings
        val isCollapseWave = allowNeuronGlow &&
            uiState.isCollapseWaveActive &&
            stageKey == CapsuleStageKey.STATE1_RING
        var isHolding by remember { mutableStateOf(false) }

        var isStageAnimating by remember { mutableStateOf(false) }
        LaunchedEffect(stageKey) {
            isStageAnimating = true
            delay(260.milliseconds)
            isStageAnimating = false
        }
        val interactionsEnabled = !isScrubbing && !isStageAnimating && !uiState.isQsRingOnlyActive

        var lastExpandedFromSatellite by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.isExpandedFromSatellite, isExpandedCard) {
            if (uiState.isExpandedFromSatellite) {
                lastExpandedFromSatellite = true
            } else if (!isExpandedCard) {
                delay(420.milliseconds)
                lastExpandedFromSatellite = false
            }
        }
        var suppressAlbumSharedAfterDrag by remember { mutableStateOf(false) }
        LaunchedEffect(suppressAlbumSharedAfterDrag, isScrubbing) {
            if (suppressAlbumSharedAfterDrag && !isScrubbing) {
                delay(420.milliseconds)
                suppressAlbumSharedAfterDrag = false
            }
        }
        val enableAlbumSharedTransition = !isScrubbing &&
            !suppressAlbumSharedAfterDrag &&
            !uiState.isExpandedFromSatellite &&
            !lastExpandedFromSatellite &&
            !isMergingSatelliteIntoMain &&
            !isMergingToCameraBeforeExpand
        val satGlowTarget = if (allowNeuronGlow &&
            (uiState.isSatelliteGlowActive ||
                isMergingSatelliteIntoMain ||
                isMergingToCameraBeforeExpand ||
                (uiState.isExpandedFromSatellite && uiState.isNeuralPulseActive) ||
                (lastExpandedFromSatellite && uiState.isCollapseWaveActive))
        ) {
            1f
        } else {
            0f
        }
        val animatedSatGlow by animateFloatAsState(
            targetValue = satGlowTarget,
            animationSpec = tween(durationMillis = if (satGlowTarget > 0f) 160 else 320),
            label = "satelliteGlow",
        )

        val playingMediaInSessions = uiState.mediaSessions.firstOrNull { it.isPlaying }
        val currentMediaInState: CapsuleContent.Media? = when {
            uiState.content is CapsuleContent.Media -> {
                val baseMedia = uiState.content as CapsuleContent.Media
                if (isExpandedCard && uiState.mediaSessions.size > 1) {
                    uiState.mediaSessions.getOrNull(uiState.selectedMediaIndex) ?: baseMedia
                } else {
                    baseMedia
                }
            }
            scrubPresentation?.expandedContent is CapsuleContent.Media && (scrubFractionClamped ?: 0f) > 0.25f ->
                scrubPresentation.expandedContent
            scrubPresentation?.compactContent is CapsuleContent.Media && (scrubFractionClamped ?: 0f) <= 0.25f ->
                scrubPresentation.compactContent
            else -> null
        }
        val isMediaContext = currentMediaInState != null
        val borderMedia = if (isMediaContext) {
            playingMediaInSessions ?: currentMediaInState
        } else {
            null
        }
        val isMediaPlaying = playingMediaInSessions != null || currentMediaInState?.isPlaying == true
        val expandedMediaBorderEnabled =
            ExpandedMediaGlowEffect.CARD_BORDER_NEURON in prefs.expandedMediaGlowEffects
        val pillMediaGlow = isMediaContext &&
            isMediaPlaying &&
            prefs.horizontalMediaGlowStyle == HorizontalMediaGlowStyle.HORIZONTAL_PILL &&
            stageKey == CapsuleStageKey.STATE2_SIDE_PILL &&
            !isScrubbing
        val cameraRingMediaGlow = isMediaContext &&
            isMediaPlaying &&
            prefs.horizontalMediaGlowStyle == HorizontalMediaGlowStyle.CAMERA_RING &&
            stageKey == CapsuleStageKey.STATE1_RING &&
            !isScrubbing
        val allowExpandedBorderPulse = allowNeuronGlow &&
            isExpandedCard &&
            uiState.isNeuralPulseActive &&
            (!isMediaContext || expandedMediaBorderEnabled)
        val keepMediaExpandedGlow = allowNeuronGlow &&
            expandedMediaBorderEnabled &&
            isExpandedCard &&
            isMediaContext &&
            prefs.keepExpandedMediaGlowAlways
        val usePrimaryMediaColor = prefs.mediaAnimationColorMode == MediaAnimationColorMode.PRIMARY_COLOR
        val animatedMediaPrimary by animateColorAsState(
            targetValue = if (usePrimaryMediaColor) {
                primaryColor
            } else {
                borderMedia?.let { Color(it.primaryColorArgb) } ?: primaryColor
            },
            animationSpec = tween(durationMillis = 360),
            label = "mediaGlowPrimary",
        )
        val animatedMediaSecondary by animateColorAsState(
            targetValue = if (usePrimaryMediaColor) {
                tertiaryColor
            } else {
                borderMedia?.let { Color(it.secondaryColorArgb) } ?: tertiaryColor
            },
            animationSpec = tween(durationMillis = 360),
            label = "mediaGlowSecondary",
        )
        val mediaAccentPrimary: Color? = if (isMediaContext) animatedMediaPrimary else null
        val mediaAccentSecondary: Color? = if (isMediaContext) animatedMediaSecondary else null

        val targetGlowIntensity = when {
            allowExpandedBorderPulse || isCollapseWave -> 1.0f
            keepMediaExpandedGlow -> 0.90f
            pillMediaGlow -> 0.88f
            isExpandedCard -> 0f
            isScrubbing -> if (allowNeuronGlow) 0.42f * (1f - (scrubFractionClamped ?: 0f)) else 0f
            isHolding -> 0.58f
            stageKey == CapsuleStageKey.STATE2_SIDE_PILL -> 0f
            else -> 0f
        }
        val targetDotIntensity = when {
            allowExpandedBorderPulse || isCollapseWave -> 1.0f
            keepMediaExpandedGlow -> 0.55f
            else -> 0f
        }
        val targetNeuralMode = when {
            isExpandedCard || isCollapseWave || pillMediaGlow -> 1.0f
            isScrubbing -> (scrubFractionClamped ?: 0f)
            else -> 0f
        }
        val rawRingMix = when {
            !prefs.showBatteryOnCameraRing && !cameraRingMediaGlow -> 0f
            isScrubbing -> if (scrubPresentation.compactVisualState == CapsuleVisualState.STATE1_CUTOUT_ANCHOR) {
                (1f - (scrubFractionClamped ?: 0f) / 0.25f).coerceIn(0f, 1f)
            } else {
                0f
            }
            stageKey == CapsuleStageKey.STATE1_RING -> 1f
            else -> 0f
        }
        val targetRingMix = rawRingMix
        val targetRingGlow = when {
            allowNeuronGlow && uiState.isRingGlowActive && stageKey == CapsuleStageKey.STATE1_RING && !isScrubbing -> 1f
            cameraRingMediaGlow -> 1.0f
            else -> 0f
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
        val mediaColorBlend = isMediaContext || pillMediaGlow || cameraRingMediaGlow
        val isChargingGlowContext = uiState.content is CapsuleContent.Charging && isExpandedCard
        val baseColorA = when {
            isChargingGlowContext -> animatedRingColor
            mediaColorBlend && mediaAccentPrimary != null -> mediaAccentPrimary
            else -> primaryColor
        }
        val baseColorB = when {
            isChargingGlowContext -> lerp(animatedRingColor, Color.White, 0.32f)
            mediaColorBlend && mediaAccentSecondary != null -> mediaAccentSecondary
            else -> tertiaryColor
        }
        val activeShaderColorA = lerp(baseColorA, animatedRingColor, effectiveRingMix)

        val isShaderClockActive = animatedIntensity > 0.01f ||
            animatedDotIntensity > 0.01f ||
            animatedRingGlow > 0.01f ||
            animatedSatGlow > 0.01f ||
            pillMediaGlow ||
            cameraRingMediaGlow ||
            keepMediaExpandedGlow ||
            ((isExpandedCard || (scrubFractionClamped ?: 0f) > 0.1f) &&
                (uiState.content is CapsuleContent.Media || scrubPresentation?.expandedContent is CapsuleContent.Media))
        val shaderTime by rememberShaderTimeSeconds(isAnimating = isShaderClockActive)
        val neuralShader = remember { NeuralBorderShader.create() }

        val capsuleShape = if (!isExpandedCard && scrubPresentation == null) {
            CircleShape
        } else {
            RoundedCornerShape(cornerRadiusDp.dp)
        }
        val cornerPx = with(density) { cornerRadiusDp.dp.toPx() }
        val strokePx = with(density) { (2.2f + 0.4f * effectiveRingMix).dp.toPx() }

        val contentPaddingDp = prefs.expandedContentPaddingDp.coerceIn(8f, 32f)
        val targetTopClearanceDp = if (prefs.belowStatusBar) {
            contentPaddingDp
        } else {
            val anchorYDp = uiState.anchorTopDp
            val holeBottomInWindowDp = (uiState.cutout.topOffsetDp + uiState.cutout.holeHeightDp) - anchorYDp
            (holeBottomInWindowDp + 10f).coerceAtLeast(40f) + (contentPaddingDp - 18f).coerceAtLeast(-6f)
        }
        val topClearanceDp by animateFloatAsState(
            targetValue = targetTopClearanceDp,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 410f),
            label = "topClearanceDp",
        )

        val notifSwipeOffsetDp = remember { Animatable(0f) }
        val notifSwipeScale = remember { Animatable(1f) }
        val bgProgress = scrubFractionClamped ?: if (isExpandedCard) 1f else 0f
        val expandedBottomAlpha = if (isBlurSupported) 0.25f else 0.72f
        val swipeOpaqueBoost = (abs(notifSwipeOffsetDp.value) / 3.5f).coerceIn(0f, 1f)
        val effectiveExpandedBottomAlpha = lerpCapsuleFloat(expandedBottomAlpha, 0.80f, swipeOpaqueBoost)
        val midAlpha = 1f - 0.30f * bgProgress
        val bottomAlpha = 1f - (1f - effectiveExpandedBottomAlpha) * bgProgress

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
        val latestUiState by rememberUpdatedState(uiState)
        val latestScrubStart by rememberUpdatedState(onVerticalScrubStart)
        val latestScrubProgress by rememberUpdatedState(onVerticalScrubProgress)
        val latestScrubEnd by rememberUpdatedState(onVerticalScrubEnd)
        val latestUserInteraction by rememberUpdatedState(onUserInteraction)
        val latestTap by rememberUpdatedState(onTap)
        val latestDoubleTapGesture by rememberUpdatedState(onDoubleTapGesture)
        val latestLongHoldGesture by rememberUpdatedState(onLongHoldGesture)
        val latestDismissNotification by rememberUpdatedState(onDismissNotification)
        val latestNotificationSwipeChanged by rememberUpdatedState(onNotificationSwipeChanged)
        val latestReplyFocusChanged by rememberUpdatedState(onReplyFocusChanged)
        var lastCompactTapUpMs by remember { mutableLongStateOf(0L) }
        var pendingCompactTapJob by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(Unit) {
            snapshotFlow { notifSwipeOffsetDp.value }.collect { offsetDp ->
                val pullFraction = (abs(offsetDp) / 11f).coerceIn(0f, 1f)
                latestNotificationSwipeChanged(offsetDp, pullFraction)
            }
        }
        val activeNotifKey = (uiState.content as? CapsuleContent.NotificationAlert)?.key
        LaunchedEffect(isExpandedCard, activeNotifKey) {
            if (!isExpandedCard) {
                if (abs(notifSwipeOffsetDp.value) > 0.01f) {
                    notifSwipeOffsetDp.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 460f))
                }
                if (abs(notifSwipeScale.value - 1f) > 0.001f) {
                    notifSwipeScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 460f))
                }
            } else {
                notifSwipeOffsetDp.snapTo(0f)
                notifSwipeScale.snapTo(1f)
            }
        }

        val layoutSpec = computeCapsuleLayoutDpSpec(state = uiState, isHoldingCapsule = isHolding)
        var retainedSatellite by remember { mutableStateOf(uiState.leftSatellite) }
        LaunchedEffect(uiState.leftSatellite) {
            if (uiState.leftSatellite != null) {
                retainedSatellite = uiState.leftSatellite
            }
        }
        val satJiggleOffsetDp = remember { Animatable(0f) }
        LaunchedEffect(uiState.satelliteJiggleSeq) {
            if (uiState.satelliteJiggleSeq > 0) {
                composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                satJiggleOffsetDp.animateTo(-5.5f, tween(durationMillis = 65))
                satJiggleOffsetDp.animateTo(5.5f, tween(durationMillis = 75))
                satJiggleOffsetDp.animateTo(-2f, tween(durationMillis = 65))
                satJiggleOffsetDp.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 620f))
            }
        }

        val mainCapsuleBox: @Composable (Modifier) -> Unit = { boxModifier ->
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = boxModifier
                    .graphicsLayer {
                        val swipeDp = notifSwipeOffsetDp.value
                        val swipeScale = notifSwipeScale.value
                        if (abs(swipeDp) > 0.01f || abs(swipeScale - 1f) > 0.001f) {
                            val pullFraction = (abs(swipeDp) / 11f).coerceIn(0f, 1f)
                            val effScale = swipeScale.coerceIn(0.86f, 1f)
                            // Pivot at top-center camera cutout so the card swings like a roller below the camera
                            val angleDeg = -(swipeDp / 3.8f).coerceIn(-3.0f, 3.0f)
                            val angleRad = abs(angleDeg) * (PI / 180.0)
                            val sinA = sin(angleRad).toFloat()
                            val cosA = cos(angleRad).toFloat()
                            val topLiftPx = size.width * 0.5f * effScale * sinA
                            val halfMarginPx = size.width * 0.5f * (1f - effScale * cosA)
                            val bottomSwingPx = size.height * effScale * sinA
                            val maxSafeTxPx = (halfMarginPx - bottomSwingPx * 0.55f).coerceAtLeast(0f)
                            val desiredTxPx = swipeDp * density.density * 0.65f

                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationX = if (isBlurSupported) 0f else desiredTxPx.coerceIn(-maxSafeTxPx, maxSafeTxPx)
                            translationY = topLiftPx
                            rotationZ = angleDeg
                            scaleX = effScale
                            scaleY = effScale
                            alpha = 1f - pullFraction * 0.14f
                        }
                    }
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
                                colorB = baseColorB,
                            )
                            drawRect(brush = ShaderBrush(neuralShader))
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (latestUiState.isQsRingOnlyActive) return@awaitEachGesture
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
                                            val activeNotif = latestUiState.content as? CapsuleContent.NotificationAlert
                                            if (activeNotif != null && abs(dx) > 22f && abs(dx) > abs(dy) * 1.35f) {
                                                change.consume()
                                                var lastScreenX = currentScreenX
                                                var lastSampleMs = SystemClock.uptimeMillis()
                                                var velocityXPxPerSec = 0f
                                                var crossedDismissDetent = false
                                                val engageScreenX = currentScreenX
                                                val densityPx = density.density.coerceAtLeast(1f)
                                                var latestDxDp = 0f

                                                do {
                                                    val dragEvent = awaitPointerEvent()
                                                    val dragChange = dragEvent.changes.firstOrNull()
                                                    if (dragChange != null) {
                                                        dragChange.consume()
                                                        latestUserInteraction()
                                                        rootView.getLocationOnScreen(screenLoc)
                                                        val sx = screenLoc[0] + dragChange.position.x
                                                        val nowMs = SystemClock.uptimeMillis()
                                                        val dtSec = ((nowMs - lastSampleMs).coerceAtLeast(1L)) / 1000f
                                                        val instVel = (sx - lastScreenX) / dtSec
                                                        velocityXPxPerSec = velocityXPxPerSec * 0.55f + instVel * 0.45f
                                                        lastScreenX = sx
                                                        lastSampleMs = nowMs

                                                        latestDxDp = (sx - engageScreenX) / densityPx
                                                        val sign = if (latestDxDp < 0f) -1f else 1f
                                                        val normalizedPull = (abs(latestDxDp) / 110f).coerceIn(0f, 1f)
                                                        val rubberDp = sign * (normalizedPull * 10.5f)
                                                        val targetScale = 1f - normalizedPull * 0.11f

                                                        if (!crossedDismissDetent && abs(latestDxDp) >= 68f) {
                                                            crossedDismissDetent = true
                                                            composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                        } else if (crossedDismissDetent && abs(latestDxDp) < 52f) {
                                                            crossedDismissDetent = false
                                                        }

                                                        latestNotificationSwipeChanged(rubberDp, normalizedPull)
                                                        coroutineScope.launch {
                                                            notifSwipeOffsetDp.snapTo(rubberDp)
                                                            notifSwipeScale.snapTo(targetScale)
                                                        }
                                                    }
                                                } while (dragEvent.changes.any { it.pressed })

                                                val velocityXDpPerSec = velocityXPxPerSec / densityPx
                                                val shouldDismiss = abs(latestDxDp) >= 68f ||
                                                    (abs(velocityXDpPerSec) >= 480f && abs(latestDxDp) >= 24f)
                                                if (shouldDismiss) {
                                                    val dir = if (latestDxDp < 0f) -1f else 1f
                                                    composeHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                    coroutineScope.launch {
                                                        launch { notifSwipeScale.animateTo(0.88f, tween(durationMillis = 75)) }
                                                        notifSwipeOffsetDp.animateTo(dir * 11.5f, tween(durationMillis = 75))
                                                        latestReplyFocusChanged(false)
                                                        latestDismissNotification(activeNotif)
                                                        launch {
                                                            notifSwipeScale.animateTo(
                                                                1f,
                                                                spring(dampingRatio = 0.65f, stiffness = 460f),
                                                            )
                                                        }
                                                        notifSwipeOffsetDp.animateTo(
                                                            0f,
                                                            spring(dampingRatio = 0.62f, stiffness = 440f),
                                                        )
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        launch {
                                                            notifSwipeScale.animateTo(
                                                                1f,
                                                                spring(dampingRatio = 0.52f, stiffness = 500f),
                                                            )
                                                        }
                                                        notifSwipeOffsetDp.animateTo(
                                                            0f,
                                                            spring(dampingRatio = 0.48f, stiffness = 520f),
                                                        )
                                                    }
                                                }
                                                return@awaitEachGesture
                                            }
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
                                                    onComplete = { commitExpanded, velocityY, _ ->
                                                        // Always use smooth scrub-to-coast on swipe-up collapse so album art never blinks
                                                        suppressAlbumSharedAfterDrag = true
                                                        latestScrubEnd(commitExpanded, velocityY, false)
                                                    },
                                                )
                                                return@awaitEachGesture
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                return@awaitEachGesture
                            }

                            down.consume()
                            val downAtMs = SystemClock.uptimeMillis()
                            val hasDoubleTapAction = latestUiState.resolveEffectiveDoubleTap().action != CapsuleGestureAction.NONE
                            val isDoubleTapCandidate = hasDoubleTapAction &&
                                lastCompactTapUpMs > 0L &&
                                (downAtMs - lastCompactTapUpMs <= DOUBLE_TAP_LOCK_WINDOW_MS)
                            if (isDoubleTapCandidate) {
                                pendingCompactTapJob?.cancel()
                                pendingCompactTapJob = null
                            }

                            var movedTooFar = false
                            var holdPeekTriggered = false
                            var longHoldTriggered = false
                            var cycledThisGesture = false

                            val holdJob = if (!isDoubleTapCandidate) {
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
                                            longHoldTriggered = true
                                            isHolding = false
                                            onHoldingStateChanged(false)
                                            composeHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            latestLongHoldGesture()
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
                                    if (longHoldTriggered) {
                                        // Hold already expanded/executed action: swallow remaining move/up so release never dismisses card
                                        change.consume()
                                        continue
                                    }
                                    rootView.getLocationOnScreen(screenLoc)
                                    val currentScreenX = screenLoc[0] + change.position.x
                                    val currentScreenY = screenLoc[1] + change.position.y
                                    val dx = currentScreenX - startScreenX
                                    val dy = currentScreenY - startScreenY
                                    if (abs(dy) > 4f || abs(dx) > 4f) {
                                        change.consume()
                                    }
                                    if (dy > 6f && abs(dy) > abs(dx)) {
                                        holdJob?.cancel()
                                    }
                                    if (dy > 10f && abs(dy) > abs(dx) * 1.05f) {
                                        lastCompactTapUpMs = 0L
                                        holdJob?.cancel()
                                        if (isHolding) {
                                            isHolding = false
                                            onHoldingStateChanged(false)
                                            haptics.cancelWave()
                                        }
                                        change.consume()
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
                                            onComplete = { commitExpanded, velocityY, isFastDirectSwipe ->
                                                suppressAlbumSharedAfterDrag = !isFastDirectSwipe
                                                latestScrubEnd(commitExpanded, velocityY, isFastDirectSwipe)
                                            },
                                        )
                                        return@awaitEachGesture
                                    } else if (!cycledThisGesture && abs(dx) > 46f && abs(dx) > abs(dy) * 1.3f) {
                                        lastCompactTapUpMs = 0L
                                        cycledThisGesture = true
                                        movedTooFar = true
                                        holdJob?.cancel()
                                        change.consume()
                                        composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onCycleContent(if (dx < 0f) 1 else -1)
                                    } else if (hypot(dx, dy) > 36f) {
                                        lastCompactTapUpMs = 0L
                                        movedTooFar = true
                                        holdJob?.cancel()
                                    } else if (holdPeekTriggered || cycledThisGesture) {
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            holdJob?.cancel()
                            if (isHolding) {
                                isHolding = false
                                onHoldingStateChanged(false)
                                if (!longHoldTriggered) {
                                    haptics.cancelWave()
                                }
                            }

                            if (!longHoldTriggered && !cycledThisGesture) {
                                if (holdPeekTriggered) {
                                    lastCompactTapUpMs = 0L
                                    onHoldPeekEnd()
                                } else if (!movedTooFar) {
                                    if (isDoubleTapCandidate) {
                                        lastCompactTapUpMs = 0L
                                        pendingCompactTapJob?.cancel()
                                        pendingCompactTapJob = null
                                        composeHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        latestDoubleTapGesture()
                                    } else if (hasDoubleTapAction) {
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
                val isCollapsingScrub = scrubPresentation?.isCollapsing == true
                val compactAlpha = when {
                    f == null -> 1f
                    isCollapsingScrub -> ((0.75f - f) / 0.75f).coerceIn(0f, 1f)
                    else -> ((0.25f - f) / 0.25f).coerceIn(0f, 1f)
                }
                val expandedAlpha = when {
                    f == null -> 1f
                    isCollapsingScrub -> (f / 0.75f).coerceIn(0f, 1f)
                    else -> ((f - 0.25f) / 0.75f).coerceIn(0f, 1f)
                }
            val expandedScale = if (scrubPresentation != null) {
                val widthRatio = (scrubPresentation.currentWidthDp / scrubPresentation.expandedWidthDp.coerceAtLeast(1f))
                    .coerceIn(0.32f, 1.08f)
                val heightRatio = (scrubPresentation.currentHeightDp / scrubPresentation.expandedHeightDp.coerceAtLeast(1f))
                    .coerceIn(0.32f, 1.08f)
                minOf(widthRatio, heightRatio)
            } else {
                1f
            }

            val stageMorphOrigin = TransformOrigin(0.5f, 0f)

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
                    transformOrigin = stageMorphOrigin
                }

            fun resolvePillScrubScale(geomWidthDp: Float): Float = if (scrubPresentation != null) {
                val liveMainW = (scrubPresentation.currentWidthDp -
                    scrubPresentation.satelliteFraction * (uiState.circleDiameterDp + SATELLITE_GAP_DP + SATELLITE_OUTER_PAD_DP))
                    .coerceAtLeast(uiState.circleDiameterDp)
                val pillWidthScale = (liveMainW / geomWidthDp.coerceAtLeast(1f))
                    .coerceIn(0.65f, 1.05f)
                (0.82f + 0.18f * compactAlpha).coerceAtMost(pillWidthScale)
            } else {
                1f
            }

            val scrubMedia = (scrubPresentation?.expandedContent as? CapsuleContent.Media)
                ?: (uiState.content as? CapsuleContent.Media)
            val useScrubCroppedMediaBg = scrubPresentation != null &&
                scrubMedia != null &&
                expandedAlpha > 0.01f

            Box(modifier = Modifier.fillMaxSize()) {
                if (useScrubCroppedMediaBg) {
                    MediaBackdropLayer(
                        media = scrubMedia,
                        timeSeconds = shaderTime,
                        mediaAnimationColorMode = prefs.mediaAnimationColorMode,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = expandedAlpha },
                    )
                }
                // Single unified stage tree ensures State 3 (e.g. MediaPlayer) never unmounts/blinks on vertical drag
                SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = stageKey,
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = {
                            val isBigCardTransition = targetState == CapsuleStageKey.STATE3_BIG_CARD ||
                                initialState == CapsuleStageKey.STATE3_BIG_CARD
                            val origin = if (isBigCardTransition) {
                                stageMorphOrigin
                            } else {
                                TransformOrigin(0.5f, 0.5f)
                            }
                            if (targetState == CapsuleStageKey.STATE1_RING || initialState == CapsuleStageKey.STATE1_RING) {
                                (fadeIn(animationSpec = tween(190)) +
                                    scaleIn(
                                        initialScale = if (isBigCardTransition) 0.68f else 0.86f,
                                        transformOrigin = origin,
                                        animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f),
                                    )) togetherWith
                                    (fadeOut(animationSpec = tween(150)) +
                                        scaleOut(
                                            targetScale = if (isBigCardTransition) 0.68f else 0.86f,
                                            transformOrigin = origin,
                                            animationSpec = tween(175),
                                        )) using SizeTransform(clip = false)
                            } else {
                                fadeIn(animationSpec = tween(200)) togetherWith
                                    fadeOut(animationSpec = tween(150)) using
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
                                val pillGeom = computeSidePillGeometry(
                                    content = pillContent,
                                    cutout = uiState.cutout,
                                    anchorPosition = prefs.cameraPosition,
                                    circleDiameterDp = uiState.circleDiameterDp,
                                )
                                val pillModifier = if (scrubPresentation != null) {
                                    Modifier.scrubContainer(
                                        targetW = pillGeom.totalWidthDp,
                                        targetH = uiState.circleDiameterDp,
                                        alphaVal = compactAlpha,
                                        scaleVal = resolvePillScrubScale(pillGeom.totalWidthDp),
                                    )
                                } else {
                                    Modifier.fillMaxSize()
                                }
                                Box(modifier = pillModifier) {
                                    State2HorizontalPill(
                                        content = pillContent,
                                        cutout = uiState.cutout,
                                        anchorPosition = prefs.cameraPosition,
                                        circleDiameterDp = uiState.circleDiameterDp,
                                        horizontalMediaGlowStyle = prefs.horizontalMediaGlowStyle,
                                        mediaAnimationColorMode = prefs.mediaAnimationColorMode,
                                        swipeDirection = uiState.swipeDirection,
                                        manualSwipeSeq = uiState.manualSwipeSeq,
                                        enableAlbumSharedTransition = enableAlbumSharedTransition,
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
                                        mediaSessions = uiState.mediaSessions,
                                        selectedMediaIndex = uiState.selectedMediaIndex,
                                        expandedMediaGlowEffects = prefs.expandedMediaGlowEffects,
                                        mediaAnimationColorMode = prefs.mediaAnimationColorMode,
                                        topClearanceDp = topClearanceDp,
                                        contentPaddingDp = contentPaddingDp,
                                        timeSeconds = shaderTime,
                                        interactionsEnabled = interactionsEnabled,
                                        showMediaBackdrop = !useScrubCroppedMediaBg,
                                        enableAlbumSharedTransition = enableAlbumSharedTransition,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onUserInteraction = onUserInteraction,
                                        onOpenNotification = onOpenNotification,
                                        onTriggerNotificationAction = onTriggerNotificationAction,
                                        onDismissNotification = onDismissNotification,
                                        onReplyFocusChanged = onReplyFocusChanged,
                                        onOpenMediaApp = onOpenMediaApp,
                                        onSelectMediaSession = onSelectMediaSession,
                                        onOpenAudioOutput = onOpenAudioOutput,
                                        onLyricsActiveChanged = onLyricsActiveChanged,
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
                                        onTriggerLiveActivityAction = onTriggerLiveActivityAction,
                                        onOpenSystemStatusSettings = onOpenSystemStatusSettings,
                                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                                        onOpenHotspotSettings = onOpenHotspotSettings,
                                        onLockScreen = onLockScreen,
                                        onOpenNotificationShade = onOpenNotificationShade,
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
                    val pillGeom = computeSidePillGeometry(
                        content = scrubPresentation.compactContent,
                        cutout = uiState.cutout,
                        anchorPosition = prefs.cameraPosition,
                        circleDiameterDp = uiState.circleDiameterDp,
                    )
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
                                    anchorPosition = prefs.cameraPosition,
                                    circleDiameterDp = uiState.circleDiameterDp,
                                    horizontalMediaGlowStyle = prefs.horizontalMediaGlowStyle,
                                    mediaAnimationColorMode = prefs.mediaAnimationColorMode,
                                    swipeDirection = uiState.swipeDirection,
                                    manualSwipeSeq = uiState.manualSwipeSeq,
                                    enableAlbumSharedTransition = false,
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
                                    mediaSessions = uiState.mediaSessions,
                                    selectedMediaIndex = uiState.selectedMediaIndex,
                                    expandedMediaGlowEffects = prefs.expandedMediaGlowEffects,
                                    mediaAnimationColorMode = prefs.mediaAnimationColorMode,
                                    topClearanceDp = topClearanceDp,
                                    contentPaddingDp = contentPaddingDp,
                                    timeSeconds = shaderTime,
                                    interactionsEnabled = false,
                                    showMediaBackdrop = !useScrubCroppedMediaBg,
                                    enableAlbumSharedTransition = false,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    onUserInteraction = onUserInteraction,
                                    onOpenNotification = onOpenNotification,
                                    onTriggerNotificationAction = onTriggerNotificationAction,
                                    onDismissNotification = onDismissNotification,
                                    onReplyFocusChanged = onReplyFocusChanged,
                                    onOpenMediaApp = onOpenMediaApp,
                                    onSelectMediaSession = onSelectMediaSession,
                                    onOpenAudioOutput = onOpenAudioOutput,
                                    onLyricsActiveChanged = onLyricsActiveChanged,
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
                                    onTriggerLiveActivityAction = onTriggerLiveActivityAction,
                                    onOpenSystemStatusSettings = onOpenSystemStatusSettings,
                                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                                    onOpenHotspotSettings = onOpenHotspotSettings,
                                    onLockScreen = onLockScreen,
                                    onOpenNotificationShade = onOpenNotificationShade,
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

        val latestExpandSatellite by rememberUpdatedState(onExpandSatellite)
        val latestMergeSatelliteIntoMain by rememberUpdatedState(onMergeSatelliteIntoMainBeforeCamera)
        val latestMergeToCamera by rememberUpdatedState(onMergeToCameraBeforeExpand)
        val latestInteractionsEnabled by rememberUpdatedState(interactionsEnabled)
        val mainReactionOffsetXDp = remember { Animatable(0f) }
        val mainReactionScaleX = remember { Animatable(1f) }
        val mainReactionScaleY = remember { Animatable(1f) }
        var previousSatLeadActive by remember { mutableStateOf(uiState.leftSatellite != null) }

        val triggerSatelliteMergeThenExpand: () -> Unit = {
            if (!isMergingSatelliteIntoMain && !isMergingToCameraBeforeExpand) {
                coroutineScope.launch {
                    val hasHorizontalPill = latestUiState.visualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL
                    if (hasHorizontalPill) {
                        // Horizontal Expand -> Left Circle: merge directly into main pill, then expand (skip camera ring)
                        isMergingSatelliteIntoMain = true
                        latestMergeSatelliteIntoMain()
                        delay(225.milliseconds)
                        latestExpandSatellite()
                        delay(320.milliseconds)
                        isMergingSatelliteIntoMain = false
                    } else {
                        // Camera Ring -> Left Circle: merge into camera circle, then expand
                        isMergingToCameraBeforeExpand = true
                        latestMergeToCamera()
                        delay(220.milliseconds)
                        latestExpandSatellite()
                        delay(320.milliseconds)
                        isMergingToCameraBeforeExpand = false
                    }
                }
            }
        }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val totalWidthDp = maxWidth.value
            val totalHeightDp = maxHeight.value
            val circleDp = uiState.circleDiameterDp
            val leadSpanDp = circleDp + SATELLITE_GAP_DP + SATELLITE_OUTER_PAD_DP
            val isSatelliteHiddenForCollapse = isExpandedCard ||
                localCollapseSatLock ||
                (isSatelliteDelayedStage && !isMergingSatelliteIntoMain)
            val targetSatLeadDp = if (
                uiState.leftSatellite != null &&
                !isSatelliteHiddenForCollapse &&
                !isMergingSatelliteIntoMain &&
                !isMergingToCameraBeforeExpand &&
                !isForceCameraCircleStage
            ) {
                leadSpanDp
            } else {
                0f
            }
            val satLeadActive = targetSatLeadDp > 0.5f
            LaunchedEffect(satLeadActive, layoutSpec.satelliteOnLeft) {
                val satDir = if (layoutSpec.satelliteOnLeft) 1f else -1f
                if (previousSatLeadActive && !satLeadActive) {
                    // Merging into main capsule: inward-safe liquid compression & bounce anchored to far edge
                    previousSatLeadActive = false
                    launch {
                        mainReactionOffsetXDp.animateTo(-2.4f * satDir, tween(durationMillis = 70))
                        mainReactionOffsetXDp.animateTo(
                            0f,
                            spring(dampingRatio = 0.52f, stiffness = 500f),
                        )
                    }
                    launch {
                        mainReactionScaleX.animateTo(0.93f, tween(durationMillis = 85))
                        mainReactionScaleX.animateTo(
                            1f,
                            spring(dampingRatio = 0.50f, stiffness = 460f),
                        )
                    }
                    launch {
                        mainReactionScaleY.animateTo(0.95f, tween(durationMillis = 85))
                        mainReactionScaleY.animateTo(
                            1f,
                            spring(dampingRatio = 0.52f, stiffness = 480f),
                        )
                    }
                } else if (!previousSatLeadActive && satLeadActive) {
                    // Separating from main capsule: surface-tension pull then elastic settle anchored to far edge
                    previousSatLeadActive = true
                    launch {
                        mainReactionOffsetXDp.animateTo(-2.0f * satDir, tween(durationMillis = 75))
                        mainReactionOffsetXDp.animateTo(
                            0f,
                            spring(dampingRatio = 0.54f, stiffness = 480f),
                        )
                    }
                    launch {
                        mainReactionScaleX.animateTo(0.95f, tween(durationMillis = 80))
                        mainReactionScaleX.animateTo(
                            1f,
                            spring(dampingRatio = 0.54f, stiffness = 460f),
                        )
                    }
                    launch {
                        mainReactionScaleY.animateTo(0.96f, tween(durationMillis = 80))
                        mainReactionScaleY.animateTo(
                            1f,
                            spring(dampingRatio = 0.54f, stiffness = 480f),
                        )
                    }
                }
            }
            val animatedSatLeadDp by animateFloatAsState(
                targetValue = targetSatLeadDp,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 410f),
                label = "satLeadSpring",
            )
            val ringSideDp = if (isHolding) circleDp + 4f else circleDp
            val maxSafeSatLeadDp = (totalWidthDp - ringSideDp).coerceAtLeast(0f)
            val rawSatLeadDp = when {
                scrubPresentation != null -> {
                    (scrubPresentation.satelliteFraction * leadSpanDp).coerceIn(0f, leadSpanDp)
                }
                isSatelliteHiddenForCollapse -> 0f
                stageKey == CapsuleStageKey.STATE1_RING -> {
                    // Lock separation 1:1 to live window delta so camera ring NEVER stretches into a capsule
                    (totalWidthDp - ringSideDp).coerceIn(0f, leadSpanDp)
                }
                else -> animatedSatLeadDp.coerceIn(0f, leadSpanDp)
            }
            val liveSatLeadDp = rawSatLeadDp.coerceAtMost(maxSafeSatLeadDp)
            LaunchedEffect(uiState.leftSatellite, liveSatLeadDp, isSatelliteHiddenForCollapse) {
                if (isSatelliteHiddenForCollapse || (uiState.leftSatellite == null && liveSatLeadDp < 1.2f)) {
                    retainedSatellite = null
                } else if (uiState.leftSatellite != null) {
                    retainedSatellite = uiState.leftSatellite
                }
            }

            val sepFrac = (liveSatLeadDp / leadSpanDp).coerceIn(0f, 1f)
            val activeSatellite = if (isSatelliteHiddenForCollapse) null else (retainedSatellite ?: uiState.leftSatellite)
            val showMetaball = activeSatellite != null && sepFrac > 0.015f
            val mainBoxWidthDp = when {
                isExpandedCard || isSatelliteHiddenForCollapse -> totalWidthDp
                showMetaball -> (totalWidthDp - liveSatLeadDp).coerceAtLeast(ringSideDp)
                else -> totalWidthDp
            }
            val satOnLeft = layoutSpec.satelliteOnLeft
            val safeReactionOrigin = TransformOrigin(if (satOnLeft) 1f else 0f, 0.5f)
            val clampedReactionTxPx = if (satOnLeft) {
                mainReactionOffsetXDp.value.coerceAtMost(0f) * density.density
            } else {
                mainReactionOffsetXDp.value.coerceAtLeast(0f) * density.density
            }
            val safeReactionScaleX = mainReactionScaleX.value.coerceIn(0.88f, 1f)
            val safeReactionScaleY = mainReactionScaleY.value.coerceIn(0.90f, 1f)

            if (showMetaball) {
                val satHeightDp = totalHeightDp.coerceAtMost(circleDp + 4f)
                val outerPadDp = SATELLITE_OUTER_PAD_DP * sepFrac

                // Gooey black liquid bridge between merging/separating satellite droplet and main capsule
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radiusPx = (satHeightDp * 0.5f).dp.toPx()
                    val cy = radiusPx
                    val leadPx = liveSatLeadDp.dp.toPx()
                    val outerPadPx = outerPadDp.dp.toPx()
                    val jigglePx = satJiggleOffsetDp.value.dp.toPx()
                    val satCenterX = if (satOnLeft) {
                        outerPadPx + radiusPx + jigglePx
                    } else {
                        size.width - outerPadPx - radiusPx + jigglePx
                    }
                    val mainNearCenterX = if (satOnLeft) {
                        leadPx + radiusPx + clampedReactionTxPx
                    } else {
                        (mainBoxWidthDp.dp.toPx() - radiusPx + clampedReactionTxPx).coerceAtLeast(radiusPx)
                    }

                    drawCircle(
                        color = Color.Black,
                        radius = radiusPx,
                        center = Offset(satCenterX, cy),
                    )

                    // Main capsule meniscus bulge reacting to the merging/separating droplet
                    val meniscusFactor = (1f - abs(sepFrac - 0.38f) / 0.62f).coerceIn(0f, 1f)
                    if (meniscusFactor > 0.02f) {
                        val bulgeShiftX = (if (satOnLeft) -1f else 1f) * radiusPx * 0.24f * meniscusFactor
                        drawCircle(
                            color = Color.Black,
                            radius = radiusPx * (0.94f + 0.09f * meniscusFactor),
                            center = Offset(mainNearCenterX + bulgeShiftX, cy),
                        )
                    }

                    if (sepFrac in 0.02f..0.92f) {
                        val neckU = ((sepFrac - 0.02f) / 0.90f).coerceIn(0f, 1f)
                        val waistHalfH = radiusPx * (0.92f * (1f - neckU * neckU))
                        val attachOffsetX = radiusPx * 0.34f
                        val xStart = if (satOnLeft) satCenterX + attachOffsetX else mainNearCenterX + attachOffsetX
                        val xEnd = if (satOnLeft) mainNearCenterX - attachOffsetX else satCenterX - attachOffsetX
                        val midX = (xStart + xEnd) * 0.5f
                        val anchorHalfH = radiusPx * 0.94f
                        val path = Path().apply {
                            moveTo(xStart, cy - anchorHalfH)
                            quadraticTo(midX, cy - waistHalfH, xEnd, cy - anchorHalfH)
                            lineTo(xEnd, cy + anchorHalfH)
                            quadraticTo(midX, cy + waistHalfH, xStart, cy + anchorHalfH)
                            close()
                        }
                        drawPath(path = path, color = Color.Black)
                    }

                    if (animatedSatGlow > 0.01f) {
                        val glowAngle = (shaderTime * 190f) % 360f
                        withTransform({ rotate(degrees = glowAngle, pivot = Offset(satCenterX, cy)) }) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    0.0f to primaryColor.copy(alpha = 0.95f * animatedSatGlow),
                                    0.35f to tertiaryColor.copy(alpha = 0.88f * animatedSatGlow),
                                    0.70f to Color.White.copy(alpha = 0.92f * animatedSatGlow),
                                    1.0f to primaryColor.copy(alpha = 0.95f * animatedSatGlow),
                                    center = Offset(satCenterX, cy),
                                ),
                                radius = (radiusPx - 1.1.dp.toPx()).coerceAtLeast(4f),
                                center = Offset(satCenterX, cy),
                                style = Stroke(width = 2.2.dp.toPx()),
                            )
                        }
                    }

                    // Ultra-small border dot indicators centered at bottom of left satellite circle (only when >1 state)
                    val dotFade = ((sepFrac - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    if (dotFade > 0.02f && activeSatellite.totalCount > 1) {
                        val totalDots = activeSatellite.totalCount
                        val activeDotIdx = activeSatellite.activeIndex.coerceIn(0, totalDots - 1)
                        val stepDeg = if (totalDots <= 4) 22f else (240f / totalDots).coerceIn(18f, 42f)
                        val startDeg = 90f - stepDeg * (totalDots - 1) * 0.5f
                        val dotOrbitRadiusPx = (radiusPx - 1.55.dp.toPx()).coerceAtLeast(4f)
                        for (i in 0 until totalDots) {
                            val angleRad = Math.toRadians((startDeg + i * stepDeg).toDouble())
                            val dotX = satCenterX + (cos(angleRad) * dotOrbitRadiusPx).toFloat()
                            val dotY = cy + (sin(angleRad) * dotOrbitRadiusPx).toFloat()
                            val isCurrentDot = i == activeDotIdx
                            val dotR = (if (isCurrentDot) 1.45.dp else 1.05.dp).toPx()
                            val haloR = (if (isCurrentDot) 2.15.dp else 1.65.dp).toPx()
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.86f * dotFade),
                                radius = haloR,
                                center = Offset(dotX, dotY),
                            )
                            drawCircle(
                                color = if (isCurrentDot) {
                                    primaryColor.copy(alpha = dotFade)
                                } else {
                                    Color.White.copy(alpha = 0.46f * dotFade)
                                },
                                radius = dotR,
                                center = Offset(dotX, dotY),
                            )
                        }
                    }
                }
            }

            // Single permanent mainCapsuleBox so pointerInput never unmounts/cancels when satellite appears or hides
            mainCapsuleBox(
                Modifier
                    .align(if (satOnLeft) Alignment.TopEnd else Alignment.TopStart)
                    .graphicsLayer {
                        if (!isExpandedCard) {
                            transformOrigin = safeReactionOrigin
                            translationX = clampedReactionTxPx
                            scaleX = safeReactionScaleX
                            scaleY = safeReactionScaleY
                        }
                    }
                    .size(width = mainBoxWidthDp.dp, height = totalHeightDp.dp),
            )

            if (showMetaball) {
                val satHeightDp = totalHeightDp.coerceAtMost(circleDp + 4f)
                val outerPadDp = SATELLITE_OUTER_PAD_DP * sepFrac
                val iconProgress = ((sepFrac - 0.25f) / 0.75f).coerceIn(0f, 1f)
                val satInteractAlign = if (satOnLeft) Alignment.TopStart else Alignment.TopEnd
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(satInteractAlign)
                        .padding(
                            start = if (satOnLeft) outerPadDp.dp else 0.dp,
                            end = if (!satOnLeft) outerPadDp.dp else 0.dp,
                        )
                        .offset { IntOffset((satJiggleOffsetDp.value * density.density).roundToInt(), 0) }
                        .size(width = circleDp.dp, height = satHeightDp.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!latestInteractionsEnabled || latestUiState.isQsRingOnlyActive) return@awaitEachGesture
                                down.consume()
                                var swiped = false
                                var holdTriggered = false
                                var totalDx = 0f
                                var totalDy = 0f
                                val holdJob = coroutineScope.launch {
                                    delay(320.milliseconds)
                                    if (!swiped && !isMergingSatelliteIntoMain && !isMergingToCameraBeforeExpand && hypot(totalDx, totalDy) < 22f) {
                                        holdTriggered = true
                                        composeHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        triggerSatelliteMergeThenExpand()
                                    }
                                }
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        if (holdTriggered) {
                                            change.consume()
                                            continue
                                        }
                                        totalDx = change.position.x - down.position.x
                                        totalDy = change.position.y - down.position.y
                                        if (!swiped && abs(totalDx) > 26f && abs(totalDx) > abs(totalDy) * 1.15f) {
                                            swiped = true
                                            holdJob.cancel()
                                            change.consume()
                                            composeHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            onCycleSatellite(if (totalDx < 0f) 1 else -1)
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                holdJob.cancel()

                                if (!swiped && !holdTriggered && !isMergingSatelliteIntoMain && !isMergingToCameraBeforeExpand && hypot(totalDx, totalDy) < 22f) {
                                    composeHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    triggerSatelliteMergeThenExpand()
                                }
                            }
                        },
                ) {
                    if (iconProgress > 0.02f) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = iconProgress
                                val sc = 0.45f + 0.55f * iconProgress
                                scaleX = sc
                                scaleY = sc
                            },
                        ) {
                            LeftSatelliteBubbleIcon(
                                satellite = activeSatellite,
                                primaryColor = primaryColor,
                                swipeSeq = uiState.satelliteSwipeSeq,
                                swipeDirection = uiState.satelliteSwipeDirection,
                            )
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
    onComplete: (commitExpanded: Boolean, velocityYPxPerSec: Float, isFastDirectSwipe: Boolean) -> Unit,
) {
    val scrubStartedAtMs = SystemClock.uptimeMillis()
    var currentFraction = startFraction
    var lastSampleY = engageScreenY
    var lastSampleTimeMs = scrubStartedAtMs
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

    val gestureDurationMs = SystemClock.uptimeMillis() - scrubStartedAtMs
    val isFastDirectSwipe = (abs(smoothedVy) >= 560f && gestureDurationMs <= 340L) ||
        (gestureDurationMs <= 180L && abs(smoothedVy) >= 340f)
    val commitExpanded = if (initiallyFromExpanded) {
        (currentFraction >= 0.75f && smoothedVy > -600f) || smoothedVy > 380f
    } else {
        (currentFraction >= 0.25f && smoothedVy >= -350f) || smoothedVy > 620f
    }
    onComplete(commitExpanded, smoothedVy, isFastDirectSwipe)
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
