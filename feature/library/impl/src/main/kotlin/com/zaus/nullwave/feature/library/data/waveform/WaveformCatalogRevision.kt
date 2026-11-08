package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore.Audio.Media
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.data.source.audioSelection

internal data class WaveformCatalogRevision(val request: String, val durationMs: Long?)

// This representation also identifies requests persisted by previous versions of the worker.
internal fun waveformCatalogRequest(
    id: TrackId, generationModified: Long?, dateModifiedSeconds: Long?, sizeBytes: Long?, durationMs: Long?,
): String = "${id.value}|$generationModified|$dateModifiedSeconds|$sizeBytes|$durationMs"

internal fun ContentResolver.readWaveformCatalogRevision(
    trackId: TrackId, uri: Uri, signal: CancellationSignal, sdk: Int = Build.VERSION.SDK_INT,
): WaveformCatalogRevision? {
    val projection = if (sdk >= 30) {
        arrayOf(Media.DATE_MODIFIED, Media.SIZE, Media.DURATION, Media.GENERATION_MODIFIED)
    } else {
        arrayOf(Media.DATE_MODIFIED, Media.SIZE, Media.DURATION)
    }
    return query(uri, projection, audioSelection(sdk), null, null, signal)?.use { row ->
        if (!row.moveToFirst()) return@use null
        val duration = row.optionalLong(Media.DURATION)
        val request = waveformCatalogRequest(
            trackId, row.optionalLong(Media.GENERATION_MODIFIED), row.optionalLong(Media.DATE_MODIFIED),
            row.optionalLong(Media.SIZE), duration,
        )
        WaveformCatalogRevision(request, duration)
    }
}

private fun Cursor.optionalLong(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getLong(index)
}
