package com.zaus.nullwave.core.data.internal

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import com.zaus.nullwave.core.data.AudioPermission
import com.zaus.nullwave.core.data.LibraryScanner
import com.zaus.nullwave.core.data.ScanOutcome
import com.zaus.nullwave.core.data.ScanProgress
import com.zaus.nullwave.data.database.NullWaveDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads MediaStore and reconciles it with the database.
 *
 * ## The four things that decide whether this works on a real library
 *
 * 1. **One transaction.** Inserting 1,284 rows outside a transaction is 1,284 commits and takes tens of
 *    seconds. The whole plan is applied inside a single `database.transaction { }`.
 * 2. **`IS_MUSIC != 0`** in the selection, or ringtones, notification sounds and podcast files are
 *    indexed as tracks.
 * 3. **Incremental.** A wipe-and-reinsert throws away `is_favourite`, `play_count` and every playlist
 *    entry pointing at those ids. [ScanDiff] decides what to insert, update and delete.
 * 4. **Permission checked here, not assumed.** Without it `ContentResolver` reports no files at all, and
 *    an incremental scan that trusted the query would read that as "the user deleted everything" and
 *    delete the library. See [ScanOutcome.PermissionDenied].
 *
 * The cursor is drained into memory *before* the transaction opens rather than written row-by-row while
 * iterating. A library's worth of these is a few hundred kilobytes, and it keeps a write lock from being
 * held across IPC with the MediaStore provider.
 */
internal class MediaStoreLibraryScanner(
    private val context: Context,
    private val database: NullWaveDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LibraryScanner {

    /**
     * Serialises scans.
     *
     * `@SingleIn` guarantees one scanner *instance*, not one *execution* - and two overlapping scans
     * corrupt the library rather than merely duplicating work. Both read `selectScanState()`, both
     * compute a plan, and the earlier plan's `delete` list was built before the later plan's inserts
     * landed. So scan A deletes the rows scan B just wrote.
     *
     * Not theoretical: a `mediaStoreChanges()` burst arriving during a manual refresh is exactly this.
     */
    private val scanLock = Mutex()

    override fun scan(): Flow<ScanProgress> = flow {
        // The lock wraps the whole collection, so a second collector waits for the first to finish
        // instead of diffing against a half-written table.
        scanLock.withLock {
            if (!hasAudioPermission()) {
                emit(ScanProgress.Finished(ScanOutcome.PermissionDenied))
                return@withLock
            }

            val startedAt = SystemClock.elapsedRealtime()
            emit(ScanProgress.Reading)

            // Null rather than an exception: the permission can be revoked between the check above and
            // the query, and a crash is precisely what ScanOutcome.PermissionDenied exists to avoid.
            val scanned = queryMediaStore { processed, total, currentFile ->
                emit(ScanProgress.Indexing(processed, total, currentFile))
            }
            if (scanned == null) {
                emit(ScanProgress.Finished(ScanOutcome.PermissionDenied))
                return@withLock
            }

            val existing = database.trackQueries.selectScanState()
                .executeAsList()
                .associate { it.id to it.date_modified }

            val plan = ScanDiff.of(existing = existing, scanned = scanned)

            if (!plan.isEmpty) {
                // Counted before the writes rather than after, so the design's "+ 3 ALBUMS · + 1 ARTIST"
                // can be shown *during* the phase. Distinct over the insert list only: an updated track
                // was already on an album we knew about.
                emit(
                    ScanProgress.Writing(
                        inserting = plan.insert.size,
                        updating = plan.update.size,
                        removing = plan.delete.size,
                        newAlbums = plan.insert.mapNotNull { it.albumId }.distinct().size,
                        newArtists = plan.insert.map { it.artist }.distinct().size,
                    )
                )
                database.transaction {
                    plan.insert.forEach(::insertTrack)
                    plan.update.forEach(::updateTrack)
                    // Chunked because SQLite's bound-parameter ceiling is 999 on older Android, and
                    // `IN ?` binds one parameter per id. A first scan after clearing a card can easily
                    // exceed that.
                    plan.delete.chunked(MaxBoundParameters).forEach(database.trackQueries::deleteByIds)
                }
            }

            emit(
                ScanProgress.Finished(
                    ScanOutcome.Completed(
                        inserted = plan.insert.size,
                        updated = plan.update.size,
                        removed = plan.delete.size,
                        total = scanned.size,
                        elapsedMillis = SystemClock.elapsedRealtime() - startedAt,
                    )
                )
            )
        }
    }.flowOn(io)

    override fun mediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        // `notifyForDescendants = true`: changes arrive against individual item URIs, not the
        // collection, so observing the collection alone would never fire.
        context.contentResolver.registerContentObserver(AudioCollection, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
        // A file copy can fire this dozens of times a second. The collector only needs to know that
        // *something* changed, and a scan is far more expensive than a dropped notification.
        .conflate()

    private fun hasAudioPermission(): Boolean =
        context.checkSelfPermission(AudioPermission.name) == PackageManager.PERMISSION_GRANTED

    /**
     * Every row MediaStore reports, or **null if the permission was lost** between the check and here.
     *
     * The null is load-bearing. An empty list and a `SecurityException` mean opposite things: the first
     * says the user deleted their music, the second says we are not allowed to look - and the diff would
     * treat both as "delete everything".
     */
    private suspend fun queryMediaStore(
        onProgress: suspend (processed: Int, total: Int, currentFile: String) -> Unit,
    ): List<ScannedTrack>? {
        val cursor = try {
            context.contentResolver.query(
                AudioCollection,
                Projection,
                // Excludes ringtones, alarms, notifications and podcasts.
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null,
            )
        } catch (e: SecurityException) {
            return null
        } ?: return emptyList()

        return cursor.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeType = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val relativePath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val displayName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModified = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            val total = c.count
            val rows = ArrayList<ScannedTrack>(total)
            while (c.moveToNext()) {
                val durationMs = c.getLong(duration)
                val sizeBytes = c.getLong(size)
                val (disc, trackNo) = decodeDiscAndTrack(
                    c.getInt(track).takeUnless { c.isNull(track) },
                )
                rows += ScannedTrack(
                    id = c.getLong(id),
                    // TITLE is non-null in practice but not contractually; DISPLAY_NAME is the file
                    // name and the only honest fallback.
                    title = c.getString(title)?.ifBlank { null } ?: c.getString(displayName).orEmpty(),
                    artist = c.getString(artist).orUnknown(UnknownArtist),
                    album = c.getString(album).orUnknown(UnknownAlbum),
                    albumId = c.getLong(albumId).takeUnless { c.isNull(albumId) },
                    artistId = c.getLong(artistId).takeUnless { c.isNull(artistId) },
                    durationMs = durationMs,
                    trackNumber = trackNo,
                    discNumber = disc,
                    // Read once. A year of 0 is MediaStore's "no value", not the year zero.
                    year = c.getInt(year).takeUnless { it == 0 || c.isNull(year) },
                    mimeType = c.getString(mimeType).orEmpty(),
                    sizeBytes = sizeBytes,
                    relativePath = c.getString(relativePath).orEmpty(),
                    displayName = c.getString(displayName).orEmpty(),
                    dateAddedEpochSeconds = c.getLong(dateAdded),
                    dateModifiedEpochSeconds = c.getLong(dateModified),
                    bitrate = deriveBitrate(sizeBytes, durationMs),
                )

                // Throttled: a thousand emissions for a scan that takes a few hundred milliseconds
                // would cost more than the work being reported. Every 32nd row, plus the last, is
                // enough to animate a bar smoothly.
                if (rows.size % ProgressEveryNRows == 0 || rows.size == total) {
                    onProgress(rows.size, total, rows.last().displayName)
                }
            }
            rows
        }
    }

    private fun insertTrack(t: ScannedTrack) = database.trackQueries.insertIfNew(
        id = t.id,
        title = t.title,
        artist_name = t.artist,
        album_name = t.album,
        album_id = t.albumId,
        artist_id = t.artistId,
        duration_ms = t.durationMs,
        track_no = t.trackNumber?.toLong(),
        disc_no = t.discNumber?.toLong(),
        year = t.year?.toLong(),
        mime_type = t.mimeType,
        size_bytes = t.sizeBytes,
        relative_path = t.relativePath,
        display_name = t.displayName,
        date_added = t.dateAddedEpochSeconds,
        date_modified = t.dateModifiedEpochSeconds,
        bitrate = t.bitrate,
    )

    /**
     * Note what this does **not** set: `is_favourite`, `play_count`, `last_played_at`, `sample_rate` and
     * `bit_depth`. The first three are the user's, the last two come from the lazy probe. `updateScanned`
     * in `Track.sq` lists its columns explicitly for exactly this reason.
     */
    private fun updateTrack(t: ScannedTrack) = database.trackQueries.updateScanned(
        title = t.title,
        artist_name = t.artist,
        album_name = t.album,
        album_id = t.albumId,
        artist_id = t.artistId,
        duration_ms = t.durationMs,
        track_no = t.trackNumber?.toLong(),
        disc_no = t.discNumber?.toLong(),
        year = t.year?.toLong(),
        mime_type = t.mimeType,
        size_bytes = t.sizeBytes,
        relative_path = t.relativePath,
        display_name = t.displayName,
        date_modified = t.dateModifiedEpochSeconds,
        bitrate = t.bitrate,
        id = t.id,
    )

    private companion object {

        /**
         * `VOLUME_EXTERNAL` rather than `EXTERNAL_CONTENT_URI`: it covers every mounted external volume,
         * including an SD card, where the legacy constant is primary storage only. Available from API 29,
         * which is `minSdk`.
         */
        val AudioCollection: Uri =
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        /**
         * `BITRATE` and `DISC_NUMBER` are absent on purpose - both arrived in API 31. Bitrate is derived
         * from size and duration; the disc number is decoded out of `TRACK`, which encodes it. See
         * [deriveBitrate] and [decodeDiscAndTrack].
         */
        val Projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )

        /** SQLite's `SQLITE_MAX_VARIABLE_NUMBER`, which is 999 on the older Android builds in range. */
        const val MaxBoundParameters = 500

        /** Progress emission interval, in cursor rows. See the throttle note at the call site. */
        const val ProgressEveryNRows = 32

        /** MediaStore's own literal for a missing tag, which it hands back verbatim. */
        const val MediaStoreUnknown = "<unknown>"
        const val UnknownArtist = "Unknown Artist"
        const val UnknownAlbum = "Unknown Album"

        /**
         * MediaStore reports a missing artist or album as the literal string `<unknown>`, which would otherwise
         * be displayed and sorted as written - under "<".
         *
         * The replacement for album matters beyond looks: `NullWaveCoverArt`'s `coverArtSource` treats
         * "Unknown Album" as a placeholder and falls back to the track title for the monogram, so using the same
         * spelling here is what makes that rule fire.
         */
        private fun String?.orUnknown(fallback: String): String =
            if (isNullOrBlank() || this == MediaStoreUnknown) fallback else this
    }
}


