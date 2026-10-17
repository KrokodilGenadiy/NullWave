package com.zaus.nullwave.core.designsystem.components.control.chip

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.preview.PreviewInteractionSample
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Chip - Yellow", group = "Chips", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowChipPreview() = ChipSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Chip - Crimson", group = "Chips", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonChipPreview() = ChipSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Chip - Dive", group = "Chips", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun DiveChipPreview() = ChipSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Chip - Large text", group = "Chips", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTextChipPreview() = ChipSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Chip - RTL", group = "Chips", widthDp = 360, heightDp = 1200)
@Composable
private fun RtlChipPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ChipSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun ChipSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val spacing = NullWaveTheme.spacing
        var preset by remember { mutableStateOf("Ровно") }
        var filters by remember { mutableStateOf(setOf("Без потерь")) }
        var enabled by remember { mutableStateOf(true) }
        var callbacks by remember { mutableIntStateOf(0) }
        var rejectedRequests by remember { mutableIntStateOf(0) }
        val presetFocus = rememberPreviewFocus()
        NullWavePreviewSurface {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("Chips / ${variant.name}")
                Text("Пресет: $preset · Нажатий: $callbacks", style = NullWaveTheme.typography.body)
                NullWaveMicroLabel("Один выбор — повторный клик не снимает пресет")
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    for (name in listOf("Ровно", "Бас", "Вокал")) {
                        NullWaveChip(
                            label = name,
                            selected = preset == name,
                            onClick = { preset = name; callbacks++ },
                            enabled = enabled,
                            modifier = if (name == "Ровно") presetFocus.modifier else Modifier,
                        )
                    }
                }
                NullWaveSectionHeader("Несколько фильтров")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    for (name in listOf("Без потерь", "Избранное")) {
                        NullWaveChip(
                            label = name,
                            selected = name in filters,
                            onClick = {
                                filters = if (name in filters) filters - name else filters + name
                                callbacks++
                            },
                            enabled = enabled,
                            leadingIcon = if (name == "Избранное") NullWaveIcons.Favourite else NullWaveIcons.Filter,
                        )
                    }
                }
                NullWaveMicroLabel("Активных фильтров: ${filters.size}")
                NullWaveButton(
                    "Выбрать Вокал извне", { preset = "Вокал" },
                    variant = NullWaveButtonVariant.Neutral,
                )
                NullWaveButton(
                    if (enabled) "Отключить chips" else "Включить chips", { enabled = !enabled },
                    variant = NullWaveButtonVariant.Neutral,
                )
                NullWaveButton(
                    "Фокус на Ровно", presetFocus::requestFocus, enabled = enabled,
                    variant = NullWaveButtonVariant.Secondary,
                )
                NullWaveMicroLabel(presetFocus.status)
                NullWaveChip("Отклонённый выбор", selected = false, onClick = { rejectedRequests++ })
                NullWaveMicroLabel("Запросов: $rejectedRequests · состояние не меняется без владельца")
                NullWaveSectionHeader("Неограниченная ширина / горизонтальный ряд")
                NullWaveMicroLabel("Частоты сохраняют одну строку; длинный chip с иконкой доступен прокруткой.")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    for (frequency in listOf("63", "125", "250", "500")) {
                        Column(Modifier.widthIn(min = NullWaveTheme.dimens.touchTarget)) {
                            NullWaveChip(frequency, selected = frequency == "125", onClick = {})
                        }
                    }
                    NullWaveChip(
                        "Длинный фильтр без потерь / Lossless tracks", selected = false, onClick = {},
                        leadingIcon = NullWaveIcons.Filter,
                    )
                }
                NullWaveSectionHeader("Длинная подпись / масштаб")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    NullWaveChip("125", selected = false, onClick = {})
                    NullWaveChip(
                        "Длинный фильтр без потерь / Lossless tracks", selected = true, onClick = {},
                        leadingIcon = NullWaveIcons.Filter,
                    )
                }
                NullWaveChip(
                    "Только композиции без потерь / Lossless tracks", selected = true, onClick = {},
                    leadingIcon = NullWaveIcons.Filter,
                    modifier = Modifier.fillMaxWidth(),
                )
                for (selected in listOf(false, true)) {
                    NullWaveSectionHeader(if (selected) "Selected" else "Unselected")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        ChipSample("Обычно", selected)
                        ChipSample("Pressed", selected, pressed = true)
                        ChipSample("Focused", selected, focused = true)
                        ChipSample("Focus + press", selected, pressed = true, focused = true)
                        ChipSample("Disabled", selected, enabled = false)
                        ChipSample("Disabled + events", selected, enabled = false, pressed = true, focused = true)
                    }
                }
                NullWaveMicroLabel("Конец витрины")
            }
        }
    }
}

@Composable
private fun ChipSample(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    pressed: Boolean = false,
    focused: Boolean = false,
) {
    PreviewInteractionSample(pressed = pressed, focused = focused) { source ->
        NullWaveChip(
            label = label,
            selected = selected,
            onClick = {},
            enabled = enabled,
            interactionSource = source,
        )
    }
}
