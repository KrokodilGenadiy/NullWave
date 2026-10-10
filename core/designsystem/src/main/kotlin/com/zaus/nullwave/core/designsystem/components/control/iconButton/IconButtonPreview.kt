package com.zaus.nullwave.core.designsystem.components.control.iconButton

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Icon button - Yellow", group = "Icon buttons", widthDp = 360, heightDp = 1100)
@Composable
private fun YellowIconButtonPreview() = IconButtonSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Icon button - Crimson", group = "Icon buttons", widthDp = 360, heightDp = 1100)
@Composable
private fun CrimsonIconButtonPreview() = IconButtonSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Icon button - Dive", group = "Icon buttons", widthDp = 360, heightDp = 1100)
@Composable
private fun DiveIconButtonPreview() = IconButtonSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Icon button - Large labels", group = "Icon buttons", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun LargeLabelsIconButtonPreview() = IconButtonSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Icon button - RTL", group = "Icon buttons", widthDp = 360, heightDp = 1100)
@Composable
private fun RtlIconButtonPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        IconButtonSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun IconButtonSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val colors = NullWaveTheme.colors
        val spacing = NullWaveTheme.spacing
        var clicks by remember { mutableIntStateOf(0) }
        var enabled by remember { mutableStateOf(true) }
        val focusRequester = remember { FocusRequester() }
        Surface(color = colors.bg) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("IconButton / ${variant.name}")
                Text("Нажатий: $clicks", style = NullWaveTheme.typography.body)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    NullWaveIconButton(
                        icon = NullWaveIcons.Menu,
                        contentDescription = "Открыть меню",
                        onClick = { clicks++ },
                        modifier = Modifier.focusRequester(focusRequester),
                        enabled = enabled,
                    )
                    NullWaveIconButton(NullWaveIcons.Search, "Поиск музыки", { clicks++ }, enabled = enabled)
                    NullWaveIconButton(NullWaveIcons.Delete, "Удалить выбранное", { clicks++ }, enabled = false)
                }
                NullWaveMicroLabel("Меню / Поиск / Удалить (всегда disabled)")
                NullWaveButton(
                    label = if (enabled) "Отключить действия" else "Включить действия",
                    onClick = { enabled = !enabled },
                    variant = NullWaveButtonVariant.Neutral,
                )
                NullWaveButton(
                    label = "Фокус на меню",
                    onClick = { focusRequester.requestFocus() },
                    enabled = enabled,
                    variant = NullWaveButtonVariant.Secondary,
                )
                for ((title, tint) in listOf("Neutral" to colors.textPrimary, "Primary" to colors.primary, "Danger" to colors.danger)) {
                    NullWaveSectionHeader(title)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        IconButtonSample("Обычно", tint)
                        IconButtonSample("Pressed", tint, pressed = true)
                        IconButtonSample("Focused", tint, focused = true)
                        IconButtonSample("Focus + press", tint, pressed = true, focused = true)
                        IconButtonSample("Disabled", tint, enabled = false)
                        IconButtonSample("Disabled + events", tint, enabled = false, pressed = true, focused = true)
                    }
                }
                NullWaveSectionHeader("Размер и направление")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    NullWaveIconButton(NullWaveIcons.Back, "Назад", { clicks++ })
                    NullWaveIconButton(NullWaveIcons.Forward, "Вперёд", { clicks++ })
                    NullWaveIconButton(NullWaveIcons.Play, "Воспроизвести", { clicks++ }, tint = colors.primary)
                    NullWaveIconButton(NullWaveIcons.Play, "Воспроизвести", { clicks++ }, modifier = Modifier.size(64.dp), tint = colors.primary)
                }
                NullWaveMicroLabel("48 / 64 dp · иконки 24 dp. В RTL отражаются Back/Forward, Play сохраняет направление.")
                NullWaveMicroLabel("Конец витрины")
            }
        }
    }
}

/** Static comparison via the real interaction API; no fake state parameters in the component. */
@Composable
private fun IconButtonSample(
    label: String,
    tint: Color,
    enabled: Boolean = true,
    pressed: Boolean = false,
    focused: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    Column(modifier = Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NullWaveIconButton(
            icon = NullWaveIcons.More,
            contentDescription = "Другие действия",
            onClick = {},
            enabled = enabled,
            tint = tint,
            interactionSource = source,
        )
        Text(label, style = NullWaveTheme.typography.caption, textAlign = TextAlign.Center)
    }
    LaunchedEffect(source, pressed, focused) {
        if (pressed) source.emit(PressInteraction.Press(Offset.Zero))
        if (focused) source.emit(FocusInteraction.Focus())
    }
}
