package com.raf.capsuleos.data.hardware

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import com.raf.capsuleos.domain.model.BrightnessInfo
import com.raf.capsuleos.domain.repository.BrightnessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Perceptually synced Android display brightness repository.
 * Uses Android 17 [DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE] when available and falls back to
 * AOSP perceptual gamma curves (`BrightnessUtils`) over [Settings.System.SCREEN_BRIGHTNESS] on
 * earlier Android versions so the slider matches Quick Settings across Pixel 6/7/8/9+ devices.
 */
@Singleton
class BrightnessRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BrightnessRepository {

    private val displayManager = context.getSystemService(DisplayManager::class.java)

    override fun observeBrightnessChanges(): Flow<BrightnessInfo> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(currentSnapshot())
            }
        }
        val displayListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit
                override fun onDisplayRemoved(displayId: Int) = Unit
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        trySend(currentSnapshot())
                    }
                }
            }
        } else {
            null
        }

        runCatching {
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                observer,
            )
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                false,
                observer,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && displayListener != null) {
                displayManager?.registerDisplayListener(
                    context.mainExecutor,
                    DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS,
                    displayListener,
                )
            }
        }
        trySend(currentSnapshot())
        awaitClose {
            runCatching { resolver.unregisterContentObserver(observer) }
            if (displayListener != null) {
                runCatching { displayManager?.unregisterDisplayListener(displayListener) }
            }
        }
    }.distinctUntilChanged()

    override fun currentSnapshot(): BrightnessInfo {
        val resolver = context.contentResolver
        val mode = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
        return BrightnessInfo(
            levelPercent = readPerceptualBrightnessPercent(resolver),
            isAuto = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            canWrite = Settings.System.canWrite(context),
        )
    }

    override fun setBrightnessPercent(percent: Int): BrightnessInfo {
        if (!Settings.System.canWrite(context)) {
            return currentSnapshot()
        }
        val clamped = percent.coerceIn(0, 100)
        val setViaDisplayManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            runCatching {
                displayManager?.setBrightness(
                    Display.DEFAULT_DISPLAY,
                    clamped.toFloat(),
                    DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE,
                )
            }.isSuccess
        } else {
            false
        }
        if (!setViaDisplayManager) {
            val linear01 = gammaToLinear(clamped / 100f)
            val rawValue = (linear01 * MAX_RAW_BRIGHTNESS).roundToInt().coerceIn(1, MAX_RAW_BRIGHTNESS)
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    rawValue,
                )
            }
            return BrightnessInfo(
                levelPercent = clamped,
                isAuto = currentSnapshot().isAuto,
                canWrite = true,
            )
        }
        return currentSnapshot()
    }

    override fun toggleAutoBrightness(): BrightnessInfo {
        if (!Settings.System.canWrite(context)) {
            openWriteSettingsPermission()
            return currentSnapshot()
        }
        val currentlyAuto = currentSnapshot().isAuto
        val nextMode = if (currentlyAuto) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                nextMode,
            )
        }
        return currentSnapshot()
    }

    private fun readPerceptualBrightnessPercent(resolver: android.content.ContentResolver): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            val dmPercent = runCatching {
                displayManager?.getBrightness(
                    Display.DEFAULT_DISPLAY,
                    DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE,
                )
            }.getOrNull()
            if (dmPercent != null && dmPercent.isFinite() && dmPercent in 0f..100f) {
                return dmPercent.roundToInt().coerceIn(0, 100)
            }
        }
        val rawBrightness = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            DEFAULT_RAW_BRIGHTNESS,
        ).coerceIn(0, MAX_RAW_BRIGHTNESS)
        val linear01 = rawBrightness / MAX_RAW_BRIGHTNESS.toFloat()
        return (linearToGamma(linear01) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun linearToGamma(linear01: Float): Float {
        val x = linear01.coerceIn(0f, 1f)
        return if (x <= GAMMA_BREAKPOINT) {
            GAMMA_R * sqrt(12f * x)
        } else {
            GAMMA_A * ln(12f * x - GAMMA_B) + GAMMA_C
        }.coerceIn(0f, 1f)
    }

    private fun gammaToLinear(gamma01: Float): Float {
        val y = gamma01.coerceIn(0f, 1f)
        return if (y <= GAMMA_R) {
            val ratio = y / GAMMA_R
            (ratio * ratio) / 12f
        } else {
            (exp((y - GAMMA_C) / GAMMA_A) + GAMMA_B) / 12f
        }.coerceIn(0f, 1f)
    }

    private fun openWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val MAX_RAW_BRIGHTNESS = 255
        const val DEFAULT_RAW_BRIGHTNESS = 128

        // AOSP SettingsLib BrightnessUtils HLG/gamma perception curve constants
        const val GAMMA_BREAKPOINT = 1f / 12f
        const val GAMMA_R = 0.5f
        const val GAMMA_A = 0.17883277f
        const val GAMMA_B = 0.28466892f
        const val GAMMA_C = 0.5599107f
    }
}
