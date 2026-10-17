package com.zaus.nullwave.core.designsystem.components.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.player.NullWaveMiniPlayer
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.motion.decorativeMotionEnabled
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.delay

@Preview(name = "Queue - Yellow", group = "Queue", widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun YellowQueuePreview() = QueueSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Queue - Crimson", group = "Queue", widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun CrimsonQueuePreview() = QueueSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Queue - Dive", group = "Queue", widthDp = 412, heightDp = 900, locale = "en")
@Composable
private fun DiveQueuePreview() = QueueSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Queue - Large text", group = "Queue", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeQueuePreview() = QueueSheet(NullWaveColorVariant.Cyberpunk, startAtManual = true)

@Preview(name = "Queue - RTL", group = "Queue", widthDp = 360, heightDp = 900, locale = "ar")
@Composable
private fun RtlQueuePreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        QueueSheet(NullWaveColorVariant.Cyberpunk)
    }
}

/** Local demo policy only: locked current entry and independent manual/automatic sections. */
@Composable
private fun QueueSheet(variant: NullWaveColorVariant, startAtManual: Boolean = false) {
    var reduceMotion by remember { mutableStateOf(false) }
    var glowEnabled by remember { mutableStateOf(true) }
    NullWaveTheme(variant = variant, effects = NullWaveEffects(reduceMotion = reduceMotion, glowEnabled = glowEnabled)) {
        var entries by remember { mutableStateOf(QueueEntries) }
        var enabled by remember { mutableStateOf(true) }
        var acceptChanges by remember { mutableStateOf(true) }
        var showControls by remember { mutableStateOf(false) }
        var interruptDrag by remember { mutableStateOf(false) }
        var externalRevision by remember { mutableIntStateOf(0) }
        var playing by remember { mutableStateOf(true) }
        var rowClicks by remember { mutableIntStateOf(0) }
        var moveRequests by remember { mutableIntStateOf(0) }
        var removeRequests by remember { mutableIntStateOf(0) }
        var dragStarts by remember { mutableIntStateOf(0) }
        var dragStops by remember { mutableIntStateOf(0) }
        var playCalls by remember { mutableIntStateOf(0) }
        var nextCalls by remember { mutableIntStateOf(0) }
        var expandCalls by remember { mutableIntStateOf(0) }
        var lastEvent by remember { mutableStateOf("—") }
        var drag by remember { mutableStateOf<QueuePreviewDragSession?>(null) }
        var drop by remember { mutableStateOf<QueueDropAnimation?>(null) }
        val animateDrop = decorativeMotionEnabled()
        val targetId by remember { derivedStateOf { drag?.targetId } }
        val insertAfter by remember { derivedStateOf { drag?.insertAfter ?: false } }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (startAtManual) 3 else 0)
        val colors = NullWaveTheme.colors
        val gutter = NullWaveTheme.dimens.gutter
        val spacing = NullWaveTheme.spacing
        val dropLine = NullWaveTheme.dimens.focusBorder
        val handleFocus = rememberPreviewFocus()

        val cancelDrag: () -> Unit = {
            drag = null
            drop = null
            externalRevision++
        }

        LaunchedEffect(drop, animateDrop) {
            val settling = drop ?: return@LaunchedEffect
            if (animateDrop) {
                settling.progress.animateTo(1f, tween(QueueDropDurationMillis, easing = FastOutSlowInEasing))
            } else settling.progress.snapTo(1f)
            if (drop === settling && drag === settling.session) {
                if (enabled && settling.session.matchesOrder(entries.map { it.id })) {
                    settling.order?.let { order ->
                        // Retain the physical viewport instead of following a moved first-visible key.
                        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                        val byId = entries.associateBy { it.id }
                        entries = order.map { byId.getValue(it) }
                    }
                }
                drag = null
                drop = null
            }
        }

        val submitMove: (List<Long>) -> Unit = { order ->
            moveRequests++
            lastEvent = if (acceptChanges) "Перестановка принята" else "Перестановка отклонена"
            if (acceptChanges) {
                val byId = entries.associateBy { it.id }
                entries = order.map { byId.getValue(it) }
            }
        }
        val move: (Long, Int) -> Unit = { id, direction ->
            val entry = entries.firstOrNull { it.id == id }
            if (enabled && entry != null && entry.group != QueueGroup.Current) {
                val group = entries.filter { it.group == entry.group }
                val neighbour = group.getOrNull(group.indexOfFirst { it.id == id } + direction)
                if (neighbour != null) {
                    if (drag != null) cancelDrag()
                    submitMove(moveQueuePreviewEntry(entries.map { it.id }, id, neighbour.id))
                }
            }
        }

        // A deterministic way to exercise cancellation while a pointer is still held down.
        LaunchedEffect(drag, interruptDrag) {
            val active = drag
            if (active != null && interruptDrag) {
                delay(1_200)
                if (drag === active) {
                    cancelDrag()
                    entries = entries.filter { it.group == QueueGroup.Current } +
                        entries.filter { it.group == QueueGroup.Manual }.reversed() +
                        entries.filter { it.group == QueueGroup.Automatic }
                    lastEvent = "Внешний порядок отменил drag"
                }
            }
        }

        NullWaveScaffold(
            topBar = { NullWaveTopBar("Очередь · ${entries.size}") },
            bottomBar = {
                NullWaveMiniPlayer(
                    "Тихий маяк", playing,
                    onExpand = { expandCalls++ },
                    onPlayPause = { playing = !playing; playCalls++ },
                    onNext = { nextCalls++ },
                    subtitle = "Сигналы утра · demo",
                    artwork = { NullWaveCoverArt(monogram = "ТМ") },
                    progress = 0.38f,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                state = listState,
                contentPadding = padding,
                userScrollEnabled = drag == null,
            ) {
                item(key = "controls") {
                    Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Move: $moveRequests · Remove: $removeRequests · Play row: $rowClicks",
                            style = NullWaveTheme.typography.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(lastEvent, style = NullWaveTheme.typography.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        NullWaveButton(if (showControls) "Скрыть условия проверки" else "Условия проверки", {
                            if (drag != null) cancelDrag()
                            showControls = !showControls
                        })
                        if (showControls) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                NullWaveButton("Принимать: $acceptChanges", { acceptChanges = !acceptChanges })
                                NullWaveButton("Reduce motion: $reduceMotion", { reduceMotion = !reduceMotion })
                                NullWaveButton("Glow: $glowEnabled", { glowEnabled = !glowEnabled })
                                NullWaveButton("Queue enabled: $enabled", {
                                    cancelDrag(); enabled = !enabled
                                })
                                NullWaveButton("Прервать drag: $interruptDrag", { interruptDrag = !interruptDrag })
                                NullWaveButton("Восстановить demo", {
                                    cancelDrag(); entries = QueueEntries; lastEvent = "Список восстановлен"
                                })
                                NullWaveButton("Фокус на ручку", handleFocus::requestFocus)
                                NullWaveDragHandle("Ручка — образец фокуса", { lastEvent = "Нажат образец ручки" },
                                    modifier = handleFocus.modifier, enabled = enabled)
                            }
                            Text("Entry IDs: ${entries.joinToString { it.id.toString() }}\n" +
                                "Drag: $dragStarts / $dragStops · Play: $playCalls · Next: $nextCalls · Expand: $expandCalls\n" +
                                "Удерживай ручку для переноса; обычный тап открывает действия. " +
                                "В этой витрине drag ограничен целиком видимыми строками своей секции, без автопрокрутки. " +
                                "Для остальных позиций используй «Выше» / «Ниже». " +
                                "Настройки темы действуют на всю витрину и MiniPlayer: Reduce motion останавливает движение, Glow отдельно управляет свечением. " +
                                "Прервать drag: через 1,2 с внешний порядок отменяет жест. " +
                                "Reject отклоняет move/remove. Записи 20/21 — один трек дважды; файлы не меняются.\n" + handleFocus.status,
                                style = NullWaveTheme.typography.caption)
                        }
                    }
                }
                QueueGroup.entries.forEach { group ->
                    val groupEntries = entries.filter { it.group == group }
                    item(key = "section-$group") {
                        NullWaveSectionHeader(group.label, Modifier.padding(gutter), supportingText = group.caption)
                    }
                    items(groupEntries, key = { it.id }) { entry ->
                        val index = groupEntries.indexOf(entry)
                        val movable = group != QueueGroup.Current
                        val isDragging = drag?.id == entry.id
                        val isTarget = drop == null && targetId == entry.id && drag?.id != entry.id
                        Box(Modifier.zIndex(if (isDragging) 1f else 0f).drawWithContent {
                            drawContent()
                            if (isTarget) {
                                val y = if (insertAfter) size.height - dropLine.toPx() / 2f else dropLine.toPx() / 2f
                                drawLine(colors.primary, Offset(gutter.toPx(), y), Offset(size.width - gutter.toPx(), y), dropLine.toPx())
                            }
                        }) {
                            // Explicit external replacement disposes an in-flight pointer detector.
                            key(externalRevision) {
                                NullWaveQueueRow(
                                    title = entry.title,
                                    onClick = { rowClicks++; lastEvent = "Play row ${entry.id}" },
                                    modifier = Modifier.graphicsLayer {
                                        translationY = drop?.offsetFor(entry.id)
                                            ?: drag?.takeIf { it.id == entry.id }?.offsetPx ?: 0f
                                    },
                                    subtitle = entry.artist,
                                    durationLabel = "3:42",
                                    playing = group == QueueGroup.Current && playing,
                                    available = entry.available,
                                    enabled = enabled,
                                    artwork = { NullWaveCoverArt(monogram = entry.monogram) },
                                    onMoveUp = if (movable && index > 0) ({ move(entry.id, -1) }) else null,
                                    onMoveDown = if (movable && index < groupEntries.lastIndex) ({ move(entry.id, 1) }) else null,
                                    onRemove = if (movable) ({
                                        if (enabled) {
                                            removeRequests++
                                            lastEvent = if (acceptChanges) "Удалена запись ${entry.id}" else "Удаление отклонено"
                                            if (acceptChanges) {
                                                if (drag != null) cancelDrag()
                                                entries = entries.filterNot { it.id == entry.id }
                                            }
                                        }
                                    }) else null,
                                    dragging = isDragging,
                                    dragCallbacks = if (!movable || drop != null) null else NullWaveDragCallbacks(
                                        onStart = {
                                            if (enabled && drag == null) {
                                                dragStarts++
                                                val layout = listState.layoutInfo
                                                val viewportTop = layout.viewportStartOffset + layout.beforeContentPadding
                                                val viewportBottom = layout.viewportEndOffset - layout.afterContentPadding
                                                val ids = entries.filter { it.group == group }.map { it.id }.toSet()
                                                val visible = layout.visibleItemsInfo.filter {
                                                    it.key in ids && it.offset >= viewportTop && it.offset + it.size <= viewportBottom
                                                }.map { QueuePreviewDragItem(it.key as Long, it.offset.toFloat(), it.size.toFloat()) }
                                                if (visible.any { it.id == entry.id }) {
                                                    drag = QueuePreviewDragSession(entry.id, entries.map { it.id }, visible)
                                                    lastEvent = "Перенос записи ${entry.id}"
                                                } else lastEvent = "Сначала прокрути строку целиком в видимую область"
                                            }
                                        },
                                        onDrag = { delta -> drag?.takeIf { it.id == entry.id }?.dragBy(delta) },
                                        onStop = { cancelled ->
                                            dragStops++
                                            val active = drag?.takeIf { it.id == entry.id }
                                            if (active != null) {
                                                val proposed = active.finish(entries.map { it.id }, accepted = enabled, cancelled = cancelled)
                                                // Freeze the demo's acceptance at release, before the visual settle.
                                                if (proposed != null) {
                                                    moveRequests++
                                                    lastEvent = if (acceptChanges) "Перестановка принята" else "Перестановка отклонена"
                                                } else lastEvent = if (cancelled) "Drag отменён" else "Позиция сохранена"
                                                drop = QueueDropAnimation(active, proposed?.takeIf { acceptChanges })
                                            }
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                item(key = "end") {
                    Text("Конец очереди. Недоступную запись можно перемещать и удалять, но нельзя воспроизвести. " +
                        "Правила секций и текущего трека заданы только этой витриной.",
                        Modifier.padding(gutter), style = NullWaveTheme.typography.caption)
                }
            }
        }
    }
}

/** Draw-phase interpolation keeps every row at its final position when the keyed order changes. */
private class QueueDropAnimation(val session: QueuePreviewDragSession, val order: List<Long>?) {
    val progress = Animatable(0f)
    private val startOffset = session.offsetPx
    private val endOffsets = session.settleOffsets(order)

    fun offsetFor(id: Long): Float {
        val from = if (id == session.id) startOffset else 0f
        val to = endOffsets[id] ?: 0f
        return from + (to - from) * progress.value
    }
}

private const val QueueDropDurationMillis = 220

private enum class QueueGroup(val label: String, val caption: String) {
    Current("Играет сейчас", "Demo: текущая запись без move/remove"),
    Manual("Добавлено вручную", "Удерживай ручку; тап — действия"),
    Automatic("Продолжение альбома", "Demo: перестановка внутри этой секции"),
}

private data class QueueEntry(
    val id: Long,
    val title: String,
    val artist: String,
    val monogram: String,
    val group: QueueGroup,
    val available: Boolean = true,
)

private val QueueEntries = listOf(
    QueueEntry(10, "Тихий маяк", "Сигналы утра", "ТМ", QueueGroup.Current),
    QueueEntry(20, "Карта пустых крыш", "Сигналы утра", "КК", QueueGroup.Manual),
    QueueEntry(21, "Карта пустых крыш", "Сигналы утра", "КК", QueueGroup.Manual),
    QueueEntry(22, "Длинное название записи о городе, который встречает утро без сигнала", "Очень длинное имя исполнителя остаётся компактным", "ГР", QueueGroup.Manual),
    QueueEntry(23, "Закрытая частота", "Недоступный файл", "ЗЧ", QueueGroup.Manual, available = false),
    QueueEntry(30, "Paper Satellite / Ночной маршрут", "Static Pilgrim", "PS", QueueGroup.Automatic),
    QueueEntry(31, "ضوء الصباح", "Сигналы утра", "NW", QueueGroup.Automatic),
    QueueEntry(32, "Последний фонарь", "Сигналы утра", "ПФ", QueueGroup.Automatic),
)
