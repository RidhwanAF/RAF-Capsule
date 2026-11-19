package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface

/**
 * State 3 card presenting Mobile Hotspot status and a quick shortcut to system tethering settings.
 */
@Composable
fun HotspotExpandedContent(
    modifier: Modifier = Modifier,
    hotspot: CapsuleContent.Hotspot,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    onOpenHotspotSettings: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

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
                .background(primaryColor.copy(alpha = 0.22f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = stringResource(R.string.desc_hotspot_icon),
                tint = primaryColor,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hotspot.ssid?.takeIf { it.isNotBlank() } ?: stringResource(R.string.label_hotspot),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (hotspot.isEnabled) {
                    if (hotspot.connectedClients > 0) {
                        "${stringResource(R.string.label_hotspot_active)} • ${
                            pluralStringResource(
                                R.plurals.label_hotspot_clients,
                                hotspot.connectedClients,
                                hotspot.connectedClients,
                            )
                        }"
                    } else {
                        stringResource(R.string.label_hotspot_active)
                    }
                } else {
                    stringResource(R.string.label_hotspot_inactive)
                },
                color = if (hotspot.isEnabled) primaryColor else Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        CapsuleExpressiveSurface(
            enabled = interactionsEnabled,
            onClick = onOpenHotspotSettings,
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
