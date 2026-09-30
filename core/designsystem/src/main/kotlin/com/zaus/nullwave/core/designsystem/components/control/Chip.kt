package com.zaus.nullwave.core.designsystem.components.control

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcons
import com.zaus.nullwave.core.designsystem.modifier.chamferBorder
import com.zaus.nullwave.core.designsystem.components.primitive.NullWaveMicroLabel
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.designsystem.theme.chamfer

/**
 * A filter or sort chip - the row above the Songs tab, and the equalizer's preset row.
 *
 * Selected is a **solid** `primary` fill with a dark label, not a tint: the component sheet shows
 * `background:#FCEE0A; color:#07090B` against unselected `border:#243038; color:#93A3AD`. The
 * silhouette never changes, only the fill, so the row does not jump when the selection moves.
 *
 * `minimumInteractiveComponentSize()` expands the *touch* target to 48dp without changing the
 * visual height, which is how a 32dp chip can still satisfy the design's 48dp floor. Sizing the
 * chip itself to 48dp would make the filter row twice as tall as the artboards show it.
 */
@Composable
fun NullWaveChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val colors = NullWaveTheme.colors

    val isFilled = isSelected && isEnabled
    val contentColor = when {
        !isEnabled -> colors.textTertiary
        isSelected -> colors.onPrimary
        else -> colors.textSecondary
    }
    val background = if (isFilled) colors.primary else Color.Transparent

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = ChipHeight)
            .clip(ChipShape)
            .background(background)
            // The selected chip has no border - the fill is its edge.
            .then(if (isFilled) Modifier else Modifier.chamferBorder(colors.outline, ChipShape))
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = NullWaveTheme.spacing.sm, vertical = NullWaveTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(ChipIconSize),
            )
        }
        Text(
            text = text.uppercase(),
            style = NullWaveTheme.typography.micro,
            color = contentColor,
        )
    }
}

/** 7dp, per the component sheet - a chip is smaller than a button, so its cut is too. */
private val ChipShape = chamfer(7.dp)
private val ChipHeight = 30.dp
private val ChipIconSize = 16.dp

@Preview(name = "Chip", showBackground = true)
@Composable
private fun ChipPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.sm),
        ) {
            NullWaveMicroLabel("sort / filter row")
            Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                NullWaveChip("A–Z", isSelected = true, onClick = {}, leadingIcon = NullWaveIcons.Sort)
                NullWaveChip("Recent", isSelected = false, onClick = {})
                NullWaveChip("Duration", isSelected = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs)) {
                NullWaveChip("Lossless", isSelected = true, onClick = {}, leadingIcon = NullWaveIcons.Filter)
                NullWaveChip("Disabled", isSelected = false, onClick = {}, isEnabled = false)
            }
        }
    }
}
