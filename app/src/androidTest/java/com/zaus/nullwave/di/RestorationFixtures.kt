package com.zaus.nullwave.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.mvi.BaseMviViewModel
import com.zaus.nullwave.core.mvi.UiEffect
import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.dsl.MviDefinition
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.awaitCancellation

/** Test-only graph keeps probe bindings out of the application's generated graph. */
@DependencyGraph(AppScope::class)
internal interface RestorationGraph : ViewModelGraph {
    val dependency: RestorationDependency
    val entryProviderInstallers: Set<EntryProviderInstaller>

    // AppScope also aggregates real feature factories, which now require platform bindings.
    @Provides
    @ForScope(AppScope::class)
    fun applicationContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
}

@Inject
@SingleIn(AppScope::class)
internal class RestorationDependency {
    var created = 0
}

internal data class RestorationState(
    val query: String = "",
    val selectedId: String? = null,
    val loading: Boolean = false,
) : UiState

internal sealed interface RestorationIntent : UiIntent {
    data class Edit(val query: String, val selectedId: String? = null) : RestorationIntent
    data object Start : RestorationIntent
    data object Notify : RestorationIntent
}

internal data object RestorationEffect : UiEffect

@AssistedInject
internal class RestorationVm(
    @Assisted private val savedStateHandle: SavedStateHandle,
    val dependency: RestorationDependency,
) : BaseMviViewModel<RestorationState, RestorationIntent, RestorationEffect>(
    initialState = RestorationState(
        query = savedStateHandle["query"] ?: "",
        selectedId = savedStateHandle["selectedId"],
    ),
) {
    var clears = 0
    var workStarts = 0
    var workStops = 0

    init {
        dependency.created++
        addCloseable { clears++ }
    }

    override fun MviDefinition<RestorationState, RestorationIntent, RestorationEffect>.define() {
        val work = idle<Unit, Unit>("work") {
            onStarted { copy(loading = true) }
            execute {
                workStarts++
                try {
                    awaitCancellation()
                } finally {
                    workStops++
                }
            }
            onFinished { copy(loading = false) }
        }
        on<RestorationIntent.Edit> { intent ->
            reduce { copy(query = intent.query, selectedId = intent.selectedId) }
            // Persistence is explicit and outside the pure reducer; transient fields stay in memory.
            savedStateHandle["query"] = state.query
            savedStateHandle["selectedId"] = state.selectedId
        }
        on<RestorationIntent.Start> { submit(work, Unit) }
        on<RestorationIntent.Notify> { tryEffect(RestorationEffect) }
    }

    @AssistedFactory
    @ViewModelAssistedFactoryKey(RestorationVm::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): RestorationVm = create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): RestorationVm
    }
}
