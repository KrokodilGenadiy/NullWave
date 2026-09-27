package com.zaus.nullwave.feature.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "ABOUT",
            style = NullWaveTheme.typography.h1,
            color = NullWaveTheme.colors.textPrimary,
        )
    }
}