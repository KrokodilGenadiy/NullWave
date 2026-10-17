package com.zaus.nullwave.core.designsystem.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.content.NullWaveTrackRow
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.iconButton.NullWaveIconButton
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveSelectionTopBar
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.overlay.NullWaveDialog
import com.zaus.nullwave.core.designsystem.components.player.NullWaveMiniPlayer
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Selection - Yellow", group = "Selection", widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun YellowSelectionPreview() = SelectionSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Selection - Crimson", group = "Selection", widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun CrimsonSelectionPreview() = SelectionSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Selection - Dive", group = "Selection", widthDp = 412, heightDp = 900, locale = "en")
@Composable
private fun DiveSelectionPreview() = SelectionSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Selection - Large text", group = "Selection", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeSelectionPreview() = SelectionSheet(NullWaveColorVariant.Cyberpunk, startAtTracks = true)

@Preview(name = "Selection - RTL", group = "Selection", widthDp = 360, heightDp = 900, locale = "ar")
@Composable
private fun RtlSelectionPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        SelectionSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun SelectionSheet(variant: NullWaveColorVariant, startAtTracks: Boolean = false) {
    NullWaveTheme(variant = variant) {
        var tracks by remember { mutableStateOf(SelectionTracks) }
        var selected by remember { mutableStateOf(setOf(1, 3, 5)) }
        var selecting by remember { mutableStateOf(true) }
        var acceptSelection by remember { mutableStateOf(true) }
        var actionsEnabled by remember { mutableStateOf(true) }
        var shareEnabled by remember { mutableStateOf(false) }
        var showControls by remember { mutableStateOf(false) }
        var playing by remember { mutableStateOf(true) }
        var pendingDelete by remember { mutableStateOf<Set<Int>?>(null) }
        var selectionRequests by remember { mutableIntStateOf(0) }
        var actionCalls by remember { mutableIntStateOf(0) }
        var lastAction by remember { mutableStateOf("—") }
        var confirms by remember { mutableIntStateOf(0) }
        var dismissals by remember { mutableIntStateOf(0) }
        var closes by remember { mutableIntStateOf(0) }
        var externalChanges by remember { mutableIntStateOf(0) }
        var rowClicks by remember { mutableIntStateOf(0) }
        var plays by remember { mutableIntStateOf(0) }
        var nexts by remember { mutableIntStateOf(0) }
        var expands by remember { mutableIntStateOf(0) }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (startAtTracks) 1 else 0)
        val availableIds = tracks.filter { it.available }.map { it.id }.toSet()
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter
        val colors = NullWaveTheme.colors
        val requestSelection: (Set<Int>) -> Unit = {
            selectionRequests++
            if (acceptSelection) selected = it
        }
        val record: (String) -> Unit = { actionCalls++; lastAction = it }
        val actions = listOf(
            NullWaveActionStripAction("queue", "В очередь", NullWaveIcons.AddToQueue, { record("queue") }),
            NullWaveActionStripAction("playlist", "Добавить в выбранный плейлист", NullWaveIcons.Playlists, { record("playlist") }),
            NullWaveActionStripAction("favorite", "В избранное", NullWaveIcons.Favourite, { record("favorite") }),
            NullWaveActionStripAction("share", "Поделиться", NullWaveIcons.Share, { record("share") }, enabled = shareEnabled),
            NullWaveActionStripAction("delete", "Удалить", NullWaveIcons.Delete, {
                record("delete")
                pendingDelete = selected.toSet()
            }, dangerous = true),
        )

        NullWaveScaffold(
            topBar = {
                if (selecting) NullWaveSelectionTopBar(selected.size, {
                    closes++; selected = emptySet(); selecting = false
                }, actions = {
                    val allSelected = selected == availableIds
                    NullWaveIconButton(NullWaveIcons.Check,
                        if (allSelected) "Снять всё выделение" else "Выбрать все доступные строки",
                        { requestSelection(if (allSelected) emptySet() else availableIds) },
                        enabled = availableIds.isNotEmpty(), tint = colors.bg,
                        pressedTint = colors.secondary, disabledTint = colors.bg)
                }) else NullWaveTopBar("Selection preview", actions = {
                    NullWaveIconButton(NullWaveIcons.Check, "Включить выделение", { selecting = true })
                })
            },
            bottomBar = {
                Column {
                    if (selecting) NullWaveActionStrip(actions,
                        Modifier.heightIn(max = ActionPanelMaxHeight).verticalScroll(rememberScrollState()),
                        enabled = actionsEnabled && selected.isNotEmpty())
                    // Playback stays reachable even when the action panel reflows and scrolls.
                    NullWaveMiniPlayer("Тихий маяк", playing, { expands++ }, { playing = !playing; plays++ }, { nexts++ },
                        subtitle = "Оригинальный demo-трек", artwork = { NullWaveCoverArt(monogram = "ТМ") },
                        progress = 0.38f, reduceMotion = true)
                }
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding), state = listState, contentPadding = padding) {
                item(key = "controls") {
                    Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Selection: $selectionRequests · Action: $actionCalls ($lastAction)\n" +
                            "Play: $plays · Confirm: $confirms", style = NullWaveTheme.typography.mono)
                        NullWaveButton(if (showControls) "Скрыть условия проверки" else "Условия проверки", { showControls = !showControls })
                        if (showControls) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                listOf("0" to 0, "1" to 1, "Несколько" to 3).forEach { (label, count) ->
                                    NullWaveButton(label, {
                                        selecting = true; selected = availableIds.take(count).toSet(); externalChanges++
                                    })
                                }
                                NullWaveButton("Принимать: $acceptSelection", { acceptSelection = !acceptSelection })
                                NullWaveButton("Actions enabled: $actionsEnabled", { actionsEnabled = !actionsEnabled })
                                NullWaveButton("Share enabled: $shareEnabled", { shareEnabled = !shareEnabled })
                                NullWaveButton("Сменить выделение извне", {
                                    selecting = true; selected = availableIds - selected; externalChanges++
                                })
                                NullWaveButton("Восстановить demo-строки", {
                                    tracks = SelectionTracks; selected = emptySet(); selecting = true; externalChanges++
                                })
                            }
                            Text("External: $externalChanges · Close: $closes · Dialog cancel: $dismissals\n" +
                                "Normal row: $rowClicks · Next: $nexts · Expand: $expands\n" +
                                "Действия считают callbacks. Share изначально недоступен; при нуле выделенных недоступны все действия. " +
                                "Reject блокирует изменение выделения по строкам и Select all, но не внешние команды витрины.",
                                style = NullWaveTheme.typography.caption)
                        }
                    }
                }
                items(tracks, key = { it.id }) { track ->
                    NullWaveTrackRow(
                        title = track.title,
                        onClick = {
                            if (selecting) requestSelection(if (track.id in selected) selected - track.id else selected + track.id)
                            else rowClicks++
                        },
                        subtitle = if (track.available) "Сигналы утра · demo" else "Недоступная строка · нажатия заблокированы",
                        durationLabel = "03:42",
                        playing = track.id == 1 && playing,
                        selected = track.id in selected,
                        selectionMode = selecting,
                        enabled = track.available,
                        artwork = { NullWaveCoverArt(monogram = track.id.toString()) },
                        onLongClick = { selecting = true; requestSelection(selected + track.id) },
                        longClickLabel = "Выбрать строку",
                        reduceMotion = true,
                    )
                }
                item(key = "end") {
                    Text("Конец списка. Нижняя строка и Pause доступны над системной навигацией. " +
                        "Delete удаляет только строки этой витрины после подтверждения; файловых операций нет.",
                        Modifier.padding(gutter), style = NullWaveTheme.typography.caption)
                }
            }
        }
        pendingDelete?.let { snapshot ->
            val cancel: () -> Unit = { dismissals++; pendingDelete = null }
            NullWaveDialog("Убрать строки примера?", onDismissRequest = cancel, actions = {
                NullWaveButton("Подтвердить: ${snapshot.size}", {
                    confirms++
                    tracks = tracks.filterNot { it.id in snapshot }
                    selected = selected - snapshot
                    pendingDelete = null
                }, variant = NullWaveButtonVariant.Danger)
                NullWaveButton("Отмена", cancel, variant = NullWaveButtonVariant.Neutral)
            }) {
                Text("Сохранено выделение при открытии: ${snapshot.sorted().joinToString()}. " +
                    "Подтверждение уберёт только эти demo-строки. Отмена сохраняет список и выделение. Реальные файлы не затрагиваются.")
            }
        }
    }
}

private data class SelectionTrack(val id: Int, val title: String, val available: Boolean = true)
private val ActionPanelMaxHeight = 180.dp
private val SelectionTracks = listOf(
    SelectionTrack(1, "Тихий маяк"),
    SelectionTrack(2, "Карта пустых крыш"),
    SelectionTrack(3, "Длинное название записи о городе, который встречает утро без сигнала"),
    SelectionTrack(4, "Закрытая частота", available = false),
    SelectionTrack(5, "Paper Satellite / Ночной маршрут"),
    SelectionTrack(6, "Синий мост"),
    SelectionTrack(7, "ضوء الصباح"),
    SelectionTrack(8, "Последний фонарь"),
)
