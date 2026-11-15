package com.zaus.nullwave.feature.library.presentation.albumdetails

import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.core.mvi.ui.UiText
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails
import com.zaus.nullwave.feature.library.presentation.LibraryPlaybackProblemKey

internal data class AlbumDetailsState(
    val localeTag: String,
    /** Last host check. A later provider denial hides content until the next check. */
    val platformGranted: Boolean = false,
    val body: AlbumDetailsBody = AlbumDetailsBody.CheckingAccess,
    val playbackError: UiText? = null,
    val monitorPlayback: Boolean = false,
    val lastPlaybackDenial: LibraryPlaybackProblemKey? = null,
) : UiState

internal sealed interface AlbumDetailsBody {
    val details: LibraryAlbumDetails? get() = null

    data object CheckingAccess : AlbumDetailsBody
    data class AccessRequired(val action: AccessAction) : AlbumDetailsBody
    data class Loading(override val details: LibraryAlbumDetails? = null) : AlbumDetailsBody
    data class Ready(override val details: LibraryAlbumDetails) : AlbumDetailsBody
    data object NotFound : AlbumDetailsBody
    data class ReadFailed(override val details: LibraryAlbumDetails?) : AlbumDetailsBody
}
