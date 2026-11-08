package com.zaus.nullwave.feature.library.data.waveform

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore.Audio.Media
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.File

/** Changes the MediaStore row independently of its physical file. */
internal class WaveformTestProvider(private val file: File, @Volatile var durationMs: Long?) : ContentProvider() {
    @Volatile var dateModifiedSeconds = 7L
    @Volatile var generationModified = 1L
    @Volatile var advanceDateAfterNextQuery = false
    @Volatile var lastProjection: List<String> = emptyList()
        private set

    fun request(id: TrackId): String = waveformCatalogRequest(
        id, generationModified.takeIf { Build.VERSION.SDK_INT >= 30 }, dateModifiedSeconds, file.length(), durationMs,
    )

    fun row(id: TrackId) = MediaStoreAudioRow(
        id = id.value.substringAfter(':').toLong(), volume = id.value.substringBefore(':'),
        title = null, displayName = file.name, artist = null, album = null, durationMs = durationMs,
        dateAddedSeconds = null, sizeBytes = file.length(), dateModifiedSeconds = dateModifiedSeconds,
        generationModified = generationModified.takeIf { Build.VERSION.SDK_INT >= 30 },
    )

    override fun onCreate() = true
    override fun getType(uri: Uri) = "audio/flac"
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor =
        AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor {
        val columns = requireNotNull(projection)
        lastProjection = columns.toList()
        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    Media.DATE_MODIFIED -> dateModifiedSeconds
                    Media.GENERATION_MODIFIED -> generationModified
                    Media.SIZE -> file.length()
                    Media.DURATION -> durationMs
                    else -> error("Unexpected column $column")
                }
            })
            if (advanceDateAfterNextQuery) {
                advanceDateAfterNextQuery = false
                dateModifiedSeconds++
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = error("Unexpected insert")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        error("Unexpected update")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected delete")
}
