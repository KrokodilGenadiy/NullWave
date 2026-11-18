package com.zaus.nullwave.feature.library.presentation.artistdetails

import com.zaus.nullwave.core.mvi.UiState
import com.zaus.nullwave.feature.library.api.access.AccessAction
import com.zaus.nullwave.feature.library.api.model.artist.LibraryArtistDetails

internal data class ArtistDetailsState(
    val localeTag: String,
    /** Last host check. A provider denial still blocks reads until a fresh check. */
    val platformGranted: Boolean = false,
    val body: ArtistDetailsBody = ArtistDetailsBody.CheckingAccess,
) : UiState

internal sealed interface ArtistDetailsBody {
    val details: LibraryArtistDetails? get() = null

    data object CheckingAccess : ArtistDetailsBody
    data class AccessRequired(val action: AccessAction) : ArtistDetailsBody
    data class Loading(override val details: LibraryArtistDetails? = null) : ArtistDetailsBody
    data class Ready(override val details: LibraryArtistDetails) : ArtistDetailsBody
    data object NotFound : ArtistDetailsBody
    data class ReadFailed(override val details: LibraryArtistDetails?) : ArtistDetailsBody
}
