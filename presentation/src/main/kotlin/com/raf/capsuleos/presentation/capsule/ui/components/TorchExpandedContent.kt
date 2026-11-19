package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.CapsuleSlider
import kotlin.math.roundToInt

/**
 * Dedicated State 3 square flashlight card sharing icon & label elements with Now Brief,
 * allowing smooth percentage tuning and delayed auto-collapse after turning off.
 *
 * @param modifier Applied to the root square container.
 * @param torch Current hardware torch state.
 * @param topClearanceDp Top padding to clear the physical camera hole.
 * @param sharedTransitionScope Scope coordinating shared-element continuity from Now Brief / Pill.
 * @param animatedVisibilityScope Active transition scope for State 3.
 * @param onUserInteraction Resets auto-collapse timer while sliding or tapping.
 * @param onToggleTorch Turns the flashlight on or off while keeping card open until auto-collapse.
 * @param onSetTorchStrength Updates torch brightness level (`1..maxStrengthLevel`).
 */
@Composable
fun TorchExpandedContent(
    modifier: Modifier = Modifier,
    torch: CapsuleContent.Torch,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    interactionsEnabled: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onUserInteraction: () -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onSetTorchStrength: (Int) -> Unit,
) {
    val localHapticFeedback = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val iconBoxBg by animateColorAsState(
        targetValue = if (torch.isEnabled) primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 240),
        label = "torchIconBoxBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (torch.isEnabled) primary else Color.White.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 240),
        label = "torchIconTint",
    )
    val statusColor by animateColorAsState(
        targetValue = if (torch.isEnabled) primary else Color.White.copy(alpha = 0.6f),
        animationSpec = tween(durationMillis = 240),
        label = "torchStatusColor",
    )
    val actionBtnBg by animateColorAsState(
        targetValue = if (torch.isEnabled) primary else Color.White.copy(alpha = 0.16f),
        animationSpec = tween(durationMillis = 260),
        label = "torchActionBg",
    )
    val actionTextColor by animateColorAsState(
        targetValue = if (torch.isEnabled) Color.Black else Color.White,
        animationSpec = tween(durationMillis = 260),
        label = "torchActionTextColor",
    )
    val sliderThumbColor by animateColorAsState(
        targetValue = if (torch.isEnabled) primary else Color.White.copy(alpha = 0.65f),
        animationSpec = tween(durationMillis = 240),
        label = "torchThumb",
    )
    val sliderTrackColor by animateColorAsState(
        targetValue = if (torch.isEnabled) primary else Color.White.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = 240),
        label = "torchTrack",
    )

    with(sharedTransitionScope) {
        val sidePadDp = (contentPaddingDp - 2f).coerceAtLeast(8f)
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = sidePadDp.dp,
                    end = sidePadDp.dp,
                    top = topClearanceDp.dp,
                    bottom = (contentPaddingDp * 0.8f).coerceAtLeast(10f).dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "torch_icon_box"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconBoxBg),
                ) {
                    AnimatedContent(
                        targetState = torch.isEnabled,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.72f, animationSpec = tween(200))) togetherWith
                                (fadeOut(tween(140)) + scaleOut(targetScale = 0.72f, animationSpec = tween(140)))
                        },
                        label = "torchIconState",
                    ) { enabled ->
                        Icon(
                            imageVector = if (enabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = stringResource(R.string.desc_torch_icon),
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.label_torch),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "torch_label"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                    )
                    val statusLabel = when {
                        !torch.isEnabled -> "OFF"
                        torch.maxStrengthLevel > 1 -> "${stringResource(R.string.label_torch_brightness)} ${torch.brightnessPercent}%"
                        else -> "ON"
                    }
                    AnimatedContent(
                        targetState = statusLabel to torch.isEnabled,
                        contentKey = { (_, enabled) -> enabled },
                        transitionSpec = {
                            (fadeIn(tween(210)) + scaleIn(initialScale = 0.90f, animationSpec = tween(210))) togetherWith
                                (fadeOut(tween(150)) + scaleOut(targetScale = 0.90f, animationSpec = tween(150)))
                        },
                        label = "torchStatusAnim",
                    ) { (textValue, _) ->
                        Text(
                            text = textValue,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (torch.maxStrengthLevel > 1) {
                CapsuleSlider(
                    value = torch.strengthLevel.toFloat(),
                    onValueChange = { next ->
                        val target = next.roundToInt().coerceIn(1, torch.maxStrengthLevel)
                        if (target != torch.strengthLevel || !torch.isEnabled) {
                            onSetTorchStrength(target)
                        }
                    },
                    valueRange = 1f..torch.maxStrengthLevel.toFloat(),
                    steps = 0,
                    enabled = interactionsEnabled,
                    onInteraction = onUserInteraction,
                    colors = SliderDefaults.colors(
                        thumbColor = sliderThumbColor,
                        activeTrackColor = sliderTrackColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )
            }

            CapsuleExpressiveSurface(
                enabled = interactionsEnabled,
                onClick = {
                    localHapticFeedback.performHapticFeedback(
                        if (torch.isEnabled) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    onUserInteraction()
                    onToggleTorch(!torch.isEnabled)
                },
                restCornerDp = 24f,
                pressedCornerDp = 10f,
                backgroundColor = actionBtnBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(38.dp),
            ) {
                AnimatedContent(
                    targetState = torch.isEnabled,
                    transitionSpec = {
                        (fadeIn(tween(210)) + scaleIn(initialScale = 0.90f, animationSpec = tween(210))) togetherWith
                            (fadeOut(tween(150)) + scaleOut(targetScale = 0.90f, animationSpec = tween(150)))
                    },
                    label = "torchActionLabel",
                ) { enabled ->
                    Text(
                        text = stringResource(if (enabled) R.string.action_turn_off else R.string.action_turn_on),
                        color = actionTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
