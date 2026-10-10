package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Preview(name = "Primitives - Yellow", group = "Primitives", widthDp = 360, heightDp = 1100)
@Composable
private fun YellowPrimitivesPreview() = PrimitiveSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Primitives - Crimson", group = "Primitives", widthDp = 360, heightDp = 1100)
@Composable
private fun CrimsonPrimitivesPreview() = PrimitiveSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Primitives - Dive", group = "Primitives", widthDp = 360, heightDp = 1100)
@Composable
private fun DivePrimitivesPreview() = PrimitiveSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Primitives - RTL", group = "Primitives", widthDp = 360, heightDp = 1100)
@Composable
private fun RtlPrimitivesPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        PrimitiveSheet(NullWaveColorVariant.Cyberpunk)
    }
}

@Preview(
    name = "Primitives - Large text",
    group = "Primitives",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
)
@Composable
private fun LargeTextPrimitivesPreview() = PrimitiveSheet(NullWaveColorVariant.Cyberpunk)

/** Scroll in Interactive Preview to inspect every sample, especially with large text. */
@Composable
private fun PrimitiveSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val colors = NullWaveTheme.colors
        val spacing = NullWaveTheme.spacing
        Surface(color = colors.bg) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                NullWaveSectionHeader(
                    title = "Примитивы / ${variant.name}",
                    supportingText = "Контейнеры, контуры и подписи",
                )
                NullWaveSurface(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        NullWaveSectionHeader(
                            title = "Недавно добавленная музыка / Recently added",
                            supportingText = "Длинный заголовок и пояснение переносятся по ширине контейнера.",
                        )
                        NullWaveDivider()
                        Text("Библиотека · Library", style = NullWaveTheme.typography.body)
                        NullWaveMicroLabel("FLAC · 44.1 kHz · +5.3 dB")
                    }
                }
                NullWaveSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surfaceRaised,
                    shape = NullWaveTheme.shapes.small,
                    border = null,
                ) {
                    Text(
                        "Raised · без контура",
                        modifier = Modifier.padding(spacing.md),
                        style = NullWaveTheme.typography.body,
                    )
                }
                NullWaveSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primary,
                    shape = NullWaveTheme.shapes.large,
                    border = null,
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NullWaveIcon(NullWaveIcons.Info, contentDescription = null)
                        Text("Текст и иконка наследуют onPrimary", style = NullWaveTheme.typography.body)
                    }
                }
                NullWaveSectionHeader("Технические подписи")
                NullWaveMicroLabel("Микс / Mix · Ёж · І · Ў · 44.1 kHz")
                NullWaveMicroLabel("Длинная техническая подпись сохраняет регистр и переносится без фиксированной высоты.")
                NullWaveMicroLabel(
                    "Явное ограничение одной строкой — длинная техническая подпись",
                    maxLines = 1,
                )
                NullWaveDivider()
                NullWaveSectionHeader("Восьмиугольные рамки")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    NullWaveOctagonFrame(Modifier.size(48.dp)) {
                        NullWaveIcon(NullWaveIcons.Info, contentDescription = null)
                    }
                    NullWaveOctagonFrame(
                        modifier = Modifier.size(64.dp),
                        color = colors.surfaceRaised,
                        contentColor = colors.primary,
                        border = BorderStroke(NullWaveTheme.dimens.hairline, colors.primary),
                    ) {
                        NullWaveIcon(NullWaveIcons.Waveform, contentDescription = null)
                    }
                    NullWaveOctagonFrame(Modifier.size(96.dp)) {
                        // Edge-to-edge child deliberately verifies clipping to the frame.
                        Box(Modifier.fillMaxSize().background(colors.secondary))
                    }
                }
                NullWaveMicroLabel("48 / 64 / 96 dp · рамки не являются кнопками")
                NullWaveDivider(color = colors.primary)
                NullWaveMicroLabel("Конец витрины", color = colors.primary)
            }
        }
    }
}
