package com.zaus.nullwave.feature.library.data.source

import android.database.MatrixCursor
import android.provider.MediaStore.Audio.Media
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import com.zaus.nullwave.feature.library.data.repository.MediaStoreLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android projection, cursor reader and repository with in-memory media rows. */
@RunWith(AndroidJUnit4::class)
class MediaStoreArtistDetailsInstrumentedTest {
    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun artistReadPreservesCompleteDownloadAlbumsOtherArtistsAndTracksWithoutAlbums() = runBlocking {
        val details = readArtist(30) {
            for (id in 1..9) {
                newRow().add(Media._ID, id).add(Media.VOLUME_NAME, "external_primary")
                    .add(Media.ARTIST_ID, if (id == 4 || id == 8) 4 else 7)
                    .add(Media.ARTIST, if (id == 4 || id == 8) "Nullwave" else "Guest")
                    .add(Media.TITLE, "Track $id").add(Media.ALBUM, "Download")
                    .add(Media.ALBUM_ID, if (id == 1) 37 else 52)
                    .add(Media.RELATIVE_PATH, "Download/").add(Media.ALBUM_ARTIST, null)
            }
            newRow().add(Media._ID, 10).add(Media.VOLUME_NAME, "external_primary")
                .add(Media.ARTIST_ID, 4).add(Media.ARTIST, "Nullwave").add(Media.TITLE, "Regular")
                .add(Media.ALBUM, "Signals").add(Media.ALBUM_ID, 37).add(Media.RELATIVE_PATH, "Music/")
            newRow().add(Media._ID, 11).add(Media.VOLUME_NAME, "external_primary")
                .add(Media.ARTIST_ID, 4).add(Media.ARTIST, "Nullwave").add(Media.DISPLAY_NAME, "Loose.mp3")
            newRow().add(Media._ID, 4).add(Media.VOLUME_NAME, "card")
                .add(Media.ARTIST_ID, 4).add(Media.ARTIST, "Nullwave").add(Media.TITLE, "Other volume")
                .add(Media.ALBUM_ID, 52).add(Media.ALBUM, "Download").add(Media.RELATIVE_PATH, "Download/")
        }

        assertEquals(4, details.artist.trackCount)
        assertEquals(2, details.artist.albumCount)
        assertEquals(listOf(11, 10, 4, 8).map { "external_primary:$it" }, details.tracks.map { it.id.value })
        val download = details.albums.single { it.id == AlbumId("external_primary:folder/Download") }
        val ordinary = details.albums.single { it.id == AlbumId("external_primary:37") }
        assertEquals(9, download.trackCount)
        assertEquals(1, ordinary.trackCount)
        assertEquals(download.artwork, ordinary.artwork)
        assertNull(details.tracks.first().artwork)
    }

    @Test
    fun androidTenArtistReadKeepsProviderAlbumsWithoutAlbumArtistColumn() = runBlocking {
        val details = readArtist(29) {
            assertTrue(getColumnIndex("album_artist") < 0)
            for (id in 1..2) {
                newRow().add(Media._ID, id).add(Media.VOLUME_NAME, "external_primary")
                    .add(Media.ARTIST_ID, 4).add(Media.ARTIST, "Nullwave").add(Media.TITLE, "Track $id")
                    .add(Media.ALBUM_ID, if (id == 1) 37 else 52).add(Media.ALBUM, "Download")
            }
        }
        assertEquals(listOf("external_primary:37", "external_primary:52"), details.albums.map { it.id.value })
        assertEquals(2, details.artist.trackCount)
        assertEquals(2, details.artist.albumCount)
    }

    private suspend fun readArtist(sdk: Int, populate: MatrixCursor.() -> Unit): LibraryArtistDetails {
        var queries = 0
        var observers = 0
        lateinit var cursor: MatrixCursor
        val source = object : MediaStoreAudioSource {
            override fun hasReadAccess() = true
            override fun observeChanges(onChanged: () -> Unit): AutoCloseable {
                observers++
                return AutoCloseable { observers-- }
            }

            override suspend fun readRows(albumId: AlbumId?): List<MediaStoreAudioRow> {
                assertNull(albumId)
                assertEquals(1, observers)
                return queryCatalogRows(albumId, sdk, Dispatchers.IO) { columns, selection, args, _ ->
                    queries++
                    val expected = if (sdk >= 30) "is_pending = 0 AND is_trashed = 0" else "is_pending = 0"
                    assertEquals(expected, selection)
                    assertNull(args)
                    MatrixCursor(columns).apply(populate).also { cursor = it }
                }
            }
        }
        val result = MediaStoreLibraryRepository(source, Dispatchers.IO)
            .observeArtistDetails(ArtistId("external_primary:4"), "en").first() as AppResult.Success
        assertEquals(1, queries)
        assertEquals(0, observers)
        assertTrue(cursor.isClosed)
        return result.value
    }
}
