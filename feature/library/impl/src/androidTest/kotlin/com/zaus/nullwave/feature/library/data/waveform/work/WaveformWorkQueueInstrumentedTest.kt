package com.zaus.nullwave.feature.library.data.waveform.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaveformWorkQueueInstrumentedTest {
    @Test
    fun newSchedulerRecognizesDurableBatchesAndAppendsOnlyChangedRevisions() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val name = "waveform-queue-test-" + UUID.randomUUID()
        val requests = List(35) { "external_primary:$it|1" }
        // Keep this test independent of battery state and of the device's actual media catalogue.
        val delayMs = 3_600_000L
        try {
            AndroidWaveformWorkQueue(context, name).enqueue(requests, delayMs)
            val original = manager.getWorkInfosForUniqueWorkFlow(name).first()
            assertEquals(3, original.size)
            assertEquals(1, original.count { it.state == WorkInfo.State.ENQUEUED })
            assertEquals(2, original.count { it.state == WorkInfo.State.BLOCKED })
            assertEquals(requests.toSet(), original.flatMap { it.tags }.intersect(requests.toSet()))

            val scheduler = AndroidWaveformWorkQueue(context, name)
            scheduler.enqueue(requests.reversed(), delayMs)
            val afterSync = manager.getWorkInfosForUniqueWorkFlow(name).first()
            assertEquals(original.map { it.id }.toSet(), afterSync.map { it.id }.toSet())
            val changed = "external_primary:2|2"
            scheduler.enqueue(requests + changed, delayMs)
            val updated = manager.getWorkInfosForUniqueWorkFlow(name).first()
            assertEquals(4, updated.size)
            assertEquals(WorkInfo.State.BLOCKED, updated.single { changed in it.tags }.state)
        } finally {
            manager.cancelUniqueWork(name).await()
        }
    }
}
