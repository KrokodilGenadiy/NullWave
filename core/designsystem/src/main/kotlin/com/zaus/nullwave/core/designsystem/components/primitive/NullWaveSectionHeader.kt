package com.zaus.nullwave.core.designsystem.components.primitive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/** Wrapping section title with an optional supporting caption and a semantic heading. */
@Composable
fun NullWaveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.xxs),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = NullWaveTheme.typography.h2,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = NullWaveTheme.colors.textSecondary,
                style = NullWaveTheme.typography.caption,
            )
        }
    }
}
