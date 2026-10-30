package com.zaus.nullwave.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.result.EmptyResult
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
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration.Companion.milliseconds

/** Presentation only. Root owns platform checks; the repository remains main-safe and cold. */
@AssistedInject
internal class SearchViewModel(
    private val repository: LibrarySearchRepository,
    playbackController: PlaybackController,
    @Assisted savedStateHandle: SavedStateHandle,
    @SearchInitialLocale initialLocaleTag: String,
) : BaseMviViewModel<SearchState, SearchIntent, Nothing>(
    initialState = SearchState(initialLocaleTag),
    savedState = searchSavedState(savedStateHandle),
    // Search is ephemeral: leaving the UI cancels pending debounce and observations immediately.
    sourceStopTimeoutMillis = 0,
) {
    private val playbackObservation = SearchPlaybackObservation(playbackController)

    @AssistedFactory
    @ViewModelAssistedFactoryKey(SearchViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): SearchViewModel = create(extras.createSavedStateHandle())
        fun create(@Assisted savedStateHandle: SavedStateHandle): SearchViewModel
    }

    override fun MviDefinition<SearchState, SearchIntent, Nothing>.define() {
        val results = source(
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
        ) { _, result ->
            // A playback/provider denial stays closed until Root supplies a fresh access check.
            if (body == SearchBody.AccessRequired) this else applyResult(result)
        }

        val playTrack = latest<PlaybackQueueRequest, EmptyResult<PlaybackCommandError>>("search.playTrack") {
            execute(playbackObservation::execute)
            onAccepted { copy(playbackNotice = null, monitorPlayback = true) }
            onResult { _, result -> withPlaybackResult(result) }
        }
        source(
            name = "search.playback",
            input = { monitorPlayback },
            flow = { enabled -> if (enabled) playbackObservation.observe() else emptyFlow() },
        ) { _, update -> withPlaybackConnection(update) }

        on<SearchIntent.PlayTrack> { intent ->
            if (state.body !is SearchBody.Ready) return@on
            val selected = intent.displayedTracks.indexOfFirst { it.id == intent.trackId }
            if (selected < 0) return@on
            submit(playTrack, PlaybackQueueRequest.create(intent.displayedTracks.map { it.id }, selected))
        }
        on<SearchIntent.DismissPlaybackError> { reduce { copy(playbackNotice = null) } }

        on<SearchIntent.QueryChanged> { intent ->
            reduce { withInput(rawQuery = intent.value) }
        }
        on<SearchIntent.ClearQuery> {
            reduce { withInput(rawQuery = "") }
        }
        on<SearchIntent.LocaleChanged> { intent ->
            reduce { withInput(localeTag = intent.localeTag) }
        }
        on<SearchIntent.AccessChecked> { intent ->
            if (!intent.granted) cancel(playTrack)
            // Fresh access checks re-read even an unchanged query; null input still performs no I/O.
            refresh(results) {
                withAccess(intent.granted).let { if (intent.granted) it else it.copy(playbackNotice = null) }
            }
        }
        on<SearchIntent.Retry> {
            val body = state.body
            if (body !is SearchBody.Failed &&
                !(body is SearchBody.Ready && body.refresh is SearchRefresh.Failed)
            ) return@on
            refresh(results) { copy(body = body.startReading()) }
        }
    }
}

/** Applied per collection, including retry/rebind; provider notifications are not delayed. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L
