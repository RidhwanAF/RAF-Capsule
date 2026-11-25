package com.raf.capsuleos.presentation.setup

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.capsule.service.CapsuleTestAction
import com.raf.capsuleos.presentation.navigation.CustomizationSection
import kotlinx.coroutines.launch

/**
 * Stateful home setup container binding [SetupViewModel] and Android system settings.
 */
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onOpenCustomizationSection: (CustomizationSection) -> Unit = {},
    onOpenSimulation: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    val adbCommand = remember(context.packageName) {
        listOf(
            "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
            "adb shell pm grant ${context.packageName} android.permission.RECORD_AUDIO",
            "adb shell appops set ${context.packageName} RECORD_AUDIO allow",
            "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
        ).joinToString("\n")
    }
    val copiedMessage = stringResource(R.string.msg_adb_copied)

    LaunchedEffect(viewModel, resources) {
        viewModel.feedback.collect { event ->
            when (event) {
                is SetupFeedback.CutoutDetected -> {
                    val msg = if (event.hasHardwareCutout) {
                        val posLabel = resources.getString(
                            when (event.position) {
                                CameraAnchorPosition.LEFT -> R.string.label_anchor_left
                                CameraAnchorPosition.CENTER -> R.string.label_anchor_center
                                CameraAnchorPosition.RIGHT -> R.string.label_anchor_right
                            },
                        )
                        resources.getString(R.string.msg_cutout_detected, posLabel, event.holeWidthDp)
                    } else {
                        resources.getString(R.string.msg_no_cutout_detected)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SetupContent(
        uiState = uiState,
        adbCommand = adbCommand,
        snackbarHostState = snackbarHostState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onGrantAccessibility = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onGrantNotificationListener = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        onGrantWriteSettings = {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
        },
        onGrantDndPolicyAccess = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        },
        onGrantBluetoothPermission = {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        },
        onGrantLocationPermission = {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        },
        onCopyAdbCommand = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCommand))
            coroutineScope.launch {
                snackbarHostState.showSnackbar(copiedMessage)
            }
        },
        onToggleStartPause = viewModel::toggleUserPause,
        onOpenCustomizationSection = onOpenCustomizationSection,
        onOpenSimulation = onOpenSimulation,
        onOpenPermissions = onOpenPermissions,
        onOpenAppSettings = onOpenAppSettings,
        onAutoDetectCamera = { viewModel.autoDetectCameraPosition(context) },
        onUpdatePreferences = viewModel::updatePreferences,
        onTestVolume = { viewModel.triggerTestAction(CapsuleTestAction.VOLUME) },
        onTestAlarm = { viewModel.triggerTestAction(CapsuleTestAction.ALARM) },
        onTestUnlock = { viewModel.triggerTestAction(CapsuleTestAction.NOW_BRIEF) },
        onTestNotification = { viewModel.triggerTestAction(CapsuleTestAction.NOTIFICATION) },
        modifier = modifier,
    )
}
