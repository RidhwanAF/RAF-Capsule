package com.raf.capsuleos.presentation.setup.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes
import com.raf.capsuleos.presentation.setup.SetupUiState

/**
 * Category card combining system permissions (which smoothly animate out as granted),
 * the Start / Pause Capsule button (enabled dynamically once Accessibility overlay is granted),
 * and the Navigation 3 Interactive Simulation launcher button directly underneath.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetupCategoryCard(
    modifier: Modifier = Modifier,
    uiState: SetupUiState,
    adbCommand: String,
    onGrantAccessibility: () -> Unit,
    onGrantNotificationListener: () -> Unit,
    onGrantWriteSettings: () -> Unit,
    onGrantDndPolicyAccess: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    onToggleStartPause: () -> Unit,
    onOpenSimulation: () -> Unit,
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
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.title_setup_category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                AnimatedVisibility(
                    visible = uiState.areAllPermissionsGranted,
                    enter = fadeIn(tween(240)),
                    exit = fadeOut(tween(180)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.label_granted),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !uiState.areAllPermissionsGranted,
                enter = fadeIn(tween(220)) + expandVertically(tween(240)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(240)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.label_setup_required_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedPermissionRow(
                        visible = !uiState.hasAccessibilityOverlay,
                        title = stringResource(R.string.label_accessibility_permission),
                        description = stringResource(R.string.msg_accessibility_permission_desc),
                        actionLabel = stringResource(R.string.action_grant),
                        onActionClick = onGrantAccessibility,
                    )
                    AnimatedPermissionRow(
                        visible = !uiState.hasNotificationListener,
                        title = stringResource(R.string.label_media_permission),
                        description = stringResource(R.string.msg_media_permission_desc),
                        actionLabel = stringResource(R.string.action_grant),
                        onActionClick = onGrantNotificationListener,
                    )
                    AnimatedPermissionRow(
                        visible = !uiState.hasWriteSettings,
                        title = stringResource(R.string.label_write_settings),
                        description = stringResource(R.string.msg_write_settings_desc),
                        actionLabel = stringResource(R.string.action_grant),
                        onActionClick = onGrantWriteSettings,
                    )
                    AnimatedPermissionRow(
                        visible = !uiState.hasDndPolicyAccess,
                        title = stringResource(R.string.label_dnd_policy_access),
                        description = stringResource(R.string.msg_dnd_policy_desc),
                        actionLabel = stringResource(R.string.action_grant),
                        onActionClick = onGrantDndPolicyAccess,
                    )
                    AnimatedAdbPermissionRow(
                        visible = !uiState.hasWriteSecureSettings,
                        adbCommand = adbCommand,
                        onCopyCommand = onCopyAdbCommand,
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.areAllPermissionsGranted,
                enter = fadeIn(tween(240)) + expandVertically(tween(220)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(220)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.label_setup_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggleStartPause()
                },
                enabled = uiState.hasAccessibilityOverlay,
                shapes = capsuleExpressiveButtonShapes(restRadius = 24.dp, pressedRadius = 10.dp),
                colors = if (uiState.isCapsuleRunning) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(
                    imageVector = if (uiState.isCapsuleRunning) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (uiState.isCapsuleRunning) {
                            R.string.action_pause_capsule
                        } else {
                            R.string.action_start_capsule
                        },
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onOpenSimulation()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 22.dp, pressedRadius = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_open_simulation),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedPermissionRow(
    modifier: Modifier = Modifier,
    visible: Boolean,
    title: String,
    description: String,
    actionLabel: String,
    onActionClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(tween(240)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(240)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onActionClick()
                    },
                    shapes = capsuleExpressiveButtonShapes(restRadius = 18.dp, pressedRadius = 8.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedAdbPermissionRow(
    modifier: Modifier = Modifier,
    visible: Boolean,
    adbCommand: String,
    onCopyCommand: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(tween(240)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(240)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.label_secure_settings),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.msg_secure_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = adbCommand,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCopyCommand()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 18.dp, pressedRadius = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_copy_adb))
            }
        }
    }
}
