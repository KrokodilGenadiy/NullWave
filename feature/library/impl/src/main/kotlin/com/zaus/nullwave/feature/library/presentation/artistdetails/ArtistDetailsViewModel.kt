package com.zaus.nullwave.feature.library.presentation.artistdetails

import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.repository.LibraryArtistDetailsRepository
import com.zaus.nullwave.feature.library.di.LibraryInitialLocale
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

@AssistedInject
internal class ArtistDetailsViewModel(
    @Assisted private val artistId: ArtistId,
    private val repository: LibraryArtistDetailsRepository,
    @LibraryInitialLocale initialLocaleTag: String,
) : BaseMviViewModel<ArtistDetailsState, ArtistDetailsIntent, Nothing>(
    initialState = ArtistDetailsState(initialLocaleTag),
    sourceStopTimeoutMillis = 0,
) {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted artistId: ArtistId): ArtistDetailsViewModel
    }

    override fun MviDefinition<ArtistDetailsState, ArtistDetailsIntent, Nothing>.define() {
        val artist = source(
            name = "artistDetails.artist",
            input = { localeTag.takeIf { platformGranted } },
            flow = { locale -> if (locale == null) emptyFlow() else observeUntilAccessDenied(locale) },
        ) { _, result ->
            if (body is ArtistDetailsBody.AccessRequired) return@source this
            when (result) {
                is AppResult.Success -> copy(body = ArtistDetailsBody.Ready(result.value))
                is AppResult.Failure -> when (result.error) {
                    DataError.NotFound -> copy(body = ArtistDetailsBody.NotFound)
                    DataError.AccessDenied -> copy(body = ArtistDetailsBody.AccessRequired(AccessAction.CheckAgain))
                    DataError.Io -> copy(body = ArtistDetailsBody.ReadFailed(body.details))
                    DataError.StorageFull -> throw IllegalStateException("Unexpected StorageFull during an artist read", result.cause)
                }
            }
        }

        on<ArtistDetailsIntent.AccessChecked> { intent ->
            when (val access = intent.access) {
                LibraryAccess.Granted -> refresh(artist) {
                    copy(platformGranted = true, body = ArtistDetailsBody.Loading(body.details))
                }
                is LibraryAccess.Required -> reduce {
                    copy(platformGranted = false, body = ArtistDetailsBody.AccessRequired(access.action))
                }
            }
        }
        on<ArtistDetailsIntent.Retry> {
            if (state.body is ArtistDetailsBody.ReadFailed) {
                refresh(artist) { copy(body = ArtistDetailsBody.Loading(body.details)) }
            }
        }
        on<ArtistDetailsIntent.LocaleChanged> { intent ->
            if (state.localeTag == intent.localeTag) return@on
            reduce {
                copy(
                    localeTag = intent.localeTag,
                    body = when (body) {
                        ArtistDetailsBody.CheckingAccess, is ArtistDetailsBody.AccessRequired -> body
                        else -> ArtistDetailsBody.Loading()
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUntilAccessDenied(locale: String) = flow {
        // A denied attempt stays closed across rebinds. Granted resets body and refreshes the source.
        if (snapshot.value.state.body is ArtistDetailsBody.AccessRequired) return@flow
        emitAll(repository.observeArtistDetails(artistId, locale).transformWhile { result ->
            emit(result)
            result !is AppResult.Failure || result.error != DataError.AccessDenied
        })
    }
}
