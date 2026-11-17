package com.raf.capsuleos.presentation.setup.components

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/**
 * Displays a permission row with live grant status and action button using M3 Expressive motion.
 *
 * @param modifier Applied to the card container.
 * @param title Permission title.
 * @param description Purpose explanation.
 * @param isGranted True when the permission is active.
 * @param actionLabel Button label when not yet granted.
 * @param onActionClick Invoked when the user taps the action button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PermissionStatusCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
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

            Spacer(modifier = Modifier.width(12.dp))

            if (isGranted) {
                FilledTonalButton(
                    onClick = {},
                    shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                    enabled = false,
                ) {
                    Text(text = stringResource(R.string.label_granted))
                }
            } else {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onActionClick()
                    },
                    shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

/**
 * Displays the optional one-time ADB command card for granting `WRITE_SECURE_SETTINGS`
 * to hide status bar icons while expanded.
 *
 * @param modifier Applied to the card container.
 * @param isGranted Whether `WRITE_SECURE_SETTINGS` is currently granted.
 * @param adbCommand Exact ADB shell command string.
 * @param onCopyCommand Copies [adbCommand] to the clipboard.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdbSecureSettingsCard(
    modifier: Modifier = Modifier,
    isGranted: Boolean,
    adbCommand: String,
    onCopyCommand: () -> Unit,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
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
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(10.dp))
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
                    shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
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
}
