package com.zaus.nullwave.feature.library.presentation.albumdetails

import com.zaus.nullwave.core.mvi.UiIntent
import com.zaus.nullwave.feature.library.api.access.LibraryAccess
import com.zaus.nullwave.feature.library.api.model.album.LibraryAlbumDetails

internal sealed interface AlbumDetailsIntent : UiIntent {
    data class AccessChecked(val access: LibraryAccess) : AlbumDetailsIntent
    data class LocaleChanged(val localeTag: String) : AlbumDetailsIntent
    data object Retry : AlbumDetailsIntent
    data class Play(val displayed: LibraryAlbumDetails, val start: AlbumPlaybackStart) : AlbumDetailsIntent
    data object DismissPlaybackError : AlbumDetailsIntent
}

internal sealed interface AlbumPlaybackStart {
    data class At(val index: Int) : AlbumPlaybackStart
    data object Shuffle : AlbumPlaybackStart
}
