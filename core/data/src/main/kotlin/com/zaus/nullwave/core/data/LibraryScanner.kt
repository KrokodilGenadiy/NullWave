package com.zaus.nullwave.core.data

import kotlinx.coroutines.flow.Flow

/**
 * Brings the database in line with what MediaStore reports.
 *
 * Incremental, not wipe-and-reinsert: the track table carries user state (`is_favourite`, `play_count`,
 * `last_played_at`) and is the target of playlist entries, so rows are inserted, updated and deleted
 * individually. See `Track.sq` for why that is two statements rather than an upsert.
 */
interface LibraryScanner {

    /**
     * Scan once, reporting progress as it goes.
     *
     * A **cold** flow: the scan starts when collected and runs once per collection, and the last emission
     * is always [ScanProgress.Finished]. Concurrent collections serialise rather than racing - see the
     * `Mutex` note in the implementation.
     *
     * Safe to collect when nothing has changed: it diffs, so a no-op scan writes nothing and emits no
     * [ScanProgress.Writing] at all. Work happens off the main thread.
     *
     * The intermediate states exist for the design's scan screen, which the UI shows only once a scan
     * outlasts a threshold - see [ScanProgress].
     */
    fun scan(): Flow<ScanProgress>

    /**
     * Emits when MediaStore's audio collection changes, so a rescan can be triggered without a refresh
     * button the design does not have.
     *
     * Emits nothing on subscribe; only on change. Unregisters its observer when collection stops.
     */
    fun mediaStoreChanges(): Flow<Unit>
}

/**
 * What a [LibraryScanner.scan] did.
 *
 * [PermissionDenied] is a distinct outcome rather than an exception or an empty [Completed] because the
 * difference matters a great deal: without the audio permission `ContentResolver` reports **no files**,
 * and a scanner that believed it would delete the entire library - every favourite, every playlist
 * entry. The scanner checks the permission itself and refuses rather than trusting the query.
 */
sealed interface ScanOutcome {

    /**
     * @param inserted rows new to the database
     * @param updated rows whose file changed since last seen
     * @param removed rows whose file MediaStore no longer reports
     * @param total tracks in the library afterwards
     * @param elapsedMillis wall clock, for the log line the first scan is judged by
     */
    data class Completed(
        val inserted: Int,
        val updated: Int,
        val removed: Int,
        val total: Int,
        val elapsedMillis: Long,
    ) : ScanOutcome

    /** The audio permission is not held. Nothing was read and nothing was written. */
    data object PermissionDenied : ScanOutcome
}
