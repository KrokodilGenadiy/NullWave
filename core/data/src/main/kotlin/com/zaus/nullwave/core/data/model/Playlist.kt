package com.zaus.nullwave.core.data.model

/**
 * A user-created playlist, as the list screen shows it.
 *
 * The only part of the library MediaStore cannot tell us, which is why it is the only thing with tables
 * of its own - `playlist` and `playlist_entry` in `Playlist.sq`.
 *
 * No `tracks` field: a playlist's contents are a separate query, so opening the Playlists tab does not
 * load every track in every playlist to render a list of names.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    /**
     * Null for an empty playlist, not zero.
     *
     * `SUM` over no rows is NULL in SQL, and the distinction is kept rather than flattened: "no tracks"
     * and "tracks totalling nothing" are different states, and only one of them should render a runtime.
     */
    val totalDurationMs: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
