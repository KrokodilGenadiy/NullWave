package com.zaus.nullwave.feature.library.data.search

import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.search.LibraryAlbumSearchHit
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchQuery
import com.zaus.nullwave.feature.library.api.model.search.LibrarySearchResults
import com.zaus.nullwave.feature.library.api.model.search.LibraryTrackSearchHit
import com.zaus.nullwave.feature.library.api.model.search.normalizeLibrarySearchText
import com.zaus.nullwave.feature.library.data.mapping.artworkKey
import com.zaus.nullwave.feature.library.data.mapping.catalogGroups
import com.zaus.nullwave.feature.library.data.mapping.metadataOrNull
import com.zaus.nullwave.feature.library.data.mapping.toAlbum
import com.zaus.nullwave.feature.library.data.mapping.toArtist
import com.zaus.nullwave.feature.library.data.mapping.toTrack
import com.zaus.nullwave.feature.library.data.mapping.validateAudioIdentityAndGetVolume
import com.zaus.nullwave.feature.library.data.source.MediaStoreAudioRow
import java.io.IOException
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** All groups come from this one complete snapshot. No partial filtering before aggregation. */
internal suspend fun List<MediaStoreAudioRow>.toSearchResults(query: LibrarySearchQuery): LibrarySearchResults {
    val context = currentCoroutineContext()
    val matcher = SearchMatcher(query, context)
    val collator = Collator.getInstance(Locale.forLanguageTag(query.localeTag))
    val ids = mutableSetOf<String>()
    for (row in this) {
        context.ensureActive()
        val volume = row.validateAudioIdentityAndGetVolume()
        if (!ids.add("$volume:${row.id}")) throw IOException("MediaStore returned duplicate audio identity")
        // DISPLAY_NAME is metadata, not a path/URI. Reject malformed provider data before API guards.
        if (row.displayName?.contains('/') == true) throw IOException("MediaStore display name contains a path")
    }

    val albumGroups = catalogGroups { it.albumId }
    val albums = albumGroups.mapValues { (id, members) ->
        context.ensureActive()
        members.toAlbum(id)
    }
    val trackHits = mapNotNull { row ->
        context.ensureActive()
        val track = row.toTrack(albums[row.artworkKey()]?.artwork)
        val fileName = row.displayName.metadataOrNull()
        val primary = normalizeLibrarySearchText(track.title)
        val match = matcher.matchPrimary(primary)
        val rank = match.rank ?: run {
            val fields = listOfNotNull(track.artist, track.album, fileName).map(::normalizeLibrarySearchText)
            if (!matcher.covers(match.missingTokens, fields)) return@mapNotNull null
            SearchMatchRank.Supporting
        }
        RankedHit(LibraryTrackSearchHit(track, fileName), rank)
    }

    val albumHits = albums.mapNotNull { (id, album) ->
        context.ensureActive()
        val primary = album.title?.let(::normalizeLibrarySearchText)
        val match = matcher.matchPrimary(primary)
        val names = if (match.rank != null) emptyList() else {
            val processedNames = mutableSetOf<String>()
            val matchingNames = mutableListOf<String>()
            for (row in albumGroups.getValue(id)) {
                context.ensureActive()
                val name = row.artist.metadataOrNull() ?: continue
                // Deduplicate originals before normalization, including names that do not match.
                // Different spellings that normalize equally still explain the result separately.
                if (!processedNames.add(name)) continue
                if (matcher.covers(match.missingTokens, listOf(normalizeLibrarySearchText(name)))) matchingNames.add(name)
            }
            if (matchingNames.isEmpty()) return@mapNotNull null
            matchingNames.sortedWith { first, second ->
                context.ensureActive()
                val order = collator.compare(first, second)
                if (order != 0) order else first.compareTo(second)
            }
        }
        RankedHit(LibraryAlbumSearchHit(album, names), match.rank ?: SearchMatchRank.Supporting)
    }

    val artistHits = catalogGroups { it.artistId }.mapNotNull { (id, members) ->
        context.ensureActive()
        val artist = members.toArtist(id)
        val rank = matcher.matchPrimary(artist.name?.let(::normalizeLibrarySearchText)).rank ?: return@mapNotNull null
        RankedHit(artist, rank)
    }
    return LibrarySearchResults(
        tracks = trackHits.sortedHits(context, collator, { it.track.title }, { it.track.id.value }),
        albums = albumHits.sortedHits(context, collator, { it.album.title }, { it.album.id.value }),
        artists = artistHits.sortedHits(context, collator, LibraryArtist::name, { it.id.value }),
    )
}
