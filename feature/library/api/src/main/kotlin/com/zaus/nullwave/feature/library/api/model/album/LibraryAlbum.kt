package com.zaus.nullwave.feature.library.api.model.album

import com.zaus.nullwave.feature.library.api.model.ArtworkRef

/**
 * One album represented by at least one eligible catalog track.
 * [title] is null for an unknown tag; identity still belongs to the source, never to the title.
 * [trackCount] counts this album's eligible tracks, not a provider's unfiltered global total.
 * [artwork] is optional and does not guarantee that a thumbnail exists.
 */
data class LibraryAlbum(
    val id: AlbumId,
    val title: String?,
    val artistCredit: AlbumArtistCredit,
    val trackCount: Int,
    val artwork: ArtworkRef? = null,
) {
    init {
        require(title == null || title.isNotBlank()) { "Unknown album title must be null." }
        require(trackCount > 0) { "A catalog album must contain at least one eligible track." }
    }
}
