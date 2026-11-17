package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LockOpen
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
import com.raf.capsuleos.domain.model.UnlockMethod
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics

/**
 * State 3 expanded alarm card displaying next alarm schedule and quick Clock shortcut.
 *
 * @param modifier Applied to the root alarm card.
 * @param alarm Current alarm state.
 * @param onOpenClock Launches the system Clock app.
 */
@Composable
fun AlarmExpandedContent(
    modifier: Modifier = Modifier,
    alarm: CapsuleContent.Alarm,
    topClearanceDp: Float,
    onOpenClock: () -> Unit,
) {
    val haptics = rememberCapsuleHaptics()
    val coral = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = topClearanceDp.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(coral.copy(alpha = 0.2f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = stringResource(R.string.desc_alarm_icon),
                    tint = coral,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.label_next_alarm),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                )
                Text(
                    text = if (alarm.isSet) alarm.formattedTime else stringResource(R.string.label_no_alarm),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        CapsuleExpressiveSurface(
            onClick = {
                haptics.click()
                onOpenClock()
            },
            restCornerDp = 22f,
            pressedCornerDp = 10f,
            backgroundColor = coral,
        ) {
            Text(
                text = stringResource(R.string.action_open_clock),
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Icon + localized marquee label mapped from [UnlockMethod] for shared State 2/3 rendering. */
internal data class UnlockVisuals(
    val icon: ImageVector,
    val label: String,
)

/** Maps [UnlockMethod] consistently across compact pill and expanded unlock cards. */
@Composable
internal fun resolveUnlockVisuals(unlockMethod: UnlockMethod): UnlockVisuals = when (unlockMethod) {
    UnlockMethod.FINGERPRINT -> UnlockVisuals(
        icon = Icons.Rounded.Fingerprint,
        label = stringResource(R.string.label_unlocked_fingerprint),
    )
    UnlockMethod.FACE -> UnlockVisuals(
        icon = Icons.Rounded.Face,
        label = stringResource(R.string.label_unlocked_face),
    )
    UnlockMethod.GENERIC -> UnlockVisuals(
        icon = Icons.Rounded.LockOpen,
        label = stringResource(R.string.label_unlocked),
    )
}

/**
 * State 3 expanded unlock summary card showing biometric/unlock method and optional next alarm.
 *
 * @param modifier Applied to the root unlock card.
 * @param unlock Unlock status payload.
 * @param topClearanceDp Top camera cutout clearance in dp.
 * @param sharedTransitionScope Scope coordinating shared-element transitions from State 2.
 * @param animatedVisibilityScope Active visibility scope for State 3.
 */
@Composable
fun UnlockExpandedContent(
    modifier: Modifier = Modifier,
    unlock: CapsuleContent.Unlock,
    topClearanceDp: Float,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val mint = MaterialTheme.colorScheme.primary
    val visuals = resolveUnlockVisuals(unlock.unlockMethod)
    val baseSubtitle = stringResource(
        if (unlock.unlockMethod == UnlockMethod.GENERIC) {
            R.string.label_unlock_status_ready
        } else {
            R.string.label_unlock_biometric_verified
        },
    )
    val nextAlarmPrefix = stringResource(R.string.label_next_alarm)
    val statusDetail = if (unlock.nextAlarmLabel != null) {
        "$baseSubtitle • $nextAlarmPrefix ${unlock.nextAlarmLabel}"
    } else {
        baseSubtitle
    }

    with(sharedTransitionScope) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = topClearanceDp.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "capsule_icon"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(mint.copy(alpha = 0.2f)),
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = stringResource(R.string.desc_unlock_icon),
                        tint = mint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = visuals.label,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 350,
                            repeatDelayMillis = 900,
                            velocity = 34.dp,
                        ),
                    )
                    Text(
                        text = statusDetail,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
