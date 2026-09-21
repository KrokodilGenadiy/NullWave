package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Motion tokens. Snappy fast-out/slow-in throughout.
 *
 * There is exactly one signature transition: a [glitchMillis] RGB-split plus scanline sweep, fired
 * when the player sheet expands and when the tab row changes. It is never used anywhere else, and
 * [reduceMotion] must disable it in favour of a plain cross-fade.
 */
@Immutable
data class NullWaveMotion(
    /** State changes: pressed, selected, toggled. */
    val stateMillis: Int = 120,
    /** The player sheet settling between collapsed and expanded. */
    val sheetMillis: Int = 200,
    /** The navigation drawer. */
    val drawerMillis: Int = 300,
    /** The RGB-split + scanline sweep. Sheet expand and tab change only. */
    val glitchMillis: Int = 90,
    val easing: Easing = FastOutSlowIn,
    /**
     * When true, the glitch and the scanline sweep are replaced by a cross-fade. Wire this to the
     * platform's animator-duration setting and to the Appearance section of Settings.
     */
    val reduceMotion: Boolean = false,
) {
    companion object {
        /** Material's fast-out/slow-in curve, restated here so the token sheet is self-contained. */
        val FastOutSlowIn: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

        val Default: NullWaveMotion = NullWaveMotion()
    }
}
