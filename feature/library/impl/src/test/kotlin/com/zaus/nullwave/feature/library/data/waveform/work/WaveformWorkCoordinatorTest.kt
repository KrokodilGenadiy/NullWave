package com.zaus.nullwave.feature.library.data.waveform.work

import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.media.waveform.Waveform
import com.zaus.nullwave.feature.library.data.source.audioRow
import com.zaus.nullwave.feature.library.data.waveform.WaveformBinCount
import com.zaus.nullwave.feature.library.data.waveform.WaveformDiskCache
import com.zaus.nullwave.feature.library.data.waveform.WaveformFileRevision
import com.zaus.nullwave.feature.library.data.waveform.WaveformPreparation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WaveformWorkCoordinatorTest {
    @get:Rule val folder = TemporaryFolder()

    private class Fixture(scope: CoroutineScope) {
        val batches = mutableListOf<List<String>>()
        val delays = mutableListOf<Long>()
        val prepared = mutableListOf<TrackId>()
        val stored = mutableMapOf<TrackId, String>()
        var access = true
        var now = 0L
        var prepare: suspend (TrackId) -> WaveformPreparation = { WaveformPreparation.Ready }
        val coordinator = WaveformWorkCoordinator(
            scope = scope,
            queue = WaveformWorkQueue { ids, delay ->
                batches.add(ids.toList())
                delays.add(delay)
            },
            hasAccess = { access },
            hasPreparedRevision = { id, revision -> stored[id] == revision },
            prepare = { id, revision ->
                prepared.add(id)
                prepare(id).also { if (it == WaveformPreparation.Ready) stored[id] = revision }
            },
            elapsedMs = { now },
        )
    }

    @Test
    fun `conflated catalogue admits each valid file and detects edits or evicted files`() = runTest {
        val f = Fixture(backgroundScope)
        val row = audioRow()
        f.coordinator.submit(listOf(audioRow(id = 99)))
        f.coordinator.submit(listOf(row, row, audioRow(volume = "external"), audioRow(id = -1)))
        runCurrent()
        val first = f.batches.single()
        assertEquals(1, first.size)
        f.coordinator.prepareWaveforms(first)
        f.coordinator.submit(listOf(row.copy(title = "Renamed for display")))
        runCurrent()
        assertEquals(1, f.batches.size)
        f.stored.clear()
        f.coordinator.submit(listOf(row))
        runCurrent()
        assertEquals(first, f.batches[1])
        f.coordinator.prepareWaveforms(first)
        f.coordinator.submit(listOf(row.copy(generationModified = 100L)))
        runCurrent()
        assertTrue(first != f.batches[2])
    }

    @Test
    fun `a fresh coordinator skips persisted work and schedules only missing or changed revisions`() = runTest {
        val directory = folder.newFolder()
        val enqueued = mutableListOf<List<String>>()
        val prepared = mutableListOf<TrackId>()
        val peaks = Waveform.of(List(WaveformBinCount) { 0.1f })
        fun newCoordinator(): WaveformWorkCoordinator {
            val disk = WaveformDiskCache(directory)
            return WaveformWorkCoordinator(
                scope = backgroundScope,
                queue = WaveformWorkQueue { requests, _ -> enqueued.add(requests.toList()) },
                hasAccess = { true },
                hasPreparedRevision = disk::hasPreparedRevision,
                prepare = { id, revision ->
                    prepared.add(id)
                    assertTrue(disk.write(WaveformFileRevision(id, "fixture"), peaks, revision))
                    WaveformPreparation.Ready
                },
                elapsedMs = { 0L },
            )
        }
        val row = audioRow()
        val unchanged = audioRow(id = 2)
        val first = newCoordinator()
        first.submit(listOf(row))
        runCurrent()
        first.prepareWaveforms(enqueued.single())
        val firstFile = directory.listFiles().orEmpty().single()
        first.submit(listOf(row, unchanged))
        runCurrent()
        first.prepareWaveforms(enqueued[1])
        val previousRequests = enqueued.flatten()
        assertEquals(2, previousRequests.size)
        prepared.clear()
        enqueued.clear()

        val cold = newCoordinator()
        cold.submit(listOf(row, unchanged))
        runCurrent()
        cold.prepareWaveforms(previousRequests)
        assertTrue(enqueued.isEmpty())
        assertTrue(prepared.isEmpty())

        val edited = row.copy(generationModified = 7)
        cold.submit(listOf(edited, unchanged))
        runCurrent()
        val editedRequest = enqueued.single().single()
        assertEquals(previousRequests.first().substringBefore('|'), editedRequest.substringBefore('|'))
        assertTrue(editedRequest != previousRequests.first())
        cold.prepareWaveforms(listOf(editedRequest))
        assertTrue(firstFile.delete())
        newCoordinator().submit(listOf(edited, unchanged))
        runCurrent()
        assertEquals(listOf(editedRequest), enqueued[1])
    }

    @Test
    fun `unavailable file leaves following tracks usable and a later sync can retry it`() = runTest {
        val f = Fixture(backgroundScope)
        val rows = listOf(audioRow(), audioRow(id = 2))
        f.coordinator.submit(rows)
        runCurrent()
        val batch = f.batches.single()
        f.prepare = { id ->
            if (id.value.endsWith(":1")) WaveformPreparation.Unavailable else WaveformPreparation.Ready
        }
        f.coordinator.prepareWaveforms(batch)
        assertEquals(2, f.prepared.size)
        f.coordinator.submit(rows)
        runCurrent()
        assertEquals(1, f.batches.size)

        val edited = rows.first().copy(generationModified = 2)
        f.coordinator.submit(listOf(edited, rows.last()))
        runCurrent()
        val editedRequest = f.batches[1].single()
        assertTrue(editedRequest != batch.first())
        f.coordinator.prepareWaveforms(listOf(editedRequest))
        assertEquals(3, f.prepared.size)

        f.now += 3_600_000L
        f.coordinator.submit(listOf(edited, rows.last()))
        runCurrent()
        assertEquals(listOf(editedRequest), f.batches[2])
    }

    @Test
    fun `checkpoint reschedules the long track after other files without another catalogue sync`() = runTest {
        val f = Fixture(backgroundScope)
        f.coordinator.submit(listOf(audioRow(), audioRow(id = 2)))
        runCurrent()
        val batch = f.batches.single()
        f.prepare = { WaveformPreparation.Incomplete }
        f.coordinator.prepareWaveforms(batch)
        assertEquals(listOf(listOf(batch[1]), listOf(batch[0])), f.batches.drop(1))
        f.prepare = { WaveformPreparation.Ready }
        f.coordinator.prepareWaveforms(f.batches[1])
        f.coordinator.prepareWaveforms(f.batches[2])
        assertEquals(listOf("external_primary:1", "external_primary:2", "external_primary:1"), f.prepared.map { it.value })
    }

    @Test
    fun `foreground contention reschedules with delay and leaves other files immediately eligible`() = runTest {
        val f = Fixture(backgroundScope)
        f.coordinator.submit(listOf(audioRow(), audioRow(id = 2)))
        runCurrent()
        val batch = f.batches.single()
        f.prepare = { WaveformPreparation.Busy }
        f.coordinator.prepareWaveforms(batch)
        assertEquals(listOf(listOf(batch[1]), listOf(batch[0])), f.batches.drop(1))
        assertEquals(listOf(0L, 60_000L), f.delays.drop(1))
    }

    @Test
    fun `cancellation or revocation never marks unprepared files completed`() = runTest {
        val f = Fixture(backgroundScope)
        val rows = listOf(audioRow(), audioRow(id = 2))
        f.coordinator.submit(rows)
        runCurrent()
        val batch = f.batches.single()
        f.prepare = { awaitCancellation() }
        val interrupted = launch { f.coordinator.prepareWaveforms(batch) }
        runCurrent()
        interrupted.cancelAndJoin()
        f.prepare = {
            f.access = false
            WaveformPreparation.Unavailable
        }
        f.coordinator.prepareWaveforms(batch)
        assertEquals(2, f.prepared.size)
        f.access = true
        f.coordinator.submit(rows)
        runCurrent()
        assertEquals(batch, f.batches[1])
    }
}
