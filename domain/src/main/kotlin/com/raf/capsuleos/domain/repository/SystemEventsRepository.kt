package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.WeatherSummary
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing system-wide battery, charging, hotspot, live activity, unlock, and alarm events.
 */
interface SystemEventsRepository {
    /** Emits battery, hotspot, live activity, unlock, and alarm events mapped to [CapsuleEvent]. */
    fun observeSystemEvents(): Flow<CapsuleEvent>

    /** Returns formatted next alarm time if an alarm is currently scheduled. */
    fun currentNextAlarmLabel(): String?

    /** Returns current (batteryPercent, isCharging) snapshot. */
    fun currentBatteryState(): Pair<Int, Boolean>

    /** Returns current active live activity notification if any. */
    fun currentLiveActivity(): CapsuleContent.LiveActivity?

    /** Returns current mobile hotspot state if detectable. */
    fun currentHotspotState(): CapsuleContent.Hotspot

    /** Emits periodically refreshed At-a-Glance weather summaries when coarse location is granted. */
    fun observeWeatherSummary(): Flow<WeatherSummary?>

    /** Returns the latest cached weather summary if available. */
    fun currentWeatherSummary(): WeatherSummary?

    /** Returns true when optional coarse location permission (`ACCESS_COARSE_LOCATION`) is granted. */
    fun hasLocationPermission(): Boolean

    /** Immediately triggers a weather refresh (e.g. right after granting location permission). */
    fun refreshWeatherNow()
}
