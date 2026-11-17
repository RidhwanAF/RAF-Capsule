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
import kotlin.math.roundToInt

/**
 * Category card for status bar integration, system popup suppression, expanded content inner padding,
 * and auto-collapse timing preferences.
 */
@Composable
fun CapsuleBehaviorCard(
    modifier: Modifier = Modifier,
    preferences: CapsulePreferences,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(22.dp),
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
                text = stringResource(R.string.title_behavior_category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(10.dp))

            BehaviorSwitchRow(
                label = stringResource(R.string.label_below_status_bar),
                checked = preferences.belowStatusBar,
                enabled = true,
                onCheckedChange = { checked ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onUpdatePreferences { it.copy(belowStatusBar = checked) }
                },
            )

            BehaviorSwitchRow(
                label = stringResource(R.string.label_auto_hide_status_icons),
                checked = preferences.autoHideStatusBarIcons,
                enabled = !preferences.belowStatusBar,
                onCheckedChange = { checked ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onUpdatePreferences { it.copy(autoHideStatusBarIcons = checked) }
                },
            )

            BehaviorSwitchRow(
                label = stringResource(R.string.label_suppress_system_heads_up),
                checked = preferences.suppressSystemHeadsUp,
                enabled = true,
                onCheckedChange = { checked ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onUpdatePreferences { it.copy(suppressSystemHeadsUp = checked) }
                },
            )

            PrecisionTuningSliderRow(
                label = stringResource(R.string.label_expanded_content_padding),
                valueLabel = formatDpValue(preferences.expandedContentPaddingDp),
                value = preferences.expandedContentPaddingDp,
                valueRange = 8f..32f,
                stepSize = 1f,
                onValueChange = { next ->
                    onUpdatePreferences { it.copy(expandedContentPaddingDp = next) }
                },
            )

            val delayMs = preferences.autoCollapseDelayMs.coerceIn(1500L, 15000L)
            PrecisionTuningSliderRow(
                label = stringResource(R.string.label_auto_collapse_ms),
                valueLabel = "$delayMs ms",
                value = delayMs.toFloat(),
                valueRange = 1500f..15000f,
                stepSize = 500f,
                onValueChange = { next ->
                    val snapped = ((next / 500f).roundToInt() * 500L).coerceIn(1500L, 15000L)
                    onUpdatePreferences { it.copy(autoCollapseDelayMs = snapped) }
                },
            )
        }
    }
}

@Composable
private fun BehaviorSwitchRow(
    modifier: Modifier = Modifier,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
