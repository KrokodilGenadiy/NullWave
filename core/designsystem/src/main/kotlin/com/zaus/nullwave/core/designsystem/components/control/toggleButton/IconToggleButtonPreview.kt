package com.zaus.nullwave.core.designsystem.components.control.toggleButton

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.NullWaveIconToggleButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Icon toggle - Yellow", group = "Icon toggles", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowIconTogglePreview() = IconToggleSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Icon toggle - Crimson", group = "Icon toggles", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonIconTogglePreview() = IconToggleSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Icon toggle - Dive", group = "Icon toggles", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun DiveIconTogglePreview() = IconToggleSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Icon toggle - Large labels", group = "Icon toggles", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeLabelsIconTogglePreview() = IconToggleSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Icon toggle - RTL", group = "Icon toggles", widthDp = 360, heightDp = 1200)
@Composable
private fun RtlIconTogglePreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        IconToggleSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun IconToggleSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val spacing = NullWaveTheme.spacing
        var favourite by remember { mutableStateOf(false) }
        var shuffle by remember { mutableStateOf(true) }
        var enabled by remember { mutableStateOf(true) }
        var callbacks by remember { mutableIntStateOf(0) }
        var rejectedRequests by remember { mutableIntStateOf(0) }
        val focusRequester = remember { FocusRequester() }
        Surface(color = NullWaveTheme.colors.bg) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("Icon toggle / ${variant.name}")
                Text("Избранное: $favourite · Shuffle: $shuffle\nCallbacks: $callbacks", style = NullWaveTheme.typography.body)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    NullWaveIconToggleButton(
                        icon = NullWaveIcons.Favourite,
                        checkedIcon = NullWaveIcons.FavouriteFilled,
                        contentDescription = "Избранное",
                        checked = favourite,
                        onCheckedChange = { favourite = it; callbacks++ },
                        modifier = Modifier.focusRequester(focusRequester),
                        enabled = enabled,
                    )
                    NullWaveIconToggleButton(
                        icon = NullWaveIcons.Shuffle,
                        contentDescription = "Случайный порядок",
                        checked = shuffle,
                        onCheckedChange = { shuffle = it; callbacks++ },
                        enabled = enabled,
                    )
                }
                NullWaveButton(
                    "Изменить избранное извне",
                    { favourite = !favourite },
                    variant = NullWaveButtonVariant.Neutral
                )
                NullWaveButton(
                    if (enabled) "Отключить toggles" else "Включить toggles",
                    { enabled = !enabled },
                    variant = NullWaveButtonVariant.Neutral,
                )
                NullWaveButton(
                    "Фокус на избранное",
                    { focusRequester.requestFocus() },
                    enabled = enabled,
                    variant = NullWaveButtonVariant.Secondary
                )
                NullWaveSectionHeader("Владелец отклоняет изменение")
                NullWaveIconToggleButton(
                    icon = NullWaveIcons.Repeat,
                    contentDescription = "Повтор",
                    checked = false,
                    onCheckedChange = { rejectedRequests++ },
                )
                NullWaveMicroLabel("Запросов: $rejectedRequests · checked всегда false, внутреннего переключения нет")
                for (checked in listOf(false, true)) {
                    NullWaveSectionHeader(if (checked) "Checked" else "Unchecked")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        ToggleSample("Обычно", checked)
                        ToggleSample("Pressed", checked, pressed = true)
                        ToggleSample("Focused", checked, focused = true)
                        ToggleSample("Focus + press", checked, focused = true, pressed = true)
                        ToggleSample("Disabled", checked, enabled = false)
                        ToggleSample("Disabled + events", checked, enabled = false, focused = true, pressed = true)
                    }
                }
                NullWaveMicroLabel("Конец витрины")
            }
        }
    }
}

@Composable
private fun ToggleSample(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    pressed: Boolean = false,
    focused: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    Column(modifier = Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NullWaveIconToggleButton(
            icon = NullWaveIcons.Favourite,
            checkedIcon = NullWaveIcons.FavouriteFilled,
            contentDescription = "Избранное",
            checked = checked,
            onCheckedChange = {},
            enabled = enabled,
            interactionSource = source,
        )
        Text(label, style = NullWaveTheme.typography.caption, textAlign = TextAlign.Center)
    }
    LaunchedEffect(source, pressed, focused) {
        if (pressed) source.emit(PressInteraction.Press(Offset.Zero))
        if (focused) source.emit(FocusInteraction.Focus())
    }
}
