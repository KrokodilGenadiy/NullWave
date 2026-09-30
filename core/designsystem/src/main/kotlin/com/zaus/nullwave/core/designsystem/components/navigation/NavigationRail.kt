package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Icons only, label underneath. The wide-window collapsed state. */
val RailCollapsedWidth: Dp = 80.dp

/** Icons beside labels. Also the width used when the rail stands in for the drawer. */
val RailExpandedWidth: Dp = 240.dp

/**
 * The left navigation surface.
 *
 * Deliberately not Material's `NavigationRail`: its active indicator is drawn with
 * `ShapeKeyTokens.CornerFull`, which resolves to `CircleShape` in Material's source and is not
 * exposed as a parameter - so it would always be a rounded pill, which rule 1 of this system
 * forbids. Selection here is the game's left accent spine: a 3dp leading bar plus a tinted fill.
 *
 * Knows nothing about navigation - callers pass `isSelected` and `onClick`. That keeps
 * `:core:designsystem` dependency-free.
 *
 * [collapsedWidth] is what makes one component serve both roles:
 * - `RailCollapsedWidth` - a permanent rail that shrinks to icons. The wide-window form.
 * - `0.dp` - the rail disappears when closed, so it behaves as a drawer. The phone form.
 *
 * In drawer mode pass an [expandedWidth] measured against the window, so a strip of content stays
 * visible and the user can see there is something to tap back to. See the caller in MainActivity.
 *
 * [content] receives the expanded flag so items do not each need the state threaded in by hand.
 */
@Composable
fun NullWaveNavigationRail(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    collapsedWidth: Dp = RailCollapsedWidth,
    expandedWidth: Dp = RailExpandedWidth,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.(isExpanded: Boolean) -> Unit,
) {
    val colors = NullWaveTheme.colors
    val motion = NullWaveTheme.motion

    // The width is FIXED for a given state - deliberately NOT animated.
    //
    // Animating the width re-measures every child on every frame: at 40dp the labels ellipsize, at
    // 120dp they partly fit, at 320dp they fit. That is the squeezing. Compose is not doing
    // anything wrong, we were just asking it to re-lay-out the content 18 times.
    //
    // Instead the content is measured once at its final width and slid into place. slideIn only
    // changes where the child is PLACED, never how it is measured, so the labels are laid out once
    // and the drawer moves as a solid object.
    val width = if (isExpanded) expandedWidth else collapsedWidth

    // Drawer mode: the surface only ever exists on screen in its open state - closed means gone.
    val isDrawerMode = collapsedWidth <= 0.dp

    // What the content is drawn AS, which is not the same as what the caller has asked for.
    //
    // The moment isExpanded flips to false the exit animation starts, but the content is still on
    // screen for another 300ms. Reading isExpanded directly here would measure it at collapsedWidth
    // (0dp in drawer mode) on the very first frame, so it would blink out instead of sliding, and
    // the labels would switch to their collapsed styling mid-flight. Freeze both for the exit.
    val renderedWidth = if (isDrawerMode) expandedWidth else width
    val renderedExpanded = if (isDrawerMode) true else isExpanded

    AnimatedVisibility(
        // Closed at zero width: not composed at all, so nothing for a screen reader to find either.
        visible = width > 0.dp,
        // drawerMillis, not stateMillis: 120ms across 300-odd dp is too fast to read as a movement.
        enter = slideInHorizontally(
            animationSpec = tween(motion.drawerMillis, easing = motion.easing),
        ) { fullWidth -> -fullWidth },
        exit = slideOutHorizontally(
            animationSpec = tween(motion.drawerMillis, easing = motion.easing),
        ) { fullWidth -> -fullWidth },
        modifier = modifier,
    ) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(renderedWidth)
            .background(colors.surface)
            // Hairline framing, never a shadow.
            .drawBehind {
                drawRect(
                    color = colors.outline,
                    topLeft = Offset(size.width - 1.dp.toPx(), 0f),
                    size = Size(1.dp.toPx(), size.height),
                )
            }
            // Bottom, not Vertical: NullWaveTopBar owns the top inset now, so consuming it here
            // too would leave a double gap above the first item.
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start)
            )
            .padding(vertical = NullWaveTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs),
    ) {
        header()
        content(renderedExpanded)
    }
    }
}

/**
 * One rail destination. 64dp tall, so it clears the 48dp touch-target floor with room for a label.
 *
 * The two layouts share one declaration of their contents - see [itemContent] below. Only the
 * container that places them changes, which is why widening the rail does not duplicate anything.
 */
@Composable
fun ColumnScope.NullWaveNavigationRailItem(
    @DrawableRes icon: Int,
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val contentColor = if (isSelected) colors.primary else colors.textSecondary

    val itemContent: @Composable () -> Unit = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(dimens.icon),
        )
        Text(
            // Expanded has room for sentence case at body size; collapsed only fits a micro label.
            text = if (isExpanded) label else label.uppercase(),
            style = if (isExpanded) NullWaveTheme.typography.body else NullWaveTheme.typography.micro,
            color = contentColor,
            textAlign = if (isExpanded) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.rowHeight)
            .clickable(onClick = onClick)
            // Selected rows get a tinted fill plus the leading spine - the journal/message-list
            // treatment from the reference material. Identical in both states: toggling the width
            // must not change what "selected" looks like.
            .background(if (isSelected) colors.surfaceRaised else colors.surface)
            .drawBehind {
                if (isSelected) {
                    drawRect(
                        color = colors.primary,
                        topLeft = Offset.Zero,
                        size = Size(dimens.accentSpine.toPx(), size.height),
                    )
                }
            },
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NullWaveTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) { itemContent() }
/*        if (isExpanded) {
            Row(
                modifier = Modifier.padding(horizontal = NullWaveTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) { itemContent() }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs),
            ) { itemContent() }
        }*/
    }
}

/**
 * The rail header: the drawer's octagonal avatar, shrunk to rail width.
 */
@Composable
fun ColumnScope.NullWaveNavigationRailHeader(
    initials: String,
    modifier: Modifier = Modifier,
) {
    val colors = NullWaveTheme.colors
    Box(
        modifier = modifier
            .padding(bottom = NullWaveTheme.spacing.sm)
            .size(40.dp)
            .background(colors.surfaceRaised, NullWaveTheme.shapes.node),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = NullWaveTheme.typography.micro,
            color = colors.primary,
        )
    }
}

@Preview(name = "Rail - collapsed", widthDp = 120, heightDp = 480, showBackground = true)
@Composable
private fun RailCollapsedPreview() {
    NullWaveTheme {
        NullWaveNavigationRail(
            isExpanded = false,
            header = { NullWaveNavigationRailHeader(initials = "NW") },
        ) { expanded ->
            SampleRailItems(isExpanded = expanded)
        }
    }
}

@Preview(name = "Rail - expanded", widthDp = 280, heightDp = 480, showBackground = true)
@Composable
private fun RailExpandedPreview() {
    NullWaveTheme {
        NullWaveNavigationRail(
            isExpanded = true,
            header = { NullWaveNavigationRailHeader(initials = "NW") },
        ) { expanded ->
            SampleRailItems(isExpanded = expanded)
        }
    }
}

@Composable
private fun ColumnScope.SampleRailItems(isExpanded: Boolean) {
    NullWaveNavigationRailItem(
        icon = NullWaveIcons.NowPlaying,
        label = "Player",
        isSelected = true,
        isExpanded = isExpanded,
        onClick = {},
    )
    NullWaveNavigationRailItem(
        icon = NullWaveIcons.Equalizer,
        label = "Equalizer",
        isSelected = false,
        isExpanded = isExpanded,
        onClick = {},
    )
    NullWaveNavigationRailItem(
        icon = NullWaveIcons.SleepTimer,
        label = "Sleep",
        isSelected = false,
        isExpanded = isExpanded,
        onClick = {},
    )
    NullWaveNavigationRailItem(
        icon = NullWaveIcons.Settings,
        label = "Settings",
        isSelected = false,
        isExpanded = isExpanded,
        onClick = {},
    )
    NullWaveNavigationRailItem(
        icon = NullWaveIcons.Info,
        label = "About",
        isSelected = false,
        isExpanded = isExpanded,
        onClick = {},
    )
}
