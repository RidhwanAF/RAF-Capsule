package com.raf.capsuleos.presentation.capsule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * Produces a monotonic shader clock in seconds synchronized with the display refresh rate
 * while [isAnimating] is true, halting frame callbacks when the capsule is idle or hidden.
 *
 * @param isAnimating Whether continuous GPU shader animation should advance.
 * @return A Compose [State] holding elapsed animation time in seconds.
 */
@Composable
fun rememberShaderTimeSeconds(isAnimating: Boolean): State<Float> {
    val timeState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect
        var startNanos = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                if (startNanos < 0L) startNanos = frameNanos
                timeState.floatValue = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }

    return timeState
}
