package com.zaus.nullwave.core.designsystem.components.control.selection

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButtonVariant
import com.zaus.nullwave.core.designsystem.components.control.checkbox.NullWaveCheckbox
import com.zaus.nullwave.core.designsystem.components.control.radioButton.NullWaveRadioButton
import com.zaus.nullwave.core.designsystem.components.control.rockerSwitch.NullWaveRockerSwitch
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.preview.PreviewInteractionSample
import com.zaus.nullwave.core.designsystem.preview.rememberPreviewFocus
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

// Shared preview fixtures only; production components do not depend on this type or these rows.
internal enum class PreviewSelectionControl(val role: Role) {
    Checkbox(Role.Checkbox), RadioButton(Role.RadioButton), RockerSwitch(Role.Switch),
}

@Composable
internal fun SelectionControlSheet(
    kind: PreviewSelectionControl,
    variant: NullWaveColorVariant = NullWaveColorVariant.Cyberpunk,
    rtl: Boolean = false,
) {
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        NullWaveTheme(variant = variant) {
            val spacing = NullWaveTheme.spacing
            var checked by remember { mutableStateOf(false) }
            var enabled by remember { mutableStateOf(true) }
            var rowSelection by remember { mutableIntStateOf(0) }
            var callbacks by remember { mutableIntStateOf(0) }
            var rejected by remember { mutableIntStateOf(0) }
            val controlFocus = rememberPreviewFocus()
            NullWavePreviewSurface {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(NullWaveTheme.dimens.gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    NullWaveSectionHeader("${kind.name} / ${variant.name}")
                    Text("Состояние: $checked · Вызовов: $callbacks", style = NullWaveTheme.typography.body)
                    SelectionPreviewControl(
                        kind, checked, { checked = it; callbacks++ }, enabled,
                        controlFocus.modifier.semantics { contentDescription = "Пример выбора" },
                    )
                    NullWaveButton("Изменить извне", { checked = !checked }, variant = NullWaveButtonVariant.Neutral)
                    NullWaveButton(
                        if (enabled) "Отключить controls" else "Включить controls", { enabled = !enabled },
                        variant = NullWaveButtonVariant.Neutral,
                    )
                    NullWaveButton(
                        "Передать фокус", controlFocus::requestFocus, enabled = enabled,
                        variant = NullWaveButtonVariant.Secondary,
                    )
                    NullWaveMicroLabel(controlFocus.status)
                    NullWaveMicroLabel("Radio выбирается повторным кликом, но не снимает выбор. Изменить извне не вызывает callback.")
                    NullWaveSectionHeader("Одна кликабельная строка")
                    Column(modifier = if (kind == PreviewSelectionControl.RadioButton) Modifier.selectableGroup() else Modifier) {
                        SelectionPreviewRow(
                            kind, "Длинная подпись настройки или выбранного элемента / Selection option",
                            rowSelection == 0, enabled,
                        ) { value ->
                            rowSelection = if (value) 0 else -1
                            callbacks++
                        }
                        if (kind == PreviewSelectionControl.RadioButton) {
                            SelectionPreviewRow(kind, "Второй вариант сортировки", rowSelection == 1, enabled) {
                                rowSelection = 1
                                callbacks++
                            }
                        }
                    }
                    NullWaveMicroLabel("Текст и знак — одно действие. Внутри строки callback = null.")
                    NullWaveSectionHeader("Отклонённый запрос")
                    SelectionPreviewControl(
                        kind, false, { rejected++ }, enabled,
                        Modifier.semantics { contentDescription = "Выбор без принятия изменения" },
                    )
                    NullWaveMicroLabel("Запросов: $rejected · состояние остаётся false")
                    for (value in listOf(false, true)) {
                        NullWaveSectionHeader(if (value) "Checked / selected" else "Unchecked / unselected")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            SelectionSample(kind, "Обычно", value)
                            SelectionSample(kind, "Pressed", value, pressed = true)
                            SelectionSample(kind, "Focused", value, focused = true)
                            SelectionSample(kind, "Focus + press", value, pressed = true, focused = true)
                            SelectionSample(kind, "Disabled", value, enabled = false)
                            SelectionSample(kind, "Disabled + events", value, enabled = false, pressed = true, focused = true)
                        }
                    }
                    NullWaveMicroLabel("Конец витрины")
                }
            }
        }
    }
}

@Composable
private fun SelectionPreviewControl(
    kind: PreviewSelectionControl,
    checked: Boolean,
    onChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    when (kind) {
        PreviewSelectionControl.Checkbox -> NullWaveCheckbox(checked, onChange, modifier, enabled, interactionSource = interactionSource)
        PreviewSelectionControl.RadioButton -> NullWaveRadioButton(
            selected = checked, onClick = onChange?.let { { it(true) } },
            modifier = modifier, enabled = enabled, interactionSource = interactionSource,
        )
        PreviewSelectionControl.RockerSwitch -> NullWaveRockerSwitch(checked, onChange, modifier, enabled, interactionSource)
    }
}

@Composable
private fun SelectionPreviewRow(
    kind: PreviewSelectionControl,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = NullWaveTheme.shapes.small
    val action = if (kind == PreviewSelectionControl.RadioButton) {
        Modifier.selectable(checked, source, ripple(), enabled, kind.role) { if (enabled) onChange(true) }
    } else {
        Modifier.toggleable(checked, source, ripple(), enabled, kind.role) { if (enabled) onChange(it) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = NullWaveTheme.dimens.touchTarget)
            .clip(shape).then(action)
            .then(if (enabled && focused) Modifier.border(NullWaveTheme.dimens.focusBorder, NullWaveTheme.colors.textPrimary, shape) else Modifier)
            .padding(NullWaveTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
    ) {
        // The compact checkbox still reserves 48 dp; the row is the only action/semantics owner.
        if (kind == PreviewSelectionControl.Checkbox) {
            NullWaveCheckbox(checked, onCheckedChange = null, enabled = enabled, size = 24.dp)
        } else SelectionPreviewControl(kind, checked, null, enabled)
        Text(label, Modifier.weight(1f), style = NullWaveTheme.typography.body)
    }
}

@Composable
private fun SelectionSample(
    kind: PreviewSelectionControl,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    pressed: Boolean = false,
    focused: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PreviewInteractionSample(pressed = pressed, focused = focused) { source ->
            SelectionPreviewControl(
                kind, checked, {}, enabled,
                Modifier.semantics { contentDescription = label }, source,
            )
        }
        Text(label, style = NullWaveTheme.typography.caption)
    }
}
