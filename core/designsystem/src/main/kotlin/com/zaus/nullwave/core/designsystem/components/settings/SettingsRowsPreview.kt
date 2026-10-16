package com.zaus.nullwave.core.designsystem.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.components.navigation.NullWaveTopBar
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Settings rows - Yellow", group = "Settings rows", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun YellowSettingsRowsPreview() = SettingsRowsSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Settings rows - Crimson", group = "Settings rows", widthDp = 360, heightDp = 1100, locale = "ru")
@Composable
private fun CrimsonSettingsRowsPreview() = SettingsRowsSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Settings rows - Dive", group = "Settings rows", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
private fun DiveSettingsRowsPreview() = SettingsRowsSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Settings rows - Large text", group = "Settings rows", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeSettingsRowsPreview() = SettingsRowsSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Settings rows - RTL", group = "Settings rows", widthDp = 360, heightDp = 1100, locale = "ar")
@Composable
private fun RtlSettingsRowsPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        SettingsRowsSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun SettingsRowsSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var enabled by remember { mutableStateOf(true) }
        var clipped by remember { mutableStateOf(false) }
        var gapless by remember { mutableStateOf(true) }
        var reduced by remember { mutableStateOf(false) }
        var choice by remember { mutableIntStateOf(0) }
        var longChoice by remember { mutableIntStateOf(0) }
        var actions by remember { mutableIntStateOf(0) }
        var switches by remember { mutableIntStateOf(0) }
        var choices by remember { mutableIntStateOf(0) }
        var rejected by remember { mutableIntStateOf(0) }
        var disabledCalls by remember { mutableIntStateOf(0) }
        var externalChanges by remember { mutableIntStateOf(0) }
        var focusRequests by remember { mutableIntStateOf(0) }
        var focused by remember { mutableStateOf(false) }
        var focusResult by remember { mutableStateOf("—") }
        val focus = remember { FocusRequester() }
        val inputMode = LocalInputModeManager.current
        val border = if (clipped) NullWaveBorderStyle.ClippedCorners else NullWaveBorderStyle.Closed
        val spacing = NullWaveTheme.spacing
        val gutter = NullWaveTheme.dimens.gutter
        val durations = remember { listOf("30 с", "60 с", "Не игнорировать") }
        val longValues = remember {
            listOf("Восстанавливать всю очередь и точную позицию последнего прослушивания",
                "Начинать воспроизведение с начала выбранного альбома")
        }

        NullWaveScaffold(topBar = { NullWaveTopBar("Settings rows") }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.padding(gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text("Action: $actions · Switch: $switches · Choice: $choices\n" +
                        "Rejected: $rejected · Disabled: $disabledCalls · External: $externalChanges",
                        style = NullWaveTheme.typography.mono)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        NullWaveButton("Enabled: $enabled", { enabled = !enabled })
                        NullWaveButton("Clipped corners: $clipped", { clipped = !clipped })
                        NullWaveButton("Сменить Gapless извне", { gapless = !gapless; externalChanges++ })
                        NullWaveButton("Фокус на Gapless", {
                            focusRequests++
                            focusResult = if (inputMode.requestInputMode(InputMode.Keyboard) && focus.requestFocus())
                                "принят" else "отклонён"
                        })
                    }
                    Text("Focus: $focused · запросов: $focusRequests · $focusResult\n" +
                        "Нажатие на текст или переключатель даёт один callback. Внешняя смена Gapless не увеличивает Switch.",
                        style = NullWaveTheme.typography.caption)
                }
                SettingsPreviewSection("Переключатели")
                NullWaveSwitchRow(
                    label = "Воспроизведение без пауз",
                    checked = gapless,
                    onCheckedChange = { gapless = it; switches++ },
                    modifier = Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused },
                    supportingText = "Gapless: непрерывные переходы между треками альбома",
                    leadingIcon = NullWaveIcons.Play,
                    enabled = enabled,
                    borderStyle = border,
                )
                NullWaveSwitchRow("Уменьшить движение", reduced, { reduced = it; switches++ },
                    supportingText = "Пример без leading icon, изначально выключен", enabled = enabled, borderStyle = border)
                NullWaveSwitchRow("Владелец отклоняет изменение", false, { rejected++ },
                    supportingText = "Callback считается, но checked остаётся false", enabled = enabled, borderStyle = border)
                SettingsPreviewSection("Действия")
                NullWaveSettingRow("Папки сканирования", { actions++ }, supportingText = "4 включено · 1 исключена — данные примера",
                    leadingIcon = NullWaveIcons.Folders, enabled = enabled)
                NullWaveSettingRow("Пересканировать сейчас", { actions++ }, supportingText = "Кнопка только увеличивает Action",
                    trailingIcon = NullWaveIcons.Rescan, enabled = enabled)
                NullWaveSettingRow("Действие без иконок", { actions++ }, trailingIcon = null, enabled = enabled)
                NullWaveSettingRow("Длинное название действия для проверки переноса текста в узкой строке", { actions++ },
                    supportingText = "Длинное пояснение тоже переносится полностью и увеличивает высоту строки, сохраняя доступную область нажатия.",
                    leadingIcon = NullWaveIcons.Info, enabled = enabled)
                SettingsPreviewSection("Текущее значение", "Здесь tap циклически меняет локальный пример; компонент сам не выбирает значение.")
                NullWaveChoiceRow("Игнорировать треки короче", durations[choice], {
                    choice = (choice + 1) % durations.size
                    choices++
                }, enabled = enabled)
                NullWaveChoiceRow("Длинное название настройки восстановления воспроизведения", longValues[longChoice], {
                    longChoice = (longChoice + 1) % longValues.size
                    choices++
                }, supportingText = "Длинные label и value должны оставаться читаемыми при fontScale 2 и RTL.",
                    leadingIcon = NullWaveIcons.Recent, enabled = enabled)
                SettingsPreviewSection("Всегда disabled", "Любое нажатие ниже оставляет Disabled равным 0.")
                NullWaveSettingRow("Недоступное действие", { disabledCalls++ }, leadingIcon = NullWaveIcons.Folders, enabled = false)
                NullWaveSwitchRow("Недоступный включённый", true, { disabledCalls++ }, enabled = false, borderStyle = border)
                NullWaveSwitchRow("Недоступный выключенный", false, { disabledCalls++ }, enabled = false, borderStyle = border)
                NullWaveChoiceRow("Недоступное значение", "30 с", { disabledCalls++ }, enabled = false)
                Text("Disabled callbacks: $disabledCalls\nAction: $actions · Switch: $switches · Choice: $choices · Rejected: $rejected",
                    Modifier.padding(gutter), style = NullWaveTheme.typography.mono)
            }
        }
    }
}

@Composable
private fun SettingsPreviewSection(title: String, supportingText: String? = null) {
    NullWaveSectionHeader(title, Modifier.padding(NullWaveTheme.dimens.gutter), supportingText)
}
