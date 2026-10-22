package com.zaus.nullwave.feature.library.api.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.zaus.nullwave.feature.library.api.model.ArtworkRef

/** Library-owned thumbnail rendering. A null/unreadable reference uses the title's monogram. */
interface LibraryArtworkRenderer {
    @Composable
    fun Artwork(artwork: ArtworkRef?, title: String, size: Dp, modifier: Modifier = Modifier)
}
