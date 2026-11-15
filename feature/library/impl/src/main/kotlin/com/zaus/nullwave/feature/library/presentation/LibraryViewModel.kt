package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.observeCurrentTrackId
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.result.EmptyResult
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
import kotlinx.coroutines.flow.emptyFlow

/** Entry-owned presentation logic. Platform access requests belong to LibraryRoot. */
@AssistedInject
internal class LibraryViewModel(
    private val repository: LibraryCatalogRepository,
    @Assisted savedStateHandle: SavedStateHandle,
    @LibraryInitialLocale initialLocaleTag: String,
    private val playback: PlaybackController,
) : BaseMviViewModel<LibraryState, LibraryIntent, Nothing>(
    initialState = LibraryState(LibraryTrackQuery(initialLocaleTag)),
    savedState = librarySavedState(savedStateHandle),
) {
    private val playbackObservation = LibraryPlaybackObservation(playback)

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
            // A confirmed access rejection must not be undone by a later catalog publication.
            // AccessChecked(Granted) reopens the gate and explicitly refreshes this source.
            if (selectedBody is LibraryBody.AccessRequired) this else applyResult(result)
        }

        val playSong = latest<PlaybackQueueRequest, EmptyResult<PlaybackCommandError>>("library.playSong") {
            execute(playbackObservation::execute)
            onAccepted { copy(playbackNotice = null, monitorPlayback = true) }
            onResult { _, result -> withPlaybackResult(result) }
        }
        source(
            name = "library.playbackAccess",
            input = { monitorPlayback },
            flow = { enabled -> if (enabled) playbackObservation.observe() else emptyFlow() },
        ) { _, update -> withPlaybackAccess(update) }
        source(
            name = "library.nowPlaying",
            flow = playback::observeCurrentTrackId,
        ) { trackId -> copy(playingTrackId = trackId) }

        on<LibraryIntent.PlaySong> { intent ->
            if (state.selectedTab != LibraryTab.Songs || state.body !is LibraryBody.Ready) return@on
            val selected = intent.displayedSongs.indexOfFirst { it.id == intent.trackId }
            if (selected < 0) return@on
            val request = PlaybackQueueRequest.create(intent.displayedSongs.map { it.id }, selected)
            submit(playSong, request)
        }
        on<LibraryIntent.DismissPlaybackError> { reduce { copy(playbackNotice = null) } }

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
            when (val access = intent.access) {
                LibraryAccess.Granted -> {
                    val denialAtCheck = playbackObservation.publishedDenial()
                    refresh(catalog) { startReadingSelected().copy(lastPlaybackDenial = denialAtCheck) }
                }
                is LibraryAccess.Required -> {
                    cancel(playSong)
                    refresh(catalog) { requireAccess(access.action).copy(playbackNotice = null) }
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
