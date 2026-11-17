package com.raf.capsuleos.presentation.setup.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes
import kotlin.math.roundToInt

/**
 * Category card for physical camera cutout alignment (Auto-Detect button, Left/Center/Right
 * anchor placement, Horizontal/Vertical offsets, and Ring Size calibration with `-` / `+` steps).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraPositionCard(
    modifier: Modifier = Modifier,
    preferences: CapsulePreferences,
    cutoutGeometry: CutoutGeometry,
    detectedPosition: CameraAnchorPosition,
    hasHardwareCutout: Boolean,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
    onAutoDetectClick: () -> Unit,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.title_camera_position_category),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val summaryText = if (hasHardwareCutout) {
                        stringResource(
                            R.string.msg_cutout_detected,
                            positionLabel(detectedPosition),
                            cutoutGeometry.holeWidthDp.roundToInt(),
                        )
                    } else {
                        stringResource(R.string.msg_no_cutout_detected)
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onAutoDetectClick()
                    },
                    shapes = capsuleExpressiveButtonShapes(restRadius = 18.dp, pressedRadius = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = stringResource(R.string.desc_auto_detect_camera),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_auto_detect))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.label_camera_anchor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CameraAnchorPosition.entries.forEach { anchor ->
                    val selected = preferences.cameraPosition == anchor
                    val containerColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "anchorBg_${anchor.name}",
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "anchorText_${anchor.name}",
                    )

                    CapsuleExpressiveSurface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onUpdatePreferences { it.copy(cameraPosition = anchor) }
                        },
                        restCornerDp = 16f,
                        pressedCornerDp = 8f,
                        pressedScale = 0.95f,
                        backgroundColor = containerColor,
                        border = if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                    ) {
                        Text(
                            text = positionLabel(anchor),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            PrecisionTuningSliderRow(
                label = stringResource(R.string.label_cutout_horizontal_offset),
                valueLabel = formatDpValue(preferences.horizontalOffsetDp, signed = true),
                value = preferences.horizontalOffsetDp,
                valueRange = -100f..100f,
                stepSize = 0.5f,
                onValueChange = { next ->
                    onUpdatePreferences { it.copy(horizontalOffsetDp = next) }
                },
            )

            PrecisionTuningSliderRow(
                label = stringResource(R.string.label_cutout_top_offset),
                valueLabel = formatDpValue(preferences.topOffsetAdjustDp, signed = true),
                value = preferences.topOffsetAdjustDp,
                valueRange = -16f..16f,
                stepSize = 0.5f,
                onValueChange = { next ->
                    onUpdatePreferences { it.copy(topOffsetAdjustDp = next) }
                },
            )

            PrecisionTuningSliderRow(
                label = stringResource(R.string.label_cutout_width),
                valueLabel = "+${formatDpValue(preferences.paddingWidthDp)}",
                value = preferences.paddingWidthDp,
                valueRange = 2f..28f,
                stepSize = 1f,
                onValueChange = { next ->
                    onUpdatePreferences { it.copy(paddingWidthDp = next) }
                },
            )
        }
    }
}

@Composable
private fun positionLabel(position: CameraAnchorPosition): String = when (position) {
    CameraAnchorPosition.LEFT -> stringResource(R.string.label_anchor_left)
    CameraAnchorPosition.CENTER -> stringResource(R.string.label_anchor_center)
    CameraAnchorPosition.RIGHT -> stringResource(R.string.label_anchor_right)
}
