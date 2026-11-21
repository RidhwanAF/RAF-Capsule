package com.raf.capsuleos.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaRouter2
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.LrcLyricsParser
import com.raf.capsuleos.domain.model.LyricLine
import com.raf.capsuleos.domain.model.MediaOutputKind
import com.raf.capsuleos.domain.repository.MediaSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * Observes and controls all active Android [MediaController] instances via [MediaSessionManager],
 * extracting album art colors, app icon badges, and current audio output device routing.
 */
@Singleton
class MediaSessionRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaSessionRepository {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val listenerComponent =
        ComponentName(context, CapsuleNotificationListenerService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionsLock = Any()

    @Volatile
    private var allControllers: List<MediaController> = emptyList()

    @Volatile
    private var selectedTokenKey: String? = null

    private val mediaSessionsFlow = MutableStateFlow<List<CapsuleContent.Media>>(emptyList())

    private val appIconBytesCache = ConcurrentHashMap<String, ByteArray>()
    private val artworkCache = ConcurrentHashMap<String, CachedArtwork>()
    private val onlineLyricsCache = ConcurrentHashMap<String, List<LyricLine>>()
    private val inFlightLyricsKeys = ConcurrentHashMap.newKeySet<String>()
    private val lyricsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var onLyricsReadyCallback: (() -> Unit)? = null

    private data class CachedArtwork(
        val artKey: String,
        val bytes: ByteArray?,
        val primaryArgb: Int,
        val secondaryArgb: Int,
        val lyrics: List<LyricLine>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CachedArtwork

            if (primaryArgb != other.primaryArgb) return false
            if (secondaryArgb != other.secondaryArgb) return false
            if (artKey != other.artKey) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (lyrics != other.lyrics) return false

            return true
        }

        override fun hashCode(): Int {
            var result = primaryArgb
            result = 31 * result + secondaryArgb
            result = 31 * result + artKey.hashCode()
            result = 31 * result + (bytes?.contentHashCode() ?: 0)
            result = 31 * result + lyrics.hashCode()
            return result
        }
    }

    override fun observeActiveMedia(): Flow<CapsuleContent.Media?> =
        observeMediaSessions()
            .map { sessions ->
                val selectedKey = selectedTokenKey
                sessions.firstOrNull { it.isPlaying }
                    ?: selectedKey?.let { key -> sessions.firstOrNull { it.sessionTokenKey == key } }
                    ?: sessions.firstOrNull()
            }
            .distinctUntilChanged()

    override fun observeMediaSessions(): Flow<List<CapsuleContent.Media>> = callbackFlow {
        val registeredCallbacks = LinkedHashMap<String, Pair<MediaController, MediaController.Callback>>()

        fun emitAllSnapshots() {
            val controllers = synchronized(sessionsLock) { allControllers }
            val mapped = controllers.mapNotNull { it.toDomainMedia() }
            mediaSessionsFlow.value = mapped
            trySend(mapped)
        }
        onLyricsReadyCallback = ::emitAllSnapshots

        fun unregisterAll() {
            synchronized(sessionsLock) {
                registeredCallbacks.values.forEach { (controller, cb) ->
                    runCatching { controller.unregisterCallback(cb) }
                }
                registeredCallbacks.clear()
                allControllers = emptyList()
            }
        }

        fun syncControllers(rawControllers: List<MediaController>?) {
            val nextList = rawControllers.orEmpty()
            val ordered = nextList.sortedByDescending { controller ->
                val state = controller.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            }
            val nextKeys = ordered.mapTo(mutableSetOf()) { it.tokenKey() }

            synchronized(sessionsLock) {
                val iterator = registeredCallbacks.entries.iterator()
                while (iterator.hasNext()) {
                    val (key, pair) = iterator.next()
                    if (key !in nextKeys) {
                        runCatching { pair.first.unregisterCallback(pair.second) }
                        artworkCache.remove(key)
                        iterator.remove()
                    }
                }
                ordered.forEach { controller ->
                    val key = controller.tokenKey()
                    if (!registeredCallbacks.containsKey(key)) {
                        val cb = object : MediaController.Callback() {
                            override fun onPlaybackStateChanged(state: PlaybackState?) = emitAllSnapshots()
                            override fun onMetadataChanged(metadata: MediaMetadata?) = emitAllSnapshots()
                            override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = emitAllSnapshots()
                            override fun onSessionDestroyed() = emitAllSnapshots()
                        }
                        controller.registerCallback(cb, mainHandler)
                        registeredCallbacks[key] = controller to cb
                    }
                }
                allControllers = ordered
            }
            emitAllSnapshots()
        }

        val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            syncControllers(controllers)
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
                syncControllers(mediaSessionManager.getActiveSessions(listenerComponent))
            }.onFailure {
                mediaSessionsFlow.value = emptyList()
                trySend(emptyList())
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
                val hasPlaying = synchronized(sessionsLock) {
                    allControllers.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                }
                if (hasPlaying) {
                    emitAllSnapshots()
                }
            }
        }

        tryRegisterSessionListener()

        awaitClose {
            bridgeJob.cancel()
            tickerJob.cancel()
            unregisterAll()
            if (listenerRegistered) {
                runCatching { mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener) }
            }
        }
    }.distinctUntilChanged()

    override fun selectMediaSession(sessionTokenKey: String) {
        selectedTokenKey = sessionTokenKey.takeIf { it.isNotBlank() }
    }

    override fun togglePlayPause(sessionTokenKey: String?) {
        val controller = resolveTargetController(sessionTokenKey) ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    override fun skipNext(sessionTokenKey: String?) {
        resolveTargetController(sessionTokenKey)?.transportControls?.skipToNext()
    }

    override fun skipPrevious(sessionTokenKey: String?) {
        resolveTargetController(sessionTokenKey)?.transportControls?.skipToPrevious()
    }

    override fun seekTo(positionMs: Long, sessionTokenKey: String?) {
        resolveTargetController(sessionTokenKey)?.transportControls?.seekTo(max(0L, positionMs))
    }

    override fun showSystemAudioOutputSwitcher(sessionTokenKey: String?): Boolean {
        // Primary: Trigger Android 15/16/17 SystemUI's publicly exported blurred Volume & Media
        // Output bottom sheet (com.android.systemui/.volume.VolumePanelDialogReceiver) via the
        // standard Settings.Panel.ACTION_VOLUME action.
        val systemUiPanelIntent = Intent(Settings.Panel.ACTION_VOLUME).setPackage(SYSTEM_UI_PACKAGE)
        val hasSystemUiPanelReceiver = runCatching {
            context.packageManager.queryBroadcastReceivers(
                systemUiPanelIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            ).isNotEmpty()
        }.getOrDefault(false)
        if (hasSystemUiPanelReceiver) {
            val sent = runCatching {
                context.sendBroadcast(systemUiPanelIntent)
                true
            }.getOrDefault(false)
            if (sent) return true
        }

        // Secondary: Android 14+ / 16.1+ MediaRouter2 system output switcher (when app is foreground)
        val controller = resolveTargetController(sessionTokenKey)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val shownViaRouter = runCatching {
                val router = MediaRouter2.getInstance(context)
                val token = controller?.sessionToken
                if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 && token != null) {
                    router.showSystemOutputSwitcher(token) || router.showSystemOutputSwitcher()
                } else {
                    router.showSystemOutputSwitcher()
                }
            }.getOrDefault(false)
            if (shownViaRouter) return true
        }

        // Return false so CapsuleOverlayController falls back to launching Settings.Panel.ACTION_VOLUME Activity
        return false
    }

    private fun resolveTargetController(sessionTokenKey: String?): MediaController? {
        val controllers = synchronized(sessionsLock) { allControllers }
        val explicitKey = sessionTokenKey?.takeIf { it.isNotBlank() } ?: selectedTokenKey
        if (!explicitKey.isNullOrBlank()) {
            controllers.firstOrNull { it.tokenKey() == explicitKey }?.let { return it }
        }
        return controllers.firstOrNull {
            val st = it.playbackState?.state
            st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        } ?: controllers.firstOrNull()
    }

    private fun MediaController.tokenKey(): String =
        "$packageName:${sessionToken.hashCode()}"

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
        val tokenId = tokenKey()

        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val rawLyrics = extractRawLyrics(meta, extras)
        val localLyrics = LrcLyricsParser.parse(rawLyrics)
        val lyricsKey = OnlineLyricsFetcher.cacheKey(title, artist)
        val resolvedLyrics = if (localLyrics.isNotEmpty()) {
            localLyrics
        } else {
            val cachedOnline = onlineLyricsCache[lyricsKey]
            if (cachedOnline == null && title.isNotBlank() && inFlightLyricsKeys.add(lyricsKey)) {
                lyricsScope.launch {
                    try {
                        val fetched = OnlineLyricsFetcher.fetch(title, artist, duration)
                        onlineLyricsCache[lyricsKey] = fetched
                        if (fetched.isNotEmpty()) {
                            artworkCache.remove(tokenId)
                            mainHandler.post { onLyricsReadyCallback?.invoke() }
                        }
                    } finally {
                        inFlightLyricsKeys.remove(lyricsKey)
                    }
                }
            }
            cachedOnline.orEmpty()
        }
        val artKey = "$packageName|$title|$artist|${bitmap?.generationId ?: 0}|${resolvedLyrics.size}"
        val cachedArt = artworkCache[tokenId]?.takeIf { it.artKey == artKey } ?: run {
            val (primary, secondary) = BitmapColorExtractor.extractColors(bitmap)
            val created = CachedArtwork(
                artKey = artKey,
                bytes = bitmap?.let { BitmapColorExtractor.toPngBytes(it) },
                primaryArgb = primary,
                secondaryArgb = secondary,
                lyrics = resolvedLyrics,
            )
            artworkCache[tokenId] = created
            created
        }

        val (outputLabel, outputKind) = resolveAudioOutput(playbackInfo)

        return CapsuleContent.Media(
            packageName = packageName,
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            positionMs = computedPosition,
            durationMs = duration,
            artworkBytes = cachedArt.bytes,
            primaryColorArgb = cachedArt.primaryArgb,
            secondaryColorArgb = cachedArt.secondaryArgb,
            sessionTokenKey = tokenId,
            appIconBytes = loadAppIconBytes(packageName),
            audioOutputLabel = outputLabel,
            audioOutputKind = outputKind,
            lyrics = cachedArt.lyrics,
        )
    }

    private fun extractRawLyrics(meta: MediaMetadata, controllerExtras: Bundle?): String? {
        for (key in LYRICS_METADATA_KEYS) {
            val direct = runCatching { meta.getString(key) }.getOrNull()?.trim()
            if (!direct.isNullOrBlank()) return direct
        }
        val desc = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)?.trim()
        if (!desc.isNullOrBlank() && (desc.contains('[') && desc.contains(']') || desc.count { it == '\n' } >= 2)) {
            return desc
        }
        if (controllerExtras != null) {
            for (key in LYRICS_EXTRAS_KEYS) {
                val extra = controllerExtras.getString(key)?.trim()
                if (!extra.isNullOrBlank()) return extra
            }
        }
        return null
    }

    private fun loadAppIconBytes(pkg: String): ByteArray? {
        appIconBytesCache[pkg]?.let { return it }
        val drawable = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        return runCatching {
            val bmp = drawable.toBitmap(width = 48, height = 48, config = Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.toByteArray()
            }
            bmp.recycle()
            appIconBytesCache[pkg] = bytes
            bytes
        }.getOrNull()
    }

    private fun resolveAudioOutput(playbackInfo: MediaController.PlaybackInfo?): Pair<String, MediaOutputKind> {
        if (playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            return "Cast" to MediaOutputKind.CAST
        }
        val mediaAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val routedDevices = runCatching {
            audioManager.getAudioDevicesForAttributes(mediaAttributes)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrNull().orEmpty()

        val bluetooth = routedDevices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST ||
                device.type == AudioDeviceInfo.TYPE_HEARING_AID
        }
        if (bluetooth != null) {
            val name = bluetooth.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Bluetooth"
            return name to MediaOutputKind.BLUETOOTH
        }

        val wired = routedDevices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (wired != null) {
            val name = wired.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Wired audio"
            return name to MediaOutputKind.WIRED
        }

        return "Speaker" to MediaOutputKind.SPEAKER
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

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        val LYRICS_METADATA_KEYS = listOf(
            "android.media.metadata.LYRICS",
            "LYRICS",
            "lyrics",
            "lyric",
            "lrc",
            "com.spotify.music.extra.LYRICS",
            "com.apple.android.music.LYRICS",
        )
        val LYRICS_EXTRAS_KEYS = listOf(
            "lyrics",
            "lyric",
            "lrc",
            "synced_lyrics",
            "android.media.metadata.LYRICS",
        )
    }
}
