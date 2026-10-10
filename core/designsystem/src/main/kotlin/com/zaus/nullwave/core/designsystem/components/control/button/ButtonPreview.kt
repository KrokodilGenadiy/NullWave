package com.zaus.nullwave.core.designsystem.components.control.button

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Button - Yellow", group = "Buttons", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowButtonPreview() = ButtonSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Button - Crimson", group = "Buttons", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonButtonPreview() = ButtonSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Button - Dive", group = "Buttons", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun DiveButtonPreview() = ButtonSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Button - Large text", group = "Buttons", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTextButtonPreview() = ButtonSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Button - RTL", group = "Buttons", widthDp = 360, heightDp = 1200)
@Composable
private fun RtlButtonPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ButtonSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun ButtonSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val spacing = NullWaveTheme.spacing
        var clicks by remember { mutableIntStateOf(0) }
        var loading by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        Surface(color = NullWaveTheme.colors.bg) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("Кнопки / ${variant.name}")
                Text("Нажатий: $clicks", style = NullWaveTheme.typography.body)
                NullWaveButton(
                    label = "Начать / Start",
                    onClick = { clicks++; loading = true },
                    modifier = Modifier.focusRequester(focusRequester),
                    loading = loading,
                    leadingIcon = NullWaveIcons.Play,
                )
                NullWaveButton(
                    label = "Завершить загрузку",
                    onClick = { loading = false },
                    variant = NullWaveButtonVariant.Neutral,
                )
                NullWaveButton(
                    label = "Фокус на Start",
                    onClick = { focusRequester.requestFocus() },
                    enabled = !loading,
                    variant = NullWaveButtonVariant.Secondary,
                )
                NullWaveMicroLabel("Start меняет размер только при изменении самой подписи. Загрузка не увеличивает счётчик повторно.")
                NullWaveButton(
                    label = "Короткая / Short",
                    onClick = { clicks++ },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = NullWaveIcons.Play,
                )
                NullWaveButton(
                    label = "Добавить выбранные композиции в новый плейлист / Add selected tracks",
                    onClick = { clicks++ },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = NullWaveIcons.Create,
                )
                for (buttonVariant in NullWaveButtonVariant.entries) {
                    NullWaveSectionHeader(buttonVariant.name)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        NullWaveButton("Обычно", { clicks++ }, variant = buttonVariant)
                        NullWaveButton("С иконкой", { clicks++ }, variant = buttonVariant, leadingIcon = NullWaveIcons.Play)
                        NullWaveButton("Disabled", { clicks++ }, variant = buttonVariant, enabled = false)
                    }
                    NullWaveMicroLabel("Pressed / Focused / Focus + press — сравнение состояний")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        InteractionSample(buttonVariant, focused = false, pressed = true)
                        InteractionSample(buttonVariant, focused = true, pressed = false)
                        InteractionSample(buttonVariant, focused = true, pressed = true)
                    }
                    NullWaveMicroLabel("Loading / Loading + disabled")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        NullWaveButton("Загрузка", { clicks++ }, variant = buttonVariant, loading = true)
                        NullWaveButton("Загрузка", { clicks++ }, variant = buttonVariant, loading = true, enabled = false)
                    }
                }
                NullWaveMicroLabel("Конец витрины")
            }
        }
    }
}

/** Emits through the real interaction API; no forced visual-state flags in production code. */
@Composable
private fun InteractionSample(variant: NullWaveButtonVariant, focused: Boolean, pressed: Boolean) {
    val source = remember { MutableInteractionSource() }
    NullWaveButton(
        label = when {
            focused && pressed -> "Focus + press"
            focused -> "Focused"
            else -> "Pressed"
        },
        onClick = {},
        variant = variant,
        interactionSource = source,
    )
    LaunchedEffect(source, focused, pressed) {
        if (focused) source.emit(FocusInteraction.Focus())
        if (pressed) source.emit(PressInteraction.Press(Offset.Zero))
    }
}
