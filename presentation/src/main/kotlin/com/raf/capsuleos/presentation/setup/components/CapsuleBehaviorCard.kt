package com.raf.capsuleos.presentation.setup.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.presentation.R
import kotlin.math.roundToInt

/**
 * Category card for status bar integration, system popup suppression, expanded content inner padding,
 * and auto-collapse timing preferences, using [AdaptiveSubItemFlowRow] for sub-items inside each section.
 */
@Composable
fun CapsuleBehaviorCard(
    modifier: Modifier = Modifier,
    preferences: CapsulePreferences,
    collapsible: Boolean = true,
    initiallyExpanded: Boolean = true,
    onOpenDedicatedScreen: (() -> Unit)? = null,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val isBodyVisible = !collapsible || expanded

    ExpandableCategoryCard(
        collapsible = collapsible,
        expanded = isBodyVisible,
        onToggleExpand = { expanded = !expanded },
        header = {
            ExpandableCategoryHeader(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.title_behavior_category),
                subtitle = stringResource(R.string.msg_behavior_subtitle),
                expanded = isBodyVisible,
                collapsible = collapsible,
                onToggleExpand = { expanded = !expanded },
                onOpenDedicatedScreen = onOpenDedicatedScreen,
            )
        },
        modifier = modifier,
    ) {
        SettingsSubSectionBox(
            title = stringResource(R.string.section_system_integration),
            icon = Icons.Rounded.Layers,
        ) {
            AdaptiveSubItemFlowRow { itemMod ->
                BehaviorSwitchRow(
                    label = stringResource(R.string.label_show_on_lock_screen),
                    checked = preferences.showOnLockScreen,
                    enabled = true,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(showOnLockScreen = checked) }
                    },
                    modifier = itemMod,
                )

                BehaviorSwitchRow(
                    label = stringResource(R.string.label_below_status_bar),
                    checked = preferences.belowStatusBar,
                    enabled = true,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(belowStatusBar = checked) }
                    },
                    modifier = itemMod,
                )

                BehaviorSwitchRow(
                    label = stringResource(R.string.label_hide_on_qs_open),
                    checked = preferences.hideOnQsOpen,
                    enabled = true,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(hideOnQsOpen = checked) }
                    },
                    modifier = itemMod,
                )

                BehaviorSwitchRow(
                    label = stringResource(R.string.label_suppress_system_heads_up),
                    checked = preferences.suppressSystemHeadsUp,
                    enabled = true,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(suppressSystemHeadsUp = checked) }
                    },
                    modifier = itemMod,
                )
            }
        }

        SettingsSubSectionBox(
            title = stringResource(R.string.section_spacing_timeouts),
            icon = Icons.Rounded.Timer,
        ) {
            AdaptiveSubItemFlowRow { itemMod ->
                PrecisionTuningSliderRow(
                    label = stringResource(R.string.label_expanded_content_padding),
                    valueLabel = formatDpValue(preferences.expandedContentPaddingDp),
                    value = preferences.expandedContentPaddingDp,
                    valueRange = 8f..32f,
                    stepSize = 1f,
                    onValueChange = { next ->
                        onUpdatePreferences { it.copy(expandedContentPaddingDp = next) }
                    },
                    modifier = itemMod,
                )

                val horizontalDelayMs = preferences.horizontalAutoCollapseDelayMs.coerceIn(2000L, 15000L)
                PrecisionTuningSliderRow(
                    label = stringResource(R.string.label_horizontal_auto_collapse_ms),
                    valueLabel = "$horizontalDelayMs ms",
                    value = horizontalDelayMs.toFloat(),
                    valueRange = 2000f..15000f,
                    stepSize = 500f,
                    onValueChange = { next ->
                        val snapped = ((next / 500f).roundToInt() * 500L).coerceIn(2000L, 15000L)
                        onUpdatePreferences { it.copy(horizontalAutoCollapseDelayMs = snapped) }
                    },
                    modifier = itemMod,
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
                    modifier = itemMod,
                )
            }
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
                .basicMarquee(),
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
