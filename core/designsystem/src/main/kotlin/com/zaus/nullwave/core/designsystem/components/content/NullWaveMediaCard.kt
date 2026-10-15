package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.modifier.nullWaveBorder
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Album/artist/playlist tile; the caller owns its width and the surrounding grid.
 * [artwork] fills a square and must be decorative/noninteractive; its semantics are suppressed.
 * [badge] is noninteractive text, included in the card's accessible name. Labels stay single-line
 * with ellipsis and retain their full semantics. No marquee, loading job or entity policy here.
 * One open action for the whole tile; labels use the full width beneath the artwork.
 */
@Composable
fun NullWaveMediaGridCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    artwork: @Composable () -> Unit = { NullWaveCoverArt() },
    badge: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.ClippedCorners,
) {
    require(title.isNotBlank()) { "Media title must not be blank" }
    MediaCardContainer(onClick, modifier, enabled, interactionSource, borderStyle) {
        Column(Modifier.padding(NullWaveTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(NullWaveTheme.shapes.medium).clearAndSetSemantics {},
                propagateMinConstraints = true,
            ) { artwork() }
            MediaLabels(title, supportingText, enabled, Modifier.fillMaxWidth())
            badge?.invoke()
        }
    }
}

/**
 * Compact collection row, sharing card styling with [NullWaveMediaGridCard]. Artwork is 56 dp;
 * badge sits below labels so it cannot squeeze the title or overflow action. This is an open action,
 * not a track selection/playback control. Full metadata belongs to a details surface.
 * Optional overflow is a separate 48 dp action and requires a contextual [moreDescription].
 */
@Composable
fun NullWaveMediaListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    artwork: @Composable () -> Unit = { NullWaveCoverArt() },
    badge: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onMoreClick: (() -> Unit)? = null,
    moreDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    borderStyle: NullWaveBorderStyle = NullWaveBorderStyle.ClippedCorners,
) {
    require(title.isNotBlank()) { "Media title must not be blank" }
    require(onMoreClick == null || !moreDescription.isNullOrBlank()) { "More action needs a description" }
    MediaCardContainer(onClick, modifier, enabled, interactionSource, borderStyle) {
        Row(
            Modifier.padding(NullWaveTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(ListArtworkSize).clip(NullWaveTheme.shapes.small).clearAndSetSemantics {}, propagateMinConstraints = true) { artwork() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                MediaLabels(title, supportingText, enabled)
                badge?.invoke()
            }
            if (onMoreClick != null) NullWaveIconButton(NullWaveIcons.More, requireNotNull(moreDescription), onMoreClick, enabled = enabled)
        }
    }
}

@Composable
private fun MediaLabels(title: String, supportingText: String?, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs)) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) NullWaveTheme.colors.textPrimary else NullWaveTheme.colors.textSecondary,
            style = NullWaveTheme.typography.bodyEmphasis)
        if (!supportingText.isNullOrBlank()) Text(supportingText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = NullWaveTheme.colors.textSecondary, style = NullWaveTheme.typography.caption)
    }
}

@Composable
private fun MediaCardContainer(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    borderStyle: NullWaveBorderStyle,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.medium
    Box(
        modifier.fillMaxWidth().defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .clip(shape)
            .background(if (enabled && pressed) colors.surfaceRaised else colors.surface)
            .nullWaveBorder(
                if (enabled && focused) BorderStroke(dimens.focusBorder, colors.textPrimary) else BorderStroke(dimens.hairline, colors.outline),
                shape, if (enabled && focused) NullWaveBorderStyle.Closed else borderStyle,
            )
            .clickable(
                interactionSource = source, indication = ripple(), enabled = enabled, role = Role.Button,
                onClick = { if (enabled) onClick() },
            ),
    ) { content() }
}

private val ListArtworkSize = 56.dp
