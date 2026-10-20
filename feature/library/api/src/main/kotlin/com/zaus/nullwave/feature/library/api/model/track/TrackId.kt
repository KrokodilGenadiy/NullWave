package com.zaus.nullwave.feature.library.api.model.track

/** Opaque catalog identity. Consumers must not parse it or assume it survives reindexing. */
@JvmInline
value class TrackId(val value: String) {
    init {
        require(value.isNotBlank()) { "Track identity must not be blank." }
    }
}
