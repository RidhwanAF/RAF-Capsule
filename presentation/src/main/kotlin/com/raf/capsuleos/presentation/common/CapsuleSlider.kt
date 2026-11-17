package com.raf.capsuleos.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt

/**
 * Shared state-backed Material 3 slider wrapper with smooth continuous dragging and
 * Compose `HapticFeedbackType.SegmentFrequentTick` feedback matching system volume sliders.
 *
 * @param modifier Applied to the underlying [Slider].
 * @param value Current slider position.
 * @param onValueChange Invoked continuously while the user drags the slider.
 * @param valueRange Inclusive bounds of allowed slider values.
 * @param steps Optional discrete intermediate step count.
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
    onValueChangeFinished: (() -> Unit)? = null,
    onInteraction: () -> Unit = {},
    colors: SliderColors = SliderDefaults.colors(),
) {
    val localHapticFeedback = LocalHapticFeedback.current
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

    val notchBuckets = remember(steps, valueRange) {
        if (steps > 0) {
            steps + 1
        } else {
            val span = valueRange.endInclusive - valueRange.start
            if (span >= 2f) span.roundToInt().coerceIn(8, 40) else 24
        }
    }

    fun bucketFor(v: Float): Int {
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val fraction = ((v - valueRange.start) / span).coerceIn(0f, 1f)
        return (fraction * notchBuckets).roundToInt()
    }

    var lastHapticBucket by remember { mutableIntStateOf(bucketFor(value)) }

    Slider(
        state = sliderState,
        interactionSource = interactionSource,
        onValueChange = { next ->
            sliderState.value = next
            onInteraction()
            val bucket = bucketFor(next)
            if (bucket != lastHapticBucket) {
                lastHapticBucket = bucket
                localHapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            onValueChange(next)
        },
        onValueChangeFinished = {
            localHapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onValueChangeFinished?.invoke()
        },
        colors = colors,
        modifier = modifier,
    )
}
