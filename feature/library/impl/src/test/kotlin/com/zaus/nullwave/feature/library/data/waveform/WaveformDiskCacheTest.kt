package com.zaus.nullwave.feature.library.data.waveform

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.Waveform
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WaveformDiskCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val revision = WaveformFileRevision(TrackId("external_primary:15"), "revision one")
    private val waveform = Waveform.of(List(WaveformBinCount) { it.toFloat() / WaveformBinCount })

    @Test
    fun aNewRevisionReplacesTheSameEntryAndEveryPeakSurvivesReopening() {
        val writer = WaveformDiskCache(folder.root)
        assertTrue(writer.write(revision, waveform))
        val reader = WaveformDiskCache(folder.root)
        assertEquals(waveform, reader.read(revision)?.waveform)

        val newer = revision.copy(fingerprint = "revision two")
        assertNull(reader.read(newer))
        val silence = Waveform.of(List(WaveformBinCount) { 0f })
        assertTrue(writer.write(newer, silence))
        assertNull(reader.read(revision))
        assertEquals(silence, reader.read(newer)?.waveform)
        assertEquals(1, folder.root.listFiles().orEmpty().size)
    }

    @Test
    fun aCatalogueStampSurvivesReopeningAndTracksAtomicReplacementAndEviction() {
        val writer = WaveformDiskCache(folder.root)
        val reader = WaveformDiskCache(folder.root)
        val id = revision.trackId
        assertTrue(writer.write(revision, waveform))
        assertFalse(reader.hasPreparedRevision(id, "catalogue A"))
        assertEquals(waveform, reader.read(revision)?.waveform)

        assertTrue(writer.write(revision, waveform, "catalogue A"))
        assertTrue(reader.hasPreparedRevision(id, "catalogue A"))
        assertTrue(reader.hasPreparedRevision(id, "catalogue A"))
        val newer = revision.copy(fingerprint = "updated audio")
        assertTrue(writer.write(newer, waveform, "catalogue B"))
        assertFalse(reader.hasPreparedRevision(id, "catalogue A"))
        assertTrue(reader.hasPreparedRevision(id, "catalogue B"))
        assertEquals(waveform, reader.read(newer)?.waveform)
        assertNull(reader.read(revision))

        val file = folder.root.listFiles().orEmpty().single()
        val damaged = file.readBytes().also { it[100] = (it[100].toInt() xor 1).toByte() }
        file.writeBytes(damaged)
        assertFalse(WaveformDiskCache(folder.root).hasPreparedRevision(id, "catalogue B"))
        assertTrue(file.delete())
        assertFalse(reader.hasPreparedRevision(id, "catalogue B"))
    }

    @Test
    fun aCheckpointSurvivesProcessRecreationWithoutBecomingACompletedWaveform() {
        val first = WaveformDiskCache(folder.root)
        val checkpoint = WaveformCheckpoint(durationUs = 90_000_000, positionUs = 32_000_000, waveform = waveform)
        assertTrue(first.writeCheckpoint(revision, checkpoint))

        val reopened = WaveformDiskCache(folder.root)
        assertEquals(checkpoint, reopened.readCheckpoint(revision))
        assertNull(reopened.readCheckpoint(revision.copy(fingerprint = "replacement")))
        assertNull(reopened.read(revision))
        assertFalse(reopened.contains(revision.trackId))

        reopened.write(revision, waveform)
        reopened.removeCheckpoint(revision.trackId)
        assertNull(first.readCheckpoint(revision))
        assertEquals(waveform, first.read(revision)?.waveform)
    }

    @Test
    fun completedPeaksRememberWhetherTheDecoderConsultedMediaStore() {
        val cache = WaveformDiskCache(folder.root)
        for (hint in listOf(null, WaveformDurationHint(null), WaveformDurationHint(6_144_000))) {
            assertTrue(cache.write(revision, waveform, durationHint = hint))
            assertEquals(StoredWaveform(waveform, hint), WaveformDiskCache(folder.root).read(revision))
        }
    }

    @Test
    fun changedOrRemovedHintDiscardsCheckpointAndItsOldProgress() {
        val checkpoint = WaveformCheckpoint(durationUs = 12_288_000, positionUs = 5_000_000, waveform = waveform)
        val cache = WaveformDiskCache(folder.root)
        for (newHint in listOf(null, 6_144_000L)) {
            assertTrue(cache.writeCheckpoint(revision, checkpoint, WaveformDurationHint(checkpoint.durationUs)))
            assertEquals(checkpoint, cache.readCheckpoint(revision) { checkpoint.durationUs })
            assertNull(cache.readCheckpoint(revision) { newHint })
            assertNull(cache.readCheckpoint(revision) { checkpoint.durationUs })
        }
    }

    @Test
    fun olderEntriesWithoutDurationProvenanceAreRebuiltInPlace() {
        val cache = WaveformDiskCache(folder.root)
        val checkpoint = WaveformCheckpoint(durationUs = 100, positionUs = 60, waveform = waveform)
        assertTrue(cache.writeCheckpoint(revision, checkpoint))
        val partial = folder.root.listFiles().orEmpty().single()
        removeDurationHeader(partial, version = 1)
        assertNull(cache.readCheckpoint(revision))

        val stamp = "catalogue"
        for ((version, catalog) in listOf(1 to null, 2 to stamp)) {
            assertTrue(cache.write(revision, waveform, catalog))
            val entry = folder.root.listFiles().orEmpty().single { it.extension == "wave" }
            removeDurationHeader(entry, version)
            assertFalse(cache.contains(revision.trackId))
            assertFalse(cache.hasPreparedRevision(revision.trackId, stamp))
            assertNull(cache.read(revision))
        }
        assertTrue(cache.write(revision, waveform, stamp))
        assertTrue(cache.hasPreparedRevision(revision.trackId, stamp))
        assertEquals(1, folder.root.listFiles().orEmpty().count { it.extension == "wave" })
    }

    private fun removeDurationHeader(file: File, version: Int) {
        val original = file.readBytes()
        val legacy = original.copyOf(original.size - Long.SIZE_BYTES)
        val hintOffset = Int.SIZE_BYTES + 32 // SHA-256 fingerprint
        original.copyInto(legacy, hintOffset, hintOffset + Long.SIZE_BYTES)
        val buffer = ByteBuffer.wrap(legacy)
        buffer.putInt(version)
        val checksumOffset = legacy.size - Int.SIZE_BYTES
        buffer.putInt(checksumOffset, CRC32().apply { update(legacy, 0, checksumOffset) }.value.toInt())
        file.writeBytes(legacy)
    }

    @Test
    fun interruptedOrInvalidCheckpointWritesCannotClaimProgress() {
        val checkpoint = WaveformCheckpoint(durationUs = 100, positionUs = 60, waveform = waveform)
        val cache = WaveformDiskCache(folder.root)
        assertTrue(cache.writeCheckpoint(revision, checkpoint))
        assertFalse(cache.writeCheckpoint(revision, checkpoint.copy(positionUs = 0)))
        assertFalse(cache.writeCheckpoint(revision, checkpoint.copy(positionUs = 100)))
        assertEquals(checkpoint, cache.readCheckpoint(revision))

        val entry = folder.root.listFiles().orEmpty().single()
        entry.writeBytes(entry.readBytes().copyOf(30))
        assertNull(cache.readCheckpoint(revision))
        val unwritable = WaveformDiskCache(folder.newFile("not-a-directory"))
        assertFalse(unwritable.writeCheckpoint(revision, checkpoint))
    }

    @Test
    fun truncatedExtraCorruptAndUnsupportedFilesAreCacheMisses() {
        val cache = WaveformDiskCache(folder.root)
        cache.write(revision, waveform)
        val file = folder.root.listFiles().orEmpty().single()
        val good = file.readBytes()
        file.writeBytes(good.copyOf(good.size - 1))
        assertFalse(cache.contains(revision.trackId))
        assertNull(cache.read(revision))
        file.writeBytes(good + 0)
        assertNull(cache.read(revision))

        val corrupt = good.clone().apply { this[60] = (this[60].toInt() xor 1).toByte() }
        file.writeBytes(corrupt)
        assertTrue(cache.contains(revision.trackId))
        assertNull(cache.read(revision))

        val future = ByteBuffer.wrap(good.clone())
        future.putInt(Int.MAX_VALUE)
        val checksumOffset = good.size - Int.SIZE_BYTES
        val checksum = CRC32().apply { update(future.array(), 0, checksumOffset) }
        future.putInt(checksumOffset, checksum.value.toInt())
        file.writeBytes(future.array())
        assertNull(cache.read(revision))
    }
}
