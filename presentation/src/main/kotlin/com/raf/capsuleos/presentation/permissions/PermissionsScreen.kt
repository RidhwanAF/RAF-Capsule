package com.raf.capsuleos.presentation.permissions

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.capsuleos.presentation.R
import com.raf.capsuleos.presentation.common.OemCompatibilityHelper
import com.raf.capsuleos.presentation.common.ShizukuPermissionHelper
import com.raf.capsuleos.presentation.common.ShizukuStatus
import com.raf.capsuleos.presentation.common.capsuleExpressiveButtonShapes
import com.raf.capsuleos.presentation.setup.SetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private val PERMISSIONS_MAX_WIDTH = 860.dp

/**
 * Dedicated full-screen Permissions & Privileged Setup hub guiding users through Core Required
 * permissions, Optional feature permissions, one-tap Shizuku or step-by-step ADB
 * `WRITE_SECURE_SETTINGS` granting, and (only on Xiaomi/Redmi/POCO hardware) MIUI/HyperOS
 * background-persistence settings.
 */
@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutDirection = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var shizukuStatus by remember { mutableStateOf(ShizukuPermissionHelper.getStatus(context)) }
    var isGrantingViaShizuku by remember { mutableStateOf(false) }
    var isXiaomiBatteryUnrestricted by remember {
        mutableStateOf(OemCompatibilityHelper.isIgnoringBatteryOptimizations(context))
    }

    val adbCommand = remember(context.packageName) {
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }
    val copiedMessage = stringResource(R.string.msg_adb_copied)
    val shizukuGrantedMessage = stringResource(R.string.msg_shizuku_grant_success)
    val shizukuFailedMessage = stringResource(R.string.msg_shizuku_grant_failed)

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

    val executeShizukuGrant: () -> Unit = {
        if (!isGrantingViaShizuku) {
            isGrantingViaShizuku = true
            coroutineScope.launch {
                val success = withContext(Dispatchers.IO) {
                    ShizukuPermissionHelper.grantWriteSecureSettings(context)
                }
                isGrantingViaShizuku = false
                shizukuStatus = ShizukuPermissionHelper.getStatus(context)
                viewModel.refreshPermissions()
                snackbarHostState.showSnackbar(
                    if (success) shizukuGrantedMessage else shizukuFailedMessage,
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
                shizukuStatus = ShizukuPermissionHelper.getStatus(context)
                if (OemCompatibilityHelper.isXiaomiDevice) {
                    isXiaomiBatteryUnrestricted =
                        OemCompatibilityHelper.isIgnoringBatteryOptimizations(context)
                }
            }
        }
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            shizukuStatus = ShizukuPermissionHelper.getStatus(context)
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            shizukuStatus = ShizukuPermissionHelper.getStatus(context)
        }
        val permResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuPermissionHelper.SHIZUKU_REQUEST_CODE) {
                shizukuStatus = ShizukuPermissionHelper.getStatus(context)
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    executeShizukuGrant()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permResultListener)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
                Shizuku.removeRequestPermissionResultListener(permResultListener)
            }
        }
    }

    val totalCount = 7
    val grantedCount = listOf(
        uiState.hasAccessibilityOverlay,
        uiState.hasNotificationListener,
        uiState.hasWriteSettings,
        uiState.hasDndPolicyAccess,
        uiState.hasBluetoothPermission,
        uiState.hasLocationPermission,
        uiState.hasWriteSecureSettings,
    ).count { it }

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "nav_permissions_container"),
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
                    Text(
                        text = stringResource(R.string.title_permissions_screen),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (grantedCount == totalCount) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(
                            text = "$grantedCount / $totalCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (grantedCount == totalCount) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
            .fillMaxSize()
            .then(sharedModifier),
    ) { innerPadding ->
        val cardWidthModifier = Modifier
            .widthIn(max = PERMISSIONS_MAX_WIDTH)
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
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
        ) {
            PermissionsSummaryBanner(
                grantedCount = grantedCount,
                totalCount = totalCount,
                modifier = cardWidthModifier,
            )

            PermissionSectionCard(
                title = stringResource(R.string.section_permissions_core),
                modifier = cardWidthModifier,
            ) {
                PermissionItemRow(
                    icon = Icons.Rounded.Layers,
                    title = stringResource(R.string.label_accessibility_permission),
                    description = stringResource(R.string.msg_accessibility_permission_desc),
                    granted = uiState.hasAccessibilityOverlay,
                    onGrantClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                PermissionItemRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.label_media_permission),
                    description = stringResource(R.string.msg_media_permission_desc),
                    granted = uiState.hasNotificationListener,
                    onGrantClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                )
            }

            WriteSecureSettingsCard(
                granted = uiState.hasWriteSecureSettings,
                shizukuStatus = shizukuStatus,
                isGrantingViaShizuku = isGrantingViaShizuku,
                adbCommand = adbCommand,
                onShizukuAction = {
                    when (shizukuStatus) {
                        ShizukuStatus.READY -> executeShizukuGrant()
                        ShizukuStatus.PERMISSION_REQUIRED -> ShizukuPermissionHelper.requestPermission()
                        ShizukuStatus.NOT_RUNNING,
                        ShizukuStatus.NOT_INSTALLED,
                        -> ShizukuPermissionHelper.openShizukuOrDownload(context)
                    }
                },
                onCopyAdbCommand = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCommand))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(copiedMessage)
                    }
                },
                modifier = cardWidthModifier,
            )

            PermissionSectionCard(
                title = stringResource(R.string.section_permissions_optional),
                modifier = cardWidthModifier,
            ) {
                PermissionItemRow(
                    icon = Icons.Rounded.Brightness6,
                    title = stringResource(R.string.label_write_settings),
                    description = stringResource(R.string.msg_write_settings_desc),
                    granted = uiState.hasWriteSettings,
                    onGrantClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                PermissionItemRow(
                    icon = Icons.Rounded.DoNotDisturbOn,
                    title = stringResource(R.string.label_dnd_policy_access),
                    description = stringResource(R.string.msg_dnd_policy_desc),
                    granted = uiState.hasDndPolicyAccess,
                    onGrantClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                PermissionItemRow(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(R.string.label_bluetooth_permission),
                    description = stringResource(R.string.msg_bluetooth_permission_desc),
                    granted = uiState.hasBluetoothPermission,
                    onGrantClick = {
                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                PermissionItemRow(
                    icon = Icons.Rounded.LocationOn,
                    title = stringResource(R.string.label_location_permission),
                    description = stringResource(R.string.msg_location_permission_desc),
                    granted = uiState.hasLocationPermission,
                    onGrantClick = {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                )
            }

            if (OemCompatibilityHelper.isXiaomiDevice) {
                XiaomiCompatibilityCard(
                    isBatteryUnrestricted = isXiaomiBatteryUnrestricted,
                    onOpenAutoStart = { OemCompatibilityHelper.openXiaomiAutoStart(context) },
                    onOpenBatterySaver = { OemCompatibilityHelper.openBatteryOptimizationSettings(context) },
                    onOpenOtherPermissions = { OemCompatibilityHelper.openXiaomiOtherPermissions(context) },
                    modifier = cardWidthModifier,
                )
            }
        }
    }
}

@Composable
private fun PermissionsSummaryBanner(
    modifier: Modifier = Modifier,
    grantedCount: Int,
    totalCount: Int,
) {
    val allGranted = grantedCount >= totalCount
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (allGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ),
                ) {
                    Icon(
                        imageVector = if (allGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                        contentDescription = null,
                        tint = if (allGranted) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (allGranted) {
                            stringResource(R.string.label_setup_ready)
                        } else {
                            stringResource(R.string.label_permissions_progress, grantedCount, totalCount)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (allGranted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = stringResource(R.string.msg_permissions_overview),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allGranted) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            LinearProgressIndicator(
                progress = { (grantedCount.toFloat() / totalCount.coerceAtLeast(1)).coerceIn(0f, 1f) },
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        }
    }
}

@Composable
private fun PermissionSectionCard(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun PermissionItemRow(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String = stringResource(R.string.action_grant),
    onGrantClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (granted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ),
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (granted) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.label_granted),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        } else {
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onGrantClick()
                },
                shapes = capsuleExpressiveButtonShapes(restRadius = 16.dp, pressedRadius = 8.dp),
            ) {
                Text(
                    text = actionLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WriteSecureSettingsCard(
    modifier: Modifier = Modifier,
    granted: Boolean,
    shizukuStatus: ShizukuStatus,
    isGrantingViaShizuku: Boolean,
    adbCommand: String,
    onShizukuAction: () -> Unit,
    onCopyAdbCommand: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shizukuStatusLabel = when (shizukuStatus) {
        ShizukuStatus.READY -> stringResource(R.string.label_shizuku_ready)
        ShizukuStatus.PERMISSION_REQUIRED -> stringResource(R.string.label_shizuku_needs_auth)
        ShizukuStatus.NOT_RUNNING -> stringResource(R.string.label_shizuku_not_running)
        ShizukuStatus.NOT_INSTALLED -> stringResource(R.string.label_shizuku_not_installed)
    }
    val shizukuButtonLabel = when (shizukuStatus) {
        ShizukuStatus.READY -> stringResource(R.string.action_grant_via_shizuku)
        ShizukuStatus.PERMISSION_REQUIRED -> stringResource(R.string.action_authorize_shizuku)
        ShizukuStatus.NOT_RUNNING -> stringResource(R.string.action_open_shizuku)
        ShizukuStatus.NOT_INSTALLED -> stringResource(R.string.action_get_shizuku)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.label_secure_settings),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (granted) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.label_granted),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.msg_secure_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!granted) {
                // Method 1: Shizuku (One-Tap, No PC)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.title_shizuku_method),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (shizukuStatus == ShizukuStatus.READY) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            ) {
                                Text(
                                    text = shizukuStatusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (shizukuStatus == ShizukuStatus.READY) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.msg_shizuku_method_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onShizukuAction()
                            },
                            enabled = !isGrantingViaShizuku,
                            shapes = capsuleExpressiveButtonShapes(restRadius = 18.dp, pressedRadius = 8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (shizukuStatus == ShizukuStatus.READY ||
                                    shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED
                                ) {
                                    Icons.Rounded.Bolt
                                } else {
                                    Icons.AutoMirrored.Rounded.OpenInNew
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = shizukuButtonLabel)
                        }
                    }
                }

                // Method 2: ADB Command (PC / Wireless Debugging)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.title_adb_method),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.msg_adb_steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = adbCommand,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onCopyAdbCommand()
                            },
                            shapes = capsuleExpressiveButtonShapes(restRadius = 18.dp, pressedRadius = 8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.action_copy_adb))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XiaomiCompatibilityCard(
    modifier: Modifier = Modifier,
    isBatteryUnrestricted: Boolean,
    onOpenAutoStart: () -> Unit,
    onOpenBatterySaver: () -> Unit,
    onOpenOtherPermissions: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.section_xiaomi_compatibility),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(R.string.msg_xiaomi_compatibility_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionItemRow(
                icon = Icons.Rounded.Bolt,
                title = stringResource(R.string.label_xiaomi_autostart),
                description = stringResource(R.string.msg_xiaomi_autostart_desc),
                granted = false,
                actionLabel = stringResource(R.string.action_open_settings),
                onGrantClick = onOpenAutoStart,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            PermissionItemRow(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.label_xiaomi_battery),
                description = stringResource(R.string.msg_xiaomi_battery_desc),
                granted = isBatteryUnrestricted,
                actionLabel = stringResource(R.string.action_open_settings),
                onGrantClick = onOpenBatterySaver,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            PermissionItemRow(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.label_xiaomi_popup_bg),
                description = stringResource(R.string.msg_xiaomi_popup_bg_desc),
                granted = false,
                actionLabel = stringResource(R.string.action_open_settings),
                onGrantClick = onOpenOtherPermissions,
            )
        }
    }
}
