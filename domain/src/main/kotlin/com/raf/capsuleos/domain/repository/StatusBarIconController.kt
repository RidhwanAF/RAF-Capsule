package com.raf.capsuleos.domain.repository

/**
 * Manages system status bar cleanup and suppressing floating system heads-up popups
 * (so notification controls remain in Quick Settings shade only) using `WRITE_SECURE_SETTINGS`.
 */
interface StatusBarIconController {
    /** Returns true when `WRITE_SECURE_SETTINGS` has been granted via ADB. */
    fun hasSecureSettingsPermission(): Boolean

    /** Restores the user's prior `icon_blacklist` snapshot if any previous version hid icons. */
    fun restoreIcons()

    /** Suppresses or restores system heads-up popup banners via `heads_up_notifications_enabled`. */
    fun setSystemHeadsUpSuppressed(suppress: Boolean)
}
