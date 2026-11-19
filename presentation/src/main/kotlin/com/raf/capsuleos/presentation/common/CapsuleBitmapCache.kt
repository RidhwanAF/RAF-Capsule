package com.raf.capsuleos.presentation.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

/**
 * Lightweight process-wide LRU cache of decoded and pre-blurred [ImageBitmap] instances keyed by
 * byte-array identity & size so State 2 pills, State 3 backdrops, and shared-element album art
 * reuse cached bitmaps in 0ms without main-thread decode stalls or HWUI `RenderEffect` flicker.
 */
object CapsuleBitmapCache {
    private val cache = LruCache<Long, ImageBitmap>(24)
    private val blurredCache = LruCache<Long, ImageBitmap>(16)

    fun decodeOrGet(bytes: ByteArray?): ImageBitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val key = cacheKey(bytes)
        synchronized(cache) {
            cache.get(key)?.let { return it }
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return null
        synchronized(cache) {
            cache.put(key, decoded)
        }
        return decoded
    }

    /**
     * Produces and caches a soft pre-blurred backdrop bitmap (scaled down + multi-pass box blur)
     * so expanded media cards never flicker under HWUI `RenderEffect` + behind-window blur compositing.
     */
    fun decodeOrGetBlurred(bytes: ByteArray?): ImageBitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val key = cacheKey(bytes)
        synchronized(blurredCache) {
            blurredCache.get(key)?.let { return it }
        }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaled = raw.scale(width = 64, height = 64, filter = true)
        if (scaled !== raw) {
            raw.recycle()
        }
        val blurred = fastBoxBlur(scaled, radius = 6, passes = 2)
        if (blurred !== scaled) {
            scaled.recycle()
        }
        val result = blurred.asImageBitmap()
        synchronized(blurredCache) {
            blurredCache.put(key, result)
        }
        return result
    }

    private fun cacheKey(bytes: ByteArray): Long =
        (System.identityHashCode(bytes).toLong() shl 32) xor (bytes.size.toLong() and 0xFFFFFFFFL)

    private fun fastBoxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 2 || h <= 2 || radius <= 0) return src
        val pixels = IntArray(w * h)
        val temp = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val window = radius * 2 + 1

        repeat(passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowOff = y * w
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dx in -radius..radius) {
                    val px = pixels[rowOff + dx.coerceIn(0, w - 1)]
                    rSum += (px ushr 16) and 0xFF
                    gSum += (px ushr 8) and 0xFF
                    bSum += px and 0xFF
                }
                for (x in 0 until w) {
                    temp[rowOff + x] = (0xFF shl 24) or
                        ((rSum / window) shl 16) or
                        ((gSum / window) shl 8) or
                        (bSum / window)
                    val outPx = pixels[rowOff + (x - radius).coerceIn(0, w - 1)]
                    val inPx = pixels[rowOff + (x + radius + 1).coerceIn(0, w - 1)]
                    rSum += ((inPx ushr 16) and 0xFF) - ((outPx ushr 16) and 0xFF)
                    gSum += ((inPx ushr 8) and 0xFF) - ((outPx ushr 8) and 0xFF)
                    bSum += (inPx and 0xFF) - (outPx and 0xFF)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dy in -radius..radius) {
                    val px = temp[dy.coerceIn(0, h - 1) * w + x]
                    rSum += (px ushr 16) and 0xFF
                    gSum += (px ushr 8) and 0xFF
                    bSum += px and 0xFF
                }
                for (y in 0 until h) {
                    pixels[y * w + x] = (0xFF shl 24) or
                        ((rSum / window) shl 16) or
                        ((gSum / window) shl 8) or
                        (bSum / window)
                    val outPx = temp[(y - radius).coerceIn(0, h - 1) * w + x]
                    val inPx = temp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                    rSum += ((inPx ushr 16) and 0xFF) - ((outPx ushr 16) and 0xFF)
                    gSum += ((inPx ushr 8) and 0xFF) - ((outPx ushr 8) and 0xFF)
                    bSum += (inPx and 0xFF) - (outPx and 0xFF)
                }
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}

/** Remembers a cached [ImageBitmap] decoded from [bytes] without redundant main-thread decodes across transitions. */
@Composable
fun rememberCachedImageBitmap(bytes: ByteArray?): ImageBitmap? =
    remember(bytes) { CapsuleBitmapCache.decodeOrGet(bytes) }

/** Remembers a cached pre-blurred backdrop [ImageBitmap] for flicker-free expanded media cards. */
@Composable
fun rememberCachedBlurredImageBitmap(bytes: ByteArray?): ImageBitmap? =
    remember(bytes) { CapsuleBitmapCache.decodeOrGetBlurred(bytes) }
