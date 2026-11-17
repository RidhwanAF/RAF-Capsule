package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsulePreferences
import kotlinx.coroutines.flow.Flow

/**
 * Persists user-tunable capsule alignment and behavior settings.
 */
interface CapsuleSettingsRepository {
    /** Emits persisted [CapsulePreferences]. */
    fun observePreferences(): Flow<CapsulePreferences>

    /** Updates cutout alignment and capsule behavior settings. */
    suspend fun updatePreferences(transform: (CapsulePreferences) -> CapsulePreferences)
}
