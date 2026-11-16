package com.zaus.nullwave.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.observeCurrentTrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.player.api.launch.PlaybackProblemKey
import com.zaus.nullwave.feature.player.api.launch.playbackLaunch
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
    private val playbackController: PlaybackController,
    @Assisted savedStateHandle: SavedStateHandle,
    @SearchInitialLocale initialLocaleTag: String,
) : BaseMviViewModel<SearchState, SearchIntent, Nothing>(
    initialState = SearchState(initialLocaleTag),
    savedState = searchSavedState(savedStateHandle),
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

        val playTrack = playbackLaunch(
            name = "search.playTrack",
            controller = playbackController,
            launchState = { playbackLaunch },
            withLaunchState = { copy(playbackLaunch = it) },
            closeAccess = { copy(body = SearchBody.AccessRequired) },
            message = { it.toSearchPlaybackMessage() },
        )
        source(
            name = "search.nowPlaying",
            flow = playbackController::observeCurrentTrackId,
        ) { trackId -> copy(playingTrackId = trackId) }

        var denialAtLastPlay: PlaybackProblemKey? = null
        on<SearchIntent.PlayTrack> { intent ->
            if (state.body !is SearchBody.Ready) return@on
            val selected = intent.displayedTracks.indexOfFirst { it.id == intent.trackId }
            if (selected < 0) return@on
            denialAtLastPlay = playTrack.publishedDenial()
            submit(playTrack.operation, PlaybackQueueRequest.create(intent.displayedTracks.map { it.id }, selected))
        }
        on<SearchIntent.DismissPlaybackError> { reduce { copy(playbackLaunch = playbackLaunch.dismissed()) } }

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
            if (intent.granted) {
                val denialAtCheck = playTrack.publishedDenial()
                // On Back the service may have reported a denial while Search had no collector.
                val deniedSincePlay = denialAtCheck != null && denialAtCheck != denialAtLastPlay
                if (state.body == SearchBody.AccessRequired || state.access == SearchAccess.Required || deniedSincePlay) {
                    cancel(playTrack.operation)
                }
                refresh(results) { withAccess(true).copy(playbackLaunch = playbackLaunch.accessGranted(denialAtCheck)) }
            } else {
                cancel(playTrack.operation)
                refresh(results) { withAccess(false).copy(playbackLaunch = playbackLaunch.dismissed()) }
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
