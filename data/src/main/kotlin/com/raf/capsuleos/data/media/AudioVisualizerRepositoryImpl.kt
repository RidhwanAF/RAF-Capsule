package com.raf.capsuleos.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.raf.capsuleos.domain.audio.FftBeatAnalyzer
import com.raf.capsuleos.domain.model.AudioBeatState
import com.raf.capsuleos.domain.repository.AudioVisualizerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10

/**
 * First-party Android [Visualizer] (`audioSessionId = 0`) implementation fusing:
 * - **FFT Frequency Spectrum** (`SCALING_MODE_NORMALIZED` 3-band bass/mid/high + spectral flux)
 * - **Hardware Peak/RMS Amplitude** (`MEASUREMENT_MODE_PEAK_RMS` compensated via [AudioManager.getStreamVolumeDb])
 * - **Self-healing background Looper watchdog** + [AudioManager.AudioPlaybackCallback] that automatically
 *   recovers when OEM Audio HALs (`OffloadEffect` / `EffectProxy`) migrate output `ioHandle`s during
 *   pause/play, track skips, seekbar scrubs, or touch haptics.
 */
@Singleton
class AudioVisualizerRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioVisualizerRepository {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val workerThread = HandlerThread("capsule-audio-vis").apply {
        isDaemon = true
        start()
    }
    private val workerHandler = Handler(workerThread.looper)
    private val workerExecutor = Executor { runnable -> workerHandler.post(runnable) }

    private val analyzer = FftBeatAnalyzer()
    private val peakRms = Visualizer.MeasurementPeakRms()

    private val _beatState = MutableStateFlow(AudioBeatState.Silent)
    override val beatState: StateFlow<AudioBeatState> = _beatState.asStateFlow()

    private var visualizer: Visualizer? = null
    private var attachRequested = false

    @Volatile
    private var playbackActive = false
    private var listenersRegistered = false

    @Volatile
    private var lastCallbackElapsedMs = 0L

    @Volatile
    private var lastSignalElapsedMs = 0L
    private var lastAttachElapsedMs = 0L
    private var lastLogElapsedMs = 0L
    private var consecutiveInvalidFrames = 0

    private data class LoudnessMetrics(
        val measurementValid: Boolean,
        val gain: Float,
        val crestFactor: Float,
        val volDb: Float,
        val srcDb: Float,
    )

    private val audioModeListener = AudioManager.OnModeChangedListener { mode ->
        if (!attachRequested) return@OnModeChangedListener
        if (isCallAudioMode(mode)) {
            releaseVisualizerOnWorker(clearState = true)
        } else if (visualizer == null) {
            attachVisualizerOnWorker(preserveAnalyzer = false)
        }
    }

    private val playbackConfigCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            if (!attachRequested || !playbackActive) return
            scheduleProactiveCheckOnWorker(PROACTIVE_RECOVERY_DELAY_MS)
        }
    }

    private val proactiveCheckRunnable = Runnable {
        performHealthCheckOnWorker()
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!attachRequested) return
            performHealthCheckOnWorker()
            workerHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun setCaptureSession(shouldAttach: Boolean, isPlaying: Boolean) {
        workerHandler.post {
            val wasPlaying = playbackActive
            playbackActive = isPlaying
            if (!isPlaying) {
                analyzer.reset()
                _beatState.value = AudioBeatState.Silent
            }

            if (attachRequested != shouldAttach) {
                attachRequested = shouldAttach
                if (shouldAttach) {
                    registerSystemListenersOnWorker()
                    if (!isCallAudioMode(audioManager.mode)) {
                        attachVisualizerOnWorker(preserveAnalyzer = false)
                    }
                    scheduleWatchdogOnWorker()
                } else {
                    workerHandler.removeCallbacks(watchdogRunnable)
                    workerHandler.removeCallbacks(proactiveCheckRunnable)
                    unregisterSystemListenersOnWorker()
                    releaseVisualizerOnWorker(clearState = true)
                }
                return@post
            }

            if (shouldAttach) {
                if (isCallAudioMode(audioManager.mode)) {
                    if (visualizer != null) {
                        releaseVisualizerOnWorker(clearState = true)
                    }
                } else if (visualizer == null) {
                    attachVisualizerOnWorker(preserveAnalyzer = false)
                } else if (!wasPlaying && isPlaying) {
                    val now = SystemClock.elapsedRealtime()
                    lastSignalElapsedMs = now
                    if (now - lastCallbackElapsedMs > STALL_CALLBACK_THRESHOLD_MS) {
                        reattachVisualizerOnWorker()
                    } else {
                        scheduleProactiveCheckOnWorker(PROACTIVE_RECOVERY_DELAY_MS)
                    }
                }
                scheduleWatchdogOnWorker()
            }
        }
    }

    override fun notifyMediaTransportHint() {
        workerHandler.post {
            if (!attachRequested) return@post
            lastSignalElapsedMs = SystemClock.elapsedRealtime()
            scheduleProactiveCheckOnWorker(PROACTIVE_RECOVERY_DELAY_MS)
        }
    }

    private fun scheduleWatchdogOnWorker() {
        workerHandler.removeCallbacks(watchdogRunnable)
        workerHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    private fun scheduleProactiveCheckOnWorker(delayMs: Long) {
        workerHandler.removeCallbacks(proactiveCheckRunnable)
        workerHandler.postDelayed(proactiveCheckRunnable, delayMs)
    }

    private fun performHealthCheckOnWorker() {
        if (!attachRequested) return
        if (isCallAudioMode(audioManager.mode)) {
            if (visualizer != null) {
                releaseVisualizerOnWorker(clearState = true)
            }
            return
        }
        if (visualizer == null) {
            attachVisualizerOnWorker(preserveAnalyzer = false)
            return
        }
        if (!playbackActive) return

        val now = SystemClock.elapsedRealtime()
        val sinceAttach = now - lastAttachElapsedMs
        val callbackStalled = sinceAttach > STALL_CALLBACK_THRESHOLD_MS &&
            (now - lastCallbackElapsedMs) > STALL_CALLBACK_THRESHOLD_MS
        val musicAudible = audioManager.isMusicActive &&
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        val deadStream = musicAudible &&
            sinceAttach > DEAD_SIGNAL_THRESHOLD_MS &&
            (now - lastSignalElapsedMs) > DEAD_SIGNAL_THRESHOLD_MS

        if (callbackStalled || deadStream) {
            Log.i(TAG, "Self-healing stalled Visualizer (stalled=$callbackStalled, deadStream=$deadStream)")
            reattachVisualizerOnWorker()
        }
    }

    private fun reattachVisualizerOnWorker() {
        releaseVisualizerOnWorker(clearState = false)
        attachVisualizerOnWorker(preserveAnalyzer = true)
    }

    private fun registerSystemListenersOnWorker() {
        if (listenersRegistered) return
        runCatching {
            audioManager.addOnModeChangedListener(workerExecutor, audioModeListener)
            audioManager.registerAudioPlaybackCallback(playbackConfigCallback, workerHandler)
            listenersRegistered = true
        }
    }

    private fun unregisterSystemListenersOnWorker() {
        if (!listenersRegistered) return
        runCatching { audioManager.removeOnModeChangedListener(audioModeListener) }
        runCatching { audioManager.unregisterAudioPlaybackCallback(playbackConfigCallback) }
        listenersRegistered = false
    }

    private fun attachVisualizerOnWorker(preserveAnalyzer: Boolean) {
        if (visualizer != null) return
        if (!hasAudioPermission()) {
            _beatState.value = AudioBeatState.Silent
            return
        }
        runCatching {
            val range = Visualizer.getCaptureSizeRange()
            val minSize = range.getOrElse(0) { 256 }
            val maxSize = range.getOrElse(1) { 1024 }
            val preferredSize = TARGET_CAPTURE_SIZE.coerceIn(minSize, maxSize)
            val maxRate = Visualizer.getMaxCaptureRate()
            val targetRate = TARGET_CAPTURE_RATE_MHZ.coerceAtMost(maxRate)

            if (preserveAnalyzer) {
                analyzer.softRecover()
            } else {
                analyzer.reset()
            }
            val now = SystemClock.elapsedRealtime()
            lastAttachElapsedMs = now
            lastCallbackElapsedMs = now
            lastSignalElapsedMs = now
            consecutiveInvalidFrames = 0

            val created = Visualizer(GLOBAL_OUTPUT_MIX_SESSION_ID).apply {
                enabled = false
                captureSize = preferredSize
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                runCatching { measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS }
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            vis: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) = Unit

                        override fun onFftDataCapture(
                            vis: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (vis !== visualizer) return
                            val tickMs = SystemClock.elapsedRealtime()
                            lastCallbackElapsedMs = tickMs
                            if (!playbackActive) {
                                consecutiveInvalidFrames = 0
                                if (_beatState.value.isLive) {
                                    _beatState.value = AudioBeatState.Silent
                                }
                                return
                            }
                            if (fft == null || fft.isEmpty()) return
                            val hasFftEnergy = hasNonZeroFftEnergy(fft)
                            val metrics = readTrackLoudnessMetrics(vis, hasFftEnergy)

                            if (!metrics.measurementValid && !hasFftEnergy) {
                                consecutiveInvalidFrames++
                                if (consecutiveInvalidFrames >= INVALID_FRAME_RECOVERY_COUNT &&
                                    tickMs - lastAttachElapsedMs > FAST_REATTACH_COOLDOWN_MS
                                ) {
                                    consecutiveInvalidFrames = 0
                                    Log.i(TAG, "Fast-healing broken HAL Visualizer sub-effect (invalid peakRms + zero FFT)")
                                    reattachVisualizerOnWorker()
                                }
                                return
                            }

                            consecutiveInvalidFrames = 0
                            if (hasFftEnergy || (metrics.measurementValid && metrics.gain > 0.01f)) {
                                lastSignalElapsedMs = tickMs
                            }
                            val state = analyzer.analyze(
                                fft = fft,
                                samplingRateMilliHz = samplingRate,
                                loudnessGain = metrics.gain,
                                transientCrest = metrics.crestFactor,
                            )
                            _beatState.value = state
                            logThrottled(state, metrics)
                        }
                    },
                    targetRate,
                    false,
                    true,
                )
            }
            visualizer = created
            created.enabled = true
            Log.i(TAG, "Visualizer attached (size=$preferredSize, rate=${targetRate}mHz, seamless=$preserveAnalyzer)")
        }.onFailure { err ->
            Log.w(TAG, "Visualizer attach failed: ${err.message}")
            releaseVisualizerOnWorker(clearState = !preserveAnalyzer)
        }
    }

    private fun hasNonZeroFftEnergy(fft: ByteArray): Boolean {
        val limit = fft.size.coerceAtMost(256)
        return (2 until limit).any { idx -> fft[idx].toInt() != 0 }
    }

    /**
     * Reads true 32-bit float audio output loudness & peak-over-RMS crest factor via
     * [Visualizer.getMeasurementPeakRms], compensated by [AudioManager.getStreamVolumeDb].
     */
    private fun readTrackLoudnessMetrics(vis: Visualizer?, hasFftEnergy: Boolean): LoudnessMetrics {
        if (vis == null) {
            return LoudnessMetrics(measurementValid = false, gain = 0f, crestFactor = 0f, volDb = 0f, srcDb = -96f)
        }
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (curVol <= 0) {
            return LoudnessMetrics(measurementValid = true, gain = 0f, crestFactor = 0f, volDb = -96f, srcDb = -96f)
        }

        val status = runCatching { vis.getMeasurementPeakRms(peakRms) }.getOrDefault(Visualizer.ERROR)
        if (status != Visualizer.SUCCESS) {
            // Only use fallback gain if real non-zero FFT bins arrived; otherwise flag broken HAL sub-effect
            val fallbackGain = if (hasFftEnergy) 0.55f else 0f
            return LoudnessMetrics(
                measurementValid = false,
                gain = fallbackGain,
                crestFactor = 0f,
                volDb = 0f,
                srcDb = if (hasFftEnergy) -20f else -96f,
            )
        }

        val rmsDb = peakRms.mRms / 100f
        val peakDb = peakRms.mPeak / 100f
        if (peakDb <= -90f && rmsDb <= -90f) {
            return LoudnessMetrics(measurementValid = true, gain = 0f, crestFactor = 0f, volDb = 0f, srcDb = -96f)
        }

        val volDb = resolveCurrentMusicVolumeDb(curVol)
        val blendedMeasuredDb = rmsDb * 0.60f + peakDb * 0.40f
        val sourceDb = blendedMeasuredDb - volDb
        val gain = ((sourceDb - MIN_SOURCE_DB) / (MAX_SOURCE_DB - MIN_SOURCE_DB)).coerceIn(0f, 1f)
        val crestFactor = (((peakDb - rmsDb) - CREST_FLOOR_DB) / CREST_SPAN_DB).coerceIn(0f, 1f)
        return LoudnessMetrics(
            measurementValid = true,
            gain = gain,
            crestFactor = crestFactor,
            volDb = volDb,
            srcDb = sourceDb,
        )
    }

    private fun resolveCurrentMusicVolumeDb(curVol: Int): Float {
        val activeDeviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                audioManager.getAudioDevicesForAttributes(MUSIC_ATTRIBUTES).firstOrNull()?.type
            }.getOrNull()
        } else {
            null
        } ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

        val hwDb = runCatching {
            audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, curVol, activeDeviceType)
        }.getOrNull()

        if (hwDb != null && hwDb.isFinite() && hwDb <= 0f && hwDb > -90f) {
            return hwDb
        }

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val ratio = (curVol.toFloat() / maxVol.toFloat()).coerceIn(0.04f, 1f)
        return 20f * log10(ratio)
    }

    private fun logThrottled(state: AudioBeatState, metrics: LoudnessMetrics) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogElapsedMs >= 2000L) {
            lastLogElapsedMs = now
            Log.d(
                TAG,
                String.format(
                    Locale.US,
                    "rms=%dmB peak=%dmB volDb=%.1f srcDb=%.1f gain=%.2f crest=%.2f bass=%.2f energy=%.2f pulse=%.2f",
                    peakRms.mRms,
                    peakRms.mPeak,
                    metrics.volDb,
                    metrics.srcDb,
                    metrics.gain,
                    metrics.crestFactor,
                    state.bass,
                    state.energy,
                    state.beatPulse,
                ),
            )
        }
    }

    private fun releaseVisualizerOnWorker(clearState: Boolean) {
        val current = visualizer
        visualizer = null
        if (current != null) {
            runCatching { current.enabled = false }
            runCatching { current.setDataCaptureListener(null, 0, false, false) }
            runCatching { current.release() }
        }
        if (clearState) {
            analyzer.reset()
            _beatState.value = AudioBeatState.Silent
        }
    }

    private fun isCallAudioMode(mode: Int): Boolean =
        mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE ||
            mode == AudioManager.MODE_CALL_SCREENING

    private companion object {
        const val TAG = "CapsuleAudioBeat"
        const val GLOBAL_OUTPUT_MIX_SESSION_ID = 0
        const val TARGET_CAPTURE_SIZE = 1024
        const val TARGET_CAPTURE_RATE_MHZ = 20_000
        const val MIN_SOURCE_DB = -42f
        const val MAX_SOURCE_DB = -6f
        const val CREST_FLOOR_DB = 4.5f
        const val CREST_SPAN_DB = 11.5f
        const val WATCHDOG_INTERVAL_MS = 350L
        const val PROACTIVE_RECOVERY_DELAY_MS = 220L
        const val STALL_CALLBACK_THRESHOLD_MS = 380L
        const val DEAD_SIGNAL_THRESHOLD_MS = 750L
        const val INVALID_FRAME_RECOVERY_COUNT = 3
        const val FAST_REATTACH_COOLDOWN_MS = 320L
        val MUSIC_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
