package com.raf.capsuleos.data.system

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import com.raf.capsuleos.data.media.CapsuleNotificationListenerService
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.SystemStatusKind
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
 * device unlock (`ACTION_USER_PRESENT`), and alarm schedule changes.
 */
@Singleton
class SystemEventsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SystemEventsRepository {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun observeSystemEvents(): Flow<CapsuleEvent> = callbackFlow {
        val (initialPercent, initialCharging) = currentBatteryState()
        trySend(CapsuleEvent.BatteryUpdated(initialPercent, initialCharging))
        trySend(CapsuleEvent.HotspotUpdated(currentHotspotState()))
        trySend(CapsuleEvent.SystemStatusUpdated(readAirplaneModeStatus()))
        trySend(CapsuleEvent.SystemStatusUpdated(readDndStatus()))
        trySend(CapsuleEvent.SystemStatusUpdated(readBatterySaverStatus()))
        trySend(CapsuleEvent.SystemStatusUpdated(readRingerMutedStatus()))
        trySend(CapsuleEvent.SystemStatusUpdated(readDataSaverStatus()))

        val activeVpnNetworks = LinkedHashSet<Network>()
        val vpnCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(activeVpnNetworks) {
                    activeVpnNetworks.add(network)
                }
                trySend(
                    CapsuleEvent.SystemStatusUpdated(
                        CapsuleContent.SystemStatus(
                            kind = SystemStatusKind.VPN,
                            isActive = true,
                        ),
                    ),
                )
            }

            override fun onLost(network: Network) {
                val stillActive = synchronized(activeVpnNetworks) {
                    activeVpnNetworks.remove(network)
                    activeVpnNetworks.isNotEmpty()
                }
                trySend(
                    CapsuleEvent.SystemStatusUpdated(
                        CapsuleContent.SystemStatus(
                            kind = SystemStatusKind.VPN,
                            isActive = stillActive,
                        ),
                    ),
                )
            }
        }
        val vpnRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val vpnRegistered = runCatching {
            connectivityManager?.registerNetworkCallback(vpnRequest, vpnCallback)
            true
        }.getOrDefault(false)
        val initialVpn = runCatching {
            connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
        trySend(
            CapsuleEvent.SystemStatusUpdated(
                CapsuleContent.SystemStatus(
                    kind = SystemStatusKind.VPN,
                    isActive = initialVpn,
                ),
            ),
        )

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
                    Intent.ACTION_USER_PRESENT -> {
                        trySend(CapsuleEvent.DeviceUnlocked(readUnlockSummary(UnlockMethod.GENERIC)))
                    }
                    AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                        trySend(CapsuleEvent.AlarmUpdated(readAlarmState()))
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        trySend(CapsuleEvent.SystemStatusUpdated(readAirplaneModeStatus()))
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        trySend(CapsuleEvent.SystemStatusUpdated(readDndStatus()))
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        trySend(CapsuleEvent.SystemStatusUpdated(readBatterySaverStatus()))
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        trySend(CapsuleEvent.SystemStatusUpdated(readRingerMutedStatus()))
                    }
                    ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED -> {
                        trySend(CapsuleEvent.SystemStatusUpdated(readDataSaverStatus()))
                    }
                    ACTION_WIFI_AP_STATE_CHANGED -> {
                        val apState = intent.getIntExtra(EXTRA_WIFI_AP_STATE, -1)
                        if (apState == WIFI_AP_STATE_ENABLED || apState == WIFI_AP_STATE_DISABLED) {
                            trySend(
                                CapsuleEvent.HotspotUpdated(
                                    CapsuleContent.Hotspot(isEnabled = apState == WIFI_AP_STATE_ENABLED),
                                ),
                            )
                        }
                    }
                    ACTION_TETHER_STATE_CHANGED -> {
                        val activeIfaces = intent.getStringArrayListExtra(EXTRA_ACTIVE_TETHER).orEmpty()
                        val hasWifiApTether = activeIfaces.any { iface ->
                            iface.contains("wlan", ignoreCase = true) ||
                                iface.contains("ap", ignoreCase = true) ||
                                iface.contains("softap", ignoreCase = true)
                        }
                        trySend(
                            CapsuleEvent.HotspotUpdated(
                                CapsuleContent.Hotspot(
                                    isEnabled = hasWifiApTether || currentHotspotState().isEnabled,
                                ),
                            ),
                        )
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
            addAction(ACTION_WIFI_AP_STATE_CHANGED)
            addAction(ACTION_TETHER_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            if (vpnRegistered) {
                runCatching { connectivityManager?.unregisterNetworkCallback(vpnCallback) }
            }
        }
    }

    private fun readAirplaneModeStatus(): CapsuleContent.SystemStatus {
        val enabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        }.getOrDefault(false)
        return CapsuleContent.SystemStatus(
            kind = SystemStatusKind.AIRPLANE_MODE,
            isActive = enabled,
        )
    }

    private fun readDndStatus(): CapsuleContent.SystemStatus {
        val filter = runCatching {
            notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL)
        val active = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        return CapsuleContent.SystemStatus(
            kind = SystemStatusKind.DO_NOT_DISTURB,
            isActive = active,
        )
    }

    private fun readBatterySaverStatus(): CapsuleContent.SystemStatus =
        CapsuleContent.SystemStatus(
            kind = SystemStatusKind.BATTERY_SAVER,
            isActive = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false),
        )

    private fun readRingerMutedStatus(): CapsuleContent.SystemStatus {
        val mode = runCatching { audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL }
            .getOrDefault(AudioManager.RINGER_MODE_NORMAL)
        return when (mode) {
            AudioManager.RINGER_MODE_SILENT -> CapsuleContent.SystemStatus(
                kind = SystemStatusKind.RINGER_MUTED,
                isActive = true,
                detailLabel = "Silent",
            )
            AudioManager.RINGER_MODE_VIBRATE -> CapsuleContent.SystemStatus(
                kind = SystemStatusKind.RINGER_MUTED,
                isActive = true,
                detailLabel = "Vibrate",
            )
            else -> CapsuleContent.SystemStatus(
                kind = SystemStatusKind.RINGER_MUTED,
                isActive = false,
            )
        }
    }

    private fun readDataSaverStatus(): CapsuleContent.SystemStatus {
        val restricted = runCatching {
            connectivityManager?.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }.getOrDefault(false)
        return CapsuleContent.SystemStatus(
            kind = SystemStatusKind.DATA_SAVER,
            isActive = restricted,
        )
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

    override fun currentHotspotState(): CapsuleContent.Hotspot {
        val enabled = runCatching {
            val method = WifiManager::class.java.getMethod("isWifiApEnabled")
            method.invoke(wifiManager) as? Boolean ?: false
        }.getOrDefault(false)
        return CapsuleContent.Hotspot(isEnabled = enabled)
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
        const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val EXTRA_WIFI_AP_STATE = "wifi_state"
        const val WIFI_AP_STATE_DISABLED = 11
        const val WIFI_AP_STATE_ENABLED = 13
        const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        const val EXTRA_ACTIVE_TETHER = "tetherArray"
        const val HTTP_TIMEOUT_MS = 5_000
        const val WEATHER_REFRESH_MINUTES = 30
        const val WEATHER_RETRY_MINUTES = 2
        const val WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1_000L
    }
}
