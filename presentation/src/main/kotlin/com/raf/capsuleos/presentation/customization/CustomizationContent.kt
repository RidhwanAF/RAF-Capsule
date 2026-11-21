package com.raf.capsuleos.presentation.customization

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.navigation.CustomizationSection
import com.raf.capsuleos.presentation.setup.SetupUiState
import com.raf.capsuleos.presentation.setup.components.CameraPositionCard
import com.raf.capsuleos.presentation.setup.components.CapsuleBehaviorCard
import com.raf.capsuleos.presentation.setup.components.GestureSettingsCard
import com.raf.capsuleos.presentation.setup.components.IndicatorsAndMediaCard

/**
 * Stateless screen displaying a real-time interactive simulation preview above (portrait) or beside
 * (landscape) the dedicated customization card for [section], applying Scaffold padding inside the
 * scrollable region so scroll content never clips against system bars.
 */
@Composable
fun CustomizationContent(
    modifier: Modifier = Modifier,
    section: CustomizationSection,
    uiState: SetupUiState,
    snackbarHostState: SnackbarHostState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    onAutoDetectCamera: () -> Unit,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
    livePreviewContent: @Composable () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val (titleRes, subtitleRes) = when (section) {
        CustomizationSection.CAMERA_POSITION ->
            R.string.title_camera_position_category to R.string.msg_customization_subtitle
        CustomizationSection.BEHAVIOR_LAYOUT ->
            R.string.title_behavior_category to R.string.msg_behavior_subtitle
        CustomizationSection.INDICATORS_MEDIA ->
            R.string.title_indicators_media_category to R.string.msg_indicators_subtitle
        CustomizationSection.GESTURES_ACTIONS ->
            R.string.title_gestures_category to R.string.msg_gestures_subtitle
    }
    val sharedContainerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "nav_section_${section.name}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
            )
        }
    } else {
        Modifier
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.desc_navigate_back),
                        )
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(subtitleRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
            .fillMaxSize()
            .then(sharedContainerModifier),
    ) { innerPadding ->
        val startPad = innerPadding.calculateStartPadding(layoutDirection) + 20.dp
        val endPad = innerPadding.calculateEndPadding(layoutDirection) + 20.dp
        val topPad = innerPadding.calculateTopPadding() + 8.dp
        val bottomPad = innerPadding.calculateBottomPadding() + 16.dp

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeSplit = maxWidth >= 640.dp

            if (isLandscapeSplit) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .padding(start = startPad, top = topPad, bottom = bottomPad),
                    ) {
                        livePreviewContent()
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = topPad, end = endPad, bottom = bottomPad),
                    ) {
                        CustomizationSectionCard(
                            section = section,
                            uiState = uiState,
                            onAutoDetectCamera = onAutoDetectCamera,
                            onUpdatePreferences = onUpdatePreferences,
                        )
                    }
                }
            } else {
                val portraitScrollState = rememberScrollState()
                val isPortraitScrolled by remember {
                    derivedStateOf { portraitScrollState.value > 0 }
                }
                val topScrollShadowAlpha by animateFloatAsState(
                    targetValue = if (isPortraitScrolled) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "customizationTopScrollShadow",
                )
                val scrollViewportShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                val scrimColor = MaterialTheme.colorScheme.scrim

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 1040.dp)
                            .fillMaxWidth()
                            .padding(start = startPad, end = endPad, top = topPad),
                    ) {
                        livePreviewContent()
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 1040.dp)
                            .fillMaxWidth()
                            .padding(start = startPad, end = endPad)
                            .clip(scrollViewportShape),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(portraitScrollState)
                                .padding(bottom = bottomPad),
                        ) {
                            CustomizationSectionCard(
                                section = section,
                                uiState = uiState,
                                onAutoDetectCamera = onAutoDetectCamera,
                                onUpdatePreferences = onUpdatePreferences,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (topScrollShadowAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(22.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            0.0f to scrimColor.copy(alpha = 0.14f * topScrollShadowAlpha),
                                            0.45f to scrimColor.copy(alpha = 0.05f * topScrollShadowAlpha),
                                            1.0f to Color.Transparent,
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizationSectionCard(
    modifier: Modifier = Modifier,
    section: CustomizationSection,
    uiState: SetupUiState,
    onAutoDetectCamera: () -> Unit,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
) {
    when (section) {
        CustomizationSection.CAMERA_POSITION -> CameraPositionCard(
            preferences = uiState.preferences,
            cutoutGeometry = uiState.cutoutGeometry,
            detectedPosition = uiState.detectedPosition,
            hasHardwareCutout = uiState.hasHardwareCutout,
            collapsible = false,
            onUpdatePreferences = onUpdatePreferences,
            onAutoDetectClick = onAutoDetectCamera,
            modifier = modifier,
        )
        CustomizationSection.BEHAVIOR_LAYOUT -> CapsuleBehaviorCard(
            preferences = uiState.preferences,
            collapsible = false,
            onUpdatePreferences = onUpdatePreferences,
            modifier = modifier,
        )
        CustomizationSection.INDICATORS_MEDIA -> IndicatorsAndMediaCard(
            preferences = uiState.preferences,
            collapsible = false,
            onUpdatePreferences = onUpdatePreferences,
            modifier = modifier,
        )
        CustomizationSection.GESTURES_ACTIONS -> GestureSettingsCard(
            preferences = uiState.preferences,
            collapsible = false,
            onUpdatePreferences = onUpdatePreferences,
            modifier = modifier,
        )
    }
}
