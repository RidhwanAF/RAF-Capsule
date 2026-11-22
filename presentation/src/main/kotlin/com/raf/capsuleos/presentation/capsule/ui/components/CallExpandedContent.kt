package com.raf.capsuleos.presentation.capsule.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface

/**
 * Interactive incoming/ongoing call card with Answer and Decline/End Material 3 Expressive buttons
 * and tap-to-open dialer support.
 */
@Composable
fun CallExpandedContent(
    modifier: Modifier = Modifier,
    call: CapsuleContent.CallAlert,
    topClearanceDp: Float,
    isLandscape: Boolean = false,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    onOpenCallApp: () -> Unit,
    onAnswerCall: () -> Unit,
    onDeclineCall: () -> Unit,
) {
    val ringTransition = rememberInfiniteTransition(label = "callRing")
    val ringAlpha by ringTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (call.isIncoming) 0.72f else 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "callHalo",
    )
    val appIconBitmap = remember(call.appIconBytes) {
        call.appIconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val answerGreen = Color(0xFF2E9E5B)
    val declineRed = Color(0xFFD93B3B)
    val callSubText = call.callStatus.ifBlank {
        if (call.isIncoming) {
            stringResource(R.string.label_incoming_call)
        } else {
            stringResource(R.string.label_ongoing_call)
        }
    }

    Column(
        horizontalAlignment = if (isLandscape) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = interactionsEnabled, onClick = onOpenCallApp)
            .padding(
                start = contentPaddingDp.dp,
                end = contentPaddingDp.dp,
                top = topClearanceDp.dp,
                bottom = contentPaddingDp.coerceAtLeast(14f).dp,
            ),
    ) {
        if (isLandscape) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(answerGreen.copy(alpha = ringAlpha * 0.45f))
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = call.appName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.desc_call_icon),
                        tint = answerGreen,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = call.callerName,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = callSubText,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(answerGreen.copy(alpha = ringAlpha * 0.45f))
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = call.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = stringResource(R.string.desc_call_icon),
                            tint = answerGreen,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = call.callerName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = callSubText,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CapsuleExpressiveSurface(
                enabled = interactionsEnabled,
                onClick = onDeclineCall,
                restCornerDp = 22f,
                pressedCornerDp = 10f,
                backgroundColor = declineRed,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (call.isIncoming || call.hasAnswerAction) {
                                R.string.action_decline_call
                            } else {
                                R.string.action_end_call
                            },
                        ),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            if (call.isIncoming || call.hasAnswerAction) {
                CapsuleExpressiveSurface(
                    enabled = interactionsEnabled,
                    onClick = onAnswerCall,
                    restCornerDp = 22f,
                    pressedCornerDp = 10f,
                    backgroundColor = answerGreen,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_answer_call),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                CapsuleExpressiveSurface(
                    enabled = interactionsEnabled,
                    onClick = onOpenCallApp,
                    restCornerDp = 22f,
                    pressedCornerDp = 10f,
                    backgroundColor = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_open),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
