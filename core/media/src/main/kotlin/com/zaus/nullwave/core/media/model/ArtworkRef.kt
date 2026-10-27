package com.zaus.nullwave.core.media.model

/** Opaque artwork identity and revision, resolved by the owning source's platform image adapter. */
data class ArtworkRef(val key: String, val revision: String) {
    init {
        require(key.isNotBlank()) { "Artwork identity must not be blank." }
        require(revision.isNotBlank()) { "Artwork revision must not be blank." }
    }
}
