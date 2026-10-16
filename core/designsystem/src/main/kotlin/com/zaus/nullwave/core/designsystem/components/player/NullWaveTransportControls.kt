package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.control.toggleButton.NullWaveIconToggleButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Display state only. The caller decides how a repeat click changes its playback mode. */
enum class NullWaveRepeatMode { Off, All, One }

/**
 * Controlled transport group. [isPlaying] means playback intent, including while buffering.
 * Previous/next availability and repeat/shuffle state come from the caller. No queue policy here.
 * Null optional callbacks omit those controls. At narrow widths shuffle/repeat move below the
 * main controls; targets never shrink. Playback glyphs retain their direction in RTL layouts.
 * [playPauseGlowEnabled] lets the host hand the accent to SeekBar while scrubbing, or disable decor.
 */
@Composable
fun NullWaveTransportControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    enabled: Boolean = true,
    canPlayPause: Boolean = true,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
    shuffle: Boolean = false,
    onShuffleChange: ((Boolean) -> Unit)? = null,
    repeatMode: NullWaveRepeatMode = NullWaveRepeatMode.Off,
    onRepeatClick: (() -> Unit)? = null,
    reduceMotion: Boolean = false,
    playPauseGlowEnabled: Boolean = true,
) {
    val spacing = NullWaveTheme.spacing
    val target = NullWaveTheme.dimens.touchTarget
    val hasModes = onShuffleChange != null || onRepeatClick != null
    val modeTint = if (enabled) NullWaveTheme.colors.primary else NullWaveTheme.colors.textSecondary
    val previous: @Composable () -> Unit = {
        NullWaveIconButton(NullWaveIcons.Previous, stringResource(R.string.nw_player_previous), onPrevious, enabled = enabled && canPrevious)
    }
    val playPause: @Composable () -> Unit = {
        NullWavePlayPauseButton(isPlaying, onPlayPause, buffering = buffering, enabled = enabled && canPlayPause,
            reduceMotion = reduceMotion, glowEnabled = playPauseGlowEnabled)
    }
    val next: @Composable () -> Unit = {
        NullWaveIconButton(NullWaveIcons.Next, stringResource(R.string.nw_player_next), onNext, enabled = enabled && canNext)
    }
    val shuffleControl: @Composable () -> Unit = {
        if (onShuffleChange != null) NullWaveIconToggleButton(
            NullWaveIcons.Shuffle, stringResource(R.string.nw_player_shuffle), shuffle, onShuffleChange,
            Modifier.modeMarker(shuffle, modeTint), enabled = enabled,
        ) else Spacer(Modifier.size(target))
    }
    val repeatControl: @Composable () -> Unit = {
        if (onRepeatClick != null) RepeatButton(repeatMode, onRepeatClick, enabled) else Spacer(Modifier.size(target))
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = hasModes && maxWidth >= HeroTransportSize + target * 4 + spacing.xxs * 4
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                shuffleControl(); previous(); playPause(); next(); repeatControl()
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs), itemVerticalAlignment = Alignment.CenterVertically) {
                    previous(); playPause(); next()
                }
                if (hasModes) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (onShuffleChange != null) shuffleControl()
                    if (onRepeatClick != null) repeatControl()
                }
            }
        }
    }
}

@Composable
private fun RepeatButton(mode: NullWaveRepeatMode, onClick: () -> Unit, enabled: Boolean) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val colors = NullWaveTheme.colors
    val tint = if (mode == NullWaveRepeatMode.Off) colors.textPrimary else colors.secondary
    val description = stringResource(when (mode) {
        NullWaveRepeatMode.Off -> R.string.nw_player_repeat_off
        NullWaveRepeatMode.All -> R.string.nw_player_repeat_all
        NullWaveRepeatMode.One -> R.string.nw_player_repeat_one
    })
    Box(contentAlignment = Alignment.Center) {
        NullWaveIconButton(NullWaveIcons.Repeat, stringResource(R.string.nw_player_repeat), onClick,
            Modifier.semantics { stateDescription = description }
                .modeMarker(mode != NullWaveRepeatMode.Off, if (enabled) tint else colors.textSecondary),
            enabled = enabled, tint = tint, interactionSource = source)
        if (mode == NullWaveRepeatMode.One) {
            // A decorative glyph inside the repeat icon, scaled as an icon rather than body text.
            val glyphSize = with(LocalDensity.current) { RepeatOneSize.toSp() }
            Text("1", Modifier.clearAndSetSemantics {},
                color = when { !enabled -> colors.textSecondary; pressed -> colors.bg; else -> tint },
                style = NullWaveTheme.typography.mono.copy(fontSize = glyphSize, lineHeight = glyphSize, fontWeight = FontWeight.Bold))
        }
    }
}

private fun Modifier.modeMarker(active: Boolean, tint: Color): Modifier = if (!active) this else drawWithContent {
    drawContent()
    drawLine(tint, Offset(center.x - ModeMarkerWidth.toPx() / 2f, size.height - ModeMarkerInset.toPx()),
        Offset(center.x + ModeMarkerWidth.toPx() / 2f, size.height - ModeMarkerInset.toPx()), ModeMarkerStroke.toPx())
}

private val HeroTransportSize = 76.dp
private val RepeatOneSize = 10.dp
private val ModeMarkerWidth = 8.dp
private val ModeMarkerInset = 5.dp
private val ModeMarkerStroke = 2.dp
