package com.raf.capsuleos.presentation.setup.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.ExpandedMediaGlowEffect
import com.raf.capsuleos.domain.model.HorizontalMediaGlowStyle
import com.raf.capsuleos.domain.model.SatellitePosition
import com.raf.capsuleos.presentation.R
import kotlin.math.roundToInt

/**
 * Category card configuring opening/closing neuron glow, outer screen-frame neuron glow,
 * battery ring display, Bluetooth/Call indicators, separated Left Satellite sources (9 system
 * states), independent Expanded (multi-select) & Horizontal Media Glow styles, and Media Auto-Collapse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndicatorsAndMediaCard(
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
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.title_indicators_media_category),
                subtitle = stringResource(R.string.msg_indicators_subtitle),
                expanded = isBodyVisible,
                collapsible = collapsible,
                onToggleExpand = { expanded = !expanded },
                onOpenDedicatedScreen = onOpenDedicatedScreen,
            )
        },
        modifier = modifier,
    ) {
        SettingsSubSectionBox(
            title = stringResource(R.string.section_glow_visuals),
            icon = Icons.Rounded.AutoAwesome,
        ) {
            AdaptiveSubItemFlowRow { itemMod ->
                IndicatorSwitchRow(
                    label = stringResource(R.string.label_enable_neuron_glow),
                    checked = preferences.enableNeuronGlow,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(enableNeuronGlow = checked) }
                    },
                    modifier = itemMod,
                )
                IndicatorSwitchRow(
                    label = stringResource(R.string.label_enable_outer_neuron_glow),
                    checked = preferences.enableOuterNeuronGlow,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(enableOuterNeuronGlow = checked) }
                    },
                    modifier = itemMod,
                )
                IndicatorSwitchRow(
                    label = stringResource(R.string.label_show_battery_on_ring),
                    checked = preferences.showBatteryOnCameraRing,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(showBatteryOnCameraRing = checked) }
                    },
                    modifier = itemMod,
                )
            }
        }

        SettingsSubSectionBox(
            title = stringResource(R.string.section_system_alerts),
            icon = Icons.Rounded.NotificationsActive,
        ) {
            AdaptiveSubItemFlowRow { itemMod ->
                IndicatorSwitchRow(
                    label = stringResource(R.string.label_enable_bluetooth_alerts),
                    checked = preferences.enableBluetoothAlerts,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(enableBluetoothAlerts = checked) }
                    },
                    modifier = itemMod,
                )
                IndicatorSwitchRow(
                    label = stringResource(R.string.label_enable_call_auto_expand),
                    checked = preferences.enableCallAutoExpand,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        onUpdatePreferences { it.copy(enableCallAutoExpand = checked) }
                    },
                    modifier = itemMod,
                )
            }
        }

        SettingsSubSectionBox(
            title = stringResource(R.string.section_left_satellite),
            icon = Icons.Rounded.RadioButtonChecked,
        ) {
            IndicatorSwitchRow(
                label = stringResource(R.string.label_enable_left_satellite),
                checked = preferences.enableLeftSatelliteBubble,
                onCheckedChange = { checked ->
                    haptics.performHapticFeedback(
                        if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                    onUpdatePreferences { it.copy(enableLeftSatelliteBubble = checked) }
                },
            )

            AnimatedVisibility(
                visible = preferences.enableLeftSatelliteBubble,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SettingsNestedInsetBox {
                    Text(
                        text = stringResource(R.string.label_satellite_position),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    val positions = listOf(
                        SatellitePosition.LEFT to stringResource(R.string.label_anchor_left),
                        SatellitePosition.RIGHT to stringResource(R.string.label_anchor_right),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        positions.forEachIndexed { index, (position, label) ->
                            SegmentedButton(
                                selected = preferences.satellitePosition == position,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onUpdatePreferences { it.copy(satellitePosition = position) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = positions.size),
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee(),
                                    )
                                },
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )

                    AdaptiveSubItemFlowRow { itemMod ->
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_torch),
                            leadingIcon = Icons.Rounded.FlashlightOn,
                            checked = preferences.showTorchInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showTorchInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_hotspot),
                            leadingIcon = Icons.Rounded.WifiTethering,
                            checked = preferences.showHotspotInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showHotspotInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_live_activity),
                            leadingIcon = Icons.Rounded.Timer,
                            checked = preferences.showLiveActivityInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showLiveActivityInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_airplane),
                            leadingIcon = Icons.Rounded.AirplanemodeActive,
                            checked = preferences.showAirplaneInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showAirplaneInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_dnd),
                            leadingIcon = Icons.Rounded.DoNotDisturbOn,
                            checked = preferences.showDndInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showDndInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_battery_saver),
                            leadingIcon = Icons.Rounded.BatterySaver,
                            checked = preferences.showBatterySaverInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showBatterySaverInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_vpn),
                            leadingIcon = Icons.Rounded.VpnKey,
                            checked = preferences.showVpnInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showVpnInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_ringer_muted),
                            leadingIcon = Icons.Rounded.NotificationsOff,
                            checked = preferences.showRingerMutedInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showRingerMutedInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                        IndicatorSwitchRow(
                            label = stringResource(R.string.label_satellite_data_saver),
                            leadingIcon = Icons.Rounded.DataSaverOn,
                            checked = preferences.showDataSaverInSatellite,
                            onCheckedChange = { checked ->
                                haptics.performHapticFeedback(
                                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                onUpdatePreferences { it.copy(showDataSaverInSatellite = checked) }
                            },
                            modifier = itemMod,
                        )
                    }
                }
            }
        }

        SettingsSubSectionBox(
            title = stringResource(R.string.label_media_expanded_animations),
            icon = Icons.Rounded.MusicNote,
        ) {
            val expandedEffectOptions = listOf(
                ExpandedMediaGlowEffect.CARD_BORDER_NEURON to stringResource(R.string.label_media_expanded_border_glow),
                ExpandedMediaGlowEffect.OUTER_SCREEN_GLOW to stringResource(R.string.label_media_expanded_outer_glow),
                ExpandedMediaGlowEffect.ALBUM_ART_RING to stringResource(R.string.label_media_expanded_album_ring),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                expandedEffectOptions.forEach { (effect, label) ->
                    val isSelected = effect in preferences.expandedMediaGlowEffects
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val nextSelected = !isSelected
                            haptics.performHapticFeedback(
                                if (nextSelected) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onUpdatePreferences { current ->
                                val next = if (effect in current.expandedMediaGlowEffects) {
                                    current.expandedMediaGlowEffects - effect
                                } else {
                                    current.expandedMediaGlowEffects + effect
                                }
                                current.copy(expandedMediaGlowEffects = next)
                            }
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            SettingsNestedInsetBox {
                AdaptiveSubItemFlowRow { itemMod ->
                    IndicatorSwitchRow(
                        label = stringResource(R.string.label_keep_expanded_media_glow),
                        checked = preferences.keepExpandedMediaGlowAlways,
                        onCheckedChange = { checked ->
                            haptics.performHapticFeedback(
                                if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onUpdatePreferences { it.copy(keepExpandedMediaGlowAlways = checked) }
                        },
                        modifier = itemMod,
                    )
                    IndicatorSwitchRow(
                        label = stringResource(R.string.label_keep_outer_media_glow),
                        checked = preferences.keepOuterMediaGlowAlways,
                        onCheckedChange = { checked ->
                            haptics.performHapticFeedback(
                                if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onUpdatePreferences { it.copy(keepOuterMediaGlowAlways = checked) }
                        },
                        modifier = itemMod,
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextChecked = !preferences.mediaFollowGlobalAutoCollapse
                            haptics.performHapticFeedback(
                                if (nextChecked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onUpdatePreferences {
                                it.copy(mediaFollowGlobalAutoCollapse = nextChecked)
                            }
                        }
                        .padding(vertical = 2.dp),
                ) {
                    Checkbox(
                        checked = preferences.mediaFollowGlobalAutoCollapse,
                        onCheckedChange = { checked ->
                            haptics.performHapticFeedback(
                                if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            onUpdatePreferences { it.copy(mediaFollowGlobalAutoCollapse = checked) }
                        },
                    )
                    Text(
                        text = stringResource(R.string.label_media_follow_global_auto_collapse),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                    )
                }

                val mediaDelayMs = preferences.mediaAutoCollapseDelayMs.coerceIn(10_000L, 60_000L)
                val mediaDelaySec = (mediaDelayMs / 1000L).toInt()
                PrecisionTuningSliderRow(
                    label = stringResource(R.string.label_media_auto_collapse_sec),
                    valueLabel = "${mediaDelaySec}s",
                    value = mediaDelaySec.toFloat(),
                    valueRange = 10f..60f,
                    stepSize = 1f,
                    enabled = !preferences.mediaFollowGlobalAutoCollapse,
                    onValueChange = { nextSec ->
                        val snappedMs = (nextSec.roundToInt().coerceIn(10, 60) * 1000L)
                        onUpdatePreferences { it.copy(mediaAutoCollapseDelayMs = snappedMs) }
                    },
                )
            }
        }

        SettingsSubSectionBox(
            title = stringResource(R.string.label_media_horizontal_glow),
            icon = Icons.Rounded.MusicNote,
        ) {
            val horizontalGlowOptions = listOf(
                HorizontalMediaGlowStyle.OFF to stringResource(R.string.label_media_horizontal_off),
                HorizontalMediaGlowStyle.HORIZONTAL_PILL to stringResource(R.string.label_media_glow_pill),
                HorizontalMediaGlowStyle.CAMERA_RING to stringResource(R.string.label_media_glow_ring),
                HorizontalMediaGlowStyle.ALBUM_ART_RING to stringResource(R.string.label_media_glow_album),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                horizontalGlowOptions.forEachIndexed { index, (styleOption, label) ->
                    SegmentedButton(
                        selected = preferences.horizontalMediaGlowStyle == styleOption,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onUpdatePreferences { it.copy(horizontalMediaGlowStyle = styleOption) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = horizontalGlowOptions.size),
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndicatorSwitchRow(
    modifier: Modifier = Modifier,
    label: String,
    leadingIcon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
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
            onCheckedChange = onCheckedChange,
        )
    }
}
