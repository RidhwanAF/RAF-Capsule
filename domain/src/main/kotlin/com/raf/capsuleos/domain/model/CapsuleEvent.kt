package com.raf.capsuleos.domain.model

/**
 * Events consumed by [com.raf.capsuleos.domain.coordinator.IslandStateCoordinator].
 */
sealed interface CapsuleEvent {
    /** Active media session changed or updated; null clears media content. */
    data class MediaUpdated(val media: CapsuleContent.Media?) : CapsuleEvent

    /** Full ordered snapshot of active media playback sessions for multi-player switching. */
    data class MediaSessionsUpdated(val sessions: List<CapsuleContent.Media>) : CapsuleEvent

    /** User switched between multiple active media players on the expanded Media card. */
    data class SelectMediaSession(val index: Int) : CapsuleEvent

    /** Ongoing live activity notification (e.g. timer, stopwatch) updated or cleared. */
    data class LiveActivityUpdated(val liveActivity: CapsuleContent.LiveActivity?) : CapsuleEvent

    /** Bluetooth device connection or battery update. Null clears the transient alert. */
    data class BluetoothDeviceUpdated(val device: CapsuleContent.BluetoothDevice?) : CapsuleEvent

    /** Mobile hotspot toggle / connected clients state changed. */
    data class HotspotUpdated(val hotspot: CapsuleContent.Hotspot) : CapsuleEvent

    /** System status mode (Airplane, DND, Battery Saver, VPN, Silent/Vibrate, Data Saver) changed. */
    data class SystemStatusUpdated(val status: CapsuleContent.SystemStatus) : CapsuleEvent

    /** Incoming or ongoing call notification updated; null clears active call state. */
    data class CallUpdated(val call: CapsuleContent.CallAlert?) : CapsuleEvent

    /** User tapped or held the detached left satellite indicator bubble. */
    data object ExpandLeftSatellite : CapsuleEvent

    /** User swiped horizontally on the detached left satellite indicator to cycle active items. */
    data class CycleLeftSatellite(val direction: Int) : CapsuleEvent

    /** Non-silent notification alert posted by an app. */
    data class NotificationPosted(val notification: CapsuleContent.NotificationAlert) : CapsuleEvent

    /** Active notification removed/dismissed by user or system. */
    data class NotificationRemoved(val key: String) : CapsuleEvent

    /** Battery percentage or charging state changed. */
    data class BatteryUpdated(val percent: Int, val isCharging: Boolean) : CapsuleEvent

    /** Camera torch state changed. */
    data class TorchUpdated(val torch: CapsuleContent.Torch) : CapsuleEvent

    /** System volume changed for an audio stream. */
    data class VolumeChanged(val volume: CapsuleContent.Volume) : CapsuleEvent

    /** Next scheduled alarm state changed. */
    data class AlarmUpdated(val alarm: CapsuleContent.Alarm) : CapsuleEvent

    /** User unlocked the device (`ACTION_USER_PRESENT`). */
    data class DeviceUnlocked(val unlock: CapsuleContent.Unlock) : CapsuleEvent

    /** Initial press-and-hold stage showing charge/battery side peek during soft wave haptic. */
    data object HoldPeekStarted : CapsuleEvent

    /** User released hold before reaching full State 3 expansion threshold. */
    data object HoldPeekReleased : CapsuleEvent

    /** User tapped the capsule in State 1, State 2, or State 3 card body. */
    data object TapCapsule : CapsuleEvent

    /** User double-tapped the capsule or expanded card with the active content context. */
    data object DoubleTapCapsule : CapsuleEvent

    /** User completed long-hold (Circle-to-Search wave) to trigger the configured hold action. */
    data object LongHoldCapsule : CapsuleEvent

    /** User completed long-hold (Circle-to-Search wave) to expand into State 3 card directly. */
    data object ExpandToFull : CapsuleEvent

    /** Explicitly expands the Now Brief dashboard into State 3 regardless of background pill/media state. */
    data object ExpandNowBrief : CapsuleEvent

    /** User interacted inside expanded State 3 card (keeps card open by resetting timer). */
    data object UserInteractedInCard : CapsuleEvent

    /** User opened or closed the inline reply keyboard/field inside an expanded notification card. */
    data class ReplyFocusChanged(val isReplying: Boolean) : CapsuleEvent

    /** User toggled the synced lyrics viewport inside the expanded Media Player card. */
    data class MediaLyricsExpandedChanged(val isLyricsVisible: Boolean) : CapsuleEvent

    /** User slid horizontally across the capsule to rotate active stack content (-1 prev, +1 next). */
    data class CycleContent(val direction: Int) : CapsuleEvent

    /** User tapped outside, swiped up, or requested collapse from State 3. */
    data object CollapseCapsule : CapsuleEvent

    /** Screen orientation changed between portrait and landscape. */
    data class OrientationChanged(val isLandscape: Boolean) : CapsuleEvent

    /** Screen power / Always-On Display state changed (hides capsule on AOD/screen off). */
    data class ScreenInactiveChanged(val isScreenInactive: Boolean) : CapsuleEvent

    /** Device keyguard / lockscreen locked state changed. */
    data class KeyguardLockedChanged(val isLocked: Boolean) : CapsuleEvent

    /** System Quick Settings / notification shade open state changed. */
    data class QuickSettingsChanged(val isQsOpen: Boolean) : CapsuleEvent

    /** Immersive fullscreen app state (gaming, video playback) changed across any orientation. */
    data class FullscreenChanged(val isFullscreen: Boolean) : CapsuleEvent

    /** Screen brightness level or adaptive mode changed in system settings. */
    data class BrightnessChanged(val brightness: BrightnessInfo) : CapsuleEvent

    /** Weather summary updated from periodic fetch or At-a-Glance signal. */
    data class WeatherUpdated(val weather: WeatherSummary?) : CapsuleEvent

    /** Active notification badge count updated for Now Brief header/at-a-glance row. */
    data class ActiveNotificationCountUpdated(val count: Int) : CapsuleEvent
}

/**
 * One-shot navigation and system action effects emitted by [com.raf.capsuleos.domain.coordinator.IslandStateCoordinator].
 */
sealed interface CapsuleEffect {
    /** Launches an application or notification content intent associated with a capsule card/pill. */
    data class LaunchApp(
        val packageName: String,
        val notificationKey: String? = null,
    ) : CapsuleEffect

    /** Locks the device screen using accessibility global action. */
    data object LockScreen : CapsuleEffect

    /** Opens the system notification shade panel using accessibility global action. */
    data object OpenNotificationShade : CapsuleEffect

    /** Toggles device camera flashlight hardware state. */
    data object ToggleTorch : CapsuleEffect

    /** Toggles play/pause on the active media playback session. */
    data object ToggleMediaPlayPause : CapsuleEffect

    /** Switches coordinator active media session token so controls dispatch to the selected player. */
    data class SwitchMediaSession(val sessionTokenKey: String) : CapsuleEffect
}
