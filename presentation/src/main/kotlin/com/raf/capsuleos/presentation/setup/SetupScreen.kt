package com.raf.capsuleos.presentation.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import kotlinx.coroutines.launch

/**
 * Stateful setup and calibration container binding [SetupViewModel] and Android system settings.
 */
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    onOpenSimulation: () -> Unit = {},
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val adbCommand = remember(context.packageName) {
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
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
        onCopyAdbCommand = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCommand))
            coroutineScope.launch {
                snackbarHostState.showSnackbar(copiedMessage)
            }
        },
        onToggleStartPause = viewModel::toggleUserPause,
        onOpenSimulation = onOpenSimulation,
        onAutoDetectCamera = { viewModel.autoDetectCameraPosition(context) },
        onUpdatePreferences = viewModel::updatePreferences,
        onTestVolume = { viewModel.triggerTestAction(CapsuleTestAction.VOLUME) },
        onTestAlarm = { viewModel.triggerTestAction(CapsuleTestAction.ALARM) },
        onTestUnlock = { viewModel.triggerTestAction(CapsuleTestAction.NOW_BRIEF) },
        onTestNotification = { viewModel.triggerTestAction(CapsuleTestAction.NOTIFICATION) },
        modifier = modifier,
    )
}
