package com.zaus.nullwave.feature.library.presentation

import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.feature.library.api.model.SortDirection
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackSort

// Keep the original Songs keys so existing saved entries continue to restore.
private const val SORT_KEY = "library.sort"
private const val DIRECTION_KEY = "library.direction"
private const val TAB_KEY = "library.tab"
private const val ALBUM_DIRECTION_KEY = "library.albums.direction"
private const val ARTIST_DIRECTION_KEY = "library.artists.direction"

/** Restore user choices only. Locale is fresh; access and catalog snapshots are never persisted. */
internal fun SavedStateHandle.restoreLibraryState(localeTag: String): LibraryState = LibraryState(
    query = LibraryTrackQuery(
        localeTag,
        enumValue(SORT_KEY, LibraryTrackSort.Title),
        enumValue(DIRECTION_KEY, SortDirection.Ascending),
    ),
    selectedTab = enumValue(TAB_KEY, LibraryTab.Songs),
    albumQuery = LibraryAlbumQuery(localeTag, enumValue(ALBUM_DIRECTION_KEY, SortDirection.Ascending)),
    artistQuery = LibraryArtistQuery(localeTag, enumValue(ARTIST_DIRECTION_KEY, SortDirection.Ascending)),
)

internal fun SavedStateHandle.saveTrackSort(state: LibraryState) {
    this[SORT_KEY] = state.query.sort.name
    this[DIRECTION_KEY] = state.query.direction.name
}

internal fun SavedStateHandle.saveTab(state: LibraryState) {
    this[TAB_KEY] = state.selectedTab.name
}

internal fun SavedStateHandle.saveAlbumSort(state: LibraryState) {
    this[ALBUM_DIRECTION_KEY] = state.albumQuery.direction.name
}

internal fun SavedStateHandle.saveArtistSort(state: LibraryState) {
    this[ARTIST_DIRECTION_KEY] = state.artistQuery.direction.name
}

private inline fun <reified T : Enum<T>> SavedStateHandle.enumValue(key: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == get<Any?>(key) } ?: fallback
