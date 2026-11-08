package com.zaus.nullwave.feature.library.data.waveform.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.await
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface WaveformWorkQueue {
    suspend fun enqueue(requests: List<String>, delayMs: Long)
}

/** One durable serial chain. Pending revisions are identified by their WorkManager tags. */
internal class AndroidWaveformWorkQueue(
    private val context: Context,
    private val workName: String = "library.waveforms",
) : WaveformWorkQueue {
    private val lock = Mutex()
    private val waitingWork = WorkQuery.Builder.fromUniqueWorkNames(listOf(workName))
        .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED))
        .build()

    override suspend fun enqueue(requests: List<String>, delayMs: Long) {
        if (requests.isEmpty()) return
        lock.withLock {
            val manager = WorkManager.getInstance(context)
            // A running worker may have just lost access. Always allow one waiting successor.
            val pending = manager.getWorkInfosFlow(waitingWork).first().flatMapTo(mutableSetOf()) { it.tags }
            val fresh = requests.distinct().filterNot(pending::contains)
            // Bound both input Data and the prerequisite graph inserted by one transaction.
            for (group in fresh.chunked(BatchSize * 32)) {
                val work = group.chunked(BatchSize).map { batch ->
                    OneTimeWorkRequestBuilder<WaveformWorker>()
                        .setConstraints(BackgroundConstraints)
                        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf(WaveformWorker.Requests to batch.toTypedArray()))
                        .apply { batch.forEach { addTag(it) } }
                        .build()
                }
                var chain = manager.beginUniqueWork(workName, ExistingWorkPolicy.APPEND_OR_REPLACE, work.first())
                for (next in work.drop(1)) chain = chain.then(next)
                chain.enqueue().await()
            }
        }
    }

    private companion object {
        const val BatchSize = 16
        val BackgroundConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
    }
}
