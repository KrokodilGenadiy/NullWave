package com.zaus.nullwave.core.data

import com.zaus.nullwave.core.data.model.Album
import com.zaus.nullwave.core.data.model.Artist
import com.zaus.nullwave.core.data.model.Folder
import com.zaus.nullwave.core.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * How the Songs tab is ordered.
 *
 * A closed set, not a free-form "column plus direction", because the design offers exactly these three
 * sort chips. Two things follow from that. Each case maps to its own named query in `Track.sq`, so each
 * is backed by an index - a parameterised `ORDER BY` would have to be a `CASE` expression, which SQLite
 * cannot satisfy from an index, turning every sort into a full scan and discarding the indices. And the
 * enum makes the valid sorts discoverable while ruling out "by mime type, descending", which no screen
 * wants.
 *
 * Adding one later costs a query and a case. If direction toggling ever appears in the design, either
 * double the cases or reverse the list in memory - still cheaper than an unindexed sort at this scale.
 */
enum class TrackSort {
    /** A-Z, case-insensitive. The default, and what the fast-scroller's letter buckets assume. */
    Title,

    /** Newest first. */
    RecentlyAdded,

    /** Longest first. */
    Longest,
}

/**
 * Reads and writes for the library.
 *
 * Injected **straight into the presentation layer** - there is no use-case tier, by decision. A use case
 * gets written here only when it has real business logic to hold, never to forward a single call.
 *
 * Every read returns a [Flow] that re-emits when the underlying tables change, which is what makes the
 * Songs list update itself after a rescan with no refresh plumbing. Writes are `suspend` and do their
 * work off the main thread.
 *
 * Returns domain types, never SQLDelight's generated ones, so a column rename stays inside `:core:data`.
 */
interface TrackRepository {

    /** The Songs tab. */
    fun tracks(sort: TrackSort = TrackSort.Title): Flow<List<Track>>

    /** A single track, re-emitting when it changes. Null once it is deleted. */
    fun track(id: Long): Flow<Track?>

    /**
     * Album detail, in playing order: disc, then track number.
     *
     * Takes MediaStore's album id, the same key [albums] groups by and `AlbumDetailKey` carries - so the
     * tile and the screen it opens cannot disagree about which tracks belong to the album.
     */
    fun tracksInAlbum(albumId: Long): Flow<List<Track>>

    /** Artist detail, grouped by album then playing order. */
    fun tracksByArtist(artistName: String): Flow<List<Track>>

    /** Folders tab detail. [path] is MediaStore's `RELATIVE_PATH`. */
    fun tracksInFolder(path: String): Flow<List<Track>>

    fun albums(): Flow<List<Album>>

    fun artists(): Flow<List<Artist>>

    fun folders(): Flow<List<Folder>>

    /** Matches title, artist and album. A table scan - see the note on `search` in `Track.sq`. */
    fun search(query: String): Flow<List<Track>>

    fun favourites(): Flow<List<Track>>

    /** Most recently played first. Never-played tracks are excluded. */
    fun recentlyPlayed(limit: Int = DefaultRecentLimit): Flow<List<Track>>

    /** Total track count, for the drawer's `LOCAL LIBRARY - 1,284 TRACKS` sub-line. */
    fun trackCount(): Flow<Long>

    suspend fun setFavourite(id: Long, isFavourite: Boolean)

    /** Increments the play count in SQL, so two quick plays cannot race each other. */
    suspend fun recordPlay(id: Long, atEpochMillis: Long)

    /**
     * Caches what the lazy `MediaExtractor` probe found, so the next read of this track reports its real
     * resolution - and its [com.zaus.nullwave.core.data.model.QualityTier] can rise from `Lossless` to
     * `HiRes`.
     */
    suspend fun setProbedMetadata(id: Long, sampleRate: Int?, bitDepth: Int?)

    companion object {
        const val DefaultRecentLimit = 50
    }
}
