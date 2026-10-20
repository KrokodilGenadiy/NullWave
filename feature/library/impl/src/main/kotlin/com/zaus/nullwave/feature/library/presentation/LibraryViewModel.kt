package com.zaus.nullwave.feature.library.presentation

import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.api.repository.LibraryRepository
import com.zaus.nullwave.feature.library.di.LibraryInitialLocale
import com.zaus.nullwave.feature.library.impl.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey

/** Entry-owned presentation logic. Platform access requests belong to the future LibraryRoot. */
@AssistedInject
internal class LibraryViewModel(
    private val repository: LibraryRepository,
    @Assisted private val savedStateHandle: SavedStateHandle,
    @LibraryInitialLocale initialLocaleTag: String,
) : BaseMviViewModel<LibraryState, LibraryIntent, Nothing>(
    LibraryState(query = savedStateHandle.restoreQuery(initialLocaleTag)),
) {
    @AssistedFactory
    @ViewModelAssistedFactoryKey(LibraryViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): LibraryViewModel = create(extras.createSavedStateHandle())
        fun create(@Assisted savedStateHandle: SavedStateHandle): LibraryViewModel
    }

    override fun MviDefinition<LibraryState, LibraryIntent, Nothing>.define() {
        source(
            name = "library.tracks",
            input = { query },
            flow = repository::observeTracks,
        ) { _, result ->
            copy(body = body.applyResult(result))
        }

        on<LibraryIntent.Refresh> {
            val ready = state.body as? LibraryBody.Ready ?: return@on
            if (ready.refresh == RefreshState.Running) return@on
            reduce { copy(body = ready.copy(refresh = RefreshState.Running)) }
            restartSources()
        }
        on<LibraryIntent.Retry> {
            val current = state.body
            if (current !is LibraryBody.Failed &&
                !(current is LibraryBody.Ready && current.refresh is RefreshState.Failed)
            ) return@on
            reduce { copy(body = body.startReading()) }
            restartSources()
        }
        on<LibraryIntent.AccessChecked> { intent ->
            reduce {
                copy(body = when (val access = intent.access) {
                    LibraryAccess.Granted -> body.startReading()
                    is LibraryAccess.Required -> LibraryBody.AccessRequired(access.action)
                })
            }
            // A fresh OS check invalidates even an equal query/grant after returning to the screen.
            // Publish the loading/gate state first: a synchronous Flow must have the final word.
            restartSources()
        }
        on<LibraryIntent.SortChanged> { intent ->
            reduce { withQuery(query.copy(sort = intent.sort, direction = intent.direction)) }
            // Persist the current state, not captured intent fields that a reentrant UI may replace.
            savedStateHandle[SORT_KEY] = state.query.sort.name
            savedStateHandle[DIRECTION_KEY] = state.query.direction.name
        }
        on<LibraryIntent.LocaleChanged> { intent ->
            reduce { withQuery(query.copy(localeTag = intent.localeTag)) }
        }
    }
}

private fun LibraryState.withQuery(next: LibraryTrackQuery): LibraryState = if (next == query) this else copy(
    query = next,
    body = (body as? LibraryBody.AccessRequired) ?: LibraryBody.Loading,
)

private fun LibraryBody.startReading(): LibraryBody = when (this) {
    is LibraryBody.Ready -> copy(refresh = RefreshState.Running)
    else -> LibraryBody.Loading
}

private fun LibraryBody.applyResult(result: AppResult<List<LibraryTrack>, DataError>): LibraryBody =
    when (result) {
        is AppResult.Success -> LibraryBody.Ready(result.value.toList())
        is AppResult.Failure -> when (result.error) {
            DataError.AccessDenied -> (this as? LibraryBody.AccessRequired)
                ?: LibraryBody.AccessRequired(AccessAction.CheckAgain)
            DataError.Io -> if (this is LibraryBody.Ready) {
                copy(refresh = RefreshState.Failed(UiText.Resource(R.string.library_refresh_failed)))
            } else {
                LibraryBody.Failed(UiText.Resource(R.string.library_load_failed))
            }
            DataError.NotFound, DataError.StorageFull -> throw IllegalStateException(
                "Library observation returned an unsupported read failure: ${result.error}", result.cause,
            )
        }
    }

private const val SORT_KEY = "library.sort"
private const val DIRECTION_KEY = "library.direction"

private fun SavedStateHandle.restoreQuery(localeTag: String): LibraryTrackQuery = LibraryTrackQuery(
    localeTag = localeTag,
    sort = LibraryTrackSort.entries.firstOrNull { it.name == get<Any?>(SORT_KEY) } ?: LibraryTrackSort.Title,
    direction = SortDirection.entries.firstOrNull { it.name == get<Any?>(DIRECTION_KEY) }
        ?: SortDirection.Ascending,
)
