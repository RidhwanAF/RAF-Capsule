package com.raf.capsuleos.presentation.capsule.ui

import kotlin.math.abs
import kotlin.math.sqrt

internal const val CAPSULE_SPRING_STIFFNESS = 410f
internal const val CAPSULE_SPRING_DAMPING = 0.82f
internal const val CAPSULE_BLUR_SPRING_STIFFNESS = 460f
internal const val CAPSULE_BLUR_SPRING_DAMPING = 0.96f
internal const val CAPSULE_JIGGLE_STIFFNESS = 520f
internal const val CAPSULE_JIGGLE_DAMPING = 0.40f
internal const val CAPSULE_JIGGLE_VELOCITY_DP_PER_SEC = 240f
internal const val CAPSULE_JIGGLE_DURATION_MS = 340L
internal const val CAPSULE_SCRUB_MAX_COAST_VELOCITY_PX = 4800f

/**
 * Semi-implicit Euler damped spring axis shared 1:1 between the live Accessibility overlay window
 * and the in-app interactive phone mockup so both render identical physical motion.
 */
internal class CapsuleSpringAxis(initialValue: Float = 0f) {
    var value: Float = initialValue
    var velocity: Float = 0f
    var target: Float = initialValue

    fun snapTo(v: Float) {
        value = v
        velocity = 0f
        target = v
    }

    fun step(dtSec: Float, stiffness: Float, dampingRatio: Float): Boolean {
        val displacement = value - target
        if (abs(displacement) < 0.35f && abs(velocity) < 1.5f) {
            value = target
            velocity = 0f
            return false
        }
        val damping = 2f * dampingRatio * sqrt(stiffness)
        val force = -stiffness * displacement - damping * velocity
        velocity += force * dtSec
        value += velocity * dtSec
        return true
    }
}

/** Linearly interpolates between [start] and [end] by [fraction]. */
internal fun lerpCapsuleFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
