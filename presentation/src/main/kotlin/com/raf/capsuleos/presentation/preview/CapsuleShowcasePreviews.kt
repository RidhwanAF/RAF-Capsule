package com.raf.capsuleos.presentation.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raf.capsuleos.domain.model.AppThemeMode
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.BrightnessInfo
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.LrcLyricsParser
import com.raf.capsuleos.domain.model.MediaOutputKind
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SatelliteSource
import com.raf.capsuleos.domain.model.StreamVolumeInfo
import com.raf.capsuleos.domain.model.WeatherConditionKind
import com.raf.capsuleos.domain.model.WeatherSummary
import com.raf.capsuleos.presentation.appsettings.AppSettingsContent
import com.raf.capsuleos.presentation.common.CapsuleTheme
import com.raf.capsuleos.presentation.setup.SetupContent
import com.raf.capsuleos.presentation.setup.SetupUiState
import com.raf.capsuleos.presentation.simulation.SimulationUiState
import com.raf.capsuleos.presentation.simulation.components.CapsulePhoneMockup

private val PREVIEW_CUTOUT = CutoutGeometry(
    holeWidthDp = 32f,
    holeHeightDp = 32f,
    topOffsetDp = 14f,
    statusBarBottomDp = 48f,
    screenWidthDp = 380f,
    holeCenterOffsetXDp = 0f,
    screenTopCornerRadiusDp = 38f,
)

private val PREVIEW_MEDIA = CapsuleContent.Media(
    packageName = "com.spotify.music",
    title = "Midnight City",
    artist = "M83",
    isPlaying = true,
    positionMs = 42_000L,
    durationMs = 243_000L,
    artworkBytes = null,
    primaryColorArgb = 0xFF6C5CE7.toInt(),
    secondaryColorArgb = 0xFF00CEC9.toInt(),
    sessionTokenKey = "preview:media:1",
    audioOutputLabel = "Pixel Buds Pro",
    audioOutputKind = MediaOutputKind.BLUETOOTH,
    lyrics = LrcLyricsParser.parse(
        """
        [00:15.00]Waiting for a ride in the dark
        [00:30.00]The night city grows
        [00:40.00]Look at the horizon glow
        [00:48.00]Waiting in a car
        """.trimIndent(),
    ),
)

private val PREVIEW_SATELLITE_TORCH = CapsuleContent.LeftSatelliteItem(
    source = SatelliteSource.TORCH,
    content = CapsuleContent.Torch(isEnabled = true, strengthLevel = 4, maxStrengthLevel = 5),
    activeIndex = 0,
    totalCount = 2,
)

/** Preview of the main RAF Capsule OS Setup & Configuration dashboard. */
@Preview(
    name = "Home Dashboard",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 820,
)
@Composable
fun PreviewHomeSetupDashboard() {
    CapsuleTheme(appThemeMode = AppThemeMode.DARK, useDynamicColor = false) {
        SetupContent(
            uiState = SetupUiState(
                hasAccessibilityOverlay = true,
                hasNotificationListener = true,
                hasWriteSecureSettings = true,
                hasWriteSettings = true,
                hasDndPolicyAccess = true,
                hasBluetoothPermission = true,
                cutoutGeometry = PREVIEW_CUTOUT,
                detectedPosition = CameraAnchorPosition.CENTER,
                hasHardwareCutout = true,
                preferences = CapsulePreferences(),
            ),
            adbCommand = "adb shell pm grant com.raf.capsuleos android.permission.WRITE_SECURE_SETTINGS",
            snackbarHostState = remember { SnackbarHostState() },
            onGrantAccessibility = {},
            onGrantNotificationListener = {},
            onGrantWriteSettings = {},
            onGrantDndPolicyAccess = {},
            onGrantBluetoothPermission = {},
            onCopyAdbCommand = {},
            onToggleStartPause = {},
            onOpenCustomizationSection = {},
            onOpenSimulation = {},
            onOpenAppSettings = {},
            onAutoDetectCamera = {},
            onUpdatePreferences = {},
            onTestVolume = {},
            onTestAlarm = {},
            onTestUnlock = {},
            onTestNotification = {},
        )
    }
}

/** Preview of the App Settings screen showcasing Dynamic Theme and searchable 14-language selector. */
@Preview(
    name = "App Settings & 14 Languages",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 820,
)
@Composable
fun PreviewAppSettingsLanguages() {
    CapsuleTheme(appThemeMode = AppThemeMode.DARK, useDynamicColor = false) {
        AppSettingsContent(
            appThemeMode = AppThemeMode.DARK,
            useDynamicColor = true,
            activeLocaleTag = "",
            onBack = {},
            onSelectThemeMode = {},
            onToggleDynamicColor = {},
            onSelectLanguageTag = {},
        )
    }
}

/** Preview of the camera battery ring alongside a separated multi-source status bubble. */
@Preview(
    name = "Battery Ring + Separated Bubble",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 200,
)
@Composable
fun PreviewBatteryRingAndSatellite() {
    ShowcaseMockupContainer(
        capsuleState = CapsuleUiState(
            visualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
            content = CapsuleContent.Idle,
            leftSatellite = PREVIEW_SATELLITE_TORCH,
            batteryPercent = 86,
            isCharging = true,
            cutout = PREVIEW_CUTOUT,
            preferences = CapsulePreferences(),
        ),
        viewportHeight = 180.dp,
    )
}

/** Preview of the horizontal media pill paired with the separated status bubble. */
@Preview(
    name = "Horizontal Media Pill + Bubble",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 200,
)
@Composable
fun PreviewHorizontalMediaPill() {
    ShowcaseMockupContainer(
        capsuleState = CapsuleUiState(
            visualState = CapsuleVisualState.STATE2_HORIZONTAL_PILL,
            content = PREVIEW_MEDIA,
            leftSatellite = PREVIEW_SATELLITE_TORCH,
            mediaSessions = listOf(PREVIEW_MEDIA),
            batteryPercent = 86,
            cutout = PREVIEW_CUTOUT,
            preferences = CapsulePreferences(),
        ),
        viewportHeight = 180.dp,
    )
}

/** Preview of the expanded Media Player card with audio output chip, visualizer, and synced lyrics. */
@Preview(
    name = "Expanded Media Player",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 340,
)
@Composable
fun PreviewExpandedMediaPlayer() {
    ShowcaseMockupContainer(
        capsuleState = CapsuleUiState(
            visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
            content = PREVIEW_MEDIA,
            mediaSessions = listOf(PREVIEW_MEDIA),
            batteryPercent = 86,
            isNeuralPulseActive = true,
            cutout = PREVIEW_CUTOUT,
            preferences = CapsulePreferences(),
        ),
        viewportHeight = 320.dp,
    )
}

/** Preview of the tall scrollable notification card with directional gradient lines and inline reply. */
@Preview(
    name = "Expanded Long Notification",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 380,
)
@Composable
fun PreviewExpandedNotificationScroll() {
    ShowcaseMockupContainer(
        capsuleState = CapsuleUiState(
            visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
            content = CapsuleContent.NotificationAlert(
                key = "preview:notif:1",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Alex Mercer",
                text = "Hey! Are we still on for the Android 17 demo review at 7 PM?\n" +
                    "I tested the new capsule physics and horizontal swipe-to-dismiss gesture — feels super fluid!\n" +
                    "Long messages expand dynamically with directional top & bottom primary scroll indicators.\n" +
                    "Inline photos, GIFs, and WebP stickers render right inside the expanded card.\n" +
                    "Swipe left or right to dismiss, or tap Reply below.",
                iconBytes = null,
                timestampMs = 1_700_000_000_000L,
                actions = listOf(
                    CapsuleContent.NotificationActionItem(
                        actionId = 1,
                        label = "Reply",
                        supportsInlineReply = true,
                        replyHint = "Type a reply…",
                    ),
                ),
            ),
            batteryPercent = 86,
            cutout = PREVIEW_CUTOUT,
            preferences = CapsulePreferences(),
        ),
        viewportHeight = 360.dp,
    )
}

/** Preview of the Now Brief quick hub card with weather, brightness, ringer, and notification counter. */
@Preview(
    name = "Now Brief Quick Hub",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 360,
)
@Composable
fun PreviewNowBriefQuickHub() {
    ShowcaseMockupContainer(
        capsuleState = CapsuleUiState(
            visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
            content = CapsuleContent.NowBrief(
                batteryPercent = 86,
                isCharging = true,
                torch = CapsuleContent.Torch(isEnabled = true, strengthLevel = 3, maxStrengthLevel = 5),
                volume = CapsuleContent.Volume(
                    activeStream = AudioStreamType.MEDIA,
                    streams = listOf(
                        StreamVolumeInfo(AudioStreamType.MEDIA, 10, 15),
                        StreamVolumeInfo(AudioStreamType.RING, 5, 7),
                    ),
                    ringerMode = RingerModeState.NORMAL,
                ),
                nextAlarmLabel = "07:00",
                brightness = BrightnessInfo(levelPercent = 76, isAuto = true, canWrite = true),
                weather = WeatherSummary(
                    temperatureCelsius = 22,
                    conditionLabel = "Clear",
                    conditionKind = WeatherConditionKind.CLEAR,
                ),
                activeNotificationCount = 3,
            ),
            batteryPercent = 86,
            isCharging = true,
            cutout = PREVIEW_CUTOUT,
            preferences = CapsulePreferences(),
        ),
        viewportHeight = 340.dp,
    )
}

/** Preview of the incoming call auto-expanded capsule card. */
@Preview(
    name = "Incoming Call Alert",
    showBackground = true,
    backgroundColor = 0xFF09090B,
    widthDp = 412,
    heightDp = 300,
)
@Composable
fun PreviewIncomingCallAlert() {
    ShowcaseMockupContainer(
        capsuleState = CapsuleUiState(
            visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
            content = CapsuleContent.CallAlert(
                key = "preview:call:1",
                callerName = "Elena Vance",
                callStatus = "Incoming call • Mobile",
                packageName = "com.google.android.dialer",
                appName = "Phone",
                isIncoming = true,
                hasAnswerAction = true,
                hasDeclineAction = true,
            ),
            batteryPercent = 86,
            cutout = PREVIEW_CUTOUT,
            preferences = CapsulePreferences(),
        ),
        viewportHeight = 280.dp,
    )
}

@Composable
private fun ShowcaseMockupContainer(
    modifier: Modifier = Modifier,
    capsuleState: CapsuleUiState,
    viewportHeight: Dp,
) {
    CapsuleTheme(appThemeMode = AppThemeMode.DARK, useDynamicColor = false) {
        Box(modifier = modifier.padding(10.dp)) {
            CapsulePhoneMockup(
                uiState = SimulationUiState(
                    capsuleUiState = capsuleState,
                    isMediaPlaying = capsuleState.content is CapsuleContent.Media,
                    isTorchOn = capsuleState.leftSatellite?.source == SatelliteSource.TORCH,
                    isCharging = capsuleState.isCharging,
                ),
                viewportHeight = viewportHeight,
                onMockupWidthMeasured = {},
                onResolveExpandTarget = { capsuleState.content },
                onResolveCollapseTarget = {
                    CapsuleVisualState.STATE1_CUTOUT_ANCHOR to CapsuleContent.Idle
                },
                onEvent = {},
                onTogglePlayPause = {},
                onSkipNext = {},
                onSkipPrevious = {},
                onSeekTo = { _, _ -> },
                onToggleTorch = {},
                onSetTorchStrength = {},
                onSetStreamVolume = { _, _ -> },
                onCycleRingerMode = {},
                onSetRingerMode = {},
                onSetBrightnessPercent = {},
                onToggleAutoBrightness = {},
                onLockScreenButton = {},
                onWakeFromLock = {},
                onOpenNotification = {},
                onTriggerNotificationAction = { _, _, _ -> },
                onDismissNotification = {},
                onOpenMediaApp = {},
                onOpenClock = {},
            )
        }
    }
}
