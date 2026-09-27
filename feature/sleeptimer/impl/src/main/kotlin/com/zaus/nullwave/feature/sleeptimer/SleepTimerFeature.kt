package com.zaus.nullwave.feature.sleeptimer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Scaffolding, not a screen. Designed as a sheet; kept as a destination so the drawer can reach it
 * directly.
 */
@Composable
fun SleepTimerScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "SLEEP TIMER",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
        )
    }
}
