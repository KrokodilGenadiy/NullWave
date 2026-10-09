package com.zaus.nullwave.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.theme.ChakraPetch
import com.zaus.nullwave.core.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * IDE-only token sheets, following the legacy DesignSystemPreview layout.
 * All specimens read the real theme: there is no separate preview palette or type scale.
 * Scroll in Interactive Preview to inspect the entire sheet.
 */
@Preview(name = "Tokens - Yellow", group = "Tokens", widthDp = 412, heightDp = 1180)
@Composable
private fun TokenSheetCyberpunkPreview() = TokenSheet(NullWaveColorVariant.Cyberpunk)

@Preview(name = "Tokens - Crimson", group = "Tokens", widthDp = 412, heightDp = 1180)
@Composable
private fun TokenSheetArasakaPreview() = TokenSheet(NullWaveColorVariant.Arasaka)

@Preview(name = "Tokens - Dive", group = "Tokens", widthDp = 412, heightDp = 1180)
@Composable
private fun TokenSheetBraindancePreview() = TokenSheet(NullWaveColorVariant.Braindance)

@Preview(name = "Tokens - Large text", group = "Accessibility", widthDp = 360, heightDp = 1180, fontScale = 2f)
@Composable
private fun TokenSheetLargeTextPreview() = TokenSheet(NullWaveColorVariant.Cyberpunk)

@Composable
private fun TokenSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val colors = NullWaveTheme.colors
        val type = NullWaveTheme.typography
        val spacing = NullWaveTheme.spacing
        Surface(color = colors.bg) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text("NULLWAVE", style = type.display, fontFamily = ChakraPetch)
                Text(variant.name, style = type.mono)

                SectionLabel("01 / COLOR — CORE")
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Swatch("Bg", colors.bg)
                    Swatch("Surface", colors.surface)
                    Swatch("SurfaceRaised", colors.surfaceRaised)
                    Swatch("Outline · decorative", colors.outline)
                    Swatch("Primary", colors.primary)
                    Swatch("OnPrimary", colors.onPrimary)
                    Swatch("Secondary", colors.secondary)
                    Swatch("Danger", colors.danger)
                    Swatch("Success", colors.success)
                    Swatch("TextPrimary", colors.textPrimary)
                    Swatch("TextSecondary", colors.textSecondary)
                }

                SectionLabel("02 / TYPE SCALE — RU + EN")
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    TypeSample("Display", "NULLWAVE / Музыка", type.display)
                    TypeSample("H1", "Библиотека / Library", type.h1)
                    TypeSample("H2", "Альбомы / Albums", type.h2)
                    TypeSample("Body", "Название трека / Track title", type.body)
                    TypeSample("Body emphasis", "Сейчас играет / Now playing", type.bodyEmphasis)
                    TypeSample("Caption", "Исполнитель / Artist", type.caption)
                    TypeSample("Micro", "АУДИО / LOCAL / FLAC", type.micro)
                    TypeSample("Mono", "03:47 / −01:12 · 320 kbps", type.mono)
                    TypeSample("Button", "СЛУШАТЬ / PLAY", type.button)
                    TypeSample("Tab", "ПЕСНИ / SONGS", type.tab)
                }

                SectionLabel("03 / SHAPES — CHAMFER + NODE")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ShapeSample("Small", NullWaveTheme.shapes.small)
                    ShapeSample("Medium", NullWaveTheme.shapes.medium)
                    ShapeSample("Large", NullWaveTheme.shapes.large)
                    ShapeSample("Node", NullWaveTheme.shapes.node)
                }

                SectionLabel("04 / SPACING")
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    listOf(
                        "xxs" to spacing.xxs,
                        "xs" to spacing.xs,
                        "sm" to spacing.sm,
                        "md" to spacing.md,
                        "lg" to spacing.lg,
                        "xl" to spacing.xl,
                        "xxl" to spacing.xxl,
                    ).forEach { (name, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Box(Modifier.size(width = value, height = spacing.xs).background(colors.primary))
                            Text("$name · ${value.value.toInt()} dp", style = type.mono)
                        }
                    }
                }

                SectionLabel("05 / SHARED DIMENSIONS")
                Text(
                    "Gutter ${NullWaveTheme.dimens.gutter.value.toInt()} dp\n" +
                        "Touch target ≥ ${NullWaveTheme.dimens.touchTarget.value.toInt()} dp\n" +
                        "Icon ${NullWaveTheme.dimens.icon.value.toInt()} dp\n" +
                        "Hairline ${NullWaveTheme.dimens.hairline.value.toInt()} dp",
                    style = type.mono,
                )

                SectionLabel("06 / MATERIAL BUTTONS")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ButtonSample(enabled = true)
                    ButtonSample(enabled = false)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = NullWaveTheme.typography.micro, color = NullWaveTheme.colors.textSecondary)
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(color)
                .border(NullWaveTheme.dimens.hairline, NullWaveTheme.colors.outline),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = NullWaveTheme.typography.body)
            Text(
                "#" + color.toArgb().toUInt().toString(16).takeLast(6).uppercase(),
                style = NullWaveTheme.typography.mono,
                color = NullWaveTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TypeSample(name: String, sample: String, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs)) {
        Text(
            "$name · ${style.fontSize.value.toInt()}/${style.lineHeight.value.toInt()} sp",
            style = NullWaveTheme.typography.mono,
            color = NullWaveTheme.colors.textSecondary,
        )
        Text(sample, style = style)
    }
}

@Composable
private fun ShapeSample(name: String, shape: Shape) {
    Column(verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
        Box(
            Modifier
                .size(64.dp)
                .background(NullWaveTheme.colors.surfaceRaised, shape)
                .border(NullWaveTheme.dimens.hairline, NullWaveTheme.colors.primary, shape),
        )
        Text(name, style = NullWaveTheme.typography.mono)
    }
}

@Composable
private fun ButtonSample(enabled: Boolean) {
    Button(
        onClick = {},
        enabled = enabled,
        modifier = Modifier.sizeIn(
            minWidth = NullWaveTheme.dimens.touchTarget,
            minHeight = NullWaveTheme.dimens.touchTarget,
        ),
        shape = NullWaveTheme.shapes.small,
    ) {
        Text(if (enabled) "Слушать / Play" else "Недоступно / Disabled")
    }
}
