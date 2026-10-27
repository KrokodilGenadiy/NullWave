package com.zaus.nullwave.feature.library.data.mapping

import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.core.media.model.ArtworkRef
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local collator per snapshot: Collator is mutable and cannot be shared across subscriptions. */
internal suspend fun List<MediaStoreAudioRow>.toTracks(query: LibraryTrackQuery): List<MediaTrack> {
    val context = currentCoroutineContext()
    val artwork = artworkReferences()
    val collator = Collator.getInstance(Locale.forLanguageTag(query.localeTag))
    val entries = map { row ->
        context.ensureActive()
        DatedTrack(
            row.toTrack(artwork[row.artworkKey()]),
            row.dateAddedSeconds?.takeIf { it > 0 },
        )
    }
    val ascending = query.direction == SortDirection.Ascending
    return entries.sortedWith { first, second ->
        context.ensureActive()
        val order = when (query.sort) {
            LibraryTrackSort.Title -> if (ascending) collator.compare(first.track.title, second.track.title)
                else collator.compare(second.track.title, first.track.title)
            LibraryTrackSort.Artist -> compareMissingLast(first.track.artist, second.track.artist, ascending, collator::compare)
            LibraryTrackSort.RecentlyAdded -> compareMissingLast(first.added, second.added, ascending, Long::compareTo)
        }
        if (order != 0) order else first.track.id.value.compareTo(second.track.id.value)
    }.map { it.track }
}

/** Shared entity mapping; callers supply artwork computed from the complete album membership. */
internal fun MediaStoreAudioRow.toTrack(artwork: ArtworkRef?): MediaTrack {
    val volume = validateAudioIdentityAndGetVolume()
    val title = title.metadataOrNull() ?: displayName.metadataOrNull()
        ?: throw IOException("MediaStore audio row has neither title nor display name")
    return MediaTrack(
        id = TrackId("$volume:$id"),
        title = title,
        artist = artist.metadataOrNull(),
        album = album.metadataOrNull(),
        durationMs = durationMs?.takeIf { it >= 0 },
        sizeBytes = sizeBytes?.takeIf { it >= 0 },
        artwork = artwork,
    )
}

private data class DatedTrack(val track: MediaTrack, val added: Long?)
