package com.zaus.nullwave.core.designsystem.components.control.searchField

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Search field - Yellow", group = "Search fields", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowSearchFieldPreview() = SearchFieldSheet()

@Preview(name = "Search field - Crimson", group = "Search fields", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonSearchFieldPreview() = SearchFieldSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Search field - Dive", group = "Search fields", widthDp = 360, heightDp = 1200, locale = "en")
@Composable
private fun DiveSearchFieldPreview() = SearchFieldSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Search field - Large text", group = "Search fields", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeSearchFieldPreview() = SearchFieldSheet()

@Preview(name = "Search field - RTL", group = "Search fields", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun RtlSearchFieldPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { SearchFieldSheet() }
}

@Composable
private fun SearchFieldSheet(variant: NullWaveColorVariant = NullWaveColorVariant.Cyberpunk) {
    NullWaveTheme(variant = variant) {
        var query by remember { mutableStateOf("") }
        var updates by remember { mutableIntStateOf(0) }
        var searches by remember { mutableIntStateOf(0) }
        var clears by remember { mutableIntStateOf(0) }
        var filters by remember { mutableIntStateOf(0) }
        var submitted by remember { mutableStateOf("") }
        var enabled by remember { mutableStateOf(true) }
        var readOnly by remember { mutableStateOf(false) }
        var showAction by remember { mutableStateOf(true) }
        val focusRequester = remember { FocusRequester() }
        NullWavePreviewSurface {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md),
            ) {
                NullWaveSectionHeader("SearchField / ${variant.name}")
                Text("Ввод: $updates · Поиск: $searches · Очистка: $clears · Фильтры: $filters", style = NullWaveTheme.typography.body)
                Text("Последний запрос: [$submitted]", style = NullWaveTheme.typography.caption)
                NullWaveSearchField(
                    query, { query = it; updates++ }, { submitted = it; searches++ },
                    modifier = Modifier.focusRequester(focusRequester),
                    onClear = { query = ""; clears++ },
                    enabled = enabled, readOnly = readOnly,
                    trailingAction = if (showAction) NullWaveSearchAction(NullWaveIcons.Filter, "Открыть фильтры", { filters++ }) else null,
                    borderStyle = NullWaveBorderStyle.ClippedCorners,
                )
                NullWaveButton("Фокус на поиск", { focusRequester.requestFocus() }, enabled = enabled, variant = NullWaveButtonVariant.Neutral)
                NullWaveButton("Запрос извне", { query = "Кино / Night 🎵" }, variant = NullWaveButtonVariant.Neutral)
                NullWaveButton("enabled = $enabled", { enabled = !enabled }, variant = NullWaveButtonVariant.Neutral)
                NullWaveButton("readOnly = $readOnly", { readOnly = !readOnly }, variant = NullWaveButtonVariant.Neutral)
                NullWaveButton("Доп. действие = $showAction", { showAction = !showAction }, variant = NullWaveButtonVariant.Neutral)
                NullWaveSectionHeader("Очистка по умолчанию")
                var defaultQuery by remember { mutableStateOf("Очистить этот текст") }
                NullWaveSearchField(defaultQuery, { defaultQuery = it }, {})
                NullWaveSectionHeader("Состояния")
                NullWaveSearchField("", {}, {}, enabled = false)
                NullWaveSearchField("Disabled / недоступно", {}, {}, enabled = false)
                NullWaveSearchField("ReadOnly / можно выделить и скопировать", {}, {}, readOnly = true)
                NullWaveSearchField("Текст с ошибкой", {}, {}, isError = true, supportingText = "Ошибка поиска: пояснение приходит от экрана")
                NullWaveSearchField("", {}, {}, placeholder = "Очень длинное приглашение к поиску / Long placeholder")
                NullWaveMicroLabel("Пустой запрос тоже передаётся в onSearch. Выполнение поиска и его политика принадлежат экрану.")
            }
        }
    }
}
