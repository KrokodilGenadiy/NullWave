package com.zaus.nullwave.core.designsystem.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.slider.NullWaveSlider
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Preview(name = "Ranges - Yellow", group = "Slider and seek", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowRangesPreview() = RangeSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Ranges - Crimson", group = "Slider and seek", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonRangesPreview() = RangeSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Ranges - Dive", group = "Slider and seek", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DiveRangesPreview() = RangeSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Ranges - Large text", group = "Slider and seek", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeRangesPreview() = RangeSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Ranges - RTL", group = "Slider and seek", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlRangesPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { RangeSheet(NullWaveColorVariant.Cyberpunk) }
}

@Composable
private fun RangeSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var volume by remember { mutableFloatStateOf(0.35f) }
        var stepped by remember { mutableFloatStateOf(-6f) }
        var finishes by remember { mutableIntStateOf(0) }
        var seeks by remember { mutableIntStateOf(0) }
        var position by remember { mutableLongStateOf(42_000L) }
        var durationMode by remember { mutableIntStateOf(0) }
        val duration = when (durationMode) { 0 -> 240_000L; 1 -> null; else -> 0L }
        var media by remember { mutableIntStateOf(1) }
        var enabled by remember { mutableStateOf(true) }
        var ticking by remember { mutableStateOf(false) }
        var scrub by remember { mutableStateOf<Long?>(null) }
        var focusResult by remember { mutableStateOf("Фокус не запрошен") }
        val focus = remember { FocusRequester() }
        val inputMode = LocalInputModeManager.current
        val scope = rememberCoroutineScope()
        // Synthetic fixture belongs only to the preview; production takes caller-provided peaks.
        val peaks = remember { List(96) { 0.15f + abs(sin(it * 0.47f)) * 0.8f } }
        LaunchedEffect(ticking, duration, media) {
            while (ticking && duration != null && duration > 0L) {
                delay(500)
                position = (position + 1000L).coerceAtMost(duration)
            }
        }
        Surface(color = NullWaveTheme.colors.bg, contentColor = NullWaveTheme.colors.textPrimary) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm)) {
                NullWaveSectionHeader("Slider / continuous и steps")
                Text("Завершений: $finishes", style = NullWaveTheme.typography.mono)
                NullWaveButton("Фокус на громкость", {
                    focusResult = if (inputMode.requestInputMode(InputMode.Keyboard) && focus.requestFocus()) "Запрос принят" else "Запрос отклонён"
                })
                Text(focusResult, style = NullWaveTheme.typography.mono)
                Text("Громкость: ${(volume * 100).roundToInt()}%")
                NullWaveSlider(volume, { volume = it }, "Громкость", Modifier.fillMaxWidth().focusRequester(focus),
                    valueDescription = "${(volume * 100).roundToInt()}%", onValueChangeFinished = { finishes++ })
                Text("Шаг 3 дБ: $stepped")
                NullWaveSlider(stepped, { stepped = it }, "Усиление", Modifier.fillMaxWidth(), valueRange = -12f..12f, steps = 7,
                    valueDescription = "$stepped дБ", onValueChangeFinished = { finishes++ })
                NullWaveSlider(0f, {}, "Начало диапазона", Modifier.fillMaxWidth())
                NullWaveSlider(1f, {}, "Конец диапазона", Modifier.fillMaxWidth())
                NullWaveSlider(0.6f, { finishes++ }, "Недоступный slider", Modifier.fillMaxWidth(), enabled = false,
                    onValueChangeFinished = { finishes++ })
                NullWaveSectionHeader("Seek / обновления playback во время drag")
                NullWaveButton("Playback: ${if (ticking) "ON" else "OFF"}", { ticking = !ticking })
                NullWaveButton("Seek: ${if (enabled) "enabled" else "disabled"}", { enabled = !enabled })
                NullWaveButton("Duration: ${duration?.let(::previewTime) ?: "unknown"}", { durationMode = (durationMode + 1) % 3 })
                NullWaveButton("Сменить трек через 2 с", { scope.launch { delay(2000); media++; position = 0L } })
                Text("Трек: $media · Seek: $seeks\nPlayback: ${previewTime(position)} · Scrub: ${scrub?.let(::previewTime) ?: "—"}", style = NullWaveTheme.typography.mono)
                NullWaveSeekBar("preview-$media", position, duration, { position = it; seeks++ }, ::previewTime,
                    enabled = enabled, waveform = peaks, onScrubPositionChange = { scrub = it })
                NullWaveSectionHeader("Без waveform / границы / неизвестная duration")
                NullWaveSeekBar("plain", position, 240_000L, { position = it; seeks++ }, ::previewTime)
                NullWaveSeekBar("unknown", 0L, null, { seeks++ }, ::previewTime)
                NullWaveSeekBar("zero", 0L, 0L, { seeks++ }, ::previewTime)
                NullWaveSeekBar("end", 240_000L, 240_000L, { seeks++ }, ::previewTime, enabled = false, waveform = peaks)
                NullWaveSectionHeader("TimeLabels / часы и оставшееся время")
                NullWaveTimeLabels("123:45:06", "124:10:00")
                NullWaveTimeLabels("02:14", "−01:46", endDescription = "Осталось 1 минута 46 секунд")
            }
        }
    }
}

private fun previewTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000L
    val minutes = seconds / 60L
    return if (minutes >= 60L) "${minutes / 60L}:${(minutes % 60L).toString().padStart(2, '0')}:${(seconds % 60L).toString().padStart(2, '0')}"
    else "${minutes.toString().padStart(2, '0')}:${(seconds % 60L).toString().padStart(2, '0')}"
}
