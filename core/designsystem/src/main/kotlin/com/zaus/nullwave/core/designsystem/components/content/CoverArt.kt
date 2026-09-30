package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * The generated cover-art fallback: `Paper` ground, a heavy two-letter monogram in `bg`, a chamfer
 * cut on one corner and a mono catalogue string along the bottom edge.
 *
 * Deterministic - the same [title] always produces the same monogram and catalogue number, so a
 * track's tile looks identical everywhere it appears.
 *
 * ## The monogram rule, which the design corrected twice
 *
 * **The monogram derives from the entity the tile represents.** A track tile and an album tile both
 * take the *album* title; an artist tile takes the *artist* name. Getting this wrong is what made
 * the same track show `SP`, `DC` and `NW` on three different screens in an early round.
 *
 * When the album field is empty, `"Single"`, `"Unknown Album"` or another placeholder, fall back to
 * the *track* title - see [coverArtSource]. The catalogue string must come from the same value, or
 * the monogram and the number on one tile disagree.
 *
 * `Paper` is the only place this off-white appears. It is not a UI colour.
 *
 * ## Everything here scales with [size], because the design's does
 *
 * Measured across all 104 tiles in the artboards, which span 36dp to 364dp:
 *
 * | property | design | note |
 * | --- | --- | --- |
 * | monogram size | `0.458 * size` | constant ratio at *every* tile size, to three decimals |
 * | monogram tracking | `-0.02em` | tight, so it overrides `display`'s `+0.04em` |
 * | chamfer | `size / 6`, capped | 36→6, 48→8, 72→12 is exactly 1/6; large tiles flatten out |
 * | catalogue | bottom **left** | `~0.06 * size` in, `~0.05 * size` up - not centred |
 *
 * The chamfer cap is the one place this approximates. The design's cut grows sublinearly and then
 * stalls (140→18, 280→20, 364→22), which looks hand-picked per artboard rather than derived. `size/6`
 * is exact for the small tiles that actually appear in lists, and the cap stops a hero tile taking a
 * 60dp bite out of its corner.
 */
@Composable
fun NullWaveCoverArt(
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showCatalogue: Boolean = size >= CatalogueMinSize,
) {
    val colors = NullWaveTheme.colors
    val shape = chamfer((size / ChamferRatio).coerceAtMost(MaxChamfer))
    val monogramSize = (size.value * MonogramScale).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.paper),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogramOf(title),
            // The design's sleeve face is a heavy condensed display type that is not bundled yet;
            // Chakra Petch Bold stands in. See NOTES.md.
            style = NullWaveTheme.typography.display.copy(
                fontSize = monogramSize,
                // `display` carries a fixed 32sp line height, which at a 167sp monogram would be a
                // line box less than a fifth of the glyph's height. The design sets `line-height:1`,
                // so the line box tracks the font size.
                lineHeight = monogramSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = MonogramTracking,
            ),
            color = colors.bg,
            maxLines = 1,
        )

        if (showCatalogue) {
            Text(
                text = catalogueOf(title),
                style = NullWaveTheme.typography.micro.copy(
                    // Pinned at 7sp on the smaller tiles, exactly as the artboards do - below that
                    // it stops being readable at all.
                    //
                    // Clamped as a Float, not as a TextUnit: `TextUnit` declares a `compareTo`
                    // operator but does not implement `Comparable`, so `coerceAtLeast` does not
                    // apply to it.
                    fontSize = maxOf(size.value * CatalogueScale, CatalogueMinFontSize).sp,
                ),
                color = colors.bg.copy(alpha = CatalogueAlpha),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = size * CatalogueInsetX, bottom = size * CatalogueInsetY),
            )
        }
    }
}

/**
 * Which string a tile's monogram and catalogue number come from.
 *
 * Pass the album title and the track title; placeholders in the album field fall through to the
 * track. Artist tiles should pass the artist name as [album] and leave [track] null.
 */
fun coverArtSource(album: String?, track: String? = null): String {
    val cleaned = album?.trim().orEmpty()
    val isPlaceholder = cleaned.isEmpty() || AlbumPlaceholders.any { it.equals(cleaned, ignoreCase = true) }
    return if (isPlaceholder) track?.trim().orEmpty() else cleaned
}

/** Initials of the first two words, or the first two letters of a single word. */
private fun monogramOf(source: String): String {
    val words = source.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "??"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * `CAT·NW-014` — stable for a given source, so a tile and its monogram always agree.
 *
 * `mod`, not `absoluteValue %`: `Int.MIN_VALUE.absoluteValue` is still negative (it has no positive
 * counterpart), which would print `CAT·NW--48`. `mod` is non-negative for every input.
 */
private fun catalogueOf(source: String): String {
    val monogram = monogramOf(source)
    val number = source.hashCode().mod(CatalogueModulus).toString().padStart(3, '0')
    return "CAT·$monogram-$number"
}

private val AlbumPlaceholders = listOf("Single", "Unknown Album", "Unknown", "Various Artists")

private const val CatalogueModulus = 1000

/** 36→6, 48→8, 72→12 in the artboards. Exact for list-sized tiles. */
private const val ChamferRatio = 6f

/** Where the design's cut stops growing. A hero tile would otherwise lose 60dp of corner. */
private val MaxChamfer = 22.dp

/** Constant across all 104 tiles, from 36dp to 364dp. */
private const val MonogramScale = 0.458f
private val MonogramTracking = (-0.02).em

private const val CatalogueAlpha = 0.55f
private const val CatalogueScale = 0.045f
private const val CatalogueInsetX = 0.06f
private const val CatalogueInsetY = 0.05f
/** In sp, unwrapped, because it is clamped against a raw Float before becoming a `TextUnit`. */
private const val CatalogueMinFontSize = 7f

/** The smallest tile the artboards give a catalogue number to. */
private val CatalogueMinSize = 116.dp

@Preview(name = "Cover art fallback", showBackground = true)
@Composable
private fun CoverArtPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
        ) {
            NullWaveMicroLabel("row 48 · grid 96 · hero 160 — monogram holds 0.458 at every size")
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.Bottom,
            ) {
                NullWaveCoverArt("Null Wave")
                NullWaveCoverArt("Null Wave", size = 96.dp)
                NullWaveCoverArt("Null Wave", size = 160.dp)
            }

            NullWaveMicroLabel("the placeholder fallback — album 'Single' uses the track title")
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.Bottom,
            ) {
                NullWaveCoverArt(coverArtSource("Single", "Dry Season Signal"), size = 96.dp)
                NullWaveCoverArt(coverArtSource("Deadzone Chorus"), size = 96.dp)
                NullWaveCoverArt(coverArtSource(null, "Static Pilgrim"), size = 96.dp)
            }

            // 116dp is where the artboards start printing a catalogue number, bottom-left.
            NullWaveMicroLabel("catalogue number — appears from 116dp up, inset proportionally")
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
                verticalAlignment = Alignment.Bottom,
            ) {
                NullWaveCoverArt("Null Wave", size = 116.dp)
                NullWaveCoverArt("Null Wave", size = 200.dp)
            }
        }
    }
}
