package com.zaus.nullwave.feature.library.api.model.album

import com.zaus.nullwave.core.media.model.ArtworkRef

/** Opaque source identity, distinct from a title or [ArtworkRef]. Not stable across reindexing. */
@JvmInline
value class AlbumId(val value: String) {
    init {
        require(value.isNotBlank()) { "Album identity must not be blank." }
    }
}
