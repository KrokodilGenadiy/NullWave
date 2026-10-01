package com.zaus.nullwave.core.data.internal

/**
 * One audio file as MediaStore described it, before it reaches the database.
 *
 * Deliberately not the domain `Track`: this is scan input, carries no user state, and its field names
 * track what the cursor gave us.
 */
internal data class ScannedTrack(
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
    val relativePath: String,
    val displayName: String,
    val dateAddedEpochSeconds: Long,
    val dateModifiedEpochSeconds: Long,
    val bitrate: Long,
)

/**
 * Works out what a scan has to write, with no database and no `ContentResolver` in sight.
 *
 * Pure on purpose. The interesting parts of a scan are the diff and the two decodings below, and all
 * three are awkward to exercise through Android - so they live here and are unit tested directly.
 */
internal object ScanDiff {

    /**
     * @param existing id to `date_modified`, as the database currently holds them
     * @param scanned every row MediaStore reported
     */
    fun of(existing: Map<Long, Long>, scanned: List<ScannedTrack>): Plan {
        val insert = mutableListOf<ScannedTrack>()
        val update = mutableListOf<ScannedTrack>()

        for (track in scanned) {
            val knownModified = existing[track.id]
            when {
                knownModified == null -> insert += track
                // Any difference, not just "newer". A file restored from a backup can go backwards, and
                // it is still a file we hold stale metadata for.
                knownModified != track.dateModifiedEpochSeconds -> update += track
                // Unchanged: deliberately nothing. This is what makes a repeat scan write zero rows and
                // therefore emit nothing to the repository's Flows.
            }
        }

        val scannedIds = scanned.mapTo(HashSet(scanned.size)) { it.id }
        val delete = existing.keys.filterNot { it in scannedIds }

        return Plan(insert = insert, update = update, delete = delete)
    }

    data class Plan(
        val insert: List<ScannedTrack>,
        val update: List<ScannedTrack>,
        val delete: List<Long>,
    ) {
        val isEmpty: Boolean get() = insert.isEmpty() && update.isEmpty() && delete.isEmpty()
    }
}

/**
 * Bits per second from file size and duration.
 *
 * `MediaStore.Audio.Media.BITRATE` exists only from API 31 and `minSdk` is 29, so deriving it removes
 * the version branch entirely - and the result is accurate enough to band a [com.zaus.nullwave.core.data.model.QualityTier],
 * which is all it is used for.
 *
 * Returns 0 for a zero or missing duration rather than dividing by it. A zero-length entry is usually a
 * truncated download, and 0 bitrate lands it in the lowest tier, which is honest.
 */
internal fun deriveBitrate(sizeBytes: Long, durationMs: Long): Long =
    if (durationMs <= 0L) 0L else sizeBytes * BitsPerByte * MillisPerSecond / durationMs

private const val BitsPerByte = 8L
private const val MillisPerSecond = 1000L

/**
 * Splits MediaStore's `TRACK` column into disc and track numbers.
 *
 * MediaStore encodes **both** in one integer: for multi-disc sets the value is `1xxx` for disc one,
 * `2xxx` for disc two and so on, so `1003` means disc 1, track 3. Using the raw value as a track number
 * sorts a two-disc album as 1001, 1002, 2001 - technically in order, but any display of it is wrong.
 *
 * This is why `DISC_NUMBER` is not read instead: that column arrived in API 31, and the encoding works
 * on every level.
 *
 * A plain value below 1000 is a track number on an implicit single disc, reported as disc `null` rather
 * than 1 - "no disc information" and "disc one of one" are different things, and only the former should
 * leave a UI free to omit it.
 */
internal fun decodeDiscAndTrack(raw: Int?): Pair<Int?, Int?> = when {
    raw == null || raw <= 0 -> null to null
    raw < TrackNumbersPerDisc -> null to raw
    else -> raw / TrackNumbersPerDisc to (raw % TrackNumbersPerDisc).takeIf { it > 0 }
}

private const val TrackNumbersPerDisc = 1000
