package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.checkbox.NullWaveCheckbox
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.motion.systemAnimationsEnabled
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Stateless track display. [playing] and [selected] are independent. Selection replaces artwork
 * with a decorative checkbox; [selectionMode] also shows unchecked rows. The whole row handles it.
 * Click/long-click policy belongs to the caller, including entering/exiting selection and playback.
 * [artwork] must be decorative, noninteractive content fitting 48 dp. Its semantics are suppressed.
 * [onMoreClick] exposes the extra action as a separate 48 dp target, also disabled by [enabled].
 * Title and subtitle each occupy at most one line; subtitle is static with ellipsis on overflow.
 * Long titles scroll unless [reduceMotion] or system animations are disabled, then use ellipsis.
 * Full text remains available to accessibility. Height grows from 72 dp with font scale, not tag length.
 * [durationLabel] is preformatted, never computed from media.
 * On narrow windows/large text, metadata moves below the title to preserve readable space.
 */
@Composable
fun NullWaveTrackRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    durationLabel: String? = null,
    playing: Boolean = false,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    enabled: Boolean = true,
    artwork: @Composable () -> Unit = { NullWaveCoverArt() },
    onMoreClick: (() -> Unit)? = null,
    moreDescription: String = stringResource(R.string.nw_track_more, title),
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    reduceMotion: Boolean = false,
) {
    require(title.isNotBlank()) { "Track title must not be blank" }
    require(onMoreClick == null || moreDescription.isNotBlank()) { "More action needs a description" }
    require(onLongClick == null || !longClickLabel.isNullOrBlank()) { "Long click needs an action label" }
    val colors = NullWaveTheme.colors
    val spacing = NullWaveTheme.spacing
    val dimens = NullWaveTheme.dimens
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // basicMarquee uses a fixed internal duration scale, so gate it explicitly for reduced motion.
    val animateTitle = !reduceMotion && systemAnimationsEnabled()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val choosing = selectionMode || selected
    val playingDescription = if (playing) stringResource(
        when {
            !choosing -> R.string.nw_track_playing
            selected -> R.string.nw_track_selected_playing
            else -> R.string.nw_track_unselected_playing
        },
    ) else null
    val spine = when {
        !enabled -> colors.textSecondary
        selected -> colors.secondary
        else -> colors.primary
    }
    val background = when {
        selected && enabled -> colors.secondary.copy(alpha = SelectedTintAlpha)
        playing || selected || enabled && pressed -> colors.surfaceRaised
        else -> colors.bg
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val metadataBelow = maxWidth < InlineMetadataMinWidth || LocalDensity.current.fontScale > InlineMetadataMaxFontScale
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = TrackRowMinHeight)
                .background(background)
                .drawBehind {
                    if (selected || playing) {
                        val width = SpineWidth.toPx().coerceAtMost(size.width)
                        drawRect(spine, Offset(if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width, 0f), Size(width, size.height))
                    }
                }
                .then(if (enabled && focused) Modifier.border(dimens.focusBorder, colors.textPrimary, RectangleShape) else Modifier)
                .semantics {
                    if (choosing) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                    if (playingDescription != null) stateDescription = playingDescription
                }
                .combinedClickable(
                    interactionSource = source, indication = ripple(), enabled = enabled,
                    role = if (choosing) Role.Checkbox else Role.Button,
                    onClick = { if (enabled) onClick() },
                    onLongClick = onLongClick?.let { action -> { if (enabled) action() } },
                    onLongClickLabel = longClickLabel,
                )
                .padding(horizontal = dimens.gutter, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(ArtworkSize).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                if (choosing) NullWaveCheckbox(selected, onCheckedChange = null, enabled = enabled) else artwork()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    title,
                    modifier = if (animateTitle) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    maxLines = 1,
                    overflow = if (animateTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                    color = when {
                        !enabled -> colors.textSecondary
                        playing -> colors.primary
                        else -> colors.textPrimary
                    },
                    style = NullWaveTheme.typography.bodyEmphasis,
                )
                if (!subtitle.isNullOrBlank()) Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textSecondary,
                    style = NullWaveTheme.typography.caption,
                )
                if (metadataBelow) TrackMetadata(durationLabel, playing, enabled)
            }
            if (!metadataBelow) TrackMetadata(durationLabel, playing, enabled)
            if (onMoreClick != null) NullWaveIconButton(NullWaveIcons.More, moreDescription, onMoreClick, enabled = enabled)
        }
    }
}

@Composable
private fun TrackMetadata(durationLabel: String?, playing: Boolean, enabled: Boolean) {
    if (durationLabel.isNullOrBlank() && !playing && enabled) return
    Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (playing) NullWaveIcon(
            NullWaveIcons.Waveform, null, Modifier.size(PlaybackMarkSize),
            tint = if (enabled) NullWaveTheme.colors.primary else NullWaveTheme.colors.textSecondary,
        )
        Column {
            if (!durationLabel.isNullOrBlank()) Text(durationLabel, style = NullWaveTheme.typography.mono, color = NullWaveTheme.colors.textSecondary)
            if (!enabled) Text(stringResource(R.string.nw_track_unavailable), style = NullWaveTheme.typography.caption, color = NullWaveTheme.colors.textSecondary)
        }
    }
}

private val TrackRowMinHeight = 72.dp
private val ArtworkSize = 48.dp
private val PlaybackMarkSize = 16.dp
private val SpineWidth = 3.dp
private val InlineMetadataMinWidth = 360.dp
private const val InlineMetadataMaxFontScale = 1.3f
private const val SelectedTintAlpha = 0.06f
