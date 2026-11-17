package com.raf.capsuleos.presentation.capsule.ui.components

import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleSlider
import com.raf.capsuleos.presentation.common.MorphingCookieShape
import com.raf.capsuleos.presentation.common.capsuleExpressiveIconButtonShapes
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics
import com.raf.capsuleos.presentation.common.rememberCookieToCircleMorph
import com.raf.capsuleos.presentation.shader.MusicWaveShader
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * State 3 expanded media player card featuring a match-parent blurred album-art backdrop,
 * monotonic zero-blink pause wave deceleration, shared album icon flight, and M3 Expressive
 * transport controls with a continuously spinning 7-sided cookie play/pause button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaExpandedContent(
    modifier: Modifier = Modifier,
    media: CapsuleContent.Media,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    timeSeconds: Float,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenMediaApp: () -> Unit,
    onUserInteraction: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val haptics = rememberCapsuleHaptics()
    val primaryColor = Color(media.primaryColorArgb)
    val secondaryColor = Color(media.secondaryColorArgb)

    val waveShader = remember { MusicWaveShader.create() }
    val blurEffect = remember {
        RenderEffect.createBlurEffect(56f, 56f, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }
    val artworkBitmap = remember(media.artworkBytes) {
        media.artworkBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    val durationMs = media.durationMs.coerceAtLeast(1L)
    val remoteProgress = (media.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    LaunchedEffect(remoteProgress, isScrubbing) {
        if (!isScrubbing) {
            scrubProgress = remoteProgress
        }
    }

    // Monotonic phase & rotation integration so pausing decelerates smoothly with zero blink/snap
    val waveActivity by animateFloatAsState(
        targetValue = if (media.isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "mediaWaveActivity",
    )
    val waveVelocity by animateFloatAsState(
        targetValue = if (media.isPlaying) 2.8f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "mediaWaveVelocity",
    )
    val cookieAngularSpeed by animateFloatAsState(
        targetValue = if (media.isPlaying) 26f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "mediaCookieSpinSpeed",
    )
    var wavePhase by remember { mutableFloatStateOf(timeSeconds) }
    var cookieRotationDeg by remember { mutableFloatStateOf(0f) }

    val isMotionNeeded = media.isPlaying || waveVelocity > 0.002f || cookieAngularSpeed > 0.02f
    LaunchedEffect(isMotionNeeded) {
        if (!isMotionNeeded) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    val dtSec = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    if (waveVelocity > 0.001f) {
                        wavePhase += dtSec * waveVelocity
                    }
                    if (cookieAngularSpeed > 0.01f) {
                        cookieRotationDeg = (cookieRotationDeg + dtSec * cookieAngularSpeed) % 360f
                    }
                }
                lastNanos = nowNanos
            }
        }
    }

    val cookieMorph = rememberCookieToCircleMorph()
    val playPauseInteraction = remember { MutableInteractionSource() }
    val isPlayPausePressed by playPauseInteraction.collectIsPressedAsState()
    val cookiePressMorph by animateFloatAsState(
        targetValue = if (isPlayPausePressed) 0.56f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 880f),
        label = "cookiePressMorph",
    )
    val cookiePressScale by animateFloatAsState(
        targetValue = if (isPlayPausePressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 880f),
        label = "cookiePressScale",
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (artworkBitmap != null) {
            Image(
                bitmap = artworkBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = blurEffect
                        alpha = 0.48f
                    },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = blurEffect
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryColor.copy(alpha = 0.25f),
                                secondaryColor.copy(alpha = 0.35f),
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.08f),
                        ),
                    ),
                )
                .drawBehind {
                    MusicWaveShader.updateUniforms(
                        shader = waveShader,
                        widthPx = size.width,
                        heightPx = size.height,
                        timeSeconds = wavePhase,
                        waveActivity = waveActivity,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                    )
                    drawRect(brush = ShaderBrush(waveShader))
                },
        )

        with(sharedTransitionScope) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = contentPaddingDp.dp,
                        end = contentPaddingDp.dp,
                        top = topClearanceDp.dp,
                        bottom = (contentPaddingDp * 0.8f).coerceAtLeast(10f).dp,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            haptics.click()
                            onOpenMediaApp()
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "capsule_icon"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
                            )
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryColor.copy(alpha = 0.24f)),
                    ) {
                        if (artworkBitmap != null) {
                            Image(
                                bitmap = artworkBitmap,
                                contentDescription = stringResource(R.string.desc_album_art),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = stringResource(R.string.desc_album_art),
                                tint = primaryColor,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (media.artist.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = media.artist,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    CapsuleSlider(
                        value = scrubProgress,
                        onValueChange = { next ->
                            isScrubbing = true
                            scrubProgress = next
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            onUserInteraction()
                            onSeekTo((scrubProgress * durationMs).toLong())
                        },
                        onInteraction = onUserInteraction,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val displayedMs = (scrubProgress * durationMs).toLong()
                        Text(
                            text = formatDuration(displayedMs),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                        )
                        Text(
                            text = formatDuration(media.durationMs),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val expressiveTransportShapes = capsuleExpressiveIconButtonShapes(
                        restRadius = 22.dp,
                        pressedRadius = 10.dp,
                    )
                    val transportColors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    )

                    FilledTonalIconButton(
                        onClick = {
                            haptics.click()
                            onUserInteraction()
                            onSkipPrevious()
                        },
                        shapes = expressiveTransportShapes,
                        colors = transportColors,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.desc_skip_previous),
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    // M3 Expressive rotating 7-sided cookie Play/Pause button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clickable(
                                interactionSource = playPauseInteraction,
                                indication = null,
                                onClick = {
                                    haptics.click()
                                    onUserInteraction()
                                    onTogglePlayPause()
                                },
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    rotationZ = cookieRotationDeg
                                    scaleX = cookiePressScale
                                    scaleY = cookiePressScale
                                }
                                .clip(MorphingCookieShape(morph = cookieMorph, progress = cookiePressMorph))
                                .background(Color.White),
                        )
                        AnimatedContent(
                            targetState = media.isPlaying,
                            transitionSpec = {
                                (fadeIn(tween(170)) + scaleIn(initialScale = 0.72f, animationSpec = tween(170))) togetherWith
                                    (fadeOut(tween(110)) + scaleOut(targetScale = 0.72f, animationSpec = tween(110)))
                            },
                            label = "mediaPlayPauseGlyph",
                            modifier = Modifier.graphicsLayer {
                                scaleX = cookiePressScale
                                scaleY = cookiePressScale
                            },
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.desc_play_pause),
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = {
                            haptics.click()
                            onUserInteraction()
                            onSkipNext()
                        },
                        shapes = expressiveTransportShapes,
                        colors = transportColors,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.desc_skip_next),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
