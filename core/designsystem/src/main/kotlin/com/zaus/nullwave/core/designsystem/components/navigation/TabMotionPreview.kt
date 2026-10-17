package com.zaus.nullwave.core.designsystem.components.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.zaus.nullwave.core.designsystem.components.control.button.NullWaveButton
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveSectionHeader
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview(name = "Tabs - Yellow", group = "Tab motion", widthDp = 360, heightDp = 1050, locale = "ru")
@Composable
private fun YellowTabMotionPreview() = TabMotionSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Tabs - Crimson", group = "Tab motion", widthDp = 360, heightDp = 1050, locale = "ru")
@Composable
private fun CrimsonTabMotionPreview() = TabMotionSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Tabs - Dive", group = "Tab motion", widthDp = 360, heightDp = 1050, locale = "en")
@Composable
private fun DiveTabMotionPreview() = TabMotionSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Tabs - Large text", group = "Tab motion", widthDp = 320, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LargeTabMotionPreview() = TabMotionSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Tabs - RTL", group = "Tab motion", widthDp = 360, heightDp = 1050, locale = "ar")
@Composable
private fun RtlTabMotionPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        TabMotionSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Composable
private fun TabMotionSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        var selected by remember { mutableIntStateOf(0) }
        var reduced by remember { mutableStateOf(false) }
        var cycling by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val items = remember {
            listOf("Песни", "Альбомы", "Исполнители", "Плейлисты", "Папки").map(::NullWaveTabItem) +
                NullWaveTabItem("Недоступно", false)
        }
        NullWavePreviewSurface {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
            ) {
                NullWaveSectionHeader("Tab lock-on / ${variant.name}", Modifier.padding(NullWaveTheme.dimens.gutter))
                NullWaveTabRow(items, selected, { selected = it }, reduceMotion = reduced)
                NullWaveButton(
                    "Reduce motion: ${if (reduced) "ON" else "OFF"}", { reduced = !reduced },
                    Modifier.padding(horizontal = NullWaveTheme.dimens.gutter),
                )
                NullWaveButton(
                    "Быстро: 1 → 2 → 0 → 4",
                    {
                        scope.launch {
                            cycling = true
                            try {
                                for (index in listOf(1, 2, 0, 4)) { selected = index; delay(70) }
                            } finally { cycling = false }
                        }
                    },
                    Modifier.padding(horizontal = NullWaveTheme.dimens.gutter), enabled = !cycling,
                )
                Text("Выбрано: $selected", Modifier.padding(horizontal = NullWaveTheme.dimens.gutter), style = NullWaveTheme.typography.mono)
                NullWaveSectionHeader("Фазы (статичные)", Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                for (progress in listOf(0f, 0.2f, 0.45f, 0.55f, 0.8f, 1f)) {
                    FrozenTabFrame(progress, reduced = false)
                }
                NullWaveSectionHeader("Reduce motion / 50%", Modifier.padding(horizontal = NullWaveTheme.dimens.gutter))
                FrozenTabFrame(0.5f, reduced = true)
            }
        }
    }
}

@Composable
private fun FrozenTabFrame(progress: Float, reduced: Boolean) {
    val colors = NullWaveTheme.colors
    val transition = remember(reduced) { TabTransition.settled(2, 0, reduced).retarget(1, 1f) }
    Column {
        Text("${(progress * 100).toInt()}%", Modifier.padding(horizontal = NullWaveTheme.dimens.gutter), style = NullWaveTheme.typography.mono)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Песни", "Альбомы").forEachIndexed { index, label ->
                val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
                BasicText(
                    label,
                    modifier = Modifier.padding(horizontal = NullWaveTheme.spacing.md, vertical = NullWaveTheme.spacing.sm)
                        .drawWithContent {
                            drawContent()
                            drawTabDecoration(
                                transition.underline(index, progress),
                                if (index == 1 && !reduced) progress else 1f,
                                layout[0], colors,
                            )
                        },
                    style = NullWaveTheme.typography.tab.copy(color = if (index == 1) colors.primary else colors.textSecondary),
                    onTextLayout = { layout[0] = it },
                )
            }
        }
    }
}
