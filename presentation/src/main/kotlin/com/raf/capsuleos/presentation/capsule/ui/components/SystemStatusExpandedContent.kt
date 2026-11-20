package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SystemStatusKind
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics

/**
 * State 3 expanded card for active system modes surfaced from the left satellite bubble
 * (Airplane Mode, Do Not Disturb, Battery Saver, VPN, Silent/Vibrate, Data Saver).
 */
@Composable
fun SystemStatusExpandedContent(
    modifier: Modifier = Modifier,
    status: CapsuleContent.SystemStatus,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onUserInteraction: () -> Unit,
    onOpenSystemStatusSettings: (SystemStatusKind) -> Unit,
    onSetRingerMode: (RingerModeState) -> Unit,
) {
    val haptics = rememberCapsuleHaptics()
    val primary = MaterialTheme.colorScheme.primary
    val title = systemStatusTitle(status.kind)
    val icon = systemStatusIcon(status)
    val activeDetail = status.detailLabel.ifBlank { stringResource(R.string.label_status_active) }

    with(sharedTransitionScope) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = contentPaddingDp.dp,
                    end = contentPaddingDp.dp,
                    top = topClearanceDp.dp,
                    bottom = (contentPaddingDp * 0.85f).coerceAtLeast(12f).dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "capsule_icon"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 410f) },
                        )
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.22f)),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = primary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = activeDetail,
                        color = primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (status.kind == SystemStatusKind.RINGER_MUTED) {
                        CapsuleExpressiveSurface(
                            enabled = interactionsEnabled,
                            onClick = {
                                haptics.click()
                                onUserInteraction()
                                onSetRingerMode(RingerModeState.NORMAL)
                            },
                            restCornerDp = 16f,
                            pressedCornerDp = 8f,
                            backgroundColor = primary.copy(alpha = 0.24f),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_restore_ring),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    CapsuleExpressiveSurface(
                        enabled = interactionsEnabled,
                        onClick = {
                            haptics.click()
                            onUserInteraction()
                            onOpenSystemStatusSettings(status.kind)
                        },
                        restCornerDp = 16f,
                        pressedCornerDp = 8f,
                        backgroundColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_open_settings),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun systemStatusTitle(kind: SystemStatusKind): String = when (kind) {
    SystemStatusKind.AIRPLANE_MODE -> stringResource(R.string.label_status_airplane)
    SystemStatusKind.DO_NOT_DISTURB -> stringResource(R.string.label_status_dnd)
    SystemStatusKind.BATTERY_SAVER -> stringResource(R.string.label_status_battery_saver)
    SystemStatusKind.VPN -> stringResource(R.string.label_status_vpn)
    SystemStatusKind.RINGER_MUTED -> stringResource(R.string.label_status_ringer_muted)
    SystemStatusKind.DATA_SAVER -> stringResource(R.string.label_status_data_saver)
}

private fun systemStatusIcon(status: CapsuleContent.SystemStatus): ImageVector = when (status.kind) {
    SystemStatusKind.AIRPLANE_MODE -> Icons.Rounded.AirplanemodeActive
    SystemStatusKind.DO_NOT_DISTURB -> Icons.Rounded.DoNotDisturbOn
    SystemStatusKind.BATTERY_SAVER -> Icons.Rounded.BatterySaver
    SystemStatusKind.VPN -> Icons.Rounded.VpnKey
    SystemStatusKind.RINGER_MUTED -> if (status.detailLabel.equals("Vibrate", ignoreCase = true)) {
        Icons.Rounded.Vibration
    } else {
        Icons.Rounded.NotificationsOff
    }
    SystemStatusKind.DATA_SAVER -> Icons.Rounded.DataSaverOn
}
