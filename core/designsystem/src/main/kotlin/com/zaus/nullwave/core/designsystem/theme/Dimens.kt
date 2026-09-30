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
    /** Arm length of the L-shaped corner ticks on emphasised containers. Artboards use 14-16px. */
    val cornerTick: Dp = 14.dp,
    /**
     * How far a corner tick sits inside the container's edge.
     *
     * Substantial on purpose. The artboards inset by 10-16px so the tick reads as a deliberate
     * inner mark; a tick placed a pixel or two off the border instead looks like a failed attempt
     * to align with it. The inset also carries it clear of a chamfered corner's 45-degree cut.
     */
    val cornerTickInset: Dp = 12.dp,
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
    /**
     * Outward extent of the tight activation glow - nodes, the playhead, indicators. The design's
     * dominant value is a `0 0 8px` CSS blur, and a CSS blur transitions across its radius *centred
     * on the edge*, so only about half of it spreads outward. Hence 6dp, not 8.
     */
    val glowRadius: Dp = 6.dp,
    /**
     * Outward extent of the wide, near-invisible wash the design pairs with the 3dp accent spine on
     * selected rows (`0 0 18px` at 0.08 alpha). Emphasis, not activation - a whole row glowing as
     * brightly as a node would break the "one active element" rule.
     */
    val glowWashRadius: Dp = 10.dp,
) {
    companion object {
        val Default: NullWaveDimens = NullWaveDimens()
    }
}
