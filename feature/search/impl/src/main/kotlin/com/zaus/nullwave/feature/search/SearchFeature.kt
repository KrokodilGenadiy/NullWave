package com.zaus.nullwave.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Scaffolding, not a screen. Empty / typing / no-results states, with results grouped by type.
 */
@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "SEARCH",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
        )
    }
}
