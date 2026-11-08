package com.zaus.nullwave.feature.library.data.waveform.work

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress

/** The default WorkManager factory can recreate this worker before any Activity exists. */
class WaveformWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val entries = inputData.getNullableStringArray(Requests) ?: return Result.failure()
        val requests = ArrayList<String>(entries.size)
        for (entry in entries) {
            if (entry == null || '|' !in entry) return Result.failure()
            val id = entry.substringBefore('|')
            if (id.isBlank() || MediaStoreTrackAddress.from(TrackId(id)) == null) return Result.failure()
            requests.add(entry)
        }
        val owner = applicationContext as WaveformWorkerComponentOwner
        return try {
            owner.waveformWorkerComponent.prepareWaveforms(requests)
            Result.success()
        } catch (_: SQLiteException) {
            // Enqueuing a continuation may temporarily fail when WorkManager's database is unavailable.
            Result.retry()
        }
    }

    internal companion object {
        const val Requests = "tracks"
    }
}

fun interface WaveformWorkerComponent {
    suspend fun prepareWaveforms(requests: List<String>)
}

interface WaveformWorkerComponentOwner {
    val waveformWorkerComponent: WaveformWorkerComponent
}
