package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing system-wide battery, charging, live activity, unlock, and alarm events.
 */
interface SystemEventsRepository {
    /** Emits battery, live activity, unlock, and alarm events mapped to [CapsuleEvent]. */
    fun observeSystemEvents(): Flow<CapsuleEvent>

    /** Returns formatted next alarm time if an alarm is currently scheduled. */
    fun currentNextAlarmLabel(): String?

    /** Returns current (batteryPercent, isCharging) snapshot. */
    fun currentBatteryState(): Pair<Int, Boolean>

    /** Returns current active live activity notification if any. */
    fun currentLiveActivity(): CapsuleContent.LiveActivity?

    /** Emits periodically refreshed At-a-Glance weather summaries (cached / network best-effort). */
    fun observeWeatherSummary(): kotlinx.coroutines.flow.Flow<com.raf.capsuleos.domain.model.WeatherSummary?>

    /** Returns the latest cached weather summary if available. */
    fun currentWeatherSummary(): com.raf.capsuleos.domain.model.WeatherSummary?
}
