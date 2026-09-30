package com.zaus.nullwave.core.designsystem.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer
import kotlinx.coroutines.launch

/**
 * The alphabetical fast-scroller rail for the Songs tab.
 *
 * Not specified by the artboards - built from the tokens, so treat the proportions as a first pass.
 *
 * Takes [letterIndex] rather than the list itself: a design system component has no business
 * knowing what a track is, and the mapping from letter to item index is the caller's data. Pass
 * pairs in list order, e.g. `listOf('A' to 0, 'B' to 14, 'C' to 31)`.
 *
 * Sits over the list rather than beside it - it is an affordance, not a column, and taking layout
 * width would narrow every row by 16dp for something used occasionally.
 *
 * ## Three things worth knowing
 *
 * **One piece of state drives both the letter and the scroll.** `activeBucket` is an index into
 * [letterIndex], and the bubble's letter and the `scrollToItem` target are both read from it. Deriving
 * them from two separate expressions drifts apart the moment the buckets are unevenly sized - which,
 * for real music libraries, is always - and you land on a row that does not start with the letter you
 * were shown. It also changes only when the finger crosses into a new letter, so a drag recomposes
 * once per letter rather than once per pixel.
 *
 * **The thumb's position is read in the layout phase, not during composition.** Both `offset`
 * modifiers take the lambda overload and call [fractionNow] inside it, so `dragFraction` and the
 * list's scroll position are subscribed to by layout. Reading them in the composable body instead
 * would recompose the rail on every frame of a drag and on every scrolled pixel - which is what the
 * `UseOfNonLambdaOffsetOverload` lint warns about.
 *
 * **The resting thumb accounts for the visible window.** A thumb positioned on
 * `firstVisibleItemIndex / lastIndex` can never reach the bottom of the rail, because the first
 * visible item at the end of a list is still a screenful short of the last one. Dividing by the
 * number of *scrollable* items instead makes the thumb actually arrive.
 */
@Composable
fun NullWaveFastScroller(
    letterIndex: List<Pair<Char, Int>>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (letterIndex.isEmpty()) return

    val colors = NullWaveTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // Which letter the finger is currently in. Drives the bubble's text AND the scroll target, and
    // changes only on a crossing - which both keeps the two in agreement and stops every drag event
    // launching its own scroll job to race the others.
    var activeBucket by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(RailWidth),
    ) {
        val trackHeight = maxHeight

        // Called from inside the `offset` lambdas, so these state reads happen during layout.
        val fractionNow: () -> Float = {
            val raw = if (isDragging) {
                dragFraction
            } else {
                val info = listState.layoutInfo
                // Items that can be scrolled past, not items that exist.
                val scrollable = info.totalItemsCount - info.visibleItemsInfo.size
                if (scrollable <= 0) 0f else listState.firstVisibleItemIndex.toFloat() / scrollable
            }
            raw.coerceIn(0f, 1f)
        }
        val thumbTravel = trackHeight - ThumbHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(letterIndex, trackHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            activeBucket = -1
                        },
                        onDragCancel = {
                            isDragging = false
                            activeBucket = -1
                        },
                    ) { change, _ ->
                        val trackPx = with(density) { trackHeight.toPx() }
                        dragFraction = (change.position.y / trackPx).coerceIn(0f, 1f)
                        val bucket = letterIndex.bucketIndexAt(dragFraction)
                        if (bucket != activeBucket) {
                            activeBucket = bucket
                            scope.launch { listState.scrollToItem(letterIndex[bucket].second) }
                        }
                    }
                },
        ) {
            // The thumb. A chamfered block, not a rounded pill.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = 0, y = (thumbTravel * fractionNow()).roundToPx()) }
                    .size(width = ThumbWidth, height = ThumbHeight)
                    .clip(ThumbShape)
                    .background(if (isDragging) colors.primary else colors.outline),
            )

            // The letter bubble, only while dragging - it would otherwise be one more thing
            // permanently on screen competing with the list.
            if (isDragging && activeBucket in letterIndex.indices) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                x = -BubbleOffsetX.roundToPx(),
                                y = (thumbTravel * fractionNow()).roundToPx(),
                            )
                        }
                        .size(BubbleSize)
                        .clip(BubbleShape)
                        .background(colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letterIndex[activeBucket].first.uppercase(),
                        style = NullWaveTheme.typography.h2,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/** Index of the entry [fraction] of the way down the rail. */
private fun List<Pair<Char, Int>>.bucketIndexAt(fraction: Float): Int =
    (fraction * (size - 1)).toInt().coerceIn(indices)

private val RailWidth = 24.dp
private val ThumbWidth = 4.dp
private val ThumbHeight = 48.dp
private val ThumbShape = chamfer(2.dp)
private val BubbleSize = 48.dp
private val BubbleShape = chamfer(10.dp)
private val BubbleOffsetX = 56.dp

@Preview(name = "Fast scroller", showBackground = true)
@Composable
private fun FastScrollerPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        val listState = rememberLazyListState()
        Box(modifier = Modifier.background(colors.bg).size(width = 320.dp, height = 320.dp)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(60) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NullWaveTheme.spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
                    ) {
                        NullWaveMicroLabel(('A' + index / 3).toString())
                        NullWaveMicroLabel("track $index", color = colors.textSecondary)
                    }
                }
            }
            NullWaveFastScroller(
                letterIndex = ('A'..'T').mapIndexed { i, c -> c to i * 3 },
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
