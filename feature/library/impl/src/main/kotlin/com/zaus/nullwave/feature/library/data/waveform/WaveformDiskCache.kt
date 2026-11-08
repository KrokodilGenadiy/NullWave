package com.zaus.nullwave.feature.library.data.waveform

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.Waveform
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

internal data class WaveformFileRevision(val trackId: TrackId, val fingerprint: String)

/** Present when the container had no duration. A null value means the provider also had no hint. */
internal data class WaveformDurationHint(val microseconds: Long?)

internal data class StoredWaveform(val waveform: Waveform, val durationHint: WaveformDurationHint?)

/** Internal state of an interrupted decode. Only completed waveforms reach Player. */
internal data class WaveformCheckpoint(val durationUs: Long, val positionUs: Long, val waveform: Waveform)

internal class WaveformDiskCache(private val directory: File) {
    private val writes = AtomicInteger()
    private val verifiedStamps = Collections.synchronizedMap(object : LinkedHashMap<TrackId, VerifiedStamp>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TrackId, VerifiedStamp>?): Boolean = size > MaxEntries
    })

    /** A scheduling hint. Only [read] verifies the file revision and contents. */
    fun contains(id: TrackId): Boolean = try {
        val size = entry(id, Kind.Complete).length()
        size == BaseBytes.toLong() || size == StampedBytes.toLong()
    } catch (_: SecurityException) {
        false
    }

    fun hasPreparedRevision(id: TrackId, catalogRevision: String): Boolean {
        val file = entry(id, Kind.Complete)
        val attributes = try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        } catch (_: IOException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
        if (attributes.size() != StampedBytes.toLong()) return false
        // An atomic replacement gets a new file identity. Unchanged entries need only this stat on later scans.
        val stamp = attributes.fileKey()?.let { VerifiedStamp(catalogRevision, it, attributes.lastModifiedTime()) }
        if (stamp != null && stamp == verifiedStamps[id]) return true
        val buffer = readEntry(id, Kind.Complete) ?: return false
        if (buffer.limit() != StampedBytes) return false
        buffer.position(BaseBytes - Int.SIZE_BYTES)
        if (!buffer.matchesDigest(catalogRevision)) return false
        if (stamp != null) verifiedStamps[id] = stamp
        return true
    }

    private data class VerifiedStamp(val catalogRevision: String, val fileKey: Any, val modified: FileTime)

    fun read(revision: WaveformFileRevision): StoredWaveform? {
        val buffer = readEntry(revision.trackId, Kind.Complete) ?: return null
        if (!buffer.matchesDigest(revision.fingerprint)) return null
        val hint = buffer.readDurationHint()
        return StoredWaveform(buffer.readPeaks() ?: return null, hint)
    }

    fun write(
        revision: WaveformFileRevision, waveform: Waveform, catalogRevision: String? = null,
        durationHint: WaveformDurationHint? = null,
    ): Boolean {
        if (waveform.peaks.size != WaveformBinCount) return false
        val stored = writeEntry(revision, Kind.Complete, durationHint, catalogRevision) { waveform.peaks.forEach { putFloat(it) } }
        if (stored) verifiedStamps.remove(revision.trackId)
        return stored
    }

    fun readCheckpoint(revision: WaveformFileRevision, currentHintUs: () -> Long? = { null }): WaveformCheckpoint? {
        val buffer = readEntry(revision.trackId, Kind.Checkpoint) ?: return null
        if (!buffer.matchesDigest(revision.fingerprint)) return null
        val hint = buffer.readDurationHint()
        if (hint != null && hint.microseconds != currentHintUs()) {
            removeCheckpoint(revision.trackId)
            return null
        }
        val durationUs = buffer.long
        val positionUs = buffer.long
        if (positionUs <= 0 || positionUs >= durationUs) return null
        return WaveformCheckpoint(durationUs, positionUs, buffer.readPeaks() ?: return null)
    }

    /** The worker can hand off this file only after its new position is durable. */
    fun writeCheckpoint(
        revision: WaveformFileRevision, checkpoint: WaveformCheckpoint, durationHint: WaveformDurationHint? = null,
    ): Boolean {
        if (checkpoint.positionUs <= 0 || checkpoint.positionUs >= checkpoint.durationUs ||
            checkpoint.waveform.peaks.size != WaveformBinCount
        ) return false
        return writeEntry(revision, Kind.Checkpoint, durationHint) {
            putLong(checkpoint.durationUs)
            putLong(checkpoint.positionUs)
            checkpoint.waveform.peaks.forEach { putFloat(it) }
        }
    }

    fun removeCheckpoint(id: TrackId) {
        try {
            entry(id, Kind.Checkpoint).delete()
        } catch (_: SecurityException) {
            // A later read validates the revision before resuming.
        }
    }

    private fun readEntry(id: TrackId, kind: Kind): ByteBuffer? {
        try {
            return DataInputStream(entry(id, kind).inputStream()).use { input ->
                val version = input.readInt()
                val size = when {
                    version == FormatVersion -> kind.bytes
                    version == StampedFormatVersion && kind == Kind.Complete -> StampedBytes
                    else -> return null
                }
                val bytes = ByteArray(size)
                val buffer = ByteBuffer.wrap(bytes)
                buffer.putInt(version)
                input.readFully(bytes, Int.SIZE_BYTES, size - Int.SIZE_BYTES)
                if (input.read() != -1) return null
                buffer.takeIf { it.getInt(size - Int.SIZE_BYTES) == checksum(bytes) }
            }
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
    }

    private fun ByteBuffer.matchesDigest(value: String): Boolean =
        ByteArray(DigestBytes).also { get(it) }.contentEquals(digest(value))

    private fun ByteBuffer.readDurationHint(): WaveformDurationHint? {
        val value = long
        return if (value == FromContainer) null else WaveformDurationHint(value.takeIf { it > 0L })
    }

    private fun ByteBuffer.readPeaks(): Waveform? {
        val peaks = List(WaveformBinCount) { float }
        return if (peaks.all { it in 0f..1f }) Waveform.of(peaks) else null
    }

    private fun writeEntry(
        revision: WaveformFileRevision,
        kind: Kind,
        durationHint: WaveformDurationHint?,
        catalogRevision: String? = null,
        writePayload: ByteBuffer.() -> Unit,
    ): Boolean {
        try {
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) return false
            val target = entry(revision.trackId, kind)
            val buffer = ByteBuffer.allocate(if (catalogRevision == null) kind.bytes else StampedBytes)
            buffer.putInt(if (catalogRevision == null) FormatVersion else StampedFormatVersion)
            buffer.put(digest(revision.fingerprint))
            buffer.putLong(if (durationHint == null) FromContainer else durationHint.microseconds ?: 0L)
            buffer.writePayload()
            if (catalogRevision != null) buffer.put(digest(catalogRevision))
            buffer.putInt(checksum(buffer.array()))
            val temp = File.createTempFile("pending-", ".tmp", directory)
            try {
                temp.writeBytes(buffer.array())
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temp.delete()
            }
            if (writes.getAndIncrement() % PruneInterval == 0) prune()
            return true
        } catch (_: IOException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
    }

    private enum class Kind(val extension: String, val bytes: Int) {
        Complete("wave", BaseBytes),
        Checkpoint("part", BaseBytes + 2 * Long.SIZE_BYTES),
    }

    private fun entry(id: TrackId, kind: Kind): File =
        File(directory, "1-${digest(id.value).toHexString()}.${kind.extension}")

    /** Bound removed tracks and abandoned checkpoints without walking the directory on every read. */
    private fun prune() {
        val files = directory.listFiles() ?: return
        val oldTempCutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        files.filter { it.extension == "tmp" && it.lastModified() < oldTempCutoff }.forEach { it.delete() }
        for (kind in Kind.entries) {
            val entries = files.filter { it.extension == kind.extension }
            if (entries.size > MaxEntries) {
                entries.map { it to it.lastModified() }.sortedBy { it.second }
                    .take(entries.size - MaxEntries).forEach { it.first.delete() }
            }
        }
    }

    private fun digest(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))

    private fun checksum(bytes: ByteArray): Int =
        CRC32().apply { update(bytes, 0, bytes.size - Int.SIZE_BYTES) }.value.toInt()

    private companion object {
        // v1/v2 lacked duration provenance. Rebuild in place on first use.
        const val FormatVersion = 3
        const val StampedFormatVersion = 4
        const val FromContainer = -1L
        const val DigestBytes = 32
        const val BaseBytes = Int.SIZE_BYTES + DigestBytes + Long.SIZE_BYTES + WaveformBinCount * Float.SIZE_BYTES + Int.SIZE_BYTES
        const val StampedBytes = BaseBytes + DigestBytes
        const val MaxEntries = 16_384
        const val PruneInterval = 256
    }
}
