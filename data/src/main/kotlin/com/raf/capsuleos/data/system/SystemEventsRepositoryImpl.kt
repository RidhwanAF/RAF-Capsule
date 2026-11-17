package com.raf.capsuleos.data.system

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import com.raf.capsuleos.data.media.CapsuleNotificationListenerService
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.UnlockMethod
import com.raf.capsuleos.domain.model.WeatherConditionKind
import com.raf.capsuleos.domain.model.WeatherSummary
import com.raf.capsuleos.domain.repository.SystemEventsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * Observes system battery level, charging state, ongoing live activities,
 * device unlock (`ACTION_USER_PRESENT` + unprivileged biometric/camera unlock signals),
 * and alarm schedule changes.
 */
@Singleton
class SystemEventsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SystemEventsRepository {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val biometricManager = context.getSystemService(BiometricManager::class.java)
    private val cameraManager = context.getSystemService(CameraManager::class.java)

    private val hasFingerprintFeature: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    private val hasFaceFeature: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)

    override fun observeSystemEvents(): Flow<CapsuleEvent> = callbackFlow {
        val (initialPercent, initialCharging) = currentBatteryState()
        trySend(CapsuleEvent.BatteryUpdated(initialPercent, initialCharging))

        val frontCameraIds = resolveFrontCameraIds()
        var lastScreenOnElapsedMs = 0L
        var lastScreenOffElapsedMs = 0L
        var isFrontCamHeldForKeyguard = false
        var lockscreenFrontCamOpenElapsedMs = 0L
        var lockscreenFrontCamCloseElapsedMs = 0L

        val cameraAvailabilityCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) {
                if (cameraId in frontCameraIds && keyguardManager.isKeyguardLocked) {
                    isFrontCamHeldForKeyguard = true
                    lockscreenFrontCamOpenElapsedMs = SystemClock.elapsedRealtime()
                    lockscreenFrontCamCloseElapsedMs = 0L
                }
            }

            override fun onCameraAvailable(cameraId: String) {
                if (cameraId in frontCameraIds && isFrontCamHeldForKeyguard) {
                    isFrontCamHeldForKeyguard = false
                    lockscreenFrontCamCloseElapsedMs = SystemClock.elapsedRealtime()
                }
            }
        }

        if (frontCameraIds.isNotEmpty()) {
            runCatching {
                cameraManager?.registerAvailabilityCallback(context.mainExecutor, cameraAvailabilityCallback)
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED,
                    Intent.ACTION_POWER_CONNECTED,
                    Intent.ACTION_POWER_DISCONNECTED,
                    -> {
                        val (percent, charging) = currentBatteryState()
                        trySend(CapsuleEvent.BatteryUpdated(percent, charging))
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        lastScreenOffElapsedMs = SystemClock.elapsedRealtime()
                        lastScreenOnElapsedMs = 0L
                        isFrontCamHeldForKeyguard = false
                        lockscreenFrontCamOpenElapsedMs = 0L
                        lockscreenFrontCamCloseElapsedMs = 0L
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        lastScreenOnElapsedMs = SystemClock.elapsedRealtime()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        val nowMs = SystemClock.elapsedRealtime()
                        val unlockMethod = inferUnlockMethod(
                            nowMs = nowMs,
                            lastScreenOnElapsedMs = lastScreenOnElapsedMs,
                            lastScreenOffElapsedMs = lastScreenOffElapsedMs,
                            isFrontCamHeldForKeyguard = isFrontCamHeldForKeyguard,
                            lockscreenFrontCamOpenElapsedMs = lockscreenFrontCamOpenElapsedMs,
                            lockscreenFrontCamCloseElapsedMs = lockscreenFrontCamCloseElapsedMs,
                        )
                        isFrontCamHeldForKeyguard = false
                        lockscreenFrontCamOpenElapsedMs = 0L
                        lockscreenFrontCamCloseElapsedMs = 0L
                        trySend(CapsuleEvent.DeviceUnlocked(readUnlockSummary(unlockMethod)))
                    }
                    AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                        trySend(CapsuleEvent.AlarmUpdated(readAlarmState()))
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            if (frontCameraIds.isNotEmpty()) {
                runCatching { cameraManager?.unregisterAvailabilityCallback(cameraAvailabilityCallback) }
            }
        }
    }

    override fun currentNextAlarmLabel(): String? {
        val alarm = readAlarmState()
        return if (alarm.isSet) alarm.formattedTime else null
    }

    override fun currentBatteryState(): Pair<Int, Boolean> {
        val percent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)
        return percent to batteryManager.isCharging
    }

    @Volatile
    private var cachedWeather: WeatherSummary? = null

    @Volatile
    private var lastWeatherAttemptMs: Long = 0L

    override fun currentLiveActivity(): CapsuleContent.LiveActivity? =
        CapsuleNotificationListenerService.activeLiveActivity.value

    override fun currentWeatherSummary(): WeatherSummary? = cachedWeather

    override fun observeWeatherSummary(): Flow<WeatherSummary?> = flow {
        emit(cachedWeather)
        while (currentCoroutineContext().isActive) {
            val nowMs = System.currentTimeMillis()
            if (cachedWeather == null || nowMs - lastWeatherAttemptMs >= WEATHER_REFRESH_INTERVAL_MS) {
                lastWeatherAttemptMs = nowMs
                fetchWeatherBestEffort()?.let { fresh ->
                    cachedWeather = fresh
                    emit(fresh)
                }
            }
            val waitMinutes = if (cachedWeather == null) WEATHER_RETRY_MINUTES else WEATHER_REFRESH_MINUTES
            delay(waitMinutes.minutes)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun resolveFrontCameraIds(): Set<String> =
        runCatching {
            val manager = cameraManager ?: return emptySet()
            manager.cameraIdList.filterTo(mutableSetOf()) { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
        }.getOrDefault(emptySet())

    private fun canAuthenticate(authenticators: Int): Boolean =
        runCatching {
            biometricManager?.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)

    private fun inferUnlockMethod(
        nowMs: Long,
        lastScreenOnElapsedMs: Long,
        lastScreenOffElapsedMs: Long,
        isFrontCamHeldForKeyguard: Boolean,
        lockscreenFrontCamOpenElapsedMs: Long,
        lockscreenFrontCamCloseElapsedMs: Long,
    ): UnlockMethod {
        val faceAuthActiveOrRecent = hasFaceFeature &&
            canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) &&
            (
                (isFrontCamHeldForKeyguard && nowMs - lockscreenFrontCamOpenElapsedMs <= FACE_HOLD_WINDOW_MS) ||
                    (lockscreenFrontCamCloseElapsedMs > 0L && nowMs - lockscreenFrontCamCloseElapsedMs <= FACE_CLOSE_WINDOW_MS) ||
                    (lockscreenFrontCamOpenElapsedMs > 0L && lockscreenFrontCamCloseElapsedMs == 0L &&
                        nowMs - lockscreenFrontCamOpenElapsedMs <= FACE_OPEN_UNCLOSED_WINDOW_MS)
                )
        if (faceAuthActiveOrRecent) return UnlockMethod.FACE

        val quickFingerprintWake = hasFingerprintFeature &&
            keyguardManager.isKeyguardSecure &&
            canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) &&
            lockscreenFrontCamOpenElapsedMs == 0L &&
            (
                (lastScreenOnElapsedMs > 0L && nowMs - lastScreenOnElapsedMs <= FAST_FINGERPRINT_WAKE_WINDOW_MS) ||
                    (lastScreenOnElapsedMs == 0L && lastScreenOffElapsedMs > 0L)
                )
        if (quickFingerprintWake) return UnlockMethod.FINGERPRINT

        return UnlockMethod.GENERIC
    }

    private fun readUnlockSummary(unlockMethod: UnlockMethod): CapsuleContent.Unlock {
        val alarm = readAlarmState()
        return CapsuleContent.Unlock(
            unlockMethod = unlockMethod,
            nextAlarmLabel = if (alarm.isSet) alarm.formattedTime else null,
        )
    }

    private fun readAlarmState(): CapsuleContent.Alarm {
        val nextAlarm = alarmManager.nextAlarmClock
        if (nextAlarm == null) {
            return CapsuleContent.Alarm(
                triggerTimeMillis = null,
                formattedTime = "",
                isSet = false,
            )
        }
        val formatted = DateFormat.getTimeFormat(context).format(Date(nextAlarm.triggerTime))
        return CapsuleContent.Alarm(
            triggerTimeMillis = nextAlarm.triggerTime,
            formattedTime = formatted,
            isSet = true,
        )
    }

    private fun fetchWeatherBestEffort(): WeatherSummary? {
        val (lat, lon) = resolveGeoCoordinates() ?: return null
        val forecastUrl =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        val body = httpGetJson(forecastUrl) ?: return null
        return runCatching {
            val current = JSONObject(body).optJSONObject("current") ?: return null
            val tempC = current.optDouble("temperature_2m", Double.NaN)
            if (tempC.isNaN()) return null
            val wmoCode = current.optInt("weather_code", -1)
            val (label, kind) = mapWmoWeather(wmoCode)
            WeatherSummary(
                temperatureCelsius = tempC.roundToInt(),
                conditionLabel = label,
                conditionKind = kind,
            )
        }.getOrNull()
    }

    private fun resolveGeoCoordinates(): Pair<Double, Double>? {
        val body = httpGetJson("https://get.geojs.io/v1/ip/geo.json") ?: return null
        return runCatching {
            val json = JSONObject(body)
            val lat = json.optString("latitude").toDoubleOrNull() ?: return null
            val lon = json.optString("longitude").toDoubleOrNull() ?: return null
            lat to lon
        }.getOrNull()
    }

    private fun httpGetJson(urlString: String): String? = runCatching {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun mapWmoWeather(code: Int): Pair<String, WeatherConditionKind> = when (code) {
        0 -> "Clear" to WeatherConditionKind.CLEAR
        1, 2, 3 -> "Cloudy" to WeatherConditionKind.CLOUDY
        45, 48 -> "Fog" to WeatherConditionKind.FOG
        in 51..67, in 80..82 -> "Rain" to WeatherConditionKind.RAIN
        in 71..77, 85, 86 -> "Snow" to WeatherConditionKind.SNOW
        95, 96, 99 -> "Storm" to WeatherConditionKind.STORM
        else -> "Cloudy" to WeatherConditionKind.CLOUDY
    }

    private companion object {
        const val FACE_HOLD_WINDOW_MS = 3_500L
        const val FACE_CLOSE_WINDOW_MS = 1_200L
        const val FACE_OPEN_UNCLOSED_WINDOW_MS = 1_800L
        const val FAST_FINGERPRINT_WAKE_WINDOW_MS = 1_600L
        const val HTTP_TIMEOUT_MS = 5_000
        const val WEATHER_REFRESH_MINUTES = 30
        const val WEATHER_RETRY_MINUTES = 2
        const val WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1_000L
    }
}
