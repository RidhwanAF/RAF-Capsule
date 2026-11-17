package com.raf.capsuleos.data.statusbar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.raf.capsuleos.domain.repository.StatusBarIconController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hides and restores ALL Pixel/AOSP system status bar icon slots via `Settings.Secure.icon_blacklist`
 * strictly while the State 3 card is expanded, with persistent crash-recovery state.
 */
@Singleton
class StatusBarIconControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : StatusBarIconController {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var isCurrentlyHidden = false
    private var isHeadsUpSuppressed = false

    init {
        if (prefs.getBoolean(KEY_IS_HIDDEN, false)) {
            restoreIcons()
        }
        if (prefs.getBoolean(KEY_HEADS_UP_SUPPRESSED, false)) {
            setSystemHeadsUpSuppressed(false)
        }
    }

    override fun hasSecureSettingsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED

    override fun hideIconsForExpansion() {
        if (isCurrentlyHidden || !hasSecureSettingsPermission()) return

        runCatching {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, KEY_ICON_BLACKLIST)
            prefs.edit {
                putString(KEY_SAVED_ORIGINAL, current)
                putBoolean(KEY_IS_HIDDEN, true)
            }

            val merged = buildSet {
                current?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.let(::addAll)
                addAll(ALL_STATUS_BAR_SLOTS)
            }.joinToString(",")

            Settings.Secure.putString(resolver, KEY_ICON_BLACKLIST, merged)
            isCurrentlyHidden = true
        }.onFailure { err ->
            Log.e(TAG, "hideIconsForExpansion failed", err)
        }
    }

    override fun restoreIcons() {
        val wasPersistedHidden = prefs.getBoolean(KEY_IS_HIDDEN, false)
        if ((!isCurrentlyHidden && !wasPersistedHidden) || !hasSecureSettingsPermission()) return

        val savedOriginal = prefs.getString(KEY_SAVED_ORIGINAL, null)
        runCatching {
            Settings.Secure.putString(context.contentResolver, KEY_ICON_BLACKLIST, savedOriginal)
        }.onFailure { err ->
            Log.e(TAG, "restoreIcons failed", err)
        }
        prefs.edit {
            remove(KEY_SAVED_ORIGINAL)
            putBoolean(KEY_IS_HIDDEN, false)
        }
        isCurrentlyHidden = false
    }

    override fun setSystemHeadsUpSuppressed(suppress: Boolean) {
        if (!hasSecureSettingsPermission()) return
        val wasPersisted = prefs.getBoolean(KEY_HEADS_UP_SUPPRESSED, false)
        if (suppress) {
            if (isHeadsUpSuppressed && wasPersisted) return
            runCatching {
                val resolver = context.contentResolver
                val original = Settings.Global.getInt(resolver, KEY_HEADS_UP_ENABLED, 1)
                if (!wasPersisted) {
                    prefs.edit {
                        putInt(KEY_SAVED_ORIGINAL_HEADS_UP, original)
                        putBoolean(KEY_HEADS_UP_SUPPRESSED, true)
                    }
                }
                Settings.Global.putInt(resolver, KEY_HEADS_UP_ENABLED, 0)
                isHeadsUpSuppressed = true
            }.onFailure { err ->
                Log.e(TAG, "setSystemHeadsUpSuppressed(true) failed", err)
            }
        } else {
            if (!isHeadsUpSuppressed && !wasPersisted) return
            val savedOriginal = prefs.getInt(KEY_SAVED_ORIGINAL_HEADS_UP, 1)
            runCatching {
                Settings.Global.putInt(context.contentResolver, KEY_HEADS_UP_ENABLED, savedOriginal)
            }.onFailure { err ->
                Log.e(TAG, "setSystemHeadsUpSuppressed(false) failed", err)
            }
            prefs.edit {
                remove(KEY_SAVED_ORIGINAL_HEADS_UP)
                putBoolean(KEY_HEADS_UP_SUPPRESSED, false)
            }
            isHeadsUpSuppressed = false
        }
    }

    private companion object {
        const val TAG = "CapsuleStatusBar"
        const val PREFS_NAME = "capsule_statusbar_prefs"
        const val KEY_SAVED_ORIGINAL = "saved_original_blacklist"
        const val KEY_IS_HIDDEN = "is_hidden_by_capsule"
        const val KEY_ICON_BLACKLIST = "icon_blacklist"
        const val KEY_HEADS_UP_ENABLED = "heads_up_notifications_enabled"
        const val KEY_HEADS_UP_SUPPRESSED = "heads_up_suppressed_by_capsule"
        const val KEY_SAVED_ORIGINAL_HEADS_UP = "saved_original_heads_up"

        val ALL_STATUS_BAR_SLOTS = listOf(
            "clock",
            "battery",
            "batteryshare",
            "wifi",
            "mobile",
            "stacked_mobile",
            "phone_signal",
            "phone_evdo_signal",
            "data_connection",
            "data_saver",
            "airplane",
            "hotspot",
            "bluetooth",
            "alarm_clock",
            "alarm",
            "rotate",
            "headset",
            "mute",
            "volume",
            "zen",
            "vpn",
            "nfc",
            "location",
            "cast",
            "satellite",
            "connected_display",
            "camera",
            "microphone",
            "screen_record",
            "sensors_off",
            "managed_profile",
            "ethernet",
            "sync_active",
            "sync_failing",
            "tty",
            "speakerphone",
            "ime",
            "no_calling",
            "call_strength",
            "cdma_eri",
            "secure",
        )
    }
}
