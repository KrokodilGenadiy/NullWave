package com.zaus.nullwave.feature.library.presentation.albumdetails

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackCommandError
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueOrder
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.core.result.EmptyResult
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.repository.LibraryAlbumDetailsRepository
import com.zaus.nullwave.feature.library.di.LibraryInitialLocale
import com.zaus.nullwave.feature.library.presentation.LibraryPlaybackObservation
import com.zaus.nullwave.feature.library.presentation.toLibraryPlaybackMessage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformWhile

@AssistedInject
internal class AlbumDetailsViewModel(
    @Assisted private val albumId: AlbumId,
    private val repository: LibraryAlbumDetailsRepository,
    @LibraryInitialLocale initialLocaleTag: String,
    playback: PlaybackController,
) : BaseMviViewModel<AlbumDetailsState, AlbumDetailsIntent, Nothing>(
    initialState = AlbumDetailsState(initialLocaleTag),
    sourceStopTimeoutMillis = 0,
) {
    private val playbackObservation = LibraryPlaybackObservation(playback)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted albumId: AlbumId): AlbumDetailsViewModel
    }

    override fun MviDefinition<AlbumDetailsState, AlbumDetailsIntent, Nothing>.define() {
        val album = source(
            name = "albumDetails.album",
            input = { localeTag.takeIf { platformGranted } },
            flow = { locale -> if (locale == null) emptyFlow() else observeUntilAccessDenied(locale) },
        ) { _, result ->
            if (body is AlbumDetailsBody.AccessRequired) return@source this
            when (result) {
                is AppResult.Success -> copy(body = AlbumDetailsBody.Ready(result.value))
                is AppResult.Failure -> when (result.error) {
                    DataError.NotFound -> copy(body = AlbumDetailsBody.NotFound)
                    DataError.AccessDenied -> copy(body = AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain))
                    DataError.Io -> copy(body = AlbumDetailsBody.ReadFailed(body.details))
                    DataError.StorageFull -> throw IllegalStateException("Unexpected StorageFull during an album read", result.cause)
                }
            }
        }

        val play = latest<PlaybackQueueRequest, EmptyResult<PlaybackCommandError>>("albumDetails.play") {
            execute(playbackObservation::execute)
            onAccepted { copy(playbackError = null, monitorPlayback = true) }
            onResult { _, result ->
                val error = (result as? AppResult.Failure)?.error ?: return@onResult this
                val message = error.toLibraryPlaybackMessage() ?: return@onResult this
                val denied = error is PlaybackCommandError.SourceFailure && error.error == PlaybackSourceError.AccessDenied
                copy(
                    playbackError = message,
                    body = if (denied) AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain) else body,
                )
            }
        }
        source(
            name = "albumDetails.playbackAccess",
            input = { monitorPlayback },
            flow = { enabled -> if (enabled) playbackObservation.observe() else emptyFlow() },
        ) { _, update ->
            if (update.denial == update.initialDenial || update.denial == lastPlaybackDenial) this
            else copy(body = AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain), lastPlaybackDenial = update.denial)
        }
        on<AlbumDetailsIntent.Play> { intent ->
            if (state.body.details == null || intent.displayed.album.id != albumId) return@on
            val ids = intent.displayed.tracks.map { it.id }
            val request = when (val start = intent.start) {
                is AlbumPlaybackStart.At -> {
                    if (start.index !in ids.indices) return@on
                    PlaybackQueueRequest.create(ids, start.index, order = PlaybackQueueOrder.InOrder)
                }
                AlbumPlaybackStart.Shuffle ->
                    PlaybackQueueRequest.create(ids, ids.indices.random(), order = PlaybackQueueOrder.Shuffled)
            }
            submit(play, request)
        }
        on<AlbumDetailsIntent.DismissPlaybackError> { reduce { copy(playbackError = null) } }
        on<AlbumDetailsIntent.AccessChecked> { intent ->
            when (val access = intent.access) {
                LibraryAccess.Granted -> refresh(album) {
                    copy(platformGranted = true, body = AlbumDetailsBody.Loading(body.details))
                }
                is LibraryAccess.Required -> {
                    cancel(play)
                    reduce { copy(platformGranted = false, body = AlbumDetailsBody.AccessRequired(access.action), playbackError = null) }
                }
            }
        }
        on<AlbumDetailsIntent.Retry> {
            if (state.body is AlbumDetailsBody.ReadFailed) {
                refresh(album) { copy(body = AlbumDetailsBody.Loading(body.details)) }
            }
        }
        on<AlbumDetailsIntent.LocaleChanged> { intent ->
            if (state.localeTag == intent.localeTag) return@on
            reduce {
                copy(
                    localeTag = intent.localeTag,
                    body = when (body) {
                        AlbumDetailsBody.CheckingAccess, is AlbumDetailsBody.AccessRequired -> body
                        else -> AlbumDetailsBody.Loading()
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUntilAccessDenied(locale: String) = snapshot
        .transformWhile { current ->
            val allowed = current.state.body !is AlbumDetailsBody.AccessRequired
            // Emit the denial to cancel the live read, then finish this attempt. A grant starts a new one.
            emit(allowed)
            allowed
        }
        .distinctUntilChanged()
        .flatMapLatest { allowed -> if (allowed) repository.observeAlbumDetails(albumId, locale) else emptyFlow() }
        .buffer(0)
}
