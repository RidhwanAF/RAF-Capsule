package com.raf.capsuleos.data.hardware

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.StreamVolumeInfo
import com.raf.capsuleos.domain.repository.VolumeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes and modifies Media, Ring, and Alarm audio stream volumes via [AudioManager]
 * and targeted `Settings.System` volume URI observation.
 */
@Singleton
class VolumeRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VolumeRepository {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun observeVolumeChanges(): Flow<CapsuleContent.Volume> = callbackFlow {
        var lastSnapshot = currentSnapshot()

        fun emitIfChanged(preferredStream: AudioStreamType?) {
            val probe = currentSnapshot()
            val diffStream = probe.streams.firstOrNull { info ->
                lastSnapshot.streams.firstOrNull { it.streamType == info.streamType }?.current != info.current
            }?.streamType
            val ringerChanged = probe.ringerMode != lastSnapshot.ringerMode
            if (diffStream == null && !ringerChanged) return

            val next = probe.copy(activeStream = preferredStream ?: diffStream ?: lastSnapshot.activeStream)
            lastSnapshot = next
            trySend(next)
        }

        val volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    VOLUME_CHANGED_ACTION -> {
                        val streamInt = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                        emitIfChanged(streamInt.toAudioStreamType())
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION,
                    INTERNAL_RINGER_MODE_CHANGED_ACTION,
                    -> emitIfChanged(null)
                }
            }
        }

        val volumeUriObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val key = uri?.lastPathSegment ?: return
                if (!key.startsWith("volume_")) return
                val stream = when {
                    key.startsWith("volume_ring") -> AudioStreamType.RING
                    key.startsWith("volume_alarm") -> AudioStreamType.ALARM
                    key.startsWith("volume_notification") -> AudioStreamType.NOTIFICATION
                    key.startsWith("volume_voice") -> AudioStreamType.VOICE
                    else -> AudioStreamType.MEDIA
                }
                emitIfChanged(stream)
            }
        }

        ContextCompat.registerReceiver(
            context,
            volumeReceiver,
            IntentFilter().apply {
                addAction(VOLUME_CHANGED_ACTION)
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(INTERNAL_RINGER_MODE_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeUriObserver,
        )

        awaitClose {
            runCatching { context.unregisterReceiver(volumeReceiver) }
            runCatching { context.contentResolver.unregisterContentObserver(volumeUriObserver) }
        }
    }.distinctUntilChanged()

    override fun currentSnapshot(activeStream: AudioStreamType): CapsuleContent.Volume =
        CapsuleContent.Volume(
            activeStream = activeStream,
            streams = AudioStreamType.entries.map { stream ->
                val androidStream = stream.toAndroidStream()
                StreamVolumeInfo(
                    streamType = stream,
                    current = audioManager.getStreamVolume(androidStream),
                    max = audioManager.getStreamMaxVolume(androidStream),
                )
            },
            ringerMode = readRingerMode(),
        )

    override fun setStreamVolume(streamType: AudioStreamType, value: Int) {
        val androidStream = streamType.toAndroidStream()
        val max = audioManager.getStreamMaxVolume(androidStream)
        audioManager.setStreamVolume(androidStream, value.coerceIn(0, max), 0)
    }

    override fun cycleRingerMode(): RingerModeState {
        val nextMode = when (readRingerMode()) {
            RingerModeState.NORMAL -> RingerModeState.VIBRATE
            RingerModeState.VIBRATE -> {
                if (canChangeSilentMode()) RingerModeState.SILENT else RingerModeState.NORMAL
            }
            RingerModeState.SILENT -> RingerModeState.NORMAL
        }
        return setRingerMode(nextMode)
    }

    override fun setRingerMode(mode: RingerModeState): RingerModeState {
        if (mode == RingerModeState.SILENT && !canChangeSilentMode()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
            return readRingerMode()
        }
        val androidMode = when (mode) {
            RingerModeState.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            RingerModeState.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            RingerModeState.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
        runCatching { audioManager.ringerMode = androidMode }
        return readRingerMode()
    }

    override fun canChangeSilentMode(): Boolean =
        runCatching { notificationManager?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)

    private fun readRingerMode(): RingerModeState = when (audioManager.ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE -> RingerModeState.VIBRATE
        AudioManager.RINGER_MODE_SILENT -> RingerModeState.SILENT
        else -> RingerModeState.NORMAL
    }

    private fun AudioStreamType.toAndroidStream(): Int = when (this) {
        AudioStreamType.MEDIA -> AudioManager.STREAM_MUSIC
        AudioStreamType.RING -> AudioManager.STREAM_RING
        AudioStreamType.ALARM -> AudioManager.STREAM_ALARM
        AudioStreamType.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        AudioStreamType.VOICE -> AudioManager.STREAM_VOICE_CALL
    }

    private fun Int.toAudioStreamType(): AudioStreamType = when (this) {
        AudioManager.STREAM_RING -> AudioStreamType.RING
        AudioManager.STREAM_ALARM -> AudioStreamType.ALARM
        AudioManager.STREAM_NOTIFICATION -> AudioStreamType.NOTIFICATION
        AudioManager.STREAM_VOICE_CALL,
        STREAM_BLUETOOTH_SCO,
        -> AudioStreamType.VOICE
        else -> AudioStreamType.MEDIA
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val INTERNAL_RINGER_MODE_CHANGED_ACTION = "android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION"
        const val STREAM_BLUETOOTH_SCO = 6
    }
}
