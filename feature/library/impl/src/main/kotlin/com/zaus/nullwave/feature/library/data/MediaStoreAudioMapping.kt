package com.zaus.nullwave.feature.library.data

import com.zaus.nullwave.feature.library.api.model.LibraryQuery
import com.zaus.nullwave.feature.library.api.model.LibrarySort
import com.zaus.nullwave.feature.library.api.model.LibraryTrack
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.TrackId
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local collator per snapshot: Collator is mutable and cannot be shared across subscriptions. */
internal suspend fun List<MediaStoreAudioRow>.toTracks(query: LibraryQuery): List<LibraryTrack> {
    val context = currentCoroutineContext()
    val artwork = artworkReferences()
    val collator = Collator.getInstance(Locale.forLanguageTag(query.localeTag))
    val entries = map { row ->
        context.ensureActive()
        val volume = row.volume?.takeIf { it.isNotBlank() }
            ?: throw IOException("MediaStore audio row has no volume")
        if (row.id < 0) throw IOException("MediaStore audio row has an invalid identity")
        val title = row.title.metadataOrNull() ?: row.displayName.metadataOrNull()
            ?: throw IOException("MediaStore audio row has neither title nor display name")
        DatedTrack(
            LibraryTrack(
                id = TrackId("$volume:${row.id}"),
                title = title,
                artist = row.artist.metadataOrNull(),
                album = row.album.metadataOrNull(),
                durationMs = row.durationMs?.takeIf { it >= 0 },
                sizeBytes = row.sizeBytes?.takeIf { it >= 0 },
                artwork = artwork[row.artworkKey()],
            ),
            row.dateAddedSeconds?.takeIf { it > 0 },
        )
    }
    val ascending = query.direction == SortDirection.Ascending
    return entries.sortedWith { first, second ->
        context.ensureActive()
        val order = when (query.sort) {
            LibrarySort.Title -> if (ascending) collator.compare(first.track.title, second.track.title)
                else collator.compare(second.track.title, first.track.title)
            LibrarySort.Artist -> compareMissingLast(first.track.artist, second.track.artist, ascending, collator::compare)
            LibrarySort.RecentlyAdded -> compareMissingLast(first.added, second.added, ascending, Long::compareTo)
        }
        if (order != 0) order else first.track.id.value.compareTo(second.track.id.value)
    }.map { it.track }
}

private fun String?.metadataOrNull(): String? = this?.trim()?.takeIf {
    it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true)
}

private fun <T : Any> compareMissingLast(first: T?, second: T?, ascending: Boolean, compare: (T, T) -> Int): Int = when {
    first == null && second == null -> 0
    first == null -> 1
    second == null -> -1
    ascending -> compare(first, second)
    else -> compare(second, first)
}

private data class DatedTrack(val track: LibraryTrack, val added: Long?)
