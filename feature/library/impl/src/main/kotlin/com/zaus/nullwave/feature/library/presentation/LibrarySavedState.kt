package com.zaus.nullwave.feature.library.presentation

import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.mvi.persistence.MviSavedState
import com.zaus.nullwave.core.mvi.persistence.savedState
import kotlinx.serialization.Serializable

/** One small payload; strings allow unknown enum values to fall back field by field. */
@Serializable
private data class LibraryInputs(
    val tab: String = "Songs",
    val sort: String = "Title",
    val direction: String = "Ascending",
    val albumDirection: String = "Ascending",
    val artistDirection: String = "Ascending",
)

/** Restore user choices onto fresh defaults. Access, locale and catalog snapshots are never saved. */
internal fun librarySavedState(handle: SavedStateHandle): MviSavedState<LibraryState> = savedState(
    handle = handle,
    key = "library.inputs",
    serializer = LibraryInputs.serializer(),
    select = {
        LibraryInputs(
            tab = selectedTab.name,
            sort = query.sort.name,
            direction = query.direction.name,
            albumDirection = albumQuery.direction.name,
            artistDirection = artistQuery.direction.name,
        )
    },
    restore = { inputs ->
        copy(
            selectedTab = inputs.tab.enumOr(selectedTab),
            query = query.copy(
                sort = inputs.sort.enumOr(query.sort),
                direction = inputs.direction.enumOr(query.direction),
            ),
            albumQuery = albumQuery.copy(direction = inputs.albumDirection.enumOr(albumQuery.direction)),
            artistQuery = artistQuery.copy(direction = inputs.artistDirection.enumOr(artistQuery.direction)),
        )
    },
    legacy = {
        LibraryInputs(
            tab = string("library.tab") ?: "Songs",
            sort = string("library.sort") ?: "Title",
            direction = string("library.direction") ?: "Ascending",
            albumDirection = string("library.albums.direction") ?: "Ascending",
            artistDirection = string("library.artists.direction") ?: "Ascending",
        )
    },
)

private inline fun <reified T : Enum<T>> String.enumOr(fallback: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: fallback
