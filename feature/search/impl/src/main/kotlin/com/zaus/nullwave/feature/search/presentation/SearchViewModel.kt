package com.zaus.nullwave.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.search.di.SearchInitialLocale
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

/** Presentation only. Root owns platform checks; the repository remains main-safe and cold. */
@AssistedInject
internal class SearchViewModel(
    private val repository: LibrarySearchRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
    @SearchInitialLocale initialLocaleTag: String,
) : BaseMviViewModel<SearchState, SearchIntent, Nothing>(
    savedStateHandle.restoreSearchState(initialLocaleTag),
    // Search is ephemeral: leaving the UI cancels pending debounce and observations immediately.
    sourceStopTimeoutMillis = 0,
) {
    @AssistedFactory
    @ViewModelAssistedFactoryKey(SearchViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): SearchViewModel = create(extras.createSavedStateHandle())
        fun create(@Assisted savedStateHandle: SavedStateHandle): SearchViewModel
    }

    override fun MviDefinition<SearchState, SearchIntent, Nothing>.define() {
        source(
            name = "search.results",
            input = { request },
            flow = { query ->
                flow {
                    if (query != null) {
                        // MVI has already fenced/cancelled the previous input before this delay.
                        delay(SEARCH_DEBOUNCE_MILLIS.milliseconds)
                        emitAll(repository.observeSearch(query))
                    }
                }
            },
        ) { _, result -> applyResult(result) }

        on<SearchIntent.QueryChanged> { intent ->
            reduce { withInput(rawQuery = intent.value) }
            // A reentrant UI may already have replaced the text during publication.
            savedStateHandle.saveSearchQuery(state)
        }
        on<SearchIntent.ClearQuery> {
            reduce { withInput(rawQuery = "") }
            savedStateHandle.saveSearchQuery(state)
        }
        on<SearchIntent.LocaleChanged> { intent ->
            reduce { withInput(localeTag = intent.localeTag) }
        }
        on<SearchIntent.AccessChecked> { intent ->
            val previous = state.request
            reduce { withAccess(intent.granted) }
            // Equal granted inputs still need a fresh read after returning from settings/resume.
            // Changed inputs already invalidate and restart the keyed source.
            if (state.request != null && previous == state.request) restartSources()
        }
        on<SearchIntent.Retry> {
            val body = state.body
            if (body !is SearchBody.Failed &&
                !(body is SearchBody.Ready && body.refresh is SearchRefresh.Failed)
            ) return@on
            reduce { copy(body = body.startReading()) }
            restartSources()
        }
    }
}

/** Applied per collection, including retry/rebind; provider notifications are not delayed. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L
