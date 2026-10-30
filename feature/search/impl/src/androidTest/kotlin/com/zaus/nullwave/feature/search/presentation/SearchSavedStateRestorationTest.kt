package com.zaus.nullwave.feature.search.presentation

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.zaus.nullwave.core.playback.api.PlaybackController
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.search.di.SearchInitialLocale
import com.zaus.nullwave.feature.search.di.SearchPlatformBindings
import com.zaus.nullwave.feature.search.ui.RecordingSearchRepository
import com.zaus.nullwave.feature.search.testing.IdlePlaybackController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Android registry/Parcel round trips through the actual assisted factory, without rendering UI.
 * Main.immediate deliberately exercises continuation resumption inside source completion.
 */
class SearchSavedStateRestorationTest {
    @Test
    fun newGraphRestoresRawTextButNotAccessResultsOrOldLocale() = runBlocking(Dispatchers.Main.immediate) {
        val firstRepository = RecordingSearchRepository()
        val firstOwner = SearchRegistryOwner(defaults = Bundle().apply { putString("search.query", "legacy default") })
        val first = firstOwner.vm(graph(firstRepository, "ru"))
        val firstUi = first.uiApi()
        val firstJob = first.viewModelScope.coroutineContext.job
        val saved: Bundle
        try {
            assertEquals("legacy default", first.snapshot.value.state.rawQuery)
            firstUi.onIntent(SearchIntent.QueryChanged("  Ёлка 🌊  "))
            firstUi.onIntent(SearchIntent.AccessChecked(true))
            val binding = launch(start = CoroutineStart.UNDISPATCHED) { first.withUiBinding { awaitCancellation() } }
            try {
                withTimeout(5_000) { first.snapshot.first { it.state.body is SearchBody.Ready } }
                saved = firstOwner.save().parcelCopy()
            } finally { binding.cancelAndJoin() }
        } finally { firstOwner.destroy() }
        // Clear requests cancellation; wait for the source's finally, not just the UI binding.
        withTimeout(5_000) { firstJob.join() }
        assertEquals(0, firstRepository.active)

        val repository = RecordingSearchRepository()
        val owner = SearchRegistryOwner(saved, Bundle().apply { putString("search.query", "must not replace restored text") })
        val restored = owner.vm(graph(repository, "sv-SE"))
        val restoredUi = restored.uiApi()
        val restoredJob = restored.viewModelScope.coroutineContext.job
        try {
            assertNotSame(first, restored)
            assertEquals("  Ёлка 🌊  ", restored.snapshot.value.state.rawQuery)
            assertEquals("елка 🌊", restored.snapshot.value.state.query?.text)
            assertEquals("sv-SE", restored.snapshot.value.state.localeTag)
            assertSame(SearchAccess.Unchecked, restored.snapshot.value.state.access)
            assertSame(SearchBody.CheckingAccess, restored.snapshot.value.state.body)
            val binding = launch(start = CoroutineStart.UNDISPATCHED) { restored.withUiBinding { awaitCancellation() } }
            try {
                yield()
                assertEquals(0, repository.calls.size)
                restoredUi.onIntent(SearchIntent.AccessChecked(false))
                assertSame(SearchBody.AccessRequired, restored.snapshot.value.state.body)
                assertEquals(0, repository.calls.size)
                restoredUi.onIntent(SearchIntent.AccessChecked(true))
                withTimeout(5_000) { restored.snapshot.first { it.state.body is SearchBody.Ready } }
                assertEquals(listOf(restored.snapshot.value.state.query), repository.calls)
            } finally { binding.cancelAndJoin() }
        } finally { owner.destroy() }
        withTimeout(5_000) { restoredJob.join() }
        assertEquals(0, repository.active)
    }

    @Test
    fun retainedVmCanClearTextAndNewStoreDoesNotReviveDefaultArguments() = runBlocking(Dispatchers.Main.immediate) {
        val firstGraph = graph(RecordingSearchRepository(), "ru")
        val firstOwner = SearchRegistryOwner()
        val first = firstOwner.vm(firstGraph)
        val firstUi = first.uiApi()
        firstUi.onIntent(SearchIntent.QueryChanged("before recreation"))
        val saved = firstOwner.save().parcelCopy()
        firstOwner.destroy(clearStore = false)
        val replacement = SearchRegistryOwner(saved, viewModelStore = firstOwner.viewModelStore)
        val updated: Bundle
        try {
            val retained = replacement.vm(firstGraph)
            val retainedUi = retained.uiApi()
            assertSame(first, retained)
            assertSame(firstUi, retainedUi)
            retainedUi.onIntent(SearchIntent.ClearQuery)
            updated = replacement.save().parcelCopy()
        } finally { replacement.destroy() }
        val repository = RecordingSearchRepository()
        val restoredOwner = SearchRegistryOwner(updated, Bundle().apply { putString("search.query", "stale default") })
        try {
            val restored = restoredOwner.vm(graph(repository, "en-US"))
            val restoredUi = restored.uiApi()
            assertNotSame(first, restored)
            assertEquals("", restored.snapshot.value.state.rawQuery)
            assertSame(SearchBody.Idle, restored.snapshot.value.state.body)
            restoredUi.onIntent(SearchIntent.AccessChecked(true))
            val binding = launch(start = CoroutineStart.UNDISPATCHED) { restored.withUiBinding { awaitCancellation() } }
            try { yield(); assertEquals(0, repository.calls.size) } finally { binding.cancelAndJoin() }
        } finally { restoredOwner.destroy() }
    }

    private fun graph(repository: LibrarySearchRepository, locale: String) =
        createGraphFactory<SearchRestorationGraph.Factory>().create(repository, IdlePlaybackController, locale)
}

@DependencyGraph(AppScope::class, excludes = [SearchPlatformBindings::class])
internal interface SearchRestorationGraph : ViewModelGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides repository: LibrarySearchRepository,
            @Provides playbackController: PlaybackController,
            @Provides @SearchInitialLocale locale: String,
        ): SearchRestorationGraph
    }
}

private class SearchRegistryOwner(
    restored: Bundle? = null,
    defaults: Bundle = Bundle(),
    override val viewModelStore: ViewModelStore = ViewModelStore(),
) : SavedStateRegistryOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val lifecycle = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val defaultViewModelProviderFactory = ViewModelProvider.NewInstanceFactory()
    override val defaultViewModelCreationExtras = MutableCreationExtras().apply {
        this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@SearchRegistryOwner
        this[VIEW_MODEL_STORE_OWNER_KEY] = this@SearchRegistryOwner
        this[DEFAULT_ARGS_KEY] = defaults
    }
    init {
        controller.performAttach()
        enableSavedStateHandles()
        controller.performRestore(restored)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun vm(graph: SearchRestorationGraph) = ViewModelProvider.create(this, graph.metroViewModelFactory)["search", SearchViewModel::class]
    fun save() = Bundle().also(controller::performSave)
    fun destroy(clearStore: Boolean = true) {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (clearStore) viewModelStore.clear()
    }
}

private fun Bundle.parcelCopy(): Bundle {
    val parcel = Parcel.obtain()
    return try {
        parcel.writeBundle(this)
        parcel.setDataPosition(0)
        checkNotNull(parcel.readBundle(SearchSavedStateRestorationTest::class.java.classLoader))
    } finally { parcel.recycle() }
}
