package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Technical caption. Preserves casing and units; callers supply any uppercase display copy. */
@Composable
fun NullWaveMicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NullWaveTheme.colors.textSecondary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = NullWaveTheme.typography.micro,
        maxLines = maxLines,
        overflow = overflow,
    )
}
