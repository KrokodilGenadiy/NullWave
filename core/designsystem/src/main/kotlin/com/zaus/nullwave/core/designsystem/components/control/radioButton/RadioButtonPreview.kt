package com.zaus.nullwave.core.designsystem.components.control.radioButton

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.components.control.selection.PreviewSelectionControl
import com.zaus.nullwave.core.designsystem.components.control.selection.SelectionControlSheet
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant

@Preview(name = "RadioButton - Yellow", group = "RadioButton", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowPreview() = SelectionControlSheet(PreviewSelectionControl.RadioButton)

@Preview(name = "RadioButton - Crimson", group = "RadioButton", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonPreview() = SelectionControlSheet(PreviewSelectionControl.RadioButton, NullWaveColorVariant.Arasaka)

@Preview(name = "RadioButton - Dive", group = "RadioButton", widthDp = 360, heightDp = 1200, locale = "en")
@Composable
private fun DivePreview() = SelectionControlSheet(PreviewSelectionControl.RadioButton, NullWaveColorVariant.Braindance)

@Preview(name = "RadioButton - Large text", group = "RadioButton", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTextPreview() = SelectionControlSheet(PreviewSelectionControl.RadioButton)

@Preview(name = "RadioButton - RTL", group = "RadioButton", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun RtlPreview() = SelectionControlSheet(PreviewSelectionControl.RadioButton, rtl = true)
