package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

enum class NullWaveArtworkPlaceholder { Missing, Loading, Error }

/**
 * Display only: a supplied [painter] takes precedence; otherwise [placeholder] is shown.
 * [monogram] is ready-to-display artwork text (normally 1–2 glyphs), not an album/track lookup key.
 * Missing art uses a paper sleeve; loading/error use neutral/raised surfaces with distinct marks.
 * [contentDescription] describes the whole image/state, or null makes it entirely decorative.
 * No click, loading job, URI, generated catalogue id or hidden image loader lives here.
 * The decorative monogram scales to the actual tile rather than the user's body-text size.
 */
@Composable
fun NullWaveCoverArt(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    contentDescription: String? = null,
    monogram: String? = null,
    placeholder: NullWaveArtworkPlaceholder = NullWaveArtworkPlaceholder.Missing,
    size: Dp = 48.dp,
    shape: Shape = chamfer((size / 6f).coerceAtMost(22.dp)),
) {
    require(size.value.isFinite() && size > 0.dp) { "Artwork size must be finite and positive" }
    require(contentDescription == null || contentDescription.isNotBlank()) { "Artwork description must not be blank" }
    val colors = NullWaveTheme.colors
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.size(size).clip(shape)
            .background(if (painter == null && placeholder == NullWaveArtworkPlaceholder.Missing) ArtworkPaper else colors.surfaceRaised)
            .clearAndSetSemantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        val extent = minOf(maxWidth, maxHeight)
        when {
            painter != null -> Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            placeholder == NullWaveArtworkPlaceholder.Missing && !monogram.isNullOrBlank() && extent > 0.dp -> {
                BasicText(
                    monogram,
                    Modifier.padding(extent * 0.1f),
                    style = NullWaveTheme.typography.h2.copy(
                        color = colors.bg, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
                        lineHeight = 1.em,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = with(density) { (extent * 0.2f).toSp() },
                        maxFontSize = with(density) { (extent * 0.458f).toSp() },
                    ),
                )
            }
            else -> NullWaveIcon(
                icon = when (placeholder) {
                    NullWaveArtworkPlaceholder.Missing -> NullWaveIcons.Songs
                    NullWaveArtworkPlaceholder.Loading -> NullWaveIcons.More
                    NullWaveArtworkPlaceholder.Error -> NullWaveIcons.Warning
                },
                contentDescription = null,
                modifier = Modifier.size(extent * 0.5f),
                tint = if (placeholder == NullWaveArtworkPlaceholder.Missing) colors.bg else colors.textSecondary,
            )
        }
    }
}

/** Sleeve color from the artwork design, not a light application surface. */
private val ArtworkPaper = Color(0xFFEDE8DC)
