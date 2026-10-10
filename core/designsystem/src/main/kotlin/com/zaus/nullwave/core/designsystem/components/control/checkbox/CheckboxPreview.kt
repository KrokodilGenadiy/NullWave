package com.zaus.nullwave.core.designsystem.components.control.checkbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.components.control.selection.PreviewSelectionControl
import com.zaus.nullwave.core.designsystem.components.control.selection.SelectionControlSheet
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant

@Preview(name = "Checkbox - Yellow", group = "Checkbox", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowPreview() = SelectionControlSheet(PreviewSelectionControl.Checkbox)

@Preview(name = "Checkbox - Crimson", group = "Checkbox", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonPreview() = SelectionControlSheet(PreviewSelectionControl.Checkbox, NullWaveColorVariant.Arasaka)

@Preview(name = "Checkbox - Dive", group = "Checkbox", widthDp = 360, heightDp = 1200, locale = "en")
@Composable
private fun DivePreview() = SelectionControlSheet(PreviewSelectionControl.Checkbox, NullWaveColorVariant.Braindance)

@Preview(name = "Checkbox - Large text", group = "Checkbox", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTextPreview() = SelectionControlSheet(PreviewSelectionControl.Checkbox)

@Preview(name = "Checkbox - RTL", group = "Checkbox", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun RtlPreview() = SelectionControlSheet(PreviewSelectionControl.Checkbox, rtl = true)
