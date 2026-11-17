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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes

/**
 * Category card housing synthetic live-overlay trigger actions when the system overlay service
 * is running.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickTestTriggersCard(
    modifier: Modifier = Modifier,
    isServiceRunning: Boolean,
    onTestVolume: () -> Unit,
    onTestAlarm: () -> Unit,
    onTestUnlock: () -> Unit,
    onTestNotification: () -> Unit,
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
                text = stringResource(R.string.title_quick_test_category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
        }
    }
}
