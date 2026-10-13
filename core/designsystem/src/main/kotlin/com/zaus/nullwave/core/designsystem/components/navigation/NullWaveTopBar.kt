package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Screen heading with optional navigation and compact actions (usually one or two icon buttons).
 * Title wraps and the bar grows from 56 dp. Move additional actions into the caller's overflow menu.
 * Insets and navigation policy belong to the host. Slots receive [LocalContentColor]; components
 * with an explicit default tint, such as NullWaveIconButton, must be passed that color explicitly.
 */
@Composable
fun NullWaveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    require(title.isNotBlank()) { "Top bar title must not be blank" }
    TopBarContent(title, modifier, NullWaveTheme.colors.surface, NullWaveTheme.colors.textPrimary, navigationIcon, actions)
}

/**
 * Controlled selection mode. Zero is valid; the caller decides when to leave selection mode.
 * Actions inherit the dark foreground of the accent surface. Closing only invokes [onClose].
 * For NullWaveIconButton actions use tint = LocalContentColor.current, pressedTint = theme.secondary
 * and disabledTint = theme.bg, so every state contrasts with the accent surface.
 */
@Composable
fun NullWaveSelectionTopBar(
    selectionCount: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeDescription: String = stringResource(R.string.nw_selection_close),
    actions: @Composable RowScope.() -> Unit = {},
) {
    require(selectionCount >= 0) { "Selection count must not be negative" }
    require(closeDescription.isNotBlank()) { "Close description must not be blank" }
    val foreground = NullWaveTheme.colors.bg
    TopBarContent(
        title = stringResource(R.string.nw_selection_count, selectionCount),
        modifier = modifier,
        containerColor = NullWaveTheme.colors.secondary,
        contentColor = foreground,
        navigationIcon = {
            NullWaveIconButton(
                NullWaveIcons.Close, closeDescription, onClose,
                tint = foreground, pressedTint = NullWaveTheme.colors.secondary,
            )
        },
        actions = actions,
    )
}

@Composable
private fun TopBarContent(
    title: String,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier.fillMaxWidth().background(containerColor)
                .defaultMinSize(minHeight = TopBarMinHeight)
                .padding(horizontal = NullWaveTheme.spacing.xs, vertical = NullWaveTheme.spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon?.invoke()
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                color = contentColor,
                style = NullWaveTheme.typography.h2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

private val TopBarMinHeight = 56.dp
