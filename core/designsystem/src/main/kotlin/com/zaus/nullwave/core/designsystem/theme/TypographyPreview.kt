package com.zaus.nullwave.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zaus.nullwave.core.designsystem.preview.NullWavePreviewSurface

@Preview(name = "Typography - Latin and Cyrillic", widthDp = 360, heightDp = 500, locale = "en")
@Preview(name = "Typography - Large text", widthDp = 320, heightDp = 850, fontScale = 2f, locale = "ru")
@Composable
private fun TypographyScriptsPreview() {
    NullWaveTheme {
        NullWavePreviewSurface {
            Column(Modifier.padding(NullWaveTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(NullWaveTheme.spacing.md)) {
                val type = NullWaveTheme.typography
                Text("LIBRARY", style = type.h2)
                Text("БИБЛИОТЕКА", style = type.h2)
                Text("SONGS / ПЕСНИ", style = type.tab)
                Text("PLAY / СЛУШАТЬ", style = type.button)
                Text("Night City · Ночной город", style = type.h1)
                Text("Deadzone Chorus / Название трека", style = type.body)
                Text("SORT · A–Z ▲ / ФИЛЬТР", style = type.mono)
            }
        }
    }
}
