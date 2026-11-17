package com.zaus.nullwave.feature.library.presentation.albumdetails

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueOrder
import com.zaus.nullwave.core.playback.api.command.PlaybackQueueRequest
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.repository.LibraryAlbumDetailsRepository
import com.zaus.nullwave.feature.library.di.LibraryInitialLocale
import com.zaus.nullwave.feature.library.presentation.toLibraryPlaybackMessage
import com.zaus.nullwave.feature.player.api.launch.playbackLaunch
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
    private val playback: PlaybackController,
) : BaseMviViewModel<AlbumDetailsState, AlbumDetailsIntent, Nothing>(
    initialState = AlbumDetailsState(initialLocaleTag),
    sourceStopTimeoutMillis = 0,
) {
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

        val play = playbackLaunch(
            name = "albumDetails.play",
            controller = playback,
            launchState = { playbackLaunch },
            withLaunchState = { copy(playbackLaunch = it) },
            // Keep the action Android supplied, such as SettingsSuggested, if the album is already closed.
            closeAccess = {
                copy(body = body as? AlbumDetailsBody.AccessRequired ?: AlbumDetailsBody.AccessRequired(AccessAction.CheckAgain))
            },
            message = { it.toLibraryPlaybackMessage() },
        )
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
            submit(play.operation, request)
        }
        on<AlbumDetailsIntent.DismissPlaybackError> { reduce { copy(playbackLaunch = playbackLaunch.dismissed()) } }
        on<AlbumDetailsIntent.AccessChecked> { intent ->
            when (val access = intent.access) {
                LibraryAccess.Granted -> {
                    play.restoreAccess(this, intent.checkedThroughDenial, album) {
                        copy(platformGranted = true, body = AlbumDetailsBody.Loading(body.details))
                    }
                }
                is LibraryAccess.Required -> {
                    cancel(play.operation)
                    reduce {
                        copy(
                            platformGranted = false,
                            body = AlbumDetailsBody.AccessRequired(access.action),
                            playbackLaunch = playbackLaunch.dismissed(),
                        )
                    }
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
