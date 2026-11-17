package com.raf.capsuleos.data.media

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Extracts vibrant primary/secondary accent colors and compressed PNG artwork bytes
 * using pure Android graphics APIs without external palette libraries.
 */
object BitmapColorExtractor {

    private const val SAMPLE_SIZE = 16
    private const val DEFAULT_PRIMARY = 0xFF64B5F6.toInt()
    private const val DEFAULT_SECONDARY = 0xFFBA68C8.toInt()

    /**
     * Compresses [bitmap] into PNG bytes suitable for crossing domain boundaries.
     *
     * @param bitmap Source album art bitmap.
     * @return Compressed PNG byte array, or null if bitmap is recycled.
     */
    fun toPngBytes(bitmap: Bitmap): ByteArray? {
        if (bitmap.isRecycled) return null
        val scaled = bitmap.scale(160, 160)
        return ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.PNG, 90, stream)
            if (scaled !== bitmap) scaled.recycle()
            stream.toByteArray()
        }
    }

    /**
     * Samples dominant vibrant colors from [bitmap] for shader and blur tinting.
     *
     * @param bitmap Source album art bitmap.
     * @return Pair of (primaryColorArgb, secondaryColorArgb).
     */
    fun extractColors(bitmap: Bitmap?): Pair<Int, Int> {
        if (bitmap == null || bitmap.isRecycled) {
            return DEFAULT_PRIMARY to DEFAULT_SECONDARY
        }

        val sample = bitmap.scale(SAMPLE_SIZE, SAMPLE_SIZE)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        sample.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        if (sample !== bitmap) sample.recycle()

        val hsv = FloatArray(3)
        var bestPrimary = DEFAULT_PRIMARY
        var bestPrimaryScore = -1f
        var bestSecondary = DEFAULT_SECONDARY
        var bestSecondaryScore = -1f

        pixels.forEach { argb ->
            if (Color.alpha(argb) < 128) return@forEach
            Color.colorToHSV(argb, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            if (value < 0.2f) return@forEach

            val vibrancy = saturation * 0.7f + value * 0.3f
            if (vibrancy > bestPrimaryScore) {
                bestSecondary = bestPrimary
                bestSecondaryScore = bestPrimaryScore
                bestPrimary = boostColor(hsv)
                bestPrimaryScore = vibrancy
            } else if (vibrancy > bestSecondaryScore && hueDistance(bestPrimary, argb) > 25f) {
                bestSecondary = boostColor(hsv)
                bestSecondaryScore = vibrancy
            }
        }

        return bestPrimary to bestSecondary
    }

    private fun boostColor(hsv: FloatArray): Int {
        val boosted = floatArrayOf(
            hsv[0],
            max(hsv[1], 0.55f),
            min(max(hsv[2], 0.65f), 0.95f),
        )
        return Color.HSVToColor(boosted)
    }

    private fun hueDistance(colorA: Int, colorB: Int): Float {
        val hsvA = FloatArray(3)
        val hsvB = FloatArray(3)
        Color.colorToHSV(colorA, hsvA)
        Color.colorToHSV(colorB, hsvB)
        val diff = abs(hsvA[0] - hsvB[0])
        return min(diff, 360f - diff)
    }
}
