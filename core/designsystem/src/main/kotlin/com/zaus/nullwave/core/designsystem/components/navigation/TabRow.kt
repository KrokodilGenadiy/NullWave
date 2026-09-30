package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * The library's tab row - `SONGS · ARTISTS · ALBUMS · PLAYLISTS · FOLDERS`.
 *
 * Modelled on the game's top menu bar: uppercase, widely tracked, inactive labels in `textTertiary`,
 * the active one `primary` with a **hard-edged 2dp underline** that slides between tabs. No Material
 * pill - and Material's own `TabRow` indicator has the same hardcoded-shape problem as
 * `NavigationRail`, so this is hand-rolled for the same reason.
 *
 * The active label also carries a soft text glow, matching the design's
 * `text-shadow: 0 0 10px rgba(accent, 0.5)`. That is `TextStyle.shadow` - a glow around glyphs,
 * which is a different thing from `Modifier.activeGlow` around a container.
 *
 * ## Why the indicator is measured rather than laid out
 *
 * The design puts `border-bottom` on the text element, so the underline is exactly as wide as the
 * word and sits 8dp below its baseline box. Two things then pull against each other: a tab padded
 * out to a 48dp touch target would stretch the underline past the word, and a tab sized to the word
 * would be about 26dp tall - well under the touch floor the design sets everywhere else.
 *
 * So the **label** is measured, not the tab. Each label reports its x, its width and its bottom edge
 * relative to the row; the indicator is positioned from those. The tab around it is then free to be
 * as large as the touch target needs, because nothing about the indicator depends on its size.
 * Sliding is two `animateDpAsState` on x and width - the y never changes between tabs, so animating
 * it would only add a spring to a straight line.
 */
@Composable
fun NullWaveTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NullWaveTheme.colors
    val motion = NullWaveTheme.motion
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val bounds = remember { mutableStateMapOf<Int, LabelBounds>() }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    val selected = bounds[selectedIndex]

    // Deliberately NOT delegated with `by`. Reading an animation's value during composition means
    // recomposing this whole row - every label, every frame - for 300ms each time a tab changes.
    // Held as `State` and read inside the draw lambda below, the slide costs neither recomposition
    // nor re-measure. This is also what the `UseOfNonLambdaOffsetOverload` lint is pointing at.
    val indicatorX = animateDpAsState(
        targetValue = selected?.x ?: 0.dp,
        animationSpec = tween(motion.stateMillis, easing = motion.easing),
        label = "tabIndicatorX",
    )
    val indicatorWidth = animateDpAsState(
        targetValue = selected?.width ?: 0.dp,
        animationSpec = tween(motion.stateMillis, easing = motion.easing),
        label = "tabIndicatorWidth",
    )

    // Only scroll when the selected tab is actually off screen. Scrolling unconditionally jerks the
    // row every time you tap a tab that was already fully visible.
    LaunchedEffect(selectedIndex, selected, scrollState.viewportSize) {
        val target = selected ?: return@LaunchedEffect
        with(density) {
            // The label's settled position, not the animating indicator's - scrolling should aim at
            // where the tab actually is.
            val start = target.x.toPx()
            val end = start + target.width.toPx()
            val viewStart = scrollState.value.toFloat()
            val viewEnd = viewStart + scrollState.viewportSize
            val margin = RowPadding.toPx()
            when {
                start < viewStart + margin ->
                    scrollState.animateScrollTo((start - margin).toInt().coerceAtLeast(0))
                end > viewEnd - margin ->
                    scrollState.animateScrollTo((end - scrollState.viewportSize + margin).toInt())
            }
        }
    }

    Box(
        modifier = modifier
            .horizontalScroll(scrollState)
            .onGloballyPositioned { rowOrigin = it.positionInWindow() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RowPadding),
            horizontalArrangement = Arrangement.spacedBy(TabGap),
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                // No horizontal padding: the tab is exactly as wide as its label, so the 20dp gap
                // is the only thing between two tabs, as in the design. Height comes from the touch
                // floor instead, which costs the indicator nothing now that it is measured.
                Box(
                    modifier = Modifier
                        .heightIn(min = NullWaveTheme.dimens.touchTarget)
                        .clickable { onTabSelected(index) }
                        .padding(top = LabelTopPadding),
                ) {
                    Text(
                        text = label.uppercase(),
                        style = NullWaveTheme.typography.tab.copy(
                            shadow = if (isSelected) {
                                Shadow(
                                    color = colors.primary.copy(alpha = GlowAlpha),
                                    blurRadius = GlowBlur,
                                )
                            } else {
                                null
                            },
                        ),
                        color = if (isSelected) colors.primary else colors.textTertiary,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            with(density) {
                                val origin = coords.positionInWindow()
                                val measured = LabelBounds(
                                    x = (origin.x - rowOrigin.x).toDp(),
                                    width = coords.size.width.toDp(),
                                    bottom = (origin.y - rowOrigin.y).toDp() + coords.size.height.toDp(),
                                )
                                // Guarded: `SnapshotStateMap.put` records a write even when the
                                // value is unchanged, and a write from inside layout would then
                                // invalidate the layout that produced it, every frame, forever.
                                if (bounds[index] != measured) bounds[index] = measured
                            }
                        },
                    )
                }
            }
        }

        // Hard-edged, exactly as wide as the label, 8dp under it. Drawn last so it sits over the row.
        //
        // A `Canvas` rather than an offset-and-sized `Box`: the indicator is one opaque rectangle, so
        // there is nothing to lay out, and every value it needs is read here in the draw phase. A
        // tab change therefore invalidates drawing alone - no recomposition, no re-measure.
        // `matchParentSize` takes the row's size without contributing to it, which matters inside
        // `horizontalScroll` where the content is measured unbounded.
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = indicatorWidth.value.toPx()
            if (width <= 0f) return@Canvas
            val top = (selected?.bottom ?: 0.dp).toPx() + IndicatorGap.toPx()
            drawRect(
                color = colors.primary,
                topLeft = Offset(indicatorX.value.toPx(), top),
                size = Size(width, IndicatorHeight.toPx()),
            )
        }
    }
}

/** A label's geometry relative to the row: where the indicator has to go. */
private data class LabelBounds(val x: Dp, val width: Dp, val bottom: Dp)

private val RowPadding = 16.dp
private val LabelTopPadding = 14.dp
private val TabGap = 20.dp
private val IndicatorGap = 8.dp
private val IndicatorHeight = 2.dp
private const val GlowAlpha = 0.5f
private const val GlowBlur = 10f

@Preview(name = "Tab row", showBackground = true)
@Composable
private fun TabRowPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(modifier = Modifier.background(colors.bg)) {
            NullWaveTabRow(
                tabs = listOf("Songs", "Artists", "Albums", "Playlists", "Folders"),
                selectedIndex = 0,
                onTabSelected = {},
            )
            NullWaveTabRow(
                tabs = listOf("Songs", "Artists", "Albums", "Playlists", "Folders"),
                selectedIndex = 2,
                onTabSelected = {},
            )
        }
    }
}
