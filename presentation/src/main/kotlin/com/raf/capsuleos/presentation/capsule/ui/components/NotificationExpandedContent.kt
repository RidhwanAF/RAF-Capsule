package com.raf.capsuleos.presentation.capsule.ui.components

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics
import kotlinx.coroutines.flow.drop
import java.nio.ByteBuffer

/**
 * State 3 expanded notification card displaying shared icon & title, body text,
 * quick Reply (with inline `RemoteInput` field), extracted app actions, Open, and Dismiss.
 */
@Composable
fun NotificationExpandedContent(
    modifier: Modifier = Modifier,
    notification: CapsuleContent.NotificationAlert,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
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
    val replyDraftState = remember(notification.key) { TextFieldState() }
    val replyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(replyDraftState) {
        snapshotFlow { replyDraftState.text.toString() }
            .drop(1)
            .collect { onUserInteraction() }
    }

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
        val clean = replyDraftState.text.toString().trim()
        if (target != null && clean.isNotEmpty()) {
            haptics.click()
            onTriggerAction(target.actionId, clean)
            replyDraftState.clearText()
            replyingAction = null
        }
    }

    val dismissAccessibilityLabel = stringResource(R.string.desc_dismiss_notification)

    with(sharedTransitionScope) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxSize()
                .semantics {
                    dismiss(label = dismissAccessibilityLabel) {
                        onDismissNotification()
                        true
                    }
                }
                .padding(
                    start = contentPaddingDp.dp,
                    end = contentPaddingDp.dp,
                    top = topClearanceDp.dp,
                    bottom = (contentPaddingDp * 0.8f).coerceAtLeast(10f).dp,
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
                        text = notification.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "capsule_title"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
                        ),
                    )
                }
            }

            if (notification.text.isNotBlank() || notification.attachmentImageBytes != null) {
                val bodyScrollState = rememberScrollState()
                val bodyContainerShape = RoundedCornerShape(16.dp)
                val attachmentSize = notification.attachmentImageBytes?.size ?: 0
                val currentSignature = "${notification.key}|${notification.text.hashCode()}|$attachmentSize"
                var userScrolledManually by remember(currentSignature) { mutableStateOf(false) }
                LaunchedEffect(bodyScrollState.isScrollInProgress) {
                    if (bodyScrollState.isScrollInProgress) {
                        userScrolledManually = true
                        onUserInteraction()
                    }
                }
                LaunchedEffect(currentSignature, bodyScrollState.maxValue) {
                    if (!userScrolledManually && bodyScrollState.maxValue > 0) {
                        bodyScrollState.scrollTo(bodyScrollState.maxValue)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 10.dp, bottom = 10.dp)
                        .clip(bodyContainerShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 0.75.dp,
                            color = Color.White.copy(alpha = 0.11f),
                            shape = bodyContainerShape,
                        ),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(bodyScrollState)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (notification.text.isNotBlank()) {
                            Text(
                                text = notification.text,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val attachmentBytes = notification.attachmentImageBytes
                        if (attachmentBytes != null) {
                            NotificationAttachmentPreview(
                                attachmentBytes = attachmentBytes,
                                contentDescription = stringResource(R.string.desc_notification_attachment),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
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
                        if (replyDraftState.text.isEmpty()) {
                            Text(
                                text = currentReplyAction.replyHint ?: stringResource(R.string.hint_quick_reply),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            state = replyDraftState,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            onKeyboardAction = { submitInlineReply() },
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

                    val canSendReply = replyDraftState.text.isNotBlank()
                    CapsuleExpressiveSurface(
                        onClick = submitInlineReply,
                        enabled = canSendReply,
                        restCornerDp = 14f,
                        pressedCornerDp = 7f,
                        backgroundColor = if (canSendReply) primary else primary.copy(alpha = 0.35f),
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
                NotificationActionsBar(
                    actions = notification.actions,
                    primaryColor = primary,
                    onActionClick = { action ->
                        haptics.click()
                        onUserInteraction()
                        if (action.supportsInlineReply) {
                            replyingAction = action
                        } else {
                            onTriggerAction(action.actionId, null)
                        }
                    },
                    onOpenNotification = {
                        haptics.click()
                        onOpenNotification()
                    },
                    onDismissNotification = {
                        haptics.click()
                        onDismissNotification()
                    },
                )
            }
        }
    }
}

/**
 * Renders an inline notification attachment (photo, sticker, or animated GIF/WebP) decoded via
 * Android's native `ImageDecoder`, preserving animation playback for `AnimatedImageDrawable`.
 */
@Composable
private fun NotificationAttachmentPreview(
    modifier: Modifier = Modifier,
    attachmentBytes: ByteArray,
    contentDescription: String,
) {
    val decodedDrawable = remember(attachmentBytes) {
        runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(attachmentBytes)))
        }.getOrNull()
    }
    val fallbackBitmap = remember(attachmentBytes, decodedDrawable) {
        when (decodedDrawable) {
            is AnimatedImageDrawable -> null
            is BitmapDrawable -> decodedDrawable.bitmap.asImageBitmap()
            else -> BitmapFactory.decodeByteArray(attachmentBytes, 0, attachmentBytes.size)?.asImageBitmap()
        }
    }

    if (decodedDrawable is AnimatedImageDrawable) {
        DisposableEffect(decodedDrawable) {
            decodedDrawable.start()
            onDispose {
                decodedDrawable.stop()
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                    }
                },
                update = { imageView ->
                    imageView.contentDescription = contentDescription
                    imageView.setImageDrawable(decodedDrawable)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 124.dp),
            )
        }
    } else if (fallbackBitmap != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            Image(
                bitmap = fallbackBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 128.dp),
            )
        }
    }
}

/**
 * Adaptive notification action layout:
 * - 0 actions: `[Open, Dismiss]`
 * - 1 action: `[Action 1, Open]`
 * - 2 actions: `[Action 1, Action 2, Open (icon-only)]`
 * - 3+ actions: full 3-column action rows, keeping `[Open]` either paired in the trailing partial row
 *   (using `[Action, Open]` or `[Action, Action, Open(icon)]`) or on its own full-width bottom row.
 */
@Composable
private fun NotificationActionsBar(
    modifier: Modifier = Modifier,
    actions: List<CapsuleContent.NotificationActionItem>,
    primaryColor: Color,
    onActionClick: (CapsuleContent.NotificationActionItem) -> Unit,
    onOpenNotification: () -> Unit,
    onDismissNotification: () -> Unit,
) {
    val openLabel = stringResource(R.string.action_open)
    if (actions.isEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            NotificationActionChip(
                label = openLabel,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                tint = primaryColor,
                containerColor = primaryColor.copy(alpha = 0.18f),
                onClick = onOpenNotification,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            NotificationActionChip(
                label = stringResource(R.string.action_dismiss),
                icon = Icons.Rounded.Close,
                tint = Color.White,
                containerColor = Color.White.copy(alpha = 0.12f),
                onClick = onDismissNotification,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        return
    }

    val actionRows = actions.chunked(3)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        actionRows.forEachIndexed { rowIndex, rowActions ->
            val isLastRow = rowIndex == actionRows.lastIndex
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                rowActions.forEach { action ->
                    NotificationActionItemChip(
                        action = action,
                        primaryColor = primaryColor,
                        onClick = { onActionClick(action) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }

                if (isLastRow && rowActions.size == 1) {
                    NotificationActionChip(
                        label = openLabel,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        tint = primaryColor,
                        containerColor = primaryColor.copy(alpha = 0.18f),
                        onClick = onOpenNotification,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                } else if (isLastRow && rowActions.size == 2) {
                    NotificationOpenIconChip(
                        contentDescription = openLabel,
                        tint = primaryColor,
                        containerColor = primaryColor.copy(alpha = 0.18f),
                        onClick = onOpenNotification,
                        modifier = Modifier
                            .width(40.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        if (actions.size % 3 == 0) {
            NotificationActionChip(
                label = openLabel,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                tint = primaryColor,
                containerColor = primaryColor.copy(alpha = 0.18f),
                onClick = onOpenNotification,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NotificationActionItemChip(
    modifier: Modifier = Modifier,
    action: CapsuleContent.NotificationActionItem,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    NotificationActionChip(
        label = action.label,
        icon = if (action.supportsInlineReply) Icons.AutoMirrored.Rounded.Reply else null,
        tint = if (action.supportsInlineReply) primaryColor else Color.White,
        containerColor = if (action.supportsInlineReply) {
            primaryColor.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.12f)
        },
        onClick = onClick,
        modifier = modifier,
    )
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
        modifier = modifier.heightIn(min = 34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
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

@Composable
private fun NotificationOpenIconChip(
    modifier: Modifier = Modifier,
    contentDescription: String,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
) {
    CapsuleExpressiveSurface(
        onClick = onClick,
        restCornerDp = 16f,
        pressedCornerDp = 8f,
        backgroundColor = containerColor,
        modifier = modifier.heightIn(min = 34.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 7.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
