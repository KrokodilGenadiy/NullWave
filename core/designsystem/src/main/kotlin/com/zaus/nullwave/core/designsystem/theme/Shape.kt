package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class NullWaveShapes(
    val small: CornerBasedShape = chamfer(8.dp),
    val medium: CornerBasedShape = chamfer(12.dp),
    val large: CornerBasedShape = chamfer(16.dp),
    val node: Shape = OctagonShape,
) {
    companion object { val Default = NullWaveShapes() }
}

/** Logical corners also mirror correctly in RTL layouts. */
fun chamfer(cut: Dp): CutCornerShape = CutCornerShape(
    topStart = 0.dp,
    topEnd = cut,
    bottomEnd = 0.dp,
    bottomStart = cut,
)

val OctagonShape: Shape = GenericShape { size, _ ->
    val x = size.width * 0.3f
    val y = size.height * 0.3f
    moveTo(x, 0f)
    lineTo(size.width - x, 0f)
    lineTo(size.width, y)
    lineTo(size.width, size.height - y)
    lineTo(size.width - x, size.height)
    lineTo(x, size.height)
    lineTo(0f, size.height - y)
    lineTo(0f, y)
    close()
}
