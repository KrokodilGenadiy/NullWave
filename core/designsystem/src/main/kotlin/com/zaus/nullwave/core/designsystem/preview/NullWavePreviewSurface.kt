package com.zaus.nullwave.core.designsystem.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

/**
 * Window host for standalone preview sheets inside [NullWaveTheme].
 *
 * The background fills the window; safe drawing insets constrain the content before its scrolling
 * modifiers. Sheets keep their own spacing and layout. Scaffold previews already own their insets
 * and must not use this host.
 */
@Composable
internal fun NullWavePreviewSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NullWaveTheme.colors.bg,
        contentColor = NullWaveTheme.colors.textPrimary,
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            content()
        }
    }
}
