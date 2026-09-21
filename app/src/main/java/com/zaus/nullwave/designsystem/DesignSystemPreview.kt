package com.zaus.nullwave.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.designsystem.theme.NullWaveColorVariant
import com.zaus.nullwave.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.designsystem.theme.QualityTierColors

/**
 * Previews only - nothing here is wired into the app.
 *
 * This is the token sheet and the icon sheet rendered from the real tokens, so a change to
 * [com.zaus.nullwave.designsystem.theme.NullWaveColors] or the type scale shows up here
 * immediately. Use it to confirm the bundled fonts resolve and that every icon passes the squint
 * test at 24dp.
 */
@Preview(name = "Tokens - Yellow", heightDp = 1180, showBackground = true)
@Composable
private fun TokenSheetYellowPreview() = TokenSheet(NullWaveColorVariant.Yellow)

@Preview(name = "Tokens - Crimson", heightDp = 1180, showBackground = true)
@Composable
private fun TokenSheetCrimsonPreview() = TokenSheet(NullWaveColorVariant.Crimson)

@Preview(name = "Tokens - Dive", heightDp = 1180, showBackground = true)
@Composable
private fun TokenSheetDivePreview() = TokenSheet(NullWaveColorVariant.Dive)

@Preview(name = "Icon sheet", heightDp = 900, showBackground = true)
@Composable
private fun IconSheetPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(NullWaveTheme.dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(NullWaveIcons.All) { (label, id) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(colors.surface, NullWaveTheme.shapes.small)
                            .border(1.dp, colors.outline, NullWaveTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = NullWaveIcons.painter(id),
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(NullWaveTheme.dimens.icon),
                        )
                    }
                    Text(
                        text = label.uppercase(),
                        style = NullWaveTheme.typography.micro,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenSheet(variant: NullWaveColorVariant) {
    NullWaveTheme(variant = variant) {
        val colors = NullWaveTheme.colors
        val type = NullWaveTheme.typography
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(NullWaveTheme.dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.lg),
        ) {
            Text(
                text = "NULLWAVE / ${variant.name.uppercase()}",
                style = type.display,
                color = colors.textPrimary,
            )

            SectionLabel("01 / COLOR - CORE")
            Column(verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                Swatch("Bg", colors.bg)
                Swatch("Surface", colors.surface)
                Swatch("SurfaceRaised", colors.surfaceRaised)
                Swatch("Outline", colors.outline)
                Swatch("Primary", colors.primary)
                Swatch("PrimaryDim", colors.primaryDim)
                Swatch("Secondary", colors.secondary)
                Swatch("Danger", colors.danger)
                Swatch("Success", colors.success)
                Swatch("TextPrimary", colors.textPrimary)
                Swatch("TextSecondary", colors.textSecondary)
                Swatch("TextTertiary", colors.textTertiary)
                Swatch("Paper", colors.paper)
            }

            SectionLabel("02 / QUALITY TIER STRIPE - FIXED, EXEMPT FROM THE THEME SWAP")
            Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md)) {
                TierStripe("<=128", QualityTierColors.Low)
                TierStripe("192-256", QualityTierColors.Standard)
                TierStripe("320", QualityTierColors.High)
                TierStripe("LOSSLESS", QualityTierColors.Lossless)
                TierStripe("HI-RES", QualityTierColors.HiRes)
            }

            SectionLabel("03 / TYPE SCALE")
            Column(verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                Text("Display 28/32", style = type.display, color = colors.textPrimary)
                Text("H1 22/28", style = type.h1, color = colors.textPrimary)
                Text("H2 18/24", style = type.h2, color = colors.textPrimary)
                Text("Body 15/22 - track titles, list content", style = type.body, color = colors.textPrimary)
                Text("Caption 13/18 - artist, metadata", style = type.caption, color = colors.textSecondary)
                Text("MICRO 10/14 - AUD/LOCAL/READ_OK", style = type.micro, color = colors.textSecondary)
                Text("Mono 12/16 tabular - 03:47 / -01:12  320 kbps", style = type.mono, color = colors.textSecondary)
                Text("BUTTON LABEL", style = type.button, color = colors.primary)
                Text("SONGS", style = type.tab, color = colors.primary)
            }

            SectionLabel("04 / SHAPE - CHAMFER 8 / 12 / 16, OCTAGON NODE")
            Row(
                horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .width(84.dp)
                        .size(width = 84.dp, height = 56.dp)
                        .background(colors.surfaceRaised, NullWaveTheme.shapes.small)
                        .border(1.dp, colors.outline, NullWaveTheme.shapes.small)
                )
                Box(
                    Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .background(colors.surfaceRaised, NullWaveTheme.shapes.medium)
                        .border(1.dp, colors.outline, NullWaveTheme.shapes.medium)
                )
                Box(
                    Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .background(colors.surfaceRaised, NullWaveTheme.shapes.large)
                        .border(1.dp, colors.outline, NullWaveTheme.shapes.large)
                )
                Box(
                    Modifier
                        .size(56.dp)
                        .background(colors.surfaceRaised, NullWaveTheme.shapes.node)
                        .border(1.5.dp, colors.primary, NullWaveTheme.shapes.node)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = NullWaveTheme.typography.micro,
        color = NullWaveTheme.colors.textTertiary,
    )
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
                .border(1.dp, NullWaveTheme.colors.outline)
        )
        Text(name, style = NullWaveTheme.typography.body, color = NullWaveTheme.colors.textPrimary)
    }
}

@Composable
private fun TierStripe(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = NullWaveTheme.dimens.tierStripe, height = 22.dp)
                .background(color)
        )
        Text(label, style = NullWaveTheme.typography.mono, color = NullWaveTheme.colors.textSecondary)
    }
}
