package com.zaus.nullwave.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.components.content.NullWaveCoverArt
import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.navigation.EntryProviderInstaller
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.AlbumArtistCredit
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.api.model.search.LibraryAlbumSearchHit
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.repository.LibraryAlbumDetailsRepository
import com.zaus.nullwave.feature.library.api.repository.LibraryCatalogRepository
import com.zaus.nullwave.feature.library.api.repository.LibrarySearchRepository
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessHost
import com.zaus.nullwave.feature.library.api.ui.LibraryAccessUi
import com.zaus.nullwave.feature.library.api.ui.LibraryArtworkRenderer
import com.zaus.nullwave.feature.library.api.ui.libraryMonogram
import com.zaus.nullwave.feature.library.di.LibraryRepositoryBindings
import com.zaus.nullwave.feature.library.di.LibraryUiBindings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.flow.flowOf

/** Keep production entries and VM factories, but give both origins the same deterministic catalog. */
@DependencyGraph(AppScope::class, excludes = [LibraryRepositoryBindings::class, LibraryUiBindings::class])
internal interface AlbumNavigationGraph : ViewModelGraph {
    val entries: Set<EntryProviderInstaller>

    @Provides @ForScope(AppScope::class)
    fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Provides fun catalog(): LibraryCatalogRepository = Albums
    @Provides fun search(): LibrarySearchRepository = Albums
    @Provides fun details(): LibraryAlbumDetailsRepository = Albums
    @Provides fun access(): LibraryAccessHost = object : LibraryAccessHost {
        @Composable override fun Content(onAccessChecked: (LibraryAccess) -> Unit, content: @Composable (LibraryAccessUi) -> Unit) {
            LaunchedEffect(Unit) { onAccessChecked(LibraryAccess.Granted) }
            content(LibraryAccessUi(access = LibraryAccess.Granted))
        }
    }
    @Provides fun artwork(): LibraryArtworkRenderer = object : LibraryArtworkRenderer {
        @Composable override fun Artwork(artwork: ArtworkRef?, title: String, size: Dp, modifier: Modifier) {
            NullWaveCoverArt(modifier = modifier, monogram = libraryMonogram(title), size = size)
        }
    }
}

private object Albums : LibraryCatalogRepository, LibrarySearchRepository, LibraryAlbumDetailsRepository {
    private val albums = (1..60).map { LibraryAlbum(AlbumId("fixture:$it"), "Record $it", AlbumArtistCredit.Named("Artist"), 1) }
    override fun observeTracks(query: LibraryTrackQuery) = flowOf(AppResult.Success(emptyList<MediaTrack>()))
    override fun observeArtists(query: LibraryArtistQuery) = flowOf(AppResult.Success(emptyList<LibraryArtist>()))
    override fun observeAlbums(query: LibraryAlbumQuery) = flowOf(AppResult.Success(albums))
    override fun observeSearch(query: LibrarySearchQuery) = flowOf(AppResult.Success(LibrarySearchResults(
        emptyList(),
        albums.filter { it.title?.contains(query.text, ignoreCase = true) == true }.map { LibraryAlbumSearchHit(it, emptyList()) },
        emptyList(),
    )))
    override fun observeAlbumDetails(albumId: AlbumId, localeTag: String) = albums.single { it.id == albumId }.let { album ->
        flowOf(AppResult.Success(LibraryAlbumDetails.create(album, listOf(
            MediaTrack(TrackId(albumId.value), "Track from ${album.title}", "Artist", album.title, 120_000),
        ))))
    }
}
