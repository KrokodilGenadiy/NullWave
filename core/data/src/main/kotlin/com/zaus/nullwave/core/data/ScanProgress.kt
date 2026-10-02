package com.zaus.nullwave.core.data

/**
 * What a scan is doing, emitted as it goes.
 *
 * ## Why this exists, and why artboard 42 is threshold-gated
 *
 * The design's scan screen shows `~40 s LEFT` and `READ TAGS · FLAC 44.1/16`, which presupposes a scanner
 * that opens every file to read its tags. This one does not: MediaStore already holds the metadata, so a
 * scan is one cursor read plus one transaction - hundreds of milliseconds for a thousand tracks. Sample
 * rate and bit depth are a deliberately *lazy* per-track probe for the same reason.
 *
 * So the progress is real but usually brief, and the UI shows artboard 42 only once a scan outlasts a
 * threshold. Emitting it regardless keeps the decision in the UI, where it belongs, rather than having the
 * scanner guess whether anyone is watching.
 *
 * ## Two phases, not one bar
 *
 * The design draws a single bar, but the work has two genuinely different phases and a first scan spends
 * most of its time in the second. Reporting file progress that completes and then appears to hang during
 * the writes would be worse than reporting nothing, so [Indexing] and [Writing] are distinct and the UI
 * can label them differently behind the same bar.
 */
sealed interface ScanProgress {

    /** Querying MediaStore. The file count is not known yet, so there is nothing to be a fraction of. */
    data object Reading : ScanProgress

    /**
     * Walking the cursor. [processed] of [total] files read.
     *
     * @param currentFile MediaStore's `DISPLAY_NAME`, for the design's three-line log panel.
     */
    data class Indexing(
        val processed: Int,
        val total: Int,
        val currentFile: String,
    ) : ScanProgress {
        /** 0..1, and 0 rather than NaN for an empty library. */
        val fraction: Float get() = if (total <= 0) 0f else processed.toFloat() / total
    }

    /**
     * Applying the diff inside the transaction - the slow phase of a first scan.
     *
     * Counts are known before the writes begin, which is what lets the design's
     * `+ 3 ALBUMS · + 1 ARTIST` line be rendered during the phase rather than after it.
     */
    data class Writing(
        val inserting: Int,
        val updating: Int,
        val removing: Int,
        val newAlbums: Int,
        val newArtists: Int,
    ) : ScanProgress

    /** Terminal. Always the last emission, including when the permission was refused. */
    data class Finished(val outcome: ScanOutcome) : ScanProgress
}
