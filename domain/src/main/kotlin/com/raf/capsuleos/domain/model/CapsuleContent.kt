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
     * @property iconBytes Optional compressed PNG icon bytes for the originating app or notification icon.
     */
    data class LiveActivity(
        val key: String,
        val title: String,
        val statusText: String,
        val subtitle: String = "",
        val packageName: String? = null,
        val iconBytes: ByteArray? = null,
        val showChronometer: Boolean = false,
        val chronometerBaseEpochMs: Long = 0L,
        val isCountdown: Boolean = false,
    ) : CapsuleContent {
        /**
         * Resolves live formatted duration (`MM:SS` or `H:MM:SS`) against [nowEpochMs] when backed
         * by an active Android chronometer, or falls back to static [statusText].
         */
        fun resolveDisplayStatus(nowEpochMs: Long): String {
            if (!showChronometer || chronometerBaseEpochMs <= 0L) return statusText
            val totalSec = if (isCountdown) {
                ((chronometerBaseEpochMs - nowEpochMs).coerceAtLeast(0L) + 999L) / 1000L
            } else {
                (nowEpochMs - chronometerBaseEpochMs).coerceAtLeast(0L) / 1000L
            }
            val hours = totalSec / 3600L
            val minutes = (totalSec % 3600L) / 60L
            val seconds = totalSec % 60L
            val minStr = minutes.toString().padStart(2, '0')
            val secStr = seconds.toString().padStart(2, '0')
            return if (hours > 0L) "$hours:$minStr:$secStr" else "$minStr:$secStr"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LiveActivity) return false
            return key == other.key &&
                title == other.title &&
                statusText == other.statusText &&
                subtitle == other.subtitle &&
                packageName == other.packageName &&
                iconBytes.contentEquals(other.iconBytes) &&
                showChronometer == other.showChronometer &&
                chronometerBaseEpochMs == other.chronometerBaseEpochMs &&
                isCountdown == other.isCountdown
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + statusText.hashCode()
            result = 31 * result + subtitle.hashCode()
            result = 31 * result + (packageName?.hashCode() ?: 0)
            result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
            result = 31 * result + showChronometer.hashCode()
            result = 31 * result + chronometerBaseEpochMs.hashCode()
            result = 31 * result + isCountdown.hashCode()
            return result
        }
    }

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
        val attachmentImageBytes: ByteArray? = null,
        val attachmentMimeType: String? = null,
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
                actions == other.actions &&
                attachmentImageBytes.contentEquals(other.attachmentImageBytes) &&
                attachmentMimeType == other.attachmentMimeType
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
            result = 31 * result + (attachmentImageBytes?.contentHashCode() ?: 0)
            result = 31 * result + (attachmentMimeType?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Persistent resting summary pill shown when no higher-priority active pill is displayed
     * and unread notifications exist in the system shade.
     *
     * @property count Number of active clearable notifications currently in the shade.
     */
    data class NotificationSummary(
        val count: Int,
    ) : CapsuleContent

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
     * @property sessionTokenKey Stable identifier distinguishing multiple simultaneous media controllers.
     * @property appIconBytes Optional compressed PNG icon bytes for the media source app.
     * @property audioOutputLabel Active audio route label (e.g. "Phone speaker", "Pixel Buds").
     * @property audioOutputKind Normalized output route category for icon rendering.
     * @property lyrics Optional synced or multi-line lyrics extracted from media metadata.
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
        val sessionTokenKey: String = "",
        val appIconBytes: ByteArray? = null,
        val audioOutputLabel: String = "",
        val audioOutputKind: MediaOutputKind = MediaOutputKind.SPEAKER,
        val lyrics: List<LyricLine> = emptyList(),
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
                secondaryColorArgb == other.secondaryColorArgb &&
                sessionTokenKey == other.sessionTokenKey &&
                appIconBytes.contentEquals(other.appIconBytes) &&
                audioOutputLabel == other.audioOutputLabel &&
                audioOutputKind == other.audioOutputKind &&
                lyrics == other.lyrics
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
            result = 31 * result + sessionTokenKey.hashCode()
            result = 31 * result + (appIconBytes?.contentHashCode() ?: 0)
            result = 31 * result + audioOutputLabel.hashCode()
            result = 31 * result + audioOutputKind.hashCode()
            result = 31 * result + lyrics.hashCode()
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

    /**
     * Connected Bluetooth peripheral alert and status card payload.
     *
     * @property name Device friendly name (e.g. "Pixel Buds Pro").
     * @property address MAC or hardware address identifier.
     * @property batteryPercent Reported peripheral battery percentage (0..100), or null when unavailable.
     * @property category Coarse Bluetooth accessory category for icon selection.
     * @property isConnected True while the accessory link is active.
     */
    data class BluetoothDevice(
        val name: String,
        val address: String,
        val batteryPercent: Int? = null,
        val category: BluetoothDeviceCategory = BluetoothDeviceCategory.OTHER,
        val isConnected: Boolean = true,
    ) : CapsuleContent

    /**
     * Incoming or ongoing phone / VoIP call payload with answer and decline affordances.
     *
     * @property key Notification key identifying this call notification.
     * @property callerName Caller display name or number.
     * @property callStatus Status subtitle (e.g., "Incoming call", "Ongoing • 01:24").
     * @property packageName Owning dialer or VoIP app package name.
     * @property appName Human-readable owning app name.
     * @property appIconBytes Optional compressed PNG bytes for caller/app icon.
     * @property isIncoming True while ringing / awaiting user action.
     * @property hasAnswerAction True when the notification exposes an Answer PendingIntent.
     * @property hasDeclineAction True when the notification exposes a Decline or Hang Up PendingIntent.
     * @property isOngoing True while call is active in progress.
     * @property startedAtMs Chronometer base or notification timestamp in milliseconds.
     */
    data class CallAlert(
        val key: String,
        val callerName: String,
        val callStatus: String,
        val packageName: String,
        val appName: String,
        val appIconBytes: ByteArray? = null,
        val isIncoming: Boolean = true,
        val hasAnswerAction: Boolean = false,
        val hasDeclineAction: Boolean = false,
        val isOngoing: Boolean = false,
        val startedAtMs: Long = 0L,
    ) : CapsuleContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CallAlert) return false
            return key == other.key &&
                callerName == other.callerName &&
                callStatus == other.callStatus &&
                packageName == other.packageName &&
                appName == other.appName &&
                appIconBytes.contentEquals(other.appIconBytes) &&
                isIncoming == other.isIncoming &&
                hasAnswerAction == other.hasAnswerAction &&
                hasDeclineAction == other.hasDeclineAction &&
                isOngoing == other.isOngoing &&
                startedAtMs == other.startedAtMs
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + callerName.hashCode()
            result = 31 * result + callStatus.hashCode()
            result = 31 * result + packageName.hashCode()
            result = 31 * result + appName.hashCode()
            result = 31 * result + (appIconBytes?.contentHashCode() ?: 0)
            result = 31 * result + isIncoming.hashCode()
            result = 31 * result + hasAnswerAction.hashCode()
            result = 31 * result + hasDeclineAction.hashCode()
            result = 31 * result + isOngoing.hashCode()
            result = 31 * result + startedAtMs.hashCode()
            return result
        }
    }

    /**
     * Mobile Wi-Fi Hotspot status surfaced primarily via the detached left satellite bubble or card.
     *
     * @property isEnabled True while the Wi-Fi AP / tethering hotspot is active.
     * @property connectedClients Number of connected tethered clients when reported by system, or 0.
     * @property ssid Optional configured hotspot SSID name.
     */
    data class Hotspot(
        val isEnabled: Boolean,
        val connectedClients: Int = 0,
        val ssid: String? = null,
    ) : CapsuleContent

    /**
     * Active system status mode surfaced in the detached left satellite bubble and expandable card.
     *
     * @property kind System mode category (Airplane, DND, Battery Saver, VPN, Silent/Vibrate, Data Saver).
     * @property isActive Whether the system mode is currently enabled.
     * @property detailLabel Optional short detail caption (e.g. "Silent", "Vibrate").
     */
    data class SystemStatus(
        val kind: SystemStatusKind,
        val isActive: Boolean,
        val detailLabel: String = "",
    ) : CapsuleContent

    /**
     * Icon-only detached satellite indicator positioned to the left of the camera cutout.
     *
     * @property source Prioritized source identity across hardware, live activity, and system modes.
     * @property content Underlying domain payload opened smoothly when expanding the satellite.
     */
    data class LeftSatelliteItem(
        val source: SatelliteSource,
        val content: CapsuleContent,
        val activeIndex: Int = 0,
        val totalCount: Int = 1,
    )
}

/** Active system toggle/mode categories surfaced in the separated left satellite bubble. */
enum class SystemStatusKind {
    AIRPLANE_MODE,
    DO_NOT_DISTURB,
    BATTERY_SAVER,
    VPN,
    RINGER_MUTED,
    DATA_SAVER,
}

/** Source category shown in the separated left satellite circle. */
enum class SatelliteSource {
    TORCH,
    HOTSPOT,
    LIVE_ACTIVITY,
    AIRPLANE_MODE,
    DO_NOT_DISTURB,
    BATTERY_SAVER,
    VPN,
    RINGER_MUTED,
    DATA_SAVER,
}

/** Physical or wireless audio output device category for active media playback. */
enum class MediaOutputKind {
    SPEAKER,
    BLUETOOTH,
    WIRED,
    CAST,
    UNKNOWN,
}

/** Coarse Bluetooth peripheral device category. */
enum class BluetoothDeviceCategory {
    HEADPHONES,
    SPEAKER,
    WATCH,
    CAR,
    COMPUTER,
    PHONE,
    OTHER,
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
 * Single synced or plain lyric line extracted from media metadata.
 *
 * @property timestampMs Millisecond offset for synced LRC lines, or `-1L` when unsynced.
 * @property text Display text of the lyric line.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

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
