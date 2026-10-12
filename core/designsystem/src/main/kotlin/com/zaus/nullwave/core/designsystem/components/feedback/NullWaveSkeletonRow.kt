package com.zaus.nullwave.core.designsystem.components.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Static, decorative placeholder: cover, two text blocks and optional trailing metadata.
 * The enclosing region supplies one loading announcement. This row has no actions or fake text.
 * Text slots follow theme line heights/font scale; reconcile geometry when TrackRow is introduced.
 */
@Composable
fun NullWaveSkeletonRow(
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    showTrailing: Boolean = true,
) {
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val typography = NullWaveTheme.typography
    val density = LocalDensity.current
    val titleHeight = with(density) { typography.body.lineHeight.toDp() }
    val subtitleHeight = with(density) { typography.caption.lineHeight.toDp() }
    val metadataHeight = with(density) { typography.mono.lineHeight.toDp() }
    Row(
        modifier = modifier
            .clearAndSetSemantics {}
            .fillMaxWidth()
            .heightIn(min = SkeletonRowMinHeight)
            .padding(horizontal = NullWaveTheme.dimens.gutter, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Box(Modifier.size(SkeletonArtworkSize).background(colors.surfaceRaised, NullWaveTheme.shapes.small))
        }
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth(0.7f).height(titleHeight / 2).background(colors.surfaceRaised))
            }
            Box(Modifier.fillMaxWidth().height(subtitleHeight), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth(0.45f).height(subtitleHeight / 2).background(colors.surfaceRaised))
            }
        }
        if (showTrailing) {
            Box(
                Modifier.width(metadataHeight * 2).height(metadataHeight / 2)
                    .background(colors.surfaceRaised),
            )
        }
    }
}

private val SkeletonArtworkSize = 48.dp
private val SkeletonRowMinHeight = 72.dp
