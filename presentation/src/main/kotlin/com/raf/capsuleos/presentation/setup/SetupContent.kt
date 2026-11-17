package com.raf.capsuleos.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.setup.components.CameraPositionCard
import com.raf.capsuleos.presentation.setup.components.CapsuleBehaviorCard
import com.raf.capsuleos.presentation.setup.components.QuickTestTriggersCard
import com.raf.capsuleos.presentation.setup.components.SetupCategoryCard

/**
 * Stateless dashboard separated cleanly into Setup, Camera Position, Behavior & Layout,
 * and Live Overlay Quick Test category cards.
 */
@Composable
fun SetupContent(
    modifier: Modifier = Modifier,
    uiState: SetupUiState,
    adbCommand: String,
    snackbarHostState: SnackbarHostState,
    onGrantAccessibility: () -> Unit,
    onGrantNotificationListener: () -> Unit,
    onGrantWriteSettings: () -> Unit,
    onGrantDndPolicyAccess: () -> Unit,
    onCopyAdbCommand: () -> Unit,
    onToggleStartPause: () -> Unit,
    onOpenSimulation: () -> Unit,
    onAutoDetectCamera: () -> Unit,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
    onTestVolume: () -> Unit,
    onTestAlarm: () -> Unit,
    onTestUnlock: () -> Unit,
    onTestNotification: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.title_setup_screen),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.msg_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SetupCategoryCard(
                uiState = uiState,
                adbCommand = adbCommand,
                onGrantAccessibility = onGrantAccessibility,
                onGrantNotificationListener = onGrantNotificationListener,
                onGrantWriteSettings = onGrantWriteSettings,
                onGrantDndPolicyAccess = onGrantDndPolicyAccess,
                onCopyAdbCommand = onCopyAdbCommand,
                onToggleStartPause = onToggleStartPause,
                onOpenSimulation = onOpenSimulation,
            )

            CameraPositionCard(
                preferences = uiState.preferences,
                cutoutGeometry = uiState.cutoutGeometry,
                detectedPosition = uiState.detectedPosition,
                hasHardwareCutout = uiState.hasHardwareCutout,
                onUpdatePreferences = onUpdatePreferences,
                onAutoDetectClick = onAutoDetectCamera,
            )

            CapsuleBehaviorCard(
                preferences = uiState.preferences,
                onUpdatePreferences = onUpdatePreferences,
            )

            QuickTestTriggersCard(
                isServiceRunning = uiState.isCapsuleRunning,
                onTestVolume = onTestVolume,
                onTestAlarm = onTestAlarm,
                onTestUnlock = onTestUnlock,
                onTestNotification = onTestNotification,
            )
        }
    }
}
