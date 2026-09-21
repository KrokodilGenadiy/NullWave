package com.zaus.nullwave.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.core.di.ActivityScope
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.feature.search.api.SearchKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * Scaffolding, not a screen. Empty / typing / no-results states, with results grouped by type.
 *
 * The shape to copy: contribute an [EntryProviderInstaller] into the set, and the host picks the
 * screen up without ever importing it. Nothing in :app changes when you flesh this out.
 */
@ContributesTo(ActivityScope::class)
@BindingContainer
object SearchFeatureBindings {

    @Provides
    @IntoSet
    fun searchEntries(): EntryProviderInstaller = {
        entry<SearchKey> { SearchScreen() }
    }
}

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