package com.zaus.nullwave.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.R as DesignR
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRow
import com.zaus.nullwave.core.designsystem.components.content.trackMetadataBelow
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonSize
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.motion.decorativeMotionEnabled
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.playback.api.model.PlaybackQueue
import com.zaus.nullwave.core.playback.api.model.QueueEntry
import com.zaus.nullwave.core.playback.api.model.QueueEntryId
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.api.ui.libraryDuration
import com.zaus.nullwave.feature.player.impl.R

@Composable
internal fun queueSummary(entries: List<QueueEntry>): String {
    val count = entries.size
    val tracks = pluralStringResource(R.plurals.player_queue_tracks, count, count)
    val minutes = remember(entries) {
        var totalMs = 0L
        for (entry in entries) {
            val duration = entry.track.durationMs ?: return@remember null
            if (duration > Long.MAX_VALUE - totalMs) return@remember null
            totalMs += duration
        }
        totalMs / 60_000
    }
    return if (minutes == null) tracks else stringResource(R.string.player_queue_minutes, tracks, minutes)
}

@Composable
internal fun PlayerQueue(
    queue: PlaybackQueue,
    currentIndex: Int,
    artwork: LibraryArtworkRenderer,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    onSelectEntry: ((QueueEntryId) -> Unit)? = null,
    onRemoveEntry: ((QueueEntryId) -> Unit)? = null,
    onMoveEntry: ((QueueEntryId, QueueEntryId?) -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val entriesById = remember(queue.entries) { queue.entries.associateBy { it.entryId } }
    val list = rememberQueueScrollState(queue, currentIndex)
    val order = queue.playOrder
    val canMove = actionsEnabled && onMoveEntry != null && order.lastIndex - currentIndex >= 2
    val reorder = remember(list, order, queue.current, canMove) {
        if (canMove) QueueReorderGesture(list, order, currentIndex) else null
    }
    val upLabel = stringResource(DesignR.string.nw_queue_move_up)
    val downLabel = stringResource(DesignR.string.nw_queue_move_down)
    val draggingLabel = stringResource(DesignR.string.nw_queue_dragging)
    val colors = NullWaveTheme.colors
    val accent = colors.primary
    val glow = NullWaveTheme.effects.glowEnabled

    // A move this queue asked for teleports the row once the service order confirms it.
    val swaps = remember(queue.current?.sessionId) { QueueSwapTracker() }
    val swap = rememberSwapPlayback(swaps, order, decorativeMotionEnabled())
    val moveEntry: ((QueueEntryId, QueueEntryId?) -> Unit)? = onMoveEntry?.let { move ->
        { id, before ->
            swaps.requested(id, before, order)
            move(id, before)
        }
    }
    reorder?.ScrollAtEdge()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The notice and MiniPlayer have already been measured. A tall summary can scroll with
        // the rows in a short viewport; the pinned MiniPlayer and system Back still return to Player.
        val scrollHeader = maxHeight < CompactQueueHeight
        val stackedDuration = trackMetadataBelow(maxWidth, LocalDensity.current.fontScale)
        Column {
            if (!scrollHeader) header()
            LazyColumn(Modifier.fillMaxWidth().testTag("player_queue").queueReorderInput(reorder, moveEntry), state = list) {
                item(key = QueueIntroKey) {
                    if (scrollHeader) header()
                    QueueActions()
                    QueueHeading(stringResource(R.string.player_now_playing))
                }
                items(
                    count = queue.playOrder.size - currentIndex,
                    key = { queue.playOrder[currentIndex + it].value },
                ) { offset ->
                    val entry = entriesById.getValue(queue.playOrder[currentIndex + offset])
                    val row = Modifier.testTag("player_queue_entry:${entry.entryId.value}")
                    val select: (() -> Unit)? = if (actionsEnabled && onSelectEntry != null) {
                        { onSelectEntry(entry.entryId) }
                    } else null
                    if (offset == 0) {
                        QueueEntrySwipe(modifier = Modifier, onRemove = null) {
                            CurrentTrack(entry.track, artwork, select, row)
                        }
                        if (currentIndex < queue.playOrder.lastIndex) QueueHeading(stringResource(R.string.player_queue_next))
                    } else {
                        val index = currentIndex + offset
                        val modifier = if (select != null) row.clickable(role = Role.Button, onClick = select)
                        else row.semantics(mergeDescendants = true) {}
                        // The rows the move passes slide one slot; the moved row itself teleports.
                        val arrival = swap?.takeIf { it.landing.entry == entry.entryId }
                        val shift = swap?.landing?.shift(entry.entryId) ?: 0
                        val passing = swap?.takeIf { shift != 0 }
                        QueueEntrySwipe(
                            modifier = (if (passing == null) Modifier else Modifier.graphicsLayer {
                                translationY = passing.slideOffset(shift, size.height)
                            }).then(modifier).semantics {
                                    if (reorder?.dragging == entry.entryId) stateDescription = draggingLabel
                                }.drawWithContent {
                                    if (arrival != null) drawSwapArrival(arrival, accent, colors.bg, glow) else drawContent()
                                    if (reorder?.dragging == entry.entryId) drawDragGhost(accent, colors.surfaceRaised)
                                    val gap = reorder?.insertionIndex
                                    val stroke = 2.dp.toPx()
                                    val y = when {
                                        gap == index -> stroke / 2
                                        index == order.lastIndex && gap == order.size -> size.height - stroke / 2
                                        else -> return@drawWithContent
                                    }
                                    drawDropRail(y, accent, glow)
                                },
                            onRemove = if (actionsEnabled && onRemoveEntry != null) {
                                { onRemoveEntry(entry.entryId) }
                            } else null,
                            moveActions = if (canMove && moveEntry != null) buildList {
                                if (offset > 1) add(CustomAccessibilityAction(upLabel) {
                                    moveEntry(entry.entryId, order[index - 1])
                                    true
                                })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(downLabel) {
                                    moveEntry(entry.entryId, order.getOrNull(index + 2))
                                    true
                                })
                            } else emptyList(),
                        ) {
                            UpcomingTrack(entry, artwork, stackedDuration, reorder)
                        }
                    }
                }
                if (currentIndex < order.lastIndex && onRemoveEntry != null) item(key = QueueHintKey) {
                    Text(stringResource(R.string.player_queue_remove_hint),
                        modifier = Modifier.padding(horizontal = NullWaveTheme.dimens.gutter, vertical = 8.dp),
                        style = NullWaveTheme.typography.mono, color = NullWaveTheme.colors.textSecondary)
                }
            }
        }
    }
}

// Int keys leave every String (including "0") available as an entry ID.
private const val QueueIntroKey = 0
private const val QueueHintKey = -1
private val CompactQueueHeight = 240.dp

@Composable
private fun rememberQueueScrollState(queue: PlaybackQueue, currentIndex: Int): LazyListState {
    val session = checkNotNull(queue.current).sessionId.value
    val list = rememberSaveable(session, saver = listSaver<LazyListState, Any>(
        save = { list ->
            val index = list.firstVisibleItemIndex
            val orderIndex = currentIndex + index - 1
            val key = if (index == 0) QueueIntroKey else list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key
                ?: queue.playOrder.getOrNull(orderIndex)?.value
            val nextKey = list.measuredEntryKey(index + 1) ?: queue.playOrder.getOrNull(orderIndex + 1)?.value
            listOf(session, key ?: QueueIntroKey, list.firstVisibleItemScrollOffset, index, nextKey ?: QueueIntroKey)
        },
        restore = { saved ->
            when {
                saved.size < 3 || saved[0] != session -> null
                saved[1] == QueueIntroKey -> LazyListState(0, saved[2] as Int)
                saved[1] == QueueHintKey -> LazyListState(queue.playOrder.size - currentIndex + 1, saved[2] as Int)
                else -> {
                    // Older saves contain only the session, entry key and pixel offset.
                    val index = queue.anchorIndex(
                        saved[1] as String, currentIndex,
                        previousIndex = saved.getOrNull(3) as? Int,
                        nextKey = saved.getOrNull(4) as? String,
                    )
                    if (index == null) LazyListState(1) else LazyListState(index, saved[2] as Int)
                }
            }
        },
    )) { LazyListState() }
    SideEffect(list, queue.playOrder, currentIndex) {
        // The last layout still identifies the occurrence the user was reading before remeasurement.
        val index = list.firstVisibleItemIndex
        val key = list.measuredEntryKey(index) ?: return@SideEffect
        val newIndex = queue.anchorIndex(key, currentIndex, index, list.measuredEntryKey(index + 1))
        if (newIndex == null) list.requestScrollToItem(1)
        else if (newIndex != index) list.requestScrollToItem(newIndex, list.firstVisibleItemScrollOffset)
    }
    return list
}

private fun LazyListState.measuredEntryKey(index: Int): String? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key as? String

private fun PlaybackQueue.anchorIndex(key: String, currentIndex: Int, previousIndex: Int?, nextKey: String?): Int? {
    val position = playOrder.indexOf(QueueEntryId(key))
    if (position >= currentIndex) return 1 + position - currentIndex
    if (position >= 0) return null // Playback moved beyond the anchor. Show the current row from its start.

    // The anchor was removed. Its successor can also have moved to a different list index.
    val successor = nextKey?.let { playOrder.indexOf(QueueEntryId(it)) } ?: -1
    return if (successor >= currentIndex) 1 + successor - currentIndex
    else previousIndex?.coerceIn(1, playOrder.size - currentIndex)
}

@Composable
private fun QueueActions() {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NullWaveTheme.dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (label in listOf(R.string.player_queue_clear, R.string.player_queue_save, R.string.player_queue_shuffle_rest)) {
            NullWaveButton(stringResource(label), onClick = {}, enabled = false,
                variant = NullWaveButtonVariant.Neutral, size = NullWaveButtonSize.Compact,
                borderStyle = NullWaveBorderStyle.ClippedCorners)
        }
    }
}

@Composable
private fun CurrentTrack(
    track: MediaTrack,
    artwork: LibraryArtworkRenderer,
    onSelect: (() -> Unit)?,
    modifier: Modifier,
) {
    val artist = track.artist ?: stringResource(R.string.player_unknown_artist)
    NullWaveTrackRow(
        title = track.title,
        onClick = onSelect,
        subtitle = track.album?.let { stringResource(R.string.player_queue_credit, artist, it) } ?: artist,
        durationLabel = libraryDuration(track.durationMs),
        playing = true,
        playingStateDescription = stringResource(R.string.player_queue_current),
        artwork = { artwork.Artwork(track.artwork, track.album ?: track.title, 48.dp) },
        modifier = modifier,
    )
}

@Composable
private fun QueueHeading(label: String) {
    Text(label, Modifier.semantics { heading() }.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = NullWaveTheme.typography.mono, color = NullWaveTheme.colors.textSecondary)
}

@Composable
private fun UpcomingTrack(entry: QueueEntry, artwork: LibraryArtworkRenderer, stackedDuration: Boolean, reorder: QueueReorderGesture?) {
    val track = entry.track
    val colors = NullWaveTheme.colors
    val duration = libraryDuration(track.durationMs)
    Column(Modifier.fillMaxWidth()) {
        // The handle sits at the trailing edge, so the artwork lines up with the current track's.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp)
                .padding(start = NullWaveTheme.dimens.gutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artwork.Artwork(track.artwork, track.album ?: track.title, 44.dp, Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = NullWaveTheme.typography.body, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist ?: stringResource(R.string.player_unknown_artist),
                    style = NullWaveTheme.typography.caption, color = colors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (stackedDuration && duration != null) Text(duration, style = NullWaveTheme.typography.mono, color = colors.textSecondary)
            }
            if (!stackedDuration && duration != null) Text(duration, Modifier.padding(start = 12.dp),
                style = NullWaveTheme.typography.mono, color = colors.textSecondary)
            // The column stays when moving is unavailable, so the duration never shifts.
            Box(Modifier.size(NullWaveTheme.dimens.touchTarget)
                .queueReorderHandle(reorder, entry.entryId),
                contentAlignment = Alignment.Center) {
                if (reorder != null) {
                    NullWaveIcon(NullWaveIcons.Reorder, null, Modifier.size(20.dp)
                        .testTag("player_queue_handle:${entry.entryId.value}"),
                        tint = if (reorder.dragging == entry.entryId) colors.primary else colors.outline)
                }
            }
        }
        HorizontalDivider(color = colors.surfaceRaised, thickness = NullWaveTheme.dimens.hairline)
    }
}
