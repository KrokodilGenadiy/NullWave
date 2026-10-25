package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.di.LibraryInitialLocale
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey

/** Entry-owned presentation logic. Platform access requests belong to LibraryRoot. */
@AssistedInject
internal class LibraryViewModel(
    private val repository: LibraryCatalogRepository,
    @Assisted savedStateHandle: SavedStateHandle,
    @LibraryInitialLocale initialLocaleTag: String,
) : BaseMviViewModel<LibraryState, LibraryIntent, Nothing>(
    initialState = LibraryState(LibraryTrackQuery(initialLocaleTag)),
    savedState = librarySavedState(savedStateHandle),
) {
    @AssistedFactory
    @ViewModelAssistedFactoryKey(LibraryViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): LibraryViewModel = create(extras.createSavedStateHandle())
        fun create(@Assisted savedStateHandle: SavedStateHandle): LibraryViewModel
    }

    override fun MviDefinition<LibraryState, LibraryIntent, Nothing>.define() {
        val catalog = source(
            name = "library.catalog",
            input = { catalogRequest },
            flow = repository::observe,
        ) { _, result ->
            applyResult(result)
        }

        on<LibraryIntent.Refresh> {
            val ready = state.selectedBody as? LibraryBody.Ready<*> ?: return@on
            if (ready.refresh == RefreshState.Running) return@on
            refresh(catalog) { startReadingSelected() }
        }
        on<LibraryIntent.Retry> {
            val current = state.selectedBody
            if (current !is LibraryBody.Failed &&
                !(current is LibraryBody.Ready<*> && current.refresh is RefreshState.Failed)
            ) return@on
            refresh(catalog) { startReadingSelected() }
        }
        on<LibraryIntent.AccessChecked> { intent ->
            refresh(catalog) {
                when (val access = intent.access) {
                    LibraryAccess.Granted -> startReadingSelected()
                    is LibraryAccess.Required -> requireAccess(access.action)
                }
            }
        }
        on<LibraryIntent.SortChanged> { intent ->
            reduce { withInputs(copy(query = query.copy(sort = intent.sort, direction = intent.direction))) }
        }
        on<LibraryIntent.TabSelected> { intent ->
            reduce { withInputs(copy(selectedTab = intent.tab)) }
        }
        on<LibraryIntent.AlbumSortChanged> { intent ->
            reduce { withInputs(copy(albumQuery = albumQuery.copy(direction = intent.direction))) }
        }
        on<LibraryIntent.ArtistSortChanged> { intent ->
            reduce { withInputs(copy(artistQuery = artistQuery.copy(direction = intent.direction))) }
        }
        on<LibraryIntent.LocaleChanged> { intent ->
            reduce {
                withInputs(copy(
                    query = query.copy(localeTag = intent.localeTag),
                    albumQuery = albumQuery.copy(localeTag = intent.localeTag),
                    artistQuery = artistQuery.copy(localeTag = intent.localeTag),
                ))
            }
        }
    }
}
