package com.raf.capsuleos.presentation.setup

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.navigation.CustomizationSection
import com.raf.capsuleos.presentation.setup.components.CameraPositionCard
import com.raf.capsuleos.presentation.setup.components.CapsuleBehaviorCard
import com.raf.capsuleos.presentation.setup.components.GestureSettingsCard
import com.raf.capsuleos.presentation.setup.components.IndicatorsAndMediaCard
import com.raf.capsuleos.presentation.setup.components.QuickTestTriggersCard
import com.raf.capsuleos.presentation.setup.components.SetupCategoryCard

private val MAIN_MENU_MAX_WIDTH = 1040.dp

/**
 * Home dashboard separating Setup & Permissions from expandable/collapsible Capsule Configuration
 * category cards. Uses a single centered scrollable column with max width per main menu card and
 * edge-to-edge non-clipping Scaffold padding, while sub-items inside each expanded menu split
 * dynamically via FlowRow in landscape.
 */
@Composable
fun SetupContent(
    modifier: Modifier = Modifier,
    uiState: SetupUiState,
    adbCommand: String,
    snackbarHostState: SnackbarHostState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onGrantAccessibility: () -> Unit,
    onGrantNotificationListener: () -> Unit,
    onGrantWriteSettings: () -> Unit,
    onGrantDndPolicyAccess: () -> Unit,
    onGrantBluetoothPermission: () -> Unit = {},
    onGrantLocationPermission: () -> Unit = {},
    onCopyAdbCommand: () -> Unit,
    onToggleStartPause: () -> Unit,
    onOpenCustomizationSection: (CustomizationSection) -> Unit,
    onOpenSimulation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAutoDetectCamera: () -> Unit,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
    onTestVolume: () -> Unit,
    onTestAlarm: () -> Unit,
    onTestUnlock: () -> Unit,
    onTestNotification: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val sharedSettingsModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "nav_app_settings_container"),
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
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = stringResource(R.string.title_setup_screen),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.msg_setup_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onOpenAppSettings()
                        },
                        modifier = sharedSettingsModifier,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.desc_open_app_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val mainCardModifier = Modifier
            .widthIn(max = MAIN_MENU_MAX_WIDTH)
            .fillMaxWidth()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 20.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 20.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 20.dp,
                ),
        ) {
            SetupCategoryCard(
                uiState = uiState,
                adbCommand = adbCommand,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onGrantAccessibility = onGrantAccessibility,
                onGrantNotificationListener = onGrantNotificationListener,
                onGrantWriteSettings = onGrantWriteSettings,
                onGrantDndPolicyAccess = onGrantDndPolicyAccess,
                onGrantBluetoothPermission = onGrantBluetoothPermission,
                onGrantLocationPermission = onGrantLocationPermission,
                onCopyAdbCommand = onCopyAdbCommand,
                onToggleStartPause = onToggleStartPause,
                onOpenSimulation = onOpenSimulation,
                modifier = mainCardModifier,
            )

            Text(
                text = stringResource(R.string.title_capsule_config_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = mainCardModifier.padding(start = 4.dp, top = 4.dp),
            )

            CameraPositionCard(
                preferences = uiState.preferences,
                cutoutGeometry = uiState.cutoutGeometry,
                detectedPosition = uiState.detectedPosition,
                hasHardwareCutout = uiState.hasHardwareCutout,
                collapsible = true,
                initiallyExpanded = false,
                onOpenDedicatedScreen = {
                    onOpenCustomizationSection(CustomizationSection.CAMERA_POSITION)
                },
                onUpdatePreferences = onUpdatePreferences,
                onAutoDetectClick = onAutoDetectCamera,
                modifier = mainCardModifier.sectionSharedModifier(
                    section = CustomizationSection.CAMERA_POSITION,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            )

            CapsuleBehaviorCard(
                preferences = uiState.preferences,
                collapsible = true,
                initiallyExpanded = false,
                onOpenDedicatedScreen = {
                    onOpenCustomizationSection(CustomizationSection.BEHAVIOR_LAYOUT)
                },
                onUpdatePreferences = onUpdatePreferences,
                modifier = mainCardModifier.sectionSharedModifier(
                    section = CustomizationSection.BEHAVIOR_LAYOUT,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            )

            IndicatorsAndMediaCard(
                preferences = uiState.preferences,
                collapsible = true,
                initiallyExpanded = false,
                onOpenDedicatedScreen = {
                    onOpenCustomizationSection(CustomizationSection.INDICATORS_MEDIA)
                },
                onUpdatePreferences = onUpdatePreferences,
                modifier = mainCardModifier.sectionSharedModifier(
                    section = CustomizationSection.INDICATORS_MEDIA,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            )

            GestureSettingsCard(
                preferences = uiState.preferences,
                collapsible = true,
                initiallyExpanded = false,
                onOpenDedicatedScreen = {
                    onOpenCustomizationSection(CustomizationSection.GESTURES_ACTIONS)
                },
                onUpdatePreferences = onUpdatePreferences,
                modifier = mainCardModifier.sectionSharedModifier(
                    section = CustomizationSection.GESTURES_ACTIONS,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            )

            QuickTestTriggersCard(
                isServiceRunning = uiState.isCapsuleRunning,
                onTestVolume = onTestVolume,
                onTestAlarm = onTestAlarm,
                onTestUnlock = onTestUnlock,
                onTestNotification = onTestNotification,
                modifier = mainCardModifier,
            )
        }
    }
}

@Composable
private fun Modifier.sectionSharedModifier(
    section: CustomizationSection,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
    with(sharedTransitionScope) {
        this@sectionSharedModifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "nav_section_${section.name}"),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
        )
    }
} else {
    this
}
