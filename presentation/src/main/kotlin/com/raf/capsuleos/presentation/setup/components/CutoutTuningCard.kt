package com.raf.capsuleos.presentation.setup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleSlider
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes
import kotlin.math.roundToInt

/**
 * Card providing Pixel 9 Pro camera cutout alignment sliders, status bar hide toggle,
 * and instant test triggers for Volume, Alarm, and Unlock pills.
 *
 * @param modifier Applied to the card container.
 * @param preferences Current persisted preferences.
 * @param isServiceRunning Whether the overlay service is active.
 * @param onUpdatePreferences Callback to mutate preferences.
 * @param onTestVolume Triggers a test volume pill.
 * @param onTestAlarm Triggers a test alarm pill.
 * @param onTestUnlock Triggers a test unlock pill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CutoutTuningCard(
    modifier: Modifier = Modifier,
    preferences: CapsulePreferences,
    isServiceRunning: Boolean,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
    onTestVolume: () -> Unit,
    onTestAlarm: () -> Unit,
    onTestUnlock: () -> Unit,
    onTestNotification: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.title_quick_test),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTestVolume()
                        },
                        shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                        enabled = isServiceRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.action_test_volume), maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTestAlarm()
                        },
                        shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                        enabled = isServiceRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.action_test_alarm), maxLines = 1)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTestUnlock()
                        },
                        shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                        enabled = isServiceRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.action_test_unlock), maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTestNotification()
                        },
                        shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                        enabled = isServiceRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.action_test_notification), maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.label_below_status_bar),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = preferences.belowStatusBar,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onUpdatePreferences { it.copy(belowStatusBar = checked) }
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.label_auto_hide_status_icons),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = preferences.autoHideStatusBarIcons,
                    enabled = !preferences.belowStatusBar,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onUpdatePreferences { it.copy(autoHideStatusBarIcons = checked) }
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.label_suppress_system_heads_up),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = preferences.suppressSystemHeadsUp,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onUpdatePreferences { it.copy(suppressSystemHeadsUp = checked) }
                    },
                )
            }

            TuningSliderRow(
                label = "${stringResource(R.string.label_cutout_width)}: +${preferences.paddingWidthDp.roundToInt()} dp",
                value = preferences.paddingWidthDp,
                range = 2f..28f,
                onValueChange = { next ->
                    onUpdatePreferences { it.copy(paddingWidthDp = next.roundToInt().toFloat()) }
                },
            )

            TuningSliderRow(
                label = "${stringResource(R.string.label_cutout_height)}: +${preferences.paddingHeightDp.roundToInt()} dp",
                value = preferences.paddingHeightDp,
                range = 2f..20f,
                onValueChange = { next ->
                    onUpdatePreferences { it.copy(paddingHeightDp = next.roundToInt().toFloat()) }
                },
            )

            TuningSliderRow(
                label = "${stringResource(R.string.label_cutout_top_offset)}: ${preferences.topOffsetAdjustDp.roundToInt()} dp",
                value = preferences.topOffsetAdjustDp,
                range = -12f..12f,
                onValueChange = { next ->
                    onUpdatePreferences { it.copy(topOffsetAdjustDp = next.roundToInt().toFloat()) }
                },
            )

            TuningSliderRow(
                label = "${stringResource(R.string.label_auto_collapse_ms)}: ${preferences.autoCollapseDelayMs} ms",
                value = preferences.autoCollapseDelayMs.toFloat().coerceIn(1500f, 15000f),
                range = 1500f..15000f,
                onValueChange = { next ->
                    val snapped = ((next / 500f).roundToInt() * 500L).coerceIn(1500L, 15000L)
                    onUpdatePreferences { it.copy(autoCollapseDelayMs = snapped) }
                },
            )
        }
    }
}

@Composable
private fun TuningSliderRow(
    modifier: Modifier = Modifier,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CapsuleSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
