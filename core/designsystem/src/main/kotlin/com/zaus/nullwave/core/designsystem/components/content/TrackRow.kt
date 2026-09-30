package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.activeGlow
import com.zaus.nullwave.core.designsystem.components.control.NullWaveCheckbox
import com.zaus.nullwave.core.designsystem.components.control.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.QualityTierColors

/** What a row is currently doing. Only one row on a screen may be [Playing]. */
enum class TrackRowState { Rest, Playing, Selected }

/**
 * The workhorse of the app. Most screens are made of these.
 *
 * ## The three states, straight from the component sheet
 *
 * | state | treatment |
 * | --- | --- |
 * | rest | no fill |
 * | playing | `surfaceRaised` fill + 3dp `primary` spine + the 0.08 primary wash |
 * | selected | `secondary` at 6% + 3dp `secondary` spine, no wash |
 *
 * Playing is `primary`, selected is `secondary`, which is what keeps "now playing" and "picked in
 * selection mode" from reading as the same thing.
 *
 * They are **mutually exclusive here**, because [TrackRowState] is an enum and the artboards never
 * draw a combined treatment. A row that is both playing and selected has to pick one, and
 * [TrackRowState.Selected] is the right choice: in selection mode the selection is what the user is
 * currently manipulating. If the combined case ever needs its own look, this becomes two booleans -
 * see NOTES.md.
 *
 * ## Spine and tier stripe coexist
 *
 * The 3dp accent spine runs the **full height**; the quality-tier stripe is the same width but
 * **inset 14dp top and bottom**, so on a playing row you see spine at both ends and tier in the
 * middle. That is the design's own geometry, not a compromise.
 *
 * The tier stripe only appears when the caller passes one - it is gated behind a Settings toggle,
 * so `tierColor` is null by default. Tier colours are a fixed encoding and never change with the
 * theme variant; see [QualityTierColors].
 */
@Composable
fun NullWaveTrackRow(
    title: String,
    artist: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: TrackRowState = TrackRowState.Rest,
    tierColor: Color? = null,
    // 72dp is the artwork-carrying row, which is what every track list in the design uses. A row
    // with no `leading` slot belongs at `dimens.rowHeight` (64dp) instead - the design's plain list
    // row - so this is a parameter rather than a constant.
    height: Dp = NullWaveTheme.dimens.rowHeightArtwork,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens

    val spineColor = when (state) {
        TrackRowState.Rest -> null
        TrackRowState.Playing -> colors.primary
        TrackRowState.Selected -> colors.secondary
    }
    val background = when (state) {
        TrackRowState.Rest -> Color.Transparent
        TrackRowState.Playing -> colors.surfaceRaised
        TrackRowState.Selected -> colors.secondary.copy(alpha = SelectedTintAlpha)
    }
    val titleColor = if (state == TrackRowState.Playing) colors.primary else colors.textPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Only the playing row glows, and only as the wide, near-invisible wash - never the
            // tight activation glow, which would make a whole row shout.
            .then(
                if (state == TrackRowState.Playing) {
                    Modifier.activeGlow(
                        color = colors.primary,
                        radius = dimens.glowWashRadius,
                        alpha = WashAlpha,
                    )
                } else {
                    Modifier
                }
            )
            .background(background)
            .clickable(onClick = onClick)
            .drawBehind {
                // Full-height accent spine.
                spineColor?.let {
                    drawRect(
                        color = it,
                        size = Size(dimens.accentSpine.toPx(), size.height),
                    )
                }
                // Tier stripe, vertically inset so a spine can run behind it.
                tierColor?.let {
                    val inset = TierStripeInset.toPx()
                    drawRect(
                        color = it,
                        topLeft = Offset(0f, inset),
                        size = Size(dimens.tierStripe.toPx(), size.height - inset * 2f),
                    )
                }
            }
            .padding(horizontal = dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = NullWaveTheme.typography.body,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Only the playing row marquees. Every row scrolling at once is a slot machine.
                modifier = if (state == TrackRowState.Playing) Modifier.basicMarquee() else Modifier,
            )
            Text(
                text = artist,
                style = NullWaveTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = duration,
            style = NullWaveTheme.typography.mono,
            color = colors.textSecondary,
        )

        trailing()
    }
}

/** The design's `rgba(0,240,255,0.06)` selected tint. */
private const val SelectedTintAlpha = 0.06f
private const val WashAlpha = 0.08f
private val TierStripeInset = 14.dp

@Preview(name = "Track row", showBackground = true)
@Composable
private fun TrackRowPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier.background(colors.bg),
        ) {
            NullWaveTrackRow(
                title = "Deadzone Chorus",
                artist = "Static Pilgrim",
                duration = "3:47",
                onClick = {},
                tierColor = QualityTierColors.Lossless,
                leading = { NullWaveCoverArt(coverArtSource("Null Wave")) },
                trailing = {
                    NullWaveIconButton(NullWaveIcons.Overflow, "More", {})
                },
            )
            NullWaveTrackRow(
                title = "A Title Long Enough That It Has To Marquee Across The Row",
                artist = "Static Pilgrim",
                duration = "4:12",
                onClick = {},
                state = TrackRowState.Playing,
                tierColor = QualityTierColors.HiRes,
                leading = { NullWaveCoverArt(coverArtSource("Null Wave")) },
                trailing = {
                    NullWaveIconButton(NullWaveIcons.Overflow, "More", {})
                },
            )
            NullWaveTrackRow(
                title = "Dry Season Signal",
                artist = "Ash Vendor",
                duration = "2:58",
                onClick = {},
                state = TrackRowState.Selected,
                leading = {
                    NullWaveCheckbox(isChecked = true, onCheckedChange = {})
                },
            )
            NullWaveTrackRow(
                title = "Kilovolt Hymn",
                artist = "Mirror Halo",
                duration = "5:03",
                onClick = {},
                tierColor = QualityTierColors.Low,
                leading = { NullWaveCoverArt(coverArtSource("Single", "Kilovolt Hymn")) },
            )
        }
    }
}
