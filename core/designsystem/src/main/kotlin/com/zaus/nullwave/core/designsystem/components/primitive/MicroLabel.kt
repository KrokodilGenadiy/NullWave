package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * The 10sp uppercase mono caption used as field labels, section headers and decorative data strings
 * (`SYS.0x4F2A`, `AUD/LOCAL/READ_OK`).
 *
 * Uppercases [text] itself. `TextStyle` cannot case-transform, and this style is uppercase
 * everywhere it appears, so doing it here stops `.uppercase()` being scattered across every screen
 * and then forgotten in one of them.
 *
 * **Design rule: texture, not meaning.** These are deliberately small and dim - well below the
 * contrast the body scale holds. Never put information here that appears nowhere else. If a user
 * has to read it to use the app, it belongs in `caption` at least.
 */
@Composable
fun NullWaveMicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NullWaveTheme.colors.textTertiary,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text.uppercase(),
        style = NullWaveTheme.typography.micro,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@Preview(name = "Micro label", showBackground = true)
@Composable
private fun MicroLabelPreview() {
    NullWaveTheme {
        val colors = NullWaveTheme.colors
        Column(
            modifier = Modifier
                .background(colors.bg)
                .padding(NullWaveTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xs),
        ) {
            // Section header - the most common use.
            NullWaveMicroLabel("01 / color — core")
            // Decorative data string.
            NullWaveMicroLabel("AUD/LOCAL/READ_OK", color = colors.textSecondary)
            NullWaveMicroLabel("SYS.0x4F2A", color = colors.textSecondary)
            // Accented, for a value readout sitting under a control.
            NullWaveMicroLabel("+5.3 dB", color = colors.primary)
            // Truncation, since these often sit in narrow slots.
            NullWaveMicroLabel(
                text = "a very long decorative string that will not fit in its slot",
                modifier = Modifier.padding(end = NullWaveTheme.spacing.xxl),
            )
        }
    }
}
