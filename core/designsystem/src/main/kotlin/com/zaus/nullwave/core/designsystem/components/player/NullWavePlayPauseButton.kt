package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

enum class NullWavePlayPauseButtonSize { Compact, Hero }

/**
 * Controlled playback action in a 48 dp compact or 76 dp hero octagon.
 * [isPlaying] describes playback intent, including while buffering: true always offers Pause.
 * Map an engine's play-when-ready state here, not an is-playing flag that becomes false on buffering.
 * [buffering] adds a decorative scanner and state description; it never disables the action or hides
 * its glyph. [enabled] alone controls availability. Reduced/system-disabled motion freezes the scan.
 * Parent constraints must allow at least the 48 dp touch target; do not shrink it with [modifier].
 * A supplied [interactionSource] remains owned by the caller.
 */
@Composable
fun NullWavePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    enabled: Boolean = true,
    size: NullWavePlayPauseButtonSize = NullWavePlayPauseButtonSize.Hero,
    reduceMotion: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.node
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val highlighted = enabled && pressed
    val focusedHighlighted = enabled && focused
    val container = if (highlighted) colors.surface else colors.primary
    val content = if (highlighted) colors.primary else colors.onPrimary
    val description = stringResource(if (isPlaying) R.string.nw_player_pause else R.string.nw_player_play)
    val bufferingDescription = if (buffering) stringResource(R.string.nw_player_buffering) else null
    val edge = if (size == NullWavePlayPauseButtonSize.Hero) HeroSize else dimens.touchTarget
    val glyph = if (size == NullWavePlayPauseButtonSize.Hero) HeroGlyphSize else dimens.icon

    IconButton(
        // Foundation's direct semantics action does not check enabled itself.
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .size(edge)
            .border(
                width = if (focusedHighlighted) dimens.focusBorder else dimens.hairline,
                color = when {
                    !enabled -> colors.outline
                    focusedHighlighted -> content
                    else -> colors.primary
                },
                shape = shape,
            )
            .semantics {
                contentDescription = description
                if (bufferingDescription != null) stateDescription = bufferingDescription
            },
        enabled = enabled,
        shape = shape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.surfaceRaised,
            disabledContentColor = colors.textSecondary,
        ),
        interactionSource = interactions,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NullWaveIcon(
                icon = if (isPlaying) NullWaveIcons.Pause else NullWaveIcons.Play,
                contentDescription = null,
                modifier = Modifier.size(glyph),
            )
            if (buffering) {
                PlaybackBufferingScan(
                    tint = if (enabled) content else colors.textSecondary,
                    reduceMotion = reduceMotion || !enabled,
                )
            }
        }
    }
}

/** Small lower rail leaves the centre glyph unobscured; neither a percentage nor a separate target. */
@Composable
private fun PlaybackBufferingScan(tint: Color, reduceMotion: Boolean) {
    val scope = rememberCoroutineScope()
    val animate = !reduceMotion && (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val phase = if (animate) {
        rememberInfiniteTransition(label = "Playback buffering").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BufferingPassMillis, easing = LinearEasing), RepeatMode.Reverse),
            label = "Buffering scan position",
        )
    } else rememberUpdatedState(0.5f)
    val stroke = NullWaveTheme.dimens.focusBorder
    Canvas(Modifier.fillMaxSize()) {
        val left = size.width * RailStartFraction
        val right = size.width * RailEndFraction
        val y = size.height * RailHeightFraction
        val width = stroke.toPx()
        drawLine(tint.copy(alpha = RailAlpha), Offset(left, y), Offset(right, y), width)
        val segment = (right - left) * SegmentFraction
        // Read only during drawing; the surrounding player does not recompose each frame.
        val start = left + (right - left - segment) * phase.value
        drawLine(tint, Offset(start, y), Offset(start + segment, y), width)
    }
}

private val HeroSize = 76.dp
private val HeroGlyphSize = 36.dp
private const val BufferingPassMillis = 900
private const val RailStartFraction = 0.32f
private const val RailEndFraction = 0.68f
private const val RailHeightFraction = 0.82f
private const val SegmentFraction = 0.4f
private const val RailAlpha = 0.35f
