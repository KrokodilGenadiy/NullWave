package com.zaus.nullwave.core.data

import com.zaus.nullwave.core.data.internal.ScanDiff
import com.zaus.nullwave.core.data.internal.ScannedTrack
import com.zaus.nullwave.core.data.internal.decodeDiscAndTrack
import com.zaus.nullwave.core.data.internal.deriveBitrate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of a scan that are not `ContentResolver` plumbing: deciding what to write, and the two
 * decodings that stand in for API 31 columns. All pure, so no device and no Robolectric.
 */
class ScanDiffTest {

    private fun scanned(id: Long, modified: Long = 100L) = ScannedTrack(
        id = id,
        title = "Track $id",
        artist = "Static Pilgrim",
        album = "Nullwave EP",
        albumId = 1,
        artistId = 1,
        durationMs = 180_000,
        trackNumber = 1,
        discNumber = null,
        year = 2077,
        mimeType = "audio/flac",
        sizeBytes = 10_000_000,
        relativePath = "Music/",
        displayName = "track.flac",
        dateAddedEpochSeconds = 1,
        dateModifiedEpochSeconds = modified,
        bitrate = 1_000_000,
    )

    @Test
    fun `an unchanged library plans no writes`() {
        val plan = ScanDiff.of(
            existing = mapOf(1L to 100L, 2L to 100L),
            scanned = listOf(scanned(1), scanned(2)),
        )

        // The property the whole incremental design rests on: a repeat scan writes nothing, so no
        // repository Flow re-emits and no list rebuilds.
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `new ids are inserted and vanished ids are deleted`() {
        val plan = ScanDiff.of(
            existing = mapOf(1L to 100L, 2L to 100L),
            scanned = listOf(scanned(2), scanned(3)),
        )

        assertEquals(listOf(3L), plan.insert.map { it.id })
        assertEquals(listOf(1L), plan.delete)
        assertTrue(plan.update.isEmpty())
    }

    @Test
    fun `a changed date_modified is an update, not an insert`() {
        val plan = ScanDiff.of(
            existing = mapOf(1L to 100L),
            scanned = listOf(scanned(1, modified = 200L)),
        )

        // An insert would be INSERT OR IGNORE and silently do nothing, leaving stale metadata forever.
        assertEquals(listOf(1L), plan.update.map { it.id })
        assertTrue(plan.insert.isEmpty())
    }

    @Test
    fun `a timestamp going backwards still counts as changed`() {
        // Restoring from a backup can move date_modified earlier. "Different" is the test, not "newer".
        val plan = ScanDiff.of(
            existing = mapOf(1L to 500L),
            scanned = listOf(scanned(1, modified = 200L)),
        )

        assertEquals(listOf(1L), plan.update.map { it.id })
    }

    @Test
    fun `an empty database inserts everything`() {
        val plan = ScanDiff.of(existing = emptyMap(), scanned = listOf(scanned(1), scanned(2)))

        assertEquals(2, plan.insert.size)
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `an empty scan deletes everything it knew about`() {
        // Only ever reached with the permission held - MediaStoreLibraryScanner refuses to scan without
        // it, precisely so this branch cannot be triggered by a revoked permission.
        val plan = ScanDiff.of(existing = mapOf(1L to 100L, 2L to 100L), scanned = emptyList())

        assertEquals(listOf(1L, 2L), plan.delete.sorted())
    }

    // ── deriveBitrate ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bitrate is derived from size and duration`() {
        // 10 MB over 180 s is about 444 kbps.
        assertEquals(444_444L, deriveBitrate(sizeBytes = 10_000_000, durationMs = 180_000))
        // A CD-rate FLAC: 30 MB over 180 s.
        assertEquals(1_333_333L, deriveBitrate(sizeBytes = 30_000_000, durationMs = 180_000))
    }

    @Test
    fun `a zero duration yields zero rather than dividing by it`() {
        assertEquals(0L, deriveBitrate(sizeBytes = 10_000_000, durationMs = 0))
        assertEquals(0L, deriveBitrate(sizeBytes = 10_000_000, durationMs = -1))
    }

    @Test
    fun `a long lossless file does not overflow`() {
        // size * 8 * 1000 is the intermediate. A 2 GB file would overflow a 32-bit computation; this is
        // the check that the arithmetic is Long all the way through.
        val twoGigabytes = 2L * 1024 * 1024 * 1024
        val threeHours = 3L * 60 * 60 * 1000
        // 2,147,483,648 bytes * 8 * 1000 / 10,800,000 ms. The intermediate is ~1.7e13, which would
        // overflow Int and come back negative or absurd - the assertion being a plausible ~1.6 Mbps is
        // the actual check.
        assertEquals(1_590_728L, deriveBitrate(twoGigabytes, threeHours))
    }

    // ── decodeDiscAndTrack ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a plain track number has no disc`() {
        assertEquals(null to 3, decodeDiscAndTrack(3))
        assertEquals(null to 999, decodeDiscAndTrack(999))
    }

    @Test
    fun `MediaStore encodes the disc in the thousands`() {
        // 1003 is disc 1, track 3 - not track 1003. Using the raw value sorts a two-disc album
        // 1001, 1002, 2001: in order, but wrong wherever it is displayed.
        assertEquals(1 to 3, decodeDiscAndTrack(1003))
        assertEquals(2 to 1, decodeDiscAndTrack(2001))
        assertEquals(3 to 12, decodeDiscAndTrack(3012))
    }

    @Test
    fun `missing and zero values decode to nothing`() {
        assertEquals(null to null, decodeDiscAndTrack(null))
        assertEquals(null to null, decodeDiscAndTrack(0))
        assertEquals(null to null, decodeDiscAndTrack(-1))
    }

    @Test
    fun `a disc marker with no track number keeps the disc`() {
        // 2000 means disc 2 with the track unknown, not disc 2 track 0.
        assertEquals(2 to null, decodeDiscAndTrack(2000))
    }
}
