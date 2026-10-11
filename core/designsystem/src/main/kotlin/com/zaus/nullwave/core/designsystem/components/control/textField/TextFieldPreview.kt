package com.zaus.nullwave.core.designsystem.components.control.textField

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.NullWaveBorderStyle
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Text field - Yellow", group = "Text fields", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowTextFieldPreview() = TextFieldSheet()

@Preview(name = "Text field - Crimson", group = "Text fields", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonTextFieldPreview() = TextFieldSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Text field - Dive", group = "Text fields", widthDp = 360, heightDp = 1200, locale = "en")
@Composable
private fun DiveTextFieldPreview() = TextFieldSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Text field - Large text", group = "Text fields", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTextFieldPreview() = TextFieldSheet()

@Preview(name = "Text field - RTL", group = "Text fields", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun RtlTextFieldPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { TextFieldSheet() }
}

@Composable
private fun TextFieldSheet(variant: NullWaveColorVariant = NullWaveColorVariant.Cyberpunk) {
    NullWaveTheme(variant = variant) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var attempted by remember { mutableStateOf(false) }
        var enabled by remember { mutableStateOf(true) }
        var readOnly by remember { mutableStateOf(false) }
        var updates by remember { mutableIntStateOf(0) }
        var done by remember { mutableIntStateOf(0) }
        val nameFocus = remember { FocusRequester() }
        val nextFocus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val spacing = NullWaveTheme.spacing
        Surface(color = NullWaveTheme.colors.bg) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("TextField / ${variant.name}")
                Text("Изменений: $updates · Done: $done", style = NullWaveTheme.typography.body)
                NullWaveTextField(
                    name, { name = it; updates++ }, "Название плейлиста",
                    modifier = Modifier.focusRequester(nameFocus),
                    placeholder = "Например, Ночная поездка",
                    isError = attempted && name.isBlank(),
                    supportingText = if (attempted && name.isBlank()) "Введите название" else "Проверка выполняется только в preview",
                    enabled = enabled, readOnly = readOnly,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { nextFocus.requestFocus() }),
                    borderStyle = NullWaveBorderStyle.ClippedCorners,
                )
                NullWaveTextField(
                    description, { description = it }, "Второе поле / Done",
                    modifier = Modifier.focusRequester(nextFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done++; focusManager.clearFocus() }),
                )
                NullWaveButton("Проверить имя", { attempted = true }, variant = NullWaveButtonVariant.Secondary)
                NullWaveButton("Передать фокус", { nameFocus.requestFocus() }, enabled = enabled, variant = NullWaveButtonVariant.Neutral)
                NullWaveButton("Изменить извне", { name = "Ночь / Night 🎵" }, variant = NullWaveButtonVariant.Neutral)
                NullWaveButton("enabled = $enabled", { enabled = !enabled }, variant = NullWaveButtonVariant.Neutral)
                NullWaveButton("readOnly = $readOnly", { readOnly = !readOnly }, variant = NullWaveButtonVariant.Neutral)
                NullWaveSectionHeader("Многострочный ввод")
                var notes by remember { mutableStateOf("Первая строка\nВторая строка") }
                NullWaveTextField(notes, { notes = it }, "Описание", singleLine = false, minLines = 3, maxLines = 5)
                NullWaveTextField(
                    "Длинное значение / Very long value to scroll horizontally while editing", {},
                    "Длинная подпись поля, которая должна переноситься целиком",
                    supportingText = "Длинное пояснение также переносится и остаётся за пределами рамки поля.",
                    leadingIcon = NullWaveIcons.Rename,
                    readOnly = true,
                )
                NullWaveTextField("Недоступное значение", {}, "Disabled", enabled = false)
                NullWaveTextField("Ошибка", {}, "Error", isError = true, supportingText = "Такое имя уже существует")
                NullWaveTextField("Ошибка без пояснения", {}, "Error / fallback", isError = true)
                for (style in NullWaveBorderStyle.entries) {
                    val source = remember(style) { MutableInteractionSource() }
                    NullWaveTextField("Focused", {}, "${style.name} / focus", interactionSource = source, borderStyle = style)
                    LaunchedEffect(source) { source.emit(FocusInteraction.Focus()) }
                }
                NullWaveMicroLabel("Конец витрины · IME и выделение проверить вручную")
            }
        }
    }
}
