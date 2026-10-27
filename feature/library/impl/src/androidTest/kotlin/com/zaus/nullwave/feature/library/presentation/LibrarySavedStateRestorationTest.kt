package com.zaus.nullwave.feature.library.presentation

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
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.core.result.DataError
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.di.LibraryEntries
import com.zaus.nullwave.feature.library.di.LibraryInitialLocale
import com.zaus.nullwave.feature.library.di.LibraryPlatformBindings
import com.zaus.nullwave.feature.library.di.LibraryRepositoryBindings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Real feature factory + registry + Parcel + new store/graph; no media access or OS process kill.
 * Main.immediate deliberately exercises continuation resumption inside source completion.
 */
class LibrarySavedStateRestorationTest {
    @Test
    fun albumsAndArtistsRestoreChoicesButRequireFreshCatalogAndAccess() = runBlocking(Dispatchers.Main.immediate) {
        for (tab in listOf(LibraryTab.Albums, LibraryTab.Artists)) {
            val firstRepository = RestorationCatalog()
            val firstOwner = LibraryRegistryOwner()
            val first = firstOwner.vm(graph(firstRepository, "ru"))
            val firstUi = first.uiApi()
            val firstJob = first.viewModelScope.coroutineContext.job
            val saved: Bundle
            try {
                firstUi.onIntent(LibraryIntent.SortChanged(LibraryTrackSort.Artist, SortDirection.Descending))
                firstUi.onIntent(LibraryIntent.AlbumSortChanged(SortDirection.Descending))
                firstUi.onIntent(LibraryIntent.ArtistSortChanged(SortDirection.Descending))
                firstUi.onIntent(LibraryIntent.TabSelected(tab))
                firstUi.onIntent(LibraryIntent.AccessChecked(LibraryAccess.Granted))
                val binding = launch(start = CoroutineStart.UNDISPATCHED) { first.withUiBinding { awaitCancellation() } }
                try {
                    withTimeout(5_000) { first.snapshot.first { it.state.selectedBody is LibraryBody.Ready<*> } }
                    saved = firstOwner.save().parcelCopy()
                } finally { binding.cancelAndJoin() }
            } finally { firstOwner.destroy() }
            // Clear requests cancellation; a scheduler yield does not guarantee source cleanup.
            withTimeout(5_000) { firstJob.join() }
            assertEquals(0, firstRepository.active)

            val repository = RestorationCatalog().apply { granted = false }
            val defaults = Bundle().apply { putString("library.tab", "Songs"); putString("library.direction", "Ascending") }
            val owner = LibraryRegistryOwner(saved, defaults)
            val restored = owner.vm(graph(repository, "sv-SE"))
            val restoredUi = restored.uiApi()
            val restoredJob = restored.viewModelScope.coroutineContext.job
            try {
                assertNotSame(first, restored)
                assertEquals(tab, restored.snapshot.value.state.selectedTab)
                assertEquals(LibraryTrackQuery("sv-SE", LibraryTrackSort.Artist, SortDirection.Descending), restored.snapshot.value.state.query)
                assertEquals(LibraryAlbumQuery("sv-SE", SortDirection.Descending), restored.snapshot.value.state.albumQuery)
                assertEquals(LibraryArtistQuery("sv-SE", SortDirection.Descending), restored.snapshot.value.state.artistQuery)
                assertSame(LibraryBody.Loading, restored.snapshot.value.state.selectedBody)
                assertEquals(0, repository.requests.size)
                restoredUi.onIntent(LibraryIntent.AccessChecked(LibraryAccess.Required(AccessAction.Request)))
                val binding = launch(start = CoroutineStart.UNDISPATCHED) { restored.withUiBinding { awaitCancellation() } }
                try {
                    withTimeout(5_000) { repository.firstRead.await() }
                    assertEquals(LibraryBody.AccessRequired(AccessAction.Request), restored.snapshot.value.state.selectedBody)
                    repository.granted = true
                    restoredUi.onIntent(LibraryIntent.AccessChecked(LibraryAccess.Granted))
                    withTimeout(5_000) { restored.snapshot.first { it.state.selectedBody is LibraryBody.Ready<*> } }
                    val expected = if (tab == LibraryTab.Albums) restored.snapshot.value.state.albumQuery else restored.snapshot.value.state.artistQuery
                    assertEquals(expected, repository.requests.last())
                } finally { binding.cancelAndJoin() }
            } finally { owner.destroy() }
            withTimeout(5_000) { restoredJob.join() }
            assertEquals(0, repository.active)
        }
    }

    @Test
    fun retainedStoreKeepsVmAndFurtherChangesRestoreIntoANewGraph() = runBlocking(Dispatchers.Main.immediate) {
        val originalGraph = graph(RestorationCatalog(), "ru")
        val firstOwner = LibraryRegistryOwner()
        val first = firstOwner.vm(originalGraph)
        val firstUi = first.uiApi()
        firstUi.onIntent(LibraryIntent.TabSelected(LibraryTab.Albums))
        val saved = firstOwner.save().parcelCopy()
        firstOwner.destroy(clearStore = false)
        val replacement = LibraryRegistryOwner(saved, viewModelStore = firstOwner.viewModelStore)
        val updated: Bundle
        try {
            val retained = replacement.vm(originalGraph)
            val retainedUi = retained.uiApi()
            assertSame(first, retained)
            assertSame(firstUi, retainedUi)
            retainedUi.onIntent(LibraryIntent.TabSelected(LibraryTab.Artists))
            retainedUi.onIntent(LibraryIntent.ArtistSortChanged(SortDirection.Descending))
            updated = replacement.save().parcelCopy()
        } finally { replacement.destroy() }
        val restoredOwner = LibraryRegistryOwner(updated)
        try {
            val restored = restoredOwner.vm(graph(RestorationCatalog(), "en-US"))
            assertNotSame(first, restored)
            assertEquals(LibraryTab.Artists, restored.snapshot.value.state.selectedTab)
            assertEquals(LibraryArtistQuery("en-US", SortDirection.Descending), restored.snapshot.value.state.artistQuery)
            assertSame(LibraryBody.Loading, restored.snapshot.value.state.selectedBody)
        } finally { restoredOwner.destroy() }
    }

    private fun graph(repository: LibraryCatalogRepository, locale: String) =
        createGraphFactory<LibraryRestorationGraph.Factory>().create(repository, locale)
}

@DependencyGraph(AppScope::class, excludes = [LibraryPlatformBindings::class, LibraryRepositoryBindings::class, LibraryEntries::class])
internal interface LibraryRestorationGraph : ViewModelGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides repository: LibraryCatalogRepository, @Provides @LibraryInitialLocale locale: String): LibraryRestorationGraph
    }
}

private class RestorationCatalog : LibraryCatalogRepository {
    val requests = mutableListOf<Any>()
    val firstRead = CompletableDeferred<Unit>()
    var granted = true
    var active = 0
    override fun observeTracks(query: LibraryTrackQuery) = observe<MediaTrack>(query)
    override fun observeAlbums(query: LibraryAlbumQuery) = observe<LibraryAlbum>(query)
    override fun observeArtists(query: LibraryArtistQuery) = observe<LibraryArtist>(query)
    private fun <T> observe(query: Any): Flow<AppResult<List<T>, DataError>> = flow {
        requests += query
        firstRead.complete(Unit)
        active++
        try {
            if (!granted) emit(AppResult.Failure(DataError.AccessDenied)) else {
                emit(AppResult.Success(emptyList()))
                awaitCancellation()
            }
        } finally { active-- }
    }
}

private class LibraryRegistryOwner(
    restored: Bundle? = null,
    defaults: Bundle = Bundle(),
    override val viewModelStore: ViewModelStore = ViewModelStore(),
) : SavedStateRegistryOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val lifecycle = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val defaultViewModelProviderFactory = ViewModelProvider.NewInstanceFactory()
    override val defaultViewModelCreationExtras = MutableCreationExtras().apply {
        this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@LibraryRegistryOwner
        this[VIEW_MODEL_STORE_OWNER_KEY] = this@LibraryRegistryOwner
        this[DEFAULT_ARGS_KEY] = defaults
    }
    init {
        controller.performAttach()
        enableSavedStateHandles()
        controller.performRestore(restored)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun vm(graph: LibraryRestorationGraph) = ViewModelProvider.create(this, graph.metroViewModelFactory)["library", LibraryViewModel::class]
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
        checkNotNull(parcel.readBundle(LibrarySavedStateRestorationTest::class.java.classLoader))
    } finally { parcel.recycle() }
}
