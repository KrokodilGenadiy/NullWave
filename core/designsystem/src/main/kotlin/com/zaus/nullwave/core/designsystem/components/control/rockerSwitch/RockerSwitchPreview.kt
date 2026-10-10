package com.zaus.nullwave.core.designsystem.components.control.rockerSwitch

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.components.control.selection.PreviewSelectionControl
import com.zaus.nullwave.core.designsystem.components.control.selection.SelectionControlSheet
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant

@Preview(name = "RockerSwitch - Yellow", group = "RockerSwitch", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun YellowPreview() = SelectionControlSheet(PreviewSelectionControl.RockerSwitch)

@Preview(name = "RockerSwitch - Crimson", group = "RockerSwitch", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun CrimsonPreview() = SelectionControlSheet(PreviewSelectionControl.RockerSwitch, NullWaveColorVariant.Arasaka)

@Preview(name = "RockerSwitch - Dive", group = "RockerSwitch", widthDp = 360, heightDp = 1200, locale = "en")
@Composable
private fun DivePreview() = SelectionControlSheet(PreviewSelectionControl.RockerSwitch, NullWaveColorVariant.Braindance)

@Preview(name = "RockerSwitch - Large text", group = "RockerSwitch", widthDp = 320, heightDp = 1200, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTextPreview() = SelectionControlSheet(PreviewSelectionControl.RockerSwitch)

@Preview(name = "RockerSwitch - RTL", group = "RockerSwitch", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun RtlPreview() = SelectionControlSheet(PreviewSelectionControl.RockerSwitch, rtl = true)
