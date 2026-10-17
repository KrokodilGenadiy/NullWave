package com.zaus.nullwave.core.designsystem.modifier

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.chip.NullWaveChip
import com.zaus.nullwave.core.designsystem.components.control.rockerSwitch.NullWaveRockerSwitch
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSurface
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Borders - Yellow", group = "Borders", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowBorderPreview() = BorderSheet()

@Preview(name = "Borders - Crimson", group = "Borders", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonBorderPreview() = BorderSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Borders - Dive", group = "Borders", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun DiveBorderPreview() = BorderSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Borders - Large text", group = "Borders", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTextBorderPreview() = BorderSheet()

@Preview(name = "Borders - RTL", group = "Borders", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun RtlBorderPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { BorderSheet() }
}

@Composable
private fun BorderSheet(variant: NullWaveColorVariant = NullWaveColorVariant.Cyberpunk) {
    NullWaveTheme(variant = variant) {
        val spacing = NullWaveTheme.spacing
        var clipped by remember { mutableStateOf(true) }
        var selected by remember { mutableStateOf(false) }
        var switchedOn by remember { mutableStateOf(false) }
        val style = if (clipped) NullWaveBorderStyle.ClippedCorners else NullWaveBorderStyle.Closed
        val buttonFocus = rememberPreviewFocus()
        val chipFocus = rememberPreviewFocus()
        val switchFocus = rememberPreviewFocus()
        NullWavePreviewSurface {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                NullWaveSectionHeader("Рамки / ${variant.name}")
                NullWaveButton(
                    "Стиль: ${style.name}", { clipped = !clipped },
                    modifier = buttonFocus.modifier,
                    variant = NullWaveButtonVariant.Secondary, borderStyle = style,
                )
                NullWaveChip(
                    "Фильтр с иконкой", selected, { selected = !selected },
                    modifier = chipFocus.modifier,
                    leadingIcon = NullWaveIcons.Filter, borderStyle = style,
                )
                NullWaveButton("Фокус Button", buttonFocus::requestFocus, variant = NullWaveButtonVariant.Neutral)
                NullWaveMicroLabel("Button · ${buttonFocus.status}")
                NullWaveButton("Фокус Chip", chipFocus::requestFocus, variant = NullWaveButtonVariant.Neutral)
                NullWaveMicroLabel("Chip · ${chipFocus.status}")
                NullWaveRockerSwitch(
                    checked = switchedOn,
                    onCheckedChange = { switchedOn = it },
                    modifier = switchFocus.modifier.semantics { contentDescription = "Пример переключателя" },
                    borderStyle = style,
                )
                NullWaveButton("Фокус Switch", switchFocus::requestFocus, variant = NullWaveButtonVariant.Neutral)
                NullWaveMicroLabel("Switch · ${switchFocus.status}")
                for (borderStyle in NullWaveBorderStyle.entries) {
                    NullWaveSectionHeader(borderStyle.name)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        for (buttonVariant in NullWaveButtonVariant.entries) {
                            NullWaveButton(buttonVariant.name, {}, variant = buttonVariant, borderStyle = borderStyle)
                        }
                        NullWaveButton("Disabled", {}, enabled = false, borderStyle = borderStyle)
                        NullWaveButton("Loading", {}, loading = true, variant = NullWaveButtonVariant.Secondary, borderStyle = borderStyle)
                        NullWaveChip("Обычный", false, {}, borderStyle = borderStyle)
                        NullWaveChip("Выбран", true, {}, borderStyle = borderStyle)
                        NullWaveChip("Disabled", false, {}, enabled = false, borderStyle = borderStyle)
                        NullWaveChip("Disabled selected", true, {}, enabled = false, borderStyle = borderStyle)
                        for (checked in listOf(false, true)) {
                            for (enabled in listOf(true, false)) {
                                NullWaveRockerSwitch(
                                    checked = checked, onCheckedChange = {}, enabled = enabled,
                                    modifier = Modifier.semantics { contentDescription = "Состояние рамки переключателя" },
                                    borderStyle = borderStyle,
                                )
                            }
                        }
                    }
                    NullWaveButton(
                        "Длинная подпись кнопки с рамкой / Long label", {},
                        modifier = Modifier.fillMaxWidth(), variant = NullWaveButtonVariant.Secondary,
                        borderStyle = borderStyle,
                    )
                    NullWaveChip(
                        "Длинная подпись фильтра / Long filter label", false, {},
                        modifier = Modifier.fillMaxWidth(), leadingIcon = NullWaveIcons.Filter,
                        borderStyle = borderStyle,
                    )
                    for ((label, focused, pressed) in listOf(
                        Triple("Press", false, true), Triple("Focus", true, false), Triple("Focus + press", true, true),
                    )) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            BorderInteractionSample(focused, pressed) { source ->
                                NullWaveButton(label, {}, variant = NullWaveButtonVariant.Secondary, interactionSource = source, borderStyle = borderStyle)
                            }
                            BorderInteractionSample(focused, pressed) { source ->
                                NullWaveChip(label, true, {}, interactionSource = source, borderStyle = borderStyle)
                            }
                            BorderInteractionSample(focused, pressed) { source ->
                                NullWaveRockerSwitch(
                                    checked = true, onCheckedChange = {},
                                    modifier = Modifier.semantics { contentDescription = label },
                                    interactionSource = source, borderStyle = borderStyle,
                                )
                            }
                        }
                    }
                    NullWaveSurface(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(2.dp, Brush.horizontalGradient(listOf(NullWaveTheme.colors.primary, NullWaveTheme.colors.secondary))),
                        borderStyle = borderStyle,
                    ) {
                        Text("Surface / 2 dp / gradient", Modifier.padding(spacing.md))
                    }
                    NullWaveSurface(
                        shape = CutCornerShape(25),
                        border = BorderStroke(1.dp, NullWaveTheme.colors.secondary),
                        borderStyle = borderStyle,
                    ) {
                        Text("4 среза / 25%", Modifier.padding(spacing.md))
                    }
                    NullWaveSurface(border = null, borderStyle = borderStyle) {
                        Text("border = null", Modifier.padding(spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun BorderInteractionSample(
    focused: Boolean,
    pressed: Boolean,
    content: @Composable (MutableInteractionSource) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    content(source)
    LaunchedEffect(source, focused, pressed) {
        if (focused) source.emit(FocusInteraction.Focus())
        if (pressed) source.emit(PressInteraction.Press(Offset.Zero))
    }
}
