package com.zaus.nullwave.feature.library.presentation

import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbum
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumQuery
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtist
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistQuery
import com.zaus.nullwave.core.media.model.track.MediaTrack
import com.zaus.nullwave.core.media.model.track.TrackId
import com.zaus.nullwave.feature.library.api.model.track.LibraryTrackQuery
import com.zaus.nullwave.feature.player.api.launch.PlaybackLaunchState

/** [query]/[body] belong to Songs; [selectedBody] exposes the active tab's loading/error status. */
internal data class LibraryState(
    val query: LibraryTrackQuery,
    val body: LibraryBody<MediaTrack> = LibraryBody.Loading,
    val selectedTab: LibraryTab = LibraryTab.Songs,
    val albumQuery: LibraryAlbumQuery = LibraryAlbumQuery(query.localeTag),
    val artistQuery: LibraryArtistQuery = LibraryArtistQuery(query.localeTag),
    val albums: LibraryBody<LibraryAlbum> = LibraryBody.Loading,
    val artists: LibraryBody<LibraryArtist> = LibraryBody.Loading,
    /** Song starts from this screen; Player presents engine problems. Never persisted. */
    val playbackLaunch: PlaybackLaunchState = PlaybackLaunchState(),
    /** Track of the current queue entry, kept through reconnection; display only, never persisted. */
    val playingTrackId: TrackId? = null,
) : UiState {
    val playbackError: UiText? get() = playbackLaunch.notice

    val selectedBody: LibraryBody<*>
        get() = when (selectedTab) {
            LibraryTab.Songs -> body
            LibraryTab.Albums -> albums
            LibraryTab.Artists -> artists
        }
}

internal enum class LibraryTab { Songs, Albums, Artists }

internal sealed interface LibraryBody<out T> {
    data object Loading : LibraryBody<Nothing>
    data class AccessRequired(val action: AccessAction) : LibraryBody<Nothing>

    /** An empty list is the successful empty state, including during refresh or its failure. */
    data class Ready<T>(
        val items: List<T>,
        val refresh: RefreshState = RefreshState.Idle,
    ) : LibraryBody<T>

    data class Failed(val message: UiText) : LibraryBody<Nothing>
}

internal sealed interface RefreshState {
    data object Idle : RefreshState
    data object Running : RefreshState
    data class Failed(val message: UiText) : RefreshState
}
