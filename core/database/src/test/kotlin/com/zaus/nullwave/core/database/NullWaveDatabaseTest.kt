package com.zaus.nullwave.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zaus.nullwave.data.database.NullWaveDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The schema, against a real SQLite engine, on the JVM. No device, no emulator, no uninstall cycle.
 *
 * This is the checkpoint in DATA.md: a wrong `.sq` is found here in a second. On a device the same
 * mistake costs an uninstall each time, because without a migration SQLite will not bring an existing
 * database file forward.
 *
 * `JdbcSqliteDriver.IN_MEMORY` plus `Schema.create` is the whole setup. Note it exercises the generated
 * DDL, so a malformed `CREATE INDEX` or a column type SQLite rejects fails here too.
 */
class NullWaveDatabaseTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: NullWaveDatabase

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NullWaveDatabase.Schema.create(driver)
        database = NullWaveDatabase(driver)
        // Off by default on every connection, and the playlist cascades depend on it. The production
        // equivalent is the onOpen callback in DatabaseBindings.
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    }

    @After
    fun tearDown() = driver.close()

    @Test
    fun `tracks sort case-insensitively`() {
        insertTrack(id = 1, title = "Zappa Variations")
        insertTrack(id = 2, title = "abba Mode")
        insertTrack(id = 3, title = "Midpoint")

        val titles = database.trackQueries.selectAllByTitle().executeAsList().map { it.title }

        // The whole reason for COLLATE NOCASE. Byte order would put "Zappa" before "abba", and the
        // fast-scroller's letter buckets would disagree with the visible list.
        assertEquals(listOf("abba Mode", "Midpoint", "Zappa Variations"), titles)
    }

    @Test
    fun `rescan updates metadata without touching user state`() {
        insertTrack(id = 1, title = "Deadzone Chorus")
        database.trackQueries.setFavourite(is_favourite = true, id = 1)
        database.trackQueries.recordPlay(last_played_at = 1_700_000_000_000, id = 1)

        // What an incremental rescan does to a row whose file changed.
        database.trackQueries.updateScanned(
            title = "Deadzone Chorus (Remaster)",
            artist_name = "Static Pilgrim",
            album_name = "Nullwave EP",
            album_id = 10,
            artist_id = 20,
            duration_ms = 227_000,
            track_no = 3,
            disc_no = 1,
            year = 2077,
            mime_type = "audio/flac",
            size_bytes = 29_000_000,
            relative_path = "Music/Static Pilgrim/",
            display_name = "03 Deadzone Chorus.flac",
            date_modified = 1_700_000_100,
            bitrate = 1_022_000,
            id = 1,
        )

        val track = database.trackQueries.selectById(1).executeAsOne()
        assertEquals("Deadzone Chorus (Remaster)", track.title)
        // The point of the test: these survived. INSERT OR REPLACE would have reset both.
        assertTrue(track.is_favourite)
        assertEquals(1L, track.play_count)
    }

    @Test
    fun `insertIfNew leaves an existing row alone`() {
        insertTrack(id = 1, title = "Original")
        database.trackQueries.setFavourite(is_favourite = true, id = 1)

        insertTrack(id = 1, title = "Should Not Overwrite")

        val track = database.trackQueries.selectById(1).executeAsOne()
        assertEquals("Original", track.title)
        assertTrue(track.is_favourite)
    }

    @Test
    fun `albums group by album id`() {
        insertTrack(id = 1, title = "A", albumId = 10, album = "Nullwave EP", artist = "Static Pilgrim")
        insertTrack(id = 2, title = "B", albumId = 10, album = "Nullwave EP", artist = "Static Pilgrim")
        insertTrack(id = 3, title = "C", albumId = 20, album = "Dry Season", artist = "Ash Vendor")

        val albums = database.trackQueries.albums().executeAsList()

        assertEquals(2, albums.size)
        val nullwave = albums.single { it.album_id == 10L }
        assertEquals("Nullwave EP", nullwave.album_name)
        assertEquals(2L, nullwave.track_count)
        assertEquals(1L, nullwave.artist_count)
    }

    @Test
    fun `two albums sharing a title stay separate, and each opens its own tracks`() {
        // A self-titled release, or everyone's "Greatest Hits". MediaStore gives them different album
        // ids, so grouping on the id keeps them apart.
        insertTrack(id = 1, title = "A", albumId = 10, album = "Untitled", artist = "Static Pilgrim")
        insertTrack(id = 2, title = "B", albumId = 20, album = "Untitled", artist = "Ash Vendor")

        assertEquals(2, database.trackQueries.albums().executeAsList().size)

        // The half this test used to be missing. Asserting only the grid passed while the detail query
        // filtered on album_name and merged both albums' tracks - a green test giving false comfort.
        assertEquals(listOf("A"), database.trackQueries.selectByAlbum(10).executeAsList().map { it.title })
        assertEquals(listOf("B"), database.trackQueries.selectByAlbum(20).executeAsList().map { it.title })
    }

    @Test
    fun `a compilation reports more than one artist`() {
        insertTrack(id = 1, title = "A", albumId = 10, album = "Mixtape", artist = "Static Pilgrim")
        insertTrack(id = 2, title = "B", albumId = 10, album = "Mixtape", artist = "Ash Vendor")

        val album = database.trackQueries.albums().executeAsList().single()

        // The repository turns this into "Various Artists". Counting here rather than picking a bare
        // artist_name is what stops SQL handing back whichever row it happened to choose.
        assertEquals(2L, album.artist_count)
        assertEquals(2L, album.track_count)
    }

    @Test
    fun `untagged tracks are excluded from albums but stay in songs`() {
        insertTrack(id = 1, title = "On An Album", albumId = 10)
        insertTrack(id = 2, title = "Loose Track", albumId = null)

        assertEquals(listOf(10L), database.trackQueries.albums().executeAsList().map { it.album_id })
        assertEquals(2, database.trackQueries.selectAllByTitle().executeAsList().size)
    }

    @Test
    fun `album detail orders by disc then track`() {
        insertTrack(id = 1, title = "Disc2 Track1", albumId = 10, disc = 2, track = 1)
        insertTrack(id = 2, title = "Disc1 Track2", albumId = 10, disc = 1, track = 2)
        insertTrack(id = 3, title = "Disc1 Track1", albumId = 10, disc = 1, track = 1)

        val titles = database.trackQueries.selectByAlbum(10).executeAsList().map { it.title }

        // A two-disc release must not interleave.
        assertEquals(listOf("Disc1 Track1", "Disc1 Track2", "Disc2 Track1"), titles)
    }

    @Test
    fun `recently played excludes never-played tracks`() {
        insertTrack(id = 1, title = "Played")
        insertTrack(id = 2, title = "Never Played")
        database.trackQueries.recordPlay(last_played_at = 1_700_000_000_000, id = 1)

        val recent = database.trackQueries.selectRecentlyPlayed(limit = 10).executeAsList()

        assertEquals(listOf("Played"), recent.map { it.title })
    }

    @Test
    fun `deleting a playlist cascades to its entries`() {
        insertTrack(id = 1, title = "A")
        val playlistId = database.transactionWithResult {
            database.playlistQueries.insertPlaylist(name = "Night Drive", created_at = 0, updated_at = 0)
            database.playlistQueries.lastInsertedPlaylistId().executeAsOne()
        }
        database.playlistQueries.appendTrack(playlistId, 1, playlistId)

        assertEquals(1, database.playlistQueries.playlistsContainingTrack(1).executeAsList().size)

        database.playlistQueries.deletePlaylist(playlistId)

        // Fails without `PRAGMA foreign_keys = ON`, which is exactly why it is worth asserting.
        assertTrue(database.playlistQueries.playlistsContainingTrack(1).executeAsList().isEmpty())
    }

    @Test
    fun `appending tracks assigns contiguous positions from zero`() {
        insertTrack(id = 1, title = "First")
        insertTrack(id = 2, title = "Second")
        val id = database.transactionWithResult {
            database.playlistQueries.insertPlaylist(name = "Queue", created_at = 0, updated_at = 0)
            database.playlistQueries.lastInsertedPlaylistId().executeAsOne()
        }

        database.playlistQueries.appendTrack(id, 1, id)
        database.playlistQueries.appendTrack(id, 2, id)

        // Order comes off the (playlist_id, position) primary key, not insertion luck.
        val titles = database.playlistQueries.selectPlaylistTracks(id).executeAsList().map { it.title }
        assertEquals(listOf("First", "Second"), titles)
    }

    @Test
    fun `playlist listing reports count and total duration`() {
        insertTrack(id = 1, title = "A", durationMs = 60_000)
        insertTrack(id = 2, title = "B", durationMs = 90_000)
        val id = database.transactionWithResult {
            database.playlistQueries.insertPlaylist(name = "Short", created_at = 0, updated_at = 0)
            database.playlistQueries.lastInsertedPlaylistId().executeAsOne()
        }
        database.playlistQueries.appendTrack(id, 1, id)
        database.playlistQueries.appendTrack(id, 2, id)

        val row = database.playlistQueries.selectAllPlaylists().executeAsOne()
        assertEquals(2L, row.track_count)
        assertEquals(150_000L, row.duration_ms)
    }

    @Test
    fun `an empty playlist reports zero tracks and null duration`() {
        database.playlistQueries.insertPlaylist(name = "Empty", created_at = 0, updated_at = 0)

        val row = database.playlistQueries.selectAllPlaylists().executeAsOne()
        // SUM over no rows is NULL, not 0 - which is why the generated column is nullable. Worth pinning
        // so nobody "simplifies" it to non-null later.
        assertEquals(0L, row.track_count)
        assertEquals(null, row.duration_ms)
    }

    @Test
    fun `search matches title, artist and album`() {
        insertTrack(id = 1, title = "Deadzone Chorus", artist = "Static Pilgrim", album = "Nullwave EP")
        insertTrack(id = 2, title = "Unrelated", artist = "Other", album = "Other")

        fun hits(q: String) = database.trackQueries.search(q, q, q).executeAsList().map { it.id }

        assertEquals(listOf(1L), hits("deadzone"))
        assertEquals(listOf(1L), hits("pilgrim"))
        assertEquals(listOf(1L), hits("nullwave"))
        assertTrue(hits("nothingmatches").isEmpty())
    }

    @Test
    fun `favourites round-trip through the boolean column`() {
        insertTrack(id = 1, title = "A")

        assertFalse(database.trackQueries.selectById(1).executeAsOne().is_favourite)
        database.trackQueries.setFavourite(is_favourite = true, id = 1)
        assertTrue(database.trackQueries.selectById(1).executeAsOne().is_favourite)
        assertEquals(1, database.trackQueries.selectFavourites().executeAsList().size)
    }

    @Test
    fun `deleteByIds removes only the listed rows`() {
        insertTrack(id = 1, title = "Gone")
        insertTrack(id = 2, title = "Gone too")
        insertTrack(id = 3, title = "Kept")

        database.trackQueries.deleteByIds(listOf(1, 2))

        assertEquals(listOf("Kept"), database.trackQueries.selectAllByTitle().executeAsList().map { it.title })
    }

    private fun insertTrack(
        id: Long,
        title: String,
        artist: String = "Static Pilgrim",
        album: String = "Nullwave EP",
        albumId: Long? = 1,
        durationMs: Long = 180_000,
        disc: Long? = 1,
        track: Long? = 1,
    ) = database.trackQueries.insertIfNew(
        id = id,
        title = title,
        artist_name = artist,
        album_name = album,
        album_id = albumId,
        artist_id = 1,
        duration_ms = durationMs,
        track_no = track,
        disc_no = disc,
        year = 2077,
        mime_type = "audio/flac",
        size_bytes = 10_000_000,
        relative_path = "Music/$artist/",
        display_name = "$title.flac",
        date_added = 1_700_000_000,
        date_modified = 1_700_000_000,
        bitrate = 1_000_000,
    )
}
