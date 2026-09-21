package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale: 4 / 8 / 12 / 16 / 24 / 32 / 48. Nothing off-scale.
 */
@Immutable
data class NullWaveSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
) {
    companion object {
        val Default: NullWaveSpacing = NullWaveSpacing()
    }
}

/**
 * Fixed measurements the components sheet specifies by name. These are not free parameters -
 * a track row is 64dp because the design says so, and the mini-player bar is 64dp to match it.
 */
@Immutable
data class NullWaveDimens(
    /** Screen gutter. */
    val gutter: Dp = 16.dp,
    /** Standard list row. */
    val rowHeight: Dp = 64.dp,
    /** List row carrying artwork. */
    val rowHeightArtwork: Dp = 72.dp,
    /** Minimum touch target. Nothing interactive goes below this. */
    val touchTarget: Dp = 48.dp,
    /** Top app bar. */
    val topBarHeight: Dp = 56.dp,
    /** Collapsed mini-player bar, docked above the content's bottom edge. */
    val miniPlayerHeight: Dp = 64.dp,
    /** The mini-player's progress hairline, across the bar's top edge. */
    val miniPlayerProgressHeight: Dp = 2.dp,
    /** Every border and divider in the system. */
    val hairline: Dp = 1.dp,
    /** The leading vertical bar on a selected row. */
    val accentSpine: Dp = 3.dp,
    /** The leading quality-tier stripe on a track row. */
    val tierStripe: Dp = 3.dp,
    /** L-shaped corner ticks on emphasised containers. */
    val cornerTick: Dp = 10.dp,
    /** Icon box. The live area inside it is 20dp. */
    val icon: Dp = 24.dp,
    /** Icon stroke weight. Secondary / dim icons use 1dp. */
    val iconStroke: Dp = 2.dp,
    /** Visible height of the expanded player's waveform seek bar. */
    val seekWaveformHeight: Dp = 32.dp,
    /** The playhead cutting through the waveform. */
    val seekPlayheadWidth: Dp = 2.dp,
    /** The octagon handle on every slider in the app. */
    val sliderNode: Dp = 28.dp,
    /** EQ band track width. */
    val eqTrackWidth: Dp = 4.dp,
    /** Touch target per EQ band. */
    val eqBandWidth: Dp = 40.dp,
    /**
     * Minimum length of a horizontal slider track.
     *
     * A 28dp node on a short track reads as nearly full long before it is - at 176dp the node is
     * a sixth of the track and the value reads correctly. Applies to bass boost, crossfade and
     * every other horizontal slider.
     */
    val horizontalSliderTrackMin: Dp = 176.dp,
    /** The active-element glow radius. Reserved for the current element only. */
    val glowRadius: Dp = 14.dp,
) {
    companion object {
        val Default: NullWaveDimens = NullWaveDimens()
    }
}
