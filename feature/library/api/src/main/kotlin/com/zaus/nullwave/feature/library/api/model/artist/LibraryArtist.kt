package com.zaus.nullwave.feature.library.api.model.artist

/**
 * One artist represented by eligible catalog tracks; an unknown name is null, not UI text.
 * [albumCount] counts distinct valid album identities among those tracks, including compilations.
 * Tracks without an album identity contribute to [trackCount] but not [albumCount].
 * An album thumbnail is not an artist portrait; image selection is outside this summary contract.
 */
data class LibraryArtist(
    val id: ArtistId,
    val name: String?,
    val trackCount: Int,
    val albumCount: Int,
) {
    init {
        require(name == null || name.isNotBlank()) { "Unknown artist name must be null." }
        require(trackCount > 0) { "A catalog artist must have at least one eligible track." }
        require(albumCount in 0..trackCount) { "Album count must be between zero and track count." }
    }
}
