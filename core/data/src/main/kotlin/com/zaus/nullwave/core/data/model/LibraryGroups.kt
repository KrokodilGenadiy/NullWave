package com.zaus.nullwave.core.data.model

/**
 * The three grouped views of the library: Albums, Artists, Folders.
 *
 * Together in one file because they are one idea - each is a `GROUP BY` projection over `track`, none is
 * stored, and none has behaviour. See the note at the top of `Track.sq` for why there are no album or
 * artist tables: they would hold nothing MediaStore has not already said, and would need resyncing on
 * every rescan.
 */

/**
 * A tile in the Albums grid.
 *
 * Identified by [albumId] - MediaStore's own album id - and **not** by name and artist. That is what
 * keeps this grid and the album detail screen agreeing about which tracks belong together, and it is
 * why `AlbumDetailKey` carries an id rather than two strings. Grouping on the name instead merged two
 * artists' same-titled releases into one track list behind two separate tiles.
 *
 * Tracks with no album tag are not here at all. They have no album id, so they are not albums - they are
 * loose tracks, and they still appear in the Songs tab.
 */
data class Album(
    /** MediaStore's album id. Non-null: untagged tracks are excluded rather than grouped under null. */
    val albumId: Long,
    val name: String,
    /**
     * The album's artist, or `"Various Artists"` when its tracks disagree.
     *
     * Derived rather than read from a row: a compilation's tracks each carry their own artist, and SQL
     * would otherwise hand back whichever one it happened to pick. The exact spelling matters -
     * `NullWaveCoverArt`'s `coverArtSource` treats it as a placeholder and falls back to the album title
     * for the monogram.
     */
    val artist: String,
    /** True when the album's tracks carry more than one artist. [artist] is then `"Various Artists"`. */
    val isCompilation: Boolean,
    val year: Int?,
    val trackCount: Int,
)

/** A tile in the Artists grid. Drawn in an octagon rather than a square; see `NullWaveGridCard`. */
data class Artist(
    val name: String,
    val artistId: Long?,
    val albumCount: Int,
    val trackCount: Int,
)

/** A row in the Folders tab. [path] is MediaStore's `RELATIVE_PATH`, e.g. `Music/Static Pilgrim/`. */
data class Folder(
    val path: String,
    val trackCount: Int,
)
