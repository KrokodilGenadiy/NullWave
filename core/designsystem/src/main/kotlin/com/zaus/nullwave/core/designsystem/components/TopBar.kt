package com.zaus.nullwave.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * The app bar: hamburger left, screen title, actions right.
 *
 * Not Material's `TopAppBar`, for the same reason the rail is not Material's `NavigationRail` -
 * Material separates the bar from content with elevation, and this system has none. The separator
 * here is a 1dp hairline, drawn rather than elevated.
 *
 * Uppercases [title] itself: in this design a top bar title is always uppercase Chakra Petch with
 * wide tracking, so making every caller remember `.uppercase()` would just be a way to get it
 * wrong eventually.
 */
@Composable
fun NullWaveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit,
    navigationContentDescription: String = "Toggle navigation",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            // ORDER MATTERS, and this is the part that is easy to get wrong.
            // background and drawBehind sit BEFORE windowInsetsPadding, so they see the node's
            // full size - inset + bar height - and the surface colour extends up behind the
            // status bar. Move them after the padding and you get a transparent strip at the top
            // with content showing through.
            .background(colors.surface)
            .drawBehind {
                // Hairline along the bottom edge. Never a shadow.
                val stroke = 1.dp.toPx()
                drawRect(
                    color = colors.outline,
                    topLeft = Offset(0f, size.height - stroke),
                    size = Size(size.width, stroke),
                )
            }
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            // After the inset padding, so this is the bar's own height, not height minus status bar.
            .height(dimens.topBarHeight)
            .padding(horizontal = NullWaveTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarIcon(
            icon = NullWaveIcons.Menu,
            contentDescription = navigationContentDescription,
            onClick = onNavigationClick,
        )

        // Decodes out of noise when the destination changes. weight(1f) makes the title absorb the
        // leftover space, which both pushes `actions` to the far end without a Spacer and gives the
        // decode a fixed-width slot, so the glyphs churn without the layout moving.
        NullWaveDecodingText(
            text = title.uppercase(),
            style = NullWaveTheme.typography.h2,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = NullWaveTheme.spacing.xs),
        )

        actions()
    }
}

/**
 * A 24dp icon centred in a 48dp hit area - the touch-target floor the design sets.
 *
 * Private for now. When the search and overflow actions land it should graduate to a public
 * `NullWaveIconButton`, which is on the design's component list anyway.
 */
@Composable
private fun TopBarIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(NullWaveTheme.dimens.touchTarget)
            // A Material ripple has no shape of its own - it is a circle clipped to the node's
            // bounds. So clipping the node to the octagon node shape is what makes the ripple an
            // octagon. Order matters: clip must come BEFORE clickable, or the indication draws
            // outside the clip and you get the default square.
            .clip(NullWaveTheme.shapes.node)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = NullWaveTheme.colors.textPrimary,
            modifier = Modifier.size(NullWaveTheme.dimens.icon),
        )
    }
}

@Preview(name = "Top bar", widthDp = 412, heightDp = 80, showBackground = true)
@Composable
private fun TopBarPreview() {
    NullWaveTheme {
        NullWaveTopBar(title = "Library", onNavigationClick = {})
    }
}

@Preview(name = "Top bar - long title", widthDp = 412, heightDp = 80, showBackground = true)
@Composable
private fun TopBarLongTitlePreview() {
    NullWaveTheme {
        NullWaveTopBar(
            title = "Deadzone Chorus and Other Very Long Album Names",
            onNavigationClick = {},
        )
    }
}
