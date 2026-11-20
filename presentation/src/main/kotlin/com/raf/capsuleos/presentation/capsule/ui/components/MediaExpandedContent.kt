package com.raf.capsuleos.presentation.capsule.ui.components

import android.graphics.Matrix
import android.graphics.SweepGradient
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.ExpandedMediaGlowEffect
import com.raf.capsuleos.domain.model.LrcLyricsParser
import com.raf.capsuleos.domain.model.LyricLine
import com.raf.capsuleos.domain.model.MediaAnimationColorMode
import com.raf.capsuleos.domain.model.MediaOutputKind
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.CapsuleExpressiveSurface
import com.raf.capsuleos.presentation.common.CapsuleSlider
import com.raf.capsuleos.presentation.common.MorphingCookieShape
import com.raf.capsuleos.presentation.common.capsuleExpressiveIconButtonShapes
import com.raf.capsuleos.presentation.common.rememberCachedBlurredImageBitmap
import com.raf.capsuleos.presentation.common.rememberCachedImageBitmap
import com.raf.capsuleos.presentation.common.rememberCapsuleHaptics
import com.raf.capsuleos.presentation.common.rememberCookieToCircleMorph
import com.raf.capsuleos.presentation.shader.MusicWaveShader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-bleed cropped & blurred media backdrop with crossfade, horizontal parallax on multi-session
 * swipes, and monotonic AGSL audio wave shimmer. Reused both inside [MediaExpandedContent] and as a
 * full-parent-fit backdrop layer during interactive scrub collapse.
 */
@Composable
fun MediaBackdropLayer(
    modifier: Modifier = Modifier,
    media: CapsuleContent.Media,
    timeSeconds: Float,
    mediaAnimationColorMode: MediaAnimationColorMode = MediaAnimationColorMode.ALBUM_COLOR,
    parallaxOffsetProvider: () -> Float = { 0f },
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeTertiary = MaterialTheme.colorScheme.tertiary
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = media,
            contentKey = { "${it.sessionTokenKey}:${it.title}:${it.artist}:${it.primaryColorArgb}:${mediaAnimationColorMode.name}" },
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 360)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 280))
            },
            label = "mediaBackdropCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { targetMedia ->
            val (targetPrimary, targetSecondary) = resolveMediaAnimationColors(
                media = targetMedia,
                colorMode = mediaAnimationColorMode,
                themePrimary = themePrimary,
                themeTertiary = themeTertiary,
            )
            val blurredBitmap = rememberCachedBlurredImageBitmap(targetMedia.artworkBytes)
            if (blurredBitmap != null) {
                Image(
                    bitmap = blurredBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.54f
                            scaleX = 1.18f
                            scaleY = 1.18f
                            translationX = parallaxOffsetProvider()
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.14f
                            scaleY = 1.14f
                            translationX = parallaxOffsetProvider()
                        }
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    targetPrimary.copy(alpha = 0.30f),
                                    targetSecondary.copy(alpha = 0.40f),
                                ),
                            ),
                        ),
                )
            }
        }

        MediaWaveShimmerOverlay(
            media = media,
            initialTimeSeconds = timeSeconds,
            mediaAnimationColorMode = mediaAnimationColorMode,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MediaWaveShimmerOverlay(
    modifier: Modifier = Modifier,
    media: CapsuleContent.Media,
    initialTimeSeconds: Float,
    mediaAnimationColorMode: MediaAnimationColorMode,
) {
    val (resolvedPrimary, resolvedSecondary) = resolveMediaAnimationColors(
        media = media,
        colorMode = mediaAnimationColorMode,
        themePrimary = MaterialTheme.colorScheme.primary,
        themeTertiary = MaterialTheme.colorScheme.tertiary,
    )
    val primaryColor by animateColorAsState(
        targetValue = resolvedPrimary,
        animationSpec = tween(durationMillis = 380),
        label = "mediaBackdropPrimary",
    )
    val secondaryColor by animateColorAsState(
        targetValue = resolvedSecondary,
        animationSpec = tween(durationMillis = 380),
        label = "mediaBackdropSecondary",
    )
    val waveActivity by animateFloatAsState(
        targetValue = if (media.isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "mediaBackdropWaveActivity",
    )
    val waveVelocity by animateFloatAsState(
        targetValue = if (media.isPlaying) 2.8f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "mediaBackdropWaveVel",
    )
    val wavePhase = remember { mutableFloatStateOf(initialTimeSeconds) }
    val needWaveMotion = media.isPlaying || waveVelocity > 0.002f
    LaunchedEffect(needWaveMotion) {
        if (!needWaveMotion) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    val dtSec = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    if (waveVelocity > 0.001f) {
                        wavePhase.floatValue += dtSec * waveVelocity
                    }
                }
                lastNanos = nowNanos
            }
        }
    }

    val waveShader = remember { MusicWaveShader.create() }
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.42f),
                        Color.Black.copy(alpha = 0.10f),
                    ),
                ),
            )
            .drawBehind {
                MusicWaveShader.updateUniforms(
                    shader = waveShader,
                    widthPx = size.width,
                    heightPx = size.height,
                    timeSeconds = wavePhase.floatValue,
                    waveActivity = waveActivity,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                )
                drawRect(brush = ShaderBrush(waveShader))
            },
    )
}

/**
 * State 3 expanded media player card featuring a blurred album-art backdrop with crossfade &
 * parallax, shared album icon flight, app badge + audio output switcher, multi-player swipe paging,
 * smooth 360-degree rotating album glow, and an M3 Expressive play/pause control morphing smoothly
 * from a spinning 7-sided cookie (playing) to a clean circle (paused).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaExpandedContent(
    modifier: Modifier = Modifier,
    media: CapsuleContent.Media,
    mediaSessions: List<CapsuleContent.Media> = emptyList(),
    selectedMediaIndex: Int = 0,
    expandedMediaGlowEffects: Set<ExpandedMediaGlowEffect> = setOf(
        ExpandedMediaGlowEffect.CARD_BORDER_NEURON,
        ExpandedMediaGlowEffect.OUTER_SCREEN_GLOW,
        ExpandedMediaGlowEffect.ALBUM_ART_RING,
    ),
    mediaAnimationColorMode: MediaAnimationColorMode = MediaAnimationColorMode.ALBUM_COLOR,
    topClearanceDp: Float,
    contentPaddingDp: Float = 18f,
    timeSeconds: Float,
    interactionsEnabled: Boolean = true,
    showBackdrop: Boolean = true,
    enableAlbumSharedTransition: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenMediaApp: (String) -> Unit,
    onSelectMediaSession: (Int) -> Unit = {},
    onOpenAudioOutput: () -> Unit = {},
    onLyricsActiveChanged: (Boolean) -> Unit = {},
    onUserInteraction: () -> Unit,
    onTogglePlayPause: (String) -> Unit,
    onSkipNext: (String) -> Unit,
    onSkipPrevious: (String) -> Unit,
    onSeekTo: (String, Long) -> Unit,
) {
    val sessions = remember(media, mediaSessions) {
        mediaSessions.ifEmpty { listOf(media) }
    }
    val safeSelectedIdx = selectedMediaIndex.coerceIn(0, sessions.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeSelectedIdx,
        pageCount = { sessions.size },
    )

    LaunchedEffect(safeSelectedIdx, sessions.size) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != safeSelectedIdx) {
            pagerState.scrollToPage(safeSelectedIdx)
        }
    }
    LaunchedEffect(pagerState.settledPage, sessions.size) {
        if (pagerState.settledPage != safeSelectedIdx && pagerState.settledPage in sessions.indices) {
            onSelectMediaSession(pagerState.settledPage)
        }
    }

    val activePageMedia = sessions.getOrNull(pagerState.currentPage) ?: media
    val cookieAngularSpeed by animateFloatAsState(
        targetValue = if (activePageMedia.isPlaying) 26f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "mediaCookieSpinSpeed",
    )
    val albumGlowAngularSpeed by animateFloatAsState(
        targetValue = if (activePageMedia.isPlaying) 84f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "mediaAlbumSpinSpeed",
    )
    val cookieRotationDeg = remember { mutableFloatStateOf(0f) }
    val albumGlowRotationDeg = remember { mutableFloatStateOf(0f) }
    val albumBeatPhase = remember { mutableFloatStateOf(0f) }

    val isMotionNeeded = activePageMedia.isPlaying || cookieAngularSpeed > 0.02f || albumGlowAngularSpeed > 0.02f
    LaunchedEffect(isMotionNeeded) {
        if (!isMotionNeeded) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    val dtSec = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    if (cookieAngularSpeed > 0.01f) {
                        cookieRotationDeg.floatValue = (cookieRotationDeg.floatValue + dtSec * cookieAngularSpeed) % 360f
                    }
                    if (albumGlowAngularSpeed > 0.01f) {
                        albumGlowRotationDeg.floatValue = (albumGlowRotationDeg.floatValue + dtSec * albumGlowAngularSpeed) % 360f
                        albumBeatPhase.floatValue = (albumBeatPhase.floatValue + dtSec / 2.8f) % 1f
                    }
                }
                lastNanos = nowNanos
            }
        }
    }

    val maxParallaxPx = with(LocalDensity.current) { 44.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        if (showBackdrop) {
            MediaBackdropLayer(
                media = activePageMedia,
                timeSeconds = timeSeconds,
                mediaAnimationColorMode = mediaAnimationColorMode,
                parallaxOffsetProvider = { -pagerState.currentPageOffsetFraction * maxParallaxPx },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = topClearanceDp.dp,
                    bottom = (contentPaddingDp * 0.72f).coerceAtLeast(10f).dp,
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = interactionsEnabled && sessions.size > 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { pageIndex ->
                val pageMedia = sessions.getOrNull(pageIndex) ?: activePageMedia
                MediaPlayerPage(
                    media = pageMedia,
                    expandedMediaGlowEffects = expandedMediaGlowEffects,
                    mediaAnimationColorMode = mediaAnimationColorMode,
                    contentPaddingDp = contentPaddingDp,
                    albumGlowRotationProvider = { albumGlowRotationDeg.floatValue },
                    albumBeatPhaseProvider = { albumBeatPhase.floatValue },
                    cookieRotationProvider = { cookieRotationDeg.floatValue },
                    interactionsEnabled = interactionsEnabled,
                    isActiveSharedPage = pageIndex == pagerState.currentPage,
                    enableAlbumSharedTransition = enableAlbumSharedTransition,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onOpenMediaApp = { onOpenMediaApp(pageMedia.packageName) },
                    onOpenAudioOutput = onOpenAudioOutput,
                    onLyricsActiveChanged = { active ->
                        if (pageIndex == pagerState.currentPage) {
                            onLyricsActiveChanged(active)
                        }
                    },
                    onUserInteraction = onUserInteraction,
                    onTogglePlayPause = { onTogglePlayPause(pageMedia.sessionTokenKey) },
                    onSkipNext = { onSkipNext(pageMedia.sessionTokenKey) },
                    onSkipPrevious = { onSkipPrevious(pageMedia.sessionTokenKey) },
                    onSeekTo = { targetMs -> onSeekTo(pageMedia.sessionTokenKey, targetMs) },
                )
            }

            if (sessions.size > 1) {
                val indicatorPrimary = MaterialTheme.colorScheme.primary
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        repeat(sessions.size) { index ->
                            val isCurrent = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(width = if (isCurrent) 14.dp else 5.5.dp, height = 5.5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) {
                                            indicatorPrimary
                                        } else {
                                            indicatorPrimary.copy(alpha = 0.34f)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaPlayerPage(
    modifier: Modifier = Modifier,
    media: CapsuleContent.Media,
    expandedMediaGlowEffects: Set<ExpandedMediaGlowEffect>,
    mediaAnimationColorMode: MediaAnimationColorMode,
    contentPaddingDp: Float,
    albumGlowRotationProvider: () -> Float,
    albumBeatPhaseProvider: () -> Float,
    cookieRotationProvider: () -> Float,
    interactionsEnabled: Boolean,
    isActiveSharedPage: Boolean,
    enableAlbumSharedTransition: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenMediaApp: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onLyricsActiveChanged: (Boolean) -> Unit,
    onUserInteraction: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val haptics = rememberCapsuleHaptics()
    val (resolvedPrimary, resolvedSecondary) = resolveMediaAnimationColors(
        media = media,
        colorMode = mediaAnimationColorMode,
        themePrimary = MaterialTheme.colorScheme.primary,
        themeTertiary = MaterialTheme.colorScheme.tertiary,
    )
    val primaryColor by animateColorAsState(
        targetValue = resolvedPrimary,
        animationSpec = tween(durationMillis = 360),
        label = "mediaPagePrimary",
    )
    val secondaryColor by animateColorAsState(
        targetValue = resolvedSecondary,
        animationSpec = tween(durationMillis = 360),
        label = "mediaPageSecondary",
    )
    val artworkBitmap = rememberCachedImageBitmap(media.artworkBytes)
    val appIconBitmap = rememberCachedImageBitmap(media.appIconBytes)
    val albumRotMatrix = remember { Matrix() }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember(media.sessionTokenKey) { mutableStateOf(false) }
    val hasLyrics = media.lyrics.isNotEmpty()
    LaunchedEffect(hasLyrics) {
        if (!hasLyrics) showLyrics = false
    }
    LaunchedEffect(showLyrics, hasLyrics) {
        onLyricsActiveChanged(showLyrics && hasLyrics)
    }
    DisposableEffect(Unit) {
        onDispose {
            onLyricsActiveChanged(false)
        }
    }

    val durationMs = media.durationMs.coerceAtLeast(1L)
    val remoteProgress = (media.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    LaunchedEffect(remoteProgress, isScrubbing) {
        if (!isScrubbing) {
            scrubProgress = remoteProgress
        }
    }

    val cookieMorph = rememberCookieToCircleMorph()
    val playPauseInteraction = remember { MutableInteractionSource() }
    val isPlayPausePressed by playPauseInteraction.collectIsPressedAsState()
    val targetMorphProgress = when {
        !media.isPlaying -> 1f
        isPlayPausePressed -> 0.56f
        else -> 0f
    }
    val cookieStateMorph by animateFloatAsState(
        targetValue = targetMorphProgress,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 560f),
        label = "cookieStateMorph",
    )
    val cookiePressScale by animateFloatAsState(
        targetValue = if (isPlayPausePressed && interactionsEnabled) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 880f),
        label = "cookiePressScale",
    )

    val showAlbumGlow = (ExpandedMediaGlowEffect.ALBUM_ART_RING in expandedMediaGlowEffects) &&
        media.isPlaying &&
        (!enableAlbumSharedTransition || !sharedTransitionScope.isTransitionActive)
    val albumGlowAlphaState = animateFloatAsState(
        targetValue = if (showAlbumGlow) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "albumGlowAlpha",
    )

    with(sharedTransitionScope) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = contentPaddingDp.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val artBaseMod = if (isActiveSharedPage && enableAlbumSharedTransition) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = "capsule_icon"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 410f) },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .drawBehind {
                            val glowAlpha = albumGlowAlphaState.value
                            if (glowAlpha > 0.02f) {
                                val baseCornerPx = 14.dp.toPx()
                                val maxExpandPx = 11.dp.toPx()
                                albumRotMatrix.reset()
                                albumRotMatrix.setRotate(albumGlowRotationProvider(), center.x, center.y)
                                val rawWave = albumBeatPhaseProvider().coerceIn(0f, 1f)
                                val eased = FastOutSlowInEasing.transform(rawWave)
                                val expandPx = eased * maxExpandPx
                                val inhale = (rawWave / 0.16f).coerceIn(0f, 1f)
                                val exhale = (1f - eased).let { it * it }
                                val fade = inhale * exhale * 0.68f * glowAlpha
                                if (fade > 0.015f) {
                                    val beatStrokePx = (2.3f - 1.55f * eased).coerceAtLeast(0.65f).dp.toPx()
                                    val beatShader = SweepGradient(
                                        center.x,
                                        center.y,
                                        intArrayOf(
                                            primaryColor.copy(alpha = fade).toArgb(),
                                            secondaryColor.copy(alpha = fade).toArgb(),
                                            Color.White.copy(alpha = 0.85f * fade).toArgb(),
                                            primaryColor.copy(alpha = fade).toArgb(),
                                        ),
                                        floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                                    ).apply {
                                        setLocalMatrix(albumRotMatrix)
                                    }
                                    drawRoundRect(
                                        brush = ShaderBrush(beatShader),
                                        topLeft = Offset(-expandPx, -expandPx),
                                        size = Size(size.width + expandPx * 2f, size.height + expandPx * 2f),
                                        cornerRadius = CornerRadius(baseCornerPx + expandPx * 0.85f),
                                        style = Stroke(width = beatStrokePx),
                                    )
                                }
                            }
                        }
                        .clickable(enabled = interactionsEnabled) {
                            haptics.click()
                            onOpenMediaApp()
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = artBaseMod
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(primaryColor.copy(alpha = 0.24f))
                            .drawWithContent {
                                drawContent()
                                val glowAlpha = albumGlowAlphaState.value
                                if (glowAlpha > 0.02f) {
                                    val strokePx = 2.5.dp.toPx()
                                    val halfStroke = strokePx * 0.5f
                                    val cornerPx = (14.dp.toPx() - halfStroke).coerceAtLeast(1f)
                                    albumRotMatrix.reset()
                                    albumRotMatrix.setRotate(albumGlowRotationProvider(), center.x, center.y)
                                    val sweepShader = SweepGradient(
                                        center.x,
                                        center.y,
                                        intArrayOf(
                                            primaryColor.copy(alpha = glowAlpha).toArgb(),
                                            secondaryColor.copy(alpha = glowAlpha).toArgb(),
                                            Color.White.copy(alpha = 0.88f * glowAlpha).toArgb(),
                                            primaryColor.copy(alpha = glowAlpha).toArgb(),
                                        ),
                                        floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                                    ).apply {
                                        setLocalMatrix(albumRotMatrix)
                                    }
                                    drawRoundRect(
                                        brush = ShaderBrush(sweepShader),
                                        topLeft = Offset(halfStroke, halfStroke),
                                        size = Size(size.width - strokePx, size.height - strokePx),
                                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                                        style = Stroke(width = strokePx),
                                    )
                                }
                            },
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

                    if (appIconBitmap != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.86f))
                                .padding(1.5.dp)
                                .clip(CircleShape),
                        ) {
                            Image(
                                bitmap = appIconBitmap,
                                contentDescription = media.packageName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = interactionsEnabled) {
                            haptics.click()
                            onOpenMediaApp()
                        },
                ) {
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

                if (hasLyrics) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CapsuleExpressiveSurface(
                        enabled = interactionsEnabled,
                        onClick = {
                            haptics.click()
                            onUserInteraction()
                            showLyrics = !showLyrics
                        },
                        restCornerDp = 14f,
                        pressedCornerDp = 7f,
                        backgroundColor = if (showLyrics) primaryColor else Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = stringResource(R.string.desc_toggle_lyrics),
                            tint = if (showLyrics) Color.Black else Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                if (media.audioOutputLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CapsuleExpressiveSurface(
                        enabled = interactionsEnabled,
                        onClick = {
                            haptics.click()
                            onUserInteraction()
                            onOpenAudioOutput()
                        },
                        restCornerDp = 14f,
                        pressedCornerDp = 7f,
                        backgroundColor = primaryColor.copy(alpha = 0.22f),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Icon(
                                imageVector = outputIconForKind(media.audioOutputKind),
                                contentDescription = stringResource(R.string.desc_audio_output),
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = media.audioOutputLabel,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = showLyrics && hasLyrics,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "mediaLyricsToggle",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { showingLyrics ->
                if (showingLyrics) {
                    SyncedLyricsPane(
                        lyrics = media.lyrics,
                        positionMs = media.positionMs,
                        durationMs = durationMs,
                        isPlaying = media.isPlaying,
                        primaryColor = primaryColor,
                        interactionsEnabled = interactionsEnabled,
                        onUserInteraction = onUserInteraction,
                        onSeekTo = onSeekTo,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                    ) {
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
                                enabled = interactionsEnabled,
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
                                enabled = interactionsEnabled,
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

                            // M3 Expressive rotating 7-sided cookie (playing) morphing smoothly into a Circle (paused)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clickable(
                                        enabled = interactionsEnabled,
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
                                            rotationZ = cookieRotationProvider()
                                            scaleX = cookiePressScale
                                            scaleY = cookiePressScale
                                        }
                                        .clip(MorphingCookieShape(morph = cookieMorph, progress = cookieStateMorph))
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
                                enabled = interactionsEnabled,
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
    }
}

@Composable
private fun SyncedLyricsPane(
    modifier: Modifier = Modifier,
    lyrics: List<LyricLine>,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    primaryColor: Color,
    interactionsEnabled: Boolean,
    onUserInteraction: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val topPeekPx = remember(density) { with(density) { 26.dp.roundToPx() } }
    var livePositionMs by remember(lyrics) { mutableLongStateOf(positionMs) }
    var hasInitialSynced by remember(lyrics) { mutableStateOf(false) }
    var isManualBrowsePaused by remember(lyrics) { mutableStateOf(false) }

    LaunchedEffect(positionMs, durationMs, isPlaying) {
        livePositionMs = positionMs
        if (!isPlaying) return@LaunchedEffect
        val anchorElapsedMs = SystemClock.elapsedRealtime()
        while (true) {
            delay(100.milliseconds)
            val delta = SystemClock.elapsedRealtime() - anchorElapsedMs
            livePositionMs = (positionMs + delta).coerceAtMost(durationMs)
        }
    }

    val activeIdx = remember(lyrics, livePositionMs) {
        LrcLyricsParser.activeLineIndex(lyrics, livePositionMs + 220L)
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isManualBrowsePaused = true
            onUserInteraction()
        } else if (isManualBrowsePaused) {
            // Re-lock to live synced lyric line smoothly after user finishes manual browsing
            delay(2_200.milliseconds)
            isManualBrowsePaused = false
            if (activeIdx >= 0) {
                listState.animateScrollToItem(index = activeIdx, scrollOffset = -topPeekPx)
            }
        }
    }

    LaunchedEffect(activeIdx, isManualBrowsePaused) {
        if (activeIdx < 0) return@LaunchedEffect
        if (!hasInitialSynced) {
            hasInitialSynced = true
            listState.scrollToItem(index = activeIdx, scrollOffset = -topPeekPx)
        } else if (!isManualBrowsePaused && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index = activeIdx, scrollOffset = -topPeekPx)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp),
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isSynced = line.timestampMs >= 0L
            val isActive = isSynced && index == activeIdx
            val textColor by animateColorAsState(
                targetValue = when {
                    isActive -> Color.White
                    !isSynced -> Color.White.copy(alpha = 0.88f)
                    index < activeIdx -> Color.White.copy(alpha = 0.38f)
                    else -> Color.White.copy(alpha = 0.56f)
                },
                animationSpec = tween(durationMillis = 220),
                label = "lyricLineColor",
            )
            Text(
                text = line.text,
                color = textColor,
                fontSize = if (isActive) 14.sp else 12.sp,
                lineHeight = if (isActive) 19.sp else 16.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .let { base ->
                        if (isSynced && interactionsEnabled) {
                            base.clickable {
                                isManualBrowsePaused = false
                                livePositionMs = line.timestampMs
                                onUserInteraction()
                                onSeekTo(line.timestampMs)
                            }
                        } else {
                            base
                        }
                    }
                    .background(if (isActive) primaryColor.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

private fun resolveMediaAnimationColors(
    media: CapsuleContent.Media,
    colorMode: MediaAnimationColorMode,
    themePrimary: Color,
    themeTertiary: Color,
): Pair<Color, Color> = when (colorMode) {
    MediaAnimationColorMode.ALBUM_COLOR -> Color(media.primaryColorArgb) to Color(media.secondaryColorArgb)
    MediaAnimationColorMode.PRIMARY_COLOR -> themePrimary to themeTertiary
}

private fun outputIconForKind(kind: MediaOutputKind): ImageVector = when (kind) {
    MediaOutputKind.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    MediaOutputKind.WIRED -> Icons.Rounded.Headphones
    MediaOutputKind.CAST -> Icons.Rounded.Cast
    MediaOutputKind.SPEAKER,
    MediaOutputKind.UNKNOWN,
    -> Icons.Rounded.Speaker
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
