package com.raf.capsuleos.presentation.simulation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes
import com.raf.capsuleos.presentation.simulation.SimulationUiState

/**
 * Category cards below the simulated phone viewport allowing isolated triggering of Media,
 * Notifications, Volume, Flashlight, Alarm, Charging, Biometric Unlock, and Left/Center/Right preview.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimulationControlsSection(
    modifier: Modifier = Modifier,
    uiState: SimulationUiState,
    onToggleMedia: () -> Unit,
    onToggleMultiMedia: () -> Unit = {},
    onPostNotification: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleHotspot: () -> Unit = {},
    onToggleLiveActivity: () -> Unit = {},
    onTriggerBluetooth: () -> Unit = {},
    onTriggerCall: () -> Unit = {},
    onTriggerAlarm: () -> Unit,
    onToggleCharging: () -> Unit,
    onTriggerUnlock: () -> Unit,
    onResetRing: () -> Unit,
    onSelectPreviewPosition: (CameraAnchorPosition) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val currentPos = uiState.capsuleUiState.preferences.cameraPosition

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Live State & Action Log Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.title_sandbox_activity),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uiState.capsuleUiState.visualState.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = uiState.latestActionLog ?: stringResource(R.string.msg_sandbox_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Sandbox Event Triggers Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.title_simulation_events),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SandboxTriggerButton(
                        label = stringResource(R.string.action_test_notification),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onPostNotification()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_media_toggle),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onToggleMedia()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_torch),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onToggleTorch()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SandboxTriggerButton(
                        label = "${stringResource(R.string.action_test_volume)} -",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            onVolumeDown()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = "${stringResource(R.string.action_test_volume)} +",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            onVolumeUp()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.action_test_alarm),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTriggerAlarm()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_battery),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onToggleCharging()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_unlocked),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTriggerUnlock()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_reset),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onResetRing()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_bluetooth),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTriggerBluetooth()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_hotspot),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onToggleHotspot()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_live_activity),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onToggleLiveActivity()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_multi_media),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onToggleMultiMedia()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SandboxTriggerButton(
                        label = stringResource(R.string.label_sim_call),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTriggerCall()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.label_camera_anchor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CameraAnchorPosition.entries.forEach { anchor ->
                        val selected = currentPos == anchor
                        val bgColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = tween(durationMillis = 180),
                            label = "simPosBg_${anchor.name}",
                        )
                        val label = stringResource(
                            when (anchor) {
                                CameraAnchorPosition.LEFT -> R.string.label_anchor_left
                                CameraAnchorPosition.CENTER -> R.string.label_anchor_center
                                CameraAnchorPosition.RIGHT -> R.string.label_anchor_right
                            },
                        )
                        CapsuleExpressiveSurface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelectPreviewPosition(anchor)
                            },
                            restCornerDp = 16f,
                            pressedCornerDp = 8f,
                            pressedScale = 0.95f,
                            backgroundColor = bgColor,
                            border = if (selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SandboxTriggerButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = capsuleExpressiveButtonShapes(restRadius = 18.dp, pressedRadius = 8.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
