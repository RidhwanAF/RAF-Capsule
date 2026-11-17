package com.raf.capsuleos.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes
import com.raf.capsuleos.presentation.setup.components.AdbSecureSettingsCard
import com.raf.capsuleos.presentation.setup.components.CutoutTuningCard
import com.raf.capsuleos.presentation.setup.components.PermissionStatusCard

/**
 * Stateless setup and calibration dashboard content.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    onPrimaryActionClick: () -> Unit,
    onUpdatePreferences: ((CapsulePreferences) -> CapsulePreferences) -> Unit,
    onTestVolume: () -> Unit,
    onTestAlarm: () -> Unit,
    onTestUnlock: () -> Unit,
    onTestNotification: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
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

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPrimaryActionClick()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 24.dp, pressedRadius = 10.dp),
                colors = if (uiState.isCapsuleRunning) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                val buttonText = when {
                    !uiState.hasAccessibilityOverlay -> stringResource(R.string.action_enable_accessibility)
                    uiState.isCapsuleRunning -> stringResource(R.string.action_pause_capsule)
                    else -> stringResource(R.string.action_resume_capsule)
                }
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.title_permissions_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            PermissionStatusCard(
                title = stringResource(R.string.label_accessibility_permission),
                description = stringResource(R.string.msg_accessibility_permission_desc),
                isGranted = uiState.hasAccessibilityOverlay,
                actionLabel = stringResource(R.string.action_grant),
                onActionClick = onGrantAccessibility,
            )

            PermissionStatusCard(
                title = stringResource(R.string.label_media_permission),
                description = stringResource(R.string.msg_media_permission_desc),
                isGranted = uiState.hasNotificationListener,
                actionLabel = stringResource(R.string.action_grant),
                onActionClick = onGrantNotificationListener,
            )

            PermissionStatusCard(
                title = stringResource(R.string.label_write_settings),
                description = stringResource(R.string.msg_write_settings_desc),
                isGranted = uiState.hasWriteSettings,
                actionLabel = stringResource(R.string.action_grant),
                onActionClick = onGrantWriteSettings,
            )

            PermissionStatusCard(
                title = stringResource(R.string.label_dnd_policy_access),
                description = stringResource(R.string.msg_dnd_policy_desc),
                isGranted = uiState.hasDndPolicyAccess,
                actionLabel = stringResource(R.string.action_grant),
                onActionClick = onGrantDndPolicyAccess,
            )

            AdbSecureSettingsCard(
                isGranted = uiState.hasWriteSecureSettings,
                adbCommand = adbCommand,
                onCopyCommand = onCopyAdbCommand,
            )

            CutoutTuningCard(
                preferences = uiState.preferences,
                isServiceRunning = uiState.isCapsuleRunning,
                onUpdatePreferences = onUpdatePreferences,
                onTestVolume = onTestVolume,
                onTestAlarm = onTestAlarm,
                onTestUnlock = onTestUnlock,
                onTestNotification = onTestNotification,
            )
        }
    }
}
