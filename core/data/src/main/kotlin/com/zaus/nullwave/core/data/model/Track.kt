package com.zaus.nullwave.core.data.model

/**
 * One audio file. What the library's repositories return, and what features consume.
 *
 * This exists instead of handing out SQLDelight's generated `Track`, for three reasons that are not
 * ceremony: the generated field names are the column names (`artist_name`, `is_favourite`), so every
 * screen would be coupled to the schema and a column rename would be a multi-module change; everything
 * integral comes back as `Long`, including disc numbers and bit depths; and [QualityTier] is real logic
 * that has to live in exactly one place.
 *
 * There is deliberately **no use-case layer** above this. Repositories go straight into the presentation
 * layer, and a use case gets written only when it has real business logic to hold - never as a proxy for
 * a single repository call.
 *
 * [id] is the MediaStore `_ID`, which is also the primary key in the database and what
 * `ContentUris.withAppendedId` needs to load artwork or open a stream.
 *
 * Formatting is **not** this type's job. [durationMs] stays milliseconds rather than becoming "3:47",
 * and the codec name is not derived from [mimeType] here - the design system's components take
 * already-formatted strings (see `NullWaveStatBar`'s KDoc), so formatting belongs at the call site,
 * where the locale and the available width are known.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long?,
    val artistId: Long?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val mimeType: String,
    val sizeBytes: Long,
    /** Bits per second. Derived at scan time, because `MediaStore` only exposes it from API 31. */
    val bitrate: Long,
    /** Null until something has asked for it - see the lazy probe note in `Track.sq`. */
    val sampleRate: Int?,
    /** Null until probed, as [sampleRate]. */
    val bitDepth: Int?,
    val relativePath: String,
    val displayName: String,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long,
    val isFavourite: Boolean,
    val playCount: Long,
    val lastPlayedAtEpochMillis: Long?,
) {
    /** The 3dp leading stripe on a track row. Computed, never stored - see [QualityTier.of]. */
    val qualityTier: QualityTier get() = QualityTier.of(mimeType, bitrate, sampleRate, bitDepth)
}
