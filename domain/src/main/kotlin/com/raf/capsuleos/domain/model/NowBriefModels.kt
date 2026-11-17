package com.raf.capsuleos.domain.model

/**
 * Audible ringer modes cycled by Now Brief's quick-toggle ringer control.
 */
enum class RingerModeState {
    /** Audible ringtone & notifications enabled. */
    NORMAL,

    /** Vibration-only alerts. */
    VIBRATE,

    /** Silent / Do Not Disturb ringer suppression (requires policy access on Android). */
    SILENT,
}

/**
 * Snapshot of display backlight level and automatic brightness state.
 *
 * @property levelPercent Display brightness normalized to `0..100`.
 * @property isAuto Whether adaptive/auto brightness is enabled.
 * @property canWrite Whether the app currently holds `WRITE_SETTINGS` to adjust brightness.
 */
data class BrightnessInfo(
    val levelPercent: Int = 50,
    val isAuto: Boolean = true,
    val canWrite: Boolean = true,
)

/**
 * Semantic atmospheric categories used to select crisp vector weather glyphs in Now Brief.
 */
enum class WeatherConditionKind {
    /** Clear sky / sunny conditions. */
    CLEAR,

    /** Partly cloudy or overcast sky. */
    CLOUDY,

    /** Rain showers or drizzle. */
    RAIN,

    /** Snow or sleet. */
    SNOW,

    /** Thunderstorm activity. */
    STORM,

    /** Fog or mist. */
    FOG,
}

/**
 * Lightweight At-a-Glance weather condition summary displayed inside Now Brief.
 *
 * @property temperatureCelsius Current temperature rounded to whole Celsius degrees.
 * @property conditionLabel Short human-readable condition label (e.g., `"Clear"`, `"Cloudy"`).
 * @property conditionKind Semantic weather classification for UI icon mapping.
 */
data class WeatherSummary(
    val temperatureCelsius: Int,
    val conditionLabel: String,
    val conditionKind: WeatherConditionKind,
) {
    /** Concise single-line pill badge text (e.g., `"14°C • Cloudy"`). */
    val formattedBadge: String
        get() = "$temperatureCelsius°C • $conditionLabel"
}
