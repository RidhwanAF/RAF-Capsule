package com.raf.capsuleos.domain.model

/**
 * Events consumed by [com.raf.capsuleos.domain.coordinator.IslandStateCoordinator].
 */
sealed interface CapsuleEvent {
    /** Active media session changed or updated; null clears media content. */
    data class MediaUpdated(val media: CapsuleContent.Media?) : CapsuleEvent

    /** Ongoing live activity notification (e.g. timer, stopwatch) updated or cleared. */
    data class LiveActivityUpdated(val liveActivity: CapsuleContent.LiveActivity?) : CapsuleEvent

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

    /** User completed long-hold (Circle-to-Search wave) to expand into State 3 card. */
    data object ExpandToFull : CapsuleEvent

    /** User interacted inside expanded State 3 card (keeps card open by resetting timer). */
    data object UserInteractedInCard : CapsuleEvent

    /** User slid horizontally across the capsule to rotate active stack content (-1 prev, +1 next). */
    data class CycleContent(val direction: Int) : CapsuleEvent

    /** User tapped outside, swiped up, or requested collapse from State 3. */
    data object CollapseCapsule : CapsuleEvent

    /** Screen orientation changed between portrait and landscape. */
    data class OrientationChanged(val isLandscape: Boolean) : CapsuleEvent

    /** Screen power / Always-On Display state changed (hides capsule on AOD/screen off). */
    data class ScreenInactiveChanged(val isScreenInactive: Boolean) : CapsuleEvent

    /** Screen brightness level or adaptive mode changed in system settings. */
    data class BrightnessChanged(val brightness: BrightnessInfo) : CapsuleEvent

    /** Weather summary updated from periodic fetch or At-a-Glance signal. */
    data class WeatherUpdated(val weather: WeatherSummary?) : CapsuleEvent

    /** Active notification badge count updated for Now Brief header/at-a-glance row. */
    data class ActiveNotificationCountUpdated(val count: Int) : CapsuleEvent
}

/**
 * One-shot navigation effects emitted by [com.raf.capsuleos.domain.coordinator.IslandStateCoordinator].
 */
sealed interface CapsuleEffect {
    /** Launches an application or notification content intent associated with a capsule card/pill. */
    data class LaunchApp(
        val packageName: String,
        val notificationKey: String? = null,
    ) : CapsuleEffect
}
