package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.artist.ArtistId
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun List<MediaStoreAudioRow>.toArtistDetails(
    artistId: ArtistId,
    localeTag: String,
): LibraryArtistDetails? {
    val context = currentCoroutineContext()
    val members = mutableListOf<MediaStoreAudioRow>()
    val identities = HashSet<String>()
    for (row in this) {
        context.ensureActive()
        val volume = row.validateAudioIdentityAndGetVolume()
        if (!identities.add("$volume:${row.id}")) throw IOException("MediaStore returned duplicate audio identity")
        if (row.catalogKey(MediaStoreAudioRow::artistKeyOnVolume) == artistId.value) members.add(row)
    }
    if (members.isEmpty()) return null

    val albumIds = members.mapNotNullTo(HashSet()) {
        context.ensureActive()
        it.catalogKey(MediaStoreAudioRow::albumKeyOnVolume)
    }
    // Other artists contribute to compilation summaries and physical album artwork revisions.
    val artwork = artworkReferences()
    val albums = catalogGroups(MediaStoreAudioRow::albumKeyOnVolume).mapNotNull { (id, rows) ->
        context.ensureActive()
        if (id in albumIds) rows.toAlbum(id, artwork) else null
    }.sortedCatalog(localeTag, SortDirection.Ascending, LibraryAlbum::title) { it.id.value }
    val tracks = members.map {
        context.ensureActive()
        it.toTrack(artwork[it.artworkKey()])
    }.sortedCatalog(localeTag, SortDirection.Ascending, MediaTrack::title) { it.id.value }
    return LibraryArtistDetails.create(members.toArtist(artistId.value), albums, tracks)
}
