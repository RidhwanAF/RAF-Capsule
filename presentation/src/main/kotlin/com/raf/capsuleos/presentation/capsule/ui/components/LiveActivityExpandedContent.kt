package com.raf.capsuleos.presentation.capsule.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Ticks [liveActivity] in real time aligned to exact 1-second chronometer boundaries when
 * [CapsuleContent.LiveActivity.showChronometer] is enabled, or returns its static status text.
 */
@Composable
internal fun rememberLiveActivityStatusText(
    liveActivity: CapsuleContent.LiveActivity,
): String {
    if (!liveActivity.showChronometer || liveActivity.chronometerBaseEpochMs <= 0L) {
        return liveActivity.statusText
    }
    var nowEpochMs by remember(
        liveActivity.key,
        liveActivity.chronometerBaseEpochMs,
        liveActivity.isCountdown,
    ) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(
        liveActivity.key,
        liveActivity.chronometerBaseEpochMs,
        liveActivity.isCountdown,
    ) {
        while (true) {
            val now = System.currentTimeMillis()
            nowEpochMs = now
            val nextTickDelayMs = if (liveActivity.isCountdown) {
                val rem = (liveActivity.chronometerBaseEpochMs - now).coerceAtLeast(0L)
                if (rem <= 0L) 1_000L else (rem % 1_000L).let { if (it == 0L) 1_000L else it }.coerceIn(100L, 1_000L)
            } else {
                val elapsed = (now - liveActivity.chronometerBaseEpochMs).coerceAtLeast(0L)
                (1_000L - (elapsed % 1_000L)).coerceIn(100L, 1_000L)
            }
            delay(nextTickDelayMs.milliseconds)
        }
    }
    return liveActivity.resolveDisplayStatus(nowEpochMs)
}

/**
 * State 3 expanded Live Activity card showing its ongoing app icon, title, live status readout,
 * optional detail subtitle, and quick button to open the source activity.
 */
@Composable
fun LiveActivityExpandedContent(
    modifier: Modifier = Modifier,
    liveActivity: CapsuleContent.LiveActivity,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onUserInteraction: () -> Unit,
    onOpenLiveActivityApp: () -> Unit,
) {
    val haptics = rememberCapsuleHaptics()
    val primary = MaterialTheme.colorScheme.primary
    val liveStatusText = rememberLiveActivityStatusText(liveActivity)
    val iconBitmap = remember(liveActivity.iconBytes) {
        liveActivity.iconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "capsule_icon"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
                            )
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.22f)),
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = liveActivity.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = liveActivity.title,
                                tint = primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_live_activity),
                            color = primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = liveActivity.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = "capsule_title"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
                            ),
                        )
                        if (liveActivity.subtitle.isNotBlank()) {
                            Text(
                                text = liveActivity.subtitle,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(primary.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = liveStatusText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            if (!liveActivity.packageName.isNullOrBlank()) {
                CapsuleExpressiveSurface(
                    onClick = {
                        haptics.click()
                        onUserInteraction()
                        onOpenLiveActivityApp()
                    },
                    restCornerDp = 16f,
                    pressedCornerDp = 8f,
                    backgroundColor = primary.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_open),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
