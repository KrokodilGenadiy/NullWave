package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Uses the host's bounded viewport. Transport gets its natural height first; a notice may use at
 * most half of what remains, with room for one touch target when space permits. The notice slot
 * owns overflow scrolling; unused height goes to navigation.
 * Measure order is independent of visual order: content, notice, transport from top to bottom.
 */
@Composable
internal fun PlayerHostLayout(
    modifier: Modifier = Modifier,
    notice: @Composable () -> Unit,
    miniPlayer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val minimumNoticeViewport = NullWaveTheme.dimens.touchTarget
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.testTag("player_content"), propagateMinConstraints = true) { content() }
            Box(Modifier.testTag("player_notice_viewport"), propagateMinConstraints = true) { notice() }
            Box(propagateMinConstraints = true) { miniPlayer() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val mini = measurables[2].measure(loose)
        val remaining = (constraints.maxHeight - mini.height).coerceAtLeast(0)
        val noticeMaxHeight = maxOf(remaining / 2, minimumNoticeViewport.roundToPx()).coerceAtMost(remaining)
        val noticePlaceable = measurables[1].measure(loose.copy(maxHeight = noticeMaxHeight))
        val contentHeight = remaining - noticePlaceable.height
        val contentPlaceable = measurables[0].measure(loose.copy(minHeight = contentHeight, maxHeight = contentHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceable.placeRelative(0, 0)
            noticePlaceable.placeRelative(0, contentHeight)
            mini.placeRelative(0, contentHeight + noticePlaceable.height)
        }
    }
}
