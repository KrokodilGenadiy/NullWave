package com.zaus.nullwave.feature.library.api.model.artist

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.model.album.AlbumId
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import java.util.Collections

/**
 * The artist summary, related albums and complete artist track list from one catalog read.
 * Albums describe their full catalog membership, including tracks by other artists on compilations.
 * Tracks belong to this artist and include files without an album identity.
 * Both lists retain the repository's display order.
 */
@ConsistentCopyVisibility
data class LibraryArtistDetails private constructor(
    val artist: LibraryArtist,
    val albums: List<LibraryAlbum>,
    val tracks: List<MediaTrack>,
) {
    companion object {
        fun create(
            artist: LibraryArtist,
            albums: List<LibraryAlbum>,
            tracks: List<MediaTrack>,
        ): LibraryArtistDetails {
            val albumSnapshot = ArrayList(albums)
            val trackSnapshot = ArrayList(tracks)
            require(albumSnapshot.size == artist.albumCount) { "Album list must match the artist's album count." }
            require(trackSnapshot.size == artist.trackCount) { "Track list must match the artist's track count." }
            val albumIds = HashSet<AlbumId>()
            require(albumSnapshot.all { albumIds.add(it.id) }) { "An artist must contain each album ID only once." }
            val trackIds = HashSet<TrackId>()
            require(trackSnapshot.all { trackIds.add(it.id) }) { "An artist must contain each track ID only once." }
            return LibraryArtistDetails(
                artist = artist,
                albums = Collections.unmodifiableList(albumSnapshot),
                tracks = Collections.unmodifiableList(trackSnapshot),
            )
        }
    }
}
