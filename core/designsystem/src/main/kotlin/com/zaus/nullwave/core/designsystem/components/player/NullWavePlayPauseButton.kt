package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Engine loading phases are not part of this UI API. [enabled] alone controls availability.
 * [glowEnabled] controls the static halo independently of motion. A containing player switches it
 * off while seeking, handing the visual accent to the seek node. Disabled buttons never glow.
 * [glowEnabled] only restricts [NullWaveTheme.effects]; it cannot re-enable its effects.
 * Leave room around the button for the halo; applying a clip outside it will trim the decoration.
 * Parent constraints must allow at least the 48 dp touch target; do not shrink it with [modifier].
 * A supplied [interactionSource] remains owned by the caller.
 */
@Composable
fun NullWavePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: NullWavePlayPauseButtonSize = NullWavePlayPauseButtonSize.Hero,
    interactionSource: MutableInteractionSource? = null,
    glowEnabled: Boolean = true,
) {
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val shape = NullWaveTheme.shapes.node
    val effects = NullWaveTheme.effects.withRestrictions(glowEnabled = glowEnabled)
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val highlighted = enabled && pressed
    val focusedHighlighted = enabled && focused
    val container = if (highlighted) colors.surface else colors.primary
    val content = if (highlighted) colors.primary else colors.onPrimary
    val description = stringResource(if (isPlaying) R.string.nw_player_pause else R.string.nw_player_play)
    val edge = if (size == NullWavePlayPauseButtonSize.Hero) HeroPlayPauseSize else dimens.touchTarget
    val glyph = if (size == NullWavePlayPauseButtonSize.Hero) HeroGlyphSize else dimens.icon

    IconButton(
        // Foundation's direct semantics action does not check enabled itself.
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .size(edge)
            .playerGlow(
                style = if (size == NullWavePlayPauseButtonSize.Hero) PlayerGlow.Hero else PlayerGlow.Compact,
                shape = shape,
                color = colors.primary,
                enabled = enabled && effects.glowEnabled,
            )
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
        NullWaveIcon(
            icon = if (isPlaying) NullWaveIcons.Pause else NullWaveIcons.Play,
            contentDescription = null,
            modifier = Modifier.size(glyph),
        )
    }
}

/** Also used by the transport group's breakpoint so it matches the actual button. */
internal val HeroPlayPauseSize = 76.dp
private val HeroGlyphSize = 36.dp
