package com.raf.capsuleos.presentation.capsule.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val ChargeRed = Color(0xFFFF453A)
private val ChargeOrange = Color(0xFFFF9F0A)
private val ChargeYellow = Color(0xFFFFD60A)
private val ChargeGreen = Color(0xFF30D158)

/**
 * Computes the capsule battery/charging accent color:
 * - On battery (`!isCharging`): returns the dynamic Material 3 `primary` color.
 * - While charging (`isCharging`): smoothly transitions across Red -> Orange -> Yellow -> Green
 *   based on battery percentage (`0..100`).
 *
 * @param batteryPercent Current battery level (0..100).
 * @param isCharging True while device is actively charging.
 * @param primaryColor Material 3 dynamic primary theme color used on battery.
 */
fun capsuleBatteryAccentColor(
    batteryPercent: Int,
    isCharging: Boolean,
    primaryColor: Color,
): Color {
    if (!isCharging) return primaryColor
    val clamped = batteryPercent.coerceIn(0, 100)
    return when {
        clamped <= 20 -> ChargeRed
        clamped <= 45 -> lerp(ChargeRed, ChargeOrange, (clamped - 20) / 25f)
        clamped <= 70 -> lerp(ChargeOrange, ChargeYellow, (clamped - 45) / 25f)
        else -> lerp(ChargeYellow, ChargeGreen, (clamped - 70) / 30f)
    }
}
