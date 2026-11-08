package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentResolver
import android.content.ContentUris
import android.content.res.AssetFileDescriptor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.SystemClock
import android.provider.MediaStore.Audio.Media
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.LruCache
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.Waveform
import com.zaus.nullwave.core.media.waveform.WaveformSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress
import com.zaus.nullwave.feature.library.data.source.withMediaStoreCancellation
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

internal enum class WaveformPreparation { Ready, Incomplete, Busy, Unavailable }

internal class MediaStoreWaveformSource(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher,
    cacheDirectory: File,
    private val hasAudioAccess: () -> Boolean,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val decode: suspend (AssetFileDescriptor, () -> Long?, WaveformDecodeSession) -> Waveform? = AndroidWaveformDecoder::decode,
) : WaveformSource {
    private val memory = LruCache<TrackId, CachedWaveform>(32)
    private val disk = WaveformDiskCache(cacheDirectory)
    private val readers = mutableMapOf<TrackId, TrackReaders>()

    fun hasDiskEntry(trackId: TrackId): Boolean = disk.contains(trackId)

    fun hasPreparedRevision(trackId: TrackId, catalogRevision: String): Boolean =
        disk.hasPreparedRevision(trackId, catalogRevision)

    override suspend fun load(trackId: TrackId): Waveform? = resolve(trackId, catalogRevision = null).waveform

    suspend fun prepareInBackground(trackId: TrackId, catalogRevision: String): WaveformPreparation =
        resolve(trackId, catalogRevision).outcome

    private suspend fun resolve(trackId: TrackId, catalogRevision: String?): Read {
        val address = MediaStoreTrackAddress.from(trackId) ?: return Read(WaveformPreparation.Unavailable)
        val slot = synchronized(readers) {
            readers.getOrPut(trackId) { TrackReaders() }.also { it.users++ }
        }
        try {
            if (catalogRevision != null) {
                if (!slot.lock.tryLock()) return Read(WaveformPreparation.Busy)
            } else {
                slot.lock.lock()
            }
            try {
                return read(trackId, address, catalogRevision)
            } finally {
                slot.lock.unlock()
            }
        } finally {
            synchronized(readers) {
                slot.users--
                if (slot.users == 0) readers.remove(trackId)
            }
        }
    }

    private suspend fun read(trackId: TrackId, address: MediaStoreTrackAddress, catalogRevision: String?): Read {
        return withMediaStoreCancellation(io) { signal ->
            if (!hasAudioAccess()) return@withMediaStoreCancellation null
            val uri = ContentUris.withAppendedId(Media.getContentUri(address.volume), address.id)
            try {
                // Open first, also on cache hits. A cached waveform cannot authorize a read.
                resolver.openAssetFileDescriptor(uri, "r", signal)?.use { file ->
                    fun readCatalogRow() = resolver.readWaveformCatalogRevision(trackId, uri, signal)
                    val catalogRow = if (catalogRevision != null) {
                        readCatalogRow()?.takeIf { it.request == catalogRevision } ?: return@use null
                    } else null
                    val currentHintUs by lazy(LazyThreadSafetyMode.NONE) {
                        if (catalogRow == null) durationHintUs(uri, signal) else catalogRow.durationMs.toDurationUs()
                    }
                    val revision = file.revision(trackId)
                    if (revision != null) {
                        val inMemory = memory.get(trackId)?.takeIf { it.revision == revision }
                        val cached = inMemory?.peaks ?: disk.read(revision)
                        if (cached != null && (cached.durationHint == null || cached.durationHint.microseconds == currentHintUs)) {
                            currentCoroutineContext().ensureActive()
                            if (!hasAudioAccess() || revision != file.revision(trackId)) return@use null
                            val persisted = when {
                                inMemory?.persisted == false -> false
                                catalogRevision != null -> disk.hasPreparedRevision(trackId, catalogRevision)
                                else -> inMemory == null || disk.contains(trackId)
                            }
                            if (!persisted && catalogRevision != null && readCatalogRow()?.request != catalogRevision) return@use null
                            return@use readyPeaks(revision, cached, catalogRevision, persisted)
                        }
                    }
                    var usedHint: WaveformDurationHint? = null
                    val session = WaveformDecodeSession(
                        previous = revision?.let { disk.readCheckpoint(it) { currentHintUs } },
                        discard = { disk.removeCheckpoint(trackId) },
                        save = { checkpoint ->
                            if (!hasAudioAccess() || revision != file.revision(trackId)) {
                                throw SecurityException("Audio is no longer readable at the same revision")
                            }
                            revision != null && disk.writeCheckpoint(revision, checkpoint, usedHint)
                        },
                        timeLimitMs = if (catalogRevision == null) null else 90_000L,
                        clockMs = clockMs,
                    )
                    val waveform = decode(file, { currentHintUs.also { usedHint = WaveformDurationHint(it) } }, session)
                    currentCoroutineContext().ensureActive()
                    if (!hasAudioAccess()) return@use null
                    if (waveform == null) {
                        when (session.sliceEnd) {
                            WaveformDecodeSession.SliceEnd.Advanced -> return@use Read(WaveformPreparation.Incomplete)
                            WaveformDecodeSession.SliceEnd.NoProgress -> Unit // Keep the position for foreground playback.
                            null -> disk.removeCheckpoint(trackId)
                        }
                        return@use null
                    }
                    if (revision != null) {
                        // A long scan may outlive the row/file it opened, including an atomic replacement.
                        val freshRevision = resolver.openAssetFileDescriptor(uri, "r", signal)?.use { it.revision(trackId) }
                        if (revision != freshRevision || !hasAudioAccess()) return@use null
                        if (catalogRevision != null) {
                            if (readCatalogRow()?.request != catalogRevision) return@use null
                        } else if (usedHint != null && usedHint.microseconds != durationHintUs(uri, signal)) {
                            return@use null
                        }
                        return@use readyPeaks(revision, StoredWaveform(waveform, usedHint), catalogRevision, persisted = false)
                    }
                    Read(WaveformPreparation.Unavailable, waveform) // An unidentified file is usable only for this read.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OperationCanceledException) {
                currentCoroutineContext().ensureActive()
                null
            } catch (_: IOException) {
                null
            } catch (_: SQLiteException) {
                currentCoroutineContext().ensureActive()
                null // Failure to open or revalidate this URI belongs to this track.
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null // Invalid format or detached MediaStore volume.
            } catch (_: IllegalStateException) {
                null // Includes MediaCodec.CodecException.
            }
        } ?: Read(WaveformPreparation.Unavailable)
    }

    private fun durationHintUs(uri: Uri, signal: CancellationSignal): Long? = try {
        resolver.query(uri, arrayOf(Media.DURATION), null, null, null, signal)?.use { rows ->
            if (rows.moveToFirst()) rows.getLong(0).toDurationUs() else null
        }
    } catch (_: SQLiteException) {
        signal.throwIfCanceled()
        null // The decoder can measure duration from the already-open file.
    }

    private fun readyPeaks(
        revision: WaveformFileRevision, peaks: StoredWaveform, catalogRevision: String?, persisted: Boolean,
    ): Read {
        val stored = persisted || disk.write(revision, peaks.waveform, catalogRevision, peaks.durationHint)
        memory.put(revision.trackId, CachedWaveform(revision, peaks, stored))
        if (stored && !persisted) disk.removeCheckpoint(revision.trackId)
        return Read(if (stored) WaveformPreparation.Ready else WaveformPreparation.Unavailable, peaks.waveform)
    }

    private data class Read(val outcome: WaveformPreparation, val waveform: Waveform? = null)

    private data class CachedWaveform(val revision: WaveformFileRevision, val peaks: StoredWaveform, val persisted: Boolean)

    private class TrackReaders {
        val lock = Mutex()
        var users = 0
    }
}

private fun Long?.toDurationUs(): Long? = this?.takeIf { it in 1L..Long.MAX_VALUE / 1_000 }?.times(1_000)

/** Some providers return pipes. Only identifiable, regular files qualify for reuse. */
private fun AssetFileDescriptor.revision(trackId: TrackId): WaveformFileRevision? {
    val stat = try {
        Os.fstat(fileDescriptor)
    } catch (_: ErrnoException) {
        return null
    }
    if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_ino == 0L) return null
    val fingerprint = listOf(stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtim.tv_sec, stat.st_mtim.tv_nsec,
        stat.st_ctim.tv_sec, stat.st_ctim.tv_nsec, startOffset, declaredLength).joinToString(":")
    return WaveformFileRevision(trackId, fingerprint)
}
