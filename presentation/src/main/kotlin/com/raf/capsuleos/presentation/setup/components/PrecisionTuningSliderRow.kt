package com.raf.capsuleos.presentation.setup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.CapsuleSlider
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Calibration slider row with optional adjacent `-` and `+` M3 Expressive step buttons for
 * exact fine tuning, emitting Compose `SegmentFrequentTick` haptics on every value change.
 *
 * @param modifier Applied to the outer row container.
 * @param label Human-readable setting label.
 * @param valueLabel Formatted current value string shown on the trailing header side.
 * @param value Current numeric slider position.
 * @param valueRange Inclusive allowed bounds.
 * @param stepSize Incremental adjustment applied by `-`/`+` buttons and snapping.
 * @param onValueChange Invoked with the updated value when dragged or stepped.
 * @param showStepButtons Whether to display `-` and `+` precision buttons beside the slider.
 */
@Composable
fun PrecisionTuningSliderRow(
    modifier: Modifier = Modifier,
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float,
    onValueChange: (Float) -> Unit,
    showStepButtons: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showStepButtons) {
                StepIconButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.desc_decrease_value),
                    enabled = value > valueRange.start + 0.001f,
                    onClick = {
                        val snapped = snapValueToStep(value - stepSize, valueRange, stepSize)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        onValueChange(snapped)
                    },
                )
            }

            CapsuleSlider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { next ->
                    onValueChange(snapValueToStep(next, valueRange, stepSize))
                },
                valueRange = valueRange,
                modifier = Modifier.weight(1f),
            )

            if (showStepButtons) {
                StepIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.desc_increase_value),
                    enabled = value < valueRange.endInclusive - 0.001f,
                    onClick = {
                        val snapped = snapValueToStep(value + stepSize, valueRange, stepSize)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        onValueChange(snapped)
                    },
                )
            }
        }
    }
}

@Composable
private fun StepIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    }
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    CapsuleExpressiveSurface(
        onClick = onClick,
        enabled = enabled,
        restCornerDp = 16f,
        pressedCornerDp = 8f,
        pressedScale = 0.90f,
        backgroundColor = bgColor,
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Formats dp numbers cleanly as signed or unsigned strings without trailing `.0`. */
internal fun formatDpValue(value: Float, signed: Boolean = false): String {
    val roundedHalf = (value * 2f).roundToInt() / 2f
    val prefix = if (signed && roundedHalf > 0f) "+" else ""
    val num = if (abs(roundedHalf % 1f) < 0.05f) {
        roundedHalf.roundToInt().toString()
    } else {
        roundedHalf.toString()
    }
    return "$prefix$num dp"
}

private fun snapValueToStep(
    raw: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float,
): Float {
    val steps = (raw / stepSize).roundToInt()
    return (steps * stepSize).coerceIn(range.start, range.endInclusive)
}
