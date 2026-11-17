package com.raf.capsuleos.data.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.repository.MediaSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * Observes and controls the top active Android [MediaController] via [MediaSessionManager].
 */
@Singleton
class MediaSessionRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaSessionRepository {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent =
        ComponentName(context, CapsuleNotificationListenerService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeController: MediaController? = null

    private var cachedArtworkKey: String? = null
    private var cachedArtworkBytes: ByteArray? = null
    private var cachedPrimaryColor: Int = 0xFF64B5F6.toInt()
    private var cachedSecondaryColor: Int = 0xFFBA68C8.toInt()

    override fun observeActiveMedia(): Flow<CapsuleContent.Media?> = callbackFlow {
        var currentController: MediaController? = null
        var callback: MediaController.Callback? = null

        fun detachCurrentController() {
            val controller = currentController
            val cb = callback
            if (controller != null && cb != null) {
                runCatching { controller.unregisterCallback(cb) }
            }
            currentController = null
            callback = null
            activeController = null
        }

        fun emitSnapshot(controller: MediaController?) {
            trySend(controller?.toDomainMedia())
        }

        fun bindTopController(controllers: List<MediaController>?) {
            val preferred = controllers.orEmpty().firstOrNull { controller ->
                val state = controller.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            } ?: controllers.orEmpty().firstOrNull()

            if (preferred?.sessionToken == currentController?.sessionToken) {
                emitSnapshot(currentController)
                return
            }

            detachCurrentController()
            if (preferred == null) {
                emitSnapshot(null)
                return
            }

            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    emitSnapshot(preferred)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    emitSnapshot(preferred)
                }

                override fun onSessionDestroyed() {
                    detachCurrentController()
                    emitSnapshot(null)
                }
            }
            preferred.registerCallback(cb, mainHandler)
            currentController = preferred
            callback = cb
            activeController = preferred
            emitSnapshot(preferred)
        }

        val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindTopController(controllers)
        }

        var listenerRegistered = false
        fun tryRegisterSessionListener() {
            if (listenerRegistered) return
            runCatching {
                mediaSessionManager.addOnActiveSessionsChangedListener(
                    sessionListener,
                    listenerComponent,
                    mainHandler,
                )
                listenerRegistered = true
                bindTopController(mediaSessionManager.getActiveSessions(listenerComponent))
            }.onFailure {
                trySend(null)
            }
        }

        val bridgeJob = launch {
            CapsuleNotificationListenerService.isConnected.collectLatest { connected ->
                if (connected) {
                    tryRegisterSessionListener()
                }
            }
        }

        val tickerJob = launch {
            while (true) {
                delay(500.milliseconds)
                val controller = currentController
                val state = controller?.playbackState?.state
                if (controller != null && state == PlaybackState.STATE_PLAYING) {
                    emitSnapshot(controller)
                }
            }
        }

        tryRegisterSessionListener()

        awaitClose {
            bridgeJob.cancel()
            tickerJob.cancel()
            detachCurrentController()
            if (listenerRegistered) {
                runCatching { mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener) }
            }
        }
    }.distinctUntilChanged()

    override fun togglePlayPause() {
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    override fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    override fun skipPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    override fun seekTo(positionMs: Long) {
        activeController?.transportControls?.seekTo(max(0L, positionMs))
    }

    private fun MediaController.toDomainMedia(): CapsuleContent.Media? {
        val meta = metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val duration = max(0L, meta.getLong(MediaMetadata.METADATA_KEY_DURATION))
        val playback = playbackState
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING ||
            playback?.state == PlaybackState.STATE_BUFFERING
        val computedPosition = computePosition(playback, duration)
        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artKey = "$packageName|$title|$artist|${bitmap?.generationId ?: 0}"
        if (artKey != cachedArtworkKey) {
            cachedArtworkKey = artKey
            val (primary, secondary) = BitmapColorExtractor.extractColors(bitmap)
            cachedPrimaryColor = primary
            cachedSecondaryColor = secondary
            cachedArtworkBytes = bitmap?.let { BitmapColorExtractor.toPngBytes(it) }
        }

        return CapsuleContent.Media(
            packageName = packageName,
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            positionMs = computedPosition,
            durationMs = duration,
            artworkBytes = cachedArtworkBytes,
            primaryColorArgb = cachedPrimaryColor,
            secondaryColorArgb = cachedSecondaryColor,
        )
    }

    private fun computePosition(playbackState: PlaybackState?, durationMs: Long): Long {
        if (playbackState == null) return 0L
        var position = playbackState.position
        if (playbackState.state == PlaybackState.STATE_PLAYING) {
            val timeDelta = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
            if (timeDelta > 0L) {
                position += (timeDelta * playbackState.playbackSpeed).toLong()
            }
        }
        return if (durationMs > 0L) position.coerceIn(0L, durationMs) else max(0L, position)
    }
}
