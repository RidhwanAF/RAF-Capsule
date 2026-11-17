package com.raf.capsuleos.data.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.edit
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.repository.TorchRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Observes and controls the device camera flashlight via [CameraManager],
 * preserving the latest live hardware/system strength level rather than resetting to 100%.
 */
@Singleton
class TorchRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TorchRepository {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val torchPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val flashCameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private val maxStrengthLevel: Int by lazy {
        val id = flashCameraId ?: return@lazy 1
        runCatching {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                ?: 1
        }.getOrDefault(1)
    }

    private val defaultStrengthLevel: Int by lazy {
        val id = flashCameraId ?: return@lazy 1
        runCatching {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)
                ?: 1
        }.getOrDefault(1)
    }

    @Volatile
    private var currentEnabled = false

    @Volatile
    private var currentLevel = resolveInitialStrengthLevel()

    override fun observeTorchState(): Flow<CapsuleContent.Torch> = callbackFlow {
        currentLevel = resolveInitialStrengthLevel()
        var hasReceivedInitial = false

        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId != flashCameraId) return
                currentEnabled = enabled
                if (enabled && maxStrengthLevel > 1) {
                    val liveSystemLevel = queryLiveHardwareStrength(cameraId)
                    if (liveSystemLevel != null) {
                        updateRememberedLevel(liveSystemLevel)
                    }
                }
                if (!hasReceivedInitial) {
                    hasReceivedInitial = true
                    if (!enabled) return
                }
                trySend(
                    CapsuleContent.Torch(
                        isEnabled = enabled,
                        strengthLevel = currentLevel,
                        maxStrengthLevel = maxStrengthLevel,
                    ),
                )
            }

            override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
                if (cameraId != flashCameraId) return
                updateRememberedLevel(newStrengthLevel)
                currentEnabled = true
                hasReceivedInitial = true
                trySend(
                    CapsuleContent.Torch(
                        isEnabled = true,
                        strengthLevel = currentLevel,
                        maxStrengthLevel = maxStrengthLevel,
                    ),
                )
            }
        }

        runCatching {
            cameraManager.registerTorchCallback(context.mainExecutor, callback)
        }

        awaitClose {
            runCatching { cameraManager.unregisterTorchCallback(callback) }
        }
    }.distinctUntilChanged()

    override fun currentSnapshot(): CapsuleContent.Torch = CapsuleContent.Torch(
        isEnabled = currentEnabled,
        strengthLevel = currentLevel,
        maxStrengthLevel = maxStrengthLevel,
    )

    override fun setTorchEnabled(enabled: Boolean) {
        val id = flashCameraId ?: return
        runCatching {
            if (enabled && maxStrengthLevel > 1) {
                val targetLevel = currentLevel.coerceIn(1, maxStrengthLevel)
                cameraManager.turnOnTorchWithStrengthLevel(id, targetLevel)
            } else {
                cameraManager.setTorchMode(id, enabled)
            }
        }
    }

    override fun setTorchStrength(level: Int) {
        val id = flashCameraId ?: return
        runCatching {
            if (maxStrengthLevel > 1) {
                val safeLevel = level.coerceIn(1, maxStrengthLevel)
                updateRememberedLevel(safeLevel)
                cameraManager.turnOnTorchWithStrengthLevel(id, safeLevel)
            } else {
                cameraManager.setTorchMode(id, true)
            }
        }
    }

    private fun resolveInitialStrengthLevel(): Int {
        val saved = torchPrefs.getInt(KEY_LAST_STRENGTH_LEVEL, -1)
        if (saved > 0) {
            return saved.coerceIn(1, maxStrengthLevel)
        }
        if (maxStrengthLevel <= 1) return 1
        // Avoid blasting 100% when OEM HAL sets default == max and no prior level was observed yet
        return if (defaultStrengthLevel >= maxStrengthLevel) {
            (maxStrengthLevel * 0.5f).roundToInt().coerceIn(1, maxStrengthLevel)
        } else {
            defaultStrengthLevel.coerceIn(1, maxStrengthLevel)
        }
    }

    private fun queryLiveHardwareStrength(cameraId: String): Int? =
        runCatching {
            cameraManager.getTorchStrengthLevel(cameraId).coerceIn(1, maxStrengthLevel)
        }.getOrNull()

    private fun updateRememberedLevel(level: Int) {
        val clamped = level.coerceIn(1, maxStrengthLevel)
        currentLevel = clamped
        torchPrefs.edit {
            putInt(KEY_LAST_STRENGTH_LEVEL, clamped)
        }
    }

    private companion object {
        const val PREFS_NAME = "capsule_torch_prefs"
        const val KEY_LAST_STRENGTH_LEVEL = "last_torch_strength_level"
    }
}
