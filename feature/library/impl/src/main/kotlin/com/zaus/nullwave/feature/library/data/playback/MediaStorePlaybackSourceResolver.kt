package com.zaus.nullwave.feature.library.data.playback

import android.database.sqlite.SQLiteException
import android.os.OperationCanceledException
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceError
import com.zaus.nullwave.core.playback.api.source.PlaybackSourceResolver
import com.zaus.nullwave.core.playback.api.source.ResolvedPlaybackTrack
import com.zaus.nullwave.core.result.AppResult
import com.zaus.nullwave.feature.library.data.mapping.albumArtwork
import com.zaus.nullwave.feature.library.data.mapping.artworkKey
import com.zaus.nullwave.feature.library.data.mapping.toTrack
import com.zaus.nullwave.feature.library.data.source.MediaStorePlaybackSource
import com.zaus.nullwave.feature.library.data.source.MediaStoreTrackAddress
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One fresh lookup per request; no catalog subscription, cached permissions or open-file guarantee. */
internal class MediaStorePlaybackSourceResolver(
    private val source: MediaStorePlaybackSource,
    private val io: CoroutineDispatcher,
) : PlaybackSourceResolver {
    override suspend fun resolveTrack(id: TrackId): AppResult<ResolvedPlaybackTrack, PlaybackSourceError> =
        withContext(io) {
            try {
                if (!source.hasReadAccess()) return@withContext AppResult.Failure(PlaybackSourceError.AccessDenied)
                val address = MediaStoreTrackAddress.from(id)
                    ?: return@withContext AppResult.Failure(PlaybackSourceError.NotFound)
                val data = source.readTrack(address)
                currentCoroutineContext().ensureActive()
                if (!source.hasReadAccess()) return@withContext AppResult.Failure(PlaybackSourceError.AccessDenied)
                if (data == null) return@withContext AppResult.Failure(PlaybackSourceError.NotFound)

                val artwork = data.row.artworkKey()?.let { key -> data.albumRows.albumArtwork(key) }
                val track = data.row.toTrack(artwork)
                if (track.id != id) throw IOException("MediaStore resolved a different track identity")
                currentCoroutineContext().ensureActive()
                if (!source.hasReadAccess()) AppResult.Failure(PlaybackSourceError.AccessDenied)
                else AppResult.Success(ResolvedPlaybackTrack(track, data.resourceUri))
            } catch (failure: SecurityException) {
                // A provider/resource can deny access even while the broad audio permission is granted.
                AppResult.Failure(PlaybackSourceError.AccessDenied, failure)
            } catch (failure: IOException) {
                ioFailure(failure)
            } catch (failure: SQLiteException) {
                // Provider IPC can erase a more specific SQLite subtype; preserve its diagnostics.
                ioFailure(failure)
            } catch (failure: OperationCanceledException) {
                // Provider cancellation is retryable; cancellation of the caller still propagates.
                ioFailure(failure)
            }
        }

    private suspend fun ioFailure(failure: Exception): AppResult.Failure<PlaybackSourceError> {
        currentCoroutineContext().ensureActive()
        val error = if (source.hasReadAccess()) PlaybackSourceError.Io else PlaybackSourceError.AccessDenied
        return AppResult.Failure(error, failure)
    }
}
