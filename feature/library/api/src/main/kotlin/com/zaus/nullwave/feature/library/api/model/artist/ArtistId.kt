package com.zaus.nullwave.feature.library.api.model.artist

import kotlinx.serialization.Serializable

/** Opaque source identity. Equal display names do not imply equal artists or cross-volume identity. */
@Serializable
@JvmInline
value class ArtistId(val value: String) {
    init {
        require(value.isNotBlank()) { "Artist identity must not be blank." }
    }
}
