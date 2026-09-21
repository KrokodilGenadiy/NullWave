package com.zaus.nullwave.designsystem.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape tokens.
 *
 * Rounded corners appear nowhere in this system. Containers are chamfered - a rectangle with two
 * opposite 45-degree cut corners - and "node" elements (avatars, slider handles, perk-tree-style
 * markers) use the flattened [OctagonShape] instead of a circle.
 */
@Immutable
data class NullWaveShapes(
    /** 8dp chamfer. Chips, icon plates, checkboxes, small containers. */
    val small: Shape,
    /** 12dp chamfer. Cards, rows, dialogs, sheets. */
    val medium: Shape,
    /** 16dp chamfer. Full-bleed panels and the expanded player's framing. */
    val large: Shape,
    /** The flattened octagon node. Avatars, slider handles, EQ band handles. */
    val node: Shape,
) {
    companion object {
        val Default: NullWaveShapes = NullWaveShapes(
            small = chamfer(8.dp),
            medium = chamfer(12.dp),
            large = chamfer(16.dp),
            node = OctagonShape,
        )
    }
}

/**
 * A rectangle with 45-degree cut corners.
 *
 * The house default cuts the top-end and bottom-start corners, which is the diagonal the game's
 * panels use. Pass the flags to cut a different pair, or all four.
 */
fun chamfer(
    cut: Dp,
    topStart: Boolean = false,
    topEnd: Boolean = true,
    bottomEnd: Boolean = false,
    bottomStart: Boolean = true,
): CutCornerShape {
    val on = CornerSize(cut)
    val off = CornerSize(0.dp)
    return CutCornerShape(
        topStart = if (topStart) on else off,
        topEnd = if (topEnd) on else off,
        bottomEnd = if (bottomEnd) on else off,
        bottomStart = if (bottomStart) on else off,
    )
}

/**
 * The perk-tree node: a flattened octagon whose diagonals cut 30% in from each edge.
 *
 * Scales with the element, so a 28dp EQ handle and a 64dp drawer avatar are the same silhouette.
 */
val OctagonShape: Shape = GenericShape { size, _ ->
    val insetX = size.width * 0.3f
    val insetY = size.height * 0.3f
    moveTo(insetX, 0f)
    lineTo(size.width - insetX, 0f)
    lineTo(size.width, insetY)
    lineTo(size.width, size.height - insetY)
    lineTo(size.width - insetX, size.height)
    lineTo(insetX, size.height)
    lineTo(0f, size.height - insetY)
    lineTo(0f, insetY)
    close()
}
