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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics

/**
 * State 3 expanded notification card displaying shared icon & title, body text,
 * quick Reply (with inline `RemoteInput` field), extracted app actions, Open, and Dismiss.
 */
@Composable
fun NotificationExpandedContent(
    modifier: Modifier = Modifier,
    notification: CapsuleContent.NotificationAlert,
    topClearanceDp: Float,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onUserInteraction: () -> Unit,
    onOpenNotification: () -> Unit,
    onTriggerAction: (actionId: Int, replyText: String?) -> Unit,
    onDismissNotification: () -> Unit,
    onReplyFocusChanged: (Boolean) -> Unit,
) {
    val haptics = rememberCapsuleHaptics()
    val primary = MaterialTheme.colorScheme.primary
    val iconBitmap = remember(notification.iconBytes) {
        notification.iconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    var replyingAction by remember(notification.key) {
        mutableStateOf<CapsuleContent.NotificationActionItem?>(null)
    }
    var replyDraft by remember(notification.key) { mutableStateOf("") }
    val replyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(replyingAction) {
        val active = replyingAction != null
        onReplyFocusChanged(active)
        if (active) {
            runCatching { replyFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onReplyFocusChanged(false)
        }
    }

    val submitInlineReply = {
        val target = replyingAction
        val clean = replyDraft.trim()
        if (target != null && clean.isNotEmpty()) {
            haptics.click()
            onTriggerAction(target.actionId, clean)
            replyDraft = ""
            replyingAction = null
        }
    }

    with(sharedTransitionScope) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = topClearanceDp.dp, bottom = 14.dp),
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.2f)),
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = notification.appName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = notification.appName,
                                tint = primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notification.appName,
                            color = primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = notification.title,
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
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                CapsuleExpressiveSurface(
                    onClick = {
                        haptics.click()
                        onDismissNotification()
                    },
                    restCornerDp = 16f,
                    pressedCornerDp = 8f,
                    backgroundColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.desc_dismiss_notification),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            val currentReplyAction = replyingAction
            if (currentReplyAction != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (replyDraft.isEmpty()) {
                            Text(
                                text = currentReplyAction.replyHint ?: stringResource(R.string.hint_quick_reply),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = replyDraft,
                            onValueChange = { next ->
                                onUserInteraction()
                                replyDraft = next
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitInlineReply() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(replyFocusRequester),
                        )
                    }

                    CapsuleExpressiveSurface(
                        onClick = {
                            haptics.click()
                            onUserInteraction()
                            replyingAction = null
                        },
                        restCornerDp = 14f,
                        pressedCornerDp = 7f,
                        backgroundColor = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    CapsuleExpressiveSurface(
                        onClick = submitInlineReply,
                        enabled = replyDraft.isNotBlank(),
                        restCornerDp = 14f,
                        pressedCornerDp = 7f,
                        backgroundColor = if (replyDraft.isNotBlank()) primary else primary.copy(alpha = 0.35f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.desc_send_reply),
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    notification.actions.take(2).forEach { action ->
                        NotificationActionChip(
                            label = action.label,
                            icon = if (action.supportsInlineReply) Icons.AutoMirrored.Rounded.Reply else null,
                            tint = if (action.supportsInlineReply) primary else Color.White,
                            containerColor = if (action.supportsInlineReply) {
                                primary.copy(alpha = 0.22f)
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                            onClick = {
                                haptics.click()
                                onUserInteraction()
                                if (action.supportsInlineReply) {
                                    replyingAction = action
                                } else {
                                    onTriggerAction(action.actionId, null)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    NotificationActionChip(
                        label = stringResource(R.string.action_open),
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        tint = primary,
                        containerColor = primary.copy(alpha = 0.18f),
                        onClick = {
                            haptics.click()
                            onOpenNotification()
                        },
                        modifier = Modifier.weight(1f),
                    )

                    if (notification.actions.isEmpty()) {
                        NotificationActionChip(
                            label = stringResource(R.string.action_dismiss),
                            icon = Icons.Rounded.Close,
                            tint = Color.White,
                            containerColor = Color.White.copy(alpha = 0.12f),
                            onClick = {
                                haptics.click()
                                onDismissNotification()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationActionChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector?,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
) {
    CapsuleExpressiveSurface(
        onClick = onClick,
        restCornerDp = 16f,
        pressedCornerDp = 8f,
        backgroundColor = containerColor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
