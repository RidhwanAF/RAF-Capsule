package com.raf.capsuleos.presentation.capsule.ui.components

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
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Watch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.BluetoothDeviceCategory
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface

/**
 * State 3 card presenting connected Bluetooth device identity, reported battery percentage,
 * and a shortcut into system Bluetooth settings.
 */
@Composable
fun BluetoothExpandedContent(
    modifier: Modifier = Modifier,
    device: CapsuleContent.BluetoothDevice,
    topClearanceDp: Float,
    isLandscape: Boolean = false,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    onOpenBluetoothSettings: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentTint = device.batteryPercent?.let { pct ->
        capsuleBatteryAccentColor(batteryPercent = pct, isCharging = pct >= 60, primaryColor = primaryColor)
    } ?: primaryColor
    val subText = device.batteryPercent?.let { pct ->
        "${stringResource(R.string.label_bluetooth_connected)} • ${stringResource(R.string.label_bluetooth_battery, pct)}"
    } ?: stringResource(R.string.label_bluetooth_connected)

    if (isLandscape) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = contentPaddingDp.dp,
                    end = contentPaddingDp.dp,
                    top = topClearanceDp.dp,
                    bottom = contentPaddingDp.coerceAtLeast(16f).dp,
                ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(accentTint.copy(alpha = 0.2f)),
            ) {
                Icon(
                    imageVector = bluetoothIconForCategory(device.category),
                    contentDescription = stringResource(R.string.desc_bluetooth_icon),
                    tint = accentTint,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = device.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subText,
                color = accentTint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(24.dp))
            CapsuleExpressiveSurface(
                enabled = interactionsEnabled,
                onClick = onOpenBluetoothSettings,
                restCornerDp = 22f,
                pressedCornerDp = 10f,
                backgroundColor = primaryColor.copy(alpha = 0.22f),
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .height(44.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_open),
                    color = primaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = contentPaddingDp.dp,
                    end = contentPaddingDp.dp,
                    top = topClearanceDp.dp,
                    bottom = contentPaddingDp.coerceAtLeast(14f).dp,
                ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentTint.copy(alpha = 0.2f)),
            ) {
                Icon(
                    imageVector = bluetoothIconForCategory(device.category),
                    contentDescription = stringResource(R.string.desc_bluetooth_icon),
                    tint = accentTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subText,
                    color = accentTint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            CapsuleExpressiveSurface(
                enabled = interactionsEnabled,
                onClick = onOpenBluetoothSettings,
                restCornerDp = 18f,
                pressedCornerDp = 9f,
                backgroundColor = primaryColor.copy(alpha = 0.22f),
                modifier = Modifier
                    .height(36.dp)
                    .width(92.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_open),
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun bluetoothIconForCategory(category: BluetoothDeviceCategory): ImageVector = when (category) {
    BluetoothDeviceCategory.HEADPHONES -> Icons.Rounded.Headphones
    BluetoothDeviceCategory.SPEAKER -> Icons.Rounded.Speaker
    BluetoothDeviceCategory.WATCH -> Icons.Rounded.Watch
    BluetoothDeviceCategory.CAR -> Icons.Rounded.DirectionsCar
    BluetoothDeviceCategory.COMPUTER,
    BluetoothDeviceCategory.PHONE,
    BluetoothDeviceCategory.OTHER,
    -> Icons.Rounded.BluetoothConnected
}
