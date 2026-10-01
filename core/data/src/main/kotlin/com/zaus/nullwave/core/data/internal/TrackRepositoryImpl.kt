package com.zaus.nullwave.core.data.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.zaus.nullwave.core.data.TrackRepository
import com.zaus.nullwave.core.data.TrackSort
import com.zaus.nullwave.core.data.model.Album
import com.zaus.nullwave.core.data.model.Artist
import com.zaus.nullwave.core.data.model.Folder
import com.zaus.nullwave.core.data.model.Track
import com.zaus.nullwave.data.database.NullWaveDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.zaus.nullwave.data.database.Albums as AlbumRow
import com.zaus.nullwave.data.database.Artists as ArtistRow
import com.zaus.nullwave.data.database.Folders as FolderRow
import com.zaus.nullwave.data.database.Track as TrackRow

/**
 * The only place the generated database types are touched.
 *
 * The import aliases (`Track as TrackRow`) keep the two `Track`s apart: `TrackRow` is the schema,
 * [Track] is the domain.
 *
 * Reads go through `.asFlow().mapToList(io)`, which re-emits on any write to the queried tables. That is
 * what makes a rescan refresh the Songs list with no plumbing in the UI. Writes use `withContext(io)`
 * because SQLDelight's generated writes are blocking.
 *
 * ## Why this is not `@ContributesBinding`
 *
 * It was, and `:app` failed to build with `[Metro/MissingBinding] No binding found for TrackRepository`.
 * `@ContributesBinding` makes the *other* module's generated graph name this class, which an `internal`
 * class cannot be. The options were to make it public or to keep it internal and contribute from a
 * binding container in this module - see `RepositoryBindings`, whose public signature mentions only
 * public types and names this class only in its body. Encapsulation won.
 */
internal class TrackRepositoryImpl(
    private val database: NullWaveDatabase,
) : TrackRepository {

    private val queries get() = database.trackQueries

    // Not injected: a dispatcher is not a dependency features should be able to vary, and nothing in
    // this class is worth a fake dispatcher in a test - the JVM tests drive the queries directly.
    private val io: CoroutineDispatcher = Dispatchers.IO

    override fun tracks(sort: TrackSort): Flow<List<Track>> = when (sort) {
        // One query per sort, each index-backed. See TrackSort's KDoc for why this is not a
        // parameterised ORDER BY.
        TrackSort.Title -> queries.selectAllByTitle()
        TrackSort.RecentlyAdded -> queries.selectAllByDateAdded()
        TrackSort.Longest -> queries.selectAllByDuration()
    }.asFlow().mapToList(io).mapEach()

    override fun track(id: Long): Flow<Track?> =
        queries.selectById(id).asFlow().mapToOneOrNull(io).mapItem()

    override fun tracksInAlbum(albumId: Long): Flow<List<Track>> =
        queries.selectByAlbum(albumId).asFlow().mapToList(io).mapEach()

    override fun tracksByArtist(artistName: String): Flow<List<Track>> =
        queries.selectByArtist(artistName).asFlow().mapToList(io).mapEach()

    override fun tracksInFolder(path: String): Flow<List<Track>> =
        queries.selectByFolder(path).asFlow().mapToList(io).mapEach()

    override fun albums(): Flow<List<Album>> =
        queries.albums().asFlow().mapToList(io).mapEachWith(AlbumRow::toDomain)

    override fun artists(): Flow<List<Artist>> =
        queries.artists().asFlow().mapToList(io).mapEachWith(ArtistRow::toDomain)

    override fun folders(): Flow<List<Folder>> =
        queries.folders().asFlow().mapToList(io).mapEachWith(FolderRow::toDomain)

    // The same term three times: the query matches it against title, artist and album. Bound separately
    // rather than reused, because SQLDelight gives each `?` its own parameter.
    override fun search(query: String): Flow<List<Track>> =
        queries.search(query, query, query).asFlow().mapToList(io).mapEach()

    override fun favourites(): Flow<List<Track>> =
        queries.selectFavourites().asFlow().mapToList(io).mapEach()

    override fun recentlyPlayed(limit: Int): Flow<List<Track>> =
        queries.selectRecentlyPlayed(limit.toLong()).asFlow().mapToList(io).mapEach()

    override fun trackCount(): Flow<Long> =
        queries.countAll().asFlow().mapToOne(io)

    // Block bodies, not `= withContext(...)`. SQLDelight's generated mutators return
    // `QueryResult<Long>` (the row count), so an expression body would widen these overrides' return
    // type and fail to match the interface. The count is of no use to a caller here.
    override suspend fun setFavourite(id: Long, isFavourite: Boolean) {
        withContext(io) { queries.setFavourite(is_favourite = isFavourite, id = id) }
    }

    override suspend fun recordPlay(id: Long, atEpochMillis: Long) {
        withContext(io) { queries.recordPlay(last_played_at = atEpochMillis, id = id) }
    }

    override suspend fun setProbedMetadata(id: Long, sampleRate: Int?, bitDepth: Int?) {
        withContext(io) {
            queries.setProbedMetadata(
                sample_rate = sampleRate?.toLong(),
                bit_depth = bitDepth?.toLong(),
                id = id,
            )
        }
    }
}

// ── Mapping ────────────────────────────────────────────────────────────────────────────────────────
// Hand-written on purpose. These twenty-odd lines are the entire price of features not being coupled to
// column names, and they are the one place a schema change has to be reflected.
//
// The `Int?` narrowings are deliberate: SQLite stores everything integral as INTEGER, so the generated
// type is all `Long`, but a disc number or a bit depth is not a 64-bit quantity and saying so in the
// domain type stops it spreading.

private fun Flow<List<TrackRow>>.mapEach(): Flow<List<Track>> = mapEachWith(TrackRow::toDomain)

private fun Flow<TrackRow?>.mapItem(): Flow<Track?> = map { row -> row?.toDomain() }

private fun <T, R> Flow<List<T>>.mapEachWith(transform: (T) -> R): Flow<List<R>> =
    map { rows -> rows.map(transform) }

internal fun TrackRow.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist_name,
    album = album_name,
    albumId = album_id,
    artistId = artist_id,
    durationMs = duration_ms,
    trackNumber = track_no?.toInt(),
    discNumber = disc_no?.toInt(),
    year = year?.toInt(),
    mimeType = mime_type,
    sizeBytes = size_bytes,
    bitrate = bitrate,
    sampleRate = sample_rate?.toInt(),
    bitDepth = bit_depth?.toInt(),
    relativePath = relative_path,
    displayName = display_name,
    dateAddedEpochSeconds = date_added,
    dateModifiedEpochSeconds = date_modified,
    isFavourite = is_favourite,
    playCount = play_count,
    lastPlayedAtEpochMillis = last_played_at,
)

internal fun AlbumRow.toDomain(): Album {
    val isCompilation = artist_count > 1
    return Album(
        albumId = album_id,
        name = album_name,
        // The one place "Various Artists" is decided. SQL counted the distinct artists; turning that
        // into a label here means no screen has to, and every screen agrees on the spelling that
        // `coverArtSource` keys off.
        artist = if (isCompilation) VariousArtists else artist_name,
        isCompilation = isCompilation,
        year = year?.toInt(),
        trackCount = track_count.toInt(),
    )
}

/** Matches the placeholder `NullWaveCoverArt.coverArtSource` already recognises. */
private const val VariousArtists = "Various Artists"

internal fun ArtistRow.toDomain(): Artist = Artist(
    name = artist_name,
    artistId = artist_id,
    albumCount = album_count.toInt(),
    trackCount = track_count.toInt(),
)

internal fun FolderRow.toDomain(): Folder = Folder(
    path = relative_path,
    trackCount = track_count.toInt(),
)
