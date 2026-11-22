package com.raf.capsuleos.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared counter provided by overlay card roots so ancestor swipe/collapse gestures yield whenever
 * a pointer is actively touching or dragging any [CapsuleSlider] without intercepting or consuming
 * the slider's own pointer stream.
 */
val LocalCapsuleSliderTouchCount = staticCompositionLocalOf<MutableIntState?> { null }

/**
 * Shared state-backed Material 3 slider wrapper with smooth continuous dragging and
 * a low-amplitude continuous wave vibration modeled after Android's system volume slider
 * (flowing smoothly while moving rather than ticking on each step change).
 *
 * @param modifier Applied to the underlying [Slider].
 * @param value Current slider position.
 * @param onValueChange Invoked continuously while the user drags the slider.
 * @param valueRange Inclusive bounds of allowed slider values.
 * @param steps Optional discrete intermediate step count.
 * @param enabled Whether the slider responds to touch input.
 * @param onValueChangeFinished Optional callback fired when dragging finishes.
 * @param onInteraction Invoked on slider touch/drag to keep expanded cards open.
 * @param colors Color palette applied to track and thumb.
 */
@Composable
fun CapsuleSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    onInteraction: () -> Unit = {},
    colors: SliderColors = SliderDefaults.colors(),
) {
    val localHapticFeedback = LocalHapticFeedback.current
    val capsuleHaptics = rememberCapsuleHaptics()
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    val sliderState = remember(steps, valueRange) {
        SliderState(value = value, steps = steps, trackRange = valueRange)
    }

    val animatedExternalValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 160),
        label = "capsuleSliderExternalSync",
    )

    LaunchedEffect(animatedExternalValue, isDragged) {
        if (!isDragged) {
            sliderState.value = animatedExternalValue
        }
    }

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val moveEpsilon = span * 0.002f
    val boundEpsilon = span * 0.005f

    var lastMovedValue by remember(valueRange) { mutableFloatStateOf(value) }
    var boundaryEdge by remember(valueRange) { mutableIntStateOf(0) }
    var slideIdleJob by remember { mutableStateOf<Job?>(null) }

    // Fallback bucket state used only on devices lacking amplitude-controlled motors.
    val fallbackBuckets = remember(steps, valueRange) { if (steps > 0) steps + 1 else 28 }
    fun fallbackBucketFor(v: Float): Int =
        (((v - valueRange.start) / span).coerceIn(0f, 1f) * fallbackBuckets).roundToInt()

    var lastFallbackBucket by remember(valueRange, fallbackBuckets) {
        mutableIntStateOf(fallbackBucketFor(value))
    }

    LaunchedEffect(value, isDragged) {
        if (!isDragged) {
            slideIdleJob?.cancel()
            slideIdleJob = null
            capsuleHaptics.stopSmoothSlideWave()
            lastMovedValue = value
            lastFallbackBucket = fallbackBucketFor(value)
            boundaryEdge = when {
                value <= valueRange.start + boundEpsilon -> -1
                value >= valueRange.endInclusive - boundEpsilon -> 1
                else -> 0
            }
        }
    }

    val activeSliderTouchCount = LocalCapsuleSliderTouchCount.current
    var isThisSliderTouched by remember { mutableStateOf(false) }

    DisposableEffect(capsuleHaptics, activeSliderTouchCount) {
        onDispose {
            slideIdleJob?.cancel()
            capsuleHaptics.stopSmoothSlideWave()
            if (isThisSliderTouched) {
                isThisSliderTouched = false
                activeSliderTouchCount?.let { counter ->
                    counter.intValue = (counter.intValue - 1).coerceAtLeast(0)
                }
            }
        }
    }

    Slider(
        state = sliderState,
        enabled = enabled,
        interactionSource = interactionSource,
        onValueChange = { next ->
            if (!enabled) return@Slider
            sliderState.value = next
            onInteraction()

            if (abs(next - lastMovedValue) > moveEpsilon) {
                lastMovedValue = next
                val newEdge = when {
                    next <= valueRange.start + boundEpsilon -> -1
                    next >= valueRange.endInclusive - boundEpsilon -> 1
                    else -> 0
                }
                if (newEdge != 0 && newEdge != boundaryEdge) {
                    boundaryEdge = newEdge
                    slideIdleJob?.cancel()
                    slideIdleJob = null
                    capsuleHaptics.slideBoundaryThud()
                } else {
                    boundaryEdge = newEdge
                    if (capsuleHaptics.supportsAmplitudeWave) {
                        capsuleHaptics.onSmoothSlideMove()
                        slideIdleJob?.cancel()
                        slideIdleJob = coroutineScope.launch {
                            delay(85.milliseconds)
                            capsuleHaptics.stopSmoothSlideWave()
                        }
                    } else {
                        val bucket = fallbackBucketFor(next)
                        if (bucket != lastFallbackBucket) {
                            lastFallbackBucket = bucket
                            localHapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    }
                }
            }
            onValueChange(next)
        },
        onValueChangeFinished = {
            slideIdleJob?.cancel()
            slideIdleJob = null
            capsuleHaptics.stopSmoothSlideWave()
            onValueChangeFinished?.invoke()
        },
        colors = colors,
        modifier = modifier.pointerInput(enabled, activeSliderTouchCount) {
            if (!enabled || activeSliderTouchCount == null) return@pointerInput
            awaitEachGesture {
                // Observe on Initial pass WITHOUT consuming so Slider receives pristine pointer events
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                isThisSliderTouched = true
                activeSliderTouchCount.intValue += 1
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                } finally {
                    if (isThisSliderTouched) {
                        isThisSliderTouched = false
                        activeSliderTouchCount.intValue = (activeSliderTouchCount.intValue - 1).coerceAtLeast(0)
                    }
                }
            }
        },
    )
}
