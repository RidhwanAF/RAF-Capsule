package com.raf.capsuleos.domain.model

import kotlin.math.roundToInt

/**
 * Pure domain representation of the payload rendered inside the capsule.
 */
sealed interface CapsuleContent {
    /** Idle state when no media, torch, live activity, or charging pill is active. */
    data object Idle : CapsuleContent

    /**
     * "Now Brief" summary card shown when holding the idle 1:1 camera ring.
     *
     * @property batteryPercent Current battery level (0..100).
     * @property isCharging Whether the device is currently charging.
     * @property torch Current flashlight state.
     * @property volume Current audio stream snapshot.
     * @property nextAlarmLabel Optional formatted next alarm string.
     * @property liveActivity Optional ongoing timer or live notification.
     */
    data class NowBrief(
        val batteryPercent: Int,
        val isCharging: Boolean,
        val torch: Torch,
        val volume: Volume,
        val nextAlarmLabel: String?,
        val liveActivity: LiveActivity? = null,
        val brightness: BrightnessInfo = BrightnessInfo(),
        val weather: WeatherSummary? = null,
        val activeNotificationCount: Int = 0,
    ) : CapsuleContent

    /**
     * Ongoing live activity or timer notification (e.g. Clock timer, stopwatch, active call).
     *
     * @property key Unique notification key.
     * @property title Short title or app label.
     * @property statusText Formatted timer countdown, chronometer, or status badge.
     * @property packageName Optional owning package used to launch the app directly on tap.
     */
    data class LiveActivity(
        val key: String,
        val title: String,
        val statusText: String,
        val packageName: String? = null,
    ) : CapsuleContent

    /**
     * Executable Android notification action surfaced inside an expanded notification card.
     *
     * @property actionId Stable action index on the cached system notification.
     * @property label Button caption (e.g., "Reply", "Mark as read").
     * @property supportsInlineReply True when this action accepts [android.app.RemoteInput] reply text.
     * @property replyHint Optional input placeholder label.
     */
    data class NotificationActionItem(
        val actionId: Int,
        val label: String,
        val supportsInlineReply: Boolean = false,
        val replyHint: String? = null,
    )

    /**
     * Active non-silent incoming notification surfaced in the side pill and expandable card.
     *
     * @property key Unique Android StatusBarNotification key.
     * @property packageName Source application package name.
     * @property appName Human-readable source application name.
     * @property title Notification headline.
     * @property text Notification message body preview.
     * @property iconBytes Optional compressed PNG icon bytes for the app/notification icon.
     * @property timestampMs Post epoch timestamp in milliseconds.
     * @property actions Optional interactive actions (Reply, Mark read, etc.) shown after expand.
     */
    data class NotificationAlert(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val text: String,
        val iconBytes: ByteArray?,
        val timestampMs: Long,
        val actions: List<NotificationActionItem> = emptyList(),
    ) : CapsuleContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NotificationAlert) return false
            return key == other.key &&
                packageName == other.packageName &&
                appName == other.appName &&
                title == other.title &&
                text == other.text &&
                iconBytes.contentEquals(other.iconBytes) &&
                timestampMs == other.timestampMs &&
                actions == other.actions
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + packageName.hashCode()
            result = 31 * result + appName.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + text.hashCode()
            result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
            result = 31 * result + timestampMs.hashCode()
            result = 31 * result + actions.hashCode()
            return result
        }
    }

    /**
     * Compact 1-side pill payload displayed when holding or plugging in during charging.
     *
     * @property batteryPercent Current battery percentage (0..100).
     */
    data class Charging(
        val batteryPercent: Int,
    ) : CapsuleContent

    /**
     * Side-pill payload showing battery icon left and percentage right when holding on battery.
     *
     * @property batteryPercent Current battery percentage (0..100).
     */
    data class BatteryPeek(
        val batteryPercent: Int,
    ) : CapsuleContent

    /**
     * Active media playback metadata and transport capabilities.
     *
     * @property packageName Source media package identifier.
     * @property title Track title.
     * @property artist Artist or subtitle text.
     * @property isPlaying Whether playback is actively running.
     * @property positionMs Current playback position in milliseconds.
     * @property durationMs Total track duration in milliseconds.
     * @property artworkBytes Optional compressed PNG/JPEG artwork bytes for presentation decoding.
     * @property primaryColorArgb Dominant ARGB color sampled from artwork.
     * @property secondaryColorArgb Accent ARGB color sampled from artwork.
     */
    data class Media(
        val packageName: String,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val artworkBytes: ByteArray?,
        val primaryColorArgb: Int,
        val secondaryColorArgb: Int,
    ) : CapsuleContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Media) return false
            return packageName == other.packageName &&
                title == other.title &&
                artist == other.artist &&
                isPlaying == other.isPlaying &&
                positionMs == other.positionMs &&
                durationMs == other.durationMs &&
                artworkBytes.contentEquals(other.artworkBytes) &&
                primaryColorArgb == other.primaryColorArgb &&
                secondaryColorArgb == other.secondaryColorArgb
        }

        override fun hashCode(): Int {
            var result = packageName.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + artist.hashCode()
            result = 31 * result + isPlaying.hashCode()
            result = 31 * result + positionMs.hashCode()
            result = 31 * result + durationMs.hashCode()
            result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
            result = 31 * result + primaryColorArgb
            result = 31 * result + secondaryColorArgb
            return result
        }
    }

    /**
     * Camera flashlight state including Android 13+ variable strength level.
     *
     * @property isEnabled Whether the torch is currently illuminated.
     * @property strengthLevel Current brightness level (1..[maxStrengthLevel]).
     * @property maxStrengthLevel Maximum supported hardware brightness level.
     */
    data class Torch(
        val isEnabled: Boolean,
        val strengthLevel: Int,
        val maxStrengthLevel: Int,
    ) : CapsuleContent {
        /** Flashlight intensity mapped smoothly to `1..100%` (`100` on fixed-level hardware). */
        val brightnessPercent: Int
            get() = if (maxStrengthLevel > 1) {
                ((strengthLevel.toFloat() / maxStrengthLevel.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(1, 100)
            } else {
                100
            }
    }

    /**
     * System audio stream levels displayed in the volume capsule.
     *
     * @property activeStream Stream that triggered the event.
     * @property streams Snapshot of adjustable streams (Media, Ring, Alarm).
     */
    data class Volume(
        val activeStream: AudioStreamType,
        val streams: List<StreamVolumeInfo>,
        val ringerMode: RingerModeState = RingerModeState.NORMAL,
    ) : CapsuleContent

    /**
     * Next scheduled alarm information.
     *
     * @property triggerTimeMillis Epoch timestamp in milliseconds, or null if cleared.
     * @property formattedTime Human-readable alarm time string.
     * @property isSet Whether an alarm is currently armed.
     */
    data class Alarm(
        val triggerTimeMillis: Long?,
        val formattedTime: String,
        val isSet: Boolean,
    ) : CapsuleContent

    /**
     * Transient device unlock greeting displaying unlock status or biometric method.
     *
     * @property unlockMethod Detected authentication method used when unlocking.
     * @property nextAlarmLabel Optional formatted next alarm string.
     */
    data class Unlock(
        val unlockMethod: UnlockMethod = UnlockMethod.GENERIC,
        val nextAlarmLabel: String? = null,
    ) : CapsuleContent
}

/** Authentication modality resolved or inferred on keyguard dismissal. */
enum class UnlockMethod {
    GENERIC,
    FINGERPRINT,
    FACE,
}

/** Supported audio streams observed by the hardware volume and mixer capsule. */
enum class AudioStreamType {
    MEDIA,
    RING,
    ALARM,
    NOTIFICATION,
    VOICE,
}

/**
 * Immutable volume level snapshot for a single audio stream.
 *
 * @property streamType Target audio stream.
 * @property current Current volume index.
 * @property max Maximum volume index for this stream.
 */
data class StreamVolumeInfo(
    val streamType: AudioStreamType,
    val current: Int,
    val max: Int,
)
