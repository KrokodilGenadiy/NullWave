package com.zaus.nullwave.core.designsystem.theme

/**
 * Host-owned visual preferences. Reduced motion and static glow are independent; disabling glow
 * never removes focus, selected-state borders, progress or other meaningful indicators.
 * Persistence belongs to the application/settings owner, not the design system.
 */
data class NullWaveEffects(
    val reduceMotion: Boolean = false,
    val glowEnabled: Boolean = true,
) {
    /** A nested theme or component may restrict effects, but cannot bypass its parent's choice. */
    fun withRestrictions(
        reduceMotion: Boolean = false,
        glowEnabled: Boolean = true,
    ): NullWaveEffects = NullWaveEffects(
        reduceMotion = this.reduceMotion || reduceMotion,
        glowEnabled = this.glowEnabled && glowEnabled,
    )

    companion object {
        val Default = NullWaveEffects()
    }
}
