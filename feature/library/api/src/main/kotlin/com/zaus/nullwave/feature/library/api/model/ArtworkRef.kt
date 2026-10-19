package com.zaus.nullwave.feature.library.api.model

/** Opaque catalog artwork identity and revision, resolved by the platform image adapter. */
data class ArtworkRef(val key: String, val revision: String) {
    init {
        require(key.isNotBlank())
        require(revision.isNotBlank())
    }
}
