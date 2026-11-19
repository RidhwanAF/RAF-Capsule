package com.raf.capsuleos.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raf.capsuleos.domain.model.AppThemeMode
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleGestureAction
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CardGestureConfig
import com.raf.capsuleos.domain.model.ExpandedMediaGlowEffect
import com.raf.capsuleos.domain.model.GestureActionBinding
import com.raf.capsuleos.domain.model.GestureCardTarget
import com.raf.capsuleos.domain.model.HorizontalMediaGlowStyle
import com.raf.capsuleos.domain.model.SatellitePosition
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.capsuleDataStore: DataStore<Preferences> by preferencesDataStore(name = "capsule_settings")

/**
 * DataStore-backed implementation of [CapsuleSettingsRepository].
 */
@Singleton
class CapsuleSettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CapsuleSettingsRepository {

    override fun observePreferences(): Flow<CapsulePreferences> =
        context.capsuleDataStore.data.map(::readPreferences)

    override suspend fun updatePreferences(transform: (CapsulePreferences) -> CapsulePreferences) {
        context.capsuleDataStore.edit { prefs ->
            val current = readPreferences(prefs)
            val updated = transform(current)
            prefs[KEY_BELOW_STATUS_BAR] = updated.belowStatusBar
            prefs[KEY_CAMERA_POSITION] = updated.cameraPosition.name
            prefs[KEY_PADDING_WIDTH_DP] = updated.paddingWidthDp
            prefs[KEY_TOP_ADJUST_DP] = updated.topOffsetAdjustDp
            prefs[KEY_HORIZONTAL_OFFSET_DP] = updated.horizontalOffsetDp
            prefs[KEY_EXPANDED_CONTENT_PADDING_DP] = updated.expandedContentPaddingDp
            prefs[KEY_CAMERA_AUTO_DETECTED] = updated.hasAutoDetectedCamera
            prefs[KEY_HORIZONTAL_AUTO_COLLAPSE_MS] = updated.horizontalAutoCollapseDelayMs
            prefs[KEY_AUTO_COLLAPSE_MS] = updated.autoCollapseDelayMs
            prefs[KEY_MEDIA_FOLLOW_GLOBAL_AUTO_COLLAPSE] = updated.mediaFollowGlobalAutoCollapse
            prefs[KEY_MEDIA_AUTO_COLLAPSE_MS] = updated.mediaAutoCollapseDelayMs
            prefs[KEY_SHOW_ON_LOCK_SCREEN] = updated.showOnLockScreen
            prefs[KEY_HIDE_ON_QS_OPEN] = updated.hideOnQsOpen
            prefs[KEY_SUPPRESS_HEADS_UP] = updated.suppressSystemHeadsUp
            prefs[KEY_ENABLE_NEURON_GLOW] = updated.enableNeuronGlow
            prefs[KEY_ENABLE_OUTER_NEURON_GLOW] = updated.enableOuterNeuronGlow
            prefs[KEY_EXPANDED_MEDIA_GLOW_EFFECTS] = updated.expandedMediaGlowEffects
                .joinToString(separator = ",") { it.name }
            prefs[KEY_HORIZONTAL_MEDIA_GLOW_STYLE] = updated.horizontalMediaGlowStyle.name
            prefs[KEY_KEEP_EXPANDED_MEDIA_GLOW_ALWAYS] = updated.keepExpandedMediaGlowAlways
            prefs[KEY_KEEP_OUTER_MEDIA_GLOW_ALWAYS] = updated.keepOuterMediaGlowAlways
            prefs[KEY_SHOW_BATTERY_ON_RING] = updated.showBatteryOnCameraRing
            prefs[KEY_ENABLE_LEFT_SATELLITE] = updated.enableLeftSatelliteBubble
            prefs[KEY_SATELLITE_POSITION] = updated.satellitePosition.name
            prefs[KEY_SHOW_TORCH_SATELLITE] = updated.showTorchInSatellite
            prefs[KEY_SHOW_HOTSPOT_SATELLITE] = updated.showHotspotInSatellite
            prefs[KEY_SHOW_LIVE_SATELLITE] = updated.showLiveActivityInSatellite
            prefs[KEY_SHOW_AIRPLANE_SATELLITE] = updated.showAirplaneInSatellite
            prefs[KEY_SHOW_DND_SATELLITE] = updated.showDndInSatellite
            prefs[KEY_SHOW_BATTERY_SAVER_SATELLITE] = updated.showBatterySaverInSatellite
            prefs[KEY_SHOW_VPN_SATELLITE] = updated.showVpnInSatellite
            prefs[KEY_SHOW_RINGER_MUTED_SATELLITE] = updated.showRingerMutedInSatellite
            prefs[KEY_SHOW_DATA_SAVER_SATELLITE] = updated.showDataSaverInSatellite
            prefs[KEY_ENABLE_BLUETOOTH_ALERTS] = updated.enableBluetoothAlerts
            prefs[KEY_ENABLE_CALL_AUTO_EXPAND] = updated.enableCallAutoExpand
            prefs[KEY_GLOBAL_DOUBLE_TAP_ACTION] = updated.globalDoubleTapAction.name
            val dtPkg = updated.globalDoubleTapAppPackage
            if (dtPkg.isNullOrBlank()) prefs.remove(KEY_GLOBAL_DOUBLE_TAP_APP) else prefs[KEY_GLOBAL_DOUBLE_TAP_APP] = dtPkg
            prefs[KEY_GLOBAL_LONG_HOLD_ACTION] = updated.globalLongHoldAction.name
            val lhPkg = updated.globalLongHoldAppPackage
            if (lhPkg.isNullOrBlank()) prefs.remove(KEY_GLOBAL_LONG_HOLD_APP) else prefs[KEY_GLOBAL_LONG_HOLD_APP] = lhPkg
            prefs[KEY_CARD_GESTURES] = encodeCardGestures(updated.cardGestures)
            prefs[KEY_APP_THEME_MODE] = updated.appThemeMode.name
            prefs[KEY_USE_DYNAMIC_COLOR] = updated.useDynamicColor
        }
    }

    private fun readPreferences(prefs: Preferences): CapsulePreferences = CapsulePreferences(
        belowStatusBar = prefs[KEY_BELOW_STATUS_BAR] ?: DEFAULT_PREFS.belowStatusBar,
        cameraPosition = prefs[KEY_CAMERA_POSITION]
            ?.let { raw -> runCatching { CameraAnchorPosition.valueOf(raw) }.getOrNull() }
            ?: DEFAULT_PREFS.cameraPosition,
        paddingWidthDp = prefs[KEY_PADDING_WIDTH_DP] ?: DEFAULT_PREFS.paddingWidthDp,
        topOffsetAdjustDp = prefs[KEY_TOP_ADJUST_DP] ?: DEFAULT_PREFS.topOffsetAdjustDp,
        horizontalOffsetDp = prefs[KEY_HORIZONTAL_OFFSET_DP] ?: DEFAULT_PREFS.horizontalOffsetDp,
        expandedContentPaddingDp = prefs[KEY_EXPANDED_CONTENT_PADDING_DP]
            ?: DEFAULT_PREFS.expandedContentPaddingDp,
        hasAutoDetectedCamera = prefs[KEY_CAMERA_AUTO_DETECTED] ?: DEFAULT_PREFS.hasAutoDetectedCamera,
        horizontalAutoCollapseDelayMs = prefs[KEY_HORIZONTAL_AUTO_COLLAPSE_MS]
            ?: DEFAULT_PREFS.horizontalAutoCollapseDelayMs,
        autoCollapseDelayMs = prefs[KEY_AUTO_COLLAPSE_MS] ?: DEFAULT_PREFS.autoCollapseDelayMs,
        mediaFollowGlobalAutoCollapse = prefs[KEY_MEDIA_FOLLOW_GLOBAL_AUTO_COLLAPSE]
            ?: DEFAULT_PREFS.mediaFollowGlobalAutoCollapse,
        mediaAutoCollapseDelayMs = (prefs[KEY_MEDIA_AUTO_COLLAPSE_MS]
            ?: DEFAULT_PREFS.mediaAutoCollapseDelayMs).coerceIn(10_000L, 60_000L),
        showOnLockScreen = prefs[KEY_SHOW_ON_LOCK_SCREEN] ?: DEFAULT_PREFS.showOnLockScreen,
        hideOnQsOpen = prefs[KEY_HIDE_ON_QS_OPEN] ?: DEFAULT_PREFS.hideOnQsOpen,
        suppressSystemHeadsUp = prefs[KEY_SUPPRESS_HEADS_UP] ?: DEFAULT_PREFS.suppressSystemHeadsUp,
        enableNeuronGlow = prefs[KEY_ENABLE_NEURON_GLOW] ?: DEFAULT_PREFS.enableNeuronGlow,
        enableOuterNeuronGlow = prefs[KEY_ENABLE_OUTER_NEURON_GLOW] ?: DEFAULT_PREFS.enableOuterNeuronGlow,
        expandedMediaGlowEffects = prefs[KEY_EXPANDED_MEDIA_GLOW_EFFECTS]
            ?.let(::decodeExpandedMediaEffects)
            ?: DEFAULT_PREFS.expandedMediaGlowEffects,
        horizontalMediaGlowStyle = prefs[KEY_HORIZONTAL_MEDIA_GLOW_STYLE]
            ?.let { raw -> runCatching { HorizontalMediaGlowStyle.valueOf(raw) }.getOrNull() }
            ?: migrateLegacyHorizontalGlow(prefs[KEY_MEDIA_GLOW_STYLE]),
        keepExpandedMediaGlowAlways = prefs[KEY_KEEP_EXPANDED_MEDIA_GLOW_ALWAYS]
            ?: DEFAULT_PREFS.keepExpandedMediaGlowAlways,
        keepOuterMediaGlowAlways = prefs[KEY_KEEP_OUTER_MEDIA_GLOW_ALWAYS]
            ?: DEFAULT_PREFS.keepOuterMediaGlowAlways,
        showBatteryOnCameraRing = prefs[KEY_SHOW_BATTERY_ON_RING] ?: DEFAULT_PREFS.showBatteryOnCameraRing,
        enableLeftSatelliteBubble = prefs[KEY_ENABLE_LEFT_SATELLITE] ?: DEFAULT_PREFS.enableLeftSatelliteBubble,
        satellitePosition = prefs[KEY_SATELLITE_POSITION]
            ?.let { raw -> runCatching { SatellitePosition.valueOf(raw) }.getOrNull() }
            ?: DEFAULT_PREFS.satellitePosition,
        showTorchInSatellite = prefs[KEY_SHOW_TORCH_SATELLITE] ?: DEFAULT_PREFS.showTorchInSatellite,
        showHotspotInSatellite = prefs[KEY_SHOW_HOTSPOT_SATELLITE] ?: DEFAULT_PREFS.showHotspotInSatellite,
        showLiveActivityInSatellite = prefs[KEY_SHOW_LIVE_SATELLITE] ?: DEFAULT_PREFS.showLiveActivityInSatellite,
        showAirplaneInSatellite = prefs[KEY_SHOW_AIRPLANE_SATELLITE] ?: DEFAULT_PREFS.showAirplaneInSatellite,
        showDndInSatellite = prefs[KEY_SHOW_DND_SATELLITE] ?: DEFAULT_PREFS.showDndInSatellite,
        showBatterySaverInSatellite = prefs[KEY_SHOW_BATTERY_SAVER_SATELLITE] ?: DEFAULT_PREFS.showBatterySaverInSatellite,
        showVpnInSatellite = prefs[KEY_SHOW_VPN_SATELLITE] ?: DEFAULT_PREFS.showVpnInSatellite,
        showRingerMutedInSatellite = prefs[KEY_SHOW_RINGER_MUTED_SATELLITE] ?: DEFAULT_PREFS.showRingerMutedInSatellite,
        showDataSaverInSatellite = prefs[KEY_SHOW_DATA_SAVER_SATELLITE] ?: DEFAULT_PREFS.showDataSaverInSatellite,
        enableBluetoothAlerts = prefs[KEY_ENABLE_BLUETOOTH_ALERTS] ?: DEFAULT_PREFS.enableBluetoothAlerts,
        enableCallAutoExpand = prefs[KEY_ENABLE_CALL_AUTO_EXPAND] ?: DEFAULT_PREFS.enableCallAutoExpand,
        globalDoubleTapAction = prefs[KEY_GLOBAL_DOUBLE_TAP_ACTION]
            ?.let { raw -> runCatching { CapsuleGestureAction.valueOf(raw) }.getOrNull() }
            ?: DEFAULT_PREFS.globalDoubleTapAction,
        globalDoubleTapAppPackage = prefs[KEY_GLOBAL_DOUBLE_TAP_APP],
        globalLongHoldAction = prefs[KEY_GLOBAL_LONG_HOLD_ACTION]
            ?.let { raw -> runCatching { CapsuleGestureAction.valueOf(raw) }.getOrNull() }
            ?: DEFAULT_PREFS.globalLongHoldAction,
        globalLongHoldAppPackage = prefs[KEY_GLOBAL_LONG_HOLD_APP],
        cardGestures = prefs[KEY_CARD_GESTURES]?.let(::decodeCardGestures).orEmpty(),
        appThemeMode = prefs[KEY_APP_THEME_MODE]
            ?.let { raw -> runCatching { AppThemeMode.valueOf(raw) }.getOrNull() }
            ?: DEFAULT_PREFS.appThemeMode,
        useDynamicColor = prefs[KEY_USE_DYNAMIC_COLOR] ?: DEFAULT_PREFS.useDynamicColor,
    )

    private fun encodeCardGestures(map: Map<GestureCardTarget, CardGestureConfig>): String =
        map.entries.joinToString(separator = ";") { (target, config) ->
            listOf(
                target.name,
                config.doubleTap.action?.name.orEmpty(),
                config.doubleTap.customAppPackage.orEmpty(),
                config.longHold.action?.name.orEmpty(),
                config.longHold.customAppPackage.orEmpty(),
            ).joinToString(separator = ",")
        }

    private fun decodeCardGestures(raw: String): Map<GestureCardTarget, CardGestureConfig> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size < 5) return@mapNotNull null
            val target = runCatching { GestureCardTarget.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val dtAction = parts[1].takeIf { it.isNotBlank() }?.let { runCatching { CapsuleGestureAction.valueOf(it) }.getOrNull() }
            val dtPkg = parts[2].takeIf { it.isNotBlank() }
            val lhAction = parts[3].takeIf { it.isNotBlank() }?.let { runCatching { CapsuleGestureAction.valueOf(it) }.getOrNull() }
            val lhPkg = parts[4].takeIf { it.isNotBlank() }
            target to CardGestureConfig(
                doubleTap = GestureActionBinding(action = dtAction, customAppPackage = dtPkg),
                longHold = GestureActionBinding(action = lhAction, customAppPackage = lhPkg),
            )
        }.toMap()
    }

    private fun decodeExpandedMediaEffects(raw: String): Set<ExpandedMediaGlowEffect> {
        if (raw.isBlank()) return emptySet()
        return raw.split(",")
            .mapNotNull { token ->
                token.trim().takeIf(String::isNotEmpty)?.let { name ->
                    runCatching { ExpandedMediaGlowEffect.valueOf(name) }.getOrNull()
                }
            }
            .toSet()
    }

    private fun migrateLegacyHorizontalGlow(legacyRaw: String?): HorizontalMediaGlowStyle = when (legacyRaw) {
        "HORIZONTAL_PILL" -> HorizontalMediaGlowStyle.HORIZONTAL_PILL
        "CAMERA_RING" -> HorizontalMediaGlowStyle.CAMERA_RING
        "ALBUM_ART_CARD" -> HorizontalMediaGlowStyle.ALBUM_ART_RING
        else -> DEFAULT_PREFS.horizontalMediaGlowStyle
    }

    private companion object {
        val DEFAULT_PREFS = CapsulePreferences()
        val KEY_BELOW_STATUS_BAR = booleanPreferencesKey("v4_below_status_bar_anchor")
        val KEY_CAMERA_POSITION = stringPreferencesKey("v4_camera_anchor_position")
        val KEY_PADDING_WIDTH_DP = floatPreferencesKey("v4_cutout_padding_width_dp")
        val KEY_TOP_ADJUST_DP = floatPreferencesKey("v4_cutout_top_adjust_dp")
        val KEY_HORIZONTAL_OFFSET_DP = floatPreferencesKey("v4_cutout_horizontal_offset_dp")
        val KEY_EXPANDED_CONTENT_PADDING_DP = floatPreferencesKey("v4_expanded_content_padding_dp")
        val KEY_CAMERA_AUTO_DETECTED = booleanPreferencesKey("v4_camera_auto_detected")
        val KEY_HORIZONTAL_AUTO_COLLAPSE_MS = longPreferencesKey("v10_horizontal_auto_collapse_ms")
        val KEY_AUTO_COLLAPSE_MS = longPreferencesKey("v4_auto_collapse_ms")
        val KEY_MEDIA_FOLLOW_GLOBAL_AUTO_COLLAPSE = booleanPreferencesKey("v11_media_follow_global_auto_collapse")
        val KEY_MEDIA_AUTO_COLLAPSE_MS = longPreferencesKey("v11_media_auto_collapse_ms")
        val KEY_SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("v13_show_on_lock_screen")
        val KEY_HIDE_ON_QS_OPEN = booleanPreferencesKey("v9_hide_on_qs_open")
        val KEY_SUPPRESS_HEADS_UP = booleanPreferencesKey("v4_suppress_system_heads_up")
        val KEY_ENABLE_NEURON_GLOW = booleanPreferencesKey("v5_enable_neuron_glow")
        val KEY_ENABLE_OUTER_NEURON_GLOW = booleanPreferencesKey("v11_enable_outer_neuron_glow")
        val KEY_MEDIA_GLOW_STYLE = stringPreferencesKey("v5_media_glow_style")
        val KEY_EXPANDED_MEDIA_GLOW_EFFECTS = stringPreferencesKey("v11_expanded_media_glow_effects")
        val KEY_HORIZONTAL_MEDIA_GLOW_STYLE = stringPreferencesKey("v11_horizontal_media_glow_style")
        val KEY_KEEP_EXPANDED_MEDIA_GLOW_ALWAYS = booleanPreferencesKey("v7_keep_expanded_media_glow_always")
        val KEY_KEEP_OUTER_MEDIA_GLOW_ALWAYS = booleanPreferencesKey("v12_keep_outer_media_glow_always")
        val KEY_SHOW_BATTERY_ON_RING = booleanPreferencesKey("v5_show_battery_on_ring")
        val KEY_ENABLE_LEFT_SATELLITE = booleanPreferencesKey("v5_enable_left_satellite")
        val KEY_SATELLITE_POSITION = stringPreferencesKey("v15_satellite_position")
        val KEY_SHOW_TORCH_SATELLITE = booleanPreferencesKey("v5_show_torch_satellite")
        val KEY_SHOW_HOTSPOT_SATELLITE = booleanPreferencesKey("v5_show_hotspot_satellite")
        val KEY_SHOW_LIVE_SATELLITE = booleanPreferencesKey("v5_show_live_satellite")
        val KEY_SHOW_AIRPLANE_SATELLITE = booleanPreferencesKey("v12_show_airplane_satellite")
        val KEY_SHOW_DND_SATELLITE = booleanPreferencesKey("v12_show_dnd_satellite")
        val KEY_SHOW_BATTERY_SAVER_SATELLITE = booleanPreferencesKey("v12_show_battery_saver_satellite")
        val KEY_SHOW_VPN_SATELLITE = booleanPreferencesKey("v12_show_vpn_satellite")
        val KEY_SHOW_RINGER_MUTED_SATELLITE = booleanPreferencesKey("v12_show_ringer_muted_satellite")
        val KEY_SHOW_DATA_SAVER_SATELLITE = booleanPreferencesKey("v12_show_data_saver_satellite")
        val KEY_ENABLE_BLUETOOTH_ALERTS = booleanPreferencesKey("v5_enable_bluetooth_alerts")
        val KEY_ENABLE_CALL_AUTO_EXPAND = booleanPreferencesKey("v5_enable_call_auto_expand")
        val KEY_GLOBAL_DOUBLE_TAP_ACTION = stringPreferencesKey("v5_global_double_tap_action")
        val KEY_GLOBAL_DOUBLE_TAP_APP = stringPreferencesKey("v5_global_double_tap_app")
        val KEY_GLOBAL_LONG_HOLD_ACTION = stringPreferencesKey("v5_global_long_hold_action")
        val KEY_GLOBAL_LONG_HOLD_APP = stringPreferencesKey("v5_global_long_hold_app")
        val KEY_CARD_GESTURES = stringPreferencesKey("v5_card_gestures")
        val KEY_APP_THEME_MODE = stringPreferencesKey("v6_app_theme_mode")
        val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("v6_use_dynamic_color")
    }
}
