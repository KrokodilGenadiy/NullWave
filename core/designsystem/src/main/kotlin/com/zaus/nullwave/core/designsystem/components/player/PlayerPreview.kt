package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.content.NullWaveArtworkPlaceholder
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Player - Yellow", group = "Player visuals", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowPlayerPreview() = PlayerSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Player - Crimson", group = "Player visuals", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonPlayerPreview() = PlayerSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Player - Dive", group = "Player visuals", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DivePlayerPreview() = PlayerSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Player - Large text", group = "Player visuals", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargePlayerPreview() = PlayerSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Player - RTL", group = "Player visuals", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlPlayerPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { PlayerSheet(NullWaveColorVariant.Cyberpunk) }
}

@Composable
private fun PlayerSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var playing by remember { mutableStateOf(false) }
        var loadingPlaying by remember { mutableStateOf(true) }
        var buffering by remember { mutableStateOf(false) }
        var enabled by remember { mutableStateOf(true) }
        var canSkip by remember { mutableStateOf(true) }
        var reduced by remember { mutableStateOf(false) }
        var shuffled by remember { mutableStateOf(false) }
        var repeat by remember { mutableStateOf(NullWaveRepeatMode.Off) }
        var progressStep by remember { mutableIntStateOf(2) }
        val progress = if (progressStep == 5) null else progressStep / 4f
        var plays by remember { mutableIntStateOf(0) }
        var previous by remember { mutableIntStateOf(0) }
        var next by remember { mutableIntStateOf(0) }
        var expands by remember { mutableIntStateOf(0) }
        var shuffleClicks by remember { mutableIntStateOf(0) }
        var repeatClicks by remember { mutableIntStateOf(0) }
        val focus = rememberPreviewFocus()
        val play: () -> Unit = { playing = !playing; plays++ }
        val cycleRepeat: () -> Unit = {
            repeat = when (repeat) {
                NullWaveRepeatMode.Off -> NullWaveRepeatMode.All
                NullWaveRepeatMode.All -> NullWaveRepeatMode.One
                NullWaveRepeatMode.One -> NullWaveRepeatMode.Off
            }
            repeatClicks++
        }
        NullWavePreviewSurface {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm)) {
                Column(Modifier.padding(horizontal = NullWaveTheme.dimens.gutter), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                    Text("Play: $plays · Prev: $previous · Next: $next\nExpand: $expands · Shuffle: $shuffleClicks · Repeat: $repeatClicks", style = NullWaveTheme.typography.mono)
                    NullWaveButton("Buffering: $buffering", { buffering = !buffering })
                    NullWaveButton("Playback enabled: $enabled", { enabled = !enabled })
                    NullWaveButton("Previous / Next: $canSkip", { canSkip = !canSkip })
                    NullWaveButton("Reduce motion: $reduced", { reduced = !reduced })
                    NullWaveButton("Progress: ${progress?.let { "${(it * 100).toInt()}%" } ?: "unknown"}", { progressStep = (progressStep + 1) % 6 })
                    NullWaveButton("Фокус на Hero", focus::requestFocus)
                    Text(focus.status, style = NullWaveTheme.typography.mono)
                    NullWaveSectionHeader("PlayPause / Hero, Compact, disabled")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        NullWavePlayPauseButton(playing, play, focus.modifier, buffering, enabled, reduceMotion = reduced)
                        NullWavePlayPauseButton(playing, play, buffering = buffering, enabled = enabled, size = NullWavePlayPauseButtonSize.Compact, reduceMotion = reduced)
                        NullWavePlayPauseButton(true, { plays++ }, buffering = true, enabled = false, reduceMotion = reduced)
                    }
                    NullWaveSectionHeader("Transport / Shuffle + Repeat")
                    NullWaveTransportControls(playing, play, { previous++ }, { next++ }, buffering = buffering, enabled = enabled,
                        canPrevious = canSkip, canNext = canSkip, shuffle = shuffled,
                        onShuffleChange = { shuffled = it; shuffleClicks++ }, repeatMode = repeat, onRepeatClick = cycleRepeat, reduceMotion = reduced)
                    Text("Shuffle: $shuffled · Repeat: $repeat", style = NullWaveTheme.typography.mono)
                    NullWaveSectionHeader("Transport / узкий контейнер 240 dp")
                    NullWaveTransportControls(playing, play, { previous++ }, { next++ }, Modifier.width(240.dp),
                        buffering = buffering, enabled = enabled, canPrevious = canSkip, canNext = canSkip,
                        shuffle = shuffled, onShuffleChange = { shuffled = it; shuffleClicks++ },
                        repeatMode = repeat, onRepeatClick = cycleRepeat, reduceMotion = reduced)
                    NullWaveSectionHeader("Transport / только основные действия")
                    NullWaveTransportControls(playing, play, { previous++ }, { next++ }, buffering = buffering, enabled = enabled,
                        canPrevious = canSkip, canNext = canSkip, reduceMotion = reduced)
                    NullWaveSectionHeader("MiniPlayer / длинные теги")
                }
                NullWaveMiniPlayer(
                    "Очень длинное название трека / A very long night drive across the city", playing, { expands++ }, play, { next++ },
                    subtitle = "Ночной Вектор · исполнитель с длинным именем", artwork = { NullWaveCoverArt(monogram = "НВ") },
                    progress = progress, buffering = buffering, playbackEnabled = enabled, canNext = canSkip, reduceMotion = reduced,
                )
                NullWaveMiniPlayer("Загрузка трека", loadingPlaying, { expands++ }, { loadingPlaying = !loadingPlaying; plays++ }, { next++ }, subtitle = "Static Pilgrim",
                    artwork = { NullWaveCoverArt(placeholder = NullWaveArtworkPlaceholder.Loading, reduceMotion = reduced) },
                    progress = null, buffering = true, canNext = false, reduceMotion = reduced)
                NullWaveMiniPlayer("Playback недоступен / открыть детали можно", false, { expands++ }, { plays++ }, { next++ },
                    progress = 0.25f, playbackEnabled = false, reduceMotion = reduced)
                Text("У недоступных transport кнопок нажатия не должны увеличивать Expand.",
                    Modifier.fillMaxWidth().padding(NullWaveTheme.dimens.gutter), style = NullWaveTheme.typography.caption)
            }
        }
    }
}
