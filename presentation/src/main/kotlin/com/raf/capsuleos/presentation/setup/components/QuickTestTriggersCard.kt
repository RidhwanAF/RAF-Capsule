package com.raf.capsuleos.presentation.setup.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes

/**
 * Category card housing synthetic live-overlay trigger actions when the system overlay service
 * is running, flowing cleanly into 2 columns on compact widths and wider rows in landscape.
 */
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
    var expanded by rememberSaveable { mutableStateOf(true) }

    ExpandableCategoryCard(
        collapsible = true,
        expanded = expanded,
        onToggleExpand = { expanded = !expanded },
        header = {
            ExpandableCategoryHeader(
                icon = Icons.Rounded.Bolt,
                title = stringResource(R.string.title_quick_test_category),
                subtitle = stringResource(R.string.msg_quick_test_subtitle),
                expanded = expanded,
                onToggleExpand = { expanded = !expanded },
            )
        },
        modifier = modifier,
    ) {
        AdaptiveSubItemFlowRow(
            minItemWidth = 124.dp,
            horizontalSpacing = 8.dp,
            verticalSpacing = 8.dp,
            maxColumns = 4,
        ) { itemMod ->
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTestVolume()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                enabled = isServiceRunning,
                modifier = itemMod,
            ) {
                Text(
                    text = stringResource(R.string.action_test_volume),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTestAlarm()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                enabled = isServiceRunning,
                modifier = itemMod,
            ) {
                Text(
                    text = stringResource(R.string.action_test_alarm),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTestUnlock()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                enabled = isServiceRunning,
                modifier = itemMod,
            ) {
                Text(
                    text = stringResource(R.string.action_test_unlock),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTestNotification()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 20.dp, pressedRadius = 10.dp),
                enabled = isServiceRunning,
                modifier = itemMod,
            ) {
                Text(
                    text = stringResource(R.string.action_test_notification),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
        }
    }
}
