package com.zaus.nullwave.core.designsystem.components.selection

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zaus.nullwave.core.designsystem.icon.NullWaveIcon
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Ready-to-display action. [id] is a stable identity, independent of label, order or availability.
 * [label] is also the accessible name. [dangerous] changes styling; confirmation belongs to caller.
 * Replace the list/items when inputs change; do not mutate an ordinary list in place.
 */
data class NullWaveActionStripAction(
    val id: String,
    val label: String,
    @param:DrawableRes val icon: Int,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val dangerous: Boolean = false,
)

/**
 * Controlled action panel: icons above wrapping labels, one Button target per action.
 * [enabled] gates the whole panel in addition to each action's availability. It never infers
 * availability from selection count, performs a bulk operation or opens a confirmation dialog.
 *
 * Equal-width columns reflow with available width and font scale. Each cell prefers at least 64 dp
 * at normal scale and grows with larger text; allow at least a 48 dp target plus the panel gutters.
 * Height is content-driven. The host owns insets and, in short windows, bounded vertical scrolling.
 * An unbounded-width parent gets one column. Empty actions produce no panel or reserved space.
 */
@Composable
fun NullWaveActionStrip(
    actions: List<NullWaveActionStripAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(actions.all { it.id.isNotBlank() && it.label.isNotBlank() }) {
        "Action IDs and labels must not be blank"
    }
    require(actions.map { it.id }.toSet().size == actions.size) { "Action IDs must be unique" }
    if (actions.isEmpty()) return

    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val spacing = NullWaveTheme.spacing
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.fillMaxWidth().background(colors.surface)
            .drawBehind {
                val stroke = dimens.hairline.toPx()
                drawLine(colors.outline, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
            }
            .padding(horizontal = dimens.gutter, vertical = spacing.xs),
    ) {
        val gap = spacing.xxs
        val gapPx = with(density) { gap.roundToPx() }
        val minCellPx = with(density) { (ActionMinCellWidth * maxOf(1f, fontScale)).roundToPx() }
        val columns = if (constraints.hasBoundedWidth) {
            ((constraints.maxWidth + gapPx) / (minCellPx + gapPx)).coerceIn(1, actions.size)
        } else 1
        // Round down in pixels so independent child rounding cannot push the final cell to a new row.
        val cellWidthPx = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - gapPx * (columns - 1)) / columns
        } else minCellPx
        val cellWidth = with(density) { cellWidthPx.toDp() }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            maxItemsInEachRow = columns,
        ) {
            // A single keyed parent preserves interaction/focus state when columns or order change.
            actions.forEach { action ->
                key(action.id) {
                    ActionStripItem(action, enabled && action.enabled, Modifier.width(cellWidth))
                }
            }
        }
    }
}

@Composable
private fun ActionStripItem(action: NullWaveActionStripAction, enabled: Boolean, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = NullWaveTheme.colors
    val dimens = NullWaveTheme.dimens
    val spacing = NullWaveTheme.spacing
    val accent = if (action.dangerous) colors.danger else colors.textPrimary
    val pressedHighlighted = enabled && pressed
    val foreground = when {
        !enabled -> colors.textSecondary
        pressedHighlighted -> colors.bg
        else -> accent
    }
    Surface(
        // Guard direct semantics invocation as well as pointer/keyboard activation.
        onClick = { if (enabled) action.onClick() },
        modifier = modifier.defaultMinSize(minWidth = dimens.touchTarget, minHeight = dimens.touchTarget)
            .semantics { role = Role.Button },
        enabled = enabled,
        shape = NullWaveTheme.shapes.small,
        color = if (pressedHighlighted) accent else Color.Transparent,
        contentColor = foreground,
        border = if (enabled && focused) BorderStroke(dimens.focusBorder, foreground) else null,
        interactionSource = source,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xxs, vertical = spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            NullWaveIcon(action.icon, contentDescription = null, modifier = Modifier.size(dimens.icon), tint = foreground)
            Text(action.label, style = NullWaveTheme.typography.mono, textAlign = TextAlign.Center)
        }
    }
}

private val ActionMinCellWidth = 64.dp
