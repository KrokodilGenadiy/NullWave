package com.zaus.nullwave.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.components.content.NullWaveArtworkPlaceholder
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.designsystem.components.layout.NullWaveScaffold
import com.zaus.nullwave.core.designsystem.theme.NullWaveEffects
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Fixed visual states, not a MediaStore request or an artificially delayed production loader. */
@Preview(name = "Library artwork states", widthDp = 360, heightDp = 420)
@Composable
private fun LibraryArtworkStatesPreview() {
    NullWaveTheme(effects = NullWaveEffects(reduceMotion = true)) {
        NullWaveScaffold { insets ->
            Column(Modifier.padding(insets).padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md)) {
                Text("Artwork / 48 dp", style = NullWaveTheme.typography.h2)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md)) {
                    NullWaveCoverArt(painter = PreviewCoverPainter)
                    Text("Loaded (sample)", style = NullWaveTheme.typography.body)
                }
                for ((placeholder, label) in listOf(
                    NullWaveArtworkPlaceholder.Missing to "Missing / failed",
                    NullWaveArtworkPlaceholder.Loading to "Loading",
                )) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md)) {
                        NullWaveCoverArt(monogram = "НГ", placeholder = placeholder)
                        Text(label, style = NullWaveTheme.typography.body)
                    }
                }
            }
        }
    }
}

private object PreviewCoverPainter : Painter() {
    override val intrinsicSize = Size(96f, 96f)
    override fun DrawScope.onDraw() {
        drawRect(Brush.linearGradient(listOf(Color(0xFF102939), Color(0xFF00BFCF), Color(0xFF12141C))))
        repeat(5) { line ->
            val x = size.width * (0.12f + line * 0.17f)
            drawLine(Color(0xFFFCEE0A), Offset(x, size.height * 0.75f),
                Offset(x, size.height * (0.2f + (line % 3) * 0.15f)), strokeWidth = size.width * 0.04f)
        }
    }
}
