package com.zaus.nullwave.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.content.NullWaveArtworkPlaceholder
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.control.chip.NullWaveChip
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTabItem
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTabRow
import com.zaus.nullwave.core.designsystem.components.player.NullWaveMiniPlayer
import com.zaus.nullwave.core.designsystem.components.player.NullWavePlayPauseButton
import com.zaus.nullwave.core.designsystem.components.player.NullWavePlayPauseButtonSize
import com.zaus.nullwave.core.designsystem.components.player.NullWaveSeekBar
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.components.queue.NullWaveQueueRow
import com.zaus.nullwave.core.designsystem.components.sleeptimer.NullWaveTimeReadout
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Effects - Yellow", group = "Effects policy", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun YellowEffectsPreview() = EffectsSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Effects - Crimson", group = "Effects policy", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun CrimsonEffectsPreview() = EffectsSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Effects - Dive", group = "Effects policy", widthDp = 412, heightDp = 1000, locale = "en")
@Composable
private fun DiveEffectsPreview() = EffectsSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Effects - Narrow", group = "Effects policy", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun NarrowEffectsPreview() = EffectsSheet(NullWaveColorVariant.Cyberpunk)

@Composable
private fun EffectsSheet(variant: NullWaveColorVariant) {
    // Only this theme receives the global policy. Main samples deliberately omit local overrides.
    var reduceMotion by remember { mutableStateOf(false) }
    var glowEnabled by remember { mutableStateOf(true) }
    NullWaveTheme(variant = variant, effects = NullWaveEffects(reduceMotion = reduceMotion, glowEnabled = glowEnabled)) {
        var tab by remember { mutableIntStateOf(0) }
        var tabClicks by remember { mutableIntStateOf(0) }
        var playing by remember { mutableStateOf(true) }
        var plays by remember { mutableIntStateOf(0) }
        var nexts by remember { mutableIntStateOf(0) }
        var expands by remember { mutableIntStateOf(0) }
        var seeks by remember { mutableIntStateOf(0) }
        var queueActions by remember { mutableIntStateOf(0) }
        var position by remember { mutableLongStateOf(84_000L) }
        val tabs = remember { listOf(NullWaveTabItem("Треки"), NullWaveTabItem("Альбомы"), NullWaveTabItem("Плейлисты")) }
        val peaks = remember { List(64) { 0.2f + (it % 7) * 0.1f } }
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter
        val play: () -> Unit = { playing = !playing; plays++ }

        NullWavePreviewSurface {
            Column(Modifier.fillMaxSize()) {
                // Kept outside the scroll region so both controls remain reachable for comparisons.
                FlowRow(
                    Modifier.fillMaxWidth().padding(gutter),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    NullWaveChip("Reduce motion", reduceMotion, { reduceMotion = !reduceMotion })
                    NullWaveChip("Glow", glowEnabled, { glowEnabled = !glowEnabled })
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Reduce motion упрощает переход вкладок и останавливает scanner и бегущую строку. " +
                            "Glow независимо управляет статичным свечением. Ниже отдельные образцы, не экран плеера.",
                            style = NullWaveTheme.typography.caption)
                        Text("Tab: $tabClicks · Play: $plays · Next: $nexts\nExpand: $expands · Seek: $seeks · Queue: $queueActions",
                            style = NullWaveTheme.typography.mono)
                        NullWaveSectionHeader("Вкладки и loading")
                    }
                    NullWaveTabRow(tabs, tab, { tab = it; tabClicks++ })
                    Row(Modifier.padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalAlignment = Alignment.CenterVertically) {
                        NullWaveCoverArt(placeholder = NullWaveArtworkPlaceholder.Loading, size = 72.dp,
                            contentDescription = "Загрузка обложки, образец scanner")
                        Text("Scanner обложки наследует общую настройку.", Modifier.weight(1f),
                            style = NullWaveTheme.typography.caption)
                    }
                    NullWaveMiniPlayer(
                        title = "Длинное название для проверки бегущей строки / A quiet city beyond the last antenna",
                        isPlaying = playing,
                        onExpand = { expands++ },
                        onPlayPause = play,
                        onNext = { nexts++ },
                        subtitle = "Оригинальный demo-трек",
                        progress = 0.38f,
                        buffering = true,
                    )
                    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        NullWaveSectionHeader("Seek и readout")
                        NullWaveSeekBar("effects-fixture", position, 241_000L, { position = it; seeks++ }, ::effectsPreviewTime,
                            waveform = peaks)
                        NullWaveTimeReadout("18:42", "Статичный остаток времени", "Осталось 18 минут 42 секунды")
                        NullWaveSectionHeader("Queue / статичное dragging")
                        NullWaveQueueRow(
                            title = "Поднятая строка очереди",
                            onClick = { queueActions++ },
                            modifier = Modifier.padding(vertical = spacing.sm),
                            subtitle = "Drag не выполняется: это образец состояния",
                            onMoveUp = { queueActions++ },
                            dragging = true,
                        )
                        Text("В dragging playback и команды очереди недоступны; Queue остаётся 0. " +
                            "Glow OFF убирает halo, но сохраняет границу поднятой строки.",
                            style = NullWaveTheme.typography.caption)
                        NullWaveSectionHeader("Локальные и вложенные ограничения")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                            Column(Modifier.width(128.dp), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                Text("Local OFF", style = NullWaveTheme.typography.caption)
                                NullWavePlayPauseButton(playing, play, buffering = true,
                                    size = NullWavePlayPauseButtonSize.Compact, reduceMotion = true, glowEnabled = false)
                            }
                            Column(Modifier.width(128.dp), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                Text("Child defaults", style = NullWaveTheme.typography.caption)
                                NullWaveTheme(variant = variant, effects = NullWaveEffects()) {
                                    NullWavePlayPauseButton(playing, play, buffering = true, size = NullWavePlayPauseButtonSize.Compact)
                                }
                            }
                            Column(Modifier.width(128.dp), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                Text("Child OFF", style = NullWaveTheme.typography.caption)
                                NullWaveTheme(variant = variant, effects = NullWaveEffects(reduceMotion = true, glowEnabled = false)) {
                                    NullWavePlayPauseButton(playing, play, buffering = true, size = NullWavePlayPauseButtonSize.Compact)
                                }
                            }
                        }
                        Text("Local OFF и Child OFF всегда статичны и без glow. Child defaults не должен возвращать эффекты, " +
                            "запрещённые внешней темой. Все кнопки сохраняют действие Play/Pause.",
                            style = NullWaveTheme.typography.caption)
                        Text("Отдельная ручная проверка: Animator duration scale = 0 в системных настройках. " +
                            "Scanner становится статичным, декоративный переход вкладок исчезает; настройка Glow остаётся независимой. " +
                            "Эта витрина не подменяет системный scale.", style = NullWaveTheme.typography.caption)
                    }
                }
            }
        }
    }
}

private fun effectsPreviewTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000L
    return "${(seconds / 60L).toString().padStart(2, '0')}:${(seconds % 60L).toString().padStart(2, '0')}"
}
