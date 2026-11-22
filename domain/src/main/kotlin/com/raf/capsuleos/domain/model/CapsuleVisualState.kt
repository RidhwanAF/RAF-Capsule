package com.raf.capsuleos.domain.model

/**
 * Discrete visual states for the dynamic cutout capsule.
 */
enum class CapsuleVisualState {
    /** Hidden completely while on AOD/Doze, screen off, or paused. */
    HIDDEN_SYSTEM,

    /** Minimal 1:1 camera-cutout ring showing battery level (and color-coded charge progress). */
    STATE1_CUTOUT_ANCHOR,

    /** Horizontal top-bar expansion displaying active media, notification, volume, or charge/battery peek. */
    STATE2_HORIZONTAL_PILL,

    /** Full downward-expanded interactive control card triggered by hold or expandable pill tap. */
    STATE3_FULL_EXPANDED,
}
