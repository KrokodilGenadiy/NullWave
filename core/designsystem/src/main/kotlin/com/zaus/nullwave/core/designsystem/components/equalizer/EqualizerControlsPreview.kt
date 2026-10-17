package com.zaus.nullwave.core.designsystem.components.equalizer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.chip.NullWaveChip
import com.zaus.nullwave.core.designsystem.components.control.slider.NullWaveVerticalSlider
import com.zaus.nullwave.core.designsystem.components.control.stepper.NullWaveStepper
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlin.math.roundToInt

@Preview(name = "EQ controls - Yellow", group = "Equalizer controls", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowEqualizerControlsPreview() = EqualizerControlsSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "EQ controls - Crimson", group = "Equalizer controls", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonEqualizerControlsPreview() = EqualizerControlsSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "EQ controls - Dive", group = "Equalizer controls", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DiveEqualizerControlsPreview() = EqualizerControlsSheet(NullWaveColorVariant.Braindance)

@Preview(name = "EQ controls - Large text", group = "Equalizer controls", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeEqualizerControlsPreview() = EqualizerControlsSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "EQ controls - RTL", group = "Equalizer controls", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlEqualizerControlsPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        EqualizerControlsSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun EqualizerControlsSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        // Frequencies and gains are UI fixtures, not an audio engine or a supported device profile.
        val bands = remember { listOf("63", "125", "250", "500", "1k", "2k", "4k", "8k") }
        val bandLabels = remember { listOf("63 Гц", "125 Гц", "250 Гц", "500 Гц", "1 кГц", "2 кГц", "4 кГц", "8 кГц") }
        val gains = remember { mutableStateListOf(3.5f, 5.3f, 0f, -2.8f, -0.9f, 1.9f, 4f, 1.1f) }
        val focus = bands.map { rememberPreviewFocus() }
        var selected by remember { mutableIntStateOf(0) }
        val focused = focus.indexOfFirst { it.isFocused }.takeIf { it >= 0 }
        var enabled by remember { mutableStateOf(true) }
        var acceptUpdates by remember { mutableStateOf(true) }
        var updates by remember { mutableIntStateOf(0) }
        var finishes by remember { mutableIntStateOf(0) }
        var externalChanges by remember { mutableIntStateOf(0) }
        var disabledCalls by remember { mutableIntStateOf(0) }
        var discrete by remember { mutableFloatStateOf(-6f) }
        var requestedBand by remember { mutableStateOf<Int?>(null) }
        val locale = LocalConfiguration.current.locales[0]
        val formatGain = remember(locale) {
            { value: Float ->
                // Integer tenths discard floating-point noise and cannot preserve negative zero.
                val rounded = (value * 10f).roundToInt() / 10.0
                String.format(locale, "%+.1f", rounded)
            }
        }
        val update: (Int, Float) -> Unit = { band, value ->
            selected = band
            updates++
            if (acceptUpdates) gains[band] = value
        }
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter

        NullWaveScaffold(topBar = { NullWaveTopBar("EQ controls") }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState())) {
                NullWaveSectionHeader("Полосы · −12 … +12 дБ", Modifier.padding(gutter),
                    "Восемь локальных значений. Подписи выбирают полосу; звук не изменяется. Прокручивай полосы горизонтально.")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    bands.forEachIndexed { index, band ->
                        Column(
                            Modifier.widthIn(min = NullWaveTheme.dimens.touchTarget),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                        ) {
                            Text(formatGain(gains[index]), style = NullWaveTheme.typography.mono,
                                color = if (index == selected) NullWaveTheme.colors.primary else NullWaveTheme.colors.textSecondary)
                            NullWaveVerticalSlider(
                                value = gains[index],
                                onValueChange = { update(index, it) },
                                label = "Усиление ${bandLabels[index]}",
                                modifier = focus[index].modifier,
                                enabled = enabled,
                                valueRange = -12f..12f,
                                steps = 239,
                                neutralValue = 0f,
                                valueDescription = "${formatGain(gains[index])} дБ",
                                onValueChangeFinished = { finishes++ },
                            )
                            NullWaveChip(
                                label = band,
                                selected = selected == index,
                                onClick = { selected = index },
                                modifier = Modifier.semantics { contentDescription = "Выбрать полосу ${bandLabels[index]}" },
                            )
                        }
                    }
                }
                Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    NullWaveSectionHeader("Точно: ${bandLabels[selected]}", supportingText = "Выбрана полоса ${selected + 1} · шаг 0,1 дБ")
                    NullWaveStepper(
                        value = gains[selected],
                        onValueChange = { update(selected, it) },
                        label = "Усиление ${bandLabels[selected]}",
                        valueLabel = "${formatGain(gains[selected])} дБ",
                        step = 0.1f,
                        valueRange = -12f..12f,
                        enabled = enabled,
                        onValueChangeFinished = { finishes++ },
                    )
                    Text("Updates: $updates · Finished: $finishes · External: $externalChanges\n" +
                        "Focus: ${focused?.let { bandLabels[it] } ?: "—"}\n" +
                        (requestedBand?.let { "${bandLabels[it]} · ${focus[it].status}" } ?: "Запрос: —"),
                        style = NullWaveTheme.typography.mono)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        NullWaveButton("Enabled: $enabled", { enabled = !enabled })
                        NullWaveButton("Принимать: $acceptUpdates", { acceptUpdates = !acceptUpdates })
                        NullWaveButton("Фокус на выбранную", {
                            requestedBand = selected
                            focus[selected].requestFocus()
                        })
                        listOf("Min" to -12f, "Zero" to 0f, "Max" to 12f).forEach { (label, value) ->
                            NullWaveButton(label, { gains[selected] = value; externalChanges++ })
                        }
                    }
                    Text("Min / Zero / Max меняют значение извне: Updates и Finished не растут. " +
                        "Принимать=false оставляет входное значение неизменным, но считает запросы. " +
                        "У выбранного slider проверь ↑/↓, Home/End, PageUp/PageDown. В RTL максимум тоже сверху.",
                        style = NullWaveTheme.typography.caption)
                    NullWaveSectionHeader("Редкие steps / disabled")
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatGain(discrete), style = NullWaveTheme.typography.mono)
                            NullWaveVerticalSlider(discrete, { discrete = it; updates++ }, "Дискретный пример, шаг 6",
                                Modifier.height(180.dp), valueRange = -12f..12f, steps = 3, neutralValue = 0f,
                                valueDescription = "${formatGain(discrete)} дБ", onValueChangeFinished = { finishes++ })
                            Text("Шаг 6", style = NullWaveTheme.typography.caption)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatGain(0f), style = NullWaveTheme.typography.mono)
                            NullWaveVerticalSlider(0f, { disabledCalls++ }, "Недоступная полоса",
                                Modifier.height(180.dp), enabled = false, valueRange = -12f..12f, neutralValue = 0f,
                                onValueChangeFinished = { disabledCalls++ })
                            Text("Disabled", style = NullWaveTheme.typography.caption)
                        }
                    }
                    NullWaveStepper(0f, { disabledCalls++ }, "Недоступная точная настройка", "${formatGain(0f)} дБ",
                        step = 0.1f, valueRange = -12f..12f, enabled = false, onValueChangeFinished = { disabledCalls++ })
                    Text("Disabled callbacks: $disabledCalls — должно оставаться 0.", style = NullWaveTheme.typography.mono)
                }
            }
        }
    }
}
