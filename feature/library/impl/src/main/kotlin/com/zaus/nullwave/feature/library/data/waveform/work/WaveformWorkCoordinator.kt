package com.zaus.nullwave.feature.library.data.waveform.work

import android.os.SystemClock
import android.util.Log
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress
import com.zaus.nullwave.feature.library.data.waveform.WaveformPreparation
import com.zaus.nullwave.feature.library.data.waveform.waveformCatalogRequest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal fun interface WaveformCatalogSync {
    fun submit(rows: List<MediaStoreAudioRow>)
}

internal class WaveformWorkCoordinator(
    scope: CoroutineScope,
    private val queue: WaveformWorkQueue,
    private val hasAccess: () -> Boolean,
    private val hasPreparedRevision: (TrackId, String) -> Boolean,
    private val prepare: suspend (TrackId, String) -> WaveformPreparation,
    private val elapsedMs: () -> Long = SystemClock::elapsedRealtime,
) : WaveformCatalogSync, WaveformWorkerComponent {
    private val catalog = Channel<List<MediaStoreAudioRow>>(Channel.CONFLATED)
    private val unavailable = ConcurrentHashMap<TrackId, Retry>()

    init {
        scope.launch {
            for (rows in catalog) {
                try {
                    if (!hasAccess()) continue
                    val ids = HashSet<TrackId>()
                    val requests = buildList {
                        for (row in backgroundTargets(rows)) {
                            val volume = row.volume ?: continue
                            // A volume name is a filesystem component. Cap serialized input to WorkManager's 10 KiB limit.
                            if (volume.length > 255) continue
                            val id = TrackId("$volume:${row.id}")
                            if (MediaStoreTrackAddress.from(id) == null || !ids.add(id)) continue
                            val request = waveformCatalogRequest(id, row.generationModified, row.dateModifiedSeconds,
                                row.sizeBytes, row.durationMs)
                            if (needsPreparation(id, request)) add(request)
                        }
                    }
                    unavailable.keys.retainAll(ids)
                    if (hasAccess() && requests.isNotEmpty()) queue.enqueue(requests, delayMs = 0)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("WaveformPreparation", "Could not enqueue catalogue waveforms", failure)
                }
            }
        }
    }

    override fun submit(rows: List<MediaStoreAudioRow>) {
        catalog.trySend(rows)
    }

    override suspend fun prepareWaveforms(requests: List<String>) {
        val started = elapsedMs()
        for ((index, request) in requests.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (!hasAccess()) return
            // Leave time for one slow file within a normal worker's execution window.
            if (index > 0 && elapsedMs() - started >= BatchBudgetMs) {
                queue.enqueue(requests.subList(index, requests.size), delayMs = 0)
                return
            }
            val id = TrackId(request.substringBefore('|'))
            if (!needsPreparation(id, request)) continue
            val result = prepare(id, request)
            currentCoroutineContext().ensureActive()
            if (!hasAccess()) return
            if (result == WaveformPreparation.Busy || result == WaveformPreparation.Incomplete) {
                // A foreground reader owns this track, or the decoder saved a later checkpoint.
                // Give the rest of this batch a turn and resume independently of future catalogue refreshes.
                val remaining = requests.drop(index + 1)
                if (remaining.isNotEmpty()) queue.enqueue(remaining, delayMs = 0)
                queue.enqueue(listOf(request), delayMs = if (result == WaveformPreparation.Busy) 60_000 else 0)
                return
            }
            if (result == WaveformPreparation.Ready) unavailable.remove(id)
            else unavailable[id] = Retry(request, elapsedMs() + UnavailableDelayMs)
        }
    }

    private fun needsPreparation(id: TrackId, request: String): Boolean {
        val retry = unavailable[id]
        if (retry?.request == request && elapsedMs() < retry.afterMs) return false
        return !hasPreparedRevision(id, request)
    }

    private data class Retry(val request: String, val afterMs: Long)

    // Leave spare entries for playback outside a very large catalogue's background working set.
    private fun backgroundTargets(rows: List<MediaStoreAudioRow>): List<MediaStoreAudioRow> =
        if (rows.size <= BackgroundLimit) rows else rows.sortedWith(
            compareByDescending<MediaStoreAudioRow> { it.dateAddedSeconds }.thenBy { it.volume }.thenBy { it.id },
        ).take(BackgroundLimit)

    private companion object {
        const val BatchBudgetMs = 2 * 60_000L
        const val BackgroundLimit = 16_000
        const val UnavailableDelayMs = 60 * 60_000L
    }
}
