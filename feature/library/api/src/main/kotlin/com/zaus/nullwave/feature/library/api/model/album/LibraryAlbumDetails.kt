package com.zaus.nullwave.feature.library.api.model.album

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import java.util.Collections

/** The album summary and its complete, ordered track list from one catalog read. */
@ConsistentCopyVisibility
data class LibraryAlbumDetails private constructor(
    val album: LibraryAlbum,
    val tracks: List<MediaTrack>,
) {
    companion object {
        fun create(album: LibraryAlbum, tracks: List<MediaTrack>): LibraryAlbumDetails {
            val snapshot = ArrayList(tracks)
            require(snapshot.size == album.trackCount) { "Track list must match the album's track count." }
            val ids = HashSet<TrackId>()
            require(snapshot.all { ids.add(it.id) }) { "An album must contain each track ID only once." }
            return LibraryAlbumDetails(album, Collections.unmodifiableList(snapshot))
        }
    }
}
