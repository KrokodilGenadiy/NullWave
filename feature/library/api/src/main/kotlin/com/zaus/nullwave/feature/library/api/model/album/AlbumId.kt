package com.zaus.nullwave.feature.library.api.model.album

import com.zaus.nullwave.core.media.model.ArtworkRef
import kotlinx.serialization.Serializable

/** Opaque source identity, distinct from a title or [ArtworkRef]. Not stable across reindexing. */
@Serializable
@JvmInline
value class AlbumId(val value: String) {
    init {
        require(value.isNotBlank()) { "Album identity must not be blank." }
    }
}
