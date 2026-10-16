package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Display-only compact player. The host owns visibility, insets, expansion and playback.
 * [isPlaying] describes playback intent, not whether audio is currently flowing during buffering.
 * Artwork/title form the expand target; play/next are sibling targets (disabled taps cannot expand).
 * [playbackEnabled] gates transport only, leaving details reachable. [progress] is a finite 0..1
 * fraction, clamped for display; null omits it. The thin strip is decorative, never a seek target.
 * [artwork] fills 40 dp and must be decorative/noninteractive. Long titles marquee; subtitles stay
 * single-line ellipsis. [reduceMotion] or system scale=0 makes the title single-line ellipsis too.
 * [playPauseGlowEnabled] controls a compact static halo on Play/Pause, not on the whole panel.
 * [progressGlowEnabled] controls the secondary halo along the known, nonzero progress strip.
 * The host can suppress it while another visible player surface owns the accent (e.g. seeking).
 */
@Composable
fun NullWaveMiniPlayer(
    title: String,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    artwork: @Composable () -> Unit = { NullWaveCoverArt() },
    progress: Float? = null,
    buffering: Boolean = false,
    playbackEnabled: Boolean = true,
    canNext: Boolean = true,
    reduceMotion: Boolean = false,
    expandInteractionSource: MutableInteractionSource? = null,
    playPauseGlowEnabled: Boolean = true,
    progressGlowEnabled: Boolean = true,
) {
    require(title.isNotBlank()) { "MiniPlayer title must not be blank" }
    require(progress == null || progress.isFinite()) { "Unknown progress must be null, not NaN/Infinity" }
    val fraction = progress?.coerceIn(0f, 1f)
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val spacing = NullWaveTheme.spacing
    val source = expandInteractionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    val animateTitle = !reduceMotion && (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = MiniPlayerMinHeight).background(colors.surface)
            .drawWithCache {
                val glow = PlayerLineGlow(colors.secondary, MiniProgressGlowRadius.toPx(), MiniProgressGlowAlpha)
                val stroke = MiniProgressHeight.toPx()
                onDrawBehind {
                    // The divider is behind the halo, not a dark cut across its upper half.
                    drawRect(colors.outline, size = Size(size.width, dimens.hairline.toPx()))
                    val width = size.width * (fraction ?: 0f)
                    val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
                    if (width > 0f) {
                        // Round caps belong inside the actual progress length, including tiny values.
                        val coreWidth = minOf(stroke, width)
                        val start = Offset(left + coreWidth / 2f, stroke / 2f)
                        val end = Offset(left + width - coreWidth / 2f, stroke / 2f)
                        if (playbackEnabled && progressGlowEnabled) {
                            glow.drawLine(this, start, end, coreWidth)
                        }
                        drawLine(colors.secondary, start, end, coreWidth, StrokeCap.Round)
                    }
                }
            }
            .padding(horizontal = dimens.gutter, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).defaultMinSize(minHeight = dimens.touchTarget)
                .then(if (focused) Modifier.border(dimens.focusBorder, colors.textPrimary, RectangleShape) else Modifier)
                .clickable(source, ripple(), role = Role.Button, onClickLabel = stringResource(R.string.nw_player_expand), onClick = onExpand),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(MiniArtworkSize).clearAndSetSemantics {}, propagateMinConstraints = true) { artwork() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(title, if (animateTitle) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    maxLines = 1, overflow = if (animateTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                    color = colors.textPrimary, style = NullWaveTheme.typography.bodyEmphasis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = colors.textSecondary, style = NullWaveTheme.typography.caption)
            }
        }
        NullWavePlayPauseButton(isPlaying, onPlayPause, buffering = buffering, enabled = playbackEnabled,
            size = NullWavePlayPauseButtonSize.Compact, reduceMotion = reduceMotion, glowEnabled = playPauseGlowEnabled)
        NullWaveIconButton(NullWaveIcons.Next, stringResource(R.string.nw_player_next), onNext, enabled = playbackEnabled && canNext)
    }
}

private val MiniPlayerMinHeight = 64.dp
private val MiniArtworkSize = 40.dp
private val MiniProgressHeight = 2.dp
private val MiniProgressGlowRadius = 6.dp
private const val MiniProgressGlowAlpha = 0.6f
