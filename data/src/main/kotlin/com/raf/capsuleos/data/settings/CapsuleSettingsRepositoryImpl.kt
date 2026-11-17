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
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsulePreferences
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
            prefs[KEY_AUTO_COLLAPSE_MS] = updated.autoCollapseDelayMs
            prefs[KEY_HIDE_STATUS_BAR] = updated.autoHideStatusBarIcons
            prefs[KEY_SUPPRESS_HEADS_UP] = updated.suppressSystemHeadsUp
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
        autoCollapseDelayMs = prefs[KEY_AUTO_COLLAPSE_MS] ?: DEFAULT_PREFS.autoCollapseDelayMs,
        autoHideStatusBarIcons = prefs[KEY_HIDE_STATUS_BAR] ?: DEFAULT_PREFS.autoHideStatusBarIcons,
        suppressSystemHeadsUp = prefs[KEY_SUPPRESS_HEADS_UP] ?: DEFAULT_PREFS.suppressSystemHeadsUp,
    )

    private companion object {
        val DEFAULT_PREFS = CapsulePreferences()
        val KEY_BELOW_STATUS_BAR = booleanPreferencesKey("v4_below_status_bar_anchor")
        val KEY_CAMERA_POSITION = stringPreferencesKey("v4_camera_anchor_position")
        val KEY_PADDING_WIDTH_DP = floatPreferencesKey("v4_cutout_padding_width_dp")
        val KEY_TOP_ADJUST_DP = floatPreferencesKey("v4_cutout_top_adjust_dp")
        val KEY_HORIZONTAL_OFFSET_DP = floatPreferencesKey("v4_cutout_horizontal_offset_dp")
        val KEY_EXPANDED_CONTENT_PADDING_DP = floatPreferencesKey("v4_expanded_content_padding_dp")
        val KEY_CAMERA_AUTO_DETECTED = booleanPreferencesKey("v4_camera_auto_detected")
        val KEY_AUTO_COLLAPSE_MS = longPreferencesKey("v4_auto_collapse_ms")
        val KEY_HIDE_STATUS_BAR = booleanPreferencesKey("v4_auto_hide_status_bar_icons")
        val KEY_SUPPRESS_HEADS_UP = booleanPreferencesKey("v4_suppress_system_heads_up")
    }
}
